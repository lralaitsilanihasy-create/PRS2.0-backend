package cnm.prs;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.ModePassation;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * Verification et cloture : passage des observations, attente de decision PRMP, rectification et
 * resoumission, worklists du verificateur, historique des echanges, transmission SIGMP et
 * archivage jusqu'a CLOTURE.
 */
class VerificationIntegrationTest extends CnmIntegrationTestSupport {

    /** ⚠️ Audit 2026-08-27 (lot B) — garde de suppression d'un passage décidé. */
    @org.springframework.beans.factory.annotation.Autowired
    private cnm.prs.repository.VerificationRepository verificationRepository;

    @Test
    @DisplayName("Suppression d'un passage de verification (⚠️ audit lot B) : passage DECIDE -> 409 (trace de "
            + "circuit) ; passage sans decision -> 204")
    void verification_suppression_passageDecideRefusee() throws Exception {
        seedPvSigne(485, 1);
        cnm.prs.entity.Verification decidee = new cnm.prs.entity.Verification();
        decidee.setIdReception(1); decidee.setIdPv(485); decidee.setImCtrlVerif("CTRVER");
        decidee.setDateVerif(java.time.LocalDate.now()); decidee.setObsLevees(false);
        int idDecidee = verificationRepository.save(decidee).getIdVerification();

        cnm.prs.entity.Verification inachevee = new cnm.prs.entity.Verification();
        inachevee.setIdReception(1); inachevee.setIdPv(485); inachevee.setImCtrlVerif("CTRVER");
        int idInachevee = verificationRepository.save(inachevee).getIdVerification();

        mvc.perform(delete("/api/verifications/" + idDecidee).header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertTrue(verificationRepository.existsById(idDecidee),
                "le passage decide est conserve");
        mvc.perform(delete("/api/verifications/" + idInachevee).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("[Auto] Circuit FAVR (⚠️ 2026-08-02) : obs. levées → OBSERVATIONS_LEVEES, transmission SIGMP → "
            + "DECISION_TRANSMISE_SIGMP, archivage Assistant → CLOTURE + CLOTURE_ELIGIBLE")
    void auto_cloture() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        // PV FAVR amené à SIGNE → dossier EN_VERIFICATION, périmètre d'observations figé.
        signerPvAvecAvis(1, "FAVR");

        // ⚠️ Décision produit 2026-08-15 : premier passage = rappel (MAINTENUE), la PRMP rectifie et
        // resoumet, puis le vérificateur LÈVE l'observation → OBSERVATIONS_LEVEES.
        String obs = mvc.perform(get("/api/observations-pv").header("Authorization", tokenVer).param("dossier", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int idObs = com.jayway.jsonpath.JsonPath.read(obs, "$[0].idObservationPv");
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":1,\"decisions\":[{\"idObservationPv\":" + idObs
                        + ",\"decision\":\"MAINTENUE\",\"precision\":\"a rectifier\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":1,\"decisions\":[{\"idObservationPv\":" + idObs + ",\"decision\":\"LEVEE\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("OBSERVATIONS_LEVEES"));

        // Le vérificateur transmet la décision à SIGMP → DECISION_TRANSMISE_SIGMP.
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("DECISION_TRANSMISE_SIGMP"));

        // L'Assistant archive le PV → dossier CLOTURE.
        mvc.perform(post("/api/pv-examens/1/archiver").header("Authorization", tokenAss))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CLOTURE"));

        // [Auto] Le Chargé de publication est alerté que le dossier clôturé est éligible.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='CLOTURE_ELIGIBLE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='CLOTURE_ELIGIBLE')].destinataireIm", hasItem("CTRPUB")));
    }

    @Test
    @DisplayName("Tâche du Vérificateur (⚠️ délégation ascendante 2026-08-14) : le CC statue un passage via la "
            + "paire CC→Vérificateur ; un Secrétaire (aucune paire) → 403")
    void verif_parNonVerificateur_403() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        signerPvAvecAvis(80, "FAVR"); // dossier 1 → EN_VERIFICATION, périmètre d'observations figé

        // Négatif : un Secrétaire (aucune paire Secrétaire → Vérificateur en table) → 403.
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":1,\"decisions\":[{\"idObservationPv\":1,\"decision\":\"LEVEE\"}]}"))
                .andExpect(status().isForbidden());

        // Le CC exerce la tâche du Vérificateur (paire active CC → Vérificateur, même localité).
        // ⚠️ Décision produit 2026-08-15 : premier passage = rappel (MAINTENUE) — la levée n'est
        // possible qu'après une resoumission de la PRMP.
        passageObservationDossier1(tokenCc, "MAINTENUE", "a rectifier");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));
    }

    @Test
    @DisplayName("Vérification réservée aux PV FAVR : avis FAV → 409")
    void verif_surAvisNonReserve_409() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis(81, "FAV"); // dossier 1 → EN_VERIFICATION, PV 81 SIGNE avis FAV
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":81,\"obsLevees\":true}"))
                .andExpect(status().isConflict());
    }

    /** Statue l'unique observation du périmètre du dossier 1 via le circuit des observations (⚠️ 2026-08-02). */
    private void passageObservationDossier1(String tokenVer, String decision, String precision) throws Exception {
        String obs = mvc.perform(get("/api/observations-pv").header("Authorization", tokenVer).param("dossier", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int idObs = com.jayway.jsonpath.JsonPath.read(obs, "$[0].idObservationPv");
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":1,\"decisions\":[{\"idObservationPv\":" + idObs + ",\"decision\":\"" + decision
                        + "\"" + (precision == null ? "" : ",\"precision\":\"" + precision + "\"") + "}]}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Passage obs. MAINTENUE (⚠️ 2026-08-02) → EN_ATTENTE_DECISION_PRMP + notif OBSERVATION_VERIFICATION (PRMP) ; saisie libre refusée 409")
    void verif_obsNonLevees_attenteDecisionPrmp() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis(82, "FAVR"); // dossier 1 → EN_VERIFICATION, périmètre figé
        // 1er passage : observation MAINTENUE → dossier EN_ATTENTE_DECISION_PRMP + notif PRMP.
        passageObservationDossier1(tokenVer, "MAINTENUE", "reserve a lever");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));
        // La PRMP du dossier reçoit l'observation (refeDossier + rappel auto-généré) via OBSERVATION_VERIFICATION.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='OBSERVATION_VERIFICATION')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='OBSERVATION_VERIFICATION')].destinataireRef", hasItem("PRMP001")));
        // Saisie libre (texte client) refusée : le périmètre est figé → 409.
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":82,\"observation\":\"ok\",\"obsLevees\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Worklist : obs. non levées → dossier dans /en-attente-prmp ET conservé dans /a-verifier (lecture seule), visible PRMP via ?statut")
    void verif_obsNonLevees_attentePrmp_worklist() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis(84, "FAVR"); // dossier 1 → EN_VERIFICATION
        passageObservationDossier1(tokenVer, "MAINTENUE", "averina");
        // Vérificateur : le dossier est dans « En attente PRMP » ET reste dans « à vérifier » (lecture seule).
        mvc.perform(get("/api/dossiers/en-attente-prmp").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));
        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));   // conservé (EN_ATTENTE_DECISION_PRMP)
        // PRMP propriétaire : le dossier apparaît via le filtre de statut.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp).param("statut", "EN_ATTENTE_DECISION_PRMP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));
    }

    @Test
    @DisplayName("Worklist : un dossier EN_ATTENTE_DECISION_PRMP est en lecture seule — vérification refusée 409")
    void verif_attentePrmp_lectureSeule_409() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis(88, "FAVR"); // dossier 1 → EN_VERIFICATION
        passageObservationDossier1(tokenVer, "MAINTENUE", "averina"); // → dossier 1 EN_ATTENTE_DECISION_PRMP
        // Le dossier reste dans « à vérifier » mais toute nouvelle vérification est refusée (lecture seule).
        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":88,\"observation\":\"encore\",\"obsLevees\":true}"))
                .andExpect(status().isConflict());
    }

    /** Amène le dossier 1 à EN_ATTENTE_DECISION_PRMP (PV FAVR signé + observation MAINTENUE par CTRVER). */
    private void dossier1EnAttenteDecisionPrmp(int idPv, String tokenVer) throws Exception {
        signerPvAvecAvis(idPv, "FAVR");
        passageObservationDossier1(tokenVer, "MAINTENUE", "averina");
    }

    @Test
    @DisplayName("Resoumission PRMP : EN_ATTENTE_DECISION_PRMP → EN_VERIFICATION + notif vérificateur + audit + motif visible")
    void resoumission_retourEnVerification() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        dossier1EnAttenteDecisionPrmp(85, tokenVer);

        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        // Notif RECTIFICATION_PRMP au vérificateur du dossier (CTRVER).
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='RECTIFICATION_PRMP')].destinataireIm", hasItem("CTRVER")));
        // Motif visible sur le passage côté vérificateur.
        mvc.perform(get("/api/verifications").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idPv==85)].motifRectif", hasItem("corrige")));
        // Le vérificateur statue de nouveau (dossier de retour en EN_VERIFICATION) : LEVÉE → OBSERVATIONS_LEVEES.
        passageObservationDossier1(tokenVer, "LEVEE", null);
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("OBSERVATIONS_LEVEES"));
    }

    @Test
    @DisplayName("Resoumission PRMP : motif vide → 400")
    void resoumission_motifVide_400() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        dossier1EnAttenteDecisionPrmp(86, tokenVer);
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Resoumission PRMP : dossier hors EN_ATTENTE_DECISION_PRMP (EN_VERIFICATION) → 409")
    void resoumission_horsAttente_409() throws Exception {
        signerPvAvecAvis(87, "FAVR"); // dossier 1 → EN_VERIFICATION (pas EN_ATTENTE)
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Historique d'échanges (dossier clôturé) : observations + rectifications PRMP ; accessible PRMP et vérificateur")
    void historique_echanges_dossierCloture() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        signerPvAvecAvis(90, "FAVR"); // dossier 1 → EN_VERIFICATION, périmètre figé
        // Passage 1 : observation MAINTENUE → resoumission (rect1).
        passageObservationDossier1(tokenVer, "MAINTENUE", "obs1");
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"rect1\"}"))
                .andExpect(status().isOk());
        // Passage 2 : observation MAINTENUE → resoumission (rect2).
        passageObservationDossier1(tokenVer, "MAINTENUE", "obs2");
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"rect2\"}"))
                .andExpect(status().isOk());
        // Passage final : observation LEVÉE → OBSERVATIONS_LEVEES, puis SIGMP + archivage → CLOTURE.
        passageObservationDossier1(tokenVer, "LEVEE", null);
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/90/archiver").header("Authorization", tokenAss))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("CLOTURE"));

        // Historique : 3 observations (passages auto-générés, dont la levée finale) + 2 rectifications.
        mvc.perform(get("/api/dossiers/1/historique-echanges").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                // Fil entrelacé (chaîne de réponse) : passage, rect1, passage, rect2, passage final.
                .andExpect(jsonPath("$[0].type").value("OBSERVATION"))
                .andExpect(jsonPath("$[1].type").value("RECTIFICATION")).andExpect(jsonPath("$[1].texte").value("rect1"))
                .andExpect(jsonPath("$[1].acteur").value("PRMP001"))
                .andExpect(jsonPath("$[2].type").value("OBSERVATION"))
                .andExpect(jsonPath("$[3].type").value("RECTIFICATION")).andExpect(jsonPath("$[3].texte").value("rect2"))
                .andExpect(jsonPath("$[4].type").value("OBSERVATION"))
                .andExpect(jsonPath("$[4].obsLevees").value(true));
        // Accessible aussi par la PRMP.
        mvc.perform(get("/api/dossiers/1/historique-echanges").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @DisplayName("Historique d'échanges : dossier non clôturé (EN_VERIFICATION) → 403")
    void historique_echanges_horsCloture_403() throws Exception {
        signerPvAvecAvis(91, "FAVR"); // dossier 1 → EN_VERIFICATION (pas CLOTURE)
        mvc.perform(get("/api/dossiers/1/historique-echanges").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vérification : identité enregistrée = JWT (CurrentUser.ref), jamais le corps ; ID auto-généré")
    void verif_identiteDepuisJwt() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis(83, "FAVR");
        // ⚠️ 2026-08-02 : le passage est créé PAR LE SERVEUR depuis les décisions — l'identité vient du JWT.
        passageObservationDossier1(tokenVer, "MAINTENUE", null);
        mvc.perform(get("/api/verifications").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPv==83)].imCtrlVerif", hasItem("CTRVER")))
                .andExpect(jsonPath("$[?(@.idPv==83)].idVerification").exists())
                .andExpect(jsonPath("$[?(@.idPv==83)].dateVerif").exists());
    }

    @Test
    @DisplayName("Worklist vérificateur « à-vérifier » : EN_VERIFICATION de la localité ; scope localité respecté")
    void worklist_aVerifier_listeEnVerification() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String tokenVerTms = bearer("CTRVER2", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER2", "TMS");
        signerPvAvecAvis(70, "FAVR"); // dossier 1 (ANT) → EN_VERIFICATION

        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));
        // Exclusif de l'historique « vérifiés ».
        mvc.perform(get("/api/dossiers/verifies").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.idDossier==1)]", hasSize(0)));
        // Scope localité : un vérificateur TMS ne voit pas le dossier ANT.
        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVerTms))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(0)));
    }

    @Test
    @DisplayName("Worklist vérificateur « vérifiés » (⚠️ bascule 2026-08-04) : le dossier y entre à la transmission SIGMP")
    void worklist_verifies_inclutAutoClotures() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis(71, "FAV"); // dossier 1 (ANT) → EN_VERIFICATION, PV 71 SIGNE

        // Avant transmission : encore une action à faire → dans « à vérifier », pas dans « vérifiés ».
        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));

        // Transmission de la décision à SIGMP → bascule instantanée vers « vérifiés ».
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/verifies").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.idDossier==1)]", hasSize(1)));
        // Exclusif de la file « à vérifier ».
        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(0)));
    }

    @Test
    @DisplayName("Rectification PPM : PATCH sur dossier EN_ATTENTE_DECISION_PRMP -> 200, champ mis a jour, statut inchange")
    void rectifier_ppm_ok() throws Exception {
        Dossier d = dossier(400, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(400, 400, "PRMP001"));

        mvc.perform(patch("/api/ppms/400/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":400,\"exercice\":2026,\"signataire\":\"Sign\",\"dateSignature\":\"2026-01-10\","
                        + "\"reference\":\"PPM-REF-400\",\"libelle\":\"Libelle rectifie\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Libelle rectifie"));
        mvc.perform(get("/api/dossiers/400").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));
    }

    @Test
    @DisplayName("Rectification PPM hors attente : dossier EN_VERIFICATION -> 409")
    void rectifier_ppm_horsAttente_409() throws Exception {
        Dossier d = dossier(402, "EN_VERIFICATION"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(420, 402, "PRMP001"));

        mvc.perform(patch("/api/ppms/420/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":402,\"exercice\":2026,\"signataire\":\"S\",\"dateSignature\":\"2026-01-10\",\"reference\":\"R\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Rectification PPM par verificateur -> 403")
    void rectifier_ppm_verificateur_403() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        Dossier d = dossier(403, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(430, 403, "PRMP001"));

        mvc.perform(patch("/api/ppms/430/rectifier").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":403,\"exercice\":2026,\"signataire\":\"S\",\"dateSignature\":\"2026-01-10\",\"reference\":\"R\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Rectification marche : PATCH sur dossier EN_ATTENTE_DECISION_PRMP -> 200, objet mis a jour, statut inchange")
    void rectifier_marche_ok() throws Exception {
        Dossier d = dossier(401, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(410, 401, "PRMP001"));
        marcheRepository.save(marche(411, 401, 410));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));

        mvc.perform(patch("/api/marches/411/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":401,\"idPpm\":410,\"designationMarche\":\"Objet rectifie\","
                        + "\"montEstim\":5000000,\"idMode\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designationMarche").value("Objet rectifie"));
        mvc.perform(get("/api/dossiers/401").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));
    }

    @Test
    @DisplayName("Rectification marche hors attente : dossier EN_VERIFICATION -> 409")
    void rectifier_marche_horsAttente_409() throws Exception {
        Dossier d = dossier(404, "EN_VERIFICATION"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(440, 404, "PRMP001"));
        marcheRepository.save(marche(441, 404, 440));

        mvc.perform(patch("/api/marches/441/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":404,\"idPpm\":440,\"designationMarche\":\"X\",\"montEstim\":1000}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Rectification marche par verificateur -> 403")
    void rectifier_marche_verificateur_403() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        Dossier d = dossier(405, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(450, 405, "PRMP001"));
        marcheRepository.save(marche(451, 405, 450));

        mvc.perform(patch("/api/marches/451/rectifier").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":405,\"idPpm\":450,\"designationMarche\":\"X\",\"montEstim\":1000}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Rectification (⚠️ audit lot B) : le CONTENU est valide — montant negatif -> 400 cible montEstim, "
            + "exercice hors bornes -> 400 cible exercice ; l'identite figee reste facultative")
    void rectifier_contenuValide() throws Exception {
        Dossier d = dossier(480, "EN_ATTENTE_DECISION_PRMP");
        d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(480, 480, "PRMP001"));
        marcheRepository.save(marche(481, 480, 480));

        // Avant le correctif : 200, et le montant negatif remontait jusqu'aux cumuls des KPI.
        mvc.perform(patch("/api/marches/481/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"designationMarche\":\"Objet\",\"montEstim\":-1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("montEstim"));
        mvc.perform(patch("/api/ppms/480/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exercice\":12,\"reference\":\"R\",\"libelle\":\"L\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("exercice"));

        // NON-REGRESSION : le corps sans identite figee (ni idDossier ni idPpm) passe toujours.
        mvc.perform(patch("/api/marches/481/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"designationMarche\":\"Objet\",\"montEstim\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montEstim").value(1000));
    }

    @Test
    @DisplayName("Saisie marche (⚠️ audit lot B) : montant negatif refuse aussi sur le PUT (borne @PositiveOrZero)")
    void marche_put_montantNegatif_400() throws Exception {
        Dossier d = dossier(482, "BROUILLON");
        d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(482, 482, "PRMP001"));
        marcheRepository.save(marche(483, 482, 482));

        mvc.perform(put("/api/marches/483").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":482,\"idPpm\":482,\"designationMarche\":\"Objet\",\"montEstim\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("montEstim"));
    }

    @Test
    @DisplayName("Rectification PPM sans idDossier (identite figee) -> 200")
    void rectifier_ppm_sansIdentite_ok() throws Exception {
        Dossier d = dossier(406, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(460, 406, "PRMP001"));
        mvc.perform(patch("/api/ppms/460/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exercice\":2026,\"signataire\":\"Sign\",\"dateSignature\":\"2026-05-10\",\"reference\":\"R\",\"libelle\":\"L\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("L"));
    }

    @Test
    @DisplayName("Rectification marche sans idDossier/idPpm (identite figee) -> 200")
    void rectifier_marche_sansIdentite_ok() throws Exception {
        Dossier d = dossier(407, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(470, 407, "PRMP001"));
        marcheRepository.save(marche(471, 407, 470));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        mvc.perform(patch("/api/marches/471/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"designationMarche\":\"Objet\",\"montEstim\":1000,\"idMode\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designationMarche").value("Objet"));
    }

    @Test
    @DisplayName("Clôture (FAVR, ⚠️ 2026-08-02) : levée + SIGMP + archivage Assistant → dossier CLOTURE")
    void dossier_cloture_assistant_notifie() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        signerPvAvecAvis(122, "FAVR");   // dossier 1 → EN_VERIFICATION
        // ⚠️ Décision produit 2026-08-15 : premier passage = rappel (MAINTENUE), puis la PRMP rectifie
        // et resoumet — la levée n'est possible qu'ensuite.
        passageObservationDossier1(tokenVer, "MAINTENUE", "a rectifier"); // → EN_ATTENTE_DECISION_PRMP
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk());                              // → EN_VERIFICATION
        passageObservationDossier1(tokenVer, "LEVEE", null);   // → OBSERVATIONS_LEVEES
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sens").value("APPROUVE"))
                .andExpect(jsonPath("$.leveeObservations").value(true));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_ARCHIVER')].destinataireIm", hasItem("CTRASS")));
        mvc.perform(post("/api/pv-examens/122/archiver").header("Authorization", tokenAss))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("CLOTURE"));
    }

    // ------------------------------------------------------------------
    // Cloisonnement des lectures de fin de circuit (⚠️ audit 2026-08-27, §3.1 du rapport)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Historique d'échanges §1 — dossier clôturé : 403 pour une PRMP étrangère et pour un vérificateur d'une autre localité")
    void historique_echanges_horsPerimetre_403() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        cloturerDossier1(123, tokenVer);

        // Flux légitime : le vérificateur de la localité et la PRMP propriétaire lisent le fil.
        mvc.perform(get("/api/dossiers/1/historique-echanges").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/dossiers/1/historique-echanges").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());

        // ⚠️ Avant le correctif : 200. Le rôle suffisait, le périmètre n'était pas vérifié — les
        // observations du vérificateur et les rectifications de la PRMP sortaient du dossier.
        String tokenPrmpEtrangere = bearer("PRMPXX", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMPXX", "ANT");
        mvc.perform(get("/api/dossiers/1/historique-echanges").header("Authorization", tokenPrmpEtrangere))
                .andExpect(status().isForbidden());
        String tokenVerTms = bearer("CTRVER2", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER2", "TMS");
        mvc.perform(get("/api/dossiers/1/historique-echanges").header("Authorization", tokenVerTms))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Transmissions SIGMP §1/§3.1 — liste bornée à la localité (avec et sans ?dossier=) ; PRMP exclue du registre")
    void sigmp_transmissions_lectureCloisonnee() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        cloturerDossier1(124, tokenVer);   // transmission SIGMP enregistrée sur le dossier 1 (ANT)

        // Flux légitime : l'écran du vérificateur d'ANT (?dossier=) et sa liste complète.
        mvc.perform(get("/api/sigmp-transmissions?dossier=1").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/sigmp-transmissions").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));

        // ⚠️ Avant le correctif : findAll servait TOUT à tout authentifié.
        String tokenVerTms = bearer("CTRVER2", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER2", "TMS");
        mvc.perform(get("/api/sigmp-transmissions").header("Authorization", tokenVerTms))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/sigmp-transmissions?dossier=1").header("Authorization", tokenVerTms))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // La PRMP est notifiée de la décision ; elle ne lit pas le registre d'interopérabilité (§3.1).
        mvc.perform(get("/api/sigmp-transmissions").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/sigmp-transmissions?dossier=1").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // NON-RÉGRESSION : le Président voit toutes les localités.
        mvc.perform(get("/api/sigmp-transmissions").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));
    }

    /**
     * Mène le dossier 1 (localité ANT) jusqu'à CLOTURE par le circuit FAVR complet : PV signé, rappel
     * MAINTENUE, resoumission de la PRMP, levée, transmission SIGMP puis archivage par l'Assistant.
     * Laisse derrière lui un historique d'échanges et une transmission SIGMP à cloisonner.
     */
    private void cloturerDossier1(int idPv, String tokenVer) throws Exception {
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR,
                "CTRASS", "ANT");
        signerPvAvecAvis(idPv, "FAVR");
        passageObservationDossier1(tokenVer, "MAINTENUE", "a rectifier");
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk());
        passageObservationDossier1(tokenVer, "LEVEE", null);
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/" + idPv + "/archiver").header("Authorization", tokenAss))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("CLOTURE"));
    }
}
