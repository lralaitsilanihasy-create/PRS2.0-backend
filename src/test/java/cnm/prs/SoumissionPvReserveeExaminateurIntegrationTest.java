package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import cnm.prs.entity.PvExamen;
import cnm.prs.enums.StatutPv;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.PvNavetteRepository;

/**
 * ⚠️ <strong>La soumission du projet de PV est réservée à l'examinateur</strong> (constat et arbitrage
 * du pilote, 2026-09-08, dossier 00305 —
 * {@code docs/demande-backend-2026-09-08-soumission-pv-reservee-examinateur.md}).
 *
 * <p>Un Président dispatcheur avait pu soumettre le projet de PV d'un examen mené par le CC à qui il
 * avait redispatché le dossier : la navette a consigné son nom, et le journal l'a désigné comme auteur
 * de la « soumission d'examen ». La garde du rédacteur admettait ce geste par sa branche de
 * <em>délégation ascendante</em> vers Membre — celle qui existe pour que la Commission ne reste pas
 * bloquée. Mais soumettre n'est pas dépanner : c'est engager l'examen, qui est le travail de son
 * assignataire.</p>
 *
 * <p>Ce que ces tests protègent : le <strong>200 de l'examinateur</strong> et le <strong>403 du
 * dispatcheur</strong>, sur la première soumission comme sur la re-soumission après rectification ;
 * l'<strong>absence d'effet</strong> d'un appel refusé (ni navette, ni changement de statut) ; le fait
 * que l'acteur consigné est l'<strong>appelant authentifié</strong> et jamais le champ du corps ; et le
 * cas du <strong>circuit court</strong>, où le P/CC auto-attribué soumet le PV de son propre examen.</p>
 */
class SoumissionPvReserveeExaminateurIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private PvExamenRepository pvExamenRepository;
    @Autowired
    private PvNavetteRepository pvNavetteRepository;

    /** Projet de PV en BROUILLON sur l'examen 1 du socle, mené par le Membre CTRMEM. */
    private void projetDuMembre(int idPv) throws Exception {
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":" + idPv + ",\"idExamen\":1,\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions soumettre(int idPv, String token,
            String imActeur) throws Exception {
        return mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"" + imActeur + "\",\"commentaire\":\"go\"}"));
    }

    // ------------------------------------------------------------------ 1. l'examinateur soumet

    @Test
    @DisplayName("Contre-recette 1 — la soumission par l'examinateur passe (200) et la navette porte SON "
            + "matricule : l'acteur consigné est l'appelant authentifié")
    void soumissionParLExaminateur_passe() throws Exception {
        projetDuMembre(300);
        soumettre(300, tokenMembre, "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));

        mvc.perform(get("/api/pv-navettes").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPv==300 && @.sens=='SOUMISSION')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPv==300)].imActeur", hasItem("CTRMEM")));
    }

    // ------------------------------------------------------------------ 2. le dispatcheur est refusé

    @Test
    @DisplayName("Contre-recette 2 — le Président dispatcheur est refusé en 403, et le CC aussi : la "
            + "délégation vers Membre ne porte plus la soumission ; aucune navette, statut inchangé")
    void soumissionParLeDispatcheur_refusee() throws Exception {
        projetDuMembre(301);

        soumettre(301, tokenPresident, "CTRPRE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("réservée à l'examinateur")));
        soumettre(301, tokenCc, "CTRCC1")
                .andExpect(status().isForbidden());

        // Refus = aucun effet : ni navette consignée, ni statut avancé.
        assertThat(navettesDu(301)).isEmpty();
        assertThat(pvExamenRepository.findById(301).orElseThrow().getStatutPv())
                .isEqualTo(StatutPv.BROUILLON.name());
    }

    @Test
    @DisplayName("⚠️ Le corps ne fait pas l'acteur — le dispatcheur qui prétend être l'examinateur "
            + "(imActeur du corps = l'assignataire) reste refusé : c'est le jeton qui décide")
    void imActeurDuCorps_neContournePasLaGarde() throws Exception {
        projetDuMembre(302);
        soumettre(302, tokenPresident, "CTRMEM")   // se déclare examinateur dans le corps
                .andExpect(status().isForbidden());
        assertThat(navettesDu(302)).isEmpty();
    }

    // ------------------------------------------------------------------ 3. re-soumission

    @Test
    @DisplayName("Contre-recette 3 — après retour en rectification, la RE-soumission suit la même règle : "
            + "refusée au dispatcheur, acceptée à l'examinateur qui a rectifié")
    void resoumissionApresRectification_memeGarde() throws Exception {
        projetDuMembre(303);
        soumettre(303, tokenMembre, "CTRMEM").andExpect(status().isOk());
        // Le P/CC renvoie le projet en rectification — geste qui, lui, reste le sien.
        mvc.perform(post("/api/pv-examens/303/retourner").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRPRE\",\"commentaire\":\"a corriger\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("EN_RECTIFICATION"));

        soumettre(303, tokenPresident, "CTRPRE").andExpect(status().isForbidden());
        soumettre(303, tokenMembre, "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));
    }

    // ------------------------------------------------------------------ 4. circuit court

    @Test
    @DisplayName("Circuit court — le P/CC AUTO-ATTRIBUÉ au dispatch soumet le PV de SON propre examen : "
            + "la garde vise l'examinateur, pas le profil")
    void circuitCourt_leCcAutoAttribue_soumetSonPropreExamen() throws Exception {
        // Le PV porte le CC comme examinateur : c'est lui l'assignataire, quel que soit son profil.
        PvExamen pv = new PvExamen();
        pv.setIdPv(304);
        pv.setIdExamen(1);
        pv.setImCtrlMembre("CTRCC1");
        pv.setStatutPv(StatutPv.BROUILLON.name());
        pv.setNbNavettes(0);
        pvExamenRepository.save(pv);

        soumettre(304, tokenMembre, "CTRMEM").andExpect(status().isForbidden());
        soumettre(304, tokenCc, "CTRCC1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));
        mvc.perform(get("/api/pv-navettes").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$[?(@.idPv==304)].imActeur", hasItem("CTRCC1")));
    }

    /** Navettes d'un PV : le contrôleur ne filtre pas, on filtre ici. */
    private java.util.List<cnm.prs.entity.PvNavette> navettesDu(int idPv) {
        return pvNavetteRepository.findAll().stream()
                .filter(n -> Integer.valueOf(idPv).equals(n.getIdPv())).toList();
    }
}
