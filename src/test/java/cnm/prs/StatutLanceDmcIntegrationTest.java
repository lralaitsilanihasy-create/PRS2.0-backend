package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;

/**
 * ⚠️ <strong>Le statut de la ligne suit la publication</strong> — règle du pilote du 2026-09-27 (demande front
 * {@code demande-backend-2026-09-27-statut-lance-dmc.md}), <strong>revue le 2026-09-30</strong> (demande
 * {@code demande-backend-2026-09-30-statut-lance-avis.md}) : la création du DMC ne touche plus le statut (§B1) ; la
 * ligne passe « Lancé » à la première impression de son avis spécifique (§B2, testé dans
 * {@link AvisSpecifiqueIntegrationTest}) ; « Lancé » ne se choisit qu'après l'avis, « Prévu » plus après (§B3) ;
 * {@code MarcheDto.avisImprimeLe} (§B4). La suppression de la fiche rend encore « Prévu » à une ligne « Lancé » d'avant
 * la règle.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), lignes en appel d'offres ouvert : 9901 {@code PREVU},
 * 9902 {@code CHDP}, 9903 {@code DSS}. Un avis imprimé est simulé par un document {@code AVIS} rattaché à la fiche.</p>
 */
class StatutLanceDmcIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String REFUS_LANCE = "Le marché passe « Lancé » à l'impression de son avis spécifique.";
    private static final String REFUS_PREVU = "L'avis spécifique de ce marché est imprimé : il ne redevient pas « Prévu ».";

    @org.springframework.beans.factory.annotation.Autowired
    private cnm.prs.repository.StatutMarcheRepository statutMarcheRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private cnm.prs.repository.DocumentFicheMarcheRepository documentRepository;

    @BeforeEach
    void jeu() {
        // Le socle ne sème que PREVU : les statuts du référentiel de recette.
        statutMarcheRepository.save(new cnm.prs.entity.StatutMarche("LANCE", "Lancé", 11, true));
        statutMarcheRepository.save(new cnm.prs.entity.StatutMarche("CHDP", "Changement de projet", 12, true));
        statutMarcheRepository.save(new cnm.prs.entity.StatutMarche("DSS", "Déclaré sans suite", 13, true));
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
        ligne(9901, "PREVU");
        ligne(9902, "CHDP");
        ligne(9903, "DSS");
    }

    @Test
    @DisplayName("B1 / B4 — La création du DMC ne touche plus le statut (statutLigne PREVU, pas de LIGNE_LANCEE) ; idDmc servi, "
            + "avisImprimeLe nul")
    void creationNeLancePas() throws Exception {
        String dmc = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.statutLigne").value("PREVU"))
                .andReturn().getResponse().getContentAsString();
        long idDmc = ((Number) JsonPath.read(dmc, "$.idDmc")).longValue();
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PREVU")).andExpect(jsonPath("$.idDmc").value(idDmc))
                .andExpect(jsonPath("$.avisImprimeLe").isEmpty());
        String journal = mvc.perform(get("/api/dossiers/9900/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='LIGNE_LANCEE')]")).isEmpty();
        mvc.perform(post("/api/dmcs/par-marche/9902").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.statutLigne").value("CHDP"));
    }

    @Test
    @DisplayName("B3 — Sans avis imprimé (sur la copie d'une mise à jour du plan, en brouillon) : LANCE refusé (400 nominatif) "
            + "sauf ligne déjà LANCE ; PREVU accepté malgré la fiche DAO ; CHDP accepté ; statut absent → inchangé")
    void sansAvis() throws Exception {
        creerDmc(9901);
        int idV2 = miseAJour();
        int[] c = copie(idV2, 9901);
        mvc.perform(put("/api/marches/" + c[0]).header("Authorization", tokenPrmp).contentType(JSON)
                .content(corps(idV2, c[1], "\"LANCE\"", c[2]))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("statut")).andExpect(jsonPath("$.erreurs[0].message").value(REFUS_LANCE));
        String r = attendu(mvc.perform(put("/api/marches/" + c[0]).header("Authorization", tokenPrmp).contentType(JSON)
                .content(corps(idV2, c[1], "\"CHDP\"", c[2]))), 200);
        assertThat(JsonPath.<String>read(r, "$.statut")).isEqualTo("CHDP");
        r = attendu(mvc.perform(put("/api/marches/" + c[0]).header("Authorization", tokenPrmp).contentType(JSON)
                .content(corps(idV2, c[1], null, JsonPath.read(r, "$.version")))), 200);
        assertThat(JsonPath.<String>read(r, "$.statut")).isEqualTo("CHDP");   // absent = inchangé
        r = attendu(mvc.perform(put("/api/marches/" + c[0]).header("Authorization", tokenPrmp).contentType(JSON)
                .content(corps(idV2, c[1], "\"PREVU\"", JsonPath.read(r, "$.version")))), 200);
        assertThat(JsonPath.<String>read(r, "$.statut")).isEqualTo("PREVU");   // la fiche DAO n'empêche plus « Prévu »

        // Une ligne « Lancé » d'avant la règle (sans avis) se ré-enregistre telle quelle, et peut redevenir « Prévu ».
        int[] c3 = copie(idV2, 9903);
        Marche l = marcheRepository.findById(c3[0]).orElseThrow();
        l.setStatut("LANCE");
        marcheRepository.saveAndFlush(l);
        r = attendu(mvc.perform(put("/api/marches/" + c3[0]).header("Authorization", tokenPrmp).contentType(JSON)
                .content(corps(idV2, c3[1], "\"LANCE\"", version(c3[0])))), 200);
        assertThat(JsonPath.<String>read(r, "$.statut")).isEqualTo("LANCE");
        attendu(mvc.perform(put("/api/marches/" + c3[0]).header("Authorization", tokenPrmp).contentType(JSON)
                .content(corps(idV2, c3[1], "\"PREVU\"", JsonPath.read(r, "$.version")))), 200);
    }

    @Test
    @DisplayName("B3 / B4 — Avec un avis imprimé : avisImprimeLe servi (ligne, liste, copie de version) ; LANCE accepté ; "
            + "PREVU refusé (PUT et rectification) ; CHDP accepté")
    void avecAvis() throws Exception {
        long idDmc = creerDmc(9901);
        avisImprime(idDmc);
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.avisImprimeLe").value("2026-10-05"));
        String liste = mvc.perform(get("/api/marches").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(liste, "$[?(@.idDetail==9901)].avisImprimeLe")).containsExactly("2026-10-05");
        assertThat(JsonPath.<List<Object>>read(liste, "$[?(@.idDetail==9903)].avisImprimeLe")).containsExactly((Object) null);

        // La copie d'une mise à jour du plan porte la même date (filiation) ; les gardes s'y appliquent.
        int idV2 = miseAJour();
        int[] c = copie(idV2, 9901);
        mvc.perform(get("/api/marches/" + c[0]).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.avisImprimeLe").value("2026-10-05"));
        String r = attendu(mvc.perform(put("/api/marches/" + c[0]).header("Authorization", tokenPrmp).contentType(JSON)
                .content(corps(idV2, c[1], "\"LANCE\"", c[2]))), 200);
        assertThat(JsonPath.<String>read(r, "$.statut")).isEqualTo("LANCE");
        assertThat(JsonPath.<String>read(r, "$.avisImprimeLe")).isEqualTo("2026-10-05");
        mvc.perform(put("/api/marches/" + c[0]).header("Authorization", tokenPrmp).contentType(JSON)
                .content(corps(idV2, c[1], "\"PREVU\"", JsonPath.read(r, "$.version")))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("statut")).andExpect(jsonPath("$.erreurs[0].message").value(REFUS_PREVU));

        // Rectification (PATCH …/rectifier) : même garde.
        Dossier plan = dossierRepository.findById(9900).orElseThrow();
        plan.setStatut("EN_ATTENTE_DECISION_PRMP");
        dossierRepository.save(plan);
        mvc.perform(patch("/api/marches/9901/rectifier").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"designationMarche\":\"Ligne corrigée\",\"statut\":\"PREVU\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].message").value(REFUS_PREVU));
        String corrige = attendu(mvc.perform(patch("/api/marches/9901/rectifier").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"designationMarche\":\"Ligne corrigée\",\"statut\":\"CHDP\"}")), 200);
        assertThat(JsonPath.<String>read(corrige, "$.statut")).isEqualTo("CHDP");
    }

    /** Ouvre la mise à jour du plan 9900 (version 2 en brouillon) ; son identifiant. */
    private int miseAJour() throws Exception {
        String v2 = mvc.perform(post("/api/saisies/ppm/9900/mise-a-jour").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"Actualisation du plan\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(v2, "$.idDossier");
    }

    /** La copie d'une ligne dans la version 2 : {@code [idDetail, idPpm, version]}. */
    private int[] copie(int idV2, int origine) throws Exception {
        String liste = mvc.perform(get("/api/marches").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String filtre = "$[?(@.idDossier==" + idV2 + " && @.idLigneOrigine==" + origine + ")]";
        return new int[] { JsonPath.<List<Integer>>read(liste, filtre + ".idDetail").get(0),
                JsonPath.<List<Integer>>read(liste, filtre + ".idPpm").get(0),
                JsonPath.<List<Integer>>read(liste, filtre + ".version").get(0) };
    }

    @Test
    @DisplayName("Suppression de la fiche : une ligne « Lancé » d'avant la règle redevient PREVU (journal) ; DSS reste DSS")
    void suppressionRendPrevu() throws Exception {
        long idDmc = creerDmc(9901);
        Marche l = marcheRepository.findById(9901).orElseThrow();
        l.setStatut("LANCE");
        marcheRepository.saveAndFlush(l);
        mvc.perform(delete("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PREVU")).andExpect(jsonPath("$.idDmc").isEmpty());
        long dss = creerDmc(9903);
        mvc.perform(delete("/api/fiches-marche/" + dss).header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        mvc.perform(get("/api/marches/9903").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("DSS"));
    }

    // ------------------------------------------------------------------ outils

    /** Un avis imprimé le 05/10/2026 sur la fiche du DMC (la fiche est enregistrée par une réponse de cadrage). */
    private void avisImprime(long idDmc) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"NON\"}}")).andExpect(status().isOk());
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int idFiche = JsonPath.read(fiche, "$.idFiche");
        documentRepository.saveAndFlush(new cnm.prs.entity.DocumentFicheMarche(null, idFiche, "AVIS", "pdf", "AVIS_test.pdf", 1L,
                "0".repeat(64), LocalDateTime.of(2026, 10, 5, 9, 0), new byte[] { 1 }, null, "{}"));
    }

    private int version(int idDetail) throws Exception {
        String m = mvc.perform(get("/api/marches/" + idDetail).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(m, "$.version");
    }

    /** Le corps de la réponse, ou un échec qui le rapporte (un « expected 200 but was 400 » muet ne dit rien). */
    private static String attendu(org.springframework.test.web.servlet.ResultActions ra, int statut) throws Exception {
        org.springframework.test.web.servlet.MvcResult r = ra.andReturn();
        String corps = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        if (r.getResponse().getStatus() != statut) {
            throw new AssertionError("statut " + r.getResponse().getStatus() + " au lieu de " + statut + " — " + corps);
        }
        return corps;
    }

    private static String corps(int idDossier, int idPpm, String statutJson, int version) {
        return "{\"idDossier\":" + idDossier + ",\"idPpm\":" + idPpm + ",\"designationMarche\":\"Acquisition de matériels\""
                + (statutJson == null ? "" : ",\"statut\":" + statutJson) + ",\"version\":" + version + "}";
    }

    private long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }

    private void ligne(int idDetail, String statut) {
        Marche l = marcheDao(idDetail, 9900, 9900);
        l.setIdMode(92);
        l.setStatut(statut);
        l.setDesignationMarche("Acquisition de matériels " + idDetail);
        marcheRepository.save(l);
    }
}
