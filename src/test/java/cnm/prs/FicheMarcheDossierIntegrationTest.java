package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDmc;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.ChampFicheMarcheRepository;
import cnm.prs.repository.FicheMarcheRepository;

/**
 * ⚠️ <strong>Fiche marché ↔ dossier soumis, lot 1b</strong> (demande front du 2026-09-23) — les sept cas de la recette :
 * migration, la fiche produit le dossier (B2), lectures croisées, rattachement de secours et retrait (B3), fiches
 * rattachables, ordre des gardes (périmètre avant 409, vacance), lecture par le contrôleur de la localité (B4).
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, entité 1, CLOTURE, PV signé FAV) avec les lignes 9901, 9906 et 9907 en appel
 * d'offres ouvert (mode 92 → DAO) ; fiche A validée sur 9901, fiche B validée sur 9906, fiche C en brouillon sur 9907.
 * Dossiers {@code DAO} créés à la main : 9950 et 9951 (brouillons, PRMP001).</p>
 */
class FicheMarcheDossierIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheRepository champRepository;
    @Autowired private FicheMarcheRepository ficheRepository;

    private String tokenUgpm;
    private String tokenPrmp2;
    private String tokenPrmp3;
    private Long ficheA;
    private Long ficheB;
    private Long ficheC;

    @BeforeEach
    void jeu() throws Exception {
        TypeDmc dao = typeDmcRepository.findByCode("DAO").orElseThrow();
        ModePassation m92 = new ModePassation(92, "Appel d'offres ouvert", null, null, null, null);
        m92.setIdTypeDmc(dao.getIdTypeDmc());
        modePassationRepository.save(m92);

        planSigne(9900, "PRMP001");
        for (int idDetail : new int[] {9901, 9906, 9907}) {
            Marche l = marcheDao(idDetail, 9900, 9900);
            l.setIdMode(92);
            l.setDesignationMarche("Marché " + idDetail);
            marcheRepository.save(l);
        }

        ugpmRepository.save(ugpm("UGPM001", "PRMP001", "RAKOTO", "Hery"));
        tokenUgpm = bearer("ugpm.hery", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", "ANT");
        prmpRepository.save(prmp("PRMP002", "TMS"));
        tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "TMS");
        Prmp sansMandat = prmp("PRMP003", "ANT");
        sansMandat.setDateNomin(LocalDate.of(2019, 1, 15));   // mandat implicite expiré depuis 2022
        prmpRepository.save(sansMandat);
        tokenPrmp3 = bearer("PRMP003", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP003", "ANT");

        ChampFicheMarche c = new ChampFicheMarche();
        c.setCode("B02-AU-01");
        c.setCodeRubrique("B02-AU");
        c.setRang(1);
        c.setLibelle("Autorité contractante (précisions)");
        c.setType("TEXTE");
        c.setSource("SAISIE");
        c.setDocumentMaitre("DPAO");
        c.setTypesMarche("QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE");
        c.setObligatoire(false);
        c.setActif(true);
        champRepository.save(c);

        ficheA = ficheValidee(9901);
        ficheB = ficheValidee(9906);
        ficheC = creerDmc(9907);
        cadrage(ficheC);

        dossierRepository.save(dossierDao(9950, "BROUILLON", "DAO", "PRMP001"));
        dossierRepository.save(dossierDao(9951, "BROUILLON", "DAO", "PRMP001"));
    }

    // ------------------------------------------------------------------ 1. migration

    @Test
    @DisplayName("1 — V36 : ID_DMC nullable et unique sur t_dossier ; un dossier existant se lit comme avant (idDmc nul, "
            + "ficheMarche absent) ; le CHECK refuse une liaison hors sous-type DAO")
    void migration() throws Exception {
        assertThat(jdbcTemplate.queryForObject("select is_nullable from information_schema.columns "
                + "where table_name = 't_dossier' and column_name = 'ID_DMC'", String.class)).isEqualTo("YES");
        assertThat(jdbcTemplate.queryForObject("select count(*) from pg_constraint where conname = 'uq_dossier_dmc' "
                + "and contype = 'u'", Integer.class)).isEqualTo(1);

        mvc.perform(get("/api/dossiers/9900").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDmc").doesNotExist())
                .andExpect(jsonPath("$.ficheMarche").doesNotExist());
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());

        dossierRepository.save(dossierDao(9952, "BROUILLON", "DAOR", "PRMP001"));
        dossierRepository.flush();
        assertThatThrownBy(() -> jdbcTemplate.update("update t_dossier set \"ID_DMC\" = ? where \"ID_DOSSIER\" = 9952", ficheB))
                .hasMessageContaining("ck_dossier_dmc_dao");
    }

    // ------------------------------------------------------------------ 2. la fiche produit le dossier

    @Test
    @DisplayName("2 — Fiche en brouillon → 409 FICHE_NON_VALIDEE ; validée → 201, dossier DMC/DAO en BROUILLON, entité et "
            + "localité de la ligne du PPM, journal DOSSIER_CREE_DEPUIS_FICHE ; second appel → 409 DOSSIER_EXISTANT + idDossier")
    void laFicheProduitLeDossier() throws Exception {
        mvc.perform(post("/api/fiches-marche/" + ficheC + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_NON_VALIDEE"));

        String corps = mvc.perform(post("/api/fiches-marche/" + ficheA + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.idTypeDossier").value("DMC"))
                .andExpect(jsonPath("$.idSousType").value("DAO"))
                .andExpect(jsonPath("$.idEntiteContract").value(1))
                .andExpect(jsonPath("$.idLocalite").value("ANT"))
                .andExpect(jsonPath("$.idPrmp").value("PRMP001"))
                .andExpect(jsonPath("$.idDmc").value(ficheA))
                .andExpect(jsonPath("$.ficheMarche.statut").value("VALIDEE"))
                .andReturn().getResponse().getContentAsString();
        int idDossier = JsonPath.read(corps, "$.idDossier");
        Dossier plan = dossierRepository.findById(9900).orElseThrow();
        assertThat(plan.getIdEntiteContract()).isEqualTo(1);
        assertThat(plan.getIdLocalite()).isEqualTo("ANT");

        String journal = mvc.perform(get("/api/dossiers/" + idDossier + "/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[*].typeAction"))
                .containsExactlyInAnyOrder("CREATION", "DOSSIER_CREE_DEPUIS_FICHE");
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='DOSSIER_CREE_DEPUIS_FICHE')].detail"))
                .containsExactly("Fiche marché version 1, 1 information(s)");

        mvc.perform(post("/api/fiches-marche/" + ficheA + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOSSIER_EXISTANT"))
                .andExpect(jsonPath("$.idDossier").value(idDossier));
        // Une révision ouverte depuis ne cache pas le dossier déjà produit.
        mvc.perform(post("/api/fiches-marche/" + ficheA + "/reviser").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        mvc.perform(post("/api/fiches-marche/" + ficheA + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOSSIER_EXISTANT"));

        // Geste de la PRMP seule.
        mvc.perform(post("/api/fiches-marche/" + ficheB + "/dossier").header("Authorization", tokenUgpm))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/fiches-marche/999999/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 3. lectures croisées

    @Test
    @DisplayName("3 — GET dossier : idDmc et bloc ficheMarche complet ; GET fiche et GET dmc : idDossierSoumis posé, idDossier "
            + "de la fiche reste le plan")
    void lecturesCroisees() throws Exception {
        int idDossier = creerDossier(ficheA);
        mvc.perform(get("/api/dossiers/" + idDossier).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDmc").value(ficheA))
                .andExpect(jsonPath("$.ficheMarche.idDmc").value(ficheA))
                .andExpect(jsonPath("$.ficheMarche.idDetail").value(9901))
                .andExpect(jsonPath("$.ficheMarche.refeDossierPpm").value("DOS-9900"))
                .andExpect(jsonPath("$.ficheMarche.designationMarche").value("Marché 9901"))
                .andExpect(jsonPath("$.ficheMarche.typeMarche").value("QUANTITE_FIXE"))
                .andExpect(jsonPath("$.ficheMarche.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.ficheMarche.version").value(1))
                .andExpect(jsonPath("$.ficheMarche.nbSaisis").value(1))
                .andExpect(jsonPath("$.ficheMarche.nbAttendus").isNumber());
        mvc.perform(get("/api/fiches-marche/" + ficheA).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDossierSoumis").value(idDossier))
                .andExpect(jsonPath("$.idDossier").value(9900));
        mvc.perform(get("/api/dmcs/" + ficheA).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idDossierSoumis").value(idDossier));
        mvc.perform(get("/api/fiches-marche/" + ficheB).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idDossierSoumis").doesNotExist());
        // Les listes portent idDmc, pas le bloc.
        String liste = mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(liste, "$[?(@.idDossier==" + idDossier + ")].idDmc")).hasSize(1);
        assertThat(JsonPath.<List<Object>>read(liste, "$[?(@.idDossier==" + idDossier + ")].ficheMarche")).isEmpty();
    }

    // ------------------------------------------------------------------ 4. rattachement de secours

    @Test
    @DisplayName("4 — Rattacher : dossier DAO brouillon + fiche validée → 200 ; transmis → 409 DOSSIER_NON_BROUILLON ; "
            + "DAOR → 409 DOSSIER_NON_DAO ; fiche déjà liée → 409 FICHE_DEJA_LIEE ; DELETE défait, autre fiche → 200")
    void rattachement() throws Exception {
        mvc.perform(put("/api/dossiers/9950/fiche-marche").header("Authorization", tokenUgpm).contentType(JSON)
                .content("{\"idDmc\":" + ficheA + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDmc").value(ficheA))
                .andExpect(jsonPath("$.ficheMarche.idDetail").value(9901));
        mvc.perform(put("/api/dossiers/9950/fiche-marche").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"idDmc\":" + ficheA + "}"))
                .andExpect(status().isOk());   // même fiche : rien ne change

        mvc.perform(put("/api/dossiers/9951/fiche-marche").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"idDmc\":" + ficheA + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FICHE_DEJA_LIEE"))
                .andExpect(jsonPath("$.idDossier").value(9950));
        mvc.perform(put("/api/dossiers/9950/fiche-marche").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"idDmc\":" + ficheB + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOSSIER_DEJA_LIE"));
        mvc.perform(put("/api/dossiers/9951/fiche-marche").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"idDmc\":" + ficheC + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_NON_VALIDEE"));

        dossierRepository.save(dossierDao(9953, "SOUMIS", "DAO", "PRMP001"));
        mvc.perform(put("/api/dossiers/9953/fiche-marche").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"idDmc\":" + ficheB + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOSSIER_NON_BROUILLON"));
        dossierRepository.save(dossierDao(9954, "BROUILLON", "DAOR", "PRMP001"));
        mvc.perform(put("/api/dossiers/9954/fiche-marche").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"idDmc\":" + ficheB + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOSSIER_NON_DAO"));
        mvc.perform(put("/api/dossiers/9951/fiche-marche").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/dossiers/9950/fiche-marche").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDmc").doesNotExist())
                .andExpect(jsonPath("$.ficheMarche").doesNotExist());
        mvc.perform(put("/api/dossiers/9950/fiche-marche").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"idDmc\":" + ficheB + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idDmc").value(ficheB));
        mvc.perform(put("/api/dossiers/9951/fiche-marche").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"idDmc\":" + ficheA + "}"))
                .andExpect(status().isOk());

        String journal = mvc.perform(get("/api/dossiers/9950/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[*].typeAction"))
                .containsExactlyInAnyOrder("FICHE_MARCHE_RATTACHEE", "FICHE_MARCHE_DETACHEE", "FICHE_MARCHE_RATTACHEE");

        // Dossier transmis : on ne détache plus.
        Dossier d = dossierRepository.findById(9950).orElseThrow();
        d.setStatut("SOUMIS");
        dossierRepository.saveAndFlush(d);
        mvc.perform(delete("/api/dossiers/9950/fiche-marche").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOSSIER_NON_BROUILLON"));
    }

    // ------------------------------------------------------------------ 5. rattachables

    @Test
    @DisplayName("5 — rattachables : fiches validées non liées du périmètre (pas la fiche en brouillon, plus celle qu'on "
            + "vient de lier) ; PRMP étrangère → liste vide (200), avec ou sans idDossier")
    void rattachables() throws Exception {
        String corps = mvc.perform(get("/api/fiches-marche/rattachables").param("idDossier", "9950")
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(corps, "$[*].idDetail")).containsExactlyInAnyOrder(9901, 9906);
        assertThat(JsonPath.<List<String>>read(corps, "$[?(@.idDetail==9901)].refeDossierPpm")).containsExactly("DOS-9900");
        assertThat(JsonPath.<List<String>>read(corps, "$[?(@.idDetail==9901)].designationMarche")).containsExactly("Marché 9901");
        assertThat(JsonPath.<List<Integer>>read(corps, "$[?(@.idDetail==9901)].version")).containsExactly(1);
        assertThat(JsonPath.<List<Object>>read(corps, "$[?(@.idDetail==9901)].dateValidation")).doesNotContainNull();

        creerDossier(ficheA);
        mvc.perform(get("/api/fiches-marche/rattachables").header("Authorization", tokenUgpm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idDetail").value(org.hamcrest.Matchers.contains(9906)));

        mvc.perform(get("/api/fiches-marche/rattachables").param("idDossier", "9950").header("Authorization", tokenPrmp2))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/fiches-marche/rattachables").header("Authorization", tokenPrmp2))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/fiches-marche/rattachables").param("idDossier", "999999").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/fiches-marche/rattachables").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 6. ordre des gardes

    @Test
    @DisplayName("6 — Autre PRMP → 403 avant tout 409 (fiche en brouillon, dossier d'autrui) ; PRMP propriétaire sans mandat "
            + "actif → 409 VACANCE_PRMP sur POST …/dossier et sur le rattachement")
    void ordreDesGardes() throws Exception {
        mvc.perform(post("/api/fiches-marche/" + ficheC + "/dossier").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/fiches-marche/" + ficheA + "/dossier").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/dossiers/9950/fiche-marche").header("Authorization", tokenPrmp2).contentType(JSON)
                .content("{\"idDmc\":" + ficheA + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/dossiers/9950/fiche-marche").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
        // Dossier à soi, fiche d'autrui : 403 aussi.
        dossierRepository.save(dossierDao(9960, "BROUILLON", "DAO", "PRMP002"));
        mvc.perform(put("/api/dossiers/9960/fiche-marche").header("Authorization", tokenPrmp2).contentType(JSON)
                .content("{\"idDmc\":" + ficheA + "}"))
                .andExpect(status().isForbidden());

        // PRMP003 : plan signé à elle, fiche validée, mandat expiré.
        planSigne(9920, "PRMP003");
        Marche l = marcheDao(9921, 9920, 9920);
        l.setIdMode(92);
        marcheRepository.save(l);
        DossierMec dmc = new DossierMec();
        dmc.setIdDetail(9921);
        dmc.setIdTypeDmc(typeDmcRepository.findByCode("DAO").orElseThrow().getIdTypeDmc());
        dmc.setStatut(StatutDmc.A_PREPARER);
        dmc.setDateCreation(LocalDateTime.of(2026, 9, 1, 8, 0));
        Long idDmc = dossierMecRepository.save(dmc).getIdDmc();
        FicheMarche f = new FicheMarche();
        f.setIdDmc(idDmc);
        f.setNumeroVersion(1);
        f.setStatut("VALIDEE");
        f.setTypeMarche("QUANTITE_FIXE");
        f.setDateCreation(LocalDateTime.of(2026, 9, 1, 8, 0));
        f.setDateValidation(LocalDateTime.of(2026, 9, 2, 8, 0));
        ficheRepository.save(f);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp3))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VACANCE_PRMP"));
        dossierRepository.save(dossierDao(9961, "BROUILLON", "DAO", "PRMP003"));
        mvc.perform(put("/api/dossiers/9961/fiche-marche").header("Authorization", tokenPrmp3).contentType(JSON)
                .content("{\"idDmc\":" + idDmc + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VACANCE_PRMP"));
    }

    // ------------------------------------------------------------------ 7. lecture par la Commission

    @Test
    @DisplayName("7 — Contrôleur de la localité : lit le dossier soumis (bloc ficheMarche) et sa fiche ; contrôleur d'une "
            + "autre localité → 403 sur les deux")
    void lectureParLaCommission() throws Exception {
        int idDossier = creerDossier(ficheA);
        Dossier d = dossierRepository.findById(idDossier).orElseThrow();
        d.setStatut("SOUMIS");
        dossierRepository.saveAndFlush(d);

        mvc.perform(get("/api/dossiers/" + idDossier).header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ficheMarche.idDmc").value(ficheA))
                .andExpect(jsonPath("$.ficheMarche.statut").value("VALIDEE"));
        mvc.perform(get("/api/fiches-marche/" + ficheA).header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idDossierSoumis").value(idDossier));

        String tokenCcTms = bearer("CTRCC9", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC9", "TMS");
        mvc.perform(get("/api/dossiers/" + idDossier).header("Authorization", tokenCcTms))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/fiches-marche/" + ficheA).header("Authorization", tokenCcTms))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ outils

    /** Plan au PV signé FAV, clôturé, entité 1, localité ANT. */
    private void planSigne(int id, String idPrmp) {
        dossierRepository.save(dossierLoc(id, "CLOTURE", "ANT", idPrmp));
        Dossier d = dossierRepository.findById(id).orElseThrow();
        d.setIdEntiteContract(1);
        dossierRepository.save(d);
        ppmRepository.save(ppm(id, id, idPrmp));
        receptionRepository.save(reception(id, id, "CTRCC1", true));
        dispatchRepository.save(dispatch(id, id, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(id, id, "CTRMEM"));
        seedPvSigne(id, id);
    }

    private Dossier dossierDao(int id, String statut, String sousType, String idPrmp) {
        Dossier d = dossierLoc(id, statut, "ANT", idPrmp);
        d.setIdTypeDossier("DMC");
        d.setIdSousType(sousType);
        d.setIdEntiteContract(1);
        return d;
    }

    private Long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }

    private void cadrage(Long idDmc) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"NON\"}}")).andExpect(status().isOk());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B02").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B02-AU-01\":\"Direction des achats\"}}")).andExpect(status().isOk());
    }

    private Long ficheValidee(int idDetail) throws Exception {
        Long idDmc = creerDmc(idDetail);
        cadrage(idDmc);
        besoinDeTest(idDmc);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        return idDmc;
    }

    private int creerDossier(Long idDmc) throws Exception {
        String corps = mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corps, "$.idDossier");
    }
}
