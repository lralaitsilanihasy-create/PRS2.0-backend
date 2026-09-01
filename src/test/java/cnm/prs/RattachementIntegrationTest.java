package cnm.prs;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ <strong>Rattachements Membre → Vérificateur → Assistant</strong> (arbitrage du pilote, 2026-09-01).
 *
 * <p>Deux natures de règles, à ne pas confondre : l'<strong>écriture</strong> du référentiel est gardée
 * (403 / 409), le <strong>ciblage</strong> ne l'est pas — il oriente files et notifications sans jamais
 * interdire à un autre Vérificateur ou Assistant de la localité d'agir (arbitrage 1). Les tests qui
 * suivent vérifient donc des DESTINATAIRES, pas des refus, dès qu'il s'agit du circuit.</p>
 *
 * <p>La fixture ne porte aucun rattachement : l'état initial de ces tests est celui du déploiement,
 * et le repli localité y est le comportement par défaut.</p>
 */
class RattachementIntegrationTest extends CnmIntegrationTestSupport {

    private org.springframework.test.web.servlet.ResultActions rattacher(String token, String porteur,
            String rattache) throws Exception {
        String corps = rattache == null ? "{}" : "{\"imRattache\":\"" + rattache + "\"}";
        return mvc.perform(put("/api/controleurs/" + porteur + "/rattachement").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    // ------------------------------------------------------------------
    // Écriture du référentiel
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Écriture — l'Administrateur rattache un Vérificateur à un Membre, puis un Assistant au Vérificateur")
    void ecriture_chaineComplete_parAdmin() throws Exception {
        rattacher(tokenAdmin, "CTRMEM", "CTRVER").andExpect(status().isOk())
                .andExpect(jsonPath("$.imRattache").value("CTRVER"))
                .andExpect(jsonPath("$.profil").value("MEMBRE"))
                .andExpect(jsonPath("$.profilAttendu").value("VERIFICATEUR"))
                .andExpect(jsonPath("$.nomRattache").exists());
        rattacher(tokenAdmin, "CTRVER", "CTRASS").andExpect(status().isOk())
                .andExpect(jsonPath("$.imRattache").value("CTRASS"))
                .andExpect(jsonPath("$.profilAttendu").value("ASSISTANT_CONTROLEUR"));
    }

    @Test
    @DisplayName("Écriture — le Président rattache partout ; le CC seulement dans SA localité (403 ailleurs)")
    void ecriture_perimetres() throws Exception {
        // Le Président est compétent partout (il n'a pas de localité).
        rattacher(tokenPresident, "CTRMEM", "CTRVER").andExpect(status().isOk());
        // Le CC d'ANT sur un porteur d'ANT : autorisé.
        rattacher(tokenCc, "CTRMEM", "CTRVER").andExpect(status().isOk());
        // Le CC de TMS sur le même porteur d'ANT : 403.
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        rattacher(tokenCcTms, "CTRMEM", "CTRVER").andExpect(status().isForbidden());
        // Un Membre n'administre rien : 403 par le @PreAuthorize.
        rattacher(tokenMembre, "CTRMEM", "CTRVER").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Écriture — profil du rattaché imposé : un Membre ne se rattache pas un Assistant (409)")
    void ecriture_profilDuRattache() throws Exception {
        rattacher(tokenAdmin, "CTRMEM", "CTRASS").andExpect(status().isConflict());
        rattacher(tokenAdmin, "CTRVER", "CTRMEM").andExpect(status().isConflict());
        // Un Assistant n'est pas porteur : la chaîne n'a que deux maillons.
        rattacher(tokenAdmin, "CTRASS", "CTRVER").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Écriture — rattachement inter-localités refusé (409) et auto-rattachement refusé (409)")
    void ecriture_localiteEtAutoRattachement() throws Exception {
        // CTRVER2 est Vérificateur de TMS ; CTRMEM est Membre d'ANT.
        controleurRepository.save(controleur("CTRVER2", 6, "TMS"));
        rattacher(tokenAdmin, "CTRMEM", "CTRVER2").andExpect(status().isConflict());
        rattacher(tokenAdmin, "CTRMEM", "CTRMEM").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Écriture — détachement toujours permis : le repli localité reprend la main")
    void ecriture_detachement() throws Exception {
        rattacher(tokenAdmin, "CTRMEM", "CTRVER").andExpect(status().isOk());
        rattacher(tokenAdmin, "CTRMEM", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.imRattache").value(nullValue()));
    }

    @Test
    @DisplayName("Signalement — le tableau expose les chaînes INCOMPLÈTES (imRattache null), état normal au déploiement")
    void tableau_signaleLesTrous() throws Exception {
        mvc.perform(get("/api/controleurs/rattachements").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                // Aucun rattachement en base : tous les porteurs ont un trou — c'est l'état de départ.
                .andExpect(jsonPath("$[?(@.imControleur=='CTRMEM' && @.imRattache == null)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.imControleur=='CTRVER' && @.imRattache == null)]", hasSize(1)));
        rattacher(tokenAdmin, "CTRMEM", "CTRVER").andExpect(status().isOk());
        mvc.perform(get("/api/controleurs/rattachements").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.imControleur=='CTRMEM')].imRattache", hasItem("CTRVER")));
    }

    @Test
    @DisplayName("Signalement — le CC ne voit que les porteurs de SA localité")
    void tableau_scopeCc() throws Exception {
        controleurRepository.save(controleur("CTRMEM3", 5, "TMS"));
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        mvc.perform(get("/api/controleurs/rattachements").header("Authorization", tokenCcTms))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.imControleur=='CTRMEM3')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.imControleur=='CTRMEM')]", hasSize(0)));
    }

    // ------------------------------------------------------------------
    // Ciblage
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Ciblage — SANS rattachement : cibles nulles, PV_A_VERIFIER à TOUS les vérificateurs (repli)")
    void ciblage_sansRattachement_repli() throws Exception {
        signerPvAvecAvis(9801, "FAVR");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.imVerificateurCible").value(nullValue()))
                .andExpect(jsonPath("$.nomVerificateurCible").value(nullValue()));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VERIFIER')].destinataireIm", hasItem("CTRVER")));
    }

    @Test
    @DisplayName("Ciblage — AVEC rattachement : le dossier expose son Vérificateur cible, et lui seul est notifié")
    void ciblage_avecRattachement() throws Exception {
        // CTRVER2, second Vérificateur d'ANT : sans ciblage, il recevrait aussi la notification.
        controleurRepository.save(controleur("CTRVER2", 6, "ANT"));
        rattacher(tokenAdmin, "CTRMEM", "CTRVER").andExpect(status().isOk());

        signerPvAvecAvis(9802, "FAVR");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.imVerificateurCible").value("CTRVER"))
                .andExpect(jsonPath("$.nomVerificateurCible").exists());
        // Ciblée sur le rattaché : le second vérificateur de la localité n'est PAS notifié…
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VERIFIER')].destinataireIm", hasItem("CTRVER")))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VERIFIER' && @.destinataireIm=='CTRVER2')]", hasSize(0)));
    }

    @Test
    @DisplayName("Ciblage — ce n'est PAS une garde : un vérificateur non ciblé statue quand même (arbitrage 1)")
    void ciblage_nEstPasUneGarde() throws Exception {
        controleurRepository.save(controleur("CTRVER2", 6, "ANT"));
        rattacher(tokenAdmin, "CTRMEM", "CTRVER").andExpect(status().isOk());
        signerPvAvecAvis(9803, "FAVR");
        // CTRVER2 n'est pas le ciblé, et agit néanmoins : l'instruction reste délégable (15/08).
        String tokenVer2 = bearer("CTRVER2", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER2", "ANT");
        String obs = mvc.perform(get("/api/observations-pv").header("Authorization", tokenVer2).param("dossier", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int idObs = com.jayway.jsonpath.JsonPath.read(obs, "$[0].idObservationPv");
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenVer2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":1,\"decisions\":[{\"idObservationPv\":" + idObs
                        + ",\"decision\":\"MAINTENUE\",\"precision\":\"a corriger\"}]}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ciblage — l'Assistant cible suit le valideur EFFECTIF : un suppléant valide, c'est SA chaîne qui archive")
    void ciblage_assistantSuitLeValideurEffectif() throws Exception {
        // Deux chaînes : CTRMEM → CTRVER → CTRASS (nominale) ; CTRVER2 → CTRASS2 (celle du suppléant).
        controleurRepository.save(controleur("CTRVER2", 6, "ANT"));
        controleurRepository.save(controleur("CTRASS2", 9, "ANT"));
        rattacher(tokenAdmin, "CTRMEM", "CTRVER").andExpect(status().isOk());
        rattacher(tokenAdmin, "CTRVER", "CTRASS").andExpect(status().isOk());
        rattacher(tokenAdmin, "CTRVER2", "CTRASS2").andExpect(status().isOk());

        // Le PV est signé FAV : la transmission SIGMP est possible sans boucle d'observations.
        signerPvAvecAvis(9804, "FAV");
        // ⚠️ C'est CTRVER2 — le suppléant — qui transmet, pas le vérificateur rattaché.
        String tokenVer2 = bearer("CTRVER2", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER2", "ANT");
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer2)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated());

        // L'assistant ciblé est CELUI DU SUPPLÉANT, pas celui de la chaîne nominale.
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.imAssistantCible").value("CTRASS2"))
                .andExpect(jsonPath("$.nomAssistantCible").exists());
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_ARCHIVER')].destinataireIm", hasItem("CTRASS2")))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_ARCHIVER' && @.destinataireIm=='CTRASS')]", hasSize(0)));
    }
}
