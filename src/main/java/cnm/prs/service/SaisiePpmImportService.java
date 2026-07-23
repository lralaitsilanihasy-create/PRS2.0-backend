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
import cnm.prs.dto.SaisiePpmImportResult.AnomalieTranscription;
import cnm.prs.dto.SaisiePpmImportResult.BeneficiaireImport;
import cnm.prs.dto.SaisiePpmImportResult.LotImport;
import cnm.prs.dto.SaisiePpmImportResult.MarcheImport;
import cnm.prs.dto.SaisiePpmImportResult.PrevisionImport;
import cnm.prs.entity.EntiteContract;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.enums.ChampAnomalie;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.GraviteAnomalie;
import cnm.prs.enums.TypeAnomalie;
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
     * des colonnes rejoués, filigrane, numéro de page (« page X/Y » ou fraction « X/Y »), et pied « Fait à … »
     * intermédiaire (seul le dernier borne).
     *
     * <p>⚠️ Règle corrigée (2026-07-22) — on ne filtre <strong>plus</strong> une ligne réduite à un court nombre
     * nu ({@code \d{1,3}} seul) : c'est presque toujours un <strong>fragment d'objet</strong> isolé par PDFBox
     * (n° de route « RNS 44 » enroulé sur sa propre ligne physique), pas un numéro de page — ceux-ci sont au
     * format « page X/Y ». Le supprimer tronquait l'objet ; on le conserve, et la garde d'invariant
     * {@code montEstim == Σ bénéficiaires} corrige le montant s'il se colle au montant.</p>
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
                    + "|\\d{1,3}\\s*/\\s*\\d{1,3}\\s*$)");
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

    /**
     * ⚠️ Règle ajoutée — <strong>annonce</strong> d'allotissement dans la désignation : « répartis en 04 Lots »,
     * « répartie en trois 3 lots », « réparti en deux 2 lots »… Variantes réparti(e)(s)/repartie(s), compte en
     * <strong>lettres</strong> (deux, trois…) et/ou en <strong>chiffres</strong>. Généralisé 2026-07-22 (SIGMP).
     * Groupes : (1) mot-nombre optionnel, (2) chiffre optionnel — au moins un des deux sert de compte annoncé.
     */
    private static final Pattern MARQUEUR_LOTS = Pattern.compile(
            "(?i)r[ée]parti(?:es?|s)?\\s+en\\s+"
                    + "(?:(un|une|deux|trois|quatre|cinq|six|sept|huit|neuf|dix)\\s+)?"
                    + "(\\d{1,3})?\\s*lots?\\b");
    /**
     * Tête d'un segment de lot, généralisée (2026-07-22, SIGMP) : « Lot 01 : », « lot n1: », « LOT N°02 : »,
     * « Lot 1 - »… — {@code n} / {@code °} / zéros de tête optionnels, terminateur {@code :} ou {@code -}.
     */
    private static final Pattern SEGMENT_LOT = Pattern.compile(
            "(?i)\\blot\\s*n?\\s*[°ºo]?\\s*0*(\\d{1,3})\\s*[:\\-]");

    /** Nombres en lettres (annonce d'allotissement « trois lots »). */
    private static final Map<String, Integer> MOTS_NOMBRE = Map.ofEntries(
            Map.entry("un", 1), Map.entry("une", 1), Map.entry("deux", 2), Map.entry("trois", 3),
            Map.entry("quatre", 4), Map.entry("cinq", 5), Map.entry("six", 6), Map.entry("sept", 7),
            Map.entry("huit", 8), Map.entry("neuf", 9), Map.entry("dix", 10));

    /**
     * ⚠️ Règle ajoutée — seuil (décision documentée) de la suggestion « vouliez-vous dire … ? » : distance
     * de Levenshtein maximale (1..3) entre formes normalisées d'un mode non résolu et d'un mode du référentiel.
     */
    static final int SEUIL_SUGGESTION = 3;

    /**
     * Montant « 1 005 000.00 » (groupes de milliers séparés par espace, ou nombre simple) — jamais un compte nu.
     * ⚠️ Règle durcie (2026-07-18) — ancrage STRICT de la colonne montant : exactement 2 décimales
     * <strong>non suivies d'un chiffre</strong> ({@code (?!\d)} : « 11,700 » — kilométrage dans la désignation —
     * ne matche plus via « 11,70 »), et <strong>non suivies d'une unité de mesure ou d'un séparateur de
     * dimension</strong> ({@code Km/ml/m/m²/m³/m2/m3/metres/ha/x/×} : « 2,00 x 2,00 m » — dimensions d'un
     * dalot, « 6,00 metres » — largeur) : un tel nombre appartient à la désignation. La fin d'unité est testée
     * par {@code (?![\p{L}\p{N}])} et non {@code \b} — ² et ³ ne sont pas des caractères de mot, un {@code \b}
     * après eux ne matche jamais devant une espace.
     */
    private static final String MONTANT =
            "(?:\\d{1,3}(?:[\\s\\u00a0]\\d{3})*|\\d+)[.,]\\d{2}(?!\\d)"
                    + "(?![\\s\\u00a0]*(?i:km|ml|m[²³23]?|ha|metres?|x|×)(?![\\p{L}\\p{N}]))";
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
        // ⚠️ Règle ajoutée (2026-07-22) — compteur de revue : marchés portant ≥1 anomalie de transcription.
        int nbAVerifier = (int) marches.stream()
                .filter(m -> m.anomalies() != null && !m.anomalies().isEmpty()).count();
        return new SaisiePpmImportResult(exercice, dateSignature, autorite, idEntite, marches, avert, nbAVerifier);
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
            return nettoyerEncodage(t);
        } catch (IOException e) {
            throw new BadRequestException("PDF illisible ou invalide.");
        }
    }

    /**
     * ⚠️ Règle ajoutée (2026-07-22) — nettoyage d'encodage <strong>best-effort</strong>. Certains PPM ont une
     * {@code ToUnicode} défaillante : le caractère de remplacement « ¿ » ({@code U+00BF}) code selon le contexte
     * soit la ligature <strong>œ</strong> (« ¿uvre » = « œuvre »), soit une <strong>apostrophe</strong> d'élision
     * (« jusqu¿à » = « jusqu'à »). Un remplacement global aveugle serait faux ; on applique donc uniquement des
     * règles <strong>ancrées et non ambiguës</strong>. Tout « ¿ » résiduel est ensuite signalé
     * ({@link TypeAnomalie#ENCODAGE_SUSPECT}) par ligne — jamais deviné en silence.
     */
    private static String nettoyerEncodage(String t) {
        return t
                // Ligature œ (contextes non ambigus : ¿ suivi de uvre/il/ur/ud/uf).
                .replaceAll("\\u00bf(?=uvre|il|urs?\\b|ud\\b|ufs?\\b)", "œ")
                // Apostrophe d'élision (mots toujours élidés devant voyelle/h).
                .replaceAll("(?i)(jusqu|aujourd|lorsqu|puisqu|quelqu)\\u00bf", "$1'");
    }

    /** Vrai si un champ porte encore un caractère de remplacement (¿ / U+FFFD) après nettoyage. */
    private static boolean encodageSuspect(String s) {
        return s != null && (s.indexOf('¿') >= 0 || s.indexOf('�') >= 0);
    }

    /**
     * Objet se terminant par un <strong>préfixe de route sans numéro</strong> (RN/RNT/RNS/RNP/RNC/RIP/RR) →
     * objet probablement tronqué (le n° a été perdu). Après la garde d'invariant, un objet sain finit par le
     * n° (« … de la RNT 33 ») ; un « … de la RNT » nu reste donc suspect.
     */
    private static final Pattern FIN_ROUTE_SANS_NUM = Pattern.compile("(?i)\\b(RN|RNT|RNS|RNP|RNC|RIP|RR)\\s*$");

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

        // ⚠️ Règle durcie (2026-07-18) — ANCRAGE PAR VRAISEMBLANCE : chaque montant du core est une ancre
        // candidate (dans l'ordre) ; on retient la PREMIÈRE dont la structure aval est plausible (≥1 code
        // SOA ou un compte). Un nombre resté dans la désignation malgré le format strict (ex. montant-like
        // dans le texte) fait ainsi replier le découpage sur l'ancre suivante au lieu de produire des
        // colonnes fausses. Aucune ancre plausible → repli sur la première (comportement antérieur, les
        // avertissements existants — mode non résolu, alignement bénéficiaires — signalent la ligne).
        List<Integer> ancres = new ArrayList<>();
        Matcher fm = MONEY.matcher(core);
        while (fm.find()) {
            ancres.add(fm.start());
        }
        if (ancres.isEmpty()) {
            avert.add("Ligne « " + (natureLibelle == null ? "?" : natureLibelle) + " » sans montant — ignorée.");
            return null;
        }
        Structure s = null;
        for (int ancre : ancres) {
            Structure t = lireStructure(core, ancre);
            if (t.plausible()) {
                s = t;
                break;
            }
        }
        if (s == null) {
            s = lireStructure(core, ancres.get(0));
        }
        String designation = core.substring(0, s.ancre()).trim();
        // ⚠️ Règle ajoutée — allotissement décrit dans la désignation : extraction best-effort des lots.
        // ⚠️ Décision revisitée (2026-07-18) : la désignation reste INTÉGRALE, énumération des lots
        // comprise (« répartis en NN Lots : Lot 01 : … ») — le doublon texte/lots[] est accepté et voulu.
        List<LotImport> lots = extraireLots(designation, avert);
        BigDecimal montEstim = s.mtsTete().isEmpty() ? null : s.mtsTete().get(0);
        BigDecimal nouvMontEstim = s.mtsTete().size() >= 2 ? s.mtsTete().get(1) : null;
        String financement = s.financement();
        String modeLibelle = s.modeLibelle();

        List<BeneficiaireImport> benef = construireBeneficiaires(s.soas(), s.compte(), s.mtsBenef(), natureLibelle, avert);

        // ⚠️ Règle ajoutée (2026-07-22, généralisée) — GARDE D'INVARIANT SYMÉTRIQUE « tête == Σ bénéficiaires »,
        // par colonne numérique (ancien PUIS nouveau). Un fragment d'objet collé par PDFBox contamine soit le
        // montant de TÊTE (montEstim/nouvMontEstim — « 33 590 000 000 »), soit un montant BÉNÉFICIAIRE
        // (« 3 125 000 000 » pour un estimatif de 125 000 000). Dans les deux sens, retirer 1-3 chiffres de tête
        // du montant fautif rétablit l'égalité → on les RECOLLE à l'objet et on réaligne. Avant forme/lots.
        boolean montantCorrige = false;
        Reconciliation ra = reconcilier(montEstim, montantsBenef(benef, true));   // colonne « ancien »
        if (ra != null) {
            montEstim = ra.tete();
            benef = avecMontantsBenef(benef, ra.benefs(), true);
            designation = (designation + " " + ra.fragment()).trim();
            montantCorrige = true;
            avert.add("Objet « " + designation + " » : « " + ra.fragment()
                    + " » recollé à l'objet, colonne « montant estimatif » réalignée sur la somme des bénéficiaires.");
        }
        Reconciliation rn = reconcilier(nouvMontEstim, montantsBenef(benef, false));   // colonne « nouveau »
        if (rn != null) {
            nouvMontEstim = rn.tete();
            benef = avecMontantsBenef(benef, rn.benefs(), false);
            designation = (designation + " " + rn.fragment()).trim();
            montantCorrige = true;
            avert.add("Objet « " + designation + " » : « " + rn.fragment()
                    + " » recollé à l'objet, colonne « nouveau montant » réalignée sur la somme des bénéficiaires.");
        }

        List<PrevisionImport> prev = List.of(
                new PrevisionImport("LANCEMENT", prev3.size() > 0 ? isoDate(prev3.get(0)) : null),
                new PrevisionImport("OUVERTURE", prev3.size() > 1 ? isoDate(prev3.get(1)) : null),
                new PrevisionImport("ATTRIBUTION", prev3.size() > 2 ? isoDate(prev3.get(2)) : null));
        return assemblerMarche(natureLibelle, designation, montEstim, nouvMontEstim, financement,
                modeLibelle, benef, prev, lots, null, montantCorrige, avert);   // PDF : forme relevée dans l'objet
    }

    /**
     * ⚠️ Règle ajoutée (2026-07-22) — <strong>assemblage final</strong> d'un marché à partir de ses champs :
     * résolution des référentiels par libellé (normalisation ÉTENDUE {@link LibelleNormalisation} ; libellé
     * renvoyé = CANONIQUE du référentiel), forme du marché relevée dans l'objet, et anomalies structurées de
     * revue. <strong>PARTAGÉ</strong> par l'import PDF ({@link #parserRecord}) et l'import tableur
     * ({@code SaisiePpmXlsxImportService}) : seule l'<em>extraction amont</em> diffère (géométrie PDF vs
     * colonnes explicites du tableur), l'assemblage/résolution/anomalies est le même.
     */
    MarcheImport assemblerMarche(String natureLibelle, String designation, BigDecimal montEstim,
            BigDecimal nouvMontEstim, String financement, String modeLibelle, List<BeneficiaireImport> benef,
            List<PrevisionImport> prev, List<LotImport> lots, String formeExplicite, boolean montantCorrige,
            List<String> avert) {
        Nature natureRef = resoudreParLibelle(natureRepository.findAll(), Nature::getLibelle, natureLibelle);
        Integer idNature = natureRef == null ? null : natureRef.getIdNature();
        String natureCanon = natureLibelle;
        if (natureLibelle != null && natureRef == null) {
            avert.add("Nature « " + natureLibelle + " » non trouvée au référentiel — à confirmer.");
        } else if (natureRef != null) {
            natureCanon = natureRef.getLibelle();
        }
        ModePassation modeRef = resoudreParLibelle(modePassationRepository.findAll(),
                ModePassation::getLibelle, modeLibelle);
        Integer idMode = modeRef == null ? null : modeRef.getIdMode();
        String modeCanon = modeLibelle;
        if (modeLibelle != null && modeRef == null) {
            avert.add(avertissementModeNonResolu(modeLibelle));
        } else if (modeRef != null) {
            modeCanon = modeRef.getLibelle();
        }
        // Forme : explicite si fournie (tableur — validée, 400 si code inconnu), sinon relevée dans l'objet (PDF).
        String formeMarche = formeExplicite != null && !formeExplicite.isBlank()
                ? FormeMarche.depuisCodeOuDefaut(formeExplicite).name()
                : FormeMarche.detecterDansDesignation(designation).name();
        List<AnomalieTranscription> anomalies = detecterAnomalies(designation, montEstim, sommeAnc(benef),
                montantCorrige, natureCanon, idNature, modeCanon, idMode, benef, lots);
        return new MarcheImport(designation, formeMarche, montEstim, nouvMontEstim, idNature, natureCanon,
                idMode, modeCanon, financement, benef, prev, lots, anomalies);
    }

    /**
     * ⚠️ Règle ajoutée (2026-07-22) — construit les {@link AnomalieTranscription} d'un marché à partir de ses
     * valeurs finales (contrat de revue front, pointant champ + gravité). Types émis :
     * <ul>
     *   <li>{@code CHAMP_MANQUANT} — objet, montant ou mode absent ;</li>
     *   <li>{@code OBJET_TRONQUE_PROBABLE} — objet finissant par un préfixe de route sans numéro ;</li>
     *   <li>{@code ENCODAGE_SUSPECT} — caractère de remplacement subsistant dans l'objet ;</li>
     *   <li>{@code MONTANT_INCOHERENT} — {@code montEstim} ≠ Σ bénéficiaires (BLOQUANT si non auto-corrigé,
     *       sinon {@code corrige=true} A_VERIFIER) ;</li>
     *   <li>{@code REFERENTIEL_INCONNU} — nature / mode / SOA / compte non résolus.</li>
     * </ul>
     */
    private List<AnomalieTranscription> detecterAnomalies(String designation, BigDecimal montEstim,
            BigDecimal sommeAnc, boolean montantCorrige, String natureLibelle, Integer idNature,
            String modeLibelle, Integer idMode, List<BeneficiaireImport> benef, List<LotImport> lots) {
        List<AnomalieTranscription> a = new ArrayList<>();

        // Objet.
        if (designation == null || designation.isBlank()) {
            a.add(anomalie(ChampAnomalie.OBJET, TypeAnomalie.CHAMP_MANQUANT, GraviteAnomalie.BLOQUANT, null,
                    "Objet du marché manquant."));
        } else {
            Matcher route = FIN_ROUTE_SANS_NUM.matcher(designation);
            if (route.find()) {
                a.add(anomalie(ChampAnomalie.OBJET, TypeAnomalie.OBJET_TRONQUE_PROBABLE, GraviteAnomalie.A_VERIFIER,
                        null, "L'objet se termine par « " + route.group(1)
                                + " » sans numéro de route — objet probablement tronqué, à compléter."));
            }
            if (encodageSuspect(designation)) {
                a.add(anomalie(ChampAnomalie.OBJET, TypeAnomalie.ENCODAGE_SUSPECT, GraviteAnomalie.A_VERIFIER, null,
                        "Caractère de remplacement (« ¿ ») dans l'objet — transcription à vérifier."));
            }
        }

        // Montant estimatif + invariant montEstim == Σ bénéficiaires.
        if (montEstim == null) {
            a.add(anomalie(ChampAnomalie.MONT_ESTIM, TypeAnomalie.CHAMP_MANQUANT, GraviteAnomalie.BLOQUANT, null,
                    "Montant estimatif absent."));
        } else if (montantCorrige) {
            a.add(anomalie(ChampAnomalie.MONT_ESTIM, TypeAnomalie.MONTANT_INCOHERENT, GraviteAnomalie.A_VERIFIER,
                    Boolean.TRUE, "Montant réaligné sur la somme des bénéficiaires ("
                            + sommeAnc.toPlainString() + ") après recollage d'un fragment d'objet — à confirmer."));
        } else if (sommeAnc.signum() > 0 && montEstim.compareTo(sommeAnc) != 0) {
            a.add(anomalie(ChampAnomalie.MONT_ESTIM, TypeAnomalie.MONTANT_INCOHERENT, GraviteAnomalie.BLOQUANT,
                    Boolean.FALSE, "Montant estimatif (" + montEstim.toPlainString()
                            + ") ≠ somme des bénéficiaires (" + sommeAnc.toPlainString()
                            + ") — incohérence non auto-corrigeable, à reprendre."));
        }

        // Mode.
        if (modeLibelle == null || modeLibelle.isBlank()) {
            a.add(anomalie(ChampAnomalie.MODE, TypeAnomalie.CHAMP_MANQUANT, GraviteAnomalie.A_VERIFIER, null,
                    "Mode de passation absent."));
        } else if (idMode == null) {
            a.add(anomalie(ChampAnomalie.MODE, TypeAnomalie.REFERENTIEL_INCONNU, GraviteAnomalie.A_VERIFIER, null,
                    "Mode de passation « " + modeLibelle + " » non trouvé au référentiel."));
        }

        // Nature.
        if (natureLibelle != null && idNature == null) {
            a.add(anomalie(ChampAnomalie.NATURE, TypeAnomalie.REFERENTIEL_INCONNU, GraviteAnomalie.A_VERIFIER, null,
                    "Nature « " + natureLibelle + " » non trouvée au référentiel."));
        }

        // Bénéficiaires : SOA / compte non résolus.
        benef.stream().map(BeneficiaireImport::soaCode).filter(Objects::nonNull).distinct()
                .filter(soa -> !soaBeneficiaireRepository.existsById(soa))
                .forEach(soa -> a.add(anomalie(ChampAnomalie.BENEFICIAIRE, TypeAnomalie.REFERENTIEL_INCONNU,
                        GraviteAnomalie.A_VERIFIER, null, "Service bénéficiaire (SOA) « " + soa + " » inconnu.")));
        benef.stream().map(BeneficiaireImport::numCompte).filter(Objects::nonNull).distinct()
                .filter(cpt -> !compteRepository.existsById(cpt))
                .forEach(cpt -> a.add(anomalie(ChampAnomalie.BENEFICIAIRE, TypeAnomalie.REFERENTIEL_INCONNU,
                        GraviteAnomalie.A_VERIFIER, null, "Compte « " + cpt + " » inconnu.")));

        // Lots : signal d'allotissement présent (annonce ou ≥2 marqueurs « Lot NN : ») mais rien d'extrait
        // → à réviser (le front consomme cette anomalie au lieu de sa propre heuristique).
        if ((lots == null || lots.isEmpty()) && signalLot(designation)) {
            a.add(anomalie(ChampAnomalie.LOT, TypeAnomalie.LOT_INCOHERENT, GraviteAnomalie.A_VERIFIER, null,
                    "Allotissement décrit dans l'objet mais lots non extraits (motif ambigu) — à saisir manuellement."));
        }
        return a;
    }

    /** Vrai si la désignation porte un signal d'allotissement : annonce « répartis en … lots » ou ≥2 « Lot NN : ». */
    private static boolean signalLot(String designation) {
        if (designation == null) {
            return false;
        }
        if (MARQUEUR_LOTS.matcher(designation).find()) {
            return true;
        }
        Matcher m = SEGMENT_LOT.matcher(designation);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n >= 2;
    }

    private static AnomalieTranscription anomalie(ChampAnomalie champ, TypeAnomalie type, GraviteAnomalie gravite,
            Boolean corrige, String message) {
        return new AnomalieTranscription(champ, type, gravite, corrige, message);
    }

    /**
     * Bloc structurel lu depuis une ancre montant : montants de tête, mode + financement, SOA, compte,
     * montants bénéficiaires. {@link #plausible()} = la ligne « ressemble » à une vraie ligne de tableau
     * (au moins un code SOA ou un compte) — critère du repli d'ancre.
     */
    private record Structure(int ancre, List<BigDecimal> mtsTete, String modeLibelle, String financement,
            List<String> soas, String compte, List<BigDecimal> mtsBenef) {

        boolean plausible() {
            return !soas.isEmpty() || compte != null;
        }
    }

    /** Lit le bloc structurel du core à partir d'une ancre montant (walk positionnel des jetons typés). */
    private Structure lireStructure(String core, int ancre) {
        List<String[]> toks = tokeniser(core.substring(ancre));
        int i = 0;
        List<BigDecimal> mtsTete = new ArrayList<>();
        while (i < toks.size() && "money".equals(toks.get(i)[0])) {
            mtsTete.add(parseMontant(toks.get(i)[1]));
            i++;
        }
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
        return new Structure(ancre, mtsTete, modeLibelle, financement, soas, compte, mtsBenef);
    }

    /**
     * ⚠️ Règle ajoutée — avertissement de mode non résolu, enrichi d'une <strong>suggestion</strong> quand un
     * libellé du référentiel est proche (Levenshtein 1..{@value #SEUIL_SUGGESTION} sur formes normalisées).
     * La suggestion n'auto-résout jamais (pas de fuzzy silencieux) : elle guide la PRMP.
     */
    private String avertissementModeNonResolu(String modeLibelle) {
        String cible = LibelleNormalisation.normaliser(modeLibelle);
        String suggestion = null;
        int meilleure = Integer.MAX_VALUE;
        for (ModePassation m : modePassationRepository.findAll()) {
            if (m.getLibelle() == null) {
                continue;
            }
            int d = LibelleNormalisation.distance(cible, LibelleNormalisation.normaliser(m.getLibelle()));
            if (d > 0 && d <= SEUIL_SUGGESTION && d < meilleure) {
                meilleure = d;
                suggestion = m.getLibelle();
            }
        }
        return suggestion != null
                ? "Mode de passation « " + modeLibelle + " » non trouvé au référentiel — vouliez-vous dire « "
                        + suggestion + " » ?"
                : "Mode de passation « " + modeLibelle + " » non trouvé au référentiel — à confirmer.";
    }

    /**
     * ⚠️ Règle ajoutée — extraction <strong>best-effort</strong> des lots décrits en texte libre dans la
     * désignation du marché. <strong>Généralisée 2026-07-22 (SIGMP)</strong> pour couvrir :
     * <ul>
     *   <li>marqueurs variés — « Lot 01 : », « lot n1: », « LOT N°02 : », « Lot 1 - » ({@link #SEGMENT_LOT}) ;</li>
     *   <li>annonce en lettres et/ou chiffres — « répartie en trois 3 lots », « en deux 2 lots » ;</li>
     *   <li><strong>cas sans annonce</strong> — au moins <strong>2</strong> marqueurs « LOT N°NN : » suffisent.</li>
     * </ul>
     *
     * <p><strong>Contrôle de cohérence</strong> : l'extraction n'aboutit que si (a) aucun segment n'est vide et
     * (b) — s'il y a une annonce — le nombre de marqueurs égale le compte annoncé ; sans annonce, il faut ≥2
     * marqueurs. <strong>Aucune exigence d'objet avant le 1ᵉʳ marqueur</strong> (une désignation qui commence par
     * « LOT N°01: » est valide). Sinon <strong>avertissement</strong> + lots vides, et une <strong>anomalie
     * {@code champ:lot}</strong> ({@code LOT_INCOHERENT}) est émise par {@link #detecterAnomalies}. Chaque lot ne
     * porte que {@code designationLot} (aucun montant/quantité).</p>
     *
     * <p>⚠️ Décision (2026-07-18) : la désignation reste <strong>intégrale</strong> (l'énumération des lots y
     * demeure, en plus de {@code lots[]}). Renvoie une liste vide si rien n'est extrait.</p>
     */
    private List<LotImport> extraireLots(String designation, List<String> avert) {
        // 1) Marqueurs de lot sur toute la désignation (le cas sans annonce n'a pas de préambule à retirer).
        List<Integer> debuts = new ArrayList<>();
        List<Integer> finsEntete = new ArrayList<>();
        Matcher seg = SEGMENT_LOT.matcher(designation);
        while (seg.find()) {
            debuts.add(seg.start());
            finsEntete.add(seg.end());
        }
        if (debuts.isEmpty()) {
            return List.of();   // aucun marqueur → pas d'allotissement décrit
        }
        // 2) Annonce éventuelle AVANT le 1er marqueur (compte en chiffres sinon en lettres).
        Integer annonce = null;
        Matcher ann = MARQUEUR_LOTS.matcher(designation.substring(0, debuts.get(0)));
        if (ann.find()) {
            annonce = ann.group(2) != null ? Integer.valueOf(ann.group(2))
                    : (ann.group(1) == null ? null : MOTS_NOMBRE.get(ann.group(1).toLowerCase(Locale.FRENCH)));
        }
        // 3) Segments (texte entre marqueurs), séparateurs de fin « ; . , - – — » retirés.
        List<LotImport> lots = new ArrayList<>();
        for (int k = 0; k < debuts.size(); k++) {
            int finTexte = k + 1 < debuts.size() ? debuts.get(k + 1) : designation.length();
            String texte = designation.substring(finsEntete.get(k), finTexte)
                    .replaceAll("[\\s;.,\\-\\u2013\\u2014]+$", "").trim();
            if (!texte.isEmpty()) {
                lots.add(new LotImport(texte, null, null, null));
            }
        }
        // 4) Garde de cohérence : aucun segment vide, compte concordant (ou ≥2 marqueurs si sans annonce).
        // ⚠️ 2026-07-22 (L18 SIGMP) — PLUS d'exigence d'objet AVANT le 1ᵉʳ marqueur : une désignation qui
        // COMMENCE par « LOT N°01: » (aucun préambule, marqueur en position 0) reste un allotissement valide
        // — la désignation demeure intégrale (décision 2026-07-18), chaque marqueur ouvre un segment.
        int attendu = annonce != null ? annonce : debuts.size();
        boolean coherent = lots.size() == debuts.size()          // aucun segment vide
                && lots.size() == attendu
                && (annonce != null || debuts.size() >= 2);      // sans annonce : ≥2 marqueurs
        if (!coherent) {
            avert.add(annonce != null
                    ? "Allotissement détecté (« " + annonce + " lot(s) » annoncé(s)) mais " + lots.size()
                            + " segment(s) « Lot NN : » exploitable(s) — lots non extraits."
                    : "Marqueurs de lot détectés mais extraction ambiguë — lots non extraits, à vérifier.");
            return List.of();
        }
        return List.copyOf(lots);
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

    /** Σ des montants « ancien » par bénéficiaire (invariant document : {@code montEstim == Σ ancMontBenef}). */
    private static BigDecimal sommeAnc(List<BeneficiaireImport> benef) {
        return benef.stream().map(BeneficiaireImport::ancMontBenef).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Résultat d'une réconciliation de colonne : tête + montants bénéficiaires corrigés, fragment recollé. */
    private record Reconciliation(BigDecimal tete, List<BigDecimal> benefs, String fragment) {
    }

    /**
     * ⚠️ Règle ajoutée (2026-07-22, symétrique) — rétablit l'invariant <strong>tête == Σ bénéficiaires</strong>
     * d'une colonne numérique (ancien ou nouveau) quand un <strong>fragment d'objet</strong> (1-3 chiffres) a
     * été collé en tête d'un montant par PDFBox. Deux sens :
     * <ul>
     *   <li><strong>tête contaminée</strong> — {@code tête == <fragment> ++ Σ} (« 33 590 000 000 » pour Σ
     *       590 000 000) → on ramène la tête à Σ ;</li>
     *   <li><strong>bénéficiaire contaminé</strong> — un {@code b_i == <fragment> ++ cible} où
     *       {@code cible = tête − Σ + b_i} (« 3 125 000 000 » pour un estimatif de 125 000 000) → on ramène
     *       {@code b_i} à sa cible.</li>
     * </ul>
     * Renvoie {@code null} si l'écart n'est pas un pur fragment de tête (autre incohérence → reste signalée).
     * Comparaison sur les entiers à 2 décimales ({@code ×100}), suffixe commun + préfixe 1-3 chiffres.
     */
    private static Reconciliation reconcilier(BigDecimal tete, List<BigDecimal> benefs) {
        if (tete == null) {
            return null;
        }
        BigDecimal somme = benefs.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (somme.signum() == 0 || tete.compareTo(somme) == 0) {
            return null;
        }
        // Cas A — tête contaminée : tête = <fragment> ++ Σ.
        String fa = fragmentPrefixe(tete, somme);
        if (fa != null) {
            return new Reconciliation(somme, benefs, fa);
        }
        // Cas B — un montant bénéficiaire contaminé : b_i = <fragment> ++ (tête − Σ + b_i).
        for (int i = 0; i < benefs.size(); i++) {
            BigDecimal bi = benefs.get(i);
            if (bi == null) {
                continue;
            }
            BigDecimal cible = tete.subtract(somme).add(bi);   // valeur de b_i pour que Σ == tête
            if (cible.signum() <= 0) {
                continue;
            }
            String fb = fragmentPrefixe(bi, cible);
            if (fb != null) {
                List<BigDecimal> corr = new ArrayList<>(benefs);
                corr.set(i, cible);
                return new Reconciliation(tete, corr, fb);
            }
        }
        return null;
    }

    /** Préfixe de tête (1-3 chiffres) si {@code gros == <fragment> ++ petit} (mêmes décimales) ; sinon {@code null}. */
    private static String fragmentPrefixe(BigDecimal gros, BigDecimal petit) {
        String g = gros.movePointRight(2).toBigInteger().toString();
        String p = petit.movePointRight(2).toBigInteger().toString();
        if (g.length() <= p.length() || !g.endsWith(p)) {
            return null;
        }
        String frag = g.substring(0, g.length() - p.length());
        return frag.length() >= 1 && frag.length() <= 3 ? frag : null;
    }

    /** Montants d'une colonne des bénéficiaires (ancien si {@code anc}, sinon nouveau). */
    private static List<BigDecimal> montantsBenef(List<BeneficiaireImport> benef, boolean anc) {
        List<BigDecimal> out = new ArrayList<>();
        for (BeneficiaireImport b : benef) {
            out.add(anc ? b.ancMontBenef() : b.nouvMontBenef());
        }
        return out;
    }

    /** Reconstruit les bénéficiaires en remplaçant la colonne (ancien si {@code anc}, sinon nouveau). */
    private static List<BeneficiaireImport> avecMontantsBenef(List<BeneficiaireImport> benef,
            List<BigDecimal> montants, boolean anc) {
        List<BeneficiaireImport> out = new ArrayList<>();
        for (int i = 0; i < benef.size(); i++) {
            BeneficiaireImport b = benef.get(i);
            out.add(anc ? new BeneficiaireImport(b.soaCode(), b.numCompte(), montants.get(i), b.nouvMontBenef())
                    : new BeneficiaireImport(b.soaCode(), b.numCompte(), b.ancMontBenef(), montants.get(i)));
        }
        return out;
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

    /** Résout un élément de référentiel par libellé normalisé (source unique {@link LibelleNormalisation}). */
    private <T> T resoudreParLibelle(List<T> refs, java.util.function.Function<T, String> libelle, String cible) {
        if (cible == null || cible.isBlank()) {
            return null;
        }
        String c = LibelleNormalisation.normaliser(cible);
        return refs.stream()
                .filter(x -> libelle.apply(x) != null
                        && LibelleNormalisation.normaliser(libelle.apply(x)).equals(c))
                .findFirst().orElse(null);
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
        return LibelleNormalisation.normaliser(s);
    }

    private static String sansAccents(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
