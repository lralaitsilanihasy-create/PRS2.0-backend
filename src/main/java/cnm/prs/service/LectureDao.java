package cnm.prs.service;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ⚠️ <strong>Import du DAO</strong> (demande front du 2026-09-28, §B1 ; ADR-0012) — la lecture « par modèle inversé » :
 * un DAO rédigé sur le document type se lit à l'envers. Le texte fixe du modèle du lot D se retrouve dans le document, ce
 * qui occupe la place d'un jeton est la valeur, et la rédaction retenue d'une section conditionnelle dit la réponse.
 *
 * <p>Portage <strong>tel quel</strong> de la spécification du front ({@code frontendprs2/scripts/import-dao/lire.mjs},
 * README §L'algorithme), mesurée sur 9 passes (0 valeur fausse en confiance haute) : mêmes étapes, mêmes seuils (fenêtre
 * de 60 paragraphes, débuts de 6 caractères cherchés dans les 40 unités suivantes, 12 caractères pour « ressemble au
 * modèle », intervalle de 6 paragraphes au plus), mêmes niveaux de confiance. Classe pure : ni base, ni Spring — elle
 * PROPOSE, l'appelant ({@link ImportDaoService}) filtre et valide.</p>
 *
 * <p>Deux écarts assumés au portage, tous deux côté serveur : les réponses déduites se lisent avec le découpage de
 * {@link ConditionsModele#implications} (un « et » dans une valeur ne coupe pas), et une réponse déduite dont la clé est un
 * <em>code de champ</em> ({@code B07-FS-01 = …}) est rendue à part ({@link Resultat#reponsesChamps}) : ce n'est pas une
 * clé de cadrage, l'appelant la propose comme valeur de champ.</p>
 */
public final class LectureDao {

    /** Les blancs de JavaScript ({@code \s}), que {@code \s} de Java ne couvre pas (espaces insécables, U+2028…). */
    private static final String BLANC = "[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]";
    private static final String BLANCS_SOUPLES = BLANC + "*";
    private static final Pattern BLANCS = Pattern.compile(BLANC + "+");
    private static final Pattern JETON = Pattern.compile("\\{\\{([^{}]+)}}");
    private static final Pattern MARQUEUR = Pattern.compile("^\\{\\{(SI|FINSI):([A-Z0-9-]+)}}$");
    private static final Pattern LOT_ENUMERE = Pattern.compile("lot\\s*n\\s*°?\\s*(\\d+)\\s*:\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SI = Pattern.compile("^\\{\\{SI:([A-Z0-9-]+)}}$");
    private static final Pattern FINSI = Pattern.compile("^\\{\\{FINSI:([A-Z0-9-]+)}}$");
    private static final Pattern POINTILLES = Pattern.compile("^[.…_" + BLANC.substring(1, BLANC.length() - 1) + "]*$");
    private static final Pattern CODE = Pattern.compile("^B\\d\\d-.*");
    private static final Pattern CODE_CHAMP = Pattern.compile("B\\d{2}-[A-Z0-9]{1,6}-\\d{2}");
    private static final Pattern DATE = Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{4})$");
    private static final Pattern DATE_HEURE = Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{4})" + BLANC + "+(\\d{2}):(\\d{2})$");
    private static final Pattern UNITE_NOMBRE = Pattern.compile("ariary|ar\\.?|%|" + BLANC, Pattern.CASE_INSENSITIVE);
    private static final Pattern NOMBRE = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    /** ⚠️ 2026-10-02 (règle 7) — un trou laissé au candidat dans le texte du modèle : « <nom du Titulaire> ». */
    private static final Pattern TROU = Pattern.compile("<[^<>]*>");
    /** ⚠️ 2026-10-01 (front eed6bc4) — des pointillés (deux signes au moins) : une case peut-être laissée en blanc. */
    private static final Pattern DEUX_POINTILLES = Pattern.compile("[.…_]{2,}");
    /** ⚠️ 2026-10-01 — les caractères d'usage privé (glyphes de police Symbol : l'astérisque de renvoi « ….* »). */
    private static final Pattern USAGE_PRIVE = Pattern.compile("[\\uE000-\\uF8FF]");
    /** ⚠️ 2026-10-01 — une unité seule, qui accompagne une case en blanc (« ........ Jours …. »). */
    private static final Pattern UNITE_SEULE = Pattern.compile(
            "(?<![\\p{L}])(?:jours?|mois|ans?|ann[ée]es?|heures?|semaines?|ariary|ar|%)(?![\\p{L}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /**
     * ⚠️ 2026-10-01 (front eed6bc4, règle « MOTS (n) ») — « CENT VINGT (120) », « Cinq (05) », « neuf cent mille Ariary
     * (Ar 9 900 000) » : les chiffres entre parenthèses font foi, si rien d'autre que des lettres ne les précède.
     */
    private static final Pattern MOTS_PUIS_CHIFFRES = Pattern.compile("^[\\p{L}" + BLANC.substring(1, BLANC.length() - 1)
            + "'’.-]+\\(" + BLANC + "*((?:ar\\.?" + BLANC + "*)?[\\d" + BLANC.substring(1, BLANC.length() - 1) + ".,]+(?:" + BLANC
            + "*(?:ariary|ar\\.?|%))?)" + BLANC + "*\\)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** ⚠️ 2026-10-01 — un texte qui finit par du texte fixe puis un point (« … jours. ») : ce point devient facultatif. */
    private static final Pattern POINT_FINAL_FIXE = Pattern.compile("[^" + BLANC.substring(1, BLANC.length() - 1) + "}]"
            + BLANC + "*\\." + BLANC + "*$");
    private static final String PONCTUATION = ",;:.!?()[]\"'-/";
    private static final String META = ".*+?^${}()|[]\\";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** Fenêtre de recherche après le dernier paragraphe reconnu. */
    static final int FENETRE = 60;
    /** ⚠️ 2026-10-02 (règle 8) — paragraphes distinctifs manqués d'affilée avant de chercher dans tout le reste du document. */
    static final int REANCRAGE = 5;
    /**
     * ⚠️ Lot D4 (2026-09-30, règle R-a du front) — fenêtre d'un paragraphe dont le texte fixe se répète ailleurs dans le
     * modèle (« Non applicable ») : absent du document, il se raccrochait à la répétition d'un article plus loin, et la
     * lecture sautait tout ce qui les séparait (CCAP-T : les assurances de l'article 8 perdues).
     */
    static final int FENETRE_REPETE = 3;
    /** Unités suivantes où chercher un début de paragraphe collé. */
    static final int PORTEE_COUPE = 40;
    /** Longueur minimale d'un début de paragraphe cherché dans une valeur. */
    static final int DEBUT_MIN = 6;
    /** Longueur minimale d'un début pour juger qu'un intervalle « ressemble au modèle ». */
    static final int DEBUT_RESSEMBLANCE = 12;
    /** Un intervalle plus long n'est pas lu. */
    static final int INTERVALLE_MAX = 6;
    /** ⚠️ B5 règle 2 (2026-09-29) — lettres de texte fixe en deçà desquelles une ancre ne donne jamais la confiance haute. */
    static final int ANCRE_HAUTE = 8;
    /** ⚠️ B5 règle 3 (2026-09-29) — lettres de texte fixe qu'il faut à un paragraphe pour attester ses sections. */
    static final int LETTRES_ATTESTATION = 20;

    public enum Confiance {
        BASSE(1), MOYENNE(2), HAUTE(3);

        final int rang;

        Confiance(int rang) {
            this.rang = rang;
        }

        /** La forme servie ({@code haute}, {@code moyenne}, {@code basse}). */
        public String libelle() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Ce que l'appelant sait d'un champ : son type, sa source, sa clé de cadrage (reflet), et ⚠️ 2026-10-01 ses options
     * (une liste ou une liste à choix multiples : les réponses déduites d'un terme {@code contient}).
     */
    public record InfoChamp(String type, String source, String cleCadrage, List<String> options) {
        public InfoChamp(String type, String source, String cleCadrage) {
            this(type, source, cleCadrage, List.of());
        }
    }

    /** Un paragraphe du modèle, avec la pile des sections conditionnelles qui l'entourent. */
    record Unite(String texte, List<String> sections) {
    }

    /** Une valeur lue, dans la forme de saisie du champ. */
    public record Proposition(String code, String valeur, String brut, Confiance confiance, String extrait) {
    }

    /** Plusieurs jetons seuls dans le même intervalle : signalé, jamais choisi. */
    public record Ambigu(List<String> candidats, String texte) {
    }

    /** Un champ (ou une clé de cadrage) lu deux fois différemment. */
    public record Conflit(String code, List<String> valeurs) {
    }

    /** Une réponse de cadrage déduite, avec la section qui la dit ({@code null} : lue sur un reflet). */
    public record Reponse(String cle, String valeur, String section) {
    }

    /** Le résultat de la lecture d'un modèle. */
    public record Resultat(String sigle, int unites, int reconnues, List<Proposition> propositions,
            List<Reponse> cadrage, List<Reponse> reponsesChamps, List<Ambigu> ambigus, List<Conflit> conflits,
            List<String> nonTrouves) {
    }

    private LectureDao() {
    }

    // ------------------------------------------------------------------ normalisation

    /** Apostrophes, tirets, guillemets, espaces insécables, blancs : ce qui varie d'un traitement de texte à l'autre. */
    public static String norm(String s) {
        if (s == null) {
            return "";
        }
        return trim(BLANCS.matcher(normSansBlancs(s)).replaceAll(" "));
    }

    /** {@link #norm} sans le resserrement des blancs : ce qui s'applique aussi à un seul graphème (§B6.3). */
    private static String normSansBlancs(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKC)
                .replaceAll("[’ʼ‘`]", "'")
                .replaceAll("[‐‑‒–—]", "-")
                .replaceAll("[«»]", "\"")
                .replace(' ', ' ').replace(' ', ' ');
    }

    private static String trim(String s) {
        return s.replaceAll("^" + BLANC + "+|" + BLANC + "+$", "");
    }

    /**
     * ⚠️ Lot D4 (2026-09-30, §B6.3) — les mêmes unités que {@link #unitesDocument}, mais TELLES QU'ÉCRITES (non normalisées ;
     * celles qui seraient vides une fois normalisées sont écartées, comme là) : une valeur de texte y est reprise.
     */
    public static List<String> unitesDocumentOrigine(List<String> lignes) {
        List<String> out = new ArrayList<>();
        for (String ligne : lignes) {
            for (String l : ligne.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
                for (String c : l.split("[\t\u001E]", -1)) {
                    if (!norm(c).isEmpty()) {
                        out.add(c);
                    }
                }
            }
        }
        return out;
    }

    /** Les paragraphes du document, prêts à lire : chaque ligne et chaque cellule est une unité, normalisée, non vide. */
    public static List<String> unitesDocument(List<String> lignes) {
        List<String> out = new ArrayList<>();
        for (String ligne : lignes) {
            for (String l : ligne.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
                for (String c : l.split("[\t\u001E]", -1)) {
                    String n = norm(c);
                    if (!n.isEmpty()) {
                        out.add(n);
                    }
                }
            }
        }
        return out;
    }

    /** Un texte fixe en motif tolérant : blancs souples, espace facultative autour de la ponctuation. */
    static String motif(String fixe) {
        StringBuilder sb = new StringBuilder();
        String n = norm(fixe);
        for (int i = 0; i < n.length(); ) {
            int cp = n.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == ' ') {
                blancs(sb);
            } else if (cp < 128 && PONCTUATION.indexOf(cp) >= 0) {
                blancs(sb);
                sb.append(echappe(cp));
                blancs(sb);
            } else {
                sb.append(echappe(cp));
            }
        }
        return sb.toString();
    }

    /** Un seul {@code \s*} de suite (le {@code replace(/(\\s\*)+/g, '\\s*')} de la spécification). */
    private static void blancs(StringBuilder sb) {
        if (sb.length() < BLANCS_SOUPLES.length()
                || !sb.substring(sb.length() - BLANCS_SOUPLES.length()).equals(BLANCS_SOUPLES)) {
            sb.append(BLANCS_SOUPLES);
        }
    }

    private static String echappe(int cp) {
        String c = new String(Character.toChars(cp));
        return cp < 128 && META.indexOf(cp) >= 0 ? "\\" + c : c;
    }

    // ------------------------------------------------------------------ le modèle

    /** Les paragraphes du modèle (cellules comprises), chacun avec sa pile de sections ; titre, vides et marqueurs exclus. */
    static List<Unite> unites(List<DocumentLibre.Element> elements) {
        Deque<String> pile = new ArrayDeque<>();
        List<Unite> out = new ArrayList<>();
        for (DocumentLibre.Element e : elements) {
            if (e instanceof DocumentLibre.Paragraphe p) {
                Matcher si = SI.matcher(p.texte());
                Matcher fin = FINSI.matcher(p.texte());
                if (si.matches()) {
                    pile.addLast(si.group(1));
                    continue;
                }
                if (fin.matches()) {
                    pile.pollLast();
                    continue;
                }
                // le titre du document est porté à part par le modèle du front (pas un bloc) ; un VIDE n'a pas de texte.
                // ⚠️ 2026-10-02 — seulement la ligne TITRE EN TÊTE du modèle : un TITRE dans le corps (avis, lettre d'invitation,
                // depuis que leur titre est descendu sous l'en-tête) est un bloc du front comme un autre, donc une unité.
                boolean titreDuDocument = p.style() == DocumentLibre.Style.TITRE && e == elements.get(0);
                if (titreDuDocument || p.style() == DocumentLibre.Style.VIDE || p.texte().isBlank()) {
                    continue;
                }
                out.add(new Unite(p.texte(), List.copyOf(pile)));
            } else if (e instanceof DocumentLibre.Tableau t) {
                // ⚠️ Lot D2 (2026-09-29) — une rangée-marqueur (première cellule {{SI:X}} / {{FINSI:X}}, les autres vides) ouvre
                // ou ferme une section de rangées ; un paragraphe de cellule qui n'est que le marqueur, une section interne à
                // la cellule. Chaque paragraphe de cellule est une unité (le DPAO enchaîne des rédactions dans une cellule).
                for (List<List<String>> ligne : t.lignes()) {
                    Matcher rangee = marqueurDeRangee(ligne);
                    if (rangee != null) {
                        if ("SI".equals(rangee.group(1))) {
                            pile.addLast(rangee.group(2));
                        } else {
                            pile.pollLast();
                        }
                        continue;
                    }
                    for (List<String> cellule : ligne) {
                        Deque<String> locale = new ArrayDeque<>();
                        for (String texte : cellule) {
                            Matcher mc = MARQUEUR.matcher(texte.trim());
                            if (mc.matches()) {
                                if ("SI".equals(mc.group(1))) {
                                    locale.addLast(mc.group(2));
                                } else {
                                    locale.pollLast();
                                }
                                continue;
                            }
                            if (!texte.isBlank()) {
                                List<String> sections = new ArrayList<>(pile);
                                sections.addAll(locale);
                                out.add(new Unite(texte, List.copyOf(sections)));
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Même règle qu'au rendu : première cellule exactement le marqueur, les autres vides. */
    private static Matcher marqueurDeRangee(List<List<String>> ligne) {
        if (ligne.isEmpty() || ligne.get(0).isEmpty()) {
            return null;
        }
        Matcher m = MARQUEUR.matcher(String.join("\u001E", ligne.get(0)).trim());
        if (!m.matches()) {
            return null;
        }
        for (int c = 1; c < ligne.size(); c++) {
            for (String p : ligne.get(c)) {
                if (!p.isBlank()) {
                    return null;
                }
            }
        }
        return m;
    }

    /** Les lettres du texte fixe d'un paragraphe du modèle (jetons retirés). */
    static int lettresFixes(String texte) {
        String t = Normalizer.normalize(JETON.matcher(texte).replaceAll(" "), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        int n = 0;
        for (int i = 0; i < t.length(); ) {
            int cp = t.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetter(cp)) {
                n++;
            }
        }
        return n;
    }

    /**
     * ⚠️ B5 règle 1 (2026-09-29) — un paragraphe du modèle dont le texte fixe n'a AUCUNE lettre (« {{B05-MO-02}}. ») est un
     * jeton seul, lu entre ses voisins : en motif, il reconnaîtrait presque tout paragraphe finissant par un point.
     */
    private static boolean seulJeton(Unite u) {
        return u.texte().contains("{{") && lettresFixes(u.texte()) == 0;
    }

    /**
     * ⚠️ Lot D4 (2026-09-30, §B6.2) — ce qui ne dépend que du modèle, calculé <strong>une fois par lecture</strong>. Chaque
     * propriété comparait le paragraphe à tous les autres en renormalisant leur texte, et « jumeau » était réévalué pour
     * chaque paragraphe du document : un coût cubique (13,8 s pour lire le CCAP-T dans un DPAO de 122 paragraphes, 53 s
     * à l'écran). Le front a fait de même dans {@code lire.mjs} (textes répétés comptés une fois par modèle).
     */
    private static final class Profil {
        /** ⚠️ Lot D4 (R-a) — un autre paragraphe du modèle a le même texte fixe (avec ou sans jeton). */
        final boolean[] repete;
        /** ⚠️ Lot D3 (§B4.1) — un paragraphe à jeton dont un autre paragraphe du modèle a le même texte fixe. */
        final boolean[] jumeau;
        /**
         * ⚠️ B5 règle 3 (2026-09-29) — un paragraphe reconnu n'atteste ses sections que s'il a au moins 20 lettres de texte
         * fixe et qu'aucun paragraphe de même texte n'existe hors de ces sections : un libellé présent dans plusieurs
         * rédactions (« forfaitaire », « importées », « groupement autorisé ») ne dit pas laquelle a été retenue.
         */
        final boolean[] distinctif;
        /** Lettres de texte fixe de chaque paragraphe. */
        final int[] lettres;
        /**
         * ⚠️ 2026-10-01 (règle R-c de {@code lire.mjs}, d718cae ; DPAC-CC, montant du DAO par lot) — les VARIANTES PLUS
         * CONTRAINTES de chaque paragraphe : parmi les {@value #VOISINAGE_VARIANTE} paragraphes qui le suivent dans le
         * modèle, ceux qui ne sont pas un jeton seul, sont rattachés à d'autres sections (liste différente) et ont plus de
         * texte fixe. « … de {{B04-DS-05.parLot}} libellé… » (alloti) reconnaît aussi « … de cinquante mille ariary (50 000
         * Ariary) libellé… », que sa variante « … de {{B04-DS-05.lettres}} ({{B04-DS-05}}) libellé… » (non alloti) décrit
         * mieux : sans cette règle, la première prenait le paragraphe et attestait alloti = OUI contre le reste du document.
         */
        final int[][] variantes;

        Profil(List<Unite> us) {
            int n = us.size();
            String[] cle = new String[n];
            Map<String, Integer> occurrences = new java.util.HashMap<>();
            Map<String, Set<String>> sectionsParCle = new java.util.HashMap<>();
            for (int k = 0; k < n; k++) {
                cle[k] = cleTexte(us.get(k));
                occurrences.merge(cle[k], 1, Integer::sum);
                sectionsParCle.computeIfAbsent(cle[k], x -> new java.util.HashSet<>()).add(String.join("|", us.get(k).sections()));
            }
            repete = new boolean[n];
            jumeau = new boolean[n];
            distinctif = new boolean[n];
            lettres = new int[n];
            for (int k = 0; k < n; k++) {
                Unite u = us.get(k);
                lettres[k] = lettresFixes(u.texte());
                repete[k] = occurrences.get(cle[k]) > 1;
                jumeau[k] = repete[k] && u.texte().contains("{{");
                // ⚠️ 2026-10-02 (règle 7, front ec4ad37) — les lettres d'un trou laissé au candidat (« <nom du Titulaire> ») ne
                // comptent pas : sous bruit, « ATTENDU QUE » + « <nom du Titulaire> » fusionnés (annexe de restitution d'avance)
                // redonnaient l'en-tête de l'annexe de bonne exécution et attestaient B05-GE-01 = OUI. La confiance, elle, compte
                // toujours toutes les lettres fixes.
                distinctif[k] = !u.sections().isEmpty() && lettresFixes(TROU.matcher(JETON.matcher(u.texte()).replaceAll(" ")).replaceAll(" ")) >= LETTRES_ATTESTATION
                        && sectionsParCle.get(cle[k]).size() == 1;
            }
            variantes = new int[n][];
            for (int k = 0; k < n; k++) {
                Unite u = us.get(k);
                List<Integer> vs = new ArrayList<>();
                for (int v = k + 1; v < Math.min(n, k + 1 + VOISINAGE_VARIANTE); v++) {
                    Unite w = us.get(v);
                    if (!seulJeton(w) && !String.join("|", w.sections()).equals(String.join("|", u.sections()))
                            && texteFixe(w.texte()) > texteFixe(u.texte())) {
                        vs.add(v);
                    }
                }
                variantes[k] = vs.stream().mapToInt(Integer::intValue).toArray();
            }
        }
    }

    /** ⚠️ 2026-10-01 (R-c) — portée de la recherche d'une variante plus contrainte (paragraphes du modèle qui suivent). */
    static final int VOISINAGE_VARIANTE = 8;

    /** ⚠️ 2026-10-01 (R-c, {@code fixe} de {@code lire.mjs}) — caractères de texte fixe, hors blancs, jetons retirés. */
    private static int texteFixe(String texte) {
        return BLANCS.matcher(norm(JETON.matcher(texte).replaceAll(""))).replaceAll("").length();
    }

    /**
     * ⚠️ 2026-10-01 (R-c) — une variante plus contrainte reconnaît aussi ce paragraphe du document : le paragraphe courant
     * lui CÈDE (il n'est pas trouvé, le curseur ne bouge pas) et elle le prend à son tour.
     */
    private static boolean varianteLeDecrit(int k, String paragraphe, List<Unite> us, Profil p) {
        for (int v : p.variantes[k]) {
            if (motifParagraphe(us.get(v).texte()).entier().matcher(paragraphe).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * ⚠️ Lot D4 (2026-09-30, règle R-b du front) — une section est ABSENTE du document quand elle a au moins un paragraphe
     * distinctif et qu'aucun de ses paragraphes n'est reconnu (ni attestée par ailleurs).
     */
    private static boolean absente(String s, List<Unite> us, Profil p, Map<Integer, Integer> trouves, Set<String> sectionsVues) {
        if (sectionsVues.contains(s)) {
            return false;
        }
        boolean aDistinctif = false;
        for (int k = 0; k < us.size(); k++) {
            Unite u = us.get(k);
            if (!u.sections().contains(s)) {
                continue;
            }
            if (trouves.containsKey(k)) {
                return false;
            }
            aDistinctif |= p.distinctif[k];
        }
        return aDistinctif;
    }

    private static String cleTexte(Unite u) {
        return norm(JETON.matcher(u.texte()).replaceAll("{}")).toLowerCase(Locale.ROOT);
    }

    /** Retire d'une valeur lue le texte fixe qui entoure le jeton dans le modèle (ponctuation d'un jeton seul). */
    static String sansTexteFixe(String brut, String modele) {
        Matcher m = JETON.matcher(modele);
        if (!m.find()) {
            return brut;
        }
        String avant = norm(modele.substring(0, m.start()));
        int fin = m.end();
        while (m.find()) {
            fin = m.end();
        }
        String apres = norm(modele.substring(fin));
        // ⚠️ Lot D4 (2026-09-30, §B6.3) — trim et non norm : les lignes du document sont déjà normalisées une à une, et une
        // valeur de plusieurs paragraphes doit garder ses sauts de ligne pour être reprise, ligne par ligne, dans le texte
        // d'origine (norm les fondait en espaces : la valeur restait normalisée). La valeur saisie, elle, passe par norm.
        String v = trim(brut);
        if (!apres.isEmpty() && v.endsWith(apres) && v.length() > apres.length()) {
            v = trim(v.substring(0, v.length() - apres.length()));
        }
        if (!avant.isEmpty() && v.startsWith(avant) && v.length() > avant.length()) {
            v = trim(v.substring(avant.length()));
        }
        return v;
    }

    private record Motif(Pattern entier, Pattern tete, List<String> jetons, boolean finitParFixe) {
    }

    private static Motif motifParagraphe(String texte) {
        List<String> jetons = new ArrayList<>();
        StringBuilder re = new StringBuilder();
        Matcher m = JETON.matcher(texte);
        int i = 0;
        while (m.find()) {
            re.append(motif(texte.substring(i, m.start())));
            re.append("(.+?)");
            jetons.add(m.group(1));
            i = m.end();
        }
        re.append(motif(texte.substring(i)));
        // ⚠️ 2026-10-01 (front eed6bc4, DAO travaux du MEN : « …sera de CENT VINGT (120) jours », sans point) — le point
        // final est facultatif, seulement quand du texte fixe le précède : un jeton qui finirait le paragraphe garderait
        // son point dans la valeur.
        String pointFinal = BLANCS_SOUPLES + "\\." + BLANCS_SOUPLES;
        if (POINT_FINAL_FIXE.matcher(texte).find() && re.toString().endsWith(pointFinal)) {
            re.setLength(re.length() - pointFinal.length());
            re.append("(?:").append(BLANCS_SOUPLES).append("\\.)?").append(BLANCS_SOUPLES);
        }
        boolean finitParFixe = !texte.matches("(?s).*}}\\s*$") && !norm(JETON.matcher(texte).replaceAll("")).isEmpty();
        return new Motif(Pattern.compile("^" + re + "$", FLAGS), finitParFixe ? Pattern.compile("^" + re, FLAGS) : null,
                jetons, finitParFixe);
    }

    // ------------------------------------------------------------------ valeurs

    /** Remet une valeur imprimée dans la forme de saisie du champ ; {@code null} : pas une valeur. */
    static String valeurSaisie(String brut, String type, String suffixe) {
        String v = norm(brut);
        if (POINTILLES.matcher(v).matches()) {
            return null;   // un jeton vide s'imprime en pointillés (R2)
        }
        // ⚠️ 2026-10-01 (front eed6bc4, AE du MEN : « Jours …. ») — une unité seule devant ou derrière des pointillés est une
        // case laissée en blanc ; les caractères d'usage privé n'y comptent pas.
        if (DEUX_POINTILLES.matcher(v).find() && POINTILLES.matcher(
                UNITE_SEULE.matcher(USAGE_PRIVE.matcher(v).replaceAll("")).replaceAll("")).matches()) {
            return null;
        }
        if ("chiffres".equals(suffixe) || "NOMBRE".equals(type) || "MONTANT".equals(type) || "POURCENTAGE".equals(type)) {
            Matcher mots = MOTS_PUIS_CHIFFRES.matcher(trim(v));
            String n = UNITE_NOMBRE.matcher(mots.matches() ? mots.group(1) : v).replaceAll("").replaceFirst(",", ".");
            return NOMBRE.matcher(n).matches() ? n : null;
        }
        if ("DATE".equals(type)) {
            Matcher d = DATE.matcher(v);
            return d.matches() ? d.group(3) + "-" + d.group(2) + "-" + d.group(1) : null;
        }
        if ("DATE_HEURE".equals(type)) {
            Matcher d = DATE_HEURE.matcher(v);
            return d.matches() ? d.group(3) + "-" + d.group(2) + "-" + d.group(1) + "T" + d.group(4) + ":" + d.group(5) : null;
        }
        // ⚠️ Lot D4 (2026-09-30, §B6) — la ponctuation de tête n'est pas la valeur : « - tranche conditionnelle 1 {{B02-LT-04}} »
        // lu dans « Tranche conditionnelle 1 : … » rendait « : … », en conflit avec la même valeur lue ailleurs. Les tirets
        // restent : ils peuvent ouvrir une liste.
        String sansTete = PONCTUATION_TETE.matcher(v).replaceFirst("");
        return sansTete.isEmpty() ? null : sansTete;
    }

    /** ⚠️ Lot D4 (§B6) — la ponctuation qui ouvre une valeur de texte et n'en fait pas partie. */
    private static final Pattern PONCTUATION_TETE = Pattern.compile("^(?:[:;,.]" + BLANC + "*)+");

    /** ⚠️ Lot D4 (§B6.3) — les types dont la valeur se convertit depuis le texte normalisé : jamais reprojetés. */
    private static final Set<String> TYPES_CONVERTIS = Set.of("NOMBRE", "MONTANT", "POURCENTAGE", "DATE", "DATE_HEURE",
            "LISTE", "LISTE_MULTIPLE", "OUI_NON");

    private static boolean contraint(String type, String suffixe) {
        return "chiffres".equals(suffixe) || type != null && List.of("NOMBRE", "MONTANT", "POURCENTAGE", "DATE", "DATE_HEURE").contains(type);
    }

    // ------------------------------------------------------------------ la lecture

    /** @param paragraphe ⚠️ §B6.3 — rang, dans le document lu, du paragraphe où commence la valeur (reprojection). */
    private record Lue(String jeton, String brut, Confiance confiance, String extrait, boolean finOuverte, int paragraphe) {
    }

    private record Jeton(String nom, List<String> sections, int k) {
    }

    /**
     * Lit un modèle dans les paragraphes d'un document (déjà passés par {@link #unitesDocument}).
     *
     * @param champs ce que l'on sait d'un code de champ ; {@code null} pour un code inconnu
     */
    public static Resultat lire(String sigle, FichierCommande.Modele modele, List<String> docLu,
            Function<String, InfoChamp> champs) {
        return lire(sigle, modele, docLu, champs, null);
    }

    /**
     * ⚠️ Lot D4 (2026-09-30, §B6.3) — avec {@code origines}, les mêmes paragraphes TELS QU'ÉCRITS
     * ({@link #unitesDocumentOrigine}) : une valeur de texte y est reprise après la détection des conflits (« m³ », « ’ »,
     * « — », « « » » conservés). Sans eux, les valeurs restent normalisées.
     */
    public static Resultat lire(String sigle, FichierCommande.Modele modele, List<String> docLu,
            Function<String, InfoChamp> champs, List<String> origines) {
        List<Unite> us = unites(modele.elements());
        Profil profil = new Profil(us);
        List<String> doc = new ArrayList<>(docLu);   // copie : un paragraphe fusionné y est redécoupé
        Map<Integer, Integer> trouves = new LinkedHashMap<>();   // unité → paragraphe du document
        List<Lue> lues = new ArrayList<>();
        Set<String> sectionsVues = new LinkedHashSet<>();   // attestées par du TEXTE FIXE reconnu, jamais par un jeton seul

        // Les débuts de paragraphe du modèle (texte fixe avant le premier jeton, 6 caractères au moins, 40 au plus).
        List<String> debuts = new ArrayList<>();
        for (Unite u : us) {
            String d = norm(u.texte().split("\\{\\{", -1)[0]).toLowerCase(Locale.ROOT);
            d = d.length() > 40 ? d.substring(0, 40) : d;
            debuts.add(d.length() >= DEBUT_MIN ? d : null);
        }

        // 1. Les paragraphes reconnus, dans l'ordre (un curseur empêche un texte répété de se lire deux fois).
        int curseur = 0;
        // ⚠️ 2026-10-02 (règle 8, front f04418a, DAO routier du MTP) — RÉANCRAGE. La première accroche se cherche dans tout le
        // document : elle peut tomber sur le SOMMAIRE, et la fenêtre de 60 paragraphes ne rejoignait jamais le vrai texte
        // (1 paragraphe reconnu sur 161). Après REANCRAGE paragraphes distinctifs (non répétés) manqués d'affilée, un
        // paragraphe distinctif se cherche dans tout le reste du document ; toute reconnaissance remet le compte à zéro.
        int manques = 0;
        for (int k = 0; k < us.size(); k++) {
            Unite u = us.get(k);
            if (seulJeton(u)) {
                continue;   // un jeton seul : borné par ses voisins, étape 2
            }
            Motif mo = motifParagraphe(u.texte());
            boolean reancre = manques >= REANCRAGE && profil.distinctif[k] && !profil.repete[k];
            int borne = trouves.isEmpty() || reancre ? doc.size()
                    : Math.min(doc.size(), curseur + (profil.repete[k] ? FENETRE_REPETE : FENETRE));
            int avant = trouves.size();
            for (int j = curseur; j < borne; j++) {
                Matcher x = mo.entier().matcher(doc.get(j));
                boolean ok = x.find();
                // B5 règle 2 — une ancre de moins de 8 lettres de texte fixe ne donne jamais la confiance haute.
                // ⚠️ Lot D3 (2026-09-29, §B4.1) — un JUMEAU (un autre paragraphe du modèle a le même texte fixe : « {{B04-EP-03}}
                // jours avant la date limite… » / « {{B04-EP-04}} … ») peut prendre la place de l'autre quand celui-ci n'est
                // pas reconnu : c'est l'ordre, pas le texte, qui les distingue — jamais la confiance haute.
                Confiance confiance = profil.lettres[k] >= ANCRE_HAUTE && !profil.jumeau[k] ? Confiance.HAUTE : Confiance.MOYENNE;
                if (!ok && mo.tete() != null) {
                    x = mo.tete().matcher(doc.get(j));
                    ok = x.find();
                    if (ok) {
                        String reste = trim(doc.get(j).substring(x.end()));
                        if (!reste.isEmpty()) {
                            doc.add(j + 1, reste);
                            doc.set(j, doc.get(j).substring(0, x.end()));
                            confiance = Confiance.MOYENNE;
                        }
                    }
                }
                if (!ok) {
                    continue;
                }
                if (profil.variantes[k].length > 0 && varianteLeDecrit(k, doc.get(j), us, profil)) {
                    break;   // ⚠️ 2026-10-01 (R-c) — le paragraphe revient à la variante plus contrainte
                }
                trouves.put(k, j);
                curseur = j + 1;
                if (profil.distinctif[k]) {
                    sectionsVues.addAll(u.sections());   // B5 règle 3
                }
                String extrait = doc.get(j);
                for (int n = 0; n < mo.jetons().size(); n++) {
                    String brut = x.group(n + 1);
                    Confiance c = confiance;
                    boolean dernierOuvert = n == mo.jetons().size() - 1 && !mo.finitParFixe();
                    // ⚠️ Lot D3 (2026-09-29, §B4.2) — la coupe vaut aussi quand le paragraphe finit par du texte fixe
                    // (« … est {{B02-OB-01}}. ») : fusionné avec les suivants, il se termine encore par ce texte, et la
                    // valeur les avalait en confiance haute. Le texte fixe final revient alors au reste relu.
                    if (n == mo.jetons().size() - 1) {
                        String[] cp = couper(brut, k, us.size(), debuts);
                        if (cp != null) {
                            brut = cp[0];
                            c = Confiance.MOYENNE;
                            String t = u.texte();
                            String fin = mo.finitParFixe() ? norm(t.substring(t.lastIndexOf("}}") + 2)) : "";
                            if (!fin.isEmpty() && brut.endsWith(fin)) {
                                brut = brut.substring(0, brut.length() - fin.length()).replaceAll(BLANC + "+$", "");
                            }
                            doc.add(j + 1, cp[1] + fin);
                            extrait = doc.get(j);
                        }
                    }
                    lues.add(new Lue(mo.jetons().get(n), brut, c, extrait, dernierOuvert, j));
                }
                break;
            }
            if (trouves.size() > avant) {
                manques = 0;
            } else if (profil.distinctif[k]) {
                manques++;
            }
        }

        // 2. Les paragraphes faits d'un seul jeton : ce qui sépare leurs voisins reconnus.
        List<Ambigu> ambigus = new ArrayList<>();
        Map<String, List<Jeton>> intervalles = new LinkedHashMap<>();
        Map<String, int[]> bornes = new LinkedHashMap<>();
        for (int k = 0; k < us.size(); k++) {
            Matcher seul = JETON.matcher(us.get(k).texte());
            if (!seulJeton(us.get(k)) || !seul.find()) {
                continue;
            }
            int a = k - 1;
            while (a >= 0 && !trouves.containsKey(a)) {
                a--;
            }
            int b = k + 1;
            while (b < us.size() && !trouves.containsKey(b)) {
                b++;
            }
            if (a < 0 || b >= us.size()) {
                continue;
            }
            String cle = a + ":" + b;
            bornes.putIfAbsent(cle, new int[] { a, b });
            intervalles.computeIfAbsent(cle, x -> new ArrayList<>()).add(new Jeton(seul.group(1), us.get(k).sections(), k));
        }
        // ⚠️ Lot D4 (R-b) — dans un intervalle à plusieurs jetons seuls, les jetons d'une section absente sont écartés ; s'il
        // n'en reste qu'un, il est lu en confiance basse (CCAP-T : « {{B02-OT-02}}. » suivi de la liste des lots, sous
        // ALLOTI, sur un marché non alloti).
        Set<String> filtres = new java.util.HashSet<>();
        for (Map.Entry<String, List<Jeton>> e : intervalles.entrySet()) {
            List<Jeton> presents = e.getValue().stream()
                    .filter(j -> j.sections().stream().noneMatch(s -> absente(s, us, profil, trouves, sectionsVues))).toList();
            if (presents.size() == 1 && e.getValue().size() > 1) {
                e.setValue(new ArrayList<>(presents));
                filtres.add(e.getKey());
            }
        }
        for (Map.Entry<String, List<Jeton>> e : intervalles.entrySet()) {
            int[] ab = bornes.get(e.getKey());
            List<Jeton> jetons = e.getValue();
            boolean filtre = filtres.contains(e.getKey());
            int de = trouves.get(ab[0]) + 1;
            int a = trouves.get(ab[1]);
            if (a <= de || a - de > INTERVALLE_MAX) {
                continue;
            }
            List<String> entre = doc.subList(de, a);
            if (jetons.size() > 1) {
                ambigus.add(new Ambigu(jetons.stream().map(Jeton::nom).toList(), String.join("\n", entre)));
                continue;
            }
            if (ressembleAuModele(entre.get(0), debuts)) {
                continue;
            }
            Jeton j = jetons.get(0);
            boolean atteste = sectionsVues.containsAll(j.sections());
            String brut = String.join("\n", entre);
            String[] cp = couper(brut, j.k(), us.size(), debuts);
            if (cp != null) {
                brut = cp[0];
            }
            // ⚠️ Écart au portage (2026-09-29) : le texte fixe sans lettre d'un jeton seul (« {{CODE}}. », « ({{CODE}}) »)
            // n'est pas la valeur. Sans cela, le point final du modèle restait collé à la dernière valeur (« Lot n° 2 : 500 000
            // Ariary. » ne se lisait plus comme un montant).
            brut = sansTexteFixe(brut, us.get(j.k()).texte());
            Confiance confiance = atteste && entre.size() == 1 && cp == null && !filtre ? Confiance.MOYENNE : Confiance.BASSE;
            // ⚠️ Lot D3 (2026-09-29, §B4.3) — plusieurs jetons séparés de ponctuation seule (« {{B02-OB-03}} — {{B02-OB-01}} ») :
            // rien n'est proposé. Tout donner au premier est une fausse valeur, et le séparateur ne découpe pas sûrement.
            Matcher nb = JETON.matcher(us.get(j.k()).texte());
            int nbJetons = 0;
            while (nb.find()) {
                nbJetons++;
            }
            if (nbJetons > 1) {
                continue;
            }
            lues.add(new Lue(j.nom(), brut, confiance, String.join("\n", entre), false, de));
        }

        // 3. Les réponses que disent les rédactions retenues.
        Map<String, Reponse> cadrage = new LinkedHashMap<>();
        Map<String, Reponse> reponsesChamps = new LinkedHashMap<>();
        List<Conflit> conflits = new ArrayList<>();
        Set<String> enConflit = new LinkedHashSet<>();
        // ⚠️ 2026-10-01 (front 0afc489, règle 6) — un terme `CODE contient Option` d'une section retenue ajoute l'option à la
        // liste du champ, si c'est une option ENTIÈRE du référentiel (B05-GQ-02, à choix multiples : un DAO qui admet les
        // trois formes de garantie donne les trois options). Section de la première option attestée.
        Map<String, List<String>> multiples = new LinkedHashMap<>();
        Map<String, String> sectionMultiple = new LinkedHashMap<>();
        for (String s : sectionsVues) {
            for (Map.Entry<String, String> t : ConditionsModele.contenus(modele.conditions().get(s))) {
                InfoChamp info = champs.apply(t.getKey());
                if (info == null || info.options() == null) {
                    continue;
                }
                String cherche = norm(t.getValue()).toLowerCase(Locale.ROOT);
                info.options().stream().filter(o -> norm(o).toLowerCase(Locale.ROOT).equals(cherche)).findFirst().ifPresent(o -> {
                    List<String> l = multiples.computeIfAbsent(t.getKey(), k -> new ArrayList<>());
                    if (!l.contains(o)) {
                        l.add(o);
                    }
                    sectionMultiple.putIfAbsent(t.getKey(), s);
                });
            }
            for (Map.Entry<String, String> t : ConditionsModele.implications(modele.conditions().get(s))) {
                Map<String, Reponse> cible = CODE_CHAMP.matcher(t.getKey()).matches() ? reponsesChamps : cadrage;
                Reponse deja = cible.get(t.getKey());
                if (deja != null && !deja.valeur().equals(t.getValue())) {
                    conflits.add(new Conflit(t.getKey(), List.of(deja.valeur(), t.getValue())));
                    enConflit.add(t.getKey());
                } else if (deja == null) {
                    cible.put(t.getKey(), new Reponse(t.getKey(), t.getValue(), s));
                }
            }
        }
        for (Map.Entry<String, List<String>> e : multiples.entrySet()) {
            // L'ordre est celui du référentiel, pas celui du document : deux lectures d'un même DAO donnent la même valeur.
            List<String> ordre = champs.apply(e.getKey()).options();
            String valeur = String.join(",", e.getValue().stream().sorted(java.util.Comparator.comparingInt(ordre::indexOf)).toList());
            Map<String, Reponse> cible = CODE_CHAMP.matcher(e.getKey()).matches() ? reponsesChamps : cadrage;
            Reponse deja = cible.get(e.getKey());
            if (deja != null && !deja.valeur().equals(valeur)) {
                conflits.add(new Conflit(e.getKey(), List.of(deja.valeur(), valeur)));
                enConflit.add(e.getKey());
            } else if (deja == null) {
                cible.put(e.getKey(), new Reponse(e.getKey(), valeur, sectionMultiple.get(e.getKey())));
            }
        }

        // 4. Valeurs dans la forme de saisie ; un même champ lu deux fois différemment est un conflit, pas un choix.
        Map<String, Proposition> parCode = new LinkedHashMap<>();
        Map<String, Lue> sources = new LinkedHashMap<>();   // ⚠️ §B6.3 — la lecture retenue d'une valeur de texte
        for (Lue p : etendre(lues)) {
            String[] parts = p.jeton().split("\\.", -1);
            String jeton = parts[0];
            String code = jeton.split("#", -1)[0];
            String suffixe = parts.length > 1 ? parts[1] : null;
            if (!CODE.matcher(code).matches() || "lettres".equals(suffixe)) {
                continue;   // LOT, DERIVE, montant en lettres : contrôles, pas des valeurs
            }
            InfoChamp info = champs.apply(code);
            String type = info == null ? null : info.type();
            String valeur = valeurSaisie(p.brut(), type, suffixe);
            if (valeur == null) {
                continue;
            }
            Confiance c = p.confiance();
            if (p.finOuverte() && c == Confiance.HAUTE && !contraint(type, suffixe)) {
                c = Confiance.MOYENNE;   // ouverte à droite, une valeur de texte peut avoir avalé un paragraphe ajouté
            }
            if (info != null && "CADRAGE".equals(info.source()) && info.cleCadrage() != null) {
                cadrage.putIfAbsent(info.cleCadrage(), new Reponse(info.cleCadrage(), valeur, null));
                continue;   // un reflet du cadrage se propose comme réponse de cadrage
            }
            Proposition deja = parCode.get(jeton);
            if (deja != null && !deja.valeur().equals(valeur)) {
                conflits.add(new Conflit(jeton, List.of(deja.valeur(), valeur)));
                enConflit.add(jeton);
                continue;
            }
            if (deja == null || c.rang > deja.confiance().rang) {
                parCode.put(jeton, new Proposition(jeton, valeur, norm(p.brut()), c, p.extrait()));
                boolean texte = !"chiffres".equals(suffixe) && (type == null || !TYPES_CONVERTIS.contains(type));
                if (texte) {
                    sources.put(jeton, p);
                } else {
                    sources.remove(jeton);
                }
            }
        }
        // ⚠️ Lot D4 (2026-09-30, §B6.3) — une valeur de texte est reprise dans le texte d'origine, ligne par ligne, APRÈS la
        // détection des conflits (qui reste sur le texte normalisé : pas de faux conflit). Une ligne introuvable garde la
        // valeur normalisée.
        Reprojection reprojection = origines == null ? null : new Reprojection(origines);
        List<Proposition> finales = parCode.values().stream().filter(p -> !enConflit.contains(p.code()))
                .map(p -> reprojection == null || !sources.containsKey(p.code()) ? p
                        : new Proposition(p.code(), reprojection.valeur(p.valeur(), sources.get(p.code())), p.brut(),
                                p.confiance(), p.extrait()))
                .toList();
        Set<String> attendus = new LinkedHashSet<>();
        for (Unite u : us) {
            Matcher m = JETON.matcher(u.texte());
            while (m.find()) {
                String code = m.group(1).split("\\.", -1)[0];
                if (CODE.matcher(code).matches()) {
                    attendus.add(code);
                }
            }
        }
        List<String> nonTrouves = attendus.stream()
                .filter(c -> parCode.keySet().stream().noneMatch(k -> k.split("#", -1)[0].equals(c))).toList();
        return new Resultat(sigle, us.size(), trouves.size(), finales,
                cadrage.values().stream().filter(r -> !enConflit.contains(r.cle())).toList(),
                reponsesChamps.values().stream().filter(r -> !enConflit.contains(r.cle())).toList(),
                ambigus, conflits, nonTrouves);
    }

    /**
     * ⚠️ Lot D4 (2026-09-30, §B6.3) — le texte normalisé d'un paragraphe et, pour chacune de ses positions, l'étendue du
     * texte d'origine qui l'a produite (NFKC change des longueurs : « ﬁ » → « fi », « … » → « ... »). Construite graphème par
     * graphème ({@link java.text.BreakIterator}, pour ne pas séparer une lettre de son accent combinant) ; une suite de
     * blancs donne une espace qui ne pointe sur rien. Portage de {@code carteNormalisee} de {@code lire.mjs}.
     */
    record Carte(String texte, int[] debut, int[] fin, String origine) {

        /** {@code null} si la reconstruction ne redonne pas {@link #norm} du texte d'origine. */
        static Carte de(String origine) {
            StringBuilder texte = new StringBuilder();
            List<Integer> debut = new ArrayList<>();
            List<Integer> fin = new ArrayList<>();
            boolean blanc = false;
            java.text.BreakIterator it = java.text.BreakIterator.getCharacterInstance(Locale.FRENCH);
            it.setText(origine);
            int a = it.first();
            for (int b = it.next(); b != java.text.BreakIterator.DONE; a = b, b = it.next()) {
                String normalise = normSansBlancs(origine.substring(a, b));
                for (int i = 0; i < normalise.length(); ) {
                    String ch = new String(Character.toChars(normalise.codePointAt(i)));
                    i += ch.length();
                    if (UN_BLANC.matcher(ch).matches()) {
                        blanc = true;
                        continue;
                    }
                    if (blanc && texte.length() > 0) {
                        texte.append(' ');
                        debut.add(-1);
                        fin.add(-1);
                    }
                    blanc = false;
                    texte.append(ch);
                    for (int k = 0; k < ch.length(); k++) {
                        debut.add(a);
                        fin.add(b);
                    }
                }
            }
            if (!texte.toString().equals(norm(origine))) {
                return null;
            }
            return new Carte(texte.toString(), debut.stream().mapToInt(Integer::intValue).toArray(),
                    fin.stream().mapToInt(Integer::intValue).toArray(), origine);
        }

        /**
         * Une ligne de valeur (normalisée) retrouvée dans ce paragraphe : son texte d'origine, les blancs multiples,
         * tabulations et sauts de ligne ramenés à une espace (une espace insécable seule est gardée) ; {@code null} sinon.
         */
        String reprojeter(String ligne) {
            int i = ligne.isEmpty() ? -1 : texte.indexOf(ligne);
            if (i < 0) {
                return null;
            }
            return BLANCS_A_RESSERRER.matcher(origine.substring(debut[i], fin[i + ligne.length() - 1])).replaceAll(" ");
        }
    }

    private static final Pattern UN_BLANC = Pattern.compile(BLANC);
    /** Comme {@code /\s{2,}|[\t\r\n]/g} du front : plusieurs blancs, ou une tabulation, un retour, un saut de ligne. */
    private static final Pattern BLANCS_A_RESSERRER = Pattern.compile(BLANC + "{2,}|[\\t\\r\\n]");

    /** ⚠️ Lot D4 (§B6.3) — les cartes des paragraphes d'origine, construites à la demande, et la reprise d'une valeur. */
    private static final class Reprojection {
        private final List<String> origines;
        private final Carte[] cartes;
        private final boolean[] faites;

        Reprojection(List<String> origines) {
            this.origines = origines;
            this.cartes = new Carte[origines.size()];
            this.faites = new boolean[origines.size()];
        }

        private Carte carte(int i) {
            if (!faites[i]) {
                cartes[i] = Carte.de(origines.get(i));
                faites[i] = true;
            }
            return cartes[i];
        }

        /**
         * La valeur reprise dans le texte d'origine, ou {@code valeur} si l'une de ses lignes est introuvable. La recherche
         * part de cinq rangs avant le paragraphe lu (un redécoupage de fusion décale les indices), puis couvre le reste.
         */
        String valeur(String valeur, Lue lue) {
            List<String> lignes = new ArrayList<>();
            for (String l : lue.brut().split("\n", -1)) {
                String n = norm(l);
                if (!n.isEmpty()) {
                    lignes.add(n);
                }
            }
            if (!lignes.isEmpty()) {
                lignes.set(0, PONCTUATION_TETE.matcher(lignes.get(0)).replaceFirst(""));
            }
            if (!String.join(" ", lignes).equals(valeur)) {
                return valeur;
            }
            int n = origines.size();
            int lo = Math.max(0, Math.min(n, lue.paragraphe() - 5));
            List<String> reprises = new ArrayList<>();
            for (String ligne : lignes) {
                String reprise = null;
                for (int k = 0; k < n && reprise == null; k++) {
                    Carte c = carte(k < n - lo ? lo + k : k - (n - lo));
                    reprise = c == null ? null : c.reprojeter(ligne);
                }
                if (reprise == null) {
                    return valeur;
                }
                reprises.add(reprise);
            }
            return String.join(" ", reprises);
        }
    }

    /**
     * ⚠️ Lot D2 (2026-09-29, §B1) — une valeur « par lot » d'un document commun ({@code {{CODE.parLot}}}) : « Lot n° 1 : v1 ;
     * Lot n° 2 : v2 » devient {@code CODE#1}, {@code CODE#2} ; sans mention de lot, la valeur seule sous {@code CODE}.
     */
    private static List<Lue> etendre(List<Lue> lues) {
        List<Lue> out = new ArrayList<>();
        for (Lue p : lues) {
            String[] parts = p.jeton().split("\\.", -1);
            if (parts.length < 2 || !FormulairesCandidat.SUFFIXE_PAR_LOT.equals(parts[1])) {
                out.add(p);
                continue;
            }
            String texte = norm(p.brut());
            Matcher m = LOT_ENUMERE.matcher(texte);
            List<int[]> reperes = new ArrayList<>();
            List<String> numeros = new ArrayList<>();
            while (m.find()) {
                reperes.add(new int[] { m.start(), m.end() });
                numeros.add(m.group(1));
            }
            if (reperes.isEmpty()) {
                out.add(new Lue(parts[0], p.brut(), p.confiance(), p.extrait(), p.finOuverte(), p.paragraphe()));
                continue;
            }
            for (int i = 0; i < reperes.size(); i++) {
                int fin = i + 1 < reperes.size() ? reperes.get(i + 1)[0] : texte.length();
                String valeur = texte.substring(reperes.get(i)[1], fin).replaceAll("\\s*;\\s*$", "");
                out.add(new Lue(parts[0] + "#" + numeros.get(i), valeur, p.confiance(), p.extrait(), p.finOuverte(), p.paragraphe()));
            }
        }
        return out;
    }

    /** Coupe un texte au premier début d'un paragraphe SUIVANT du modèle (k + 1 … k + 40) ; {@code null} s'il n'y en a pas. */
    private static String[] couper(String texte, int k, int nbUnites, List<String> debuts) {
        String t = texte.toLowerCase(Locale.ROOT);
        int i = -1;
        for (int q = k + 1; q < Math.min(nbUnites, k + PORTEE_COUPE + 1); q++) {
            String d = debuts.get(q);
            if (d == null) {
                continue;
            }
            int p = t.indexOf(d, 1);
            if (p > 0 && (i < 0 || p < i)) {
                i = p;
            }
        }
        return i > 0 ? new String[] { texte.substring(0, i).trim(), texte.substring(i).trim() } : null;
    }

    private static boolean ressembleAuModele(String texte, List<String> debuts) {
        String t = norm(texte).toLowerCase(Locale.ROOT);
        return debuts.stream().anyMatch(d -> d != null && d.length() >= DEBUT_RESSEMBLANCE && t.startsWith(d));
    }
}
