package cnm.prs.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cnm.prs.dto.BilanControlesDto;
import cnm.prs.dto.EstimationLotDto;

/**
 * ⚠️ V116 (2026-10-14, demande front « q11-prix-estimatifs », §B2 ; arbitrage du pilote du 14/10 sur Q11, manuel révisé §23) —
 * l'estimation de la fiche DAO, lot par lot, et ses deux contrôles <strong>bloquants</strong> à la validation :
 * <ul>
 *   <li>{@code PRIX_ESTIME_MANQUANT} : un article du lot n'a pas de prix unitaire estimé ;</li>
 *   <li>{@code ESTIMATION_ECART} : le total estimé du lot (quantité × prix unitaire, arrondi à l'ariary) n'égale pas le montant HT du
 *       lot au plan (H1, sans tolérance). Un lot dont le plan ne porte pas de montant n'est pas comparé : avertissement.</li>
 * </ul>
 * La quantité d'un marché à commandes est son maximum (H2, {@link BesoinFiche.Article#quantiteEstimee(boolean)}).
 */
public final class EstimationFiche {

    public static final String PRIX_ESTIME_MANQUANT = "PRIX_ESTIME_MANQUANT";
    public static final String ESTIMATION_ECART = "ESTIMATION_ECART";
    private static final String BLOC_BESOIN = "B12";

    private EstimationFiche() {
    }

    /** L'estimation de chaque lot de la ligne (le lot unique {@code null} d'une ligne non allotie). */
    public static List<EstimationLotDto> parLot(List<BesoinFiche.Article> articles, boolean aCommande,
            Map<Integer, BigDecimal> montantsPlan, int nbLots) {
        List<Integer> lots = new ArrayList<>();
        if (LotsFiche.alloti(nbLots)) {
            for (int n = 1; n <= nbLots; n++) {
                lots.add(n);
            }
        } else {
            lots.add(null);
        }
        List<EstimationLotDto> out = new ArrayList<>();
        for (Integer lot : lots) {
            List<BesoinFiche.Article> duLot = articles.stream().filter(a -> Objects.equals(a.lot(), lot)).toList();
            BigDecimal total = BigDecimal.ZERO;
            List<String> sansPrix = new ArrayList<>();
            for (BesoinFiche.Article a : duLot) {
                BigDecimal m = a.montantEstime(aCommande);
                if (a.prixUnitaireEstime() == null) {
                    sansPrix.add(libelle(a));
                } else if (m != null) {
                    total = total.add(m);
                }
            }
            BigDecimal plan = montantsPlan == null ? null : montantsPlan.get(lot);
            BigDecimal totalServi = BesoinFiche.plat(total);
            out.add(new EstimationLotDto(lot, BesoinFiche.plat(plan), totalServi,
                    plan == null ? null : BesoinFiche.plat(total.subtract(plan)), duLot.size(), sansPrix));
        }
        return out;
    }

    /** Les deux contrôles, lot par lot ; un lot sans article est laissé à {@code BESOIN_INCOMPLET}. */
    public static void auBilan(List<EstimationLotDto> estimations, BilanControlesDto bilan) {
        boolean complet = true;
        for (EstimationLotDto e : estimations) {
            if (e.nbArticles() == 0) {
                complet = false;
                continue;
            }
            String nomLot = e.lot() == null ? "Le besoin" : "Le lot " + e.lot();
            if (!e.articlesSansPrix().isEmpty()) {
                complet = false;
                int n = e.articlesSansPrix().size();
                bilan.bloquants().add(new BilanControlesDto.Controle(PRIX_ESTIME_MANQUANT, List.of(), BLOC_BESOIN, nomLot + " : "
                        + (n == 1 ? "un article n'a pas de prix unitaire estimé" : n + " articles n'ont pas de prix unitaire estimé")
                        + " (" + String.join(" ; ", e.articlesSansPrix()) + ")."));
                continue;
            }
            String total = ValeursPpmService.montant(e.totalEstime().setScale(0, RoundingMode.HALF_UP)) + " Ar";
            if (e.montantPlan() == null) {
                bilan.avertissements().add(new BilanControlesDto.Controle(ESTIMATION_ECART, List.of(), BLOC_BESOIN, nomLot
                        + " n'a pas de montant au plan : son total estimé (" + total + ") ne lui est pas comparé."));
                continue;
            }
            BigDecimal ecart = e.totalEstime().setScale(0, RoundingMode.HALF_UP)
                    .subtract(e.montantPlan().setScale(0, RoundingMode.HALF_UP));
            String plan = ValeursPpmService.montant(e.montantPlan()) + " Ar";
            if (ecart.signum() != 0) {
                complet = false;
                bilan.bloquants().add(new BilanControlesDto.Controle(ESTIMATION_ECART, List.of(), BLOC_BESOIN, nomLot
                        + " : le total estimé (" + total + ") n'égale pas le montant du lot au plan (" + plan + ") : écart de "
                        + (ecart.signum() > 0 ? "+" : "−") + ValeursPpmService.montant(ecart.abs()) + " Ar."));
            } else {
                bilan.ok().add(new BilanControlesDto.Controle(ESTIMATION_ECART, List.of(), BLOC_BESOIN, nomLot
                        + " : le total estimé égale le montant du plan (" + plan + ")."));
            }
        }
        if (complet && !estimations.isEmpty()) {
            bilan.ok().add(new BilanControlesDto.Controle(PRIX_ESTIME_MANQUANT, List.of(), BLOC_BESOIN,
                    "Chaque article a son prix unitaire estimé."));
        }
    }

    /** « n° 001 — Béton dosé à 350 kg » (travaux) ou « article 2 — Ordinateur portable ». */
    static String libelle(BesoinFiche.Article a) {
        return (a.numeroPrix() != null && !a.numeroPrix().isBlank() ? "n° " + a.numeroPrix() : "article " + a.ordre()) + " — "
                + a.designation();
    }
}
