package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.entity.DelaiParametre;
import cnm.prs.repository.DelaiParametreRepository;

/**
 * ⚠️ R6a (manuel révisé, lot 2, §B1 ; arbitrage Q6) — l'exigence de publicité par mode (loi ou paramètre), le constat (le jour de
 * départ n'est pas compté) et la table des neuf délais légaux.
 */
class DelaisPubliciteTest {

    private final DelaiParametreRepository repo = mock(DelaiParametreRepository.class);
    private final DelaisPublicite delais = new DelaisPublicite(repo);

    @Test
    @DisplayName("Table de la loi et du décret 2019-1064 : onze délais numérotés 1 à 11, les cinq de publicité bloquants (30 / 45 / 10 jours ; contrat-cadre 10 / 5)")
    void table() {
        assertThat(DelaisLegaux.TABLE).hasSize(11).extracting(d -> d.numero()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        assertThat(DelaisLegaux.par(DelaisLegaux.PUBLICITE_CONTRAT_CADRE_AO).orElseThrow().valeur()).isEqualTo(10);   // 15/10 : décret, art. 10
        assertThat(DelaisLegaux.par(DelaisLegaux.PUBLICITE_CONTRAT_CADRE_CONSULTATION).orElseThrow().valeur()).isEqualTo(5);
        assertThat(DelaisLegaux.par(DelaisLegaux.PUBLICITE_AOO_NATIONAL).orElseThrow().valeur()).isEqualTo(30);
        assertThat(DelaisLegaux.par(DelaisLegaux.PUBLICITE_AOO_INTERNATIONAL).orElseThrow().valeur()).isEqualTo(45);
        assertThat(DelaisLegaux.par(DelaisLegaux.PUBLICITE_CONSULTATION).orElseThrow().valeur()).isEqualTo(10);
        assertThat(DelaisLegaux.par(DelaisLegaux.PLAN_REPUTE_APPROUVE).orElseThrow().unite()).isEqualTo("JOURS_OUVRABLES");
        assertThat(DelaisLegaux.par(DelaisLegaux.ATTENTE_SIGNATURE).orElseThrow().unite()).isEqualTo("JOURS_FRANCS");
    }

    @Test
    @DisplayName("Exigence par mode : AOO national 30 j (loi, bloquant), international 45 j, consultation 10 j ; restreint, PI, AMI, "
            + "pré-qualification et délai aménagé → paramètre signalé (inactif ou absent : aucune exigence) ; contrat-cadre : décret, 10 j "
            + "par appel d'offres, 5 j par consultation, bloquant ; "
            + "gré à gré et achat direct : aucune")
    void exigences() {
        when(repo.findById("PUBLICITE_AOR")).thenReturn(Optional.of(new DelaiParametre("PUBLICITE_AOR", 15, "JOURS", "AOR", true, null, null)));
        when(repo.findById("PUBLICITE_DC_PI")).thenReturn(Optional.of(new DelaiParametre("PUBLICITE_DC_PI", 30, "JOURS", null, true, null, null)));
        when(repo.findById("DELAI_AMENAGE")).thenReturn(Optional.of(new DelaiParametre("DELAI_AMENAGE", 15, "JOURS", "aménagé", false, null, null)));
        when(repo.findById("PUBLICITE_CONTRAT_CADRE")).thenReturn(Optional.empty());

        DelaisPublicite.Exigence aoo = delais.exigence("Appel d'offres ouvert", false, "FOURNITURES_SERVICES", "QUANTITE_FIXE");
        assertThat(aoo.code()).isEqualTo(DelaisLegaux.PUBLICITE_AOO_NATIONAL);
        assertThat(aoo.requis()).isEqualTo(30);
        assertThat(aoo.legal()).isTrue();
        assertThat(aoo.bloquant()).isTrue();
        assertThat(delais.exigence("APPEL D'OFFRE OUVERT INTERNATIONAL", false, "TRAVAUX", "QUANTITE_FIXE").requis()).isEqualTo(45);
        assertThat(delais.exigence("Consultation des Prix Ouverte", false, "FOURNITURES_SERVICES", "A_COMMANDE").requis()).isEqualTo(10);
        assertThat(delais.exigence("CONSULTATION DE PRIX OUVERTE PIP", false, null, null).code()).isEqualTo(DelaisLegaux.PUBLICITE_CONSULTATION);

        DelaisPublicite.Exigence aor = delais.exigence("Appel d'offres restreint", false, "FOURNITURES_SERVICES", "QUANTITE_FIXE");
        assertThat(aor.code()).isEqualTo("PUBLICITE_AOR");
        assertThat(aor.requis()).isEqualTo(15);
        assertThat(aor.legal()).isFalse();
        assertThat(aor.bloquant()).isFalse();
        assertThat(aor.libelle()).isEqualTo("AOR");
        assertThat(delais.exigence("Appel d'offres ouvert", false, "PRESTATIONS_INTELLECTUELLES", "QUANTITE_FIXE").code()).isEqualTo("PUBLICITE_DC_PI");
        DelaisPublicite.Exigence cc = delais.exigence("Appel d'offres ouvert", false, "FOURNITURES_SERVICES", "CONTRAT_CADRE");
        assertThat(cc.code()).isEqualTo(DelaisLegaux.PUBLICITE_CONTRAT_CADRE_AO);   // 15/10 : le paramètre (absent ici) n'est plus lu
        assertThat(cc.requis()).isEqualTo(10);
        assertThat(cc.bloquant()).isTrue();
        assertThat(delais.exigence("Consultation de prix ouverte", false, "FOURNITURES_SERVICES", "CONTRAT_CADRE").requis()).isEqualTo(5);
        assertThat(delais.exigence("Appel d'offres ouvert", true, "FOURNITURES_SERVICES", "QUANTITE_FIXE")).isNull();   // DELAI_AMENAGE inactif
        assertThat(delais.exigence("Gré à gré", false, "FOURNITURES_SERVICES", "QUANTITE_FIXE")).isNull();
        assertThat(delais.exigence("Achat Direct", false, null, null)).isNull();
        assertThat(delais.exigence(null, false, null, null)).isNull();
    }

    @Test
    @DisplayName("Constat : le jour de départ n'est pas compté — 30 jours requis : limite au 31e jour conforme, au 30e non ; une date "
            + "absente : non évaluable")
    void constat() {
        DelaisPublicite.Exigence aoo = delais.exigence("Appel d'offres ouvert", false, "FOURNITURES_SERVICES", "QUANTITE_FIXE");
        LocalDate depart = LocalDate.of(2026, 10, 1);
        DelaisPublicite.Constat ok = DelaisPublicite.constater(aoo, depart, LocalDate.of(2026, 10, 31));
        assertThat(ok.constate()).isEqualTo(30);
        assertThat(ok.conforme()).isTrue();
        DelaisPublicite.Constat court = DelaisPublicite.constater(aoo, depart, LocalDate.of(2026, 10, 30));
        assertThat(court.constate()).isEqualTo(29);
        assertThat(court.conforme()).isFalse();
        assertThat(DelaisPublicite.message(court, "la date de publication")).contains("29 jour(s)", "30 jour(s) requis", "art. 35-II",
                "date limite trop proche");
        assertThat(DelaisPublicite.constater(aoo, null, LocalDate.of(2026, 10, 30))).isNull();
        assertThat(DelaisPublicite.constater(aoo, depart, null)).isNull();
        assertThat(DelaisPublicite.constater(null, depart, depart)).isNull();
        assertThat(DelaisPublicite.dateLimite(java.util.Map.of("B04-OV-02", "2026-11-03T10:30"))).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(DelaisPublicite.champLimite(java.util.Map.of("B04-LH-02", "2026-11-03T10:30"))).isEqualTo("B04-LH-02");
        assertThat(DelaisPublicite.champLimite(java.util.Map.of())).isNull();
    }
}
