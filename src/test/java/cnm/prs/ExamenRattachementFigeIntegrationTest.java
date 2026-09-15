package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ <strong>Revue du 2026-09-14 — un PUT déplaçait un résultat vers un examen au PV signé.</strong>
 *
 * <p>{@code PUT /api/examen-details/{id}} (et {@code /api/examen-pieces/{id}}, même code) vérifiait
 * l'attributaire de l'examen visé par le corps, mais le verrou d'état seulement sur l'examen en place : le
 * Membre attributaire de deux examens rattachait un résultat à celui dont le PV était signé, et recevait 200.
 * Le rattachement est désormais figé : un changement d'examen au PUT est refusé en 400 (champ
 * {@code idExamen}), que l'examen visé soit verrouillé ou non ; l'identité reste vérifiée d'abord (403).</p>
 *
 * <p>Décor : examen 1 du socle (dossier 1 {@code EXAMINE}, ANT, attributaire {@code CTRMEM}) ; examen 3
 * (dossier 3 {@code PV_SIGNE}) et examen 4 (dossier 4 {@code EXAMINE}), tous deux en ANT et attribués au même
 * Membre.</p>
 */
class ExamenRattachementFigeIntegrationTest extends CnmIntegrationTestSupport {

    private static final int POINT = 7790;
    private static final int RESULTAT = 7790;
    private static final int EXAMEN_SIGNE = 3;
    private static final int EXAMEN_OUVERT = 4;

    @BeforeEach
    void resultatEtDeuxAutresExamensDuMemeMembre() {
        PointsCtrl pc = new PointsCtrl();
        pc.setIdPointCtrl(POINT);
        pc.setLibelPointCtrl("Point " + POINT);
        pc.setObligatoire(true);
        pc.setIdTypeDossier("DDP");
        pointsCtrlRepository.save(pc);
        ExamenDetail d = new ExamenDetail();
        d.setIdDetailExamen(RESULTAT);
        d.setIdExamen(1);
        d.setIdPtControle(POINT);
        d.setConforme(true);
        examenDetailRepository.save(d);

        examenDuMembre(EXAMEN_SIGNE, "PV_SIGNE");
        examenDuMembre(EXAMEN_OUVERT, "EXAMINE");
    }

    @Test
    @DisplayName("examen-details PUT — la sonde du 2026-09-14 : rattacher le résultat à l'examen au PV signé → 400 "
            + "idExamen (était 200) ; le résultat reste sur son examen")
    void examenDetails_versExamenAuPvSigne_400() throws Exception {
        mvc.perform(put("/api/examen-details/" + RESULTAT).header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(corpsResultat(EXAMEN_SIGNE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idExamen"));
        assertThat(examenDetailRepository.findById(RESULTAT).orElseThrow().getIdExamen()).isEqualTo(1);
    }

    @Test
    @DisplayName("examen-details PUT — rattachement figé même vers un examen OUVERT du même Membre (400) ; un autre "
            + "Membre reçoit d'abord 403 ; le PUT sur le même examen reste 200")
    void examenDetails_rattachementFige_identiteDabord_nonRegression() throws Exception {
        mvc.perform(put("/api/examen-details/" + RESULTAT).header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(corpsResultat(EXAMEN_OUVERT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idExamen"));

        String tokenAutreMembre = bearer("CTRMEM2", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM2", "ANT");
        mvc.perform(put("/api/examen-details/" + RESULTAT).header("Authorization", tokenAutreMembre)
                .contentType(MediaType.APPLICATION_JSON).content(corpsResultat(EXAMEN_OUVERT)))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/examen-details/" + RESULTAT).header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(corpsResultat(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idExamen").value(1));
        assertThat(examenDetailRepository.findById(RESULTAT).orElseThrow().getIdExamen()).isEqualTo(1);
    }

    @Test
    @DisplayName("examen-pieces PUT — même trou, même règle : vers l'examen au PV signé ou un examen ouvert → 400 "
            + "idExamen ; sur le même examen → 200")
    void examenPieces_rattachementFige() throws Exception {
        mvc.perform(post("/api/examen-pieces").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamenPiece\":7791,\"idExamen\":1,\"idPiece\":1,\"conforme\":true}"))
                .andExpect(status().isCreated());

        for (int examen : new int[] { EXAMEN_SIGNE, EXAMEN_OUVERT }) {
            mvc.perform(put("/api/examen-pieces/7791").header("Authorization", tokenMembre)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idExamenPiece\":7791,\"idExamen\":" + examen + ",\"idPiece\":1,\"conforme\":false}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erreurs[0].champ").value("idExamen"));
        }
        mvc.perform(put("/api/examen-pieces/7791").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamenPiece\":7791,\"idExamen\":1,\"idPiece\":1,\"conforme\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idExamen").value(1));
    }

    /** Dossier, réception (ANT), dispatch et examen {@code id}, attribués à CTRMEM. */
    private void examenDuMembre(int id, String statutDossier) {
        dossierRepository.save(dossier(id, statutDossier));
        receptionRepository.save(reception(id, id, "CTRCC1", true));
        dispatchRepository.save(dispatch(id, id, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(id, id, "CTRMEM"));
    }

    private static String corpsResultat(int idExamen) {
        return "{\"idDetailExamen\":" + RESULTAT + ",\"idExamen\":" + idExamen + ",\"idPtControle\":" + POINT
                + ",\"conforme\":true}";
    }
}
