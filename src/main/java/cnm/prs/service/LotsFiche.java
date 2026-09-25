package cnm.prs.service;

import java.util.List;
import java.util.Map;

import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.exception.ErrorResponse;

/**
 * ⚠️ <strong>Les valeurs par lot de la fiche DAO</strong> (demande front du 2026-09-25, §B2, dossier réel à commande en
 * cinq lots) — la règle en un seul endroit.
 *
 * <ul>
 *   <li>Un champ {@code parLot} d'une ligne <strong>allotie</strong> (plus d'un lot au plan : {@code NB_LOTS_PPM}) a une
 *       valeur par lot, enregistrée sous {@code CODE#n}, {@code n} = rang du lot dans l'ordre du plan, de 1 au nombre
 *       de lots. La clé nue y est refusée (400 nominatif) : elle ne dirait pas de quel lot il s'agit.</li>
 *   <li>Ligne non allotie : la clé nue, comme tout champ ; {@code CODE#1} est admis et vaut la clé nue (lot unique), un
 *       autre rang est refusé.</li>
 *   <li>Un champ qui n'est pas {@code parLot} refuse toute clé {@code #n}.</li>
 * </ul>
 *
 * <p>Si le nombre de lots du plan change après la saisie, les valeurs de l'autre forme restent enregistrées mais ne
 * comptent plus (ni au bilan, ni aux documents) : l'écran les redemande sous la forme du plan.</p>
 */
public final class LotsFiche {

    public static final char SEPARATEUR = '#';

    private LotsFiche() {
    }

    /** Le nombre de lots du plan, lu sur les valeurs reprises ({@code NB_LOTS_PPM}) ; 0 si absent ou illisible. */
    public static int nbLots(Map<String, String> valeursPpm) {
        String n = valeursPpm == null ? null : valeursPpm.get("NB_LOTS_PPM");
        try {
            return n == null ? 0 : Math.max(0, Integer.parseInt(n.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** La saisie par lot s'applique : plus d'un lot au plan. */
    public static boolean alloti(int nbLots) {
        return nbLots > 1;
    }

    /** Le champ se saisit par lot sur cette ligne. */
    public static boolean parLot(ChampFicheMarche c, int nbLots) {
        return Boolean.TRUE.equals(c.getParLot()) && alloti(nbLots);
    }

    /** Le rang de lot d'une clé ({@code B05-GS-03#2} → 2) ; {@code null} pour une clé nue ou illisible. */
    public static Integer lotDe(String cle) {
        int i = cle == null ? -1 : cle.indexOf(SEPARATEUR);
        if (i < 0) {
            return null;
        }
        try {
            return Integer.valueOf(cle.substring(i + 1).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code B05-GS-03#2}. */
    public static String cle(String code, int lot) {
        return code + SEPARATEUR + lot;
    }

    /**
     * Les clés sous lesquelles le champ se lit sur cette ligne : {@code CODE#1} à {@code CODE#n} s'il vaut par lot, le
     * code nu sinon.
     */
    public static List<String> cles(ChampFicheMarche c, int nbLots) {
        if (!parLot(c, nbLots)) {
            return List.of(c.getCode());
        }
        return java.util.stream.IntStream.rangeClosed(1, nbLots).mapToObj(n -> cle(c.getCode(), n)).toList();
    }

    /**
     * La clé sous laquelle enregistrer une valeur reçue sous {@code cle} ({@code rang} : ce qui suit le {@code #}, ou
     * {@code null}) ; {@code null} et une erreur nominative si la clé ne convient pas au champ ou à la ligne.
     */
    static String cleSaisie(ChampFicheMarche c, String cle, String rang, int nbLots,
            List<ErrorResponse.FieldError> erreurs) {
        boolean parLot = Boolean.TRUE.equals(c.getParLot());
        if (rang == null) {
            if (parLot(c, nbLots)) {
                erreurs.add(new ErrorResponse.FieldError(cle, "« " + c.getLibelle() + " » se saisit par lot : le plan "
                        + "compte " + nbLots + " lots (clés " + cle(c.getCode(), 1) + " à " + cle(c.getCode(), nbLots) + ")."));
                return null;
            }
            return c.getCode();
        }
        if (!parLot) {
            erreurs.add(new ErrorResponse.FieldError(cle, "« " + c.getLibelle() + " » ne vaut pas par lot : saisir "
                    + c.getCode() + " sans rang."));
            return null;
        }
        int n;
        try {
            n = Integer.parseInt(rang.trim());
        } catch (NumberFormatException e) {
            n = 0;
        }
        int max = Math.max(1, nbLots);
        if (n < 1 || n > max) {
            erreurs.add(new ErrorResponse.FieldError(cle, "Lot « " + rang + " » hors du plan : "
                    + (alloti(nbLots) ? "la ligne compte " + nbLots + " lots (1 à " + nbLots + ")."
                            : "la ligne n'est pas allotie (lot unique : " + c.getCode() + ").")));
            return null;
        }
        return alloti(nbLots) ? cle(c.getCode(), n) : c.getCode();
    }
}
