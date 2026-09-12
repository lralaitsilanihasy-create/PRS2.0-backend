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
import java.time.LocalDateTime;
import java.util.List;

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
 * ⚠️ <strong>Chronométrage et prévision des délais</strong> (règle du pilote, 2026-09-01 ; refonte du
 * 2026-09-12).
 *
 * <p>Ce que ces tests protègent en priorité : que le délai de chaque étape soit <strong>mesuré et
 * jamais saisi</strong> (fin − entrée, l'entrée étant dérivée de la transition précédente), que le
 * chronométrage <strong>n'empêche jamais le métier</strong>, que la date annoncée <strong>glisse au lieu
 * de mentir</strong>, et que les attentes PRMP ne soient <strong>imputées à personne</strong> à la
 * CNM.</p>
 */
class ChronometrageIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private TacheDossierRepository tacheRepository;

    @Autowired
    private SuspensionDossierRepository suspensionRepository;

    @Autowired
    private cnm.prs.service.ChronometrageService chronometrageService;

    // ------------------------------------------------------------------ référentiel des délais standards

    @Test
    @DisplayName("Référentiel — les HUIT étapes sont servies, dans l'ordre du circuit, avec leur délai")
    void referentiel_huitEtapes() throws Exception {
        mvc.perform(get("/api/delais-standards").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(8)))
                .andExpect(jsonPath("$[0].etape").value("RECEPTION"))
                .andExpect(jsonPath("$[7].etape").value("ARCHIVAGE"))
                .andExpect(jsonPath("$[2].etape").value("EXAMEN"));
    }

    @Test
    @DisplayName("Référentiel — réglage réservé à l'Administrateur ; délai < 1 refusé ; étape inconnue → 404")
    void referentiel_gardes() throws Exception {
        mvc.perform(put("/api/delais-standards/EXAMEN").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiHeures\":48}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/delais-standards/EXAMEN").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiHeures\":0}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/delais-standards/INCONNUE").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiHeures\":8}"))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/delais-standards/EXAMEN").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiHeures\":48}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delaiHeures").value(48));
    }

    // ------------------------------------------------------------------ plus de prise en charge (2026-09-12)

    @Test
    @DisplayName("⚠️ 2026-09-12 — POST /prise-en-charge n'existe plus : la route est inconnue, pour tous les profils")
    void priseEnCharge_routeSupprimee() throws Exception {
        dossierEnStatut(500, "PRET_DISPATCH");
        for (String token : new String[] { tokenCc, tokenPrmp, tokenAdmin }) {
            mvc.perform(post("/api/dossiers/500/prise-en-charge").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("⚠️ 2026-09-12 — le geste métier s'exécute DIRECTEMENT : la réception enregistre la fin de "
            + "RECEPTION sans qu'aucun Secrétaire ait eu à déclarer quoi que ce soit")
    void gesteMetier_sExecuteDirectement() throws Exception {
        dossierEnStatut(500, "SOUMIS");
        mvc.perform(post("/api/receptions").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":500,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":true}"))
                .andExpect(status().isCreated());

        TacheDossier passage = tacheRepository.findParDossier(500).stream()
                .filter(t -> EtapeCircuit.RECEPTION.name().equals(t.getEtape())).findFirst().orElse(null);
        assertNotNull(passage, "le geste métier enregistre à lui seul la fin de l'étape");
        assertNotNull(passage.getDateFin(), "une ligne n'existe que parce qu'un passage s'est achevé");
        assertEquals("CTRCC1", passage.getImActeur());
        assertEquals(1, passage.getOccurrence());
    }

    // ------------------------------------------------------------------ délai mesuré, jamais saisi

    @Test
    @DisplayName("⚠️ Délai AUTOMATIQUE — l'entrée d'une étape est la fin de la précédente : la durée de "
            + "DISPATCH court depuis la clôture de RECEPTION, sans aucune saisie")
    void duree_entreeDeriveeDeLaTransitionPrecedente() throws Exception {
        dossierEnStatut(500, "SOUMIS");
        chronometrageService.cloturer(500, EtapeCircuit.RECEPTION);
        dossierEnStatut(500, "PRET_DISPATCH");
        chronometrageService.cloturer(500, EtapeCircuit.DISPATCH);

        String corps = mvc.perform(get("/api/dossiers/500/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapes", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.etapes[0].etape").value("RECEPTION"))
                .andExpect(jsonPath("$.etapes[1].etape").value("DISPATCH"))
                // Le troisième est l'étape EN COURS, ouverte par la fin du dispatch.
                .andExpect(jsonPath("$.etapes[2].enCours").value(true))
                .andExpect(jsonPath("$.etapes[2].fin").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String finReception = com.jayway.jsonpath.JsonPath.read(corps, "$.etapes[0].fin");
        String entreeDispatch = com.jayway.jsonpath.JsonPath.read(corps, "$.etapes[1].entree");
        String finDispatch = com.jayway.jsonpath.JsonPath.read(corps, "$.etapes[1].fin");
        String entreeCourante = com.jayway.jsonpath.JsonPath.read(corps, "$.etapes[2].entree");
        assertEquals(finReception, entreeDispatch,
                "l'entrée du dispatch est EXACTEMENT la fin de la réception : une seule et même borne");
        assertEquals(finDispatch, entreeCourante, "et l'étape en cours entre à la fin du dispatch");
    }

    @Test
    @DisplayName("⚠️ GET /chronometrage — plus de « taches » ni d'« acteursAttendus » : les deux champs que "
            + "servait la prise en charge ont disparu avec elle")
    void chronometrage_champsDeLaPriseEnChargeRetires() throws Exception {
        mvc.perform(get("/api/dossiers/1/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taches").doesNotExist())
                .andExpect(jsonPath("$.acteursAttendus").doesNotExist())
                .andExpect(jsonPath("$.etapes").isArray())
                // ... et aucune prévision nulle part : plus rien ne se saisit.
                .andExpect(jsonPath("$..previsionHeures").isEmpty())
                .andExpect(jsonPath("$..previsionStandard").isEmpty());
    }

    @Test
    @DisplayName("⚠️ L'attente PRMP n'est imputée à PERSONNE — l'étape qui reprend entre à la SORTIE de "
            + "l'attente, pas à l'endroit où le dossier est parti")
    void duree_lAttentePrmpNeSImputePasALEtapeQuiReprend() throws Exception {
        dossierEnStatut(500, "EXAMINE");
        // Un examen clos il y a longtemps, puis une fenêtre d'attente PRMP fermée à l'instant.
        chronometrageService.cloturer(500, EtapeCircuit.EXAMEN);
        TacheDossier examen = tacheRepository.findParDossier(500).get(0);
        examen.setDateFin(LocalDateTime.now().minusDays(30));
        tacheRepository.save(examen);

        cnm.prs.entity.SuspensionDossier attente = new cnm.prs.entity.SuspensionDossier();
        attente.setIdSuspension(suspensionRepository.nextId());
        attente.setIdDossier(500);
        attente.setStatut("EN_ATTENTE_PIECES");
        attente.setDebut(LocalDateTime.now().minusDays(29));
        attente.setFin(LocalDateTime.now().minusHours(1));
        suspensionRepository.save(attente);

        // Le dossier revient à l'examen : l'occurrence en cours ne doit PAS porter les 30 jours d'attente.
        dossierEnStatut(500, "A_REEXAMINER");
        String corps = mvc.perform(get("/api/dossiers/500/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapes[1].enCours").value(true))
                .andExpect(jsonPath("$.etapes[1].etape").value("EXAMEN"))
                .andReturn().getResponse().getContentAsString();
        int duree = com.jayway.jsonpath.JsonPath.read(corps, "$.etapes[1].dureeHeuresOuvrees");
        assertTrue(duree <= 8, "le réexamen ne compte que depuis le retour du dossier, et non " + duree + " h");
    }

    // ------------------------------------------------------------------ date prévisionnelle

    @Test
    @DisplayName("Date annoncée DÈS LA SOUMISSION — somme des délais standards des étapes du compteur")
    void datePrevisionnelle_desLaSoumission() throws Exception {
        dossierEnStatut(500, "SOUMIS");
        // Seed converti x 8 : RECEPTION 8 + DISPATCH 8 + EXAMEN 40 + VISA 16 + COSIGNATURE 8 + VERIFICATION 24
        // + TRANSMISSION_SIGMP 8 = 112 h, soit 14 jours ouvrés — IDENTIQUE à avant la bascule d unité.
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
        // ⚠️ 2026-09-12 — plus de prise en charge à reculer : l'entrée dans la première étape est le DÉPÔT
        // du dossier. Déposé il y a vingt jours et toujours pas réceptionné, il est largement en dépassement.
        Dossier d = dossierRepository.findById(500).orElseThrow();
        d.setDateSoumission(LocalDateTime.now().minusDays(20));
        dossierRepository.save(d);

        // RECEPTION compte 0 (dépassée) ; il reste 8+40+16+8+24+8 = 104 h, soit 13 jours ouvrés.
        LocalDate attendue = JoursOuvres.ajouter(LocalDate.now(), 13);
        mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datePrevisionnelleFin").value(attendue.toString()));
    }

    @Test
    @DisplayName("Étapes déjà franchies exclues — un dossier EN_VERIFICATION ne recompte pas l'examen")
    void datePrevisionnelle_etapesFranchiesExclues() throws Exception {
        dossierEnStatut(500, "EN_VERIFICATION");
        // Restent VERIFICATION (24 h) + TRANSMISSION_SIGMP (8 h) = 32 h, soit 4 jours ouvrés.
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
        // Les statuts d'attente de PIÈCES : personne n'est nommément attendu, aucune étape n'est portée.
        for (String statut : new String[] { "EN_ATTENTE_COMPLEMENTS_DEPOT", "EN_ATTENTE_PIECES" }) {
            dossierEnStatut(500, statut);
            mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.attentePrmp").value(true))
                    .andExpect(jsonPath("$.datePrevisionnelleFin").exists())
                    .andExpect(jsonPath("$.etapeCourante").doesNotExist());
        }

        // ⚠️ EN_ATTENTE_DECISION_PRMP porte une étape NOMMÉE, RECTIFICATION_PRMP : l'attente reste
        // suspensive (drapeau et date inchangés, l'étape est hors compteur), mais elle a un porteur — et
        // c'est ce qui donne à la PRMP un délai mesuré, à côté de ceux de la Commission.
        dossierEnStatut(500, "EN_ATTENTE_DECISION_PRMP");
        mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attentePrmp").value(true))
                .andExpect(jsonPath("$.datePrevisionnelleFin").exists())
                .andExpect(jsonPath("$.etapeCourante").value("RECTIFICATION_PRMP"));
    }

    @Test
    @DisplayName("Attente après observations non levées — la VÉRIFICATION reste à faire, elle sera rejouée")
    void attentePrmp_verificationRestantAJouer() throws Exception {
        dossierEnStatut(500, "EN_ATTENTE_DECISION_PRMP");
        // La reprise se fera en VERIFICATION : il reste 24 + 8 = 32 h, soit 4 jours ouvrés, pas seulement 1.
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
        // ⚠️ Réordonnancement FAVR (2026-09-07) — une PREMIÈRE fenêtre s'ouvre dès la co-signature (les
        // réserves partent à la PRMP) et se ferme à sa resoumission ; l'observation MAINTENUE en ouvre
        // une SECONDE. Deux attentes PRMP successives, donc deux fenêtres — et c'est fidèle.
        passageObservationDossier1(tokenVer, "MAINTENUE", "a rectifier");
        assertEquals(2, suspensionRepository.findByIdDossierOrderByDebutAsc(1).size());
        assertTrue(suspensionRepository.findFirstByIdDossierAndFinIsNullOrderByDebutDesc(1).isPresent(),
                "la fenêtre reste ouverte tant que la PRMP n'a pas rendu la main");

        resoumettreDossier(1, "corrige");
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
        resoumettreDossier(1, "corrige");
        passageObservationDossier1(tokenVer, "LEVEE", null);

        java.util.List<TacheDossier> verifs = tacheRepository.findParDossier(1).stream()
                .filter(t -> EtapeCircuit.VERIFICATION.name().equals(t.getEtape())).toList();
        assertEquals(2, verifs.size(), "chaque passage du Vérificateur est une occurrence distincte");
        assertEquals(1, verifs.get(0).getOccurrence());
        assertEquals(2, verifs.get(1).getOccurrence());
    }

    @Test
    @DisplayName("⚠️ Le délai de la PRMP est mesuré à part — la RECTIFICATION_PRMP a son propre passage, "
            + "clos par la resoumission, et reste hors compteur global")
    void rectificationPrmp_passageMesureSansAucuneSaisie() throws Exception {
        int idPv = 903;
        signerPvAvecAvis(idPv, "FAVR");
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        passageObservationDossier1(tokenVer, "MAINTENUE", "a rectifier");

        resoumettreDossier(1, "corrige");

        java.util.List<TacheDossier> rectifs = tacheRepository.findParDossier(1).stream()
                .filter(t -> EtapeCircuit.RECTIFICATION_PRMP.name().equals(t.getEtape())).toList();
        assertTrue(!rectifs.isEmpty(), "la resoumission enregistre la fin de l'étape de la PRMP");
        assertNotNull(rectifs.get(rectifs.size() - 1).getDateFin());
        assertEquals("PRMP001", rectifs.get(rectifs.size() - 1).getImActeur());
    }

    // ------------------------------------------------------------------ restitution

    @Test
    @DisplayName("GET /chronometrage — passages, compteurs en HEURES ouvrées, et NET = BRUT − attentes PRMP")
    void chronometrage_compteurs() throws Exception {
        int idPv = 902;
        signerPvAvecAvis(idPv, "FAVR");
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        passageObservationDossier1(tokenVer, "MAINTENUE", "a rectifier");

        String resp = mvc.perform(get("/api/dossiers/1/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDossier").value(1))
                .andExpect(jsonPath("$.attentePrmp").value(true))
                .andExpect(jsonPath("$.etapes").isArray())
                .andReturn().getResponse().getContentAsString();

        int brut = com.jayway.jsonpath.JsonPath.read(resp, "$.dureeBruteHeuresOuvrees");
        int net = com.jayway.jsonpath.JsonPath.read(resp, "$.dureeNetteHeuresOuvrees");
        int attentes = com.jayway.jsonpath.JsonPath.read(resp, "$.attentePrmpHeuresOuvrees");
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

    @Test
    @DisplayName("Migration × 8 — le référentiel est en HEURES : le seed d'hier converti, pas réinitialisé")
    void referentiel_migreEnHeures() throws Exception {
        // V15 a multiplié par 8 les valeurs stockées (1/1/5/2/1/3/1/2 jours → 8/8/40/16/8/24/8/16 h).
        mvc.perform(get("/api/delais-standards").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].delaiHeures").value(8))    // RECEPTION
                .andExpect(jsonPath("$[1].delaiHeures").value(8))    // DISPATCH
                .andExpect(jsonPath("$[2].delaiHeures").value(40))   // EXAMEN
                .andExpect(jsonPath("$[3].delaiHeures").value(16))   // VISA
                .andExpect(jsonPath("$[4].delaiHeures").value(8))    // COSIGNATURE
                .andExpect(jsonPath("$[5].delaiHeures").value(24))   // VERIFICATION
                .andExpect(jsonPath("$[6].delaiHeures").value(8))    // TRANSMISSION_SIGMP
                .andExpect(jsonPath("$[7].delaiHeures").value(16));  // ARCHIVAGE
    }

    @Test
    @DisplayName("⚠️ Durée à la MÊME échelle — une étape entrée hier matin et close ce matin vaut 8 h "
            + "ouvrées (et non 24 h d'horloge) : la mesure reste dans l'échelle du délai standard")
    void duree_enHeuresOuvrees_pasEnHeuresDHorloge() throws Exception {
        dossierEnStatut(500, "PRET_DISPATCH");
        chronometrageService.cloturer(500, EtapeCircuit.RECEPTION);
        chronometrageService.cloturer(500, EtapeCircuit.DISPATCH);

        // Deux jours OUVRÉS consécutifs, à la même heure : en heures d'horloge l'écart vaut 24 h (72 h
        // par-dessus un week-end), et le dispatch paraîtrait en dépassement de son délai standard de 8 h.
        LocalDateTime jour2 = jourOuvre(LocalDateTime.now().withHour(9).withMinute(0).withSecond(0).withNano(0));
        LocalDateTime jour1 = veilleOuvree(jour2);
        List<TacheDossier> passages = tacheRepository.findParDossier(500);
        passages.get(0).setDateFin(jour1);
        passages.get(1).setDateFin(jour2);
        tacheRepository.saveAll(passages);

        String corps = mvc.perform(get("/api/dossiers/500/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapes[1].etape").value("DISPATCH"))
                .andReturn().getResponse().getContentAsString();
        int duree = com.jayway.jsonpath.JsonPath.read(corps, "$.etapes[1].dureeHeuresOuvrees");
        assertEquals(8, duree, "7 h la veille (09:00 → 16:00) + 1 h le lendemain (08:00 → 09:00)");
    }

    /** Dernier jour OUVRÉ à cette heure-là : le week-end n'est pas un jour de service. */
    private static LocalDateTime jourOuvre(LocalDateTime instant) {
        LocalDateTime jour = instant;
        while (!JoursOuvres.estOuvre(jour.toLocalDate())) {
            jour = jour.minusDays(1);
        }
        return jour;
    }

    @Test
    @DisplayName("Arrondi au jour SUPÉRIEUR — 9 h restantes tiennent sur 2 jours ouvrés, pas 1")
    void datePrevisionnelle_arrondiSuperieur() throws Exception {
        dossierEnStatut(500, "OBSERVATIONS_LEVEES");   // seule TRANSMISSION_SIGMP reste
        // Le référentiel donne 8 h à cette étape → 1 jour. Porté à 9 h, il en faut 2.
        mvc.perform(put("/api/delais-standards/TRANSMISSION_SIGMP").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"delaiHeures\":9}"))
                .andExpect(status().isOk());

        LocalDate attendue = JoursOuvres.ajouter(LocalDate.now(), 2);
        mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datePrevisionnelleFin").value(attendue.toString()));
    }

    /** Veille OUVRÉE d'un instant : recule d'un jour, puis saute le week-end. */
    private static LocalDateTime veilleOuvree(LocalDateTime instant) {
        LocalDateTime veille = instant.minusDays(1);
        while (!JoursOuvres.estOuvre(veille.toLocalDate())) {
            veille = veille.minusDays(1);
        }
        return veille;
    }

    // ------------------------------------------------------------------ attributaire courant

    @Test
    @DisplayName("⚠️ GET /chronometrage — « attributaire » suit la RÉATTRIBUTION : c'est le titulaire courant, "
            + "pas le premier assigné")
    void chronometrage_attributaire_suitLaReattribution() throws Exception {
        // Le dossier 1 est dispatché à CTRMEM par la fixture.
        mvc.perform(get("/api/dossiers/1/chronometrage").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributaire").value("CTRMEM"));

        // Réattribution : le dispatch ne garde que son dernier état, et c'est bien celui-là que le
        // chronométrage doit servir — c'est aussi lui qui portera l'examen quand il sera clos.
        controleurRepository.save(controleur("MEMANT7", 5, "ANT"));
        var dispatch = dispatchRepository.findById(1).orElseThrow();
        dispatch.setImCtrlMembre("MEMANT7");
        dispatchRepository.save(dispatch);

        mvc.perform(get("/api/dossiers/1/chronometrage").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributaire").value("MEMANT7"));

        // ⚠️ L'étape EXAMEN en cours est servie au nom du titulaire COURANT : c'est lui qu'elle mesure.
        var d = dossierRepository.findById(1).orElseThrow();
        d.setStatut("DISPATCHE");
        dossierRepository.save(d);
        mvc.perform(get("/api/dossiers/1/chronometrage").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapeCourante").value("EXAMEN"))
                .andExpect(jsonPath("$.etapes[-1:].imActeur").value("MEMANT7"));
    }

    @Test
    @DisplayName("GET /chronometrage — dossier NON dispatché : « attributaire » est null, pas une chaîne vide")
    void chronometrage_attributaire_nullSansDispatch() throws Exception {
        // Un dossier en cours de réception n'a pas de dispatch : personne n'est attributaire, et le
        // front doit pouvoir le distinguer d'un matricule inconnu.
        dossierEnStatut(4700, "SOUMIS");

        mvc.perform(get("/api/dossiers/4700/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapeCourante").value("RECEPTION"))
                .andExpect(jsonPath("$.attributaire").doesNotExist());
    }

    // ------------------------------------------------------------------ date d'enregistrement (2026-09-06)

    /**
     * ⚠️ Suivi des délais CNM (demande pilote 2026-09-06) — le tableau de bord PRMP affiche « référence ·
     * enregistrement · fin prévue » ; {@code GET /api/receptions} est vide pour la PRMP et le chronométrage
     * par dossier serait un N+1. Le {@code DossierDto} sert donc {@code dateEnregistrement}, résolue en lot.
     */
    @Test
    @DisplayName("Date d'enregistrement — dossier réceptionné : dateEnregistrement = clôture de RECEPTION, "
            + "identique au debutCompteur du chronométrage")
    void dateEnregistrement_dossierReceptionne_egaleAuDebutCompteur() throws Exception {
        dossierEnStatut(500, "SOUMIS");
        mvc.perform(post("/api/receptions").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":500,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":true}"))
                .andExpect(status().isCreated());

        String chrono = mvc.perform(get("/api/dossiers/500/chronometrage").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debutCompteur").exists())
                .andReturn().getResponse().getContentAsString();
        String debutCompteur = com.jayway.jsonpath.JsonPath.read(chrono, "$.debutCompteur");
        assertNotNull(debutCompteur);

        mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateEnregistrement").value(debutCompteur));
    }

    @Test
    @DisplayName("Date d'enregistrement — dossier soumis non réceptionné : null (la date prévisionnelle, elle, est déjà là)")
    void dateEnregistrement_nonReceptionne_null() throws Exception {
        dossierEnStatut(500, "SOUMIS");
        mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateEnregistrement").doesNotExist())
                .andExpect(jsonPath("$.datePrevisionnelleFin").exists());
    }

    @Test
    @DisplayName("Date d'enregistrement — la PRMP la lit sur SES dossiers via la liste GET /api/dossiers, "
            + "sans que la portée de GET /api/receptions ne s'élargisse (toujours vide pour elle)")
    void dateEnregistrement_visibleSurLaListePrmp_receptionsToujoursVides() throws Exception {
        dossierEnStatut(500, "SOUMIS");
        mvc.perform(post("/api/receptions").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":500,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":true}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==500)].dateEnregistrement", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==500)].dateEnregistrement[0]", org.hamcrest.Matchers.notNullValue()));
        // Aucun élargissement de portée : la liste des réceptions reste vide pour la PRMP.
        mvc.perform(get("/api/receptions").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    // ------------------------------------------------------------------ dates des étapes de la frise (2026-09-07)

    /**
     * ⚠️ Frise du tableau de bord (demande pilote 2026-09-07) — le front datait chaque point par jointure de
     * listes que la portée du lecteur rend vides (Président « toutes localités ») : {@code datesEtapes} sert
     * la date de franchissement de chaque étape depuis le dossier lui-même, dérivée des passages en lot.
     */
    @Test
    @DisplayName("Dates des étapes — dossier examiné (projet de PV) : RECEPTION, DISPATCH, EXAMEN, PROJET_PV datés, "
            + "PV_SIGNE / VERIFICATION / CLOTURE absents")
    void datesEtapes_dossierExamine() throws Exception {
        dossierEnStatut(500, "EXAMINE");
        chronometrageService.cloturer(500, EtapeCircuit.RECEPTION);
        chronometrageService.cloturer(500, EtapeCircuit.DISPATCH);
        chronometrageService.cloturer(500, EtapeCircuit.EXAMEN);

        mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datesEtapes.RECEPTION").exists())
                .andExpect(jsonPath("$.datesEtapes.DISPATCH").exists())
                .andExpect(jsonPath("$.datesEtapes.EXAMEN").exists())
                .andExpect(jsonPath("$.datesEtapes.PROJET_PV").exists())
                .andExpect(jsonPath("$.datesEtapes.PV_SIGNE").doesNotExist())
                .andExpect(jsonPath("$.datesEtapes.VERIFICATION").doesNotExist())
                .andExpect(jsonPath("$.datesEtapes.CLOTURE").doesNotExist());
    }

    @Test
    @DisplayName("Dates des étapes — le franchissement se juge sur le STATUT : un dispatch annulé (PRET_DISPATCH) "
            + "laisse ses passages derrière lui mais ne date ni DISPATCH ni EXAMEN")
    void datesEtapes_dispatchAnnule_nonFranchi() throws Exception {
        dossierEnStatut(500, "EXAMINE");
        chronometrageService.cloturer(500, EtapeCircuit.RECEPTION);
        chronometrageService.cloturer(500, EtapeCircuit.DISPATCH);
        chronometrageService.cloturer(500, EtapeCircuit.EXAMEN);
        // Annulation du dispatch : retour en PRET_DISPATCH, les passages restent (histoire).
        dossierEnStatut(500, "PRET_DISPATCH");

        mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datesEtapes.RECEPTION").exists())
                .andExpect(jsonPath("$.datesEtapes.DISPATCH").doesNotExist())
                .andExpect(jsonPath("$.datesEtapes.EXAMEN").doesNotExist())
                .andExpect(jsonPath("$.datesEtapes.PROJET_PV").doesNotExist());
    }

    @Test
    @DisplayName("Dates des étapes — cohérence : datesEtapes.RECEPTION = dateEnregistrement, et un dossier jamais "
            + "réceptionné n'a aucune étape datée")
    void datesEtapes_receptionEgaleDateEnregistrement() throws Exception {
        dossierEnStatut(500, "SOUMIS");
        mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.datesEtapes.RECEPTION").doesNotExist())
                .andExpect(jsonPath("$.dateEnregistrement").doesNotExist());

        chronometrageService.cloturer(500, EtapeCircuit.RECEPTION);
        String corps = mvc.perform(get("/api/dossiers/500").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateEnregistrement").exists())
                .andReturn().getResponse().getContentAsString();
        String enregistrement = com.jayway.jsonpath.JsonPath.read(corps, "$.dateEnregistrement");
        assertEquals(enregistrement, com.jayway.jsonpath.JsonPath.read(corps, "$.datesEtapes.RECEPTION"));
    }

    @Test
    @DisplayName("Dates des étapes — le Président les lit sur GET /api/dossiers, sans dépendre des listes "
            + "dispatchs / examens (portée inchangée)")
    void datesEtapes_surLaListeDuPresident() throws Exception {
        dossierEnStatut(500, "DISPATCHE");
        chronometrageService.cloturer(500, EtapeCircuit.RECEPTION);
        chronometrageService.cloturer(500, EtapeCircuit.DISPATCH);
        String tokenPresident = bearer("CTRPRE", ProfilUtilisateur.PRESIDENT, TypeActeur.CONTROLEUR, "CTRPRE", "ANT");

        mvc.perform(get("/api/dossiers").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==500)].datesEtapes.RECEPTION", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==500)].datesEtapes.DISPATCH", org.hamcrest.Matchers.hasSize(1)))
                // Étape non atteinte : la clé est servie avec la valeur null (contrat de la demande).
                .andExpect(jsonPath("$[?(@.idDossier==500)].datesEtapes.EXAMEN", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())));
        // Aucune réception, aucun dispatch, aucun examen n'existe pour ce dossier dans la fixture : la
        // date ne peut venir que des passages du chronométrage, pas d'une jointure sur ces listes.
        assertTrue(receptionRepository.findByIdDossier(500).isEmpty(), "fixture sans réception");
    }

    // ------------------------------------------------------------------ utilitaires

    /** Force le statut d'un dossier existant (ou le crée pour le dossier 500, absent du socle). */
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
}
