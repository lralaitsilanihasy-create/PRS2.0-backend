package cnm.prs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.TacheDossier;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.SuspensionDossierRepository;
import cnm.prs.repository.TacheDossierRepository;
import cnm.prs.service.JoursOuvres;

/**
 * ⚠️ <strong>Chronométrage et prévision des délais</strong> (règle du pilote, 2026-09-01).
 *
 * <p>Ce que ces tests protègent en priorité : que le chronométrage <strong>n'empêche jamais le
 * métier</strong> (tolérance), que la date annoncée <strong>glisse au lieu de mentir</strong>, et que
 * les attentes PRMP ne soient <strong>imputées à personne</strong> à la CNM.</p>
 */
class ChronometrageIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private TacheDossierRepository tacheRepository;

    @Autowired
    private SuspensionDossierRepository suspensionRepository;

    // ------------------------------------------------------------------ référentiel des délais standards

    @Test
    @DisplayName("Référentiel — les HUIT étapes sont servies, dans l'ordre du circuit, avec leur délai")
    void referentiel_huitEtapes() throws Exception {
        mvc.perform(get("/api/delais-standards").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(8)))
                .andExpect(jsonPath("$[0].etape").value("RECEPTION"))
                .andExpect(jsonPath("$[7].etape").value("ARCHIVAGE"))
                .andExpect(jsonPath("$[2].etape").value("EXAMEN"))
                .andExpect(jsonPath("$[2].delaiJours").value(5));
    }

    @Test
    @DisplayName("Référentiel — réglage réservé à l'Administrateur ; délai < 1 refusé ; étape inconnue → 404")
    void referentiel_gardes() throws Exception {
        mvc.perform(put("/api/delais-standards/EXAMEN").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiJours\":7}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/delais-standards/EXAMEN").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiJours\":0}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/delais-standards/PAUSE_CAFE").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiJours\":3}"))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/delais-standards/EXAMEN").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiJours\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delaiJours").value(7));
    }

    // ------------------------------------------------------------------ prise en charge

    @Test
    @DisplayName("Prise en charge — le porteur de l'étape ouvre la tâche avec sa prévision")
    void priseEnCharge_ouvreLaTache() throws Exception {
        dossierEnStatut(1, "PRET_DISPATCH");

        mvc.perform(post("/api/dossiers/1/prise-en-charge").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsionJours\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("DISPATCH"))
                .andExpect(jsonPath("$.occurrence").value(1))
                .andExpect(jsonPath("$.previsionJours").value(4))
                .andExpect(jsonPath("$.previsionStandard").value(false))
                .andExpect(jsonPath("$.enCours").value(true))
                .andExpect(jsonPath("$.imActeur").value("CTRCC1"));
    }

    @Test
    @DisplayName("Prise en charge REJOUÉE sur une tâche ouverte — corrige la prévision, n'ouvre PAS d'occurrence")
    void priseEnCharge_rejouee_corrige() throws Exception {
        dossierEnStatut(1, "PRET_DISPATCH");
        prendreEnCharge(1, tokenCc, 4);

        mvc.perform(post("/api/dossiers/1/prise-en-charge").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsionJours\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrence").value(1))
                .andExpect(jsonPath("$.previsionJours").value(9));

        assertEquals(1, tacheRepository.findByIdDossierOrderByDatePriseEnChargeAsc(1).size());
    }

    @Test
    @DisplayName("Prise en charge — profil hors étape → 403 ; aucune étape ouverte → 409")
    void priseEnCharge_gardes() throws Exception {
        dossierEnStatut(1, "PRET_DISPATCH");
        // L'étape DISPATCH revient au P/CC : la PRMP n'a rien à y prendre en charge.
        mvc.perform(post("/api/dossiers/1/prise-en-charge").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsionJours\":2}"))
                .andExpect(status().isForbidden());

        // CLOTURE : le dossier est sorti du circuit, plus aucune tâche n'est ouverte.
        dossierEnStatut(1, "CLOTURE");
        mvc.perform(post("/api/dossiers/1/prise-en-charge").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsionJours\":2}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Prise en charge — prévision absente ou nulle refusée en 400")
    void priseEnCharge_previsionObligatoire() throws Exception {
        dossierEnStatut(1, "PRET_DISPATCH");
        mvc.perform(post("/api/dossiers/1/prise-en-charge").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/dossiers/1/prise-en-charge").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsionJours\":0}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ tolérance et clôture

    @Test
    @DisplayName("TOLÉRANCE — un geste métier sans prise en charge n'est pas bloqué : durée nulle et prévision STANDARD")
    void tolerance_gesteSansPriseEnCharge() throws Exception {
        // Réception COMPLET du dossier 2 (SOUMIS) : geste de clôture de l'étape RECEPTION, sans qu'aucun
        // Secrétaire ait cliqué « Prendre en charge ».
        dossierEnStatut(500, "SOUMIS");
        mvc.perform(post("/api/receptions").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":500,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":true}"))
                .andExpect(status().isCreated());

        TacheDossier tache = tacheRepository.findByIdDossierOrderByDatePriseEnChargeAsc(500).stream()
                .filter(t -> EtapeCircuit.RECEPTION.name().equals(t.getEtape())).findFirst().orElse(null);
        assertNotNull(tache, "la clôture doit créer l'occurrence même sans prise en charge");
        assertTrue(Boolean.TRUE.equals(tache.getPrevisionStandard()), "prévision reprise du référentiel");
        assertEquals(0L, JoursOuvres.ecoules(tache.getDatePriseEnCharge(), tache.getDateFin()));
        assertNotNull(tache.getDateFin(), "la tâche est close par le geste métier");
    }

    @Test
    @DisplayName("Clôture — la tâche prise en charge est fermée par le geste métier, la prévision saisie est conservée")
    void cloture_fermeLaTachePriseEnCharge() throws Exception {
        dossierEnStatut(500, "SOUMIS");
        // Le Secrétaire — ici le CC, qui peut exercer ses tâches — prend en charge puis réceptionne.
        prendreEnCharge(500, tokenCc, 3);
        mvc.perform(post("/api/receptions").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":500,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":true}"))
                .andExpect(status().isCreated());

        TacheDossier tache = tacheRepository.findByIdDossierOrderByDatePriseEnChargeAsc(500).get(0);
        assertEquals(EtapeCircuit.RECEPTION.name(), tache.getEtape());
        assertNotNull(tache.getDateFin());
        assertEquals(3, tache.getPrevisionJours());
        assertTrue(Boolean.FALSE.equals(tache.getPrevisionStandard()), "prévision saisie, pas standard");
    }

    // ------------------------------------------------------------------ date prévisionnelle

    @Test
    @DisplayName("Date annoncée DÈS LA SOUMISSION — somme des délais standards des étapes du compteur")
    void datePrevisionnelle_desLaSoumission() throws Exception {
        dossierEnStatut(500, "SOUMIS");
        // Seed : RECEPTION 1 + DISPATCH 1 + EXAMEN 5 + VISA 2 + COSIGNATURE 1 + VERIFICATION 3
        // + TRANSMISSION_SIGMP 1 = 14 jours ouvrés. ARCHIVAGE (2) est HORS compteur global.
        LocalDate attendue = JoursOuvres.ajouter(LocalDate.now(), 14);
        mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datePrevisionnelleFin").value(attendue.toString()))
                .andExpect(jsonPath("$.etapeCourante").value("RECEPTION"))
                .andExpect(jsonPath("$.attentePrmp").value(false));
    }

    @Test
    @DisplayName("La date GLISSE — une étape en dépassement compte 0, elle ne promet pas de rattrapage")
    void datePrevisionnelle_etapeEnDepassementCompteZero() throws Exception {
        dossierEnStatut(500, "SOUMIS");
        prendreEnCharge(500, tokenCc, 1);
        // Prise en charge reculée de 10 jours ouvrés : le reste de l'étape est largement négatif.
        TacheDossier tache = tacheRepository.findByIdDossierOrderByDatePriseEnChargeAsc(500).get(0);
        tache.setDatePriseEnCharge(java.time.LocalDateTime.now().minusDays(20));
        tacheRepository.save(tache);

        // RECEPTION compte 0 (dépassée) ; il reste 1+5+2+1+3+1 = 13 jours ouvrés.
        LocalDate attendue = JoursOuvres.ajouter(LocalDate.now(), 13);
        mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datePrevisionnelleFin").value(attendue.toString()));
    }

    @Test
    @DisplayName("Étapes déjà franchies exclues — un dossier EN_VERIFICATION ne recompte pas l'examen")
    void datePrevisionnelle_etapesFranchiesExclues() throws Exception {
        dossierEnStatut(500, "EN_VERIFICATION");
        // Restent VERIFICATION (3) + TRANSMISSION_SIGMP (1) = 4 jours ouvrés.
        LocalDate attendue = JoursOuvres.ajouter(LocalDate.now(), 4);
        mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datePrevisionnelleFin").value(attendue.toString()))
                .andExpect(jsonPath("$.etapeCourante").value("VERIFICATION"));
    }

    @Test
    @DisplayName("Hors circuit — brouillon, clôturé ou retiré : aucune date annoncée")
    void datePrevisionnelle_horsCircuit() throws Exception {
        for (String statut : new String[] { "BROUILLON", "CLOTURE", "RETIRE" }) {
            dossierEnStatut(500, statut);
            mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.datePrevisionnelleFin").doesNotExist())
                    .andExpect(jsonPath("$.etapeCourante").doesNotExist());
        }
    }

    // ------------------------------------------------------------------ attente PRMP

    @Test
    @DisplayName("Attente PRMP — le drapeau suit le STATUT COURANT, et la date reste annoncée")
    void attentePrmp_drapeauEtDateConservee() throws Exception {
        // Les trois statuts suspensifs de la cartographie validée.
        for (String statut : new String[] { "EN_ATTENTE_COMPLEMENTS_DEPOT", "EN_ATTENTE_PIECES",
                "EN_ATTENTE_DECISION_PRMP" }) {
            dossierEnStatut(500, statut);
            mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.attentePrmp").value(true))
                    .andExpect(jsonPath("$.datePrevisionnelleFin").exists())
                    .andExpect(jsonPath("$.etapeCourante").doesNotExist());
        }
    }

    @Test
    @DisplayName("Attente après observations non levées — la VÉRIFICATION reste à faire, elle sera rejouée")
    void attentePrmp_verificationRestantAJouer() throws Exception {
        dossierEnStatut(500, "EN_ATTENTE_DECISION_PRMP");
        // La reprise se fera en VERIFICATION : il reste 3 + 1 = 4 jours ouvrés, pas seulement 1.
        LocalDate attendue = JoursOuvres.ajouter(LocalDate.now(), 4);
        mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datePrevisionnelleFin").value(attendue.toString()));
    }

    @Test
    @DisplayName("Suspension — l'attente PRMP est ouverte à l'entrée, fermée à la resoumission, et sort du NET")
    void suspension_ouverteEtFermee() throws Exception {
        int idPv = 900;
        signerPvAvecAvis(idPv, "FAVR");
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        // Observation MAINTENUE → EN_ATTENTE_DECISION_PRMP : la fenêtre s'ouvre.
        passageObservationDossier1(tokenVer, "MAINTENUE", "a rectifier");
        assertEquals(1, suspensionRepository.findByIdDossierOrderByDebutAsc(1).size());
        assertTrue(suspensionRepository.findFirstByIdDossierAndFinIsNullOrderByDebutDesc(1).isPresent(),
                "la fenêtre reste ouverte tant que la PRMP n'a pas rendu la main");

        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk());
        assertTrue(suspensionRepository.findFirstByIdDossierAndFinIsNullOrderByDebutDesc(1).isEmpty(),
                "la resoumission referme la fenêtre");
    }

    // ------------------------------------------------------------------ étapes rejouables

    @Test
    @DisplayName("Étapes REJOUABLES — deux vérifications successives donnent DEUX occurrences, append-only")
    void etapesRejouables_occurrencesDistinctes() throws Exception {
        int idPv = 901;
        signerPvAvecAvis(idPv, "FAVR");
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");

        passageObservationDossier1(tokenVer, "MAINTENUE", "a rectifier");
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk());
        passageObservationDossier1(tokenVer, "LEVEE", null);

        java.util.List<TacheDossier> verifs = tacheRepository
                .findByIdDossierOrderByDatePriseEnChargeAsc(1).stream()
                .filter(t -> EtapeCircuit.VERIFICATION.name().equals(t.getEtape())).toList();
        assertEquals(2, verifs.size(), "chaque passage du Vérificateur est une occurrence distincte");
        assertEquals(1, verifs.get(0).getOccurrence());
        assertEquals(2, verifs.get(1).getOccurrence());
    }

    // ------------------------------------------------------------------ restitution

    @Test
    @DisplayName("GET /chronometrage — occurrences, compteurs, et NET = BRUT − attentes PRMP")
    void chronometrage_compteurs() throws Exception {
        int idPv = 902;
        signerPvAvecAvis(idPv, "FAVR");
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        passageObservationDossier1(tokenVer, "MAINTENUE", "a rectifier");

        String resp = mvc.perform(get("/api/dossiers/1/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDossier").value(1))
                .andExpect(jsonPath("$.attentePrmp").value(true))
                .andExpect(jsonPath("$.taches").isArray())
                .andReturn().getResponse().getContentAsString();

        int brut = com.jayway.jsonpath.JsonPath.read(resp, "$.dureeBruteJoursOuvres");
        int net = com.jayway.jsonpath.JsonPath.read(resp, "$.dureeNetteJoursOuvres");
        int attentes = com.jayway.jsonpath.JsonPath.read(resp, "$.attentePrmpJoursOuvres");
        assertEquals(brut - attentes, net, "le net CNM est le brut moins les attentes PRMP");
        assertTrue(net >= 0, "le net ne peut pas être négatif");
    }

    @Test
    @DisplayName("GET /chronometrage — la PRMP voit SON dossier ; une autre PRMP est refusée")
    void chronometrage_perimetre() throws Exception {
        mvc.perform(get("/api/dossiers/1/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());

        prmpRepository.save(prmp("PRMP002", "ANT"));
        String autrePrmp = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT");
        mvc.perform(get("/api/dossiers/1/chronometrage").header("Authorization", autrePrmp))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ utilitaires

    /** Force le statut d'un dossier existant (ou le crée pour le dossier 2, absent du socle). */
    private void dossierEnStatut(int idDossier, String statut) {
        Dossier d = dossierRepository.findById(idDossier).orElseGet(() -> {
            Dossier neuf = dossier(idDossier, statut);
            neuf.setIdPrmp("PRMP001");
            neuf.setIdLocalite("ANT");
            return neuf;
        });
        d.setStatut(statut);
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
    }

    private void prendreEnCharge(int idDossier, String token, int prevision) throws Exception {
        mvc.perform(post("/api/dossiers/" + idDossier + "/prise-en-charge").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"previsionJours\":" + prevision + "}"))
                .andExpect(status().isOk());
    }
}
