package cnm.prs;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import cnm.prs.entity.Avis;
import cnm.prs.entity.Dispatch;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Examen;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * Workflow du proces-verbal : creation, navette, co-signature, branchements d'avis (FAV / FAVR /
 * DEF / NSP), statuts du dossier, circuit complet de bout en bout, transitions interdites et
 * telechargement du PV signe.
 */
class PvWorkflowIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Statut examen : signer le PV (favorable avec réserves) fait passer le dossier EXAMINE → EN_VERIFICATION")
    void statut_signaturePvAvanceVersPvSigne() throws Exception {
        // Dossier 1 = EXAMINE (seed). PV FAVR (≥ 1 observation requise) sur l'examen 1, soumis, accepté, co-signé.
        ajouterObservationExamen1();
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":90,\"idExamen\":1,\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/90/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/90/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAVR\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/90/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/90/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("SIGNE"));

        // Le dossier 1 (avis FAVR) est passé EXAMINE → EN_VERIFICATION.
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
    }

    @Test
    @DisplayName("Branchement signature (⚠️ 2026-08-02) — avis FAVORABLE (FAV) → dossier EN_VERIFICATION + PRMP PV_SIGNE + vérificateur DECISION_A_TRANSMETTRE")
    void signature_avisFavorable_clotureAuto() throws Exception {
        signerPvAvecAvis(94, "FAV");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='DECISION_A_TRANSMETTRE')].destinataireIm", hasItem("CTRVER")));
    }

    @Test
    @DisplayName("Branchement signature (⚠️ 2026-08-02) — avis DÉFAVORABLE (DEF) → dossier EN_VERIFICATION + PRMP PV_SIGNE + vérificateur DECISION_A_TRANSMETTRE")
    void signature_avisDefavorable_clotureAuto() throws Exception {
        signerPvAvecAvis(95, "DEF");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='DECISION_A_TRANSMETTRE')].destinataireIm", hasItem("CTRVER")));
    }

    @Test
    @DisplayName("Branchement signature (⚠️ 2026-08-02) — avis NE SE PRONONCE PAS (NSP) → dossier EN_VERIFICATION (idem DEF) + notifs PRMP + vérificateur")
    void signature_avisNeSePrononce_clotureAuto() throws Exception {
        signerPvAvecAvis(96, "NSP");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='DECISION_A_TRANSMETTRE')].destinataireIm", hasItem("CTRVER")));
    }

    @Test
    @DisplayName("Branchement signature — avis FAVORABLE AVEC RÉSERVE (FAVR) → dossier EN_VERIFICATION + vérificateur PV_A_VERIFIER + PRMP PV_SIGNE")
    void signature_avisReserve_enVerification() throws Exception {
        signerPvAvecAvis(97, "FAVR");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VERIFIER')].destinataireIm", hasItem("CTRVER")));
    }

    @Test
    @DisplayName("Co-signature PV : rôle↔acteur authentifié, identité enregistrée (Membre attributaire + Président réel)")
    void cosignature_authentificationEtIdentite() throws Exception {
        // PV sur examen 1 (Membre CTRMEM), porté à PROJET_ACCEPTE.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":92,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/92/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/92/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isOk());

        // Un Membre ne peut PAS falsifier la signature Président → 403.
        mvc.perform(post("/api/pv-examens/92/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"PRESIDENT\"}"))
                .andExpect(status().isForbidden());
        // Un AUTRE Membre (non attributaire) ne peut pas signer comme MEMBRE → 403.
        String tokenAutreMembre = bearer("CTRMEM2", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM2", "ANT");
        mvc.perform(post("/api/pv-examens/92/signer").header("Authorization", tokenAutreMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM2\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isForbidden());

        // Le Membre attributaire signe → reste PROJET_ACCEPTE (le co-signataire manque).
        mvc.perform(post("/api/pv-examens/92/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        // Le Président réel co-signe → SIGNE, identités enregistrées (plus de « — »).
        mvc.perform(post("/api/pv-examens/92/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRMEM"))
                .andExpect(jsonPath("$.imCtrlPresident").value("CTRPRE"));
    }

    @Test
    @DisplayName("Co-signature PV par le CC : CC de la localité OK (identité enregistrée), CC d'une autre localité → 403")
    void cosignature_ccDeLaLocalite() throws Exception {
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":93,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/93/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/93/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/93/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk());

        // Un CC d'une AUTRE localité (TMS) ne peut pas co-signer un PV d'ANT → 403.
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        mvc.perform(post("/api/pv-examens/93/signer").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC2\",\"role\":\"CC\"}"))
                .andExpect(status().isForbidden());
        // Le CC de la localité (ANT) co-signe → SIGNE, identité enregistrée.
        mvc.perform(post("/api/pv-examens/93/signer").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\",\"role\":\"CC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                .andExpect(jsonPath("$.imCtrlCc").value("CTRCC1"));
    }

    @Test
    @DisplayName("Workflow PV : cycle complet BROUILLON → SIGNE avec gardes et navette")
    void workflowPv_cycleComplet() throws Exception {
        // Création : le statut envoyé (SIGNE) est ignoré, le PV démarre en BROUILLON.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":1,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"SIGNE\",\"nbNavettes\":99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutPv").value("BROUILLON"))
                .andExpect(jsonPath("$.nbNavettes").value(0));

        soumettre(tokenMembre).andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));

        // Retour interdit au Membre.
        mvc.perform(post("/api/pv-examens/1/retourner").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"x\"}"))
                .andExpect(status().isForbidden());

        // Retour sans commentaire interdit (garde métier).
        mvc.perform(post("/api/pv-examens/1/retourner").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(status().isConflict());

        // Retour valide par le CC.
        mvc.perform(post("/api/pv-examens/1/retourner").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"commentaire\":\"Corriger la synthèse\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("EN_RECTIFICATION"));

        soumettre(tokenMembre).andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));

        // ⚠️ Clôture de navette (2026-08-01) : l'acceptation pose l'avis global + le secrétaire de séance.
        mvc.perform(post("/api/pv-examens/1/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));

        // Une seule signature ne suffit pas.
        signer(tokenMembre, "CTRMEM", "MEMBRE").andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));

        // Co-signature → SIGNE.
        signer(tokenPresident, "CTRPRE", "PRESIDENT").andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                .andExpect(jsonPath("$.datePv").isNotEmpty());

        // 4 navettes tracées (SOUMISSION, RETOUR_RECTIF, SOUMISSION, ACCEPTATION).
        mvc.perform(get("/api/pv-navettes").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));

        // PV signé non éditable.
        mvc.perform(put("/api/pv-examens/1").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":1,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"SIGNE\",\"nbNavettes\":4}"))
                .andExpect(status().isConflict());

        // Navette non supprimable.
        mvc.perform(delete("/api/pv-navettes/1").header("Authorization", tokenMembre))
                .andExpect(status().isConflict());

        // [Auto] La PRMP du dossier reçoit une notification PV_SIGNE.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')].destinataireEmail", hasItem("prmp@min.mg")));
    }

    @Test
    @DisplayName("Création PV : imCtrlMembre dérivé de l'attribution (dispatch), le corps est ignoré")
    void creationPv_imCtrlMembreDeriveDeLAttribution() throws Exception {
        // Examen 1 → dispatch 1 → attributaire CTRMEM ; le corps tente d'usurper « USURP ».
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":60,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"USURP\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRMEM"));
    }

    @Test
    @DisplayName("Création PV : examen sans Membre attributaire (dispatch) → 409")
    void creationPv_examenSansAttributaire_409() throws Exception {
        dossierRepository.save(dossier(60, "DISPATCHE"));
        receptionRepository.save(reception(60, 60, "CTRCC1", true));
        dispatchRepository.save(dispatch(60, 60, "CTRCC1", null)); // dispatch sans attributaire
        examenRepository.save(examen(60, 60, "CTRMEM"));
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":61,\"idExamen\":60,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Circuit complet : Réception → PRET_DISPATCH → Dispatch → Examen soumis → PV(navette → SIGNE) → SIGMP → Archivage → CLOTURE")
    void circuitComplet_boutEnBout() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // Dossier de test neuf (id 3), distinct des dossiers seedés.
        dossierRepository.save(dossier(3, "EXAMINE"));

        // 1) Réception complète par le Secrétaire → [Auto] dossier PRET_DISPATCH.
        //    L'id de réception (PK technique) est alloué par le serveur (séquence) : on le capture pour la suite.
        String recBody = mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":3,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idRec = com.jayway.jsonpath.JsonPath.read(recBody, "$.idReception");
        mvc.perform(get("/api/dossiers/3").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("PRET_DISPATCH"));

        // 2) Dispatch par le CC (titulaire dans sa localité ANT).
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":3,\"idReception\":" + idRec + ",\"imCtrlDispatch\":\"CTRCC1\",\"imCtrlCc\":\"CTRCC1\","
                        + "\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());
        // Le dispatch fait avancer le dossier à DISPATCHE (règle ajoutée).
        mvc.perform(get("/api/dossiers/3").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("DISPATCHE"));

        // 3) Examen par le Membre (brouillon) puis SOUMISSION → dossier EXAMINE + Projet de PV créé.
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":3,\"idDispatch\":3,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isCreated());
        String pvBody = mvc.perform(post("/api/examens/3/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutPv").value("BROUILLON"))
                .andReturn().getResponse().getContentAsString();
        int idPv = com.jayway.jsonpath.JsonPath.read(pvBody, "$.idPv");
        mvc.perform(get("/api/dossiers/3").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EXAMINE"));

        // 4) Navette : soumettre → accepter (clôture de navette : avis FAV + secrétaire), co-signature → SIGNE.
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\"}"))
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        // Un seul signataire ne suffit pas : le PV reste PROJET_ACCEPTE.
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\"}"))
                .andExpect(jsonPath("$.statutPv").value("SIGNE"));

        // 5) Signature (FAV) → EN_VERIFICATION ; transmission SIGMP puis archivage Assistant → CLOTURE.
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        mvc.perform(get("/api/dossiers/3").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":3}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/" + idPv + "/archiver").header("Authorization", tokenAss))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/3").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CLOTURE"));
    }

    @Test
    @DisplayName("Transitions interdites : rôle non autorisé → 403, saut d'étape du PV → 409")
    void transitionsInterdites() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");

        // Rôle : un Vérificateur ne peut pas dispatcher → 403.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"interimDispatch\":false}"))
                .andExpect(status().isForbidden());

        // Rôle : un Secrétaire ne peut pas accepter un projet de PV (réservé CC / Président) → 403.
        mvc.perform(post("/api/pv-examens/1/accepter").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRSEC\"}"))
                .andExpect(status().isForbidden());

        // Saut d'étape : un PV en BROUILLON ne peut être ni accepté ni signé → 409.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":4,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/4/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/pv-examens/4/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Préconditions du circuit : dispatch hors PRET_DISPATCH / doublon, examen hors circuit, vérif hors PV SIGNE → 409")
    void preconditionsCircuit_bloquent() throws Exception {
        // (a) Dispatch d'un dossier non PRET_DISPATCH (dossier 2 = EXAMINE, réception 2 sans dispatch) → 409.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":40,\"idReception\":2,\"interimDispatch\":false}"))
                .andExpect(status().isConflict());

        // (b) Anti-doublon : un dossier PRET_DISPATCH qui a déjà un dispatch → 2e dispatch refusé.
        dossierRepository.save(dossier(14, "PRET_DISPATCH"));
        receptionRepository.save(reception(24, 14, "CTRSEC", true));
        dispatchRepository.save(dispatch(41, 24, "CTRCC1", "CTRMEM"));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":42,\"idReception\":24,\"interimDispatch\":false}"))
                .andExpect(status().isConflict());

        // (c) Examen d'un dossier non dispatché (dispatch 1 → dossier 1 = EXAMINE, pas DISPATCHE) → 409.
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":40,\"idDispatch\":1,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isConflict());

        // (d) Vérification sur un PV non SIGNE (BROUILLON) → 409 (par un vérificateur, pour atteindre la garde PV SIGNE).
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":5,\"idExamen\":1,\"idAvis\":\"FAVR\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":5,\"obsLevees\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PV projets vs definitifs : un PV signe quitte /pv-examens et apparait dans /pv-examens/definitifs")
    void pv_projets_et_definitifs() throws Exception {
        // PV non signé (BROUILLON) sur examen 1.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":96,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        // PV signé (FAV) sur examen 1.
        signerPvAvecAvis(95, "FAV");

        // Projets : contient 96 (BROUILLON), exclut 95 (SIGNE).
        mvc.perform(get("/api/pv-examens").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPv==96)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPv==95)]", hasSize(0)));
        // Définitifs : contient 95 (SIGNE), exclut 96 (BROUILLON).
        mvc.perform(get("/api/pv-examens/definitifs").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPv==95)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPv==96)]", hasSize(0)));
    }

    @Test
    @DisplayName("Téléchargement PV — GET /document renvoie le PDF stocké (FSX)")
    void pv_document_telechargement_ok() throws Exception {
        byte[] contenu = "%PDF-1.5 contenu du PV".getBytes(StandardCharsets.US_ASCII);
        java.nio.file.Path fichier = java.nio.file.Files.createTempFile("pv-doc-", ".pdf");
        java.nio.file.Files.write(fichier, contenu);
        cnm.prs.entity.PvExamen pv = new cnm.prs.entity.PvExamen();
        pv.setIdPv(80);
        pv.setIdExamen(1);
        pv.setIdAvis("FAVR");
        pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("BROUILLON");
        pv.setNbNavettes(0);
        pv.setCheminDocument(fichier.toString());
        pvExamenRepository.save(pv);

        var resp = mvc.perform(get("/api/pv-examens/80/document").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertEquals(MediaType.APPLICATION_PDF_VALUE, resp.getContentType());
        org.junit.jupiter.api.Assertions.assertArrayEquals(contenu, resp.getContentAsByteArray());
    }

    @Test
    @DisplayName("Téléchargement PV — PV non éligible sans document → 404")
    void pv_document_absent_404() throws Exception {
        seedPvSigne(81, 1);   // PV avis FAV (non éligible) sans CHEMIN_DOCUMENT → pas de régénération
        mvc.perform(get("/api/pv-examens/81/document").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PvExamenDto.documentDisponible : projet éligible → true (cotation incluse) ; PV SIGNE → true seulement quand CHEMIN_DOCUMENT est posé (fenêtre de génération post-commit → false)")
    void pv_documentDisponible_refleteEligibilite() throws Exception {
        modePassationRepository.save(new ModePassation(5, "Demande de cotation", null, null, null, null));

        // ÉLIGIBLE MALGRÉ un marché en « Demande de cotation » : le mode ne conditionne plus l'éligibilité AFSR.
        cnm.prs.entity.Marche mCot = marche(9600, 1, 1); mCot.setIdMode(5); marcheRepository.save(mCot);
        cnm.prs.entity.PvExamen pvFavr = new cnm.prs.entity.PvExamen();
        pvFavr.setIdPv(600); pvFavr.setIdExamen(1); pvFavr.setIdAvis("FAVR"); pvFavr.setImCtrlMembre("CTRMEM");
        pvFavr.setStatutPv("PROJET_ACCEPTE"); pvFavr.setNbNavettes(0);
        pvExamenRepository.save(pvFavr);

        // NON ÉLIGIBLE pour un vrai motif : avis NSP (⚠️ 2026-08-03 : seul avis SANS modèle Word —
        // FAV → AF et DEF → ANF ont désormais leur gabarit).
        dossierRepository.save(dossierLoc(502, "EXAMINE", "ANT", "PRMP001"));
        receptionRepository.save(reception(502, 502, "CTRCC1", true));   // CTRCC1 = localité ANT
        dispatchRepository.save(dispatch(502, 502, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(502, 502, "CTRMEM"));
        ppmRepository.save(ppm(502, 502, "PRMP001"));
        marcheRepository.save(marche(9601, 502, 502));
        cnm.prs.entity.PvExamen pvDef = new cnm.prs.entity.PvExamen();
        pvDef.setIdPv(601); pvDef.setIdExamen(502); pvDef.setIdAvis("NSP"); pvDef.setImCtrlMembre("CTRMEM");
        pvDef.setStatutPv("SIGNE"); pvDef.setNbNavettes(0);
        // Invariant du schéma réel (t_pv_examen_cosignataire_check) : SIGNE => Membre + un co-signataire.
        pvDef.setDateSignatureMembre(LocalDate.now()); pvDef.setDateSignaturePresident(LocalDate.now());
        pvExamenRepository.save(pvDef);

        // PROJET FAVR + ANT + PPM + cotation → true : « un document sera produit à la signature »
        // (cas signalé 00008/DDP/CRM-ANT/PV/2026 — le mode ne bloque plus).
        mvc.perform(get("/api/pv-examens/600").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentDisponible").value(true));
        // ⚠️ 2026-08-19 (génération post-commit) — PV SIGNE : le flag dit « fichier prêt MAINTENANT ».
        // Signé sans CHEMIN_DOCUMENT (fenêtre de génération) → false ; chemin posé → true.
        pvFavr.setStatutPv("SIGNE");
        pvExamenRepository.save(pvFavr);
        mvc.perform(get("/api/pv-examens/600").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentDisponible").value(false));
        pvFavr.setCheminDocument("PV/pv-600.pdf");
        pvExamenRepository.save(pvFavr);
        mvc.perform(get("/api/pv-examens/600").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentDisponible").value(true));
        // Avis NSP → non éligible (aucun modèle Word pour « ne se prononce pas »).
        mvc.perform(get("/api/pv-examens/601").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentDisponible").value(false));
    }

    @Test
    @DisplayName("Garde-fou dossier↔PV : suppression du PV signé d'un dossier EN_VERIFICATION → dossier remis à EXAMINE")
    void suppressionPvSigne_realigneDossierEnVerification() throws Exception {
        // Chaîne complète + PV signé FAVR → dossier EN_VERIFICATION (comme après une signature FAVR).
        dossierRepository.save(dossierLoc(700, "EN_VERIFICATION", "ANT", "PRMP001"));
        receptionRepository.save(reception(700, 700, "CTRCC1", true));
        dispatchRepository.save(dispatch(700, 700, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(700, 700, "CTRMEM"));
        cnm.prs.entity.PvExamen pv = new cnm.prs.entity.PvExamen();
        pv.setIdPv(700); pv.setIdExamen(700); pv.setIdAvis("FAVR"); pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("SIGNE"); pv.setNbNavettes(0);
        // Invariant du schéma réel (t_pv_examen_cosignataire_check) : SIGNE => Membre + un co-signataire.
        pv.setDateSignatureMembre(LocalDate.now()); pv.setDateSignaturePresident(LocalDate.now());
        pvExamenRepository.save(pv);

        // Suppression du PV signé (Administrateur).
        mvc.perform(delete("/api/pv-examens/700").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());

        // Le dossier ne reste pas bloqué EN_VERIFICATION (« PV signé introuvable ») : il redevient EXAMINE.
        mvc.perform(get("/api/dossiers/700").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EXAMINE"));
    }

    /** Rend l'examen 1 (dossier 1, ppm 1) éligible (1 ligne de marché en AOO) puis crée + signe un PV FAVR. */
    private void signerPvEligible(int idPv) throws Exception {
        modePassationRepository.save(new ModePassation(1, "AOO", null, null, null, null));
        cnm.prs.entity.Marche m = marche(9500, 1, 1);   // dossier 1, ppm 1
        m.setIdMode(1);                                  // appel d'offres ouvert
        marcheRepository.save(m);
        signerPvAvecAvis(idPv, "FAVR");                  // → SIGNE → génération du document si éligible
    }

    @Test
    @DisplayName("Signature PV éligible → réponse immédiate (SIGNE) ; le document est produit APRÈS COMMIT : chemin NULL + documentDisponible=false dans la fenêtre")
    void signature_pv_genere_document_ok() throws Exception {
        signerPvEligible(110);
        cnm.prs.entity.PvExamen pv = pvExamenRepository.findById(110).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("SIGNE", pv.getStatutPv());
        // ⚠️ 2026-08-19 — la génération (Word, plusieurs secondes) est sortie du chemin de la signature :
        // elle part APRÈS COMMIT (PvDocumentTache). Dans la transaction de test (jamais commitée),
        // l'événement ne part pas — le chemin reste NULL, exactement comme pendant la fenêtre de
        // génération en prod, et documentDisponible est false (contrat front : « fichier prêt maintenant »).
        org.junit.jupiter.api.Assertions.assertNull(pv.getCheminDocument(),
                "la signature ne produit plus le document dans sa transaction");
        mvc.perform(get("/api/pv-examens/110").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                .andExpect(jsonPath("$.documentDisponible").value(false));
    }

    @Test
    @DisplayName("Téléchargement PV après signature → 200 application/pdf")
    void document_pv_telechargement_ok() throws Exception {
        signerPvEligible(111);
        var resp = mvc.perform(get("/api/pv-examens/111/document").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertEquals(MediaType.APPLICATION_PDF_VALUE, resp.getContentType());
        assertTrue(resp.getContentAsByteArray().length > 0, "le PDF n'est pas vide");
    }

    @Test
    @DisplayName("PV signé sans document (ancien) → régénération paresseuse au téléchargement → 200")
    void migration_pv_anciens_sans_document() throws Exception {
        signerPvEligible(112);
        cnm.prs.entity.PvExamen pv = pvExamenRepository.findById(112).orElseThrow();
        pv.setCheminDocument(null);            // simule un PV signé avant le correctif (chemin_document NULL)
        pvExamenRepository.save(pv);
        mvc.perform(get("/api/pv-examens/112/document").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertNotNull(
                pvExamenRepository.findById(112).orElseThrow().getCheminDocument(),
                "chemin_document régénéré à la demande");
    }

    @Test
    @DisplayName("Grille de contrôle — point « Conformité au budget » non conforme → observations chargées (>= 1)")
    void pv_detail_observations_chargees() throws Exception {
        PointsCtrl pc = new PointsCtrl();
        pc.setIdPointCtrl(1);
        pc.setLibelPointCtrl("Conformité au budget");
        pc.setObligatoire(true);
        pc.setIdTypeDossier("DDP");
        pointsCtrlRepository.save(pc);
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":530,\"idExamen\":1,\"idPtControle\":1,\"conforme\":false,"
                        + "\"observations\":[{\"auLieuDe\":\"250 000 000\",\"lire\":\"200 000 000\",\"ordre\":1}]}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/examen-details/530").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conforme").value(false))
                .andExpect(jsonPath("$.observations.length()").value(1));
    }

    @Test
    @DisplayName("PV définitifs — nomSecretaireSeance peuplé dans la liste (pas seulement le détail)")
    void pv_definitifs_nom_secretaire_peuple() throws Exception {
        cnm.prs.entity.PvExamen pv = new cnm.prs.entity.PvExamen();
        pv.setIdPv(120);
        pv.setIdExamen(1);
        pv.setIdAvis("FAVR");
        pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("SIGNE");
        pv.setNbNavettes(0);
        pv.setIdSecretaireSeance("CTRVER");
        // Invariant du schéma réel (t_pv_examen_cosignataire_check) : SIGNE => Membre + un co-signataire.
        pv.setDateSignatureMembre(LocalDate.now());
        pv.setDateSignaturePresident(LocalDate.now());
        pvExamenRepository.save(pv);
        mvc.perform(get("/api/pv-examens/definitifs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPv==120)].nomSecretaireSeance", hasItem("Prenoms NomCTRVER")));
    }

    // ------------------------------------------------------------------
    // Gardes d'identité et de localité de la navette (⚠️ audit 2026-08-27, lot B)
    // ------------------------------------------------------------------

    /** Crée un projet de PV BROUILLON sur l'examen 1 (attributaire CTRMEM, localité ANT). */
    private void creerProjetSurExamen1(int idPv) throws Exception {
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":" + idPv + ",\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Projet de PV (lot B) — édition et soumission réservées à l'attributaire : un AUTRE Membre de la "
            + "localité → 403 sur PUT et sur /soumettre ; l'attributaire passe (non-régression)")
    void projetPv_editionEtSoumissionReserveesALAttributaire() throws Exception {
        creerProjetSurExamen1(950);
        // Un autre Membre d'ANT (non attributaire du dispatch 1) : ni PUT, ni soumission.
        String tokenAutreMembre = bearer("CTRMEM2", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM2", "ANT");
        mvc.perform(put("/api/pv-examens/950").header("Authorization", tokenAutreMembre)
                .contentType(MediaType.APPLICATION_JSON).content(corpsProjet950("usurpation")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pv-examens/950/soumettre").header("Authorization", tokenAutreMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM2\"}"))
                .andExpect(status().isForbidden());
        // La synthèse n'a pas été écrasée par la tentative.
        mvc.perform(get("/api/pv-examens/950").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syntheseObservations").value(nullValue()))
                .andExpect(jsonPath("$.statutPv").value("BROUILLON"));

        // NON-RÉGRESSION : l'attributaire édite puis soumet.
        mvc.perform(put("/api/pv-examens/950").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(corpsProjet950("synthese")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syntheseObservations").value("synthese"));
        mvc.perform(post("/api/pv-examens/950/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));
        // NON-RÉGRESSION délégation : le CC de la localité (paire CC → Membre active) peut éditer.
        mvc.perform(post("/api/pv-examens/950/retourner").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"a corriger\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/pv-examens/950").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(corpsProjet950("reprise CC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syntheseObservations").value("reprise CC"));
    }

    /** Corps de PUT du projet 950 (champs obligatoires du DTO renseignés), avec la synthèse donnée. */
    private String corpsProjet950(String synthese) {
        return "{\"idPv\":950,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0,\"syntheseObservations\":\"" + synthese + "\"}";
    }

    @Test
    @DisplayName("Clôture de navette (lot B) — un CC d'une AUTRE localité ne retourne ni n'accepte le projet (403) ; "
            + "le CC de la localité et le Président passent (non-régression)")
    void clotureNavette_borneeALaLocaliteDuDossier() throws Exception {
        creerProjetSurExamen1(951);
        mvc.perform(post("/api/pv-examens/951/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\"}"))
                .andExpect(status().isOk());

        // CC de TMS sur un dossier d'ANT : retour ET acceptation refusés (avant le correctif : 200).
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        mvc.perform(post("/api/pv-examens/951/retourner").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"hors localite\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pv-examens/951/accepter").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pv-examens/951").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));

        // NON-RÉGRESSION : le Président (toutes localités) retourne, le CC d'ANT accepte.
        mvc.perform(post("/api/pv-examens/951/retourner").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"a corriger\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("EN_RECTIFICATION"));
        mvc.perform(post("/api/pv-examens/951/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/951/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
    }

    @Test
    @DisplayName("Trace de navette (lot B) — IM_ACTEUR vient du JWT : un imActeur falsifié dans le corps est ignoré")
    void navette_acteurTraceDepuisLeJeton() throws Exception {
        creerProjetSurExamen1(952);
        // Le Membre soumet en déclarant « CTRPRE » : la navette doit porter CTRMEM (son jeton).
        mvc.perform(post("/api/pv-examens/952/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\"}"))
                .andExpect(status().isOk());
        // Le CC retourne en déclarant « CTRMEM » : la navette doit porter CTRCC1.
        mvc.perform(post("/api/pv-examens/952/retourner").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"a corriger\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/pv-navettes").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPv==952 && @.sens=='SOUMISSION')].imActeur", hasItem("CTRMEM")))
                .andExpect(jsonPath("$[?(@.idPv==952 && @.sens=='RETOUR_RECTIF')].imActeur", hasItem("CTRCC1")))
                .andExpect(jsonPath("$[?(@.idPv==952 && @.imActeur=='CTRPRE')]", hasSize(0)));
    }

    @Test
    @DisplayName("Suppression d'un PV (⚠️ audit lot B) : un PV ARCHIVÉ est une pièce close du circuit → 409 ; "
            + "un PV signé non archivé reste supprimable (garde-fou de réalignement du dossier)")
    void suppressionPvArchive_refusee() throws Exception {
        cnm.prs.entity.PvExamen archive = new cnm.prs.entity.PvExamen();
        archive.setIdPv(953); archive.setIdExamen(1); archive.setIdAvis("FAV"); archive.setImCtrlMembre("CTRMEM");
        archive.setStatutPv("SIGNE"); archive.setNbNavettes(0);
        archive.setDateSignatureMembre(LocalDate.now()); archive.setDateSignaturePresident(LocalDate.now());
        archive.setDateArchivage(LocalDate.now()); archive.setImArchiveur("CTRASS");
        pvExamenRepository.save(archive);

        mvc.perform(delete("/api/pv-examens/953").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertTrue(pvExamenRepository.existsById(953),
                "le PV archivé est conservé");
    }

    @Test
    @DisplayName("ANNEXE PV — préfixe du libellé par ligne de marché (unitaire, sans Word) : [Marché « … »] / [Dossier]")
    void pvAnnexe_prefixeLibelle() {
        org.junit.jupiter.api.Assertions.assertEquals("[Marché « Travaux RN13 »] Cohérence",
                cnm.prs.service.PvDocumentService.prefixerLibelle(42, "Cohérence", "Travaux RN13"));
        // Point dossier (idDetail null) → [Dossier].
        org.junit.jupiter.api.Assertions.assertEquals("[Dossier] fractionnement illicite",
                cnm.prs.service.PvDocumentService.prefixerLibelle(null, "fractionnement illicite", null));
        // Ligne sans désignation → repli « n°<id> ».
        org.junit.jupiter.api.Assertions.assertEquals("[Marché « n°7 »] Objet",
                cnm.prs.service.PvDocumentService.prefixerLibelle(7, "Objet", null));
    }
}
