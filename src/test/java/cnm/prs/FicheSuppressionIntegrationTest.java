package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.repository.FicheArticleRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.FicheMarcheValeurRepository;
import cnm.prs.service.ChampFicheMarcheService;

/**
 * ⚠️ <strong>Défaire une fiche marché ouverte par erreur</strong> (demande front du 2026-09-25, §B1) —
 * {@code DELETE /api/fiches-marche/{idDmc}} : une fiche sans historique (jamais validée, sans document, sans dossier)
 * se supprime avec son DMC, ses valeurs et son besoin, et la ligne redevient préparable ; une fiche qui a de l'histoire
 * répond un 409 à code stable.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), lignes à quantité fixe en appel d'offres ouvert, nature
 * fournitures : 9901, 9902, 9903.</p>
 */
class FicheSuppressionIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheService champService;
    @Autowired private FicheMarcheRepository ficheRepository;
    @Autowired private FicheMarcheValeurRepository valeurRepository;
    @Autowired private FicheArticleRepository articleRepository;

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
        for (int id : new int[] { 9901, 9902, 9903 }) {
            Marche l = marcheDao(id, 9900, 9900);
            l.setIdMode(92);
            l.setFormeMarche(FormeMarche.QUANTITE_FIXE);
            marcheRepository.save(l);
        }
        champService.importerCsv(new ClassPathResource("fiche-marche/referentiel-champs-fiche-marche-fournitures.csv")
                .getFile().toPath());
    }

    @Test
    @DisplayName("1 — Fiche en brouillon, valeurs et besoin saisis : 204 ; DMC, versions, valeurs et besoin effacés ; la ligne "
            + "redevient préparable (dejaDao faux) ; GET → 404 ; journal du plan FICHE_MARCHE_SUPPRIMEE ; une fiche jamais "
            + "enregistrée se supprime aussi")
    void suppressionSansHistorique() throws Exception {
        Long idDmc = creerDmc(9901);
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"NON\"}}")).andExpect(status().isOk());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B04").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B04-VO-01\":90}}")).andExpect(status().isOk());
        besoinDeTest(idDmc);
        Integer idFiche = ficheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElseThrow().getIdFiche();
        assertThat(eligible(9901)).isTrue();

        mvc.perform(delete("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        assertThat(dossierMecRepository.existsById(idDmc)).isFalse();
        assertThat(ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc)).isEmpty();
        assertThat(valeurRepository.findByIdFiche(idFiche)).isEmpty();
        assertThat(articleRepository.findParFiche(idFiche)).isEmpty();
        assertThat(eligible(9901)).isFalse();
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        String journal = mvc.perform(get("/api/dossiers/9900/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='FICHE_MARCHE_SUPPRIMEE')].detail"))
                .containsExactly("DAO de la ligne 9901 (DMC " + idDmc + ") supprimé, sans historique");

        Long vierge = creerDmc(9901);   // la ligne s'est rouverte ; une fiche jamais écrite se supprime aussi
        mvc.perform(delete("/api/fiches-marche/" + vierge).header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/fiches-marche/" + vierge).header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("2 — Fiche validée → 409 FICHE_VALIDEE ; révision ouverte → 409 FICHE_AVEC_HISTORIQUE ; brouillon rattaché "
            + "à un dossier → 409 FICHE_AVEC_DOSSIER avec idDossier ; Administrateur et contrôleur → 403")
    void refus() throws Exception {
        Long idDmc = creerDmc(9902);
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"NON\",\"alloti\":\"NON\"}}")).andExpect(status().isOk());
        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", Map.of());
        mvc.perform(delete("/api/fiches-marche/" + idDmc).header("Authorization", tokenAdmin)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/fiches-marche/" + idDmc).header("Authorization", tokenCc)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_VALIDEE"));
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(delete("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_AVEC_HISTORIQUE"));

        // Un brouillon rattaché à un dossier ne s'obtient pas par l'API (un dossier ne reçoit qu'une fiche validée) :
        // la garde est posée en défense, éprouvée sur un état semé.
        Long autre = creerDmc(9903);
        FicheMarche f = new FicheMarche();
        f.setIdDmc(autre);
        f.setNumeroVersion(1);
        f.setStatut("BROUILLON");
        f.setTypeMarche("QUANTITE_FIXE");
        f.setDateCreation(LocalDateTime.now());
        ficheRepository.save(f);
        Dossier soumis = dossier(9950, "BROUILLON");
        soumis.setIdTypeDossier("DMC");
        soumis.setIdSousType("DAO");
        soumis.setIdPrmp("PRMP001");
        soumis.setIdLocalite("ANT");
        soumis.setIdDmc(autre);
        dossierRepository.save(soumis);
        mvc.perform(delete("/api/fiches-marche/" + autre).header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_AVEC_DOSSIER"))
                .andExpect(jsonPath("$.idDossier").value(9950));
    }

    private boolean eligible(int idDetail) throws Exception {
        String corps = mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Boolean> deja = JsonPath.read(corps, "$[?(@.idDetail==" + idDetail + ")].dejaDao");
        return !deja.isEmpty() && deja.get(0);
    }

    private Long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }
}
