package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Marche;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ 2026-10-31 (demande front « filiation-dossier », §B1) — l'arbre d'un dossier : plan et sa mise à jour, dossier de mise en
 * concurrence né d'une ligne, dossier de marché du lot, acte de gestion du marché ; le même arbre depuis chaque point d'entrée ; un nœud
 * hors périmètre masqué.
 */
class FiliationDossierIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired private cnm.prs.repository.DossierMecRepository dmcRepository;
    @Autowired private cnm.prs.repository.AttributionRepository attributionRepository;
    @Autowired private cnm.prs.repository.ActeGestionRepository acteRepository;
    @Autowired private cnm.prs.repository.ContratVigueurRepository contratRepository;
    @Autowired private cnm.prs.repository.TacheDossierRepository tacheRepository;

    private Long idDmc;

    @BeforeEach
    void arbre() {
        for (String[] t : List.of(new String[] { "DDM", "Dossier de Marché" }, new String[] { "DGC", "Dossier de gestion contractuelle" })) {
            if (!typeDossierRepository.existsById(t[0])) {
                typeDossierRepository.save(new cnm.prs.entity.TypeDossier(t[0], t[1]));
            }
        }
        // Le plan 9500 et sa mise à jour 9505 ; la ligne 9501 du plan 9500.
        Dossier plan = dossierLoc(9500, "CLOTURE", "ANT", "PRMP001");
        plan.setIdTypeDossier("DDP");
        plan.setRefeDossier("00001/PPM-AGPM/CNM/2026");
        dossierRepository.save(plan);
        ppmRepository.save(ppm(9500, 9500, "PRMP001"));
        Dossier maj = dossierLoc(9505, "SOUMIS", "ANT", "PRMP001");
        maj.setIdTypeDossier("DDP");
        maj.setIdDossierParent(9500);
        maj.setRefeDossier("00003/PPM-AGPM/CNM/2026");
        dossierRepository.save(maj);
        Marche l = marche(9501, 9500, 9500);
        l.setDesignationMarche("articles de bureau");
        marcheRepository.save(l);
        // La procédure de la ligne, son dossier de mise en concurrence, le marché du lot 1, l'acte de gestion du marché.
        DossierMec dmc = new DossierMec();
        dmc.setIdDetail(9501);
        dmc.setIdTypeDmc(typeDmcRepository.findByCode("DAO").orElseThrow().getIdTypeDmc());
        dmc.setDateCreation(LocalDateTime.now());
        idDmc = dmcRepository.save(dmc).getIdDmc();
        Dossier mec = dossierLoc(9510, "CLOTURE", "ANT", "PRMP001");
        mec.setIdTypeDossier("DMC");
        mec.setIdSousType("DAOO-CC");
        mec.setIdDmc(idDmc);
        mec.setRefeDossier("00002/DAOO-CC/CNM/2026");
        dossierRepository.save(mec);
        Dossier ddm = dossierLoc(9520, "CLOTURE", "ANT", "PRMP001");
        ddm.setIdTypeDossier("DDM");
        ddm.setRefeDossier("00009/MAOO-CC/CNM/2026");
        dossierRepository.save(ddm);
        cnm.prs.entity.Attribution a = new cnm.prs.entity.Attribution();
        a.setIdDmc(idDmc);
        a.setLot(1);
        a.setEtat("NOTIFIE");
        a.setIdDossier(9520);
        attributionRepository.save(a);
        Dossier dgc = dossierLoc(9530, "BROUILLON", "ANT", "PRMP001");
        dgc.setIdTypeDossier("DGC");
        dossierRepository.save(dgc);
        cnm.prs.entity.ActeGestion acte = new cnm.prs.entity.ActeGestion();
        acte.setIdDossier(9530);
        acte.setIdDossierMarche(9520);
        acte.setSousType("AVN");
        acte.setCreeLe(LocalDateTime.now());
        acteRepository.save(acte);
    }

    @Test
    @DisplayName("Le même arbre depuis chaque point d'entrée : plan → mise à jour ; plan → mise en concurrence (ligne) → marché (lot) → "
            + "modification")
    void arbreComplet() throws Exception {
        for (int entree : List.of(9500, 9505, 9510, 9520, 9530)) {
            String corps = mvc.perform(get("/api/dossiers/" + entree + "/filiation").header("Authorization", tokenPresident))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.idDossierDemande").value(entree)).andReturn().getResponse()
                    .getContentAsString();
            assertThat(JsonPath.<Integer>read(corps, "$.racine.idDossier")).isEqualTo(9500);
            assertThat(JsonPath.<String>read(corps, "$.racine.lien")).isEqualTo("RACINE");
            assertThat(JsonPath.<List<String>>read(corps, "$.racine.enfants[*].lien")).containsExactly("VERSION_SUIVANTE", "MISE_EN_CONCURRENCE");
            assertThat(JsonPath.<List<Integer>>read(corps, "$.racine.enfants[*].idDossier")).containsExactly(9505, 9510);
            assertThat(JsonPath.<Integer>read(corps, "$.racine.enfants[1].ligne.idDetail")).isEqualTo(9501);
            assertThat(JsonPath.<String>read(corps, "$.racine.enfants[1].ligne.objet")).isEqualTo("articles de bureau");
            assertThat(JsonPath.<Integer>read(corps, "$.racine.enfants[1].enfants[0].idDossier")).isEqualTo(9520);
            assertThat(JsonPath.<String>read(corps, "$.racine.enfants[1].enfants[0].lien")).isEqualTo("MARCHE");
            assertThat(JsonPath.<Integer>read(corps, "$.racine.enfants[1].enfants[0].lot")).isEqualTo(1);
            assertThat(JsonPath.<Integer>read(corps, "$.racine.enfants[1].enfants[0].enfants[0].idDossier")).isEqualTo(9530);
            assertThat(JsonPath.<String>read(corps, "$.racine.enfants[1].enfants[0].enfants[0].lien")).isEqualTo("MODIFICATION");
        }
        mvc.perform(get("/api/dossiers/999999/filiation").header("Authorization", tokenPresident)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("⚠️ 2026-11-03 (« jalons-reels », B1) — les dates réelles des jalons d'un plan, déduites des faits : réception (106) et "
            + "signature du PV (109) du dossier de mise en concurrence, attribution (123, et l'ancien processus 4) et signature (125) du lot, "
            + "la source en clair ; rien sans fait ; 409 PAS_UN_PLAN sur un autre dossier")
    void jalonsReels() throws Exception {
        receptionRepository.save(reception(9511, 9510, "CTRCC1", true));
        dispatchRepository.save(dispatch(9511, 9511, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(9511, 9511, "CTRMEM"));
        seedPvSigne(9511, 9511);
        cnm.prs.entity.Attribution a = attributionRepository.findByIdDmcOrderByLotAsc(idDmc).get(0);
        a.setAttribueLe(LocalDateTime.of(2026, 8, 1, 9, 0));
        a.setDateSignature(java.time.LocalDate.of(2026, 8, 15));
        attributionRepository.save(a);

        String corps = mvc.perform(get("/api/dossiers/9500/jalons-reels").header("Authorization", tokenPresident)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(corps, "$[*].idDetail")).containsOnly(9501);
        assertThat(JsonPath.<List<Integer>>read(corps, "$[*].idCapm")).contains(106, 109, 123, 4, 125).doesNotContain(101, 104, 113);
        assertThat(JsonPath.<List<String>>read(corps, "$[?(@.idCapm==106)].dateReelle")).containsExactly("2026-06-02");
        assertThat(JsonPath.<List<String>>read(corps, "$[?(@.idCapm==106)].source")).singleElement().asString()
                .contains("Réception du dossier 00002/DAOO-CC/CNM/2026", "le 02/06/2026");
        assertThat(JsonPath.<List<String>>read(corps, "$[?(@.idCapm==109)].dateReelle")).containsExactly(java.time.LocalDate.now().toString());
        assertThat(JsonPath.<List<String>>read(corps, "$[?(@.idCapm==123)].source")).singleElement().asString().contains("(lot 1)");
        assertThat(JsonPath.<List<String>>read(corps, "$[?(@.idCapm==4)].dateReelle")).containsExactly("2026-08-01");
        assertThat(JsonPath.<List<String>>read(corps, "$[?(@.idCapm==125)].dateReelle")).containsExactly("2026-08-15");
        mvc.perform(get("/api/dossiers/9510/jalons-reels").header("Authorization", tokenPresident)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAS_UN_PLAN"));
    }

    @Test
    @DisplayName("⚠️ V163 (« chaine-par-ligne », B1, B2) — le nœud ligne porte idLigneOrigine ; un rapport justificatif et un marché de gré à "
            + "gré créés hors fiche et rattachés à une ligne entrent dans la filiation (RJ sous le plan, MGG sous le RJ) ; ligne inconnue, "
            + "hors des plans de la PRMP ou déjà rattachée à un dossier de la même famille → 400 idDetailOrigine")
    void dossiersHorsFicheRattachesALaLigne() throws Exception {
        for (String[] st : List.of(new String[] { "RJ", "DMC" }, new String[] { "MGG", "DDM" })) {
            if (!sousTypeDossierRepository.existsById(st[0])) {
                sousTypeDossierRepository.save(new cnm.prs.entity.SousTypeDossier(st[0], "Sous-type " + st[0], st[1]));
            }
        }
        Marche l = marche(9502, 9500, 9500);
        l.setDesignationMarche("entretien des groupes électrogènes");
        marcheRepository.save(l);
        String rj = mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"RJ\",\"idEntiteContract\":1,\"idDetailOrigine\":9502}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.idDetailOrigine").value(9502)).andReturn().getResponse().getContentAsString();
        int idRj = JsonPath.read(rj, "$.idDossier");
        String mgg = mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"MGG\",\"idEntiteContract\":1,\"idDetailOrigine\":9502}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idMgg = JsonPath.read(mgg, "$.idDossier");
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"RJ\",\"idEntiteContract\":1,\"idDetailOrigine\":9502}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idDetailOrigine"));
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"RJ\",\"idEntiteContract\":1,\"idDetailOrigine\":999999}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idDetailOrigine"));
        cnm.prs.entity.Prmp autrePrmp = prmp("PRMP002", null);
        autrePrmp.setCin("202022223333");
        autrePrmp.setEmailPrmp("prmp2@min.mg");
        prmpRepository.save(autrePrmp);
        Dossier autrePlan = dossierLoc(9600, "CLOTURE", "ANT", "PRMP002");
        autrePlan.setIdTypeDossier("DDP");
        dossierRepository.save(autrePlan);
        marcheRepository.save(marche(9601, 9600, 9500));
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"RJ\",\"idEntiteContract\":1,\"idDetailOrigine\":9601}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idDetailOrigine"));

        for (int entree : List.of(9500, idRj, idMgg)) {
            String corps = mvc.perform(get("/api/dossiers/" + entree + "/filiation").header("Authorization", tokenPrmp))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(JsonPath.<Integer>read(corps, "$.racine.idDossier")).isEqualTo(9500);
            assertThat(JsonPath.<List<Integer>>read(corps, "$.racine.enfants[?(@.lien=='MISE_EN_CONCURRENCE')].ligne.idLigneOrigine"))
                    .contains(9501, 9502);   // sans origine propre, la ligne est sa propre origine
            assertThat(JsonPath.<List<Integer>>read(corps, "$.racine.enfants[?(@.idDossier==" + idRj + ")].ligne.idDetail")).containsExactly(9502);
            assertThat(JsonPath.<List<Integer>>read(corps, "$.racine.enfants[?(@.idDossier==" + idRj + ")].enfants[*].idDossier"))
                    .containsExactly(idMgg);
            assertThat(JsonPath.<List<String>>read(corps, "$.racine.enfants[?(@.idDossier==" + idRj + ")].enfants[*].lien"))
                    .containsExactly("MARCHE");
        }
    }

    @Test
    @DisplayName("⚠️ §B2 — l'historique du dossier, trié par date : réception, dispatch, examen, PV (référence, avis), lettre de renvoi, "
            + "acte de gestion du marché ; la PRMP n'y voit ni dispatch, ni examen, ni PV en projet, ni lettre en brouillon, ni matricule")
    void historique() throws Exception {
        receptionRepository.save(reception(9510, 9510, "CTRCC1", true));
        dispatchRepository.save(dispatch(9510, 9510, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9510, 9510, "CTRMEM"));
        seedPvSigne(9510, 9510);
        cnm.prs.entity.PvExamen pv = pvExamenRepository.findById(9510).orElseThrow();
        pv.setRefePv("00002/DAOO-CC/CNM/PV/2026");
        pvExamenRepository.save(pv);
        for (String statut : List.of("SIGNE", "BROUILLON")) {
            cnm.prs.entity.LettreRenvoi l = new cnm.prs.entity.LettreRenvoi();
            l.setIdExamen(9510);
            l.setIdDossier(9510);
            l.setObjetLettre("Renvoi");
            l.setStatut(statut);
            l.setDateLettre(java.time.LocalDate.now());
            l.setRefLettre("SIGNE".equals(statut) ? "00002/DAOO-CC/CNM/LR/2026" : null);
            lettreRenvoiRepository.save(l);
        }
        String president = mvc.perform(get("/api/dossiers/9510/historique").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(president, "$[*].type"))
                .contains("RECEPTION", "DISPATCH", "EXAMEN", "PV", "LETTRE_RENVOI");
        assertThat(JsonPath.<List<String>>read(president, "$[?(@.type=='PV')].reference")).containsExactly("00002/DAOO-CC/CNM/PV/2026");
        assertThat(JsonPath.<List<String>>read(president, "$[?(@.type=='PV')].statut")).containsExactly("DEFINITIF");
        assertThat(JsonPath.<List<String>>read(president, "$[?(@.type=='PV')].avis")).containsExactly("FAV");
        assertThat(JsonPath.<List<Integer>>read(president, "$[?(@.type=='PV')].id")).containsExactly(9510);
        assertThat(JsonPath.<List<Object>>read(president, "$[?(@.type=='LETTRE_RENVOI')].id")).hasSize(2);
        String marche = mvc.perform(get("/api/dossiers/9520/historique").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(marche, "$[?(@.type=='ACTE_GESTION')].id")).containsExactly(9530);
        // Second temps : la procédure — l'attribution du lot, le contrat en vigueur, la publication de l'AGPM du plan.
        cnm.prs.entity.ContratVigueur c = new cnm.prs.entity.ContratVigueur();
        c.setIdDmc(idDmc);
        c.setLot(1);
        c.setTypeMarche("CONTRAT_CADRE");
        c.setDateEffet(java.time.LocalDate.now().minusMonths(1));
        c.setPeriodeMois(12);
        c.setDateFin(java.time.LocalDate.now().plusMonths(11));
        c.setCreeLe(LocalDateTime.now());
        contratRepository.save(c);
        cnm.prs.entity.PublicationAgpm agpm = new cnm.prs.entity.PublicationAgpm();
        agpm.setIdDossier(9500);
        agpm.setJournal("Midi Madagasikara");
        agpm.setDateParution(java.time.LocalDate.now().minusDays(60));
        agpm.setReferenceAgpm("AGPM-2026");
        agpm.setNomFichier("agpm.pdf");
        agpm.setContenu("%PDF".getBytes());
        agpm.setTaille(4L);
        agpm.setDeposeLe(LocalDateTime.now());
        publicationAgpmRepository.save(agpm);
        marche = mvc.perform(get("/api/dossiers/9520/historique").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(marche, "$[*].type")).contains("ATTRIBUTION", "CONTRAT", "PUBLICATION_AGPM");
        assertThat(JsonPath.<List<Integer>>read(marche, "$[?(@.type=='ATTRIBUTION')].lot")).containsExactly(1);
        assertThat(JsonPath.<List<String>>read(marche, "$[?(@.type=='PUBLICATION_AGPM')].reference")).containsExactly("AGPM-2026");
        String plan = mvc.perform(get("/api/dossiers/9500/historique").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(plan, "$[*].type")).containsExactly("PUBLICATION_AGPM");
        String prmp = mvc.perform(get("/api/dossiers/9510/historique").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(prmp, "$[*].type")).doesNotContain("DISPATCH", "EXAMEN").contains("RECEPTION", "PV");
        assertThat(JsonPath.<List<String>>read(prmp, "$[?(@.type=='LETTRE_RENVOI')].reference")).containsExactly("00002/DAOO-CC/CNM/LR/2026");
        assertThat(JsonPath.<List<Object>>read(prmp, "$[*].acteur")).containsOnlyNulls();
        mvc.perform(get("/api/dossiers/9510/historique").header("Authorization",
                bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("⚠️ 2026-11-01 (« essentiel-dossier ») — B1 : l'avis du dernier PV, l'attributaire du lot, le contrat, les suites ; "
            + "un plan n'a que l'avis ; B2 : un dispatch sans date prend celle du passage DISPATCH du chronométrage")
    void essentielEtDateDuDispatch() throws Exception {
        receptionRepository.save(reception(9520, 9520, "CTRCC1", true));
        dispatchRepository.save(dispatch(9520, 9520, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9520, 9520, "CTRMEM"));
        seedPvSigne(9520, 9520);
        cnm.prs.entity.PvExamen pv = pvExamenRepository.findById(9520).orElseThrow();
        pv.setRefePv("00009/MAOO-CC/CNM/PV/2026");
        pvExamenRepository.save(pv);
        cnm.prs.entity.Attribution a = attributionRepository.findFirstByIdDossier(9520).orElseThrow();
        a.setAttribueLe(LocalDateTime.now().minusDays(20));
        a.setSigneLe(LocalDateTime.of(2026, 9, 14, 10, 0));
        a.setMontant(new java.math.BigDecimal("11279883606"));
        attributionRepository.save(a);
        cnm.prs.entity.ContratVigueur c = new cnm.prs.entity.ContratVigueur();
        c.setIdDmc(idDmc);
        c.setLot(1);
        c.setTypeMarche("CONTRAT_CADRE");
        c.setDateEffet(java.time.LocalDate.of(2026, 9, 16));
        c.setPeriodeMois(12);
        c.setDateFin(java.time.LocalDate.of(2027, 9, 16));
        c.setCreeLe(LocalDateTime.now());
        contratRepository.save(c);

        mvc.perform(get("/api/dossiers/9520/essentiel").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.avis.code").value("FAV")).andExpect(jsonPath("$.avis.definitif").value(true))
                .andExpect(jsonPath("$.avis.refePv").value("00009/MAOO-CC/CNM/PV/2026")).andExpect(jsonPath("$.avis.idPv").value(9520))
                .andExpect(jsonPath("$.attributions[0].lot").value(1)).andExpect(jsonPath("$.attributions[0].montant").value(11279883606L))
                .andExpect(jsonPath("$.attributions[0].idDossierMarche").value(9520))
                .andExpect(jsonPath("$.contrat.forme").value("CONTRAT_CADRE")).andExpect(jsonPath("$.contrat.signeLe").value("2026-09-14"))
                .andExpect(jsonPath("$.contrat.finLe").value("2027-09-16")).andExpect(jsonPath("$.suites.actesGestion").value(1))
                .andExpect(jsonPath("$.suites.bonsCommande").value(0));
        // ⚠️ 2026-11-01 (« contrats-vigueur-liste », §B1) — le contrat porte la référence de son dossier de marché.
        mvc.perform(get("/api/contrats-en-vigueur").header("Authorization", tokenPresident)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDmc==" + idDmc + ")].idDossierMarche").value(org.hamcrest.Matchers.hasItem(9520)))
                .andExpect(jsonPath("$[?(@.idDmc==" + idDmc + ")].refeDossierMarche").value(org.hamcrest.Matchers.hasItem("00009/MAOO-CC/CNM/2026")))
                .andExpect(jsonPath("$[?(@.idDmc==" + idDmc + ")].bonsEmis").value(org.hamcrest.Matchers.hasItem(0)));
        // Le dossier de mise en concurrence voit l'attribution de chaque lot ; le plan, l'avis seul (ici aucun PV : tout nul).
        mvc.perform(get("/api/dossiers/9510/essentiel").header("Authorization", tokenPresident)).andExpect(status().isOk())
                .andExpect(jsonPath("$.attributions[0].idDossierMarche").value(9520));
        mvc.perform(get("/api/dossiers/9500/essentiel").header("Authorization", tokenPresident)).andExpect(status().isOk())
                .andExpect(jsonPath("$.avis").doesNotExist()).andExpect(jsonPath("$.attributions").doesNotExist())
                .andExpect(jsonPath("$.contrat").doesNotExist());

        // B2 — un dispatch sans date ni affectation datée : la fin du passage DISPATCH du chronométrage.
        cnm.prs.entity.Dispatch d = dispatchRepository.findById(9520).orElseThrow();
        d.setDateDispatch(null);
        d.setDateCtrlAssigne(null);
        dispatchRepository.save(d);
        cnm.prs.entity.TacheDossier t = new cnm.prs.entity.TacheDossier();
        t.setIdTache(tacheRepository.nextId());
        t.setIdDossier(9520);
        t.setEtape("DISPATCH");
        t.setOccurrence(1);
        t.setImActeur("CTRPRE");
        t.setDateFin(LocalDateTime.of(2026, 8, 25, 9, 30));
        tacheRepository.save(t);
        String hist = mvc.perform(get("/api/dossiers/9520/historique").header("Authorization", tokenPresident)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(hist, "$[?(@.type=='DISPATCH')].date")).containsExactly("2026-08-25T09:30:00");
    }

    @Test
    @DisplayName("Périmètre : le dossier demandé hors périmètre → 403 ; un nœud hors périmètre de l'arbre est masqué (lien et enfants seuls)")
    void perimetre() throws Exception {
        String autre = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT");
        mvc.perform(get("/api/dossiers/9500/filiation").header("Authorization", autre)).andExpect(status().isForbidden());
        // La mise à jour du plan appartient à une autre PRMP : PRMP001 la voit masquée, ses autres nœuds en clair.
        if (!prmpRepository.existsById("PRMP002")) {
            prmpRepository.save(prmp("PRMP002", "ANT"));
        }
        Dossier maj = dossierRepository.findById(9505).orElseThrow();
        maj.setIdPrmp("PRMP002");
        dossierRepository.save(maj);
        String corps = mvc.perform(get("/api/dossiers/9520/filiation").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(corps, "$.racine.enfants[0].masque")).isTrue();
        assertThat(JsonPath.<Object>read(corps, "$.racine.enfants[0].idDossier")).isNull();
        assertThat(JsonPath.<Object>read(corps, "$.racine.enfants[0].refeDossier")).isNull();
        assertThat(JsonPath.<String>read(corps, "$.racine.enfants[0].lien")).isEqualTo("VERSION_SUIVANTE");
        assertThat(JsonPath.<Boolean>read(corps, "$.racine.enfants[1].masque")).isFalse();
        assertThat(JsonPath.<Integer>read(corps, "$.racine.enfants[1].idDossier")).isEqualTo(9510);
    }
}
