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
    private static final Pattern SEUL_JETON = Pattern.compile("^\\{\\{([^{}]+)}}$");
    private static final Pattern SI = Pattern.compile("^\\{\\{SI:([A-Z0-9-]+)}}$");
    private static final Pattern FINSI = Pattern.compile("^\\{\\{FINSI:([A-Z0-9-]+)}}$");
    private static final Pattern POINTILLES = Pattern.compile("^[.…_" + BLANC.substring(1, BLANC.length() - 1) + "]*$");
    private static final Pattern CODE = Pattern.compile("^B\\d\\d-.*");
    private static final Pattern CODE_CHAMP = Pattern.compile("B\\d{2}-[A-Z0-9]{1,6}-\\d{2}");
    private static final Pattern DATE = Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{4})$");
    private static final Pattern DATE_HEURE = Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{4})" + BLANC + "+(\\d{2}):(\\d{2})$");
    private static final Pattern UNITE_NOMBRE = Pattern.compile("ariary|ar\\.?|%|" + BLANC, Pattern.CASE_INSENSITIVE);
    private static final Pattern NOMBRE = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final String PONCTUATION = ",;:.!?()[]\"'-/";
    private static final String META = ".*+?^${}()|[]\\";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** Fenêtre de recherche après le dernier paragraphe reconnu. */
    static final int FENETRE = 60;
    /** Unités suivantes où chercher un début de paragraphe collé. */
    static final int PORTEE_COUPE = 40;
    /** Longueur minimale d'un début de paragraphe cherché dans une valeur. */
    static final int DEBUT_MIN = 6;
    /** Longueur minimale d'un début pour juger qu'un intervalle « ressemble au modèle ». */
    static final int DEBUT_RESSEMBLANCE = 12;
    /** Un intervalle plus long n'est pas lu. */
    static final int INTERVALLE_MAX = 6;

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

    /** Ce que l'appelant sait d'un champ : son type, sa source, sa clé de cadrage (reflet). */
    public record InfoChamp(String type, String source, String cleCadrage) {
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
        String t = Normalizer.normalize(s, Normalizer.Form.NFKC)
                .replaceAll("[’ʼ‘`]", "'")
                .replaceAll("[‐‑‒–—]", "-")
                .replaceAll("[«»]", "\"")
                .replace(' ', ' ').replace(' ', ' ');
        return trim(BLANCS.matcher(t).replaceAll(" "));
    }

    private static String trim(String s) {
        return s.replaceAll("^" + BLANC + "+|" + BLANC + "+$", "");
    }

    /** Les paragraphes du document, prêts à lire : chaque ligne et chaque cellule est une unité, normalisée, non vide. */
    public static List<String> unitesDocument(List<String> lignes) {
        List<String> out = new ArrayList<>();
        for (String ligne : lignes) {
            for (String l : ligne.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
                for (String c : l.split("\t", -1)) {
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
                // le titre du document est porté à part par le modèle du front (pas un bloc) ; un VIDE n'a pas de texte
                if (p.style() == DocumentLibre.Style.TITRE || p.style() == DocumentLibre.Style.VIDE || p.texte().isBlank()) {
                    continue;
                }
                out.add(new Unite(p.texte(), List.copyOf(pile)));
            } else if (e instanceof DocumentLibre.Tableau t) {
                for (List<List<String>> ligne : t.lignes()) {
                    for (List<String> cellule : ligne) {
                        String texte = String.join(" ", cellule);
                        if (!texte.isBlank()) {
                            out.add(new Unite(texte, List.copyOf(pile)));
                        }
                    }
                }
            }
        }
        return out;
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
        if ("chiffres".equals(suffixe) || "NOMBRE".equals(type) || "MONTANT".equals(type) || "POURCENTAGE".equals(type)) {
            String n = UNITE_NOMBRE.matcher(v).replaceAll("").replaceFirst(",", ".");
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
        return v;
    }

    private static boolean contraint(String type, String suffixe) {
        return "chiffres".equals(suffixe) || type != null && List.of("NOMBRE", "MONTANT", "POURCENTAGE", "DATE", "DATE_HEURE").contains(type);
    }

    // ------------------------------------------------------------------ la lecture

    private record Lue(String jeton, String brut, Confiance confiance, String extrait, boolean finOuverte) {
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
        List<Unite> us = unites(modele.elements());
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
        for (int k = 0; k < us.size(); k++) {
            Unite u = us.get(k);
            if (SEUL_JETON.matcher(u.texte().trim()).matches()) {
                continue;   // un jeton seul : borné par ses voisins, étape 2
            }
            Motif mo = motifParagraphe(u.texte());
            int borne = trouves.isEmpty() ? doc.size() : Math.min(doc.size(), curseur + FENETRE);
            for (int j = curseur; j < borne; j++) {
                Matcher x = mo.entier().matcher(doc.get(j));
                boolean ok = x.find();
                Confiance confiance = Confiance.HAUTE;
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
                trouves.put(k, j);
                curseur = j + 1;
                sectionsVues.addAll(u.sections());
                String extrait = doc.get(j);
                for (int n = 0; n < mo.jetons().size(); n++) {
                    String brut = x.group(n + 1);
                    Confiance c = confiance;
                    boolean dernierOuvert = n == mo.jetons().size() - 1 && !mo.finitParFixe();
                    if (dernierOuvert) {
                        String[] cp = couper(brut, k, us.size(), debuts);
                        if (cp != null) {
                            brut = cp[0];
                            c = Confiance.MOYENNE;
                            doc.add(j + 1, cp[1]);
                            extrait = doc.get(j);
                        }
                    }
                    lues.add(new Lue(mo.jetons().get(n), brut, c, extrait, dernierOuvert));
                }
                break;
            }
        }

        // 2. Les paragraphes faits d'un seul jeton : ce qui sépare leurs voisins reconnus.
        List<Ambigu> ambigus = new ArrayList<>();
        Map<String, List<Jeton>> intervalles = new LinkedHashMap<>();
        Map<String, int[]> bornes = new LinkedHashMap<>();
        for (int k = 0; k < us.size(); k++) {
            Matcher seul = SEUL_JETON.matcher(us.get(k).texte().trim());
            if (!seul.matches()) {
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
        for (Map.Entry<String, List<Jeton>> e : intervalles.entrySet()) {
            int[] ab = bornes.get(e.getKey());
            List<Jeton> jetons = e.getValue();
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
            Confiance confiance = atteste && entre.size() == 1 && cp == null ? Confiance.MOYENNE : Confiance.BASSE;
            lues.add(new Lue(j.nom(), brut, confiance, String.join("\n", entre), false));
        }

        // 3. Les réponses que disent les rédactions retenues.
        Map<String, Reponse> cadrage = new LinkedHashMap<>();
        Map<String, Reponse> reponsesChamps = new LinkedHashMap<>();
        List<Conflit> conflits = new ArrayList<>();
        Set<String> enConflit = new LinkedHashSet<>();
        for (String s : sectionsVues) {
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

        // 4. Valeurs dans la forme de saisie ; un même champ lu deux fois différemment est un conflit, pas un choix.
        Map<String, Proposition> parCode = new LinkedHashMap<>();
        for (Lue p : lues) {
            String[] parts = p.jeton().split("\\.", -1);
            String code = parts[0];
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
            Proposition deja = parCode.get(code);
            if (deja != null && !deja.valeur().equals(valeur)) {
                conflits.add(new Conflit(code, List.of(deja.valeur(), valeur)));
                enConflit.add(code);
                continue;
            }
            if (deja == null || c.rang > deja.confiance().rang) {
                parCode.put(code, new Proposition(code, valeur, norm(p.brut()), c, p.extrait()));
            }
        }
        List<Proposition> finales = parCode.values().stream().filter(p -> !enConflit.contains(p.code())).toList();
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
        List<String> nonTrouves = attendus.stream().filter(c -> !parCode.containsKey(c)).toList();
        return new Resultat(sigle, us.size(), trouves.size(), finales,
                cadrage.values().stream().filter(r -> !enConflit.contains(r.cle())).toList(),
                reponsesChamps.values().stream().filter(r -> !enConflit.contains(r.cle())).toList(),
                ambigus, conflits, nonTrouves);
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
