package cnm.prs.service;

import java.text.Normalizer;
import java.util.Locale;

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

    /** Forme canonique de comparaison : accents/casse/séparateurs neutralisés + « s » finaux par token. */
    public static String normaliser(String s) {
        if (s == null) {
            return "";
        }
        String sansAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String majuscules = sansAccents.toUpperCase(Locale.FRENCH);
        StringBuilder sb = new StringBuilder();
        for (String token : majuscules.split("[^A-Z0-9]+")) {
            if (token.isEmpty()) {
                continue;
            }
            // Pluriel simple : un « s » final par token (token d'au moins 2 caractères).
            if (token.length() >= 2 && token.endsWith("S")) {
                token = token.substring(0, token.length() - 1);
            }
            sb.append(token);
        }
        return sb.toString();
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
