package cnm.prs.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.SaisiePpmImportResult;
import cnm.prs.dto.SaisiePpmImportResult.BeneficiaireImport;
import cnm.prs.dto.SaisiePpmImportResult.MarcheImport;
import cnm.prs.dto.SaisiePpmImportResult.PrevisionImport;
import cnm.prs.entity.EntiteContract;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.exception.BadRequestException;
import cnm.prs.repository.CompteRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.NatureRepository;
import cnm.prs.repository.SoaBeneficiaireRepository;

/**
 * Parsing <strong>read-only</strong> d'un PPM PDF pour pré-remplir le formulaire de saisie
 * ({@code POST /api/saisies/ppm/import}). N'écrit rien : extrait le texte (PDFBox), résout au mieux l'en-tête,
 * les <strong>lignes de données du tableau</strong> et les référentiels (sinon libellé + avertissement), et
 * renvoie un {@link SaisiePpmImportResult}. Un PDF illisible → {@link BadRequestException} (400).
 *
 * <p>Calibré sur le format officiel (ex. {@code PPM_26-…}) : le tableau est délimité par la ligne d'en-tête des
 * colonnes ({@code NATURE OBJET MONTANT …}) et se termine avant « {@code Fait à … le …} ». Chaque ligne de
 * données regroupe : {@code NATURE} + {@code OBJET} (multi-lignes, recomposé), puis une <em>ligne montants</em>
 * {@code montEstim [nouvMontEstim] | mode | financement | soaCode | compte | montBenef [nouvMontBenef] | 3 dates}.
 */
@Service
public class SaisiePpmImportService {

    private static final Pattern EXERCICE = Pattern.compile("(?i)(?:exercice|gestion|ann[eé]e)\\D{0,15}(20\\d{2})");
    private static final Pattern ANNEE = Pattern.compile("(20\\d{2})");
    private static final Pattern FAIT_LE = Pattern.compile(
            "(?i)fait\\s+[aàâä]\\s+[^,\\n]+?\\s+le\\s+(\\d{1,2})\\s+([a-zàâäéèêëîïôöùûüç]+)\\s+(20\\d{2})");
    private static final Pattern ETABLISSEMENT = Pattern.compile(
            "(?i)date\\s+d.?[eé]tablissement[^:]*:?\\s*(\\d{2})/(\\d{2})/(20\\d{2})");
    private static final Pattern AUTORITE = Pattern.compile("(?i)autorit[eé]\\s+contractante\\s*:?\\s*(.+)");

    /** Ligne d'en-tête des colonnes : marque le début du tableau (les données suivent). */
    private static final Pattern ENTETE_COLONNES = Pattern.compile("(?i)nature\\s+objet\\s+montant");
    /**
     * Fin <strong>structurelle</strong> du tableau : bloc signature. Multi-pages : on borne à la
     * <strong>dernière</strong> occurrence (un « Fait à … » répété en pied de chaque page ne doit pas tronquer).
     */
    private static final Pattern FIN_TABLEAU = Pattern.compile("(?i)^\\s*(fait\\s+[aàâä]\\b|la\\s+personne\\s+responsable)");
    /**
     * Bruit de page <strong>répété</strong> à ignorer dans le tableau (multi-pages) : en-tête et sous-en-tête
     * des colonnes rejoués, filigrane, numéro de page, et pied « Fait à … » intermédiaire (seul le dernier borne).
     */
    private static final Pattern BRUIT_PAGE = Pattern.compile(
            "(?i)^\\s*(nature\\s+objet\\s+montant"
                    + "|service\\s+b[eé]n[eé]ficiaire"
                    + "|powered\\s+by"
                    + "|page\\s+\\d"
                    + "|fait\\s+[aàâä]\\b"
                    + "|la\\s+personne\\s+responsable"
                    + "|\\d{1,3}\\s*/\\s*\\d{1,3}\\s*$"
                    + "|\\d{1,3}\\s*$)");

    /** Nature en tête d'une ligne de données (vocabulaire PPM, ordonné du plus long au plus court). */
    private static final Pattern NATURE_TETE = Pattern.compile(
            "(?i)^\\s*(Fournitures et services|Prestations intellectuelles|Travaux|Fournitures|Services)\\s+(.+)$");

    private static final String M = "([\\d][\\d\\s\\u00a0]*[.,]\\d{2})";   // un montant « 1 005 000.00 »
    private static final String D = "(\\d{2}/\\d{2}/\\d{4})";              // une date « 27/04/2026 »
    /** Ligne montants : montEstim [nouvMontEstim] mode financement soaCode compte montBenef [nouvMontBenef] d1 d2 d3. */
    private static final Pattern LIGNE_DONNEES = Pattern.compile(
            "^\\s*" + M + "(?:\\s+" + M + ")?\\s+(.+?)\\s+([A-Za-zÀ-ÿ]{2,})\\s+(\\d{2}-\\d{2}-\\d\\S*)\\s+(\\S+)\\s+"
                    + M + "(?:\\s+" + M + ")?\\s+" + D + "\\s+" + D + "\\s+" + D + "\\s*$");

    private static final Map<String, Integer> MOIS = Map.ofEntries(
            Map.entry("janvier", 1), Map.entry("fevrier", 2), Map.entry("mars", 3), Map.entry("avril", 4),
            Map.entry("mai", 5), Map.entry("juin", 6), Map.entry("juillet", 7), Map.entry("aout", 8),
            Map.entry("septembre", 9), Map.entry("octobre", 10), Map.entry("novembre", 11), Map.entry("decembre", 12));

    private final EntiteContractRepository entiteRepository;
    private final NatureRepository natureRepository;
    private final ModePassationRepository modePassationRepository;
    private final CompteRepository compteRepository;
    private final SoaBeneficiaireRepository soaBeneficiaireRepository;

    public SaisiePpmImportService(EntiteContractRepository entiteRepository, NatureRepository natureRepository,
            ModePassationRepository modePassationRepository, CompteRepository compteRepository,
            SoaBeneficiaireRepository soaBeneficiaireRepository) {
        this.entiteRepository = entiteRepository;
        this.natureRepository = natureRepository;
        this.modePassationRepository = modePassationRepository;
        this.compteRepository = compteRepository;
        this.soaBeneficiaireRepository = soaBeneficiaireRepository;
    }

    public SaisiePpmImportResult importer(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Fichier PDF manquant ou vide.");
        }
        String texte = extraireTexte(fichier);
        List<String> avert = new ArrayList<>();

        Integer exercice = extraireExercice(texte);
        if (exercice == null) {
            avert.add("Exercice non détecté dans le PDF — à saisir.");
        }
        String dateSignature = extraireDateSignature(texte);
        if (dateSignature == null) {
            avert.add("Date de signature / d'établissement non détectée — à confirmer.");
        }
        String autorite = extraireAutorite(texte);
        Integer idEntite = resoudreEntite(autorite, avert);

        List<MarcheImport> marches = parserMarches(texte, avert);
        return new SaisiePpmImportResult(exercice, dateSignature, autorite, idEntite, marches, avert);
    }

    private String extraireTexte(MultipartFile fichier) {
        byte[] bytes;
        try {
            bytes = fichier.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Lecture du fichier impossible : " + e.getMessage());
        }
        if (bytes.length < 5 || !(bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F')) {
            throw new BadRequestException("Le fichier fourni n'est pas un PDF valide.");
        }
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String t = new PDFTextStripper().getText(doc);
            if (t == null || t.isBlank()) {
                throw new BadRequestException("PDF sans texte extractible (document scanné/image ?).");
            }
            return t;
        } catch (IOException e) {
            throw new BadRequestException("PDF illisible ou invalide.");
        }
    }

    private Integer extraireExercice(String texte) {
        Matcher m = EXERCICE.matcher(texte);
        if (m.find()) {
            return Integer.valueOf(m.group(1));
        }
        Matcher a = ANNEE.matcher(texte);
        return a.find() ? Integer.valueOf(a.group(1)) : null;
    }

    /** « Fait à … le 14 avril 2026 » ; à défaut « Date d'établissement … : 14/04/2026 » → {@code 2026-04-14}. */
    private String extraireDateSignature(String texte) {
        Matcher m = FAIT_LE.matcher(texte);
        if (m.find()) {
            Integer mois = MOIS.get(sansAccents(m.group(2)).toLowerCase(Locale.FRENCH));
            if (mois != null) {
                return String.format("%04d-%02d-%02d", Integer.parseInt(m.group(3)), mois, Integer.parseInt(m.group(1)));
            }
        }
        Matcher e = ETABLISSEMENT.matcher(texte);
        return e.find() ? e.group(3) + "-" + e.group(2) + "-" + e.group(1) : null;
    }

    private String extraireAutorite(String texte) {
        Matcher m = AUTORITE.matcher(texte);
        if (!m.find()) {
            return null;
        }
        String v = m.group(1).trim();
        int coupe = v.indexOf("  ");
        return coupe > 0 ? v.substring(0, coupe).trim() : v;
    }

    private Integer resoudreEntite(String autorite, List<String> avert) {
        if (autorite == null || autorite.isBlank()) {
            avert.add("Autorité contractante non détectée — la PRMP choisit son entité.");
            return null;
        }
        String cible = normaliser(autorite);
        Integer id = entiteRepository.findAll().stream()
                .filter(e -> e.getLibelleEntite() != null && !e.getLibelleEntite().isBlank())
                .filter(e -> {
                    String lib = normaliser(e.getLibelleEntite());
                    return cible.contains(lib) || lib.contains(cible);
                })
                .map(EntiteContract::getIdEntiteContract)
                .findFirst().orElse(null);
        if (id == null) {
            avert.add("Entité contractante « " + autorite + " » non résolue — à sélectionner dans la liste.");
        }
        return id;
    }

    /**
     * Extrait les lignes de <strong>données</strong> du tableau, entre l'en-tête des colonnes et « Fait à… ».
     * Chaque ligne = {@code NATURE} + {@code OBJET} (recomposé sur plusieurs lignes) closes par la ligne montants.
     */
    private List<MarcheImport> parserMarches(String texte, List<String> avert) {
        String[] lignes = texte.split("\\r?\\n");
        int debut = -1;
        for (int i = 0; i < lignes.length; i++) {
            if (ENTETE_COLONNES.matcher(lignes[i]).find()) {   // premier en-tête de colonnes = début du tableau
                debut = i + 1;
                break;
            }
        }
        if (debut < 0) {
            avert.add("En-tête du tableau (NATURE | OBJET | …) introuvable — aucune ligne extraite.");
            return List.of();
        }
        // Multi-pages : le tableau se termine à la DERNIÈRE fin structurelle (« Fait à … » / « La personne
        // responsable »), pas à la première — sinon un pied de page répété tronquerait le tableau dès la page 1.
        int fin = lignes.length;
        for (int i = lignes.length - 1; i >= debut; i--) {
            if (FIN_TABLEAU.matcher(lignes[i].trim()).find()) {
                fin = i;
                break;
            }
        }

        List<MarcheImport> marches = new ArrayList<>();
        List<String> objet = new ArrayList<>();
        String natureLibelle = null;
        for (int i = debut; i < fin; i++) {
            String l = lignes[i].trim();
            if (l.isEmpty() || BRUIT_PAGE.matcher(l).find()) {   // en-tête/sous-en-tête/n° page/filigrane/pied répétés
                continue;
            }
            Matcher donnees = LIGNE_DONNEES.matcher(l);
            if (donnees.matches() && !objet.isEmpty()) {
                marches.add(construireMarche(natureLibelle, String.join(" ", objet).trim(), donnees, avert));
                objet.clear();
                natureLibelle = null;
                continue;
            }
            Matcher nat = NATURE_TETE.matcher(l);
            if (objet.isEmpty()) {
                if (nat.matches()) {                // début d'une ligne de données : NATURE + OBJET(1)
                    natureLibelle = nat.group(1).trim();
                    objet.add(nat.group(2).trim());
                }                                    // sinon : ligne d'en-tête de sous-colonne → ignorée
            } else {
                objet.add(l);                        // continuation de l'OBJET (cellule multi-lignes)
            }
        }
        if (marches.isEmpty()) {
            avert.add("Aucune ligne de données détectée dans le tableau — format à vérifier.");
        }
        return marches;
    }

    private MarcheImport construireMarche(String natureLibelle, String designation, Matcher m, List<String> avert) {
        BigDecimal montEstim = parseMontant(m.group(1));
        BigDecimal nouvMontEstim = m.group(2) == null ? null : parseMontant(m.group(2));
        String modeLibelle = m.group(3).trim();
        String financement = m.group(4).trim();
        String soaCode = m.group(5).trim();
        String compte = m.group(6).trim();
        BigDecimal ancMontBenef = parseMontant(m.group(7));
        BigDecimal nouvMontBenef = m.group(8) == null ? null : parseMontant(m.group(8));

        Integer idNature = resoudreIdParLibelle(natureRepository.findAll(), Nature::getLibelle, Nature::getIdNature, natureLibelle);
        if (natureLibelle != null && idNature == null) {
            avert.add("Nature « " + natureLibelle + " » non trouvée au référentiel — à confirmer.");
        }
        Integer idMode = resoudreIdParLibelle(modePassationRepository.findAll(), ModePassation::getLibelle,
                ModePassation::getIdMode, modeLibelle);
        if (modeLibelle != null && idMode == null) {
            avert.add("Mode de passation « " + modeLibelle + " » non trouvé au référentiel — à confirmer.");
        }
        if (!soaCode.isBlank() && !soaBeneficiaireRepository.existsById(soaCode)) {
            avert.add("Service bénéficiaire (SOA) « " + soaCode + " » inconnu — à confirmer.");
        }
        if (!compte.isBlank() && !compteRepository.existsById(compte)) {
            avert.add("Compte « " + compte + " » inconnu — à confirmer.");
        }

        List<BeneficiaireImport> benef = List.of(new BeneficiaireImport(soaCode, compte, ancMontBenef, nouvMontBenef));
        List<PrevisionImport> prev = List.of(
                new PrevisionImport("LANCEMENT", isoDate(m.group(9))),
                new PrevisionImport("OUVERTURE", isoDate(m.group(10))),
                new PrevisionImport("ATTRIBUTION", isoDate(m.group(11))));
        return new MarcheImport(designation, montEstim, nouvMontEstim, idNature, natureLibelle,
                idMode, modeLibelle, financement, benef, prev);
    }

    private <T> Integer resoudreIdParLibelle(List<T> refs, java.util.function.Function<T, String> libelle,
            java.util.function.Function<T, Integer> id, String cible) {
        if (cible == null || cible.isBlank()) {
            return null;
        }
        String c = normaliser(cible);
        return refs.stream()
                .filter(x -> libelle.apply(x) != null && normaliser(libelle.apply(x)).equals(c))
                .map(id).findFirst().orElse(null);
    }

    /** « 27/04/2026 » → « 2026-04-27 ». */
    private static String isoDate(String jjmmaaaa) {
        String[] p = jjmmaaaa.split("/");
        return p[2] + "-" + p[1] + "-" + p[0];
    }

    private static BigDecimal parseMontant(String brut) {
        if (brut == null) {
            return null;
        }
        String net = brut.replaceAll("[\\s\\u00a0]", "").replace(',', '.');
        try {
            return new BigDecimal(net);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normaliser(String s) {
        return sansAccents(s).toUpperCase(Locale.FRENCH).replaceAll("[^A-Z0-9]", "");
    }

    private static String sansAccents(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
