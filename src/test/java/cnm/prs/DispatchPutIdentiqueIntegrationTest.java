package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutPv;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ <strong>Audit 2026-09-14 (C1) — un PUT de dispatch identique ne transfère plus l'identité de
 * dispatcheur.</strong>
 *
 * <p>{@code DispatchService.update} réécrivait {@code IM_CTRL_DISPATCH} avec l'appelant à chaque PUT, même
 * sans rien changer. Or le visa lit le dispatcheur <em>courant</em> pour décider qui vise, qui est
 * intérimaire et si l'examinateur « cumule légitimement ». Deux scénarios en découlaient, sans aucune
 * trace ni au journal ni au chronométrage :</p>
 * <ul>
 *   <li><strong>(A) centrale</strong> — le CC attributaire d'un dossier confié par le Président renvoyait
 *       le dispatch tel quel, devenait dispatcheur, « cumulait » et visait son propre examen sans le
 *       Président ;</li>
 *   <li><strong>(B) régional</strong> — le Président, non dispatcheur, faisait de même et visait sans la
 *       note d'intérim que la règle du 2026-09-01 exige.</li>
 * </ul>
 * <p>Le dispatcheur n'est désormais réécrit qu'au <strong>changement d'attributaire</strong>.</p>
 */
class DispatchPutIdentiqueIntegrationTest extends CnmIntegrationTestSupport {

    /**
     * Dossier EXAMINE de PRMP001 dans {@code localite}, dispatché par {@code dispatcheur} à
     * {@code examinateur}, examen au nom de l'examinateur ; le projet de PV est créé puis SOUMIS par
     * l'examinateur. Navette simple (le dispatcheur n'est pas un CC réattribuant à un tiers).
     */
    private void projetSoumis(int id, String localite, String receptionnaire, String dispatcheur,
            String examinateur, String tokenExaminateur) throws Exception {
        dossierRepository.save(dossierLoc(id, "EXAMINE", localite, "PRMP001"));
        receptionRepository.save(reception(id, id, receptionnaire, true));
        dispatchRepository.save(dispatch(id, id, null, examinateur, dispatcheur));
        examenRepository.save(examen(id, id, examinateur));
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":" + id + ",\"idExamen\":" + id + ",\"idAvis\":\"FAV\",\"imCtrlMembre\":\""
                        + examinateur + "\",\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/" + id + "/soumettre").header("Authorization", tokenExaminateur)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"pret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));
    }

    /** PUT de dispatch sur la même réception, avec l'attributaire donné (inchangé = PUT identique). */
    private void putDispatch(int id, String token, String attributaire) throws Exception {
        mvc.perform(put("/api/dispatchs/" + id).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":" + id + ",\"idReception\":" + id + ",\"imCtrlMembre\":\""
                        + attributaire + "\",\"interimDispatch\":false}"))
                .andExpect(status().isOk());
    }

    private String dispatcheur(int id) {
        return dispatchRepository.findById(id).orElseThrow().getImCtrlDispatch();
    }

    @Test
    @DisplayName("C1 (A) centrale — le CC attributaire renvoie un PUT identique : le Président reste dispatcheur, "
            + "et le visa du CC sur son propre examen est refusé (403)")
    void centrale_putIdentiqueDuCcAttributaire_neLeRendPasDispatcheur_visa403() throws Exception {
        projetSoumis(9700, "ANT", "CTRCC1", "CTRPRE", "CTRCC1", tokenCc);

        putDispatch(9700, tokenCc, "CTRCC1");
        entityManager.flush();
        entityManager.clear();
        assertThat(dispatcheur(9700)).as("le dispatcheur ne suit pas l'auteur d'un PUT sans changement")
                .isEqualTo("CTRPRE");

        viser(9700, tokenCc, "CTRCC1", "FAV", null, "CTRMEM")
                .andExpect(status().isForbidden());
        assertThat(pvExamenRepository.findById(9700).orElseThrow().getStatutPv())
                .isEqualTo(StatutPv.PROJET_SOUMIS.name());
    }

    @Test
    @DisplayName("C1 (B) régional — le Président non dispatcheur renvoie un PUT identique : le CC reste "
            + "dispatcheur, et le visa JSON du Président exige toujours la note d'intérim (400)")
    void regional_putIdentiqueDuPresident_neLeRendPasDispatcheur_noteInterimRequise() throws Exception {
        controleurRepository.save(controleur("MEMTMS7", 5, "TMS"));
        String tokenMembreTms = bearer("MEMTMS7", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "MEMTMS7", "TMS");
        projetSoumis(9701, "TMS", "CTRCC2", "CTRCC2", "MEMTMS7", tokenMembreTms);

        putDispatch(9701, tokenPresident, "MEMTMS7");
        entityManager.flush();
        entityManager.clear();
        assertThat(dispatcheur(9701)).isEqualTo("CTRCC2");

        viser(9701, tokenPresident, "CTRPRE", "FAV", null, "MEMTMS7")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Note d'intérim requise")));
    }

    @Test
    @DisplayName("C1 — non-régression : la RÉATTRIBUTION (changement d'attributaire, examen non entamé) fait "
            + "toujours de l'auteur du PUT le dispatcheur")
    void reattribution_changeToujoursLeDispatcheur() throws Exception {
        dossierRepository.save(dossierLoc(9702, "DISPATCHE", "ANT", "PRMP001"));
        receptionRepository.save(reception(9702, 9702, "CTRCC1", true));
        dispatchRepository.save(dispatch(9702, 9702, null, "CTRCC1", "CTRPRE"));

        // Le CC à qui le Président a confié le dossier le réattribue à un Membre : il en devient le dispatcheur.
        putDispatch(9702, tokenCc, "CTRMEM");
        entityManager.flush();
        entityManager.clear();
        assertThat(dispatcheur(9702)).isEqualTo("CTRCC1");
    }
}
