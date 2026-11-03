package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
