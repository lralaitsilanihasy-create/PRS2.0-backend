package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import cnm.prs.entity.Capm;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.Nature;
import cnm.prs.entity.TacheDossier;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.TacheDossierRepository;

/**
 * ⚠️ <strong>Le délai de rectification de la PRMP se mesure, il ne se déclare plus</strong>
 * (demande pilote du 2026-09-12 ; remplace {@code PriseEnChargeRectificationPrmpIntegrationTest}).
 *
 * <p>Le 2026-09-07, « aucune action sans prise en charge » avait été étendue à la PRMP : pendant
 * {@code EN_ATTENTE_DECISION_PRMP}, ni rectifier ni resoumettre ne passait tant qu'elle n'avait pas
 * ouvert sa tâche. La demande du 2026-09-12 <strong>retire ce verrou</strong> — comme tous les autres :
 * les deux gestes s'exécutent directement, et le temps de la PRMP est mesuré entre la vérification qui a
 * maintenu les observations et sa resoumission.</p>
 *
 * <p>Ce que ces tests protègent : l'étape {@code RECTIFICATION_PRMP} <strong>existe toujours</strong> et
 * reste portée par la PRMP propriétaire (c'est elle qui donne son délai un nom) ; les deux gestes passent
 * <strong>sans rien déclarer</strong> ; le passage est enregistré, clos, à son nom ; les gardes de
 * <strong>propriété</strong> demeurent, elles, intactes — retirer le verrou du chronomètre n'ouvre la
 * rectification à personne d'autre ; et ce délai reste <strong>hors du compteur net CNM</strong>.</p>
 */
class RectificationPrmpDelaiMesureIntegrationTest extends CnmIntegrationTestSupport {

    private static final int DOSSIER = 800;
    private static final int PPM = 800;
    private static final int LIGNE = 8001;

    @Autowired
    private TacheDossierRepository tacheRepository;

    /** Dossier DDP de PRMP001, en attente de rectification — l'état où tout se joue. */
    @BeforeEach
    void dossierEnAttenteDeRectification() {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        Dossier d = dossierLoc(DOSSIER, "EN_ATTENTE_DECISION_PRMP", "ANT", "PRMP001");
        d.setIdTypeDossier("DDP");
        dossierRepository.save(d);
        ppmRepository.save(ppm(PPM, DOSSIER, "PRMP001"));
        Marche m = marche(LIGNE, DOSSIER, PPM);
        m.setMontEstim(new BigDecimal("100"));
        marcheRepository.save(m);
    }

    // ------------------------------------------------------------------ 1. l'étape existe et a un porteur

    @Test
    @DisplayName("1 — Pendant l'attente, l'étape RECTIFICATION_PRMP est EN COURS au nom de la PRMP "
            + "propriétaire ; plus aucun « acteursAttendus » : il n'y a plus de geste à autoriser")
    void attenteRectification_etapeEnCoursAuNomDeLaPrmp() throws Exception {
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapeCourante").value("RECTIFICATION_PRMP"))
                .andExpect(jsonPath("$.attentePrmp").value(true))
                .andExpect(jsonPath("$.acteursAttendus").doesNotExist())
                // L'étape en cours ferme la liste des passages, sans fin et au nom de son porteur.
                .andExpect(jsonPath("$.etapes[?(@.etape=='RECTIFICATION_PRMP')]", hasSize(1)))
                .andExpect(jsonPath("$.etapes[?(@.etape=='RECTIFICATION_PRMP')].enCours", hasItem(true)))
                .andExpect(jsonPath("$.etapes[?(@.etape=='RECTIFICATION_PRMP')].imActeur", hasItem("PRMP001")));
    }

    // ------------------------------------------------------------------ 2. les deux gestes passent directement

    @Test
    @DisplayName("2 — ⚠️ 2026-09-12 : rectifier PUIS resoumettre passent SANS rien prendre en charge — les "
            + "deux 409 « Prenez d'abord en charge » ont disparu")
    void sansRienDeclarer_rectificationEtResoumissionPassent() throws Exception {
        rectifier("300").andExpect(status().isOk());
        mvc.perform(post("/api/dossiers/" + DOSSIER + "/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));

        TacheDossier passage = tacheRepository.findParDossier(DOSSIER).stream()
                .filter(t -> EtapeCircuit.RECTIFICATION_PRMP.name().equals(t.getEtape())).findFirst().orElseThrow();
        assertThat(passage.getDateFin()).as("la resoumission enregistre la fin de l'étape").isNotNull();
        assertThat(passage.getImActeur()).isEqualTo("PRMP001");

        // Le dossier reparti, plus aucune étape PRMP n'est en cours : le chronomètre suit le circuit.
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.etapeCourante").value("VERIFICATION"))
                .andExpect(jsonPath("$.attentePrmp").value(false));
    }

    // ------------------------------------------------------------------ 3. les gardes de PROPRIÉTÉ demeurent

    @Test
    @DisplayName("3 — Retirer le verrou du chronomètre n'ouvre rien : la rectification reste réservée à la "
            + "PRMP PROPRIÉTAIRE — un contrôleur CNM et une autre PRMP sont toujours refusés")
    void laRectificationResteReserveeALaPrmpProprietaire() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        mvc.perform(post("/api/dossiers/" + DOSSIER + "/resoumettre").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isForbidden());

        String tokenAutrePrmp = bearer("PRMP009", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP009", "ANT");
        mvc.perform(post("/api/dossiers/" + DOSSIER + "/resoumettre").header("Authorization", tokenAutrePrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isForbidden());

        // Refusés, donc rien n'a bougé — et aucun passage n'a été enregistré à leur nom.
        mvc.perform(get("/api/dossiers/" + DOSSIER).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));
        assertThat(tacheRepository.findParDossier(DOSSIER)).isEmpty();
    }

    // ------------------------------------------------------------------ 4. hors du compteur net CNM

    @Test
    @DisplayName("4 — L'étape reste HORS du compteur net CNM : absente du référentiel des délais (toujours "
            + "huit étapes), et le temps de la PRMP ne devient jamais un délai de la Commission")
    void horsCompteurNetCnm_referentielEtDateInchanges() throws Exception {
        mvc.perform(get("/api/delais-standards").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[*].etape", not(hasItem("RECTIFICATION_PRMP"))));
        // Ce qui ne figure pas au référentiel ne s'y règle pas : un réglage invisible serait un piège.
        mvc.perform(put("/api/delais-standards/RECTIFICATION_PRMP").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiHeures\":12}"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/dossiers/" + DOSSIER + "/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.attentePrmp").value(true))
                // Le temps de la PRMP reste compté comme attente, jamais comme délai de la CNM.
                .andExpect(jsonPath("$.dureeNetteHeuresOuvrees").value(0));
    }

    // ------------------------------------------------------------------ outillage

    /** PUT de rectification (façade de saisie) — la structure est figée, seul le montant change. */
    private ResultActions rectifier(String montEstim) throws Exception {
        String corps = "{\"exercice\":2026,\"signataire\":\"PRMP Test\",\"dateSignature\":\"2026-06-01\","
                + "\"reference\":\"PPM-800\",\"marches\":[{\"idDetail\":" + LIGNE
                + ",\"formeMarche\":\"QUANTITE_FIXE\",\"montEstim\":" + montEstim
                + ",\"idNature\":1,\"statut\":\"PREVU\",\"designationMarche\":\"Marche 8001\"}]}";
        return mvc.perform(put("/api/saisies/ppm/" + DOSSIER).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corps));
    }
}
