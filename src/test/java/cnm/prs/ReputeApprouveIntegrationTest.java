package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Notification;
import cnm.prs.entity.TypeDmc;
import cnm.prs.service.ReputeApprouveService;

/**
 * ⚠️ R6b (manuel révisé pour l'application, lot 2, §B1 règle 5 ; arbitrage Q6 du pilote ; V107) — le plan réputé approuvé : un plan
 * soumis depuis dix jours ouvrables sans PV signé reçoit le marqueur daté, le journal et les notifications (PRMP, Président de la
 * localité) ; un plan récent ou déjà muni d'un PV signé n'est pas touché ; la création d'un DMC, refusée avant (PV_NON_SIGNE), passe
 * après ; le planificateur est idempotent.
 *
 * <p>Jeu : plans 9900 (soumis il y a 25 jours, sans PV), 9910 (soumis avant-hier), 9920 (soumis il y a 25 jours, PV signé) ; ligne
 * 9901 du plan 9900, mode 92 → DAO.</p>
 */
class ReputeApprouveIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired private ReputeApprouveService service;
    @Autowired private cnm.prs.repository.NotificationRepository notificationRepository;

    @BeforeEach
    void jeu() {
        TypeDmc dao = typeDmcRepository.findByCode("DAO").orElseThrow();
        ModePassation m92 = new ModePassation(92, "Appel d'offres ouvert", null, null, null, null);
        m92.setIdTypeDmc(dao.getIdTypeDmc());
        modePassationRepository.save(m92);
        plan(9900, "SOUMIS", LocalDate.now().minusDays(25));
        Marche l = marcheDao(9901, 9900, 9900);
        l.setIdMode(92);
        marcheRepository.save(l);
        plan(9910, "SOUMIS", LocalDate.now().minusDays(2));
        plan(9920, "PV_SIGNE", LocalDate.now().minusDays(25));
        receptionRepository.save(reception(9920, 9920, "CTRCC1", true));
        dispatchRepository.save(dispatch(9920, 9920, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9920, 9920, "CTRMEM"));
        seedPvSigne(9920, 9920);
    }

    @Test
    @DisplayName("Dix jours ouvrables sans PV signé : marqueur reputeApprouveLe, journal PLAN_REPUTE_APPROUVE, notifications PRMP et "
            + "Président ; plan récent et plan au PV signé intacts ; le DMC refusé (PV_NON_SIGNE) se crée ensuite ; second passage sans effet")
    void reputeApprouve() throws Exception {
        mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PV_NON_SIGNE"));
        assertThat(ReputeApprouveService.echu(LocalDate.now().minusDays(25).atStartOfDay(), LocalDate.now())).isTrue();
        assertThat(ReputeApprouveService.echu(LocalDate.now().minusDays(2).atStartOfDay(), LocalDate.now())).isFalse();

        assertThat(service.verifier()).isEqualTo(1);

        mvc.perform(get("/api/dossiers/9900").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reputeApprouveLe").isNotEmpty()).andExpect(jsonPath("$.statut").value("SOUMIS"));
        mvc.perform(get("/api/dossiers/9910").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reputeApprouveLe").isEmpty());
        mvc.perform(get("/api/dossiers/9920").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reputeApprouveLe").isEmpty());
        String journal = mvc.perform(get("/api/dossiers/9900/journal").header("Authorization", tokenPresident)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='PLAN_REPUTE_APPROUVE')].detail")).singleElement().asString()
                .contains("art. 26-IV", "10 jours ouvrables", "vaut observation");
        List<Notification> emises = notificationRepository.findAll().stream()
                .filter(n -> "PLAN_REPUTE_APPROUVE".equals(n.getTypeNotif())).toList();
        assertThat(emises).hasSizeGreaterThanOrEqualTo(2);
        assertThat(emises).extracting(Notification::getDestinataireIm).contains("CTRPRE");
        assertThat(emises).allSatisfy(n -> assertThat(n.getTitre()).isEqualTo("Plan de passation réputé approuvé"));

        mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp)).andExpect(status().isCreated());
        assertThat(service.verifier()).isZero();
    }

    private void plan(int id, String statut, LocalDate soumisLe) {
        dossierRepository.save(dossierLoc(id, statut, "ANT", "PRMP001"));
        Dossier d = dossierRepository.findById(id).orElseThrow();
        d.setIdTypeDossier("DDP");
        d.setIdEntiteContract(1);
        d.setDateSoumission(soumisLe.atTime(9, 0));
        dossierRepository.save(d);
        ppmRepository.save(ppm(id, id, "PRMP001"));
    }
}
