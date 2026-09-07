package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
 * ⚠️ <strong>Prise en charge AVANT la rectification PRMP</strong> (règle pilote du 2026-09-07,
 * {@code docs/demande-backend-2026-09-07-prise-en-charge-rectification-prmp.md}).
 *
 * <p>« Aucune action sans prise en charge » valait pour les huit étapes portées par la CNM ; elle
 * s'arrêtait au seuil de la PRMP. Pendant {@code EN_ATTENTE_DECISION_PRMP} aucune étape n'était ouverte :
 * la prise en charge répondait 409, le front n'avait rien sur quoi verrouiller, et la rectification se
 * faisait sans qu'aucun geste ne soit horodaté.</p>
 *
 * <p>Ce que ces tests protègent, dans l'ordre de la recette de contre-vérification : l'étape
 * {@code RECTIFICATION_PRMP} <strong>ouverte et portée par la PRMP propriétaire</strong> pendant
 * l'attente ; le <strong>double refus</strong> (rectifier ET resoumettre) tant qu'elle n'a pas pris en
 * charge ; l'ouverture des deux gestes après ; la <strong>réserve</strong> de l'étape à la PRMP
 * propriétaire ; et le fait que ce chronomètre reste <strong>hors du compteur net CNM</strong> — le
 * temps de la PRMP est suspensif, il ne devient pas un délai de la Commission.</p>
 */
class PriseEnChargeRectificationPrmpIntegrationTest extends CnmIntegrationTestSupport {

    private static final int DOSSIER = 800;
    private static final int PPM = 800;
    private static final int LIGNE = 8001;

    @Autowired
    private TacheDossierRepository tacheRepository;

    /** Dossier DDP de PRMP001, en attente de rectification — l'état où le constat a été fait. */
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
    @DisplayName("Recette 1 — pendant l'attente, l'étape RECTIFICATION_PRMP est ouverte et revient à la PRMP "
            + "propriétaire ; la prise en charge répond 200 (elle répondait 409, faute d'étape)")
    void attenteRectification_ouvreUneEtapePorteeParLaPrmp() throws Exception {
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapeCourante").value("RECTIFICATION_PRMP"))
                .andExpect(jsonPath("$.attentePrmp").value(true))
                // La liste est CLOSE : la PRMP propriétaire, et elle seule — le front y masque le geste.
                .andExpect(jsonPath("$.acteursAttendus", hasSize(1)))
                .andExpect(jsonPath("$.acteursAttendus", hasItem("PRMP001")))
                // Rien n'est encore pris en charge : aucune tâche ouverte de cette étape.
                .andExpect(jsonPath("$.taches[?(@.etape=='RECTIFICATION_PRMP')]", hasSize(0)));

        mvc.perform(post("/api/dossiers/" + DOSSIER + "/prise-en-charge").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsionHeures\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("RECTIFICATION_PRMP"))
                .andExpect(jsonPath("$.occurrence").value(1))
                .andExpect(jsonPath("$.previsionHeures").value(6))
                .andExpect(jsonPath("$.previsionStandard").value(false))
                .andExpect(jsonPath("$.enCours").value(true))
                .andExpect(jsonPath("$.imActeur").value("PRMP001"));

        // La tâche est désormais visible dans la frise, ouverte : c'est ce que le widget lit pour
        // reconnaître « ma tâche » et ouvrir les gestes.
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.taches[?(@.etape=='RECTIFICATION_PRMP')]", hasSize(1)))
                .andExpect(jsonPath("$.taches[?(@.etape=='RECTIFICATION_PRMP')].enCours", hasItem(true)));
    }

    // ------------------------------------------------------------------ 2. le verrou n'est pas cosmétique

    @Test
    @DisplayName("Recette 2 — SANS prise en charge, les DEUX gestes sont refusés en 409 : rectifier (PUT de "
            + "saisie) comme resoumettre ; le message dit le geste à poser")
    void sansPriseEnCharge_niRectification_niResoumission() throws Exception {
        rectifier("200").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Prendre en charge")));

        mvc.perform(post("/api/dossiers/" + DOSSIER + "/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Prendre en charge")));

        // Refus, donc rien n'a bougé : le dossier attend toujours sa rectification.
        mvc.perform(get("/api/dossiers/" + DOSSIER).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));
    }

    // ------------------------------------------------------------------ 3 & 4. après la prise en charge

    @Test
    @DisplayName("Recette 3 & 4 — après la prise en charge, la rectification passe et la resoumission renvoie "
            + "le dossier en vérification ; la tâche est close par ce geste qui l'achève")
    void apresPriseEnCharge_rectificationEtResoumissionPassent() throws Exception {
        prendreEnChargeRectification(DOSSIER);

        rectifier("300").andExpect(status().isOk());
        mvc.perform(post("/api/dossiers/" + DOSSIER + "/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));

        TacheDossier tache = tacheRepository.findByIdDossierOrderByDatePriseEnChargeAsc(DOSSIER).stream()
                .filter(t -> EtapeCircuit.RECTIFICATION_PRMP.name().equals(t.getEtape())).findFirst().orElseThrow();
        assertThat(tache.getDateFin()).as("la resoumission clôt la tâche de rectification").isNotNull();
        // Le dossier étant reparti, plus aucune étape PRMP n'est ouverte : le verrou se referme seul.
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.etapeCourante").value("VERIFICATION"))
                .andExpect(jsonPath("$.attentePrmp").value(false));
    }

    // ------------------------------------------------------------------ 5. l'étape est réservée à la PRMP

    @Test
    @DisplayName("La rectification revient à la PRMP PROPRIÉTAIRE — un contrôleur CNM est refusé en 403, une "
            + "autre PRMP aussi : la prise en charge ne se délègue pas à qui a demandé la rectification")
    void laRectificationEstReserveeALaPrmpProprietaire() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        mvc.perform(post("/api/dossiers/" + DOSSIER + "/prise-en-charge").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsionHeures\":4}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/dossiers/" + DOSSIER + "/prise-en-charge").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsionHeures\":4}"))
                .andExpect(status().isForbidden());

        String tokenAutrePrmp = bearer("PRMP009", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP009", "ANT");
        mvc.perform(post("/api/dossiers/" + DOSSIER + "/prise-en-charge").header("Authorization", tokenAutrePrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsionHeures\":4}"))
                .andExpect(status().isForbidden());

        assertThat(tacheRepository.ouvertes(DOSSIER, EtapeCircuit.RECTIFICATION_PRMP.name())).isEmpty();
    }

    // ------------------------------------------------------------------ 6. hors du compteur net CNM

    @Test
    @DisplayName("Arbitrage retenu — l'étape reste HORS du compteur net CNM : absente du référentiel des "
            + "délais (toujours huit étapes), et la prise en charge ne déplace pas la date annoncée")
    void horsCompteurNetCnm_referentielEtDateInchanges() throws Exception {
        mvc.perform(get("/api/delais-standards").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[*].etape", not(hasItem("RECTIFICATION_PRMP"))));
        // Ce qui ne figure pas au référentiel ne s'y règle pas : un réglage invisible serait un piège.
        mvc.perform(put("/api/delais-standards/RECTIFICATION_PRMP").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiHeures\":12}"))
                .andExpect(status().isNotFound());

        String avant = mvc.perform(get("/api/dossiers/" + DOSSIER).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        prendreEnChargeRectification(DOSSIER);
        String apres = mvc.perform(get("/api/dossiers/" + DOSSIER).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(com.jayway.jsonpath.JsonPath.read(apres, "$.datePrevisionnelleFin").toString())
                .as("le chronomètre de la PRMP est suspensif : il ne change pas la date annoncée")
                .isEqualTo(com.jayway.jsonpath.JsonPath.read(avant, "$.datePrevisionnelleFin").toString());
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
