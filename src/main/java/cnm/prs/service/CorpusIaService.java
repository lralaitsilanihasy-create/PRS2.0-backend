package cnm.prs.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import cnm.prs.config.AssistantIaProperties;
import cnm.prs.config.AssistantIaProperties.DocumentCorpus;

/**
 * Corpus de l'assistant IA : les documents de référence, découpés en passages citables et indexés
 * ({@code docs/plan-assistant-ia.md}, lot 1).
 *
 * <p>Le lot 1 ne lit <strong>aucune donnée métier</strong> : le corpus est fait de textes identiques
 * pour tous les profils (le manuel de contrôle a priori de la CNM, les règles de gestion de PRS). Il
 * ne peut donc rien faire fuir d'un périmètre à l'autre.</p>
 *
 * <ul>
 *   <li><strong>PDF</strong> : un passage par page, cité « p. N » ;</li>
 *   <li><strong>Markdown</strong> : un passage par section de titre ({@code #} à {@code ####}), les
 *       lignes entièrement en gras (« **Module 02 — Saisie & gestion PPM** ») servant de sous-titre ;
 *       une section trop longue est recoupée vers {@value #TAILLE_MAX_PASSAGE} caractères, de
 *       préférence sur une ligne vide ou un début de liste.</li>
 * </ul>
 *
 * <p>Chargé une fois (au démarrage si l'assistant est actif, sinon au premier usage) puis immuable.
 * Un document introuvable ou illisible est <strong>ignoré avec un avertissement</strong> : il ne doit
 * pas empêcher l'application de démarrer.</p>
 */
@Service
public class CorpusIaService {

    private static final Logger log = LoggerFactory.getLogger(CorpusIaService.class);

    /** Taille visée d'un passage recoupé (~500 jetons) : cinq passages tiennent dans le contexte. */
    static final int TAILLE_MAX_PASSAGE = 1800;
    /** Une page PDF plus courte (couverture, page blanche) n'apprend rien au modèle. */
    private static final int TAILLE_MIN_PAGE = 120;
    /** Début de la page suivante joint à chaque page PDF, pour qu'une liste coupée reste entière. */
    static final int TAILLE_SUITE_PAGE = 600;
    /** Un passage Markdown plus court (titre suivi d'une ligne) n'est pas un extrait utile. */
    private static final int TAILLE_MIN_SECTION = 40;

    private static final Pattern TITRE = Pattern.compile("^(#{1,4})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern SOUS_TITRE_GRAS = Pattern.compile("^\\*\\*([^*]{3,}?)\\*\\*\\s*:?\\s*$");
    private static final Pattern NUMERO_DE_PAGE = Pattern.compile("^\\d{1,4}$");

    /**
     * Un passage citable.
     *
     * @param id        identifiant stable dans le corpus chargé (ex. {@code d0-p15})
     * @param document  libellé du document (ex. « Manuel de contrôle a priori (CNM, février 2026) »)
     * @param reference emplacement dans le document (ex. « p. 15 », « 3.1. PRMP › Module 02 »)
     * @param texte     contenu du passage
     */
    public record Passage(String id, String document, String reference, String texte) {
    }

    /** Un document effectivement chargé et le nombre de ses passages. */
    public record DocumentCharge(String libelle, int passages) {
    }

    private record Corpus(List<DocumentCharge> documents, Map<String, Passage> passages, IndexLexicalIa index) {
    }

    private final AssistantIaProperties props;
    private volatile Corpus corpus;

    public CorpusIaService(AssistantIaProperties props) {
        this.props = props;
    }

    /** Charge le corpus dès le démarrage quand l'assistant est actif : la première question n'attend pas. */
    @EventListener(ApplicationReadyEvent.class)
    public void prechargerSiActif() {
        if (props.actif()) {
            corpus();
        }
    }

    /** Les passages les plus pertinents pour la question, du meilleur au moins bon (liste possiblement vide). */
    public List<Passage> rechercher(String question, int nombre) {
        Corpus c = corpus();
        return c.index().rechercher(question, nombre).stream()
                .map(r -> c.passages().get(r.identifiant()))
                .toList();
    }

    /** Les documents chargés, dans l'ordre de la configuration. */
    public List<DocumentCharge> documents() {
        return corpus().documents();
    }

    private Corpus corpus() {
        Corpus c = corpus;
        if (c == null) {
            synchronized (this) {
                c = corpus;
                if (c == null) {
                    c = charger();
                    corpus = c;
                }
            }
        }
        return c;
    }

    private Corpus charger() {
        List<DocumentCharge> documents = new ArrayList<>();
        Map<String, Passage> passages = new LinkedHashMap<>();
        int rang = 0;
        for (DocumentCorpus doc : props.corpus()) {
            String prefixe = "d" + rang++;
            if (doc == null || doc.chemin() == null || doc.chemin().isBlank()) {
                continue;
            }
            String libelle = doc.libelle() == null || doc.libelle().isBlank()
                    ? Path.of(doc.chemin()).getFileName().toString() : doc.libelle().strip();
            Path fichier = Path.of(doc.chemin().strip());
            if (!Files.isRegularFile(fichier)) {
                log.warn("Assistant IA : document du corpus introuvable, ignoré : {}", fichier);
                continue;
            }
            try {
                List<Passage> lus = fichier.toString().toLowerCase(Locale.ROOT).endsWith(".pdf")
                        ? decouperPdf(fichier, libelle, prefixe)
                        : decouperMarkdown(Files.readString(fichier, StandardCharsets.UTF_8), libelle, prefixe);
                lus.forEach(p -> passages.put(p.id(), p));
                documents.add(new DocumentCharge(libelle, lus.size()));
            } catch (IOException | RuntimeException e) {
                log.warn("Assistant IA : document du corpus illisible, ignoré : {} ({})", fichier, e.getMessage());
            }
        }
        Map<String, String> textes = new LinkedHashMap<>();
        passages.values().forEach(p -> textes.put(p.id(), p.reference() + "\n" + p.texte()));
        IndexLexicalIa index = IndexLexicalIa.construire(textes);
        log.info("Assistant IA : corpus chargé — {} passage(s) dans {} document(s)", index.taille(), documents.size());
        return new Corpus(List.copyOf(documents), Map.copyOf(passages), index);
    }

    /** Un passage par page, cité « p. N » (numérotation du fichier PDF). */
    static List<Passage> decouperPdf(Path fichier, String libelle, String prefixe) throws IOException {
        List<Passage> passages = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(fichier.toFile())) {
            PDFTextStripper extracteur = new PDFTextStripper();
            // ⚠️ Recette 2026-09-18 — surtout PAS de tri par position : le manuel est fait de tableaux
            // à deux ou trois colonnes, et le tri par position fond les colonnes ligne à ligne
            // (« maximum 20% anormalement basses et pour les offres anormalement hautes… », p. 20).
            // Le modèle, sur ce texte mêlé, inversait les seuils des offres anormalement hautes et
            // basses. L'ordre du flux du PDF suit les cellules : la règle y reste lisible d'un tenant.
            extracteur.setSortByPosition(false);
            List<String> pages = new ArrayList<>();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                extracteur.setStartPage(page);
                extracteur.setEndPage(page);
                pages.add(nettoyerPage(extracteur.getText(pdf)));
            }
            for (int i = 0; i < pages.size(); i++) {
                String texte = pages.get(i);
                if (texte.length() < TAILLE_MIN_PAGE) {
                    continue;
                }
                // ⚠️ Recette 2026-09-18 — une liste coupée par un saut de page (les conditions du
                // marché complémentaire, p. 43-44) arrivait tronquée au modèle, qui complétait de
                // travers. Chaque page emporte donc le début de la suivante, signalé comme tel.
                if (i + 1 < pages.size() && pages.get(i + 1).length() >= TAILLE_MIN_PAGE) {
                    texte += "\n[… suite en page " + (i + 2) + " :]\n" + debut(pages.get(i + 1), TAILLE_SUITE_PAGE);
                }
                int page = i + 1;
                passages.add(new Passage(prefixe + "-p" + page, libelle, "p. " + page, texte));
            }
        }
        return passages;
    }

    /** Début d'un texte, coupé à la dernière fin de ligne avant {@code taille} caractères. */
    private static String debut(String texte, int taille) {
        if (texte.length() <= taille) {
            return texte;
        }
        int coupure = texte.lastIndexOf('\n', taille);
        return texte.substring(0, coupure > taille / 2 ? coupure : taille) + " […]";
    }

    /**
     * Lignes rognées, espaces multiples réduits, lignes vides retirées, et le numéro de page ôté — en
     * fin de texte comme en tête : dans l'ordre du flux du PDF, le pied de page du manuel sort en
     * PREMIER (recette du 2026-09-18, l'extrait de la p. 15 s'ouvrait sur « 15 »).
     */
    static String nettoyerPage(String brut) {
        List<String> lignes = new ArrayList<>();
        for (String ligne : brut.split("\\R")) {
            String l = ligne.replaceAll("[ \\t\\u00A0]{2,}", " ").strip();
            if (!l.isEmpty()) {
                lignes.add(l);
            }
        }
        if (!lignes.isEmpty() && NUMERO_DE_PAGE.matcher(lignes.get(lignes.size() - 1)).matches()) {
            lignes.remove(lignes.size() - 1);
        }
        if (!lignes.isEmpty() && NUMERO_DE_PAGE.matcher(lignes.get(0)).matches()) {
            lignes.remove(0);
        }
        return String.join("\n", lignes);
    }

    /**
     * Un passage par section de titre, recoupée si besoin. La référence est le chemin des titres à
     * partir du niveau 2 (le niveau 1 est le titre du document, déjà porté par son libellé), suivi du
     * sous-titre en gras courant.
     */
    static List<Passage> decouperMarkdown(String contenu, String libelle, String prefixe) {
        List<Passage> passages = new ArrayList<>();
        String[] titres = new String[5];
        String sousTitre = null;
        StringBuilder tampon = new StringBuilder();
        int[] rang = {0};

        for (String ligne : contenu.split("\\R", -1)) {
            Matcher titre = TITRE.matcher(ligne);
            if (titre.matches()) {
                vider(passages, tampon, reference(titres, sousTitre), libelle, prefixe, rang);
                int niveau = titre.group(1).length();
                titres[niveau] = nettoyerTitre(titre.group(2));
                for (int n = niveau + 1; n < titres.length; n++) {
                    titres[n] = null;
                }
                sousTitre = null;
                continue;
            }
            Matcher gras = SOUS_TITRE_GRAS.matcher(ligne.strip());
            if (gras.matches()) {
                vider(passages, tampon, reference(titres, sousTitre), libelle, prefixe, rang);
                sousTitre = nettoyerTitre(gras.group(1));
                continue;
            }
            boolean coupure = ligne.isBlank() || ligne.startsWith("- ") || ligne.startsWith("* ")
                    || ligne.matches("^\\d+\\.\\s.*");
            boolean plein = tampon.length() + ligne.length() > TAILLE_MAX_PASSAGE;
            if (!tampon.isEmpty() && ((plein && coupure) || tampon.length() > TAILLE_MAX_PASSAGE * 3 / 2)) {
                vider(passages, tampon, reference(titres, sousTitre), libelle, prefixe, rang);
            }
            if (!(tampon.isEmpty() && ligne.isBlank())) {
                tampon.append(ligne).append('\n');
            }
        }
        vider(passages, tampon, reference(titres, sousTitre), libelle, prefixe, rang);
        return passages;
    }

    private static void vider(List<Passage> passages, StringBuilder tampon, String reference, String libelle,
            String prefixe, int[] rang) {
        String texte = tampon.toString().strip();
        tampon.setLength(0);
        if (texte.length() >= TAILLE_MIN_SECTION) {
            passages.add(new Passage(prefixe + "-s" + rang[0]++, libelle, reference, texte));
        }
    }

    private static String reference(String[] titres, String sousTitre) {
        List<String> parties = new ArrayList<>();
        for (int n = 2; n < titres.length; n++) {
            if (titres[n] != null) {
                parties.add(titres[n]);
            }
        }
        if (sousTitre != null) {
            parties.add(sousTitre);
        }
        return parties.isEmpty() ? "Introduction" : String.join(" › ", parties);
    }

    /** Retire le balisage Markdown et les pictogrammes d'un titre, pour qu'il se lise comme une référence. */
    static String nettoyerTitre(String titre) {
        return titre.replaceAll("[*`_]", "")
                .replaceAll("[\\p{So}\\p{Cn}\\uFE0F]", "")
                .replaceAll("\\s{2,}", " ")
                .strip();
    }
}
