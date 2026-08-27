package cnm.prs;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Examen;
import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.Nature;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * Examen d'un dossier : grille de controle par sous-type et par ligne de marche, observations,
 * completude et avis suggere a la soumission, verrou apres signature, reservation du travail au
 * Membre attributaire.
 */
class ExamenIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Statut examen (⚠️ règle déplacée 2026-08-01) : la création laisse le dossier DISPATCHE (brouillon), "
            + "c'est la SOUMISSION de l'examen qui le passe EXAMINE")
    void statut_examenAvanceVersExamine() throws Exception {
        dossierRepository.save(dossier(30, "PRET_DISPATCH"));
        receptionRepository.save(reception(60, 30, "CTRSEC", true)); // ANT
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":80,\"idReception\":60,\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());
        // Avant examen : DISPATCHE (à examiner).
        mvc.perform(get("/api/dossiers/30").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("DISPATCHE"));

        // Le Membre crée l'examen (brouillon de progression) → le dossier RESTE DISPATCHE.
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":80,\"idDispatch\":80,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/30").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("DISPATCHE"));

        // La soumission de l'examen (projet de PV) fait passer le dossier EXAMINE.
        mvc.perform(post("/api/examens/80/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/30").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("EXAMINE"));

        // Exclusivité : présent en ?statut=EXAMINE, absent de ?statut=DISPATCHE.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPresident).param("statut", "EXAMINE"))
                .andExpect(jsonPath("$[?(@.idDossier==30)]", hasSize(1)));
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPresident).param("statut", "DISPATCHE"))
                .andExpect(jsonPath("$[?(@.idDossier==30)]", hasSize(0)));
    }

    @Test
    @DisplayName("Verrou examen : modifiable tant que EXAMINE, verrouillé (409) dès la signature du PV")
    void verrou_examenJusquaSignature() throws Exception {
        // Dossier 1 = EXAMINE (seed) : l'examen 1 est modifiable.
        mvc.perform(put("/api/examens/1").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":1,\"idDispatch\":1,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isOk());

        // Signer le PV (FAV) de l'examen 1 → dossier auto-clôturé (CLOTURE), examen définitif.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":91,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/91/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/91/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/91/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/91/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\"}"))
                .andExpect(status().isOk());

        // Examen verrouillé (dossier ≠ EXAMINE) : update de l'examen et écriture d'un détail → 409.
        mvc.perform(put("/api/examens/1").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":1,\"idDispatch\":1,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":500,\"idExamen\":1,\"idPtControle\":1,\"conforme\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Autorisation examen : réservé au Membre attributaire ; un autre Membre → 403 ; CC par délégation → OK")
    void autorisation_examenReserveeAttributaire() throws Exception {
        // Dossier dispatché au Membre CTRMEM.
        dossierRepository.save(dossier(40, "PRET_DISPATCH"));
        receptionRepository.save(reception(70, 40, "CTRSEC", true)); // ANT
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":90,\"idReception\":70,\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());

        // Un AUTRE Membre d'ANT (non attributaire) → 403.
        String tokenAutreMembre = bearer("CTRMEM2", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM2", "ANT");
        mvc.perform(post("/api/examens").header("Authorization", tokenAutreMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":90,\"idDispatch\":90,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isForbidden());

        // Le Membre attributaire (CTRMEM) → 201.
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":90,\"idDispatch\":90,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isCreated());

        // Délégation : le CC peut instruire l'examen à la place d'un Membre de sa localité → 201.
        dossierRepository.save(dossier(41, "PRET_DISPATCH"));
        receptionRepository.save(reception(71, 41, "CTRSEC", true));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":91,\"idReception\":71,\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/examens").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":91,\"idDispatch\":91,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Listes Membre : à-examiner (DISPATCHE assignés) et examinés (paginé) scopés à l'attributaire")
    void listes_membreScopeesAttributaire() throws Exception {
        controleurRepository.save(controleur("CTRMEM2", 5, "ANT")); // 2e Membre d'ANT
        // Dossier 50 DISPATCHE assigné à CTRMEM ; dossier 51 DISPATCHE assigné à CTRMEM2.
        dossierRepository.save(dossier(50, "DISPATCHE"));
        receptionRepository.save(reception(80, 50, "CTRSEC", true));
        dispatchRepository.save(dispatch(95, 80, "CTRCC1", "CTRMEM"));
        dossierRepository.save(dossier(51, "DISPATCHE"));
        receptionRepository.save(reception(81, 51, "CTRSEC", true));
        dispatchRepository.save(dispatch(96, 81, "CTRCC1", "CTRMEM2"));

        // à-examiner de CTRMEM : son dossier 50, pas celui de l'autre Membre (51).
        mvc.perform(get("/api/dossiers/a-examiner").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==50)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==51)]", hasSize(0)));

        // CTRMEM examine son dossier 50 puis SOUMET l'examen (⚠️ 2026-08-01 : la transition
        // DISPATCHE → EXAMINE se fait à la soumission, plus à la création).
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":95,\"idDispatch\":95,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/examens/95/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());

        // Exclusivité : 50 quitte à-examiner et entre dans examinés (paginé → $.content).
        mvc.perform(get("/api/dossiers/a-examiner").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idDossier==50)]", hasSize(0)));
        mvc.perform(get("/api/dossiers/examines").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.idDossier==50)]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.idDossier==51)]", hasSize(0)));
    }

    @Test
    @DisplayName("Observation-controle — création d'une ligne (Membre) → 201")
    void observation_creation_ok() throws Exception {
        PointsCtrl pc = new PointsCtrl();
        pc.setIdPointCtrl(1); pc.setLibelPointCtrl("Montant"); pc.setObligatoire(true); pc.setIdTypeDossier("DDP");
        pointsCtrlRepository.save(pc);
        ExamenDetail d = new ExamenDetail();
        d.setIdDetailExamen(520); d.setIdExamen(1); d.setIdPtControle(1); d.setConforme(false);
        examenDetailRepository.save(d);
        mvc.perform(post("/api/observation-controles").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":520,\"auLieuDe\":\"500000\",\"lire\":\"5000000\",\"ordre\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idObservation").exists())
                .andExpect(jsonPath("$.idDetail").value(520));
    }

    @Test
    @DisplayName("Examen-détail — non conforme sans lignes d'observation → 400")
    void observation_non_conforme_sans_lignes_400() throws Exception {
        // examen 1 = EXAMINE (seed, modifiable) ; conforme=false + observations vide → 400 (avant save).
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":510,\"idExamen\":1,\"idPtControle\":1,\"conforme\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='observations')].message",
                        hasItem("Au moins une ligne d'observation est obligatoire si le point est non conforme.")));
    }

    @Test
    @DisplayName("Examen-détail — conforme sans lignes d'observation → 200")
    void observation_conforme_sans_lignes_ok() throws Exception {
        PointsCtrl pc = new PointsCtrl();
        pc.setIdPointCtrl(1); pc.setLibelPointCtrl("Montant"); pc.setObligatoire(true); pc.setIdTypeDossier("DDP");
        pointsCtrlRepository.save(pc);
        ExamenDetail d = new ExamenDetail();
        d.setIdDetailExamen(511); d.setIdExamen(1); d.setIdPtControle(1); d.setConforme(true);
        examenDetailRepository.save(d);
        mvc.perform(put("/api/examen-details/511").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":511,\"idExamen\":1,\"idPtControle\":1,\"conforme\":true,\"observations\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conforme").value(true));
    }

    @Test
    @DisplayName("Soumission examen → Projet de PV créé (toujours un PV)")
    void examen_soumettre_pv_ok() throws Exception {
        mvc.perform(post("/api/examens/1/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idExamen").value(1))
                .andExpect(jsonPath("$.statutPv").value("BROUILLON"));
    }

    @Test
    @DisplayName("Soumission examen sans secrétaire de séance → 201 (⚠️ 2026-08-01 : optionnel, posé à la clôture de navette)")
    void soumission_examen_sans_secretaire_400() throws Exception {
        mvc.perform(post("/api/examens/1/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idAvis\":\"FAV\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutPv").value("BROUILLON"));
    }

    @Test
    @DisplayName("Soumission examen — secrétaire non vérificateur (autre profil/localité) → 400")
    void soumission_examen_secretaire_invalide_400() throws Exception {
        // CTRMEM est un MEMBRE (pas un vérificateur) → secrétaire de séance invalide.
        mvc.perform(post("/api/examens/1/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRMEM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idSecretaireSeance"));
    }

    @Test
    @DisplayName("Soumission examen — secrétaire vérificateur valide → 201, PV avec secrétaire de séance")
    void soumission_examen_secretaire_ok() throws Exception {
        mvc.perform(post("/api/examens/1/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutPv").value("BROUILLON"))
                .andExpect(jsonPath("$.idSecretaireSeance").value("CTRVER"))
                .andExpect(jsonPath("$.nomSecretaireSeance").value("Prenoms NomCTRVER"));
    }

    @Test
    @DisplayName("Grille d'examen par sous-type : PPM = points communs seuls ; PPM-AGPM = communs + spécifique ; gardes 400")
    void grilleExamen_parSousType() throws Exception {
        // 2 points COMMUNS famille DDP (idSousType null) + 1 point SPÉCIFIQUE au sous-type PPM-AGPM.
        PointsCtrl c1 = new PointsCtrl();
        c1.setIdPointCtrl(801); c1.setLibelPointCtrl("Montants cohérents"); c1.setObligatoire(true);
        c1.setIdTypeDossier("DDP"); c1.setOrdrePointCtrl(1);
        pointsCtrlRepository.save(c1);
        PointsCtrl c2 = new PointsCtrl();
        c2.setIdPointCtrl(802); c2.setLibelPointCtrl("Signataire habilité"); c2.setObligatoire(true);
        c2.setIdTypeDossier("DDP"); c2.setOrdrePointCtrl(2);
        pointsCtrlRepository.save(c2);
        PointsCtrl s1 = new PointsCtrl();
        s1.setIdPointCtrl(803); s1.setLibelPointCtrl("AGPM joint et conforme"); s1.setObligatoire(true);
        s1.setIdTypeDossier("DDP"); s1.setIdSousType("PPM-AGPM"); s1.setOrdrePointCtrl(3);
        pointsCtrlRepository.save(s1);

        // Grille effective d'un PPM : les 2 communs, PAS le point AGPM.
        mvc.perform(get("/api/points-ctrls?sousType=PPM").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.idPointCtrl==803)]", hasSize(0)));
        // Grille effective d'un PPM-AGPM : communs + spécifique (3 points) — grille PPM ≠ PPM-AGPM.
        mvc.perform(get("/api/points-ctrls?sousType=PPM-AGPM").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.idPointCtrl==803)]", hasSize(1)));
        // Filtre famille (écran admin) : tous les points DDP, spécifiques compris.
        mvc.perform(get("/api/points-ctrls?typeDossier=DDP").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
        // Gardes : sous-type inconnu → 400 ; sous-type hors famille → 400.
        mvc.perform(get("/api/points-ctrls?sousType=XXX").header("Authorization", tokenMembre))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/points-ctrls?typeDossier=DMC&sousType=PPM-AGPM").header("Authorization", tokenMembre))
                .andExpect(status().isBadRequest());
        // Admin : création d'un point ciblant un sous-type d'une AUTRE famille → 400 (cohérence dropdown).
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPointCtrl\":804,\"libelPointCtrl\":\"x\",\"obligatoire\":true,"
                        + "\"idTypeDossier\":\"DDP\",\"idSousType\":\"DAO\"}"))
                .andExpect(status().isBadRequest());
        // Admin : création cohérente (sous-type de la même famille) → 201 avec idSousType exposé.
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPointCtrl\":805,\"libelPointCtrl\":\"Controle DAOR\",\"obligatoire\":false,"
                        + "\"idTypeDossier\":\"DMC\",\"idSousType\":\"DAOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idSousType").value("DAOR"));
    }

    @Test
    @DisplayName("Grille — la portée (LIGNE/DOSSIER) est exposée dans le DTO ; défaut LIGNE ; création avec portée")
    void grille_exposePortee() throws Exception {
        PointsCtrl ligne = new PointsCtrl();
        ligne.setIdPointCtrl(810); ligne.setLibelPointCtrl("Objet"); ligne.setObligatoire(true);
        ligne.setIdTypeDossier("DDP"); ligne.setOrdrePointCtrl(1);   // portee non fixée → défaut LIGNE
        pointsCtrlRepository.save(ligne);
        PointsCtrl dossier = new PointsCtrl();
        dossier.setIdPointCtrl(811); dossier.setLibelPointCtrl("fractionnement illicite"); dossier.setObligatoire(true);
        dossier.setIdTypeDossier("DDP"); dossier.setOrdrePointCtrl(2);
        dossier.setPortee(cnm.prs.enums.PorteePointCtrl.DOSSIER);
        pointsCtrlRepository.save(dossier);

        mvc.perform(get("/api/points-ctrls?sousType=PPM").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPointCtrl==810)].portee", hasItem("LIGNE")))
                .andExpect(jsonPath("$[?(@.idPointCtrl==811)].portee", hasItem("DOSSIER")));

        // Création admin avec portée DOSSIER → exposée ; code inconnu → 400.
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPointCtrl\":812,\"libelPointCtrl\":\"Coherence globale\",\"obligatoire\":true,"
                        + "\"idTypeDossier\":\"DDP\",\"portee\":\"DOSSIER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.portee").value("DOSSIER"));
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPointCtrl\":813,\"libelPointCtrl\":\"x\",\"obligatoire\":true,"
                        + "\"idTypeDossier\":\"DDP\",\"portee\":\"MARCHE\"}"))
                .andExpect(status().isBadRequest());
        // Création sans portée → défaut LIGNE en sortie.
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPointCtrl\":814,\"libelPointCtrl\":\"Nature\",\"obligatoire\":true,"
                        + "\"idTypeDossier\":\"DDP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.portee").value("LIGNE"));
    }

    /** Circuit DDP/PPM EXAMINE (dossier + 2 marchés + PPM + réception/dispatch/examen ANT) pour l'examen par ligne. */
    private void seedCircuitExamenParLigne() {
        Dossier d = dossier(6000, "EXAMINE");
        d.setIdTypeDossier("DDP"); d.setIdSousType("PPM"); d.setIdLocalite("ANT");
        dossierRepository.save(d);
        ppmRepository.save(ppm(6000, 6000, "PRMP001"));
        marcheRepository.save(marche(60001, 6000, 6000));
        marcheRepository.save(marche(60002, 6000, 6000));
        receptionRepository.save(reception(6000, 6000, "CTRSEC", true));   // localité ANT
        dispatchRepository.save(dispatch(6000, 6000, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(6000, 6000, "CTRMEM"));
        // Grille DDP : 2 points LIGNE (901, 902) + 1 point DOSSIER (903 = fractionnement).
        pointLigne(901, "Forme", 1);
        pointLigne(902, "Mode de passation", 2);
        PointsCtrl frac = new PointsCtrl();
        frac.setIdPointCtrl(903); frac.setLibelPointCtrl("fractionnement illicite"); frac.setObligatoire(true);
        frac.setIdTypeDossier("DDP"); frac.setOrdrePointCtrl(3);
        frac.setPortee(cnm.prs.enums.PorteePointCtrl.DOSSIER);
        pointsCtrlRepository.save(frac);
    }

    private void pointLigne(int id, String libelle, int ordre) {
        PointsCtrl p = new PointsCtrl();
        p.setIdPointCtrl(id); p.setLibelPointCtrl(libelle); p.setObligatoire(true);
        p.setIdTypeDossier("DDP"); p.setOrdrePointCtrl(ordre);   // portee défaut LIGNE
        pointsCtrlRepository.save(p);
    }

    @Test
    @DisplayName("Examen par ligne — résultat porté par marché + unicité (idExamen,idDetail,idPtControle) + cohérence portée/idDetail")
    void examen_resultatParLigne_etUnicite() throws Exception {
        seedCircuitExamenParLigne();

        // Point LIGNE (901) évalué sur le marché 60001 → 201, idDetail renvoyé.
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":70001,\"idExamen\":6000,\"idDetail\":60001,\"idPtControle\":901,\"conforme\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idDetail").value(60001));
        // Doublon exact du triplet → 400 idPtControle.
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":70002,\"idExamen\":6000,\"idDetail\":60001,\"idPtControle\":901,\"conforme\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idPtControle"));
        // Même point, AUTRE marché (60002) → 201 (triplet distinct).
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":70003,\"idExamen\":6000,\"idDetail\":60002,\"idPtControle\":901,\"conforme\":true}"))
                .andExpect(status().isCreated());
        // Point DOSSIER (903) AVEC idDetail → 400 idDetail (s'évalue une seule fois, sans ligne).
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":70004,\"idExamen\":6000,\"idDetail\":60001,\"idPtControle\":903,\"conforme\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idDetail"));
        // idDetail hors du dossier → 400 idDetail.
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":70005,\"idExamen\":6000,\"idDetail\":99999,\"idPtControle\":901,\"conforme\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idDetail"));
        // Point DOSSIER (903) sans idDetail → 201, idDetail null.
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":70006,\"idExamen\":6000,\"idPtControle\":903,\"conforme\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idDetail").doesNotExist());
    }

    @Test
    @DisplayName("Examen par ligne — complétude à la soumission (400 tant que toutes les lignes ne sont pas traitées) + avis suggéré")
    void examen_completude_soumission_etAvisSuggere() throws Exception {
        seedCircuitExamenParLigne();

        // Aucun détail → avisSuggere null.
        mvc.perform(get("/api/examens/6000").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.avisSuggere").doesNotExist());
        // Soumission alors que la grille n'est pas remplie → 400 ciblé « grille ».
        mvc.perform(post("/api/examens/6000/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("grille"));

        // Remplir : 2 points LIGNE × 2 marchés (4) + 1 point DOSSIER (1) = 5 évaluations.
        detailLigne(70101, 901, 60001, true, null);
        detailLigne(70102, 901, 60002, true, null);
        detailLigne(70103, 902, 60001, true, null);
        detailLigne(70104, 902, 60002, true, null);
        // Point DOSSIER non conforme (avec observation) → avis suggéré DEFAVORABLE.
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":70105,\"idExamen\":6000,\"idPtControle\":903,\"conforme\":false,"
                        + "\"observations\":[{\"auLieuDe\":\"3 marchés\",\"lire\":\"1 marché\",\"ordre\":1}]}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/examens/6000").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.avisSuggere").value("DEF"));

        // Grille complète → soumission 201 (Projet de PV créé).
        mvc.perform(post("/api/examens/6000/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"DEF\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idExamen").value(6000));
    }

    @Test
    @DisplayName("Examen par ligne — avis suggéré FAVORABLE quand tous les points sont conformes")
    void examen_avisSuggere_favorable() throws Exception {
        seedCircuitExamenParLigne();
        detailLigne(70201, 901, 60001, true, null);
        mvc.perform(get("/api/examens/6000").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.avisSuggere").value("FAV"));
    }

    private void detailLigne(int idDetailExamen, int idPt, int idMarche, boolean conforme, String obs) throws Exception {
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":" + idDetailExamen + ",\"idExamen\":6000,\"idDetail\":" + idMarche
                        + ",\"idPtControle\":" + idPt + ",\"conforme\":" + conforme + "}"))
                .andExpect(status().isCreated());
    }
}
