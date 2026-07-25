package cnm.prs.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * ⚠️ Règle ajoutée — <strong>source unique</strong> de normalisation des libellés de référentiel
 * (modes de passation, natures) pour la résolution-ou-création : partagée par l'import PPM
 * ({@link SaisiePpmImportService}) et la création-à-la-volée ({@link SaisieService}), afin qu'une
 * même chaîne résolve identiquement des deux côtés.
 *
 * <p><strong>Normalisation étendue</strong> (décisions documentées, §import PPM / §création à la volée) :
 * trim + casse + accents (existant), <strong>plus</strong> : apostrophes/espaces typographiques neutralisés
 * (tout non-alphanumérique = séparateur) et <strong>pluriels simples</strong> = suppression d'un « s »
 * <em>final</em> par token (« OFFRES » ≡ « OFFRE » ; pas de « x » final — « travaux » ne se singularise pas).
 * Appliquée symétriquement au libellé du référentiel et au texte entrant, l'égalité est préservée.
 * Ex. « APPEL D'OFFRE OUVERT » ≡ « Appel d'offres ouvert » → {@code APPELDOFFREOUVERT}.</p>
 *
 * <p><strong>Portée</strong> : libellés (modes, natures) uniquement — jamais les codes SOA/comptes,
 * qui sont des identifiants exacts ({@code existsById}).</p>
 */
public final class LibelleNormalisation {

    private LibelleNormalisation() {
    }

    /**
     * Jetons de <strong>source de financement</strong> reconnus en suffixe d'un libellé de mode
     * (⚠️ règle ajoutée). Retirés avant la résolution : « ACHAT DIRECT <em>RPI</em> » a le même noyau que
     * « Achat Direct ». Liste volontairement restreinte aux sources confirmées ({@code RPI}, {@code PIP}) —
     * <strong>pas</strong> {@code PPP}, qui distingue un mode à part entière (« MARCHE DE GRE A GRE PPP »).
     * Extensible ici (ou, à terme, via un référentiel administrable).
     */
    private static final Set<String> SOURCES_FINANCEMENT = Set.of("RPI", "PIP");

    /** Forme canonique de comparaison : accents/casse/séparateurs neutralisés + « s » finaux par token. */
    public static String normaliser(String s) {
        if (s == null) {
            return "";
        }
        return concatSansPluriel(jetons(deaccentueMajuscule(s)));
    }

    private static String deaccentueMajuscule(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.FRENCH);
    }

    private static List<String> jetons(String deaccentueMajuscule) {
        List<String> out = new ArrayList<>();
        for (String token : deaccentueMajuscule.split("[^A-Z0-9]+")) {
            if (!token.isEmpty()) {
                out.add(token);
            }
        }
        return out;
    }

    /** Concatène les tokens en retirant un « s » final par token d'au moins 2 caractères (pluriel simple). */
    private static String concatSansPluriel(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (token.length() >= 2 && token.endsWith("S")) {
                token = token.substring(0, token.length() - 1);
            }
            sb.append(token);
        }
        return sb.toString();
    }

    /**
     * ⚠️ Règle ajoutée — sépare un libellé de mode en <strong>[noyau normalisé, source de financement]</strong>.
     * Si le dernier token est une {@link #SOURCES_FINANCEMENT source de financement} reconnue (RPI/PIP), il est
     * retiré du noyau et renvoyé en seconde position (majuscule) ; sinon la source est {@code null} et le noyau
     * est la forme normalisée complète. Appliquée symétriquement au libellé du référentiel et au texte entrant.
     */
    public static String[] separerSource(String s) {
        if (s == null || s.isBlank()) {
            return new String[] { "", null };
        }
        List<String> tokens = jetons(deaccentueMajuscule(s));
        String source = null;
        if (!tokens.isEmpty() && SOURCES_FINANCEMENT.contains(tokens.get(tokens.size() - 1))) {
            source = tokens.remove(tokens.size() - 1);
        }
        return new String[] { concatSansPluriel(tokens), source };
    }

    /**
     * ⚠️ Règle ajoutée — résout un libellé de mode contre le référentiel en <strong>ignorant un suffixe de
     * source de financement</strong> (RPI/PIP). Deux passes :
     * <ol>
     *   <li><strong>Source exacte</strong> : une entrée dont le noyau <em>et</em> la source correspondent
     *       exactement (une entrée sans source correspond à un texte sans source → mode <em>base</em>).
     *       Ainsi « … PIP » résout vers la variante PIP (idMode=8), jamais une autre source.</li>
     *   <li><strong>Repli base</strong> : si le texte porte une source sans variante dédiée, l'entrée
     *       <em>base</em> (sans suffixe) de même noyau. Ainsi « … RPI » (aucune entrée RPI) résout vers le base
     *       (idMode=4), <strong>jamais</strong> vers « … PIP » (RPI ≠ PIP).</li>
     * </ol>
     * Retourne {@code null} si aucun noyau ne correspond (pas de résolution floue : cf. {@link #distance}).
     */
    public static <T> T resoudreMode(List<T> modes, Function<T, String> libelle, String cible) {
        if (cible == null || cible.isBlank()) {
            return null;
        }
        String[] c = separerSource(cible);
        String noyau = c[0];
        String source = c[1];
        if (noyau.isEmpty()) {
            return null;
        }
        // Passe 1 — source exacte (sans-source → entrée base).
        for (T m : modes) {
            String lib = libelle.apply(m);
            if (lib == null) {
                continue;
            }
            String[] r = separerSource(lib);
            if (r[0].equals(noyau) && Objects.equals(r[1], source)) {
                return m;
            }
        }
        // Passe 2 — repli sur l'entrée base quand la source entrante n'a pas de variante dédiée.
        if (source != null) {
            for (T m : modes) {
                String lib = libelle.apply(m);
                if (lib == null) {
                    continue;
                }
                String[] r = separerSource(lib);
                if (r[0].equals(noyau) && r[1] == null) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * Distance de Levenshtein (insertions/suppressions/substitutions) entre deux chaînes — utilisée sur les
     * <strong>formes normalisées</strong> pour la suggestion « vouliez-vous dire … ? » (jamais pour
     * auto-résoudre : pas de fuzzy silencieux).
     */
    public static int distance(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cout = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cout);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }
}
