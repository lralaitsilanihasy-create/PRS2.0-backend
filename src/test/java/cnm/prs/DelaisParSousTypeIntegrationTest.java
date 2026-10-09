package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import cnm.prs.entity.Dossier;
import cnm.prs.enums.EtapeCircuit;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M5b, §B6 ; V99) — les délais par sous-type (surcharge réglée par l'Administrateur,
 * retirée, prise en compte par le calcul) et l'alerte d'examen au-delà de 5 jours ouvrés (Membre, Chef de commission, Président ; une
 * seule par passage).
 */
class DelaisParSousTypeIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired private cnm.prs.service.DelaiStandardService delais;
    @Autowired private cnm.prs.service.AlerteExamenService alertes;
    @Autowired private cnm.prs.repository.AlerteExamenRepository alerteRepository;
    @Autowired private cnm.prs.repository.NotificationRepository notificationRepository;

    @Test
    @DisplayName("Surcharge d'une étape pour un sous-type : Administrateur seul, lue par le calcul, retirée")
    void surcharges() throws Exception {
        int standardExamen = delais.delais().get(EtapeCircuit.EXAMEN);
        mvc.perform(get("/api/delais-standards/sous-types/DAOO").header("Authorization", tokenMembre)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.etape=='EXAMEN')].surcharge").value(false))
                .andExpect(jsonPath("$[?(@.etape=='RECTIFICATION_PRMP')]").isEmpty());
        mvc.perform(put("/api/delais-standards/sous-types/DAOO/EXAMEN").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiHeures\":16}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/delais-standards/sous-types/DAOO/EXAMEN").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiHeures\":0}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/delais-standards/sous-types/INCONNU/EXAMEN").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiHeures\":16}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/delais-standards/sous-types/DAOO/RECTIFICATION_PRMP").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiHeures\":16}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/delais-standards/sous-types/DAOO/EXAMEN").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiHeures\":16}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.delaiHeures").value(16)).andExpect(jsonPath("$.surcharge").value(true));
        mvc.perform(get("/api/delais-standards/sous-types/DAOO").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.etape=='EXAMEN')].delaiHeures").value(16))
                .andExpect(jsonPath("$[?(@.etape=='EXAMEN')].standardHeures").value(standardExamen));

        assertThat(delais.delais("DAOO").get(EtapeCircuit.EXAMEN)).isEqualTo(16);
        assertThat(delais.delais("DAOR").get(EtapeCircuit.EXAMEN)).isEqualTo(standardExamen);
        assertThat(delais.delais(null).get(EtapeCircuit.EXAMEN)).isEqualTo(standardExamen);

        mvc.perform(delete("/api/delais-standards/sous-types/DAOO/EXAMEN").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        assertThat(delais.delais("DAOO").get(EtapeCircuit.EXAMEN)).isEqualTo(standardExamen);
    }

    @Test
    @DisplayName("Examen au-delà de 5 jours ouvrés : alerte au Membre, au CC et au Président, une seule fois par passage")
    void alerte() {
        Dossier ancien = dossierLoc(97200, "DISPATCHE", "ANT", "PRMP001");
        ancien.setIdTypeDossier("DMC");
        ancien.setIdSousType("DAOO");
        ancien.setDateSoumission(LocalDateTime.now().minusDays(20));
        dossierRepository.save(ancien);
        receptionRepository.save(reception(97200, 97200, "CTRSEC", true));
        dispatchRepository.save(dispatch(97200, 97200, "CTRCC1", "CTRMEM"));

        Dossier recent = dossierLoc(97201, "DISPATCHE", "ANT", "PRMP001");
        recent.setIdTypeDossier("DMC");
        recent.setIdSousType("DAOO");
        recent.setDateSoumission(LocalDateTime.now());
        dossierRepository.save(recent);

        alertes.verifier();
        assertThat(alerteRepository.findByIdDossierOrderByIdAlerteAsc(97200)).hasSize(1);
        assertThat(alerteRepository.findByIdDossierOrderByIdAlerteAsc(97201)).isEmpty();
        assertThat(notificationRepository.findAll()).filteredOn(n -> "EXAMEN_EN_DEPASSEMENT".equals(String.valueOf(n.getTypeNotif()))
                && Integer.valueOf(97200).equals(n.getIdDossier())).extracting(n -> n.getDestinataireRef())
                .containsExactlyInAnyOrder("CTRMEM", "CTRCC1", "CTRPRE");

        // Le même passage n'est pas signalé deux fois.
        alertes.verifier();
        assertThat(alerteRepository.findByIdDossierOrderByIdAlerteAsc(97200)).hasSize(1);
    }
}
