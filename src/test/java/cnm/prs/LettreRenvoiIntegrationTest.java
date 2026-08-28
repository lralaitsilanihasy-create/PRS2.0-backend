package cnm.prs;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * Lettre de renvoi : creation a la cloture de navette, signature (centrale / regionale), document
 * PDF genere et stocke, marquage « lue » par la PRMP destinataire, completion du dossier apres
 * renvoi et acces de l'Assistant controleur.
 */
class LettreRenvoiIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Lettre de renvoi : marquée lue à la consultation du détail par la PRMP propriétaire")
    void lettre_marquee_lue_apres_consultation() throws Exception {
        int id = seedLettreSignee();
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        // ⚠️ 2026-08-27 : la trace est posée pour l'AGENT (login du jeton), plus pour la tutelle.
        assertTrue(lueRepository.existsByIdLettreAndLoginAgent(id, "PRMP001"), "trace de lecture créée");
    }

    @Test
    @DisplayName("Lettre de renvoi : 2ᵉ consultation → pas de doublon de lecture (UNIQUE)")
    void lettre_deja_lue_pas_doublon() throws Exception {
        int id = seedLettreSignee();
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenPrmp)).andExpect(status().isOk());
        assertTrue(lueRepository.count() == 1, "une seule entrée de lecture malgré 2 consultations");
    }

    @Test
    @DisplayName("LettreRenvoiDto : flag lue = true après consultation par la PRMP")
    void lettre_dto_lue_flag() throws Exception {
        int id = seedLettreSignee();
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lue").value(true));
    }

    @Test
    @DisplayName("Complétion après lettre de renvoi : dépôt PRMP (idLettre) → dossier DISPATCHE + notif unique au Membre + réapparaît dans a-examiner")
    void completionApresRenvoi_notifieMembreEtRouvreExamen() throws Exception {
        // Dossier déjà examiné puis remis PRET_DISPATCH par la signature de la lettre (signer() testé ailleurs — dépend de Word).
        Dossier d = dossierLoc(400, "PRET_DISPATCH", "ANT", "PRMP001");
        d.setIdTypeDossier("DDP");
        d.setRefeDossier("00004/DDP/CRM-ANT/2026");
        dossierRepository.save(d);
        receptionRepository.save(reception(400, 400, "CTRCC1", true));
        dispatchRepository.save(dispatch(400, 400, "CTRCC1", "CTRMEM"));   // Membre attributaire = CTRMEM
        examenRepository.save(examen(400, 400, "CTRMEM"));
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(400); l.setIdDossier(400); l.setObjetLettre("Renvoi"); l.setStatut("SIGNE");
        int idLettre = lettreRenvoiRepository.save(l).getIdLettre();
        int idType = seedTypePiece("Pièce complémentaire", false, "DDP",1);

        byte[] pdf = "%PDF-1.4 piece complementaire".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile data = new MockMultipartFile("data", "", "application/json",
                ("{\"idDossier\":400,\"idTypePiece\":" + idType + ",\"idLettre\":" + idLettre + "}").getBytes(StandardCharsets.UTF_8));
        MockMultipartFile fichier = new MockMultipartFile("fichier", "piece.pdf", "application/pdf", pdf);

        // 1er dépôt après renvoi (PRMP propriétaire) → 201.
        mvc.perform(multipart("/api/piece-jointe-dossiers").file(data).file(fichier)
                .header("Authorization", tokenPrmp))
                .andExpect(status().isCreated());

        // Le dossier est rouvert à l'examen (PRET_DISPATCH → DISPATCHE, dispatch existant réutilisé).
        mvc.perform(get("/api/dossiers/400").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.statut").value("DISPATCHE"));
        // Il réapparaît dans la file « à examiner » du Membre attributaire.
        mvc.perform(get("/api/dossiers/a-examiner").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idDossier==400)]", hasSize(1)));
        // Le Membre reçoit UNE notification PIECE_AJOUTEE_APRES_RENVOI.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='PIECE_AJOUTEE_APRES_RENVOI' && @.idObjet==400)]", hasSize(1)));

        // 2e dépôt : le dossier est déjà DISPATCHE → pas de ré-avance ni de 2e notification (regroupée).
        MockMultipartFile data2 = new MockMultipartFile("data", "", "application/json",
                ("{\"idDossier\":400,\"idTypePiece\":" + idType + ",\"idLettre\":" + idLettre + "}").getBytes(StandardCharsets.UTF_8));
        MockMultipartFile fichier2 = new MockMultipartFile("fichier", "piece2.pdf", "application/pdf", pdf);
        mvc.perform(multipart("/api/piece-jointe-dossiers").file(data2).file(fichier2)
                .header("Authorization", tokenPrmp))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='PIECE_AJOUTEE_APRES_RENVOI' && @.idObjet==400)]", hasSize(1)));
    }

    @Test
    @DisplayName("Lettre de renvoi — création à la clôture de navette (CC/Président, objetLettre ignoré) → 201 BROUILLON")
    void lettre_creation_pendant_examen_ok() throws Exception {
        // objetLettre encore envoyé par un ancien frontend : ignoré (compat rétroactive), pas d'erreur.
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":1,\"objetLettre\":\"Renvoi du dossier\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idExamen").value(1))
                .andExpect(jsonPath("$.idDossier").value(1))
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.objetLettre").doesNotExist());
    }

    @Test
    @DisplayName("Lettre de renvoi — création sans objetLettre → 201 (objet désormais fixe)")
    void lettre_creation_sans_objet_ok() throws Exception {
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idExamen").value(1))
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.objetLettre").doesNotExist());
    }

    @Test
    @DisplayName("Lettre de renvoi — le DTO ne contient plus objetLettre")
    void lettre_dto_sans_objet() throws Exception {
        int id = seedLettreSoumise();
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLettre").value(id))
                .andExpect(jsonPath("$.objetLettre").doesNotExist());
    }

    @Test
    @DisplayName("Lettre de renvoi — détail d'une lettre SIGNE → nomSignataire (prénoms nom) non vide")
    void lettre_detail_signataire_ok() throws Exception {
        int id = seedLettreSoumise();
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imSignataire").value("CTRCC1"))
                .andExpect(jsonPath("$.nomSignataire").value("Prenoms NomCTRCC1"));
    }

    @Test
    @DisplayName("Assistant contrôleur — login ASSANT1/Test@1234 → 200, role ASSISTANT_CONTROLEUR")
    void assistant_login_ok() throws Exception {
        controleurRepository.save(controleur("ASSANT1", 9, "ANT"));
        compteAuthRepository.save(new CompteAuth("ASSANT1",
                passwordEncoder.encode("Test@1234"), "CONTROLEUR", "ASSANT1", true));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ASSANT1\",\"motDePasse\":\"Test@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ASSISTANT_CONTROLEUR"));
    }

    @Test
    @DisplayName("Assistant contrôleur — accès GET /api/lettre-renvois → 200")
    void assistant_acces_lettre_renvoi_ok() throws Exception {
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        mvc.perform(get("/api/lettre-renvois").header("Authorization", tokenAss))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Assistant contrôleur — accès GET /api/pv-examens → 200")
    void assistant_acces_pv_ok() throws Exception {
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        mvc.perform(get("/api/pv-examens").header("Authorization", tokenAss))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Lettre de renvoi — N lettres sur le même examen → 201 chacune")
    void lettre_multiple_meme_examen_ok() throws Exception {
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1,\"objetLettre\":\"Lettre 1\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1,\"objetLettre\":\"Lettre 2\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Assistant contrôleur hors localité → accès lettre 403")
    void assistant_acces_lettre_autre_localite_403() throws Exception {
        int id = seedLettreSoumise();   // examen 1 → localité ANT
        String tokenAssTms = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "TMS");
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenAssTms))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PRMP — GET /api/lettre-renvois/mes-lettres (lecture seule) → 200")
    void prmp_mes_lettres_lecture_seule() throws Exception {
        mvc.perform(get("/api/lettre-renvois/mes-lettres").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Lot D §5 — mes-lettres : nom du signataire et flag « lue » résolus EN LOT — "
            + "valeurs identiques par lettre, et coût en requêtes indépendant de la taille de la liste")
    void mesLettres_nomSignataireEtLue_resolusEnLot() throws Exception {
        // ⚠️ Audit 2026-08-27 (lot D §5) : peuplerNomSignataire + peuplerLue faisaient DEUX requêtes
        // PAR LETTRE. Ici, 4 lettres de 4 signataires différents (dont un IM inconnu du référentiel)
        // et 2 lectures posées : le contenu doit être exact ET le coût constant.
        org.hibernate.stat.Statistics stats = entityManager.getEntityManagerFactory()
                .unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        int seule = seedLettreSigneeSignee("CTRCC1");
        marquerLue(seule, "PRMP001");
        stats.clear();
        mvc.perform(get("/api/lettre-renvois/mes-lettres").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        long coutUneLettre = stats.getPrepareStatementCount();

        int deux = seedLettreSigneeSignee("CTRSEC");
        int trois = seedLettreSigneeSignee("CTRMEM");
        int quatre = seedLettreSigneeSignee("INCONU");   // IM absent de tr_controleur (aucune FK sur IM_SIGNATAIRE)
        marquerLue(trois, "PRMP001");
        stats.clear();
        String corps = mvc.perform(get("/api/lettre-renvois/mes-lettres").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andReturn().getResponse().getContentAsString();
        long coutQuatreLettres = stats.getPrepareStatementCount();

        // Comportement identique à la résolution unitaire : nom « Prénoms Nom », null si l'IM est inconnu.
        assertEquals("Prenoms NomCTRCC1", lire(corps, seule, "nomSignataire"));
        assertEquals("Prenoms NomCTRSEC", lire(corps, deux, "nomSignataire"));
        assertNull(lire(corps, quatre, "nomSignataire"), "signataire inconnu du référentiel → nomSignataire null");
        // Flag « lue » individuel (login de l'agent), lettre par lettre.
        assertEquals(Boolean.TRUE, lire(corps, seule, "lue"));
        assertEquals(Boolean.FALSE, lire(corps, deux, "lue"));
        assertEquals(Boolean.TRUE, lire(corps, trois, "lue"));
        assertEquals(Boolean.FALSE, lire(corps, quatre, "lue"));

        // Garde-fou : sans compteur actif, l'assertion suivante serait vraie pour de mauvaises raisons.
        assertTrue(coutUneLettre > 0, "statistiques Hibernate actives");
        // Trois lettres de plus ne doivent pas coûter trois requêtes de plus : avant le lot D, elles en
        // coûtaient SIX (un findById du signataire + un exists de lecture par lettre).
        assertTrue(coutQuatreLettres - coutUneLettre <= 1,
                "coût constant attendu (1 lettre : " + coutUneLettre + " requêtes ; 4 lettres : "
                        + coutQuatreLettres + ")");
    }

    /** Lettre SIGNE du dossier 1 (PPM de PRMP001), attribuée à un signataire donné. */
    private int seedLettreSigneeSignee(String imSignataire) {
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(1);
        l.setIdDossier(1);
        l.setObjetLettre("Renvoi");
        l.setStatut("SIGNE");
        l.setImSignataire(imSignataire);
        return lettreRenvoiRepository.save(l).getIdLettre();
    }

    /** Pose la trace de lecture d'un agent sur une lettre (unicité uk_lettre_lue_agent). */
    private void marquerLue(int idLettre, String login) {
        cnm.prs.entity.LettreRenvoiLue lue = new cnm.prs.entity.LettreRenvoiLue();
        lue.setIdLettre(idLettre);
        lue.setLoginAgent(login);
        lue.setIdPrmp("PRMP001");
        lue.setDateLecture(java.time.LocalDateTime.of(2026, 6, 6, 9, 0));
        lueRepository.save(lue);
    }

    /** Valeur d'un champ de la lettre {@code idLettre} dans la réponse JSON de la liste. */
    private Object lire(String corps, int idLettre, String champ) {
        java.util.List<java.util.Map<String, Object>> lettres = com.jayway.jsonpath.JsonPath.read(corps, "$");
        return lettres.stream()
                .filter(m -> Integer.valueOf(idLettre).equals(m.get("idLettre")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("lettre " + idLettre + " absente de la liste"))
                .get(champ);
    }

    @Test
    @DisplayName("Lettre de renvoi — un Membre tente de signer → 403")
    void lettre_signer_membre_403() throws Exception {
        int id = seedLettreSoumise();
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Lettre de renvoi — le CC signe → SIGNE")
    void lettre_signer_cc_ok() throws Exception {
        int id = seedLettreSoumise();
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SIGNE"))
                .andExpect(jsonPath("$.imSignataire").value("CTRCC1"));
    }

    @Test
    @DisplayName("Lettre de renvoi — le Président signe → SIGNE")
    void lettre_signer_president_ok() throws Exception {
        int id = seedLettreSoumise();
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SIGNE"))
                .andExpect(jsonPath("$.imSignataire").value("CTRPRE"));
    }

    @Test
    @DisplayName("Signature lettre (centrale ANT) : le CC signe → 200")
    void signature_centrale_cc_ok() throws Exception {
        int id = seedLettreSoumiseLoc(710, "ANT");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("SIGNE"));
    }

    @Test
    @DisplayName("Signature lettre (centrale ANT) : le Président signe → 200")
    void signature_centrale_president_ok() throws Exception {
        int id = seedLettreSoumiseLoc(711, "ANT");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("SIGNE"));
    }

    @Test
    @DisplayName("Signature lettre (régionale TMS) : le CC DE LA LOCALITÉ signe → 200")
    void signature_regionale_cc_ok() throws Exception {
        int id = seedLettreSoumiseLoc(712, "TMS");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCcTms()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("SIGNE"));
    }

    @Test
    @DisplayName("Signature lettre régionale (⚠️ audit lot B) : un CC d'une AUTRE localité → 403 ; la lettre reste SOUMIS")
    void signature_regionale_ccAutreLocalite_403() throws Exception {
        int id = seedLettreSoumiseLoc(716, "TMS");
        // Le CC d'ANT signait la lettre régionale de TMS — dont le PDF porte pourtant l'en-tête de
        // TOAMASINA et la ligne « Le Chef de la Commission Régionale des Marchés ».
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"));
    }

    @Test
    @DisplayName("Suppression d'une lettre (⚠️ audit lot B) : lettre SIGNÉE → 409 (notifiée à la PRMP, PDF sur le "
            + "FSX) ; brouillon → 204")
    void suppression_lettreSignee_refusee() throws Exception {
        int signee = seedLettreSignee();
        mvc.perform(delete("/api/lettre-renvois/" + signee).header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertTrue(lettreRenvoiRepository.existsById(signee),
                "la lettre signée est conservée");

        // NON-RÉGRESSION : un brouillon (jamais soumis, jamais notifié) reste supprimable.
        String creee = mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int brouillon = com.jayway.jsonpath.JsonPath.read(creee, "$.idLettre");
        mvc.perform(delete("/api/lettre-renvois/" + brouillon).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
    }

    /** Jeton du Chef de commission de TMS (CTRCC2, seedé dans le socle). */
    private String tokenCcTms() {
        return bearer("CTRCC2", cnm.prs.enums.ProfilUtilisateur.CHEF_COMMISSION,
                cnm.prs.enums.TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
    }

    @Test
    @DisplayName("Signature lettre (régionale TMS) : le Président signe → 403")
    void signature_regionale_president_403() throws Exception {
        int id = seedLettreSoumiseLoc(713, "TMS");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenPresident))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Document : signature centrale → PDF téléchargeable (200, application/pdf)")
    @org.junit.jupiter.api.Tag("word")   // telechargement du PDF : conversion docx→PDF via MS Word, exclu en CI Linux
    void document_genere_centrale_ok() throws Exception {
        byte[] pdf = signerEtPdf(714, "ANT", tokenCc);
        assertTrue(pdf.length > 0 && new String(pdf, 0, 4, StandardCharsets.ISO_8859_1).equals("%PDF"),
                "PDF généré (en-tête %PDF)");
    }

    @Test
    @DisplayName("Document : signature régionale → PDF téléchargeable (200, application/pdf)")
    @org.junit.jupiter.api.Tag("word")   // telechargement du PDF : conversion docx→PDF via MS Word, exclu en CI Linux
    void document_genere_regionale_ok() throws Exception {
        int id = seedLettreSoumiseLoc(715, "TMS");
        // ⚠️ Audit lot B — la lettre régionale se signe dans SA localité : CC de TMS. Le téléchargement
        // reste demandé par le Président (le périmètre de LECTURE suit la localité de réception, ANT
        // dans cette fixture montée sur l'examen 1).
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCcTms()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/lettre-renvois/" + id + "/document").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Document : texte EXACT du modèle (pas une paraphrase)")
    @org.junit.jupiter.api.Tag("word")   // telechargement du PDF : conversion docx→PDF via MS Word, exclu en CI Linux
    void document_texte_identique_modele() throws Exception {
        String texte = texteDuPdf(signerEtPdf(740, "ANT", tokenCc));
        assertTrue(texte.contains("Commission Nationale des Marchés renvoie")
                && texte.contains("une séance ultérieure en demandant au service de"),
                "phrase exacte du modèle présente dans le PDF");
    }

    @Test
    @DisplayName("Document : le PDF contient l'image de l'emblème")
    @org.junit.jupiter.api.Tag("word")   // telechargement du PDF : conversion docx→PDF via MS Word, exclu en CI Linux
    void document_contient_image() throws Exception {
        assertTrue(contientImage(signerEtPdf(741, "ANT", tokenCc)), "le PDF contient au moins un objet image");
    }

    @Test
    @DisplayName("Document : signataire = nom réel seul (pas de texte parasite)")
    @org.junit.jupiter.api.Tag("word")   // telechargement du PDF : conversion docx→PDF via MS Word, exclu en CI Linux
    void document_signataire_sans_texte_parasite() throws Exception {
        String texte = texteDuPdf(signerEtPdf(742, "ANT", tokenCc));
        assertFalse(texte.contains("Le Président ou le Chef de Commission,"),
                "pas de libellé de rôle parasite codé en dur");
        assertTrue(texte.contains("NomCTRCC1"), "nom réel du signataire présent");
    }

    @Test
    @DisplayName("Document : aucun placeholder résiduel <...>")
    @org.junit.jupiter.api.Tag("word")   // telechargement du PDF : conversion docx→PDF via MS Word, exclu en CI Linux
    void document_aucun_placeholder_residuel() throws Exception {
        String texte = texteDuPdf(signerEtPdf(743, "ANT", tokenCc));
        assertFalse(java.util.regex.Pattern.compile("<[A-Z _]+>").matcher(texte).find(),
                "aucun placeholder <...> ne subsiste dans le texte du PDF");
    }

    @Test
    @DisplayName("Document : en-tête républicain présent")
    @org.junit.jupiter.api.Tag("word")   // telechargement du PDF : conversion docx→PDF via MS Word, exclu en CI Linux
    void document_genere_entete_present() throws Exception {
        String texte = texteDuPdf(signerEtPdf(744, "ANT", tokenCc));
        assertTrue(texte.contains("REPOBLIKAN") && texte.contains("MADAGASIKARA"),
                "en-tête républicain présent dans le PDF");
    }

    @Test
    @DisplayName("Document : corps de la lettre saisi présent")
    @org.junit.jupiter.api.Tag("word")   // telechargement du PDF : conversion docx→PDF via MS Word, exclu en CI Linux
    void document_genere_corps_lettre_present() throws Exception {
        assertTrue(texteDuPdf(signerEtPdf(745, "ANT", tokenCc)).contains("Corps de la lettre de renvoi"),
                "texte du corps présent dans le PDF");
    }

    @Test
    @DisplayName("Document : PDF stocké sur le FSX (répertoire LR/), produit après la signature")
    @org.junit.jupiter.api.Tag("word")   // production du PDF : conversion docx→PDF via MS Word, exclu en CI Linux
    void document_genere_stocke_fsx_ok() throws Exception {
        int id = seedLettreSoumiseLoc(730, "ANT");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                // ⚠️ 2026-08-28 (option B) — la signature ne produit PLUS le PDF : elle commite seule et
                // le document part après commit. À cet instant précis, il n'existe donc pas encore.
                .andExpect(jsonPath("$.documentDisponible").value(false));
        // Le téléchargement force la production (filet de régénération paresseuse) : c'est LUI qui
        // garantit qu'une lettre signée finit toujours par avoir son fichier sur le FSX.
        mvc.perform(get("/api/lettre-renvois/" + id + "/document").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        String chemin = lettreRenvoiRepository.findById(id).orElseThrow().getCheminDocument();
        assertTrue(chemin != null && java.nio.file.Files.exists(java.nio.file.Path.of(chemin)),
                "fichier PDF présent sur le FSX : " + chemin);
        assertTrue(chemin.endsWith("00007_DDP_CRM-ANT_LR_2026.pdf"),
                "nom de fichier dérivé de refLettre avec '/' remplacés par '_'");
    }

    @Test
    @DisplayName("Document régional : en-tête contient la localité du dossier (TOAMASINA)")
    @org.junit.jupiter.api.Tag("word")   // telechargement du PDF : conversion docx→PDF via MS Word, exclu en CI Linux
    void document_genere_localite_dossier_ok() throws Exception {
        int id = seedLettreSoumiseLoc(731, "TMS");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCcTms()))
                .andExpect(status().isOk());
        byte[] pdf = mvc.perform(get("/api/lettre-renvois/" + id + "/document").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertTrue(texteDuPdf(pdf).contains("COMMISSION REGIONALE DES MARCHES TOAMASINA"),
                "localité du dossier injectée dans l'en-tête régional");
    }

    @Test
    @DisplayName("Document régional : signataire « Le Chef de la Commission Régionale des Marchés »")
    @org.junit.jupiter.api.Tag("word")   // telechargement du PDF : conversion docx→PDF via MS Word, exclu en CI Linux
    void document_genere_signataire_regional_ok() throws Exception {
        int id = seedLettreSoumiseLoc(733, "TMS");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCcTms()))
                .andExpect(status().isOk());
        byte[] pdf = mvc.perform(get("/api/lettre-renvois/" + id + "/document").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertTrue(texteDuPdf(pdf).contains("Le Chef de la Commission Régionale des Marchés"),
                "ligne signataire régionale corrigée dans le modèle");
    }

    /** Signe une lettre (dossier localisé) et renvoie le PDF téléchargé. */
    private byte[] signerEtPdf(int idDossier, String localite, String token) throws Exception {
        int id = seedLettreSoumiseLoc(idDossier, localite);
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", token))
                .andExpect(status().isOk());
        return mvc.perform(get("/api/lettre-renvois/" + id + "/document").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    /** Crée un dossier localisé (entité 1) + une lettre SOUMIS (examen 1) ; renvoie la PK de la lettre. */
    private int seedLettreSoumiseLoc(int idDossier, String localite) {
        Dossier d = dossier(idDossier, "EXAMINE");
        d.setIdLocalite(localite);
        d.setIdEntiteContract(1);
        dossierRepository.save(d);
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(1);
        l.setIdDossier(idDossier);
        l.setRefLettre("00007/DDP/CRM-" + localite + "/LR/2026");   // contient des '/' (à nettoyer dans le nom de fichier)
        l.setObjetLettre("Renvoi");
        l.setCorpsLettre("Corps de la lettre de renvoi.");
        l.setDateLettre(LocalDate.of(2026, 6, 20));
        l.setDateExamen(LocalDate.of(2026, 6, 15));
        l.setStatut("SOUMIS");
        return lettreRenvoiRepository.save(l).getIdLettre();
    }
}
