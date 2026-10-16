package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.entity.BonCommande;
import cnm.prs.entity.ReceptionCommande;

/** ⚠️ 2026-10-16 (lot MS-4) — les dérivations de la réception : l'état du bon, la garantie, l'échéance de livraison, la pénalité. */
class ReceptionCommandeServiceTest {

    private static BonCommande bon(String delai) {
        BonCommande b = new BonCommande();
        b.setEtat(BonCommande.EMIS);
        b.setDelaiLivraison(delai);
        b.setEmisLe(LocalDate.of(2026, 1, 10).atTime(9, 0));
        return b;
    }

    private static ReceptionCommande r(String type, String etat, String decision, LocalDate le) {
        ReceptionCommande x = new ReceptionCommande();
        x.setType(type);
        x.setEtat(etat);
        x.setDecision(decision);
        x.setDateReception(le);
        return x;
    }

    @Test
    @DisplayName("L'état de réception suit la dernière réception : ajournée ou rejetée, une nouvelle livraison est attendue ; sans garantie, "
            + "la provisoire vaut définitive")
    void etatReception() {
        BonCommande b = bon("10 jours");
        LocalDate le = LocalDate.of(2026, 2, 1);
        assertThat(ReceptionCommandeService.etatReception(b, List.of(), 6)).isEqualTo("A_LIVRER");
        assertThat(ReceptionCommandeService.etatReception(b, List.of(r("PROVISOIRE", "PV_ETABLI", "RECEPTION", le)), 6)).isEqualTo("EN_RECEPTION");
        assertThat(ReceptionCommandeService.etatReception(b, List.of(r("PROVISOIRE", "SIGNE", "AJOURNEMENT", le)), 6)).isEqualTo("AJOURNE");
        assertThat(ReceptionCommandeService.etatReception(b, List.of(r("PROVISOIRE", "SIGNE", "REJET", le)), 6)).isEqualTo("REJETE");
        assertThat(ReceptionCommandeService.etatReception(b, List.of(r("PROVISOIRE", "SIGNE", "REFACTION", le)), 6)).isEqualTo("RECU_PROVISOIRE");
        assertThat(ReceptionCommandeService.etatReception(b, List.of(r("PROVISOIRE", "SIGNE", "RECEPTION", le)), null)).isEqualTo("RECU_DEFINITIF");
        List<ReceptionCommande> defi = List.of(r("PROVISOIRE", "SIGNE", "RECEPTION", le), r("DEFINITIVE", "A_ETABLIR", null, null));
        assertThat(ReceptionCommandeService.etatReception(b, defi, 6)).isEqualTo("EN_RECEPTION_DEFINITIVE");
        assertThat(ReceptionCommandeService.etatReception(b, List.of(defi.get(0), r("DEFINITIVE", "SIGNE", "AJOURNEMENT", le)), 6))
                .isEqualTo("RECU_PROVISOIRE");
        assertThat(ReceptionCommandeService.etatReception(b, List.of(defi.get(0), r("DEFINITIVE", "SIGNE", "RECEPTION", le)), 6))
                .isEqualTo("RECU_DEFINITIF");
        assertThat(ReceptionCommandeService.garantieJusquau(defi, 6)).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(ReceptionCommandeService.garantieJusquau(defi, null)).isNull();
    }

    @Test
    @DisplayName("L'échéance de livraison : la notification + le délai lu en jours, semaines ou mois ; illisible, nulle")
    void echeanceLivraison() {
        assertThat(ReceptionCommandeService.echeanceLivraison(bon("10 jours"))).isEqualTo(LocalDate.of(2026, 1, 20));
        assertThat(ReceptionCommandeService.echeanceLivraison(bon("Deux semaines, soit 2 semaines"))).isEqualTo(LocalDate.of(2026, 1, 24));
        assertThat(ReceptionCommandeService.echeanceLivraison(bon("3 mois"))).isEqualTo(LocalDate.of(2026, 4, 10));
        assertThat(ReceptionCommandeService.echeanceLivraison(bon("dès que possible"))).isNull();
    }

    @Test
    @DisplayName("La pénalité indicative : montant × taux (‰) × jours, plafonnée ; nulle sans retard")
    void penalite() {
        assertThat(ReceptionCommandeService.penalite(new BigDecimal("2000000"), BigDecimal.ONE, 10, new BigDecimal("15"))).isEqualByComparingTo("20000");
        assertThat(ReceptionCommandeService.penalite(new BigDecimal("2000000"), BigDecimal.ONE, 400, new BigDecimal("10"))).isEqualByComparingTo("200000");
        assertThat(ReceptionCommandeService.penalite(new BigDecimal("2000000"), BigDecimal.ONE, 0, new BigDecimal("10"))).isEqualByComparingTo("0");
    }
}
