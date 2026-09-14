package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.SuspensionDossier;
import cnm.prs.entity.TacheDossier;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.repository.SuspensionDossierRepository;
import cnm.prs.repository.TacheDossierRepository;
import cnm.prs.service.ChronometrageService;
import cnm.prs.service.ChronometrageService.DelaiCourant;
import cnm.prs.service.DelaiStandardService;

/**
 * ⚠️ <strong>Concordance du délai courant avec le chronométrage servi</strong> (demande front du 2026-09-14,
 * accueil « À faire », §4 et test attendu 9) — pour un dossier en cours, {@code delaiCourant.ecouleHeures}
 * égale le {@code dureeHeuresOuvrees} du passage {@code enCours} de {@code GET /api/dossiers/{id}/chronometrage},
 * et l'entrée est la même.
 *
 * <p><strong>Horloge figée</strong> au lundi 2026-09-14 15:00 ({@link HorlogeTest}) : l'écoulé se compte en heures
 * pleines, une horloge système pourrait franchir une heure entre la lecture HTTP et le calcul. C'est aussi ce qui
 * vérifie que le service lit bien l'horloge injectée — pour sa restitution comme pour ses écritures.</p>
 *
 * <p>Contexte Spring distinct de {@code CnmIntegrationTestSupport} (bean {@code Clock} remplacé), comme
 * {@code AlerteSchedulerIntegrationTest}.</p>
 */
class DelaiCourantConcordanceIntegrationTest extends CnmIntegrationTestSupport {

    private static final LocalDate JEUDI = LocalDate.of(2026, 9, 10);
    private static final LocalDate VENDREDI = LocalDate.of(2026, 9, 11);
    private static final LocalDateTime MAINTENANT = LocalDate.of(2026, 9, 14).atTime(15, 0);

    @TestConfiguration
    static class HorlogeTest {
        // Nom de bean différent de `clock` (ClockConfig) : @Primary départage l'injection par type.
        @Bean
        @Primary
        Clock horlogeDuDelaiCourant() {
            return new HorlogeMutable(MAINTENANT.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired private ChronometrageService chronometrageService;
    @Autowired private DelaiStandardService delaiStandardService;
    @Autowired private TacheDossierRepository tacheRepository;
    @Autowired private SuspensionDossierRepository suspensionRepository;

    @Test
    @DisplayName("PRET_DISPATCH entré vendredi 14:00 — l'écoulé et l'entrée du délai courant sont ceux du passage "
            + "enCours servi (9 h), le reste −1 et l'échéance lundi 14:00")
    void pretDispatch_concordance() throws Exception {
        dossier(500, "PRET_DISPATCH", JEUDI.atTime(10, 5));
        passage(500, "RECEPTION", VENDREDI.atTime(14, 0));

        String corps = mvc.perform(get("/api/dossiers/500/chronometrage").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        DelaiCourant delai = delaiCourantDe(500, null);

        assertThat(JsonPath.<String>read(corps, "$.etapes[-1].etape")).isEqualTo("DISPATCH");
        assertThat(JsonPath.<Boolean>read(corps, "$.etapes[-1].enCours")).isTrue();
        assertThat(delai.etape()).isEqualTo(EtapeCircuit.DISPATCH);
        assertThat(delai.ecouleHeures()).isEqualTo(9L)
                .isEqualTo(JsonPath.<Integer>read(corps, "$.etapes[-1].dureeHeuresOuvrees").longValue());
        assertThat(delai.entree()).isEqualTo(VENDREDI.atTime(14, 0))
                .isEqualTo(LocalDateTime.parse(JsonPath.<String>read(corps, "$.etapes[-1].entree")));
        assertThat(delai.standardHeures()).isEqualTo(delaiStandardService.delais().get(EtapeCircuit.DISPATCH));
        assertThat(delai.restantHeures()).isEqualTo(delai.standardHeures() - 9L);
        // Et la date annoncée par le GET est celle que calcule le service sur les mêmes données.
        assertThat(JsonPath.<String>read(corps, "$.datePrevisionnelleFin")).isEqualTo(
                chronometrageService.datePrevisionnelleFin("PRET_DISPATCH", null, tacheRepository.findParDossier(500),
                        List.of(), JEUDI.atTime(10, 5), MAINTENANT, delaiStandardService.delais()).toString());
    }

    @Test
    @DisplayName("EN_ATTENTE_DECISION_PRMP — la rectification en cours concorde aussi, et la pause part du début de "
            + "la fenêtre ouverte")
    void rectification_concordanceEtPause() throws Exception {
        dossier(501, "EN_ATTENTE_DECISION_PRMP", JEUDI.atTime(8, 0));
        passage(501, "VERIFICATION", VENDREDI.atTime(10, 0));
        SuspensionDossier attente = new SuspensionDossier();
        attente.setIdSuspension(suspensionRepository.nextId());
        attente.setIdDossier(501);
        attente.setStatut("EN_ATTENTE_DECISION_PRMP");
        attente.setDebut(VENDREDI.atTime(10, 0));
        suspensionRepository.save(attente);

        String corps = mvc.perform(get("/api/dossiers/501/chronometrage").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        DelaiCourant delai = delaiCourantDe(501, "SIGNE");

        assertThat(JsonPath.<String>read(corps, "$.etapes[-1].etape")).isEqualTo("RECTIFICATION_PRMP");
        assertThat(delai.etape()).isEqualTo(EtapeCircuit.RECTIFICATION_PRMP);
        assertThat(delai.ecouleHeures())
                .isEqualTo(JsonPath.<Integer>read(corps, "$.etapes[-1].dureeHeuresOuvrees").longValue())
                .isEqualTo(13L);
        assertThat(delai.pauseDepuis()).isEqualTo(VENDREDI.atTime(10, 0));
        assertThat(delai.pauseHeures()).isEqualTo(13L);
    }

    @Test
    @DisplayName("Horloge injectée — une fin d'étape écrite par le service porte l'instant de l'horloge, pas celui "
            + "de la machine")
    void ecriture_litLHorlogeInjectee() throws Exception {
        dossier(502, "SOUMIS", JEUDI.atTime(10, 0));
        chronometrageService.cloturer(502, EtapeCircuit.RECEPTION);

        List<TacheDossier> passages = tacheRepository.findParDossier(502);
        assertThat(passages).hasSize(1);
        assertThat(passages.get(0).getDateFin()).isEqualTo(MAINTENANT);
    }

    // ------------------------------------------------------------------ fixture

    private DelaiCourant delaiCourantDe(int idDossier, String statutPv) {
        Dossier d = dossierRepository.findById(idDossier).orElseThrow();
        return chronometrageService.delaiCourant(d.getStatut(), statutPv, tacheRepository.findParDossier(idDossier),
                suspensionRepository.findByIdDossierOrderByDebutAsc(idDossier), d.getDateSoumission(),
                LocalDateTime.now(clock), delaiStandardService.delais());
    }

    @Autowired private Clock clock;

    private void dossier(int id, String statut, LocalDateTime depot) {
        Dossier d = dossier(id, statut);
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        d.setDateSoumission(depot);
        dossierRepository.save(d);
    }

    private void passage(int idDossier, String etape, LocalDateTime fin) {
        TacheDossier t = new TacheDossier();
        t.setIdTache(tacheRepository.nextId());
        t.setIdDossier(idDossier);
        t.setEtape(etape);
        t.setOccurrence(1);
        t.setImActeur("CTRSEC");
        t.setProfil("SECRETAIRE");
        t.setDateFin(fin);
        tacheRepository.save(t);
    }
}
