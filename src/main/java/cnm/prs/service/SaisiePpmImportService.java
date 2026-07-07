package cnm.prs.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
 * <p><strong>Parseur sémantique par enregistrement</strong> (robuste aux en-têtes de colonnes multi-lignes et au
 * multi-pages) : l'extraction démarre à la <strong>première NATURE connue</strong> (l'en-tête des colonnes, même
 * éclaté sur plusieurs lignes, est ignoré) et se termine à la <strong>dernière</strong> « Fait à … ». Chaque
 * enregistrement (délimité par une NATURE) est <strong>recomposé</strong> (lignes jointes) puis lu par position :
 * {@code NATURE} → {@code OBJET} (avant le 1ᵉʳ montant) → {@code montEstim [nouvMontEstim]} → {@code mode}
 * (multi-mots) + {@code financement} (dernier mot avant le 1ᵉʳ SOA) → {@code SOA[]} → {@code compte} →
 * {@code montants bénéficiaires} → 3 dates prévisionnelles. Multi-bénéficiaires : {@code K} montants pour
 * {@code n} SOA ⇒ {@code K=2n} (anc + nouv) ou {@code K=n} (anc seul).
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
    /** Étiquettes de l'en-tête doc qui bornent la valeur (multi-lignes) de l'autorité contractante. */
    private static final Pattern LABEL_ENTETE = Pattern.compile(
            "(?i)^\\s*(nom\\s+de\\s+la\\s+prmp|adresse|date\\s+d|num[eé]ro|nature\\b|objet\\b)");

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
            "(?i)^\\s*(nature\\s+objet"
                    + "|montant\\s+estimatif"
                    + "|nouveau\\s+montant"
                    + "|mode\\s+de\\s+passation"
                    + "|informations\\s+sur\\s+le\\s+b[eé]n[eé]ficiaire"
                    + "|service\\s+b[eé]n[eé]ficiaire"
                    + "|montant|estimatif|initial|nouveau|cement|finan|nelle|ment|des\\s+plis|d.attri|bution|ouveture|prevision"
                    + "|powered\\s+by"
                    + "|page\\s+\\d"
                    + "|fait\\s+[aàâä]\\b"
                    + "|la\\s+personne\\s+responsable"
                    + "|\\d{1,3}\\s*/\\s*\\d{1,3}\\s*$"
                    + "|\\d{1,3}\\s*$)");
    /** En-tête de page courant du PPM (ex. « PPM_26-488-0078 page 2/2 18/06/2026 05:55 »). */
    private static final Pattern PAGE_COURANTE = Pattern.compile("(?i)\\bpage\\s+\\d+\\s*/\\s*\\d+");

    /** Début d'enregistrement en style MAJUSCULES (ex. MIDSP : NATURE seule sur sa ligne). */
    private static final Pattern DEBUT_MAJ = Pattern.compile("^(FOURNITURES|TRAVAUX|PRESTATIONS|SERVICES)\\b");
    /** Début d'enregistrement en style « titre » (NATURE + OBJET sur la même ligne, ex. PPM_26-…). */
    private static final Pattern NATURE_TETE = Pattern.compile(
            "(?i)^\\s*(Fournitures et services|Prestations intellectuelles|Prestations de service"
                    + "|Travaux|Fournitures|Services)\\s+(.+)$");
    /** Libellé NATURE en tête d'un enregistrement recomposé (le plus long d'abord). */
    private static final Pattern NATURE_LABEL = Pattern.compile(
            "(?i)^(Fournitures et services|Prestations intellectuelles|Prestations de service"
                    + "|Fournitures|Travaux|Services)\\b");

    /** Montant « 1 005 000.00 » (groupes de milliers séparés par espace, ou nombre simple) — jamais un compte nu. */
    private static final String MONTANT = "(?:\\d{1,3}(?:[\\s\\u00a0]\\d{3})*|\\d+)[.,]\\d{2}";
    private static final Pattern MONEY = Pattern.compile(MONTANT);
    private static final Pattern DATE = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");
    /** Jeton typé du bloc structurel : montant | code SOA | date | compte (3-4 chiffres nus) | mot. */
    private static final Pattern TOKEN = Pattern.compile(
            "(?<money>" + MONTANT + ")"
                    + "|(?<soa>\\d{2}-\\d{2}-\\d-[A-Za-z0-9]{1,3}-\\d{5})"
                    + "|(?<date>\\d{2}/\\d{2}/\\d{4})"
                    + "|(?<compte>\\d{3,4})"
                    + "|(?<word>[\\p{L}][\\p{L}'’.()/\\-]*)");

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

    /**
     * Autorité contractante, <strong>recomposée</strong> si elle déborde sur les lignes suivantes (uniquement les
     * lignes entièrement en MAJUSCULES, jusqu'à la prochaine étiquette de l'en-tête ou une ligne en casse mixte).
     */
    private String extraireAutorite(String texte) {
        String[] lignes = texte.split("\\r?\\n");
        for (int i = 0; i < lignes.length; i++) {
            Matcher m = AUTORITE.matcher(lignes[i]);
            if (!m.find()) {
                continue;
            }
            StringBuilder sb = new StringBuilder(m.group(1).trim());
            for (int j = i + 1; j < lignes.length; j++) {
                String l = lignes[j].trim();
                // Continuation d'un nom d'autorité = ligne en MAJUSCULES (pas de minuscule) ; sinon on arrête.
                if (l.isEmpty() || LABEL_ENTETE.matcher(l).find() || l.matches(".*[a-zà-ÿ].*")) {
                    break;
                }
                sb.append(' ').append(l);
            }
            String v = sb.toString().trim();
            int coupe = v.indexOf("  ");
            return coupe > 0 ? v.substring(0, coupe).trim() : v;
        }
        return null;
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
     * Découpe le tableau en enregistrements (un par NATURE), en démarrant à la première NATURE connue (l'en-tête
     * des colonnes — éventuellement éclaté sur plusieurs lignes — est ainsi ignoré) et en bornant à la dernière
     * fin de tableau (multi-pages). Chaque enregistrement recomposé est lu par {@link #parserRecord}.
     */
    private List<MarcheImport> parserMarches(String texte, List<String> avert) {
        String[] lignes = texte.split("\\r?\\n");
        // Style du document : MAJUSCULES (NATURE seule) vs. « titre » (NATURE + OBJET même ligne).
        boolean styleMaj = false;
        for (String l : lignes) {
            if (DEBUT_MAJ.matcher(l.trim()).find()) {
                styleMaj = true;
                break;
            }
        }
        int debut = -1;
        for (int i = 0; i < lignes.length; i++) {
            if (estDebutRecord(lignes[i].trim(), styleMaj)) {
                debut = i;
                break;
            }
        }
        if (debut < 0) {
            avert.add("Aucune ligne de données (NATURE) détectée — en-tête du tableau introuvable, format à vérifier.");
            return List.of();
        }
        int fin = lignes.length;
        for (int i = lignes.length - 1; i >= debut; i--) {
            if (FIN_TABLEAU.matcher(lignes[i].trim()).find()) {
                fin = i;
                break;
            }
        }

        List<MarcheImport> marches = new ArrayList<>();
        List<String> record = new ArrayList<>();
        for (int i = debut; i < fin; i++) {
            String l = lignes[i].trim();
            if (l.isEmpty() || PAGE_COURANTE.matcher(l).find() || BRUIT_PAGE.matcher(l).find()) {
                continue;   // en-tête/sous-en-tête/n° page/filigrane/pied répétés
            }
            if (estDebutRecord(l, styleMaj) && !record.isEmpty()) {
                marches.add(parserRecord(String.join(" ", record), avert));
                record.clear();
            }
            record.add(l);
        }
        if (!record.isEmpty()) {
            marches.add(parserRecord(String.join(" ", record), avert));
        }
        marches.removeIf(Objects::isNull);
        if (marches.isEmpty()) {
            avert.add("Aucune ligne de données détectée dans le tableau — format à vérifier.");
        }
        return marches;
    }

    private static boolean estDebutRecord(String ligne, boolean styleMaj) {
        return styleMaj ? DEBUT_MAJ.matcher(ligne).find() : NATURE_TETE.matcher(ligne).matches();
    }

    /**
     * Lit un enregistrement recomposé (lignes jointes) par position : NATURE, OBJET, montants de tête
     * ({@code montEstim [nouvMontEstim]}), mode + financement, codes SOA, compte, montants bénéficiaires, 3 dates.
     * Retourne {@code null} si aucun montant (ligne non exploitable).
     */
    private MarcheImport parserRecord(String rt, List<String> avert) {
        String natureLibelle = null;
        Matcher nl = NATURE_LABEL.matcher(rt);
        if (nl.find() && nl.start() == 0) {
            natureLibelle = rt.substring(0, nl.end()).trim();
            rt = rt.substring(nl.end()).trim();
        }
        // Prévisions = 3 dernières dates ; le « core » exploitable est ce qui précède.
        List<String> dates = new ArrayList<>();
        List<Integer> pos = new ArrayList<>();
        Matcher dm = DATE.matcher(rt);
        while (dm.find()) {
            dates.add(dm.group());
            pos.add(dm.start());
        }
        int coreEnd = rt.length();
        List<String> prev3 = dates;
        if (dates.size() >= 3) {
            coreEnd = pos.get(dates.size() - 3);
            prev3 = dates.subList(dates.size() - 3, dates.size());
        } else if (!pos.isEmpty()) {
            coreEnd = pos.get(0);
        }
        String core = rt.substring(0, coreEnd);

        Matcher fm = MONEY.matcher(core);
        if (!fm.find()) {
            avert.add("Ligne « " + (natureLibelle == null ? "?" : natureLibelle) + " » sans montant — ignorée.");
            return null;
        }
        String designation = core.substring(0, fm.start()).trim();
        List<String[]> toks = tokeniser(core.substring(fm.start()));

        int i = 0;
        List<BigDecimal> mtsTete = new ArrayList<>();
        while (i < toks.size() && "money".equals(toks.get(i)[0])) {
            mtsTete.add(parseMontant(toks.get(i)[1]));
            i++;
        }
        BigDecimal montEstim = mtsTete.isEmpty() ? null : mtsTete.get(0);
        BigDecimal nouvMontEstim = mtsTete.size() >= 2 ? mtsTete.get(1) : null;

        List<String> mots = new ArrayList<>();
        while (i < toks.size() && "word".equals(toks.get(i)[0])) {
            mots.add(toks.get(i)[1]);
            i++;
        }
        String financement = mots.isEmpty() ? null : mots.get(mots.size() - 1);
        String modeLibelle = mots.size() <= 1 ? null : String.join(" ", mots.subList(0, mots.size() - 1));

        List<String> soas = new ArrayList<>();
        while (i < toks.size() && "soa".equals(toks.get(i)[0])) {
            soas.add(toks.get(i)[1]);
            i++;
        }
        String compte = null;
        if (i < toks.size() && "compte".equals(toks.get(i)[0])) {
            compte = toks.get(i)[1];
            i++;
        }
        List<BigDecimal> mtsBenef = new ArrayList<>();
        for (; i < toks.size(); i++) {
            if ("money".equals(toks.get(i)[0])) {
                mtsBenef.add(parseMontant(toks.get(i)[1]));
            }
        }

        List<BeneficiaireImport> benef = construireBeneficiaires(soas, compte, mtsBenef, natureLibelle, avert);
        List<PrevisionImport> prev = List.of(
                new PrevisionImport("LANCEMENT", prev3.size() > 0 ? isoDate(prev3.get(0)) : null),
                new PrevisionImport("OUVERTURE", prev3.size() > 1 ? isoDate(prev3.get(1)) : null),
                new PrevisionImport("ATTRIBUTION", prev3.size() > 2 ? isoDate(prev3.get(2)) : null));

        Integer idNature = resoudreIdParLibelle(natureRepository.findAll(), Nature::getLibelle, Nature::getIdNature, natureLibelle);
        if (natureLibelle != null && idNature == null) {
            avert.add("Nature « " + natureLibelle + " » non trouvée au référentiel — à confirmer.");
        }
        Integer idMode = resoudreIdParLibelle(modePassationRepository.findAll(), ModePassation::getLibelle,
                ModePassation::getIdMode, modeLibelle);
        if (modeLibelle != null && idMode == null) {
            avert.add("Mode de passation « " + modeLibelle + " » non trouvé au référentiel — à confirmer.");
        }
        return new MarcheImport(designation, montEstim, nouvMontEstim, idNature, natureLibelle,
                idMode, modeLibelle, financement, benef, prev);
    }

    /**
     * Reconstruit les bénéficiaires : un par code SOA, compte partagé, avec l'alignement des montants
     * ({@code 2n} montants ⇒ ancien + nouveau par bénéficiaire ; {@code n} montants ⇒ ancien seul).
     */
    private List<BeneficiaireImport> construireBeneficiaires(List<String> soas, String compte,
            List<BigDecimal> montants, String nat, List<String> avert) {
        List<BeneficiaireImport> benef = new ArrayList<>();
        int n = soas.size();
        if (n == 0) {
            if (compte != null || !montants.isEmpty()) {
                benef.add(new BeneficiaireImport(null, compte,
                        montants.size() > 0 ? montants.get(0) : null,
                        montants.size() > 1 ? montants.get(1) : null));
            }
            return benef;
        }
        List<BigDecimal> anc;
        List<BigDecimal> nouv = null;
        if (montants.size() == 2 * n) {
            anc = montants.subList(0, n);
            nouv = montants.subList(n, 2 * n);
        } else if (montants.size() == n) {
            anc = montants;
        } else {
            avert.add("Bénéficiaires « " + nat + " » : " + n + " service(s) pour " + montants.size()
                    + " montant(s) — alignement incertain, à vérifier.");
            anc = montants;
        }
        for (int k = 0; k < n; k++) {
            benef.add(new BeneficiaireImport(soas.get(k), compte,
                    k < anc.size() ? anc.get(k) : null,
                    nouv != null && k < nouv.size() ? nouv.get(k) : null));
        }
        long soaInconnus = soas.stream().distinct().filter(s -> !soaBeneficiaireRepository.existsById(s)).count();
        if (soaInconnus > 0) {
            avert.add(soaInconnus + " service(s) bénéficiaire(s) (SOA) inconnu(s) au référentiel — à confirmer.");
        }
        if (compte != null && !compteRepository.existsById(compte)) {
            avert.add("Compte « " + compte + " » inconnu — à confirmer.");
        }
        return benef;
    }

    private static List<String[]> tokeniser(String s) {
        List<String[]> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(s);
        while (m.find()) {
            if (m.group("money") != null) {
                out.add(new String[] { "money", m.group("money") });
            } else if (m.group("soa") != null) {
                out.add(new String[] { "soa", m.group("soa") });
            } else if (m.group("date") != null) {
                out.add(new String[] { "date", m.group("date") });
            } else if (m.group("compte") != null) {
                out.add(new String[] { "compte", m.group("compte") });
            } else if (m.group("word") != null) {
                out.add(new String[] { "word", m.group("word") });
            }
        }
        return out;
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
