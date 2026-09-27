package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * ⚠️ <strong>La ligne du plan passe à « Lancé » quand son dossier de mise en concurrence est créé</strong> (règle du
 * pilote du 2026-09-27, demande front {@code demande-backend-2026-09-27-statut-lance-dmc.md}) — §B1 : la création du
 * DMC pose {@code LANCE} sur une ligne {@code PREVU}, conserve un statut manuel et le dit ({@code statutLigne}, journal
 * {@code LIGNE_LANCEE}) ; §B2 : la suppression de la fiche rend {@code PREVU} ; §B3 : une ligne en mise en concurrence ne
 * redevient pas « Prévu » à la main (400 nominatif), la copie de version garde {@code LANCE} ; §B4 : {@code MarcheDto.idDmc}.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), lignes en appel d'offres ouvert : 9901 {@code PREVU},
 * 9902 {@code CHDP}, 9903 {@code DSS}.</p>
 */
class StatutLanceDmcIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @org.springframework.beans.factory.annotation.Autowired
    private cnm.prs.repository.StatutMarcheRepository statutMarcheRepository;

    @BeforeEach
    void jeu() {
        // Le socle ne sème que PREVU : les deux statuts manuels du référentiel de recette ; LANCE est remis par le serveur.
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
    @DisplayName("B1 / B4 — La création du DMC pose LANCE sur une ligne PREVU (statutLigne, journal LIGNE_LANCEE, idDmc servi) ; "
            + "un statut manuel CHDP est conservé et la réponse le dit")
    void creationPoseLance() throws Exception {
        String dmc = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.statutLigne").value("LANCE"))
                .andReturn().getResponse().getContentAsString();
        long idDmc = ((Number) JsonPath.read(dmc, "$.idDmc")).longValue();
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("LANCE")).andExpect(jsonPath("$.idDmc").value(idDmc));
        String liste = mvc.perform(get("/api/marches").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(liste, "$[?(@.idDetail==9901)].idDmc")).containsExactly((int) idDmc);
        assertThat(JsonPath.<List<Object>>read(liste, "$[?(@.idDetail==9903)].idDmc")).containsExactly((Object) null);
        String journal = mvc.perform(get("/api/dossiers/9900/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='LIGNE_LANCEE')].detail"))
                .containsExactly("Ligne 9901 : DMC " + idDmc + " créé, statut PREVU → LANCE");

        // Statut manuel : la création reste possible, le statut n'est pas touché, la réponse et le journal le disent.
        mvc.perform(post("/api/dmcs/par-marche/9902").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.statutLigne").value("CHDP"));
        mvc.perform(get("/api/marches/9902").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CHDP")).andExpect(jsonPath("$.idDmc").isNumber());
        journal = mvc.perform(get("/api/dossiers/9900/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='LIGNE_LANCEE')].detail"))
                .anyMatch(d -> d.startsWith("Ligne 9902 : DMC ") && d.endsWith("statut CHDP conservé (statut manuel)"));
    }

    @Test
    @DisplayName("B2 — La suppression de la fiche rend PREVU à la ligne LANCE (journal) ; une ligne DSS reste DSS")
    void suppressionRendPrevu() throws Exception {
        long idDmc = creerDmc(9901);
        mvc.perform(delete("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PREVU")).andExpect(jsonPath("$.idDmc").isEmpty());
        String journal = mvc.perform(get("/api/dossiers/9900/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='FICHE_MARCHE_SUPPRIMEE')].detail"))
                .containsExactly("DAO de la ligne 9901 (DMC " + idDmc + ") supprimé, sans historique ; statut rendu à PREVU (ligne 9901)");

        long dss = creerDmc(9903);
        mvc.perform(delete("/api/fiches-marche/" + dss).header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        mvc.perform(get("/api/marches/9903").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("DSS"));
    }

    @Test
    @DisplayName("B3 — La mise à jour du plan recopie LANCE (idDmc par filiation) ; PUT en PREVU → 400 nominatif statut ; "
            + "DSS → 200 ; statut absent → inchangé ; LANCE explicite accepté")
    void retourPrevuRefuseEtCopieDeVersion() throws Exception {
        long idDmc = creerDmc(9901);
        String v2 = mvc.perform(post("/api/saisies/ppm/9900/mise-a-jour").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"Actualisation du plan\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idV2 = JsonPath.read(v2, "$.idDossier");
        String liste = mvc.perform(get("/api/marches").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String filtre = "$[?(@.idDossier==" + idV2 + " && @.idLigneOrigine==9901)]";
        assertThat(JsonPath.<List<String>>read(liste, filtre + ".statut")).containsExactly("LANCE");
        assertThat(JsonPath.<List<Integer>>read(liste, filtre + ".idDmc")).containsExactly((int) idDmc);
        int copie = JsonPath.<List<Integer>>read(liste, filtre + ".idDetail").get(0);
        int idPpm = JsonPath.<List<Integer>>read(liste, filtre + ".idPpm").get(0);
        int version = JsonPath.<List<Integer>>read(liste, filtre + ".version").get(0);

        mvc.perform(put("/api/marches/" + copie).header("Authorization", tokenPrmp).contentType(JSON)
                .content(corps(idV2, idPpm, "\"PREVU\"", version))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("statut"))
                .andExpect(jsonPath("$.erreurs[0].message").value("La ligne est en mise en concurrence (dossier de mise en concurrence n° "
                        + idDmc + ") : elle ne redevient pas « Prévu ». Supprimez la fiche DAO pour la rendre préparable."));
        String apres = attendu(mvc.perform(put("/api/marches/" + copie).header("Authorization", tokenPrmp).contentType(JSON)
                .content(corps(idV2, idPpm, null, version))), 200);   // absent = inchangé
        assertThat(JsonPath.<String>read(apres, "$.statut")).isEqualTo("LANCE");
        version = JsonPath.read(apres, "$.version");
        apres = mvc.perform(put("/api/marches/" + copie).header("Authorization", tokenPrmp).contentType(JSON)
                .content(corps(idV2, idPpm, "\"DSS\"", version))).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("DSS")).andReturn().getResponse().getContentAsString();
        version = JsonPath.read(apres, "$.version");
        mvc.perform(put("/api/marches/" + copie).header("Authorization", tokenPrmp).contentType(JSON)
                .content(corps(idV2, idPpm, "\"LANCE\"", version))).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("LANCE"));
        // Une ligne sans DMC revient à PREVU librement.
        String l3 = mvc.perform(get("/api/marches").header("Authorization", tokenPrmp)).andReturn().getResponse().getContentAsString();
        String f3 = "$[?(@.idDossier==" + idV2 + " && @.idLigneOrigine==9903)]";
        int copie3 = JsonPath.<List<Integer>>read(l3, f3 + ".idDetail").get(0);
        int version3 = JsonPath.<List<Integer>>read(l3, f3 + ".version").get(0);
        mvc.perform(put("/api/marches/" + copie3).header("Authorization", tokenPrmp).contentType(JSON)
                .content(corps(idV2, idPpm, "\"PREVU\"", version3))).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PREVU"));
    }

    @Test
    @DisplayName("B3 — Rectification (PATCH …/rectifier) : PREVU sur une ligne lancée → 400 statut ; CHDP → 200")
    void rectificationRefusePrevu() throws Exception {
        long idDmc = creerDmc(9901);
        Dossier plan = dossierRepository.findById(9900).orElseThrow();
        plan.setStatut("EN_ATTENTE_DECISION_PRMP");
        dossierRepository.save(plan);
        mvc.perform(patch("/api/marches/9901/rectifier").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"designationMarche\":\"Ligne corrigée\",\"statut\":\"PREVU\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("statut"))
                .andExpect(jsonPath("$.erreurs[0].message").value(org.hamcrest.Matchers.containsString("n° " + idDmc)));
        String corrige = attendu(mvc.perform(patch("/api/marches/9901/rectifier").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"designationMarche\":\"Ligne corrigée\",\"statut\":\"CHDP\"}")), 200);
        assertThat(JsonPath.<String>read(corrige, "$.statut")).isEqualTo("CHDP");
        assertThat(((Number) JsonPath.read(corrige, "$.idDmc")).longValue()).isEqualTo(idDmc);
    }

    // ------------------------------------------------------------------ outils

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
