package cnm.prs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import cnm.prs.entity.DemandeRetrait;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Capm;
import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.Nature;
import cnm.prs.entity.Ppm;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * Demande de retrait d'un dossier (§3.3) : creation avec lettre PDF obligatoire, dossiers
 * retirables, decision du CC ou du President, purge FK-safe du circuit a l'acceptation,
 * historique et scoping par localite.
 */
class RetraitIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Demandes de retrait : ouverture de l'écran (mes-demandes) → consultation enregistrée")
    void demande_retrait_vue_maj_ok() throws Exception {
        mvc.perform(get("/api/demande-retraits/mes-demandes").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        assertTrue(demandeRetraitVueRepository.findByIdPrmp("PRMP001").isPresent(),
                "date de dernière consultation enregistrée pour la PRMP");
    }

    @Test
    @DisplayName("Filtre demandes de retrait : PRMP voit les siennes, CC celles de sa localité")
    void filtreLocalite_demandeRetrait() throws Exception {
        // PRMP001 voit sa demande.
        mvc.perform(get("/api/demande-retraits").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // Le CC d'ANT voit la demande de sa localité (dossier ANT).
        mvc.perform(get("/api/demande-retraits").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // Une autre PRMP ne voit rien.
        String tokenAutrePrmp = bearer("PRMPXX", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMPXX", "ANT");
        mvc.perform(get("/api/demande-retraits").header("Authorization", tokenAutrePrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Demande de retrait — création OK : identité JWT, EN_ATTENTE, notif DEMANDE_RETRAIT_A_VALIDER au CC + Président")
    void retrait_creation_ok() throws Exception {
        Dossier d = dossier(120, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        mvc.perform(posterRetrait("{\"idDossier\":120,\"motifRetrait\":\"Erreur de saisie\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idDemandeRetrait").isNumber())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.idPrmp").value("PRMP001"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='DEMANDE_RETRAIT_A_VALIDER')].destinataireIm", hasItem("CTRCC1")))
                .andExpect(jsonPath("$[?(@.typeNotif=='DEMANDE_RETRAIT_A_VALIDER')].destinataireIm", hasItem("CTRPRE")));
    }

    @Test
    @DisplayName("Demande de retrait — identité du demandeur = JWT (corps idPrmp ignoré)")
    void retrait_creation_identiteJWT() throws Exception {
        Dossier d = dossier(121, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        mvc.perform(posterRetrait("{\"idDossier\":121,\"idPrmp\":\"USURP\",\"motifRetrait\":\"x\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPrmp").value("PRMP001"));
    }

    @Test
    @DisplayName("Demande de retrait — non propriétaire → 403")
    void retrait_creation_nonProprietaire_403() throws Exception {
        // Dossier non possédé par PRMP001 (idPrmp null) → la PRMP connectée n'est pas propriétaire.
        Dossier d = dossier(122, "SOUMIS"); d.setIdLocalite("ANT");
        dossierRepository.save(d);
        mvc.perform(posterRetrait("{\"idDossier\":122,\"motifRetrait\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Demande de retrait — dossier à PV signé (PV_SIGNE) → 409 (au-delà de « avant PV signé », §3.3)")
    void retrait_creation_pvSigne_409() throws Exception {
        // §3.3 — le retrait est refusé dès que le PV est signé. Dossier PV_SIGNE de PRMP001, sans demande préalable.
        Dossier d = dossier(124, "PV_SIGNE"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        mvc.perform(posterRetrait("{\"idDossier\":124,\"motifRetrait\":\"x\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Retrait §3.3 — un dossier EXAMINE est retirable (listé + POST 201) ; un PV_SIGNE ni listé ni acceptable (409)")
    void retrait_avantPvSigne_examineRetirable_pvSigneRefuse() throws Exception {
        // Deux dossiers de PRMP001 : l'un examiné (avant PV signé → éligible), l'autre PV signé (au-delà → refusé).
        Dossier ex = dossier(700, "EXAMINE"); ex.setIdLocalite("ANT"); ex.setIdPrmp("PRMP001");
        dossierRepository.save(ex);
        Dossier pv = dossier(701, "PV_SIGNE"); pv.setIdLocalite("ANT"); pv.setIdPrmp("PRMP001");
        dossierRepository.save(pv);

        // La liste déroulante des retirables inclut l'EXAMINE, exclut le PV_SIGNE (même ensemble que la garde).
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==700)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==701)]", hasSize(0)));

        // POST retrait sur l'EXAMINE → 201 (créé, EN_ATTENTE).
        mvc.perform(posterRetrait("{\"idDossier\":700,\"motifRetrait\":\"corriger avant PV\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));

        // POST retrait sur le PV_SIGNE → 409 (au-delà de la limite).
        mvc.perform(posterRetrait("{\"idDossier\":701,\"motifRetrait\":\"x\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Retrait §3.3 — acceptation sur un EXAMINE avec circuit complet : dossier→BROUILLON + purge FK-safe de tout l'historique")
    void retrait_accepte_purgeCircuitComplet() throws Exception {
        // Dossier EXAMINE de PRMP001 portant tout l'enchaînement de circuit (avant PV signé → le PV est un projet).
        Dossier d = dossier(710, "EXAMINE"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        d.setRefeDossier("00009/DDP/CRM-ANT/2026");                      // réf de réception (à invalider)
        dossierRepository.save(d);
        Ppm p = ppm(710, 710, "PRMP001"); p.setReference("00010/DGB/PPM/2026"); ppmRepository.save(p);   // réf initiale
        receptionRepository.save(reception(710, 710, "CTRCC1", true));
        dispatchRepository.save(dispatch(710, 710, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(710, 710, "CTRMEM"));
        // Détail d'examen (7100) + observation (petit-enfant). Le point de contrôle référencé doit exister (FK).
        PointsCtrl pc = new PointsCtrl();
        pc.setIdPointCtrl(1); pc.setLibelPointCtrl("Montant"); pc.setObligatoire(true); pc.setIdTypeDossier("DDP");
        pointsCtrlRepository.save(pc);
        ExamenDetail ed = new ExamenDetail();
        ed.setIdDetailExamen(7100); ed.setIdExamen(710); ed.setIdPtControle(1); ed.setConforme(false);
        examenDetailRepository.save(ed);
        cnm.prs.entity.ObservationControle obs = new cnm.prs.entity.ObservationControle();
        obs.setIdDetail(7100); obs.setAuLieuDe("500000"); obs.setLire("5000000"); obs.setOrdre(1);
        observationControleRepository.save(obs);
        // Projet de PV (710) + navette (enfant).
        cnm.prs.entity.PvExamen pv = new cnm.prs.entity.PvExamen();
        pv.setIdPv(710); pv.setIdExamen(710); pv.setIdAvis("FAV"); pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("BROUILLON"); pv.setNbNavettes(1);
        pvExamenRepository.save(pv);
        cnm.prs.entity.PvNavette nav = new cnm.prs.entity.PvNavette();
        nav.setIdNavette(7101); nav.setIdPv(710); nav.setNumNavette(1); nav.setSens("ALLER");
        nav.setImActeur("CTRMEM"); nav.setDateAction(LocalDateTime.of(2026, 6, 5, 9, 0));
        pvNavetteRepository.save(nav);
        // Copie de dossier (enfant du dispatch) + lettre de renvoi + accusé de lecture (petit-enfant).
        cnm.prs.entity.CopieDossier cop = new cnm.prs.entity.CopieDossier();
        cop.setIdCopie(7102); cop.setIdDispatch(710); cop.setIdDossier(710); cop.setImDestinataire("CTRMEM");
        cop.setTypeCopie("MEMBRE"); cop.setDateTransmission(LocalDateTime.of(2026, 6, 5, 9, 0)); cop.setAccuseReception(false);
        copieDossierRepository.save(cop);
        cnm.prs.entity.LettreRenvoi lr = new cnm.prs.entity.LettreRenvoi();
        lr.setIdExamen(710); lr.setIdDossier(710); lr.setObjetLettre("Renvoi"); lr.setStatut("SIGNE");
        int idLettre = lettreRenvoiRepository.save(lr).getIdLettre();
        cnm.prs.entity.LettreRenvoiLue lue = new cnm.prs.entity.LettreRenvoiLue();
        // ⚠️ 2026-08-27 : la trace porte le login de l'agent (NOT NULL), ID_PRMP restant la tutelle.
        lue.setIdLettre(idLettre); lue.setIdPrmp("PRMP001"); lue.setLoginAgent("PRMP001");
        lue.setDateLecture(LocalDateTime.of(2026, 6, 6, 9, 0));
        lueRepository.save(lue);

        int drId = demandeRetraitRepository.save(demandeRetrait(0, 710, "PRMP001")).getIdDemandeRetrait();

        // Acceptation → 200 : aucune violation de FK malgré le circuit complet.
        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"));

        // Dossier → BROUILLON avec sa référence initiale (PPM) restaurée.
        Dossier apres = dossierRepository.findById(710).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("BROUILLON", apres.getStatut());
        org.junit.jupiter.api.Assertions.assertEquals("00010/DGB/PPM/2026", apres.getRefeDossier());
        // Tout le circuit est purgé (feuilles → racine).
        org.junit.jupiter.api.Assertions.assertFalse(receptionRepository.existsById(710), "réception purgée");
        org.junit.jupiter.api.Assertions.assertFalse(dispatchRepository.existsById(710), "dispatch purgé");
        org.junit.jupiter.api.Assertions.assertFalse(examenRepository.existsById(710), "examen purgé");
        org.junit.jupiter.api.Assertions.assertFalse(examenDetailRepository.existsById(7100), "détail d'examen purgé");
        org.junit.jupiter.api.Assertions.assertTrue(observationControleRepository.findByIdDetailOrderByOrdreAsc(7100).isEmpty(), "observations purgées");
        org.junit.jupiter.api.Assertions.assertFalse(pvExamenRepository.existsById(710), "PV purgé");
        org.junit.jupiter.api.Assertions.assertFalse(pvNavetteRepository.existsById(7101), "navette purgée");
        org.junit.jupiter.api.Assertions.assertFalse(copieDossierRepository.existsById(7102), "copie purgée");
        org.junit.jupiter.api.Assertions.assertFalse(lettreRenvoiRepository.existsById(idLettre), "lettre de renvoi purgée");
        org.junit.jupiter.api.Assertions.assertFalse(lueRepository.existsByIdLettreAndLoginAgent(idLettre, "PRMP001"), "accusé de lecture purgé");
    }

    @Test
    @DisplayName("Retrait C3 §3.3/§3.5 — le PV a été signé DEPUIS la demande : l'acceptation est refusée (409) et le circuit signé reste intact")
    void retrait_accepte_dossierProgresseDepuisLaDemande_409() throws Exception {
        // ⚠️ Audit 2026-08-27 (C3) — une demande EN_ATTENTE ne suspend pas le circuit : le dossier était
        // EXAMINE (donc retirable) à la demande, il est PV_SIGNE au moment de la décision.
        Dossier d = dossier(720, "EXAMINE"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        receptionRepository.save(reception(720, 720, "CTRCC1", true));
        dispatchRepository.save(dispatch(720, 720, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(720, 720, "CTRMEM"));
        cnm.prs.entity.PvExamen pv = new cnm.prs.entity.PvExamen();
        pv.setIdPv(720); pv.setIdExamen(720); pv.setIdAvis("FAV"); pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("SIGNE"); pv.setNbNavettes(1);
        pv.setDateSignatureMembre(java.time.LocalDate.now());
        pv.setDateSignaturePresident(java.time.LocalDate.now());
        pvExamenRepository.save(pv);

        // La demande, elle, a été enregistrée avant : elle est toujours EN_ATTENTE.
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 720, "PRMP001")).getIdDemandeRetrait();
        // Le circuit a progressé entre-temps jusqu'à la signature du PV.
        Dossier progresse = dossierRepository.findById(720).orElseThrow();
        progresse.setStatut("PV_SIGNE");
        dossierRepository.save(progresse);

        // Avant le correctif : 200, dossier ramené en BROUILLON et PV signé DÉTRUIT par la purge.
        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("caduque")));

        // Rien n'a bougé : ni le statut du dossier, ni le PV signé, ni le reste du circuit.
        org.junit.jupiter.api.Assertions.assertEquals("PV_SIGNE",
                dossierRepository.findById(720).orElseThrow().getStatut());
        assertTrue(pvExamenRepository.existsById(720), "le PV signé survit à la demande caduque");
        assertTrue(examenRepository.existsById(720), "l'examen n'est pas purgé");
        assertTrue(receptionRepository.existsById(720), "la réception n'est pas purgée");
        // La demande reste EN_ATTENTE : le décideur peut la REFUSER (le refus ne touche pas au circuit).
        org.junit.jupiter.api.Assertions.assertEquals("EN_ATTENTE",
                demandeRetraitRepository.findById(drId).orElseThrow().getStatut());
        mvc.perform(post("/api/demande-retraits/" + drId + "/refuser").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"PV deja signe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REFUSEE"));
    }

    @Test
    @DisplayName("Retrait C3 — NON-RÉGRESSION : tant que le dossier reste avant PV signé, l'acceptation passe (200)")
    void retrait_accepte_dossierEncoreRetirable_ok() throws Exception {
        Dossier d = dossier(721, "DISPATCHE"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 721, "PRMP001")).getIdDemandeRetrait();
        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"));
        org.junit.jupiter.api.Assertions.assertEquals("BROUILLON",
                dossierRepository.findById(721).orElseThrow().getStatut());
    }

    @Test
    @DisplayName("Demande de retrait — doublon EN_ATTENTE → 409")
    void retrait_creation_doublonEnAttente_409() throws Exception {
        Dossier d = dossier(123, "PRET_DISPATCH"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        mvc.perform(posterRetrait("{\"idDossier\":123,\"motifRetrait\":\"x\"}"))
                .andExpect(status().isCreated());
        mvc.perform(posterRetrait("{\"idDossier\":123,\"motifRetrait\":\"y\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Retrait — lettre obligatoire : absente, non-PDF ou trop volumineuse → 400 (magic-bytes, pas le Content-Type déclaré)")
    void retrait_lettre_validations400() throws Exception {
        Dossier d = dossier(125, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        MockMultipartFile data = new MockMultipartFile("data", "", "application/json",
                "{\"idDossier\":125,\"motifRetrait\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        // Partie « fichier » absente → 400.
        mvc.perform(multipart("/api/demande-retraits").file(data).header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("obligatoire")));
        // Contenu non-PDF (PNG déguisé en Content-Type application/pdf) → 400 : la validation lit les magic-bytes.
        mvc.perform(multipart("/api/demande-retraits").file(data)
                .file(new MockMultipartFile("fichier", "lettre.pdf", "application/pdf",
                        new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A }))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("PDF")));
        // PDF trop volumineux (> 10 Mo) → 400.
        byte[] gros = new byte[10 * 1024 * 1024 + 1];
        gros[0] = '%'; gros[1] = 'P'; gros[2] = 'D'; gros[3] = 'F'; gros[4] = '-';
        mvc.perform(multipart("/api/demande-retraits").file(data)
                .file(new MockMultipartFile("fichier", "lettre.pdf", "application/pdf", gros))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("volumineuse")));
        // Aucune demande créée par ces tentatives.
        org.junit.jupiter.api.Assertions.assertTrue(
                demandeRetraitRepository.findAll().stream().noneMatch(dr -> Integer.valueOf(125).equals(dr.getIdDossier())));
    }

    @Test
    @DisplayName("Retrait — lettre jointe : DTO expose nomFichier/tailleFichier ; GET document pour PRMP demanderesse + décideur, 403 hors périmètre")
    void retrait_lettre_creationEtLecture() throws Exception {
        Dossier d = dossier(126, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        byte[] pdf = lettreRetraitPdf().getBytes();
        String rep = mvc.perform(posterRetrait("{\"idDossier\":126,\"motifRetrait\":\"avec lettre\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomFichier").value("lettre-retrait.pdf"))
                .andExpect(jsonPath("$.tailleFichier").value(pdf.length))
                .andReturn().getResponse().getContentAsString();
        int id = Integer.parseInt(rep.replaceAll(".*\"idDemandeRetrait\":(\\d+).*", "$1"));

        // Lecture de la lettre : PRMP demanderesse, CC de la localité (décideur), Président — 200, PDF sain.
        mvc.perform(get("/api/demande-retraits/" + id + "/document").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("lettre-retrait.pdf")))
                .andExpect(content().bytes(pdf));
        mvc.perform(get("/api/demande-retraits/" + id + "/document").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        mvc.perform(get("/api/demande-retraits/" + id + "/document").header("Authorization", tokenPresident))
                .andExpect(status().isOk());

        // Hors périmètre : CC d'une autre localité, Membre (non décideur), autre PRMP → 403.
        mvc.perform(get("/api/demande-retraits/" + id + "/document")
                .header("Authorization", bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/demande-retraits/" + id + "/document").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/demande-retraits/" + id + "/document")
                .header("Authorization", bearer("PRMP999", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP999", "ANT")))
                .andExpect(status().isForbidden());

        // Les listes exposent les métadonnées de la lettre (jamais le contenu).
        mvc.perform(get("/api/demande-retraits/mes-demandes").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDemandeRetrait==" + id + ")].nomFichier", hasItem("lettre-retrait.pdf")));
    }

    @Test
    @DisplayName("Retrait — la lettre SURVIT à l'acceptation (purge du circuit) : elle justifie la décision ; rétro-compat : demande sans pièce → nomFichier null + document 404")
    void retrait_lettre_survitAcceptation_etRetroCompat() throws Exception {
        Dossier d = dossier(127, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        String rep = mvc.perform(posterRetrait("{\"idDossier\":127,\"motifRetrait\":\"lettre à conserver\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = Integer.parseInt(rep.replaceAll(".*\"idDemandeRetrait\":(\\d+).*", "$1"));
        // Acceptation par le CC → purge du circuit + dossier BROUILLON… mais la lettre reste lisible
        // (stockage dédié t_piece_demande_retrait, hors t_piece_jointe_dossier purgée avec le circuit).
        mvc.perform(post("/api/demande-retraits/" + id + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"))
                .andExpect(jsonPath("$.nomFichier").value("lettre-retrait.pdf"));
        mvc.perform(get("/api/demande-retraits/" + id + "/document").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(content().bytes(lettreRetraitPdf().getBytes()));

        // Rétro-compat — demande créée avant l'obligation (sans pièce) : valide, nomFichier null, document 404 explicite.
        Dossier ancien = dossier(128, "SOUMIS"); ancien.setIdLocalite("ANT"); ancien.setIdPrmp("PRMP001");
        dossierRepository.save(ancien);
        int idAncien = demandeRetraitRepository.save(demandeRetrait(0, 128, "PRMP001")).getIdDemandeRetrait();
        mvc.perform(get("/api/demande-retraits/" + idAncien).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomFichier").value(nullValue()));
        mvc.perform(get("/api/demande-retraits/" + idAncien + "/document").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Décision retrait — CC de la localité accepte → ACCEPTEE, dossier BROUILLON, notif RETRAIT_ACCEPTE")
    void decision_accepter_parCc_dossierBrouillon() throws Exception {
        Dossier d = dossier(130, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        d.setRefeDossier("00002/DDP/CRM-ANT/2026");   // réf. posée à la réception, à invalider au retrait
        dossierRepository.save(d);
        Ppm p = ppm(130, 130, "PRMP001"); p.setReference("00003/DGB/PPM/2026"); ppmRepository.save(p);  // réf initiale
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 130, "PRMP001")).getIdDemandeRetrait();

        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"))
                .andExpect(jsonPath("$.imCtrlCc").value("CTRCC1"));
        // Le dossier repasse en BROUILLON avec sa RÉFÉRENCE INITIALE restaurée (celle du PPM), la réf de
        // réception étant invalidée.
        Dossier apres = dossierRepository.findById(130).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("BROUILLON", apres.getStatut());
        org.junit.jupiter.api.Assertions.assertEquals("00003/DGB/PPM/2026", apres.getRefeDossier());
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='RETRAIT_ACCEPTE')]", hasSize(1)));
        // ⚠️ Audit lot B — la décision était notifiée par E-MAIL SEUL (destinataireRef nul) : invisible
        // de « mes notifications » dès que l'e-mail du compte diffère de t_prmp.EMAIL_PRMP.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeNotif=='RETRAIT_ACCEPTE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='RETRAIT_ACCEPTE')].idObjet", hasItem(130)));

        // ⚠️ Audit lot B — une demande DÉCIDÉE n'est plus supprimable : sa lettre justifie la décision
        // (règle 2026-08-17) et lui survit parce que la demande survit.
        mvc.perform(delete("/api/demande-retraits/" + drId).header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertTrue(demandeRetraitRepository.existsById(drId),
                "la demande décidée est conservée");
    }

    @Test
    @DisplayName("Retrait accepté — le dossier BROUILLON reste entièrement modifiable (édition PPM acceptée)")
    void retrait_accepte_dossier_modifiable() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        Dossier d = dossier(135, "SOUMIS");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        d.setRefeDossier("00006/DDP/CRM-ANT/2026");   // réf de réception (à invalider)
        dossierRepository.save(d);
        Ppm p135 = ppm(135, 135, "PRMP001"); p135.setReference("00005/DGB/PPM/2026"); ppmRepository.save(p135);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 135, "PRMP001")).getIdDemandeRetrait();
        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        // Réf. initiale (PPM) restaurée dès le retrait.
        org.junit.jupiter.api.Assertions.assertEquals("00005/DGB/PPM/2026",
                dossierRepository.findById(135).orElseThrow().getRefeDossier());
        // Dossier BROUILLON issu du retrait → édition PPM (en-tête + ligne de marché) acceptée (200).
        // Ligne NOUVELLE à l'édition → ≥1 processus obligatoire (règle corrigée, comme au POST).
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        String edition = "{\"exercice\":2027,\"signataire\":\"Maj retrait\",\"dateSignature\":\"2026-02-01\","
                + "\"reference\":\"PPM-135-v2\",\"marches\":[{\"montEstim\":500000000,\"idNature\":1,"
                + "\"statut\":\"PREVU\",\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(put("/api/saisies/ppm/135").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(edition))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON"));
    }

    @Test
    @DisplayName("Retrait accepté — l'API renvoie la référence initiale (PPM) du dossier BROUILLON")
    void brouillon_retrait_api_retourne_ref_initiale() throws Exception {
        Dossier d = dossier(140, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        d.setRefeDossier("00002/DDP/CRM-ANT/2026");   // réf de réception (à remplacer)
        dossierRepository.save(d);
        Ppm p = ppm(140, 140, "PRMP001"); p.setReference("00004/DGB/PPM/2026"); ppmRepository.save(p);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 140, "PRMP001")).getIdDemandeRetrait();
        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        // GET du dossier (« Mes brouillons ») → refeDossier = référence initiale (PPM), pas la réf de réception.
        mvc.perform(get("/api/dossiers/140").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refeDossier").value("00004/DGB/PPM/2026"));
    }

    @Test
    @DisplayName("Retrait accepté — la réception résiduelle est supprimée → dossier resoumis réapparaît dans a-receptionner")
    void retrait_accepte_supprime_reception_et_reReceptionnable() throws Exception {
        Dossier d = dossier(145, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        d.setIdTypeDossier("DDP");
        d.setRefeDossier("00003/DDP/CRM-ANT/2026");   // réf de réception résiduelle
        dossierRepository.save(d);
        Ppm p = ppm(145, 145, "PRMP001"); p.setReference("00009/DGB/PPM/2026"); ppmRepository.save(p);
        marcheRepository.save(marche(1450, 145, 145));                  // un PPM doit comporter un marché pour être soumis
        receptionRepository.save(reception(1450, 145, "CTRSEC", true)); // réception résiduelle (bloque a-receptionner)
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 145, "PRMP001")).getIdDemandeRetrait();

        // Acceptation du retrait → dossier BROUILLON + réception supprimée.
        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertFalse(receptionRepository.existsByIdDossier(145),
                "La réception résiduelle doit être supprimée à l'acceptation du retrait");

        // Resoumission par la PRMP → SOUMIS.
        mvc.perform(post("/api/dossiers/145/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());

        // Le dossier réapparaît dans la worklist du Secrétaire (SOUMIS sans réception) → re-réceptionnable.
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        mvc.perform(get("/api/dossiers/a-receptionner").header("Authorization", tokenSec))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==145)]", hasSize(1)));
    }

    @Test
    @DisplayName("Décision retrait — le Président accepte (toutes localités) → ACCEPTEE, dossier BROUILLON")
    void decision_accepter_parPresident_ok() throws Exception {
        Dossier d = dossier(131, "PRET_DISPATCH"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 131, "PRMP001")).getIdDemandeRetrait();

        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"))
                .andExpect(jsonPath("$.imCtrlCc").value("CTRPRE"));
        org.junit.jupiter.api.Assertions.assertEquals("BROUILLON",
                dossierRepository.findById(131).orElseThrow().getStatut());
    }

    @Test
    @DisplayName("Décision retrait — un CC d'une autre localité (dossier TMS) → 403")
    void decision_parCcAutreLocalite_403() throws Exception {
        Dossier d = dossier(132, "SOUMIS"); d.setIdLocalite("TMS"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 132, "PRMP001")).getIdDemandeRetrait();

        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Décision retrait — refus : REFUSEE + motif, dossier inchangé, notif RETRAIT_REFUSE")
    void decision_refuser_dossierInchange() throws Exception {
        Dossier d = dossier(133, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 133, "PRMP001")).getIdDemandeRetrait();

        mvc.perform(post("/api/demande-retraits/" + drId + "/refuser").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Dossier incomplet\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REFUSEE"))
                .andExpect(jsonPath("$.obsDecision").value("Dossier incomplet"));
        // Dossier inchangé (toujours SOUMIS, donc visible).
        mvc.perform(get("/api/dossiers/133").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("SOUMIS"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='RETRAIT_REFUSE')]", hasSize(1)));
    }

    @Test
    @DisplayName("Décision retrait — demande déjà traitée → 409")
    void decision_dejaTraitee_409() throws Exception {
        Dossier d = dossier(134, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 134, "PRMP001")).getIdDemandeRetrait();

        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Dropdown retirables §3.3 : SOUMIS/PRET_DISPATCH/DISPATCHE/EXAMINE de la PRMP ; exclut PV_SIGNE et les dossiers d'autrui")
    void retrait_dropdown_retirables() throws Exception {
        Dossier a = dossier(150, "SOUMIS"); a.setIdLocalite("ANT"); a.setIdPrmp("PRMP001"); dossierRepository.save(a);
        Dossier b = dossier(151, "PRET_DISPATCH"); b.setIdLocalite("ANT"); b.setIdPrmp("PRMP001"); dossierRepository.save(b);
        Dossier c = dossier(152, "EXAMINE"); c.setIdLocalite("ANT"); c.setIdPrmp("PRMP001"); dossierRepository.save(c);
        Dossier di = dossier(148, "DISPATCHE"); di.setIdLocalite("ANT"); di.setIdPrmp("PRMP001"); dossierRepository.save(di);
        Dossier pv = dossier(149, "PV_SIGNE"); pv.setIdLocalite("ANT"); pv.setIdPrmp("PRMP001"); dossierRepository.save(pv);
        Dossier e = dossier(153, "SOUMIS"); e.setIdLocalite("ANT"); dossierRepository.save(e); // sans propriétaire
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==150)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==151)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==152)]", hasSize(1)))   // EXAMINE désormais retirable (§3.3)
                .andExpect(jsonPath("$[?(@.idDossier==148)]", hasSize(1)))   // DISPATCHE retirable
                .andExpect(jsonPath("$[?(@.idDossier==149)]", hasSize(0)))   // PV_SIGNE exclu (au-delà de la limite)
                .andExpect(jsonPath("$[?(@.idDossier==153)]", hasSize(0)));  // dossier d'autrui
    }

    @Test
    @DisplayName("Retirables — dossier BROUILLON de la PRMP → absent")
    void retirables_brouillon_exclu() throws Exception {
        Dossier d = dossier(154, "BROUILLON"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==154)]", hasSize(0)));
    }

    @Test
    @DisplayName("Retirables — dossier SOUMIS de la PRMP → présent")
    void retirables_soumis_inclus() throws Exception {
        Dossier d = dossier(155, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==155)]", hasSize(1)));
    }

    @Test
    @DisplayName("Retirables — dossier PRET_DISPATCH de la PRMP → présent")
    void retirables_pret_dispatch_inclus() throws Exception {
        Dossier d = dossier(156, "PRET_DISPATCH"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==156)]", hasSize(1)));
    }

    @Test
    @DisplayName("Retirables — dossier SOUMIS avec demande EN_ATTENTE → absent")
    void retirables_demande_en_attente_exclu() throws Exception {
        Dossier d = dossier(157, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        demandeRetraitRepository.save(demandeRetrait(0, 157, "PRMP001"));   // statut EN_ATTENTE
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==157)]", hasSize(0)));
    }

    @Test
    @DisplayName("Retirables — dossier SOUMIS avec demande REFUSEE → absent (pas de nouvelle demande)")
    void retirables_demande_refusee_exclu() throws Exception {
        Dossier d = dossier(159, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        DemandeRetrait dr = demandeRetrait(0, 159, "PRMP001");
        dr.setStatut("REFUSEE");
        demandeRetraitRepository.save(dr);
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==159)]", hasSize(0)));
    }

    @Test
    @DisplayName("Retirables — dossier SOUMIS d'une autre PRMP → absent")
    void retirables_autre_prmp_exclu() throws Exception {
        prmpRepository.save(prmp("PRMP009", "ANT"));
        Dossier d = dossier(158, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP009"); dossierRepository.save(d);
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==158)]", hasSize(0)));
    }

    @Test
    @DisplayName("À valider : CC voit les EN_ATTENTE de sa localité (ANT), pas TMS ; le Président voit les deux")
    void retrait_aValider_scopeLocalite() throws Exception {
        Dossier ant = dossier(160, "SOUMIS"); ant.setIdLocalite("ANT"); ant.setIdPrmp("PRMP001"); dossierRepository.save(ant);
        Dossier tms = dossier(161, "SOUMIS"); tms.setIdLocalite("TMS"); tms.setIdPrmp("PRMP001"); dossierRepository.save(tms);
        int drAnt = demandeRetraitRepository.save(demandeRetrait(0, 160, "PRMP001")).getIdDemandeRetrait();
        int drTms = demandeRetraitRepository.save(demandeRetrait(0, 161, "PRMP001")).getIdDemandeRetrait();

        mvc.perform(get("/api/demande-retraits/a-valider").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDemandeRetrait==" + drAnt + ")]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDemandeRetrait==" + drTms + ")]", hasSize(0)));
        mvc.perform(get("/api/demande-retraits/a-valider").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$[?(@.idDemandeRetrait==" + drAnt + ")]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDemandeRetrait==" + drTms + ")]", hasSize(1)));
    }

    @Test
    @DisplayName("Historique : une demande décidée (REFUSEE) y apparaît, et plus dans « à valider »")
    void retrait_historique() throws Exception {
        Dossier d = dossier(162, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 162, "PRMP001")).getIdDemandeRetrait();
        mvc.perform(post("/api/demande-retraits/" + drId + "/refuser").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"x\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/demande-retraits/historique").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.idDemandeRetrait==" + drId + ")]", hasSize(1)));
        mvc.perform(get("/api/demande-retraits/a-valider").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.idDemandeRetrait==" + drId + ")]", hasSize(0)));
    }

    /** POST multipart d'une demande de retrait pour la PRMP connectée : partie {@code data} (DTO JSON) + lettre PDF valide. */
    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder posterRetrait(String dataJson) {
        return multipart("/api/demande-retraits")
                .file(new MockMultipartFile("data", "", "application/json", dataJson.getBytes(StandardCharsets.UTF_8)))
                .file(lettreRetraitPdf())
                .header("Authorization", tokenPrmp);
    }

    private static MockMultipartFile lettreRetraitPdf() {
        return new MockMultipartFile("fichier", "lettre-retrait.pdf", "application/pdf",
                "%PDF-1.4 lettre de demande de retrait datee et signee".getBytes(StandardCharsets.US_ASCII));
    }
}
