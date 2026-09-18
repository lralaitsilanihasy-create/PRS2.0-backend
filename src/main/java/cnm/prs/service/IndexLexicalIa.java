package cnm.prs.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Recherche lexicale de l'assistant IA : classement <strong>BM25</strong> des passages du corpus pour
 * une question ({@code docs/plan-assistant-ia.md}, lot 1).
 *
 * <p>Pourquoi pas de vecteurs : le corpus tient en quelques centaines de passages, une recherche par
 * mots suffit, et elle n'exige ni extension PostgreSQL ({@code pgvector}) ni second modèle. Les
 * vecteurs ne viendront que si la batterie de questions de référence montre qu'elle ne suffit pas.</p>
 *
 * <p>La normalisation vise le français administratif : minuscules, accents retirés, mots vides
 * écartés, pluriel en {@code s}/{@code x} retiré, puis <strong>troncature à sept lettres</strong> —
 * une racinisation grossière mais robuste, qui rapproche « fractionnement », « fractionné » et
 * « fractionner ». Elle confond parfois deux mots de même racine (« contrôle » et « contrôleur ») :
 * c'est le prix accepté pour ne pas dépendre d'un analyseur linguistique.</p>
 *
 * <p>Immuable une fois construit : partagé sans verrou entre les requêtes.</p>
 */
public final class IndexLexicalIa {

    /** Saturation de la fréquence d'un terme (valeur usuelle de BM25). */
    private static final double K1 = 1.2;
    /** Poids de la normalisation par la longueur du passage (valeur usuelle de BM25). */
    private static final double B = 0.75;
    private static final int LONGUEUR_RACINE = 7;

    private static final Set<String> MOTS_VIDES = Set.of(
            "a", "au", "aux", "avec", "ce", "ces", "cet", "cette", "dans", "de", "des", "du", "elle",
            "elles", "en", "entre", "est", "et", "etre", "eux", "il", "ils", "je", "la", "le", "les",
            "leur", "leurs", "lui", "ma", "mais", "me", "meme", "mes", "moi", "mon", "ne", "nos",
            "notre", "nous", "on", "ou", "par", "pas", "pour", "qu", "que", "qui", "sa", "se", "ses",
            "son", "sont", "sur", "ta", "te", "tes", "toi", "ton", "tu", "un", "une", "vos", "votre",
            "vous", "c", "d", "j", "l", "m", "n", "s", "t", "y", "ont", "ai", "as", "avons", "avez",
            "sera", "seront", "etait", "etaient", "fait", "faut", "peut", "peuvent", "doit", "doivent",
            "comment", "quel", "quelle", "quels", "quelles", "quoi", "dont", "si", "plus",
            "moins", "tres", "aussi", "alors", "donc", "car", "ni", "sans", "sous", "chez", "tout",
            "tous", "toute", "toutes", "autre", "autres", "cela", "ceci", "ca", "lorsque", "quand",
            "selon", "afin", "ainsi", "cas", "faire", "avoir");

    private final List<String> identifiants;
    private final List<Map<String, Integer>> frequences;
    private final int[] longueurs;
    private final Map<String, Integer> nbDocumentsParTerme;
    private final double longueurMoyenne;

    private IndexLexicalIa(List<String> identifiants, List<Map<String, Integer>> frequences, int[] longueurs,
            Map<String, Integer> nbDocumentsParTerme, double longueurMoyenne) {
        this.identifiants = identifiants;
        this.frequences = frequences;
        this.longueurs = longueurs;
        this.nbDocumentsParTerme = nbDocumentsParTerme;
        this.longueurMoyenne = longueurMoyenne;
    }

    /**
     * Construit l'index.
     *
     * @param textes identifiant du passage → texte indexé (l'ordre d'insertion départage les ex æquo)
     */
    public static IndexLexicalIa construire(Map<String, String> textes) {
        List<String> ids = new ArrayList<>(textes.size());
        List<Map<String, Integer>> freqs = new ArrayList<>(textes.size());
        int[] longueurs = new int[textes.size()];
        Map<String, Integer> df = new HashMap<>();
        long total = 0;
        int i = 0;
        for (Map.Entry<String, String> e : textes.entrySet()) {
            List<String> termes = termes(e.getValue());
            Map<String, Integer> f = new HashMap<>();
            for (String t : termes) {
                f.merge(t, 1, Integer::sum);
            }
            for (String t : f.keySet()) {
                df.merge(t, 1, Integer::sum);
            }
            ids.add(e.getKey());
            freqs.add(f);
            longueurs[i++] = termes.size();
            total += termes.size();
        }
        double moyenne = ids.isEmpty() ? 0 : (double) total / ids.size();
        return new IndexLexicalIa(List.copyOf(ids), List.copyOf(freqs), longueurs, Map.copyOf(df), moyenne);
    }

    /** Un passage retenu et son score (strictement positif). */
    public record Resultat(String identifiant, double score) {
    }

    /**
     * Les {@code k} passages les plus pertinents pour la question, du meilleur au moins bon. Un
     * passage qui ne partage aucun terme avec la question n'est jamais rendu : la liste peut donc
     * être plus courte que {@code k}, voire vide.
     */
    public List<Resultat> rechercher(String question, int k) {
        List<String> requete = termes(question).stream().distinct().toList();
        if (requete.isEmpty() || identifiants.isEmpty() || k <= 0) {
            return List.of();
        }
        int n = identifiants.size();
        List<Resultat> resultats = new ArrayList<>();
        for (int d = 0; d < n; d++) {
            double score = 0;
            Map<String, Integer> f = frequences.get(d);
            for (String t : requete) {
                Integer tf = f.get(t);
                if (tf == null) {
                    continue;
                }
                int nd = nbDocumentsParTerme.getOrDefault(t, 0);
                double idf = Math.log(1 + (n - nd + 0.5) / (nd + 0.5));
                double norme = K1 * (1 - B + B * longueurs[d] / Math.max(longueurMoyenne, 1));
                score += idf * tf * (K1 + 1) / (tf + norme);
            }
            if (score > 0) {
                resultats.add(new Resultat(identifiants.get(d), score));
            }
        }
        // Tri stable : à score égal, l'ordre d'insertion (celui du corpus) départage.
        resultats.sort(Comparator.comparingDouble(Resultat::score).reversed());
        return resultats.size() > k ? List.copyOf(resultats.subList(0, k)) : List.copyOf(resultats);
    }

    /** Nombre de passages indexés. */
    public int taille() {
        return identifiants.size();
    }

    /** Termes normalisés d'un texte (visible pour les tests). */
    static List<String> termes(String texte) {
        if (texte == null || texte.isBlank()) {
            return List.of();
        }
        String simple = Normalizer.normalize(texte.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('’', '\'')
                .replace('œ', 'o');
        List<String> termes = new ArrayList<>();
        for (String brut : simple.split("[^a-z0-9]+")) {
            if (brut.isEmpty() || MOTS_VIDES.contains(brut)) {
                continue;
            }
            termes.add(racine(brut));
        }
        return termes;
    }

    private static String racine(String mot) {
        String r = mot;
        if (r.length() > 3 && !Character.isDigit(r.charAt(0)) && (r.endsWith("s") || r.endsWith("x"))) {
            r = r.substring(0, r.length() - 1);
        }
        return r.length() > LONGUEUR_RACINE ? r.substring(0, LONGUEUR_RACINE) : r;
    }
}
