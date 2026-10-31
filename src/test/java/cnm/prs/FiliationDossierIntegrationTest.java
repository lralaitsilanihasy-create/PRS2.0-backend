package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
