package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.SousTypeDossier;
import cnm.prs.enums.PorteePointCtrl;

/**
 * ⚠️ 2026-10-08 (manuel de contrôle a priori, tranche M3, §B3 ; V96) — la grille par sous-type : la grille de base s'ajoute (DAOR ←
 * DAOO), une grille propre écarte les points communs de la famille, un point conditionné par la catégorie reste servi sans fiche ; les
 * points du manuel se sèment au démarrage.
 */
class GrilleParSousTypeIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired private cnm.prs.repository.PointsCtrlRepository points;
    @Autowired private cnm.prs.seed.PointsCtrlManuelSeeder seeder;

    private int point(int id, String famille, String sousType, String libelle, int ordre, String categorie) {
        PointsCtrl p = new PointsCtrl();
        p.setIdPointCtrl(id);
        p.setLibelPointCtrl(libelle);
        p.setOrdrePointCtrl(ordre);
        p.setObligatoire(true);
        p.setIdTypeDossier(famille);
        p.setIdSousType(sousType);
        p.setPortee(PorteePointCtrl.DOSSIER);
        p.setCategorie(categorie);
        return points.save(p).getIdPointCtrl();
    }

    @Test
    @DisplayName("Grille de base, grille propre, point conditionné servi sans fiche ; grille d'un dossier ; seeder du manuel")
    void grilles() throws Exception {
        SousTypeDossier daor = sousTypeDossierRepository.findById("DAOR").orElseThrow();
        daor.setGrilleBase("DAOO");
        sousTypeDossierRepository.save(daor);
        SousTypeDossier dc = sousTypeDossierRepository.findById("DC").orElseThrow();
        dc.setGrillePropre(true);
        sousTypeDossierRepository.save(dc);
        int commun = point(98001, "DMC", null, "Point commun DMC", 1, null);
        int daoo = point(98002, "DMC", "DAOO", "Point DAOO", 2, null);
        int travaux = point(98003, "DMC", "DAOO", "Visite des lieux (travaux)", 3, "TRAVAUX");
        int propreDaor = point(98004, "DMC", "DAOR", "Motif de l'AO restreint", 4, null);
        int propreDc = point(98005, "DMC", "DC", "Mode de sélection adapté", 5, null);

        String grilleDaor = mvc.perform(get("/api/points-ctrls").param("sousType", "DAOR").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(grilleDaor, "$[*].idPointCtrl")).containsExactly(commun, daoo, travaux, propreDaor);
        assertThat(JsonPath.<List<String>>read(grilleDaor, "$[?(@.idPointCtrl==" + travaux + ")].categorie")).containsExactly("TRAVAUX");
        String grilleDc = mvc.perform(get("/api/points-ctrls").param("sousType", "DC").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(grilleDc, "$[*].idPointCtrl")).containsExactly(propreDc);

        int id = dossierSaisiHorsFiche("DMC", "DAOR", tokenPrmp);   // ⚠️ 2026-10-15 : saisi hors fiche avant la règle (H1)
        String grilleDossier = mvc.perform(get("/api/dossiers/" + id + "/grille").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // Sans fiche, le point conditionné par la catégorie reste à examiner.
        assertThat(JsonPath.<List<Integer>>read(grilleDossier, "$[*].idPointCtrl")).containsExactly(commun, daoo, travaux, propreDaor);

        // Le seeder du manuel : il sème les points des familles et sous-types présents, une seule fois.
        int crees = seeder.semer();
        assertThat(crees).isGreaterThan(40);
        assertThat(seeder.semer()).isZero();
        assertThat(points.findAll()).filteredOn(p -> "DAOO".equals(p.getIdSousType()) && "TRAVAUX".equals(p.getCategorie())).hasSize(6);
        assertThat(points.findAll()).filteredOn(p -> "DAOO".equals(p.getIdSousType()) && "CONTRAT_CADRE".equals(p.getForme())).hasSize(5);
        assertThat(points.findAll()).allMatch(p -> p.getDecriptPointCtrl() == null || p.getDecriptPointCtrl().length() <= 255);
    }

    @Autowired private cnm.prs.seed.PointsCtrlDossierMarcheSeeder seederMarche;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

    @Test
    @DisplayName("⚠️ V166-V167 (« points-par-element », B1-B3) — les éléments des points du dossier de marché : posés par les semeurs, "
            + "sans DAO ni BORDEREAU (B3) ; V166 puis V167 réécrivent une valeur de V166, gardent un réglage de l'Administrateur, rattachent "
            + "un point MAOR de même libellé ; servis en tableau ; écrits par l'administration (casse ignorée, absent conservé, [] vide, "
            + "code inconnu 400 elements)")
    void elementsDesPoints() throws Exception {
        if (!typeDossierRepository.existsById("DDM")) {
            typeDossierRepository.save(new cnm.prs.entity.TypeDossier("DDM", "Dossier de marché"));
        }
        for (String st : List.of("MAOO", "MAOR")) {
            if (!sousTypeDossierRepository.existsById(st)) {
                sousTypeDossierRepository.save(new SousTypeDossier(st, "Marché " + st, "DDM"));
            }
        }
        seederMarche.semer();
        seeder.semer();
        java.util.function.Function<String, List<String>> elementsDe = libelle -> {
            try {
                String grille = mvc.perform(get("/api/points-ctrls").param("sousType", "MAOO").header("Authorization", tokenMembre))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                return JsonPath.<List<List<String>>>read(grille, "$[?(@.libelPointCtrl=='" + libelle.replace("'", "\'") + "')].elements").get(0);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        assertThat(elementsDe.apply("Offre retenue acceptable")).containsExactly("ATTRIBUTION", "PRESENTATION");
        assertThat(elementsDe.apply("Délai de remise des offres")).containsExactly("SEANCE");
        assertThat(elementsDe.apply("CAO désignée, membres déclarés")).containsExactly("SEANCE", "PIECES");
        PointsCtrl maor = new PointsCtrl();
        maor.setIdPointCtrl(cnm.prs.service.ClePrimaire.allouerLibre(points::existsById, points::nextIdPointCtrl));
        maor.setLibelPointCtrl("Délai de remise des offres");
        maor.setObligatoire(Boolean.TRUE);
        maor.setIdTypeDossier("DDM");
        maor.setIdSousType("MAOR");
        maor.setPortee(PorteePointCtrl.DOSSIER);
        points.save(maor);
        // Les migrations : une valeur de V166 réécrite, un point vidé rattaché puis réécrit, un réglage gardé, le point MAOR rattaché.
        em.flush();
        jdbc.update("update tr_points_ctrl set \"ELEMENTS\" = 'EVALUATION,BORDEREAU' where \"LIBEL_POINT_CTRL\" = 'Moralité des prix'");
        jdbc.update("update tr_points_ctrl set \"ELEMENTS\" = null where \"LIBEL_POINT_CTRL\" = 'Rejets motivés par le DAO'");
        jdbc.update("update tr_points_ctrl set \"ELEMENTS\" = 'PIECES' where \"LIBEL_POINT_CTRL\" = 'Spécifications techniques'");
        for (String v : List.of("V166__elements_points_ctrl.sql", "V167__elements_points_ctrl_nouveaux.sql", "V167__elements_points_ctrl_nouveaux.sql")) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
                    org.springframework.jdbc.datasource.DataSourceUtils.getConnection(jdbc.getDataSource()),
                    new org.springframework.core.io.ClassPathResource("db/migration/" + v));
        }
        em.clear();   // le cache de la session masquerait les écritures JDBC
        assertThat(elementsDe.apply("Moralité des prix")).containsExactly("EVALUATION");
        assertThat(elementsDe.apply("Rejets motivés par le DAO")).containsExactly("EVALUATION");
        assertThat(elementsDe.apply("Spécifications techniques")).as("un réglage de l'Administrateur n'est pas écrasé").containsExactly("PIECES");
        assertThat(jdbc.queryForObject("select \"ELEMENTS\" from tr_points_ctrl where \"ID_POINT_CTRL\" = ?", String.class, maor.getIdPointCtrl()))
                .isEqualTo("SEANCE");

        String json = "{\"libelPointCtrl\":\"Point de test\",\"obligatoire\":true,\"idTypeDossier\":\"DDM\",\"portee\":\"DOSSIER\",";
        String cree = mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(json + "\"elements\":[\"dao\",\"Seance\",\"DAO\"]}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.elements").value(org.hamcrest.Matchers.contains("DAO", "SEANCE"))).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(cree, "$.idPointCtrl");
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(json + "\"elements\":[\"INCONNU\"]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("elements"));
        mvc.perform(put("/api/points-ctrls/" + id).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(json.substring(0, json.length() - 1) + "}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.elements").value(org.hamcrest.Matchers.contains("DAO", "SEANCE")));
        mvc.perform(put("/api/points-ctrls/" + id).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(json + "\"elements\":[]}")).andExpect(status().isOk()).andExpect(jsonPath("$.elements").isEmpty());
    }

    @Test
    @DisplayName("⚠️ V168 (« points-par-element », B4) — la grille DAOO rattachée PAR FAMILLE : le semeur pose AVIS,DAO sur « Délai de "
            + "remise des offres » de DAOO quand le dossier de marché garde SEANCE ; V168 rattache un point saisi sans élément (→ DAO) ; "
            + "PLAN, AVIS, CANEVAS admis à l'écriture")
    void elementsDeLaGrilleDaoo() throws Exception {
        for (String fam : List.of("DMC", "DDM")) {
            if (!typeDossierRepository.existsById(fam)) {
                typeDossierRepository.save(new cnm.prs.entity.TypeDossier(fam, fam));
            }
        }
        if (!sousTypeDossierRepository.existsById("DAOO")) {
            sousTypeDossierRepository.save(new SousTypeDossier("DAOO", "DAO ouvert", "DMC"));
        }
        if (!sousTypeDossierRepository.existsById("MAOO")) {
            sousTypeDossierRepository.save(new SousTypeDossier("MAOO", "Marché MAOO", "DDM"));
        }
        seeder.semer();
        PointsCtrl allot = new PointsCtrl();   // comme les points saisis par l'Administrateur le 26/09
        allot.setIdPointCtrl(cnm.prs.service.ClePrimaire.allouerLibre(points::existsById, points::nextIdPointCtrl));
        allot.setLibelPointCtrl("Allotissement");
        allot.setObligatoire(Boolean.TRUE);
        allot.setIdTypeDossier("DMC");
        allot.setIdSousType("DAOO");
        allot.setPortee(PorteePointCtrl.DOSSIER);
        points.save(allot);
        em.flush();
        org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
                org.springframework.jdbc.datasource.DataSourceUtils.getConnection(jdbc.getDataSource()),
                new org.springframework.core.io.ClassPathResource("db/migration/V168__elements_points_ctrl_daoo.sql"));
        em.clear();
        String daoo = mvc.perform(get("/api/points-ctrls").param("sousType", "DAOO").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<List<String>>>read(daoo, "$[?(@.libelPointCtrl=='Délai de remise des offres')].elements").get(0))
                .containsExactly("AVIS", "DAO");
        assertThat(JsonPath.<List<List<String>>>read(daoo, "$[?(@.libelPointCtrl=='Allotissement')].elements").get(0)).containsExactly("DAO");
        assertThat(JsonPath.<List<String>>read(daoo, "$[*].elements[*]")).as("aucun point sur PLAN").doesNotContain("PLAN");
        String maoo = mvc.perform(get("/api/points-ctrls").param("sousType", "MAOO").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<List<String>>>read(maoo, "$[?(@.libelPointCtrl=='Délai de remise des offres')].elements").get(0))
                .as("le même libellé garde ses éléments au dossier de marché").containsExactly("SEANCE");
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"libelPointCtrl\":\"Point B4\",\"obligatoire\":true,\"idTypeDossier\":\"DMC\",\"portee\":\"DOSSIER\","
                        + "\"elements\":[\"avis\",\"CANEVAS\",\"PLAN\"]}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.elements").value(org.hamcrest.Matchers.contains("AVIS", "CANEVAS", "PLAN")));
    }

    @Test
    @DisplayName("⚠️ V162 (« grille-marche-aoo ») — trois points du MAOO hérités par ses dérivés ; « Publicité effective de l'avis » écarté "
            + "du MAOR (et du MAORI qui en hérite), gardé au MAOOI ; « offre complète » au point des documents essentiels")
    void grilleDuMarcheSurAppelOffres() throws Exception {
        if (!typeDossierRepository.existsById("DDM")) {
            typeDossierRepository.save(new cnm.prs.entity.TypeDossier("DDM", "Dossier de marché"));
        }
        for (String st : List.of("MAOO", "MAOR", "MAORI", "MAOOI")) {
            if (!sousTypeDossierRepository.existsById(st)) {
                sousTypeDossierRepository.save(new SousTypeDossier(st, "Marché " + st, "DDM"));
            }
        }
        for (String[] base : List.of(new String[] { "MAOR", "MAOO" }, new String[] { "MAORI", "MAOR" }, new String[] { "MAOOI", "MAOO" })) {
            SousTypeDossier st = sousTypeDossierRepository.findById(base[0]).orElseThrow();
            st.setGrilleBase(base[1]);
            sousTypeDossierRepository.save(st);
        }
        seeder.semer();
        java.util.function.Function<String, List<String>> libelles = sousType -> {
            try {
                return JsonPath.read(mvc.perform(get("/api/points-ctrls").param("sousType", sousType).header("Authorization", tokenMembre))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$[*].libelPointCtrl");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        List<String> nouveaux = List.of("Offre retenue acceptable", "Publicité effective de l'avis", "Offres anormales : processus et seuils");
        assertThat(libelles.apply("MAOO")).containsAll(nouveaux);
        assertThat(libelles.apply("MAOOI")).containsAll(nouveaux);
        assertThat(libelles.apply("MAOR")).contains("Offre retenue acceptable", "Offres anormales : processus et seuils")
                .doesNotContain("Publicité effective de l'avis");
        assertThat(libelles.apply("MAORI")).contains("Offre retenue acceptable").doesNotContain("Publicité effective de l'avis");
        assertThat(points.findAll()).filteredOn(p -> "MAOO".equals(p.getIdSousType()) && "Conformité des documents essentiels".equals(p.getLibelPointCtrl()))
                .singleElement().extracting(p -> p.getDecriptPointCtrl()).asString().contains("offre complète");
        assertThat(points.findAll()).filteredOn(p -> nouveaux.contains(p.getLibelPointCtrl())).allMatch(p -> Boolean.TRUE.equals(p.getObligatoire()));
    }
}
