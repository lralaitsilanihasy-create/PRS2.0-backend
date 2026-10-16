package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.entity.BonCommande;
import cnm.prs.entity.ContratVigueur;

/** ⚠️ 2026-10-16 (lot MS-3) — les cumuls et alertes des bons de commande : l'année contractuelle, le minimum annuel, la valeur estimée. */
class BonCommandeServiceTest {

    private static ContratVigueur contrat(String type, LocalDate effet, int mois) {
        ContratVigueur c = new ContratVigueur();
        c.setTypeMarche(type);
        c.setDateEffet(effet);
        c.setDateFin(effet.plusMonths(mois).minusDays(1));
        return c;
    }

    private static BonCommande bon(String etat, String montant, LocalDate le) {
        BonCommande b = new BonCommande();
        b.setEtat(etat);
        b.setMontantHt(new BigDecimal(montant));
        b.setEmisLe(le.atTime(10, 0));
        return b;
    }

    @Test
    @DisplayName("L'année contractuelle court de la date d'effet, d'an en an, bornée par la fin du contrat")
    void anneeContractuelle() {
        ContratVigueur c = contrat(ContratVigueur.A_COMMANDE, LocalDate.of(2025, 3, 1), 30);
        assertThat(BonCommandeService.anneeContractuelle(c, LocalDate.of(2025, 3, 1))).containsExactly(LocalDate.of(2025, 3, 1), LocalDate.of(2026, 2, 28));
        assertThat(BonCommandeService.anneeContractuelle(c, LocalDate.of(2026, 3, 1))).containsExactly(LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28));
        assertThat(BonCommandeService.anneeContractuelle(c, LocalDate.of(2027, 5, 1))).containsExactly(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 8, 31));
    }

    @Test
    @DisplayName("Le minimum annuel non atteint n'est signalé qu'à trente jours au plus de la fin de l'année ; les bons annulés ne comptent pas")
    void minimumNonAtteint() {
        ContratVigueur c = contrat(ContratVigueur.A_COMMANDE, LocalDate.of(2025, 11, 1), 24);
        c.setMontantMinimum(new BigDecimal("1000"));
        List<BonCommande> bs = List.of(bon(BonCommande.EMIS, "600", LocalDate.of(2026, 1, 10)), bon(BonCommande.ANNULE, "900", LocalDate.of(2026, 2, 1)),
                bon(BonCommande.EMIS, "5000", LocalDate.of(2025, 10, 31)));
        assertThat(BonCommandeService.cumul(bs)).isEqualByComparingTo("5600");
        assertThat(BonCommandeService.alertes(c, bs, LocalDate.of(2026, 9, 1))).isEmpty();
        assertThat(BonCommandeService.alertes(c, bs, LocalDate.of(2026, 10, 2))).containsExactly(BonCommandeService.MINIMUM_NON_ATTEINT);
        List<BonCommande> atteint = List.of(bon(BonCommande.EMIS, "1000", LocalDate.of(2026, 1, 10)));
        assertThat(BonCommandeService.alertes(c, atteint, LocalDate.of(2026, 10, 2))).isEmpty();
        assertThat(BonCommandeService.alertes(c, bs, LocalDate.of(2028, 1, 1))).isEmpty();   // échu : l'alerte ECHU suffit
    }

    @Test
    @DisplayName("Contrat-cadre : pas de minimum (art. 34) ; le cumul au-delà de la valeur maximale estimée est signalé (art. 8)")
    void valeurEstimee() {
        ContratVigueur c = contrat(ContratVigueur.CONTRAT_CADRE, LocalDate.of(2026, 1, 1), 12);
        c.setMontantMinimum(new BigDecimal("999999"));
        c.setMontantEstime(new BigDecimal("1000"));
        assertThat(BonCommandeService.alertes(c, List.of(bon(BonCommande.EMIS, "1000", LocalDate.of(2026, 2, 1))), LocalDate.of(2026, 12, 15))).isEmpty();
        assertThat(BonCommandeService.alertes(c, List.of(bon(BonCommande.EMIS, "1000.01", LocalDate.of(2026, 2, 1))), LocalDate.of(2026, 3, 1)))
                .containsExactly(BonCommandeService.MONTANT_ESTIME_DEPASSE);
    }
}
