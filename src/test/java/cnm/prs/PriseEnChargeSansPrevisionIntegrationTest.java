package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import cnm.prs.entity.Dossier;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.repository.TacheDossierRepository;

/**
 * ⚠️ <strong>« Prendre en charge » ne demande plus de prévision</strong> (demande pilote du 2026-09-08 —
 * {@code docs/demande-backend-2026-09-08-prise-en-charge-sans-prevision-defaut-standard.md}).
 *
 * <p>Le bouton ne sert qu'à <strong>démarrer le chronomètre</strong> : la prévision est prise par défaut
 * sur le <strong>délai standard administrable</strong> de l'étape, et l'occurrence porte
 * {@code previsionStandard = true}. Une valeur explicite reste acceptée — elle vaut alors prévision
 * <em>estimée</em>, et c'est cette distinction que le drapeau doit continuer de porter : une prévision
 * choisie ne se lit pas comme une prévision par défaut.</p>
 *
 * <p>Ce que ces tests protègent : le <strong>corps absent</strong> accepté, la prévision lue dans le
 * <strong>référentiel</strong> (et non un littéral — EXAMEN vaut 40 h, pas le repli de 8 h), le cas de
 * {@code RECTIFICATION_PRMP} qui ne figure pas au référentiel, le <strong>rejeu idempotent</strong>, la
 * <strong>compatibilité</strong> de la valeur explicite, et le fait que « zéro heure » reste une erreur —
 * pouvoir ne rien dire n'autorise pas à dire n'importe quoi.</p>
 */
class PriseEnChargeSansPrevisionIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private TacheDossierRepository tacheRepository;

    private void dossier1EnStatut(String statut) {
        Dossier d = dossierRepository.findById(1).orElseThrow();
        d.setStatut(statut);
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
    }

    /** Prise en charge SANS corps du tout — le geste que le front posera désormais. */
    private org.springframework.test.web.servlet.ResultActions prendreEnChargeSansCorps(int idDossier,
            String token) throws Exception {
        return mvc.perform(post("/api/dossiers/" + idDossier + "/prise-en-charge")
                .header("Authorization", token));
    }

    // ------------------------------------------------------------------ 1. le standard s'applique

    @Test
    @DisplayName("Contre-recette 1 — prise en charge SANS CORPS par le porteur : 200, prévision = le "
            + "standard du référentiel (EXAMEN → 40 h, pas le repli de 8 h) et previsionStandard = true")
    void sansCorps_laPrevisionEstLeStandardDuReferentiel() throws Exception {
        dossier1EnStatut("DISPATCHE");   // étape courante : EXAMEN, attribuée à CTRMEM

        prendreEnChargeSansCorps(1, tokenMembre)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("EXAMEN"))
                .andExpect(jsonPath("$.occurrence").value(1))
                .andExpect(jsonPath("$.previsionHeures").value(40))
                .andExpect(jsonPath("$.previsionStandard").value(true))
                .andExpect(jsonPath("$.enCours").value(true))
                .andExpect(jsonPath("$.imActeur").value("CTRMEM"));

        // La valeur vient bien du référentiel : le régler change la prévision du geste suivant.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/delais-standards/EXAMEN").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiHeures\":33}"))
                .andExpect(status().isOk());
        prendreEnChargeSansCorps(1, tokenMembre)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previsionHeures").value(33))
                .andExpect(jsonPath("$.occurrence").value(1));   // rejeu : aucune occurrence de plus
    }

    @Test
    @DisplayName("Contre-recette 1 (VISA) — le porteur du visa prend en charge sans corps : 16 h, le "
            + "standard de SON étape ; la date prévisionnelle reste celle que le référentiel calcule")
    void sansCorps_surLeVisa_seizeHeures() throws Exception {
        dossier1EnStatut("EXAMINE");
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":980,\"idExamen\":1,\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/980/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"prêt\"}"))
                .andExpect(status().isOk());

        String avant = mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        prendreEnChargeSansCorps(1, tokenPresident)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("VISA"))
                .andExpect(jsonPath("$.previsionHeures").value(16))
                .andExpect(jsonPath("$.previsionStandard").value(true));

        // La prévision étant le standard, la date annoncée ne bouge pas : elle était déjà calculée dessus.
        String apres = mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(com.jayway.jsonpath.JsonPath.read(apres, "$.datePrevisionnelleFin").toString())
                .isEqualTo(com.jayway.jsonpath.JsonPath.read(avant, "$.datePrevisionnelleFin").toString());
    }

    // ------------------------------------------------------------------ 2. l'étape de la PRMP

    @Test
    @DisplayName("Contre-recette 3 — RECTIFICATION_PRMP n'est PAS au référentiel (étape de la PRMP, pas "
            + "un délai de la Commission) : la prise en charge sans corps y applique le repli serveur de 8 h")
    void sansCorps_surLaRectificationPrmp_repliServeur() throws Exception {
        dossier1EnStatut("EN_ATTENTE_DECISION_PRMP");

        prendreEnChargeSansCorps(1, tokenPrmp)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("RECTIFICATION_PRMP"))
                .andExpect(jsonPath("$.previsionHeures").value(8))
                .andExpect(jsonPath("$.previsionStandard").value(true));

        // Le référentiel, lui, ne l'expose toujours pas : il ne décrit que les délais de la CNM.
        mvc.perform(get("/api/delais-standards").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(8)))
                .andExpect(jsonPath("$[*].etape",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("RECTIFICATION_PRMP"))));
    }

    // ------------------------------------------------------------------ 3. compatibilité et bornes

    @Test
    @DisplayName("Contre-recette 4 — une prévision EXPLICITE reste acceptée et vaut prévision ESTIMÉE "
            + "(previsionStandard = false) ; le drapeau distingue toujours le choix du défaut")
    void previsionExplicite_encoreAcceptee_etMarqueeCommeSaisie() throws Exception {
        dossier1EnStatut("DISPATCHE");

        mvc.perform(post("/api/dossiers/1/prise-en-charge").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsionHeures\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previsionHeures").value(12))
                .andExpect(jsonPath("$.previsionStandard").value(false));

        // Rejeu SANS corps sur sa propre tâche : retour au standard, sans occurrence de plus.
        prendreEnChargeSansCorps(1, tokenMembre)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previsionHeures").value(40))
                .andExpect(jsonPath("$.previsionStandard").value(true))
                .andExpect(jsonPath("$.occurrence").value(1));
        assertThat(tacheRepository.ouvertes(1, EtapeCircuit.EXAMEN.name())).hasSize(1);
    }

    @Test
    @DisplayName("Pouvoir NE RIEN DIRE n'autorise pas à dire n'importe quoi — zéro ou négatif reste 400 ; "
            + "et les gardes d'identité comme le 409 « aucune étape » ne bougent pas")
    void bornesEtGardesInchangees() throws Exception {
        dossier1EnStatut("DISPATCHE");

        mvc.perform(post("/api/dossiers/1/prise-en-charge").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsionHeures\":0}"))
                .andExpect(status().isBadRequest());

        // Garde d'identité : l'examen revient à son attributaire, corps ou pas.
        prendreEnChargeSansCorps(1, tokenPresident).andExpect(status().isForbidden());

        // 409 inchangé : hors circuit, il n'y a rien à prendre en charge.
        dossier1EnStatut("CLOTURE");
        prendreEnChargeSansCorps(1, tokenMembre).andExpect(status().isConflict());
    }
}
