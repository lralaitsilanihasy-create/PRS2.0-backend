package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.service.InstantaneService;

/**
 * ⚠️ R4 (manuel révisé pour l'application, lot 1, §B5 ; V104, 2026-10-13) — <strong>ce que le contrôleur voit</strong> : à la
 * soumission d'un dossier DAO produit par la fiche, les écrans PLAN, AVIS et CAO sont figés (JSON tel que servi, empreinte SHA-256,
 * numéro de soumission) ; le Membre les lit et les exporte en PDF ; la resoumission fige la soumission n° 2 ; une ligne figée est
 * immuable.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), ligne 9901 de fournitures (mode 92 → DAO), fiche validée (v1).</p>
 */
class InstantaneIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private Long idDmc;

    @BeforeEach
    void jeu() throws Exception {
        TypeDmc dao = typeDmcRepository.findByCode("DAO").orElseThrow();
        ModePassation m92 = new ModePassation(92, "Appel d'offres ouvert", null, null, null, null);
        m92.setIdTypeDmc(dao.getIdTypeDmc());
        modePassationRepository.save(m92);
        dossierRepository.save(dossierLoc(9900, "CLOTURE", "ANT", "PRMP001"));
        Dossier plan = dossierRepository.findById(9900).orElseThrow();
        plan.setIdEntiteContract(1);
        dossierRepository.save(plan);
        ppmRepository.save(ppm(9900, 9900, "PRMP001"));
        receptionRepository.save(reception(9900, 9900, "CTRCC1", true));
        dispatchRepository.save(dispatch(9900, 9900, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9900, 9900, "CTRMEM"));
        seedPvSigne(9900, 9900);
        Marche l = marcheDao(9901, 9900, 9900);
        l.setIdMode(92);
        l.setDesignationMarche("Fourniture de mobilier de bureau");
        marcheRepository.save(l);
        TypePieceJointe t = typePieceJointeRepository.findById(seedTypePiece("Dossier d'appel d'offres complet", true, "DMC", 1))
                .orElseThrow();
        t.setCode("DAO_COMPLET");
        typePieceJointeRepository.save(t);

        String corps = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        idDmc = ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"NON\",\"alloti\":\"NON\",\"typePrix\":\"UNITAIRES\"}}")).andExpect(status().isOk());
        valider();
    }

    @Test
    @DisplayName("Soumission : PLAN, AVIS et CAO figés sous le n° 1 (JSON servi, empreinte SHA-256 du contenu stocké, acteur) ; le Membre "
            + "lit la liste, chaque écran et son PDF ; écran inconnu → 400, écran sans donnée → 404, soumission inconnue → 404 ; "
            + "la resoumission fige le n° 2, ?soumission=1 relit le premier ; la ligne figée est immuable")
    void instantanes() throws Exception {
        String cree = mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDossier = JsonPath.read(cree, "$.idDossier");
        mvc.perform(get("/api/dossiers/" + idDossier + "/instantanes").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mvc.perform(post("/api/dossiers/" + idDossier + "/soumettre").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"));

        String liste = mvc.perform(get("/api/dossiers/" + idDossier + "/instantanes").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(liste, "$[*].ecran")).containsExactlyInAnyOrder("PLAN", "AVIS", "CAO");
        assertThat(JsonPath.<List<Integer>>read(liste, "$[*].numeroSoumission")).containsOnly(1);
        assertThat(JsonPath.<List<String>>read(liste, "$[*].figePar")).containsOnly("PRMP001");
        assertThat(JsonPath.<List<String>>read(liste, "$[*].empreinte")).allMatch(e -> e.matches("[0-9a-f]{64}"));
        assertThat(JsonPath.<List<Integer>>read(liste, "$[*].taille")).allMatch(n -> n > 2);

        // Le JSON de chaque écran, tel que servi à la soumission.
        mvc.perform(get("/api/dossiers/" + idDossier + "/instantanes/PLAN").header("Authorization", tokenMembre)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ligne.designationMarche").value("Fourniture de mobilier de bureau"))
                .andExpect(jsonPath("$.ligne.idDetail").value(9901)).andExpect(jsonPath("$.valeurs").isMap())
                .andExpect(jsonPath("$.valeurs.MODE").value("Appel d'offres ouvert"));
        mvc.perform(get("/api/dossiers/" + idDossier + "/instantanes/avis").header("Authorization", tokenMembre)).andExpect(status().isOk())
                .andExpect(jsonPath("$.avis.raison").value("PV_NON_SIGNE")).andExpect(jsonPath("$.avis.idDossierSoumis").value(idDossier))
                .andExpect(jsonPath("$.lettres.raison").value("CATEGORIE_SANS_LETTRE"))
                .andExpect(jsonPath("$.projets[?(@.type=='AVIS_PROJET')]").isNotEmpty()).andExpect(jsonPath("$.documents").isArray());
        mvc.perform(get("/api/dossiers/" + idDossier + "/instantanes/CAO").header("Authorization", tokenMembre)).andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap());
        mvc.perform(get("/api/dossiers/" + idDossier + "/instantanes/SEANCE").header("Authorization", tokenMembre)).andExpect(status().isNotFound());
        mvc.perform(get("/api/dossiers/" + idDossier + "/instantanes/XYZ").header("Authorization", tokenMembre)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ECRAN_INCONNU"));
        mvc.perform(get("/api/dossiers/" + idDossier + "/instantanes/PLAN").param("soumission", "2").header("Authorization", tokenMembre))
                .andExpect(status().isNotFound());

        // L'export PDF, pour l'archivage avec le PV.
        byte[] pdf = mvc.perform(get("/api/dossiers/" + idDossier + "/instantanes/PLAN/pdf").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("INSTANTANE_PLAN_" + idDossier + "_s1.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        String texte = texteDuPdf(pdf).replaceAll("\\s+", " ");
        assertThat(texte).contains("Instantané — PLAN", "soumission n° 1", "Fourniture de mobilier de bureau", "empreinte SHA-256");

        // L'empreinte est celle du contenu stocké.
        List<Map<String, Object>> lignes = jdbcTemplate.queryForList(
                "select \"ECRAN\", \"CONTENU\", \"EMPREINTE\" from t_dossier_instantane where \"ID_DOSSIER\" = ? order by \"ECRAN\"", idDossier);
        assertThat(lignes).hasSize(3);
        for (Map<String, Object> l : lignes) {
            assertThat(l.get("EMPREINTE")).isEqualTo(InstantaneService.empreinte((String) l.get("CONTENU")));
        }

        // Resoumission (chemin FAVR : dossier rendu à la PRMP, révision validée) : soumission n° 2 ; le n° 1 reste lisible.
        Dossier d = dossierRepository.findById(idDossier).orElseThrow();
        d.setStatut("EN_ATTENTE_DECISION_PRMP");
        dossierRepository.saveAndFlush(d);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        valider();
        mvc.perform(post("/api/dossiers/" + idDossier + "/resoumettre").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motifRectification\":\"Correction du cadrage\"}")).andExpect(status().isOk());
        liste = mvc.perform(get("/api/dossiers/" + idDossier + "/instantanes").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(liste, "$[*].numeroSoumission")).containsOnly(1, 2).hasSize(6);
        mvc.perform(get("/api/dossiers/" + idDossier + "/instantanes/PLAN").param("soumission", "1").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ligne.idDetail").value(9901));
        mvc.perform(get("/api/dossiers/" + idDossier + "/instantanes/AVIS").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.projets[0].version").value(2));   // la dernière soumission : le projet de la v2

        // Immuable : aucune mise à jour (trigger) — dernier geste du test, la transaction est marquée pour l'annulation.
        assertThatThrownBy(() -> jdbcTemplate.update("update t_dossier_instantane set \"ECRAN\" = 'PLAN' where \"ID_DOSSIER\" = ?", idDossier))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immuable");
    }

    private void valider() throws Exception {
        besoinDeTest(idDmc);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp)).andExpect(status().isOk());
    }
}
