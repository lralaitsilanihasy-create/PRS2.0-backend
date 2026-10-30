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

    private static cnm.prs.entity.BonCommandeLigne ligne(int rang, String quantite, String pu) {
        cnm.prs.entity.BonCommandeLigne l = new cnm.prs.entity.BonCommandeLigne();
        l.setRang(rang);
        l.setQuantite(new BigDecimal(quantite));
        l.setPrixUnitaire(new BigDecimal(pu));
        return l;
    }

    private static cnm.prs.entity.ReceptionLigne rl(long reception, int rang, String livree, String recue) {
        cnm.prs.entity.ReceptionLigne l = new cnm.prs.entity.ReceptionLigne();
        l.setIdReception(reception);
        l.setRang(rang);
        l.setQuantiteLivree(new BigDecimal(livree));
        l.setQuantiteRecue(recue == null ? null : new BigDecimal(recue));
        return l;
    }

    @Test
    @DisplayName("⚠️ 2026-10-29 (bons-reception, §B3) — livré, reçu et reliquat par ligne : seuls les PV signés acceptés comptent au reçu ; "
            + "une réception d'avant V148 vaut tout le bon ; reliquat : RECU_PARTIEL")
    void receptionPartielle() {
        List<cnm.prs.entity.BonCommandeLigne> ls = List.of(ligne(1, "10", "1000"), ligne(2, "4", "2500"));
        ReceptionCommande premiere = r("PROVISOIRE", "SIGNE", "RECEPTION", LocalDate.of(2026, 2, 1));
        premiere.setId(1L);
        ReceptionCommande rejetee = r("PROVISOIRE", "SIGNE", "REJET", LocalDate.of(2026, 2, 5));
        rejetee.setId(2L);
        ReceptionCommande enCours = r("PROVISOIRE", "A_ETABLIR", null, null);
        enCours.setId(3L);
        java.util.Map<Long, List<cnm.prs.entity.ReceptionLigne>> par = java.util.Map.of(1L, List.of(rl(1, 1, "6", "6"), rl(1, 2, "0", "0")),
                2L, List.of(rl(2, 1, "4", "0")), 3L, List.of(rl(3, 1, "4", null), rl(3, 2, "4", null)));
        ReceptionCommandeService.Quantites q = ReceptionCommandeService.quantites(ls, List.of(premiere, rejetee, enCours), par);
        assertThat(q.livre()).containsEntry(1, new BigDecimal("14")).containsEntry(2, new BigDecimal("4"));
        assertThat(q.recu().get(1)).isEqualByComparingTo("6");
        assertThat(q.reliquat(1)).isEqualByComparingTo("4");
        assertThat(q.reliquat(2)).isEqualByComparingTo("4");
        assertThat(q.resteALivrer()).isTrue();
        assertThat(ReceptionCommandeService.etatReception(bon("10 jours"), List.of(premiere), 6, true)).isEqualTo("RECU_PARTIEL");
        assertThat(ReceptionCommandeService.etatReception(bon("10 jours"), List.of(premiere), 6, false)).isEqualTo("RECU_PROVISOIRE");
        assertThat(ReceptionCommandeService.valeur(ls, java.util.Map.of(1, new BigDecimal("6"), 2, BigDecimal.ONE))).isEqualByComparingTo("8500");
        // Avant V148 : une réception sans ligne vaut tout le bon.
        ReceptionCommandeService.Quantites tout = ReceptionCommandeService.quantites(ls, List.of(premiere), java.util.Map.of());
        assertThat(tout.resteALivrer()).isFalse();
    }

    @Test
    @DisplayName("⚠️ 2026-10-29 (§B3) — reçu au PV : au plus le livré, ligne omise = livré, au moins une positive ; ajournement ou rejet : rien")
    void quantitesRecues() {
        List<cnm.prs.entity.ReceptionLigne> livraison = List.of(rl(3, 1, "4", null), rl(3, 2, "2", null));
        assertThat(ReceptionCommandeService.recues(livraison, true, null)).containsEntry(1, new BigDecimal("4")).containsEntry(2, new BigDecimal("2"));
        assertThat(ReceptionCommandeService.recues(livraison, true, List.of(new cnm.prs.dto.ReceptionCommandeDto.LigneRecue(1, new BigDecimal("3")))))
                .containsEntry(1, new BigDecimal("3")).containsEntry(2, new BigDecimal("2"));
        assertThat(ReceptionCommandeService.recues(livraison, false, null).values()).allMatch(v -> v.signum() == 0);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ReceptionCommandeService.recues(livraison, true,
                List.of(new cnm.prs.dto.ReceptionCommandeDto.LigneRecue(1, new BigDecimal("5"))))).hasMessageContaining("va de 0");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ReceptionCommandeService.recues(livraison, true,
                List.of(new cnm.prs.dto.ReceptionCommandeDto.LigneRecue(1, BigDecimal.ZERO), new cnm.prs.dto.ReceptionCommandeDto.LigneRecue(2,
                        BigDecimal.ZERO)))).hasMessageContaining("au moins une quantité");
    }

    @Test
    @DisplayName("⚠️ 2026-10-29 (marche-a-commandes-champs) — le délai de garantie : B09-GT-01 pour un marché à commandes de travaux, "
            + "B09-DG-01 en fournitures, B09-GP-03 pour un contrat-cadre")
    void garantieParCategorie() {
        cnm.prs.dto.FicheMarcheDto d = new cnm.prs.dto.FicheMarcheDto();
        d.setValeurs(new java.util.HashMap<>(java.util.Map.of("B09-GT-01", "12", "B09-DG-01", "6", "B09-GP-03", "3")));
        cnm.prs.entity.ContratVigueur c = new cnm.prs.entity.ContratVigueur();
        c.setTypeMarche(cnm.prs.entity.ContratVigueur.A_COMMANDE);
        c.setLot(1);
        assertThat(ReceptionCommandeService.garantieMois(c, new FicheMarcheService.EtatVersion(1, d, java.util.Map.of(), "A_COMMANDE", "TRAVAUX")))
                .isEqualTo(12);
        assertThat(ReceptionCommandeService.garantieMois(c, new FicheMarcheService.EtatVersion(1, d, java.util.Map.of(), "A_COMMANDE",
                "FOURNITURES_SERVICES"))).isEqualTo(6);
        c.setTypeMarche(cnm.prs.entity.ContratVigueur.CONTRAT_CADRE);
        assertThat(ReceptionCommandeService.garantieMois(c, new FicheMarcheService.EtatVersion(1, d, java.util.Map.of(), "CONTRAT_CADRE",
                "TRAVAUX"))).isEqualTo(3);
    }

    private static FicheMarcheService.EtatVersion fiche(String categorie, java.util.Map<String, String> valeurs) {
        cnm.prs.dto.FicheMarcheDto d = new cnm.prs.dto.FicheMarcheDto();
        d.setValeurs(new java.util.HashMap<>(valeurs));
        return new FicheMarcheService.EtatVersion(1, d, java.util.Map.of(), "A_COMMANDE", categorie);
    }

    @Test
    @DisplayName("⚠️ 2026-10-30 (ccag-penalites-reception, §B1, §B4) — taux par défaut : CCAP (B09-PE-02) sinon CCAG de la catégorie (1 ‰, "
            + "0,5 ‰ en travaux et PI) ; garantie de 12 mois en travaux sans B09-GT-01")
    void ccagParCategorie() {
        assertThat(ReceptionCommandeService.tauxDefaut(fiche("FOURNITURES_SERVICES", java.util.Map.of()), 1).taux()).isEqualByComparingTo("1");
        assertThat(ReceptionCommandeService.tauxDefaut(fiche("TRAVAUX", java.util.Map.of()), 1))
                .satisfies(t -> assertThat(t.taux()).isEqualByComparingTo("0.5")).satisfies(t -> assertThat(t.source()).contains("art. 20.1"));
        assertThat(ReceptionCommandeService.tauxDefaut(fiche("PRESTATIONS_INTELLECTUELLES", java.util.Map.of()), 1).source()).contains("art. 16.1");
        assertThat(ReceptionCommandeService.tauxDefaut(fiche("TRAVAUX", java.util.Map.of("B09-PE-02", "1/2000")), 1))
                .satisfies(t -> assertThat(t.taux()).isEqualByComparingTo("0.5")).satisfies(t -> assertThat(t.source()).contains("CCAP"));
        assertThat(ReceptionCommandeService.tauxCcap("2")).isEqualByComparingTo("2");
        assertThat(ReceptionCommandeService.tauxCcap("un millième")).isNull();
        cnm.prs.entity.ContratVigueur c = new cnm.prs.entity.ContratVigueur();
        c.setTypeMarche(cnm.prs.entity.ContratVigueur.A_COMMANDE);
        c.setLot(1);
        assertThat(ReceptionCommandeService.garantieMois(c, fiche("TRAVAUX", java.util.Map.of()))).isEqualTo(12);
        assertThat(ReceptionCommandeService.garantieMois(c, fiche("TRAVAUX", java.util.Map.of("B09-GT-01", "6")))).isEqualTo(6);
        assertThat(ReceptionCommandeService.garantieMois(c, fiche("FOURNITURES_SERVICES", java.util.Map.of()))).isNull();
    }

    @Test
    @DisplayName("⚠️ 2026-10-30 (§B3, CCAG art. 22.2) — une définitive partielle par livraison : le bon n'est RECU_DEFINITIF qu'avec la "
            + "définitive de chaque livraison reçue ; la définitive d'une livraison est due à sa propre garantie ; échéance de décision J+15")
    void definitivesPartielles() {
        BonCommande b = bon("10 jours");
        ReceptionCommande p1 = r("PROVISOIRE", "SIGNE", "RECEPTION", LocalDate.of(2026, 2, 1));
        p1.setId(1L);
        ReceptionCommande p2 = r("PROVISOIRE", "SIGNE", "RECEPTION", LocalDate.of(2026, 5, 1));
        p2.setId(2L);
        ReceptionCommande d1 = r("DEFINITIVE", "SIGNE", "RECEPTION", LocalDate.of(2026, 8, 2));
        d1.setId(3L);
        d1.setIdProvisoire(1L);
        assertThat(ReceptionCommandeService.etatReception(b, List.of(p1, p2), 6, false)).isEqualTo("RECU_PROVISOIRE");
        assertThat(ReceptionCommandeService.etatReception(b, List.of(p1, p2, d1), 6, false)).isEqualTo("RECU_PROVISOIRE");   // p2 sans définitive
        assertThat(ReceptionCommandeService.definitivesAPrononcer(List.of(p1, p2, d1), 6, LocalDate.of(2026, 9, 1))).isEmpty();
        assertThat(ReceptionCommandeService.definitivesAPrononcer(List.of(p1, p2, d1), 6, LocalDate.of(2026, 11, 1))).containsExactly(p2);
        ReceptionCommande d2 = r("DEFINITIVE", "SIGNE", "RECEPTION", LocalDate.of(2026, 11, 2));
        d2.setId(4L);
        d2.setIdProvisoire(2L);
        assertThat(ReceptionCommandeService.etatReception(b, List.of(p1, p2, d1, d2), 6, false)).isEqualTo("RECU_DEFINITIF");
        // Bon reçu en partie : la définitive de la première livraison est due sans attendre le reste.
        assertThat(ReceptionCommandeService.etatReception(b, List.of(p1), 6, true)).isEqualTo("RECU_PARTIEL");
        assertThat(ReceptionCommandeService.definitivesAPrononcer(List.of(p1), 6, LocalDate.of(2026, 8, 1))).containsExactly(p1);
        ReceptionCommande livree = r("PROVISOIRE", "A_ETABLIR", null, null);
        livree.setDateLivraison(LocalDate.of(2026, 3, 1));
        assertThat(ReceptionCommandeService.decisionAvantLe(livree)).isEqualTo(LocalDate.of(2026, 3, 16));
        assertThat(ReceptionCommandeService.decisionAvantLe(d1)).isNull();
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
