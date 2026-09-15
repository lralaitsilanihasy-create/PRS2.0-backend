package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import cnm.prs.entity.Controleur;
import cnm.prs.entity.PvExamen;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.service.ChronometrageService;

/**
 * ⚠️ <strong>Audit 2026-09-14 (C2) — ce que reçoivent la PRMP et l'UGPM.</strong>
 *
 * <p>Deux règles écrites étaient trahies par le serveur, le front se contentant de masquer : les « vues
 * internes CNM » (demande pilote du 2026-09-06 — journal et chronométrage réservés à la Commission) et le
 * secret de l'intérim (la note est refusée à la PRMP « pour que l'extérieur ne l'apprenne pas »). Le filtrage
 * est désormais fait côté serveur, en un point de projection par ressource, pour
 * {@code Visibilite.estPrmp()} — qui couvre la PRMP <strong>et</strong> l'UGPM :</p>
 * <ol>
 *   <li>{@code GET /api/dossiers/{id}/journal} → <strong>403</strong> ;</li>
 *   <li>{@code GET /api/dossiers/{id}/chronometrage} → servi <strong>sans identités</strong> ;</li>
 *   <li>{@code PvExamenDto} → ni intérim ni dispatcheur ;</li>
 *   <li>{@code DossierDto} → ni cibles Vérificateur/Assistant, ni {@code acteursEtapes} ;</li>
 *   <li>⚠️ revue du 2026-09-14 — {@code GET /api/observations-pv} → sans l'auteur des décisions de
 *       vérification ({@code historique[].imVerificateur}).</li>
 * </ol>
 * <p>Chaque chemin est éprouvé avec un jeton PRMP, l'UGPM au moins une fois par chemin, et un contrôleur
 * qui, lui, reçoit toujours les champs.</p>
 */
class VuesInternesPrmpIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private ChronometrageService chronometrageService;
    @Autowired
    private cnm.prs.repository.ObservationPvRepository observationPvRepository;
    @Autowired
    private cnm.prs.repository.SuiviObservationRepository suiviObservationRepository;

    /** Agent UGPM sous la tutelle de PRMP001 : son jeton porte la ref de sa tutelle. */
    private String tokenUgpm;

    /**
     * Dossier 1 du socle (ANT, PPM de PRMP001, dispatché par CTRPRE à CTRMEM), porté EN_VERIFICATION : des
     * passages nominatifs, des rattachements Membre → Vérificateur → Assistant, et un PV SIGNÉ visé par
     * intérim.
     */
    @BeforeEach
    void dossierTraiteParLaCnm() {
        ugpmRepository.save(ugpm("UGPM014", "PRMP001", "RAKOTO", "Hery"));
        tokenUgpm = bearer("ugpm.hery", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", null);

        cnm.prs.entity.Dossier dossier = dossierRepository.findById(1).orElseThrow();
        dossier.setStatut("EN_VERIFICATION");   // PV signé : le dossier est chez le vérificateur
        dossier.setIdPrmp("PRMP001");
        dossierRepository.save(dossier);

        chronometrageService.cloturerPourActeur(1, EtapeCircuit.RECEPTION, "CTRSEC");
        chronometrageService.cloturerPourActeur(1, EtapeCircuit.DISPATCH, "CTRPRE");
        chronometrageService.cloturerPourActeur(1, EtapeCircuit.EXAMEN, "CTRMEM");

        rattacher("CTRMEM", "CTRVER");
        rattacher("CTRVER", "CTRASS");

        seedPvSigne(9801, 1);
        PvExamen pv = pvExamenRepository.findById(9801).orElseThrow();
        pv.setImCtrlCc("CTRCC1");                        // le CC a visé en suppléant le Président dispatcheur
        pv.setDateSignatureCc(java.time.LocalDate.now());
        pv.setViseParInterim(true);
        pv.setNoteInterim(pdfMinimal());
        pv.setNoteInterimNom("absence-du-president.pdf");
        pv.setNoteInterimTaille((long) pdfMinimal().length);
        pvExamenRepository.save(pv);
    }

    private void rattacher(String porteur, String rattache) {
        Controleur c = controleurRepository.findById(porteur).orElseThrow();
        c.setImRattache(rattache);
        controleurRepository.save(c);
    }

    // ------------------------------------------------------------------ 1. journal

    @Test
    @DisplayName("C2.1 — journal : 403 pour la PRMP et pour l'UGPM ; un contrôleur le lit toujours (200)")
    void journal_refusePrmpEtUgpm_serviAuControleur() throws Exception {
        mvc.perform(get("/api/dossiers/1/journal").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dossiers/1/journal").header("Authorization", tokenUgpm))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dossiers/1/journal").header("Authorization", tokenCc))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 2. chronométrage

    @Test
    @DisplayName("C2.2 — chronométrage : PRMP et UGPM reçoivent étapes, dates, durées, profils et fin prévue, "
            + "SANS imActeur, nomActeur ni attributaire ; le contrôleur reçoit les identités")
    void chronometrage_sansIdentitesPourPrmpEtUgpm() throws Exception {
        for (String token : new String[] { tokenPrmp, tokenUgpm }) {
            mvc.perform(get("/api/dossiers/1/chronometrage").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.etapes", hasSize(4)))   // trois passages + l'étape en cours
                    .andExpect(jsonPath("$.etapes[*].imActeur", everyItem(nullValue())))
                    .andExpect(jsonPath("$.etapes[*].nomActeur", everyItem(nullValue())))
                    .andExpect(jsonPath("$.attributaire").value(nullValue()))
                    // Ce qui reste servi : le widget compact de la PRMP (étape courante + fin prévue) et la frise.
                    .andExpect(jsonPath("$.etapes[*].etape", hasItem("RECEPTION")))
                    .andExpect(jsonPath("$.etapes[*].profil", everyItem(notNullValue())))
                    .andExpect(jsonPath("$.etapes[0].fin").value(notNullValue()))
                    .andExpect(jsonPath("$.etapeCourante").value("VERIFICATION"))
                    .andExpect(jsonPath("$.datePrevisionnelleFin").value(notNullValue()))
                    .andExpect(jsonPath("$.attentePrmp").value(false))
                    .andExpect(jsonPath("$.dureeBruteHeuresOuvrees").value(notNullValue()));
        }
        mvc.perform(get("/api/dossiers/1/chronometrage").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapes[*].imActeur", hasItem("CTRSEC")))
                .andExpect(jsonPath("$.etapes[*].nomActeur", hasItem("Prenoms NomCTRSEC")))
                .andExpect(jsonPath("$.attributaire").value("CTRMEM"));
    }

    // ------------------------------------------------------------------ 3. PvExamenDto

    @Test
    @DisplayName("C2.3 — PV signé : ni intérim ni dispatcheur pour la PRMP et l'UGPM, sur /{id} comme sur "
            + "/definitifs ; les signataires officiels restent servis ; le contrôleur reçoit tout")
    void pvExamen_sansInterimNiDispatcheurPourPrmpEtUgpm() throws Exception {
        for (String token : new String[] { tokenPrmp, tokenUgpm }) {
            mvc.perform(get("/api/pv-examens/9801").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.viseParInterim").value(nullValue()))
                    .andExpect(jsonPath("$.noteInterimNom").value(nullValue()))
                    .andExpect(jsonPath("$.noteInterimDisponible").value(nullValue()))
                    .andExpect(jsonPath("$.imDispatcheur").value(nullValue()))
                    .andExpect(jsonPath("$.nomDispatcheur").value(nullValue()))
                    // L'acte signé, lui, reste lisible : ses signataires y figurent.
                    .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                    .andExpect(jsonPath("$.imCtrlMembre").value("CTRMEM"))
                    .andExpect(jsonPath("$.imCtrlCc").value("CTRCC1"));
            mvc.perform(get("/api/pv-examens/definitifs").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.idPv==9801)]", hasSize(1)))
                    .andExpect(jsonPath("$[?(@.idPv==9801)].viseParInterim", everyItem(nullValue())))
                    .andExpect(jsonPath("$[?(@.idPv==9801)].noteInterimNom", everyItem(nullValue())))
                    .andExpect(jsonPath("$[?(@.idPv==9801)].noteInterimDisponible", everyItem(nullValue())))
                    .andExpect(jsonPath("$[?(@.idPv==9801)].imDispatcheur", everyItem(nullValue())))
                    .andExpect(jsonPath("$[?(@.idPv==9801)].nomDispatcheur", everyItem(nullValue())));
        }
        mvc.perform(get("/api/pv-examens/9801").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viseParInterim").value(true))
                .andExpect(jsonPath("$.noteInterimNom").value("absence-du-president.pdf"))
                .andExpect(jsonPath("$.noteInterimDisponible").value(true))
                .andExpect(jsonPath("$.imDispatcheur").value("CTRPRE"))
                .andExpect(jsonPath("$.nomDispatcheur").value("Prenoms NomCTRPRE"));
    }

    // ------------------------------------------------------------------ 4. DossierDto

    @Test
    @DisplayName("C2.4 — DossierDto (unitaire et liste) : ni cibles Vérificateur/Assistant ni acteursEtapes pour "
            + "la PRMP et l'UGPM ; dates et attentePrmp conservées ; le contrôleur reçoit les champs")
    void dossierDto_sansActeursInternesPourPrmpEtUgpm() throws Exception {
        for (String token : new String[] { tokenPrmp, tokenUgpm }) {
            mvc.perform(get("/api/dossiers/1").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imVerificateurCible").value(nullValue()))
                    .andExpect(jsonPath("$.nomVerificateurCible").value(nullValue()))
                    .andExpect(jsonPath("$.imAssistantCible").value(nullValue()))
                    .andExpect(jsonPath("$.nomAssistantCible").value(nullValue()))
                    .andExpect(jsonPath("$.acteursEtapes").value(nullValue()))
                    .andExpect(jsonPath("$.datesEtapes.RECEPTION").value(notNullValue()))
                    .andExpect(jsonPath("$.dateEnregistrement").value(notNullValue()))
                    .andExpect(jsonPath("$.datePrevisionnelleFin").value(notNullValue()))
                    .andExpect(jsonPath("$.attentePrmp").value(false));
            mvc.perform(get("/api/dossiers").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)))
                    .andExpect(jsonPath("$[?(@.idDossier==1)].imVerificateurCible", everyItem(nullValue())))
                    .andExpect(jsonPath("$[?(@.idDossier==1)].nomAssistantCible", everyItem(nullValue())))
                    .andExpect(jsonPath("$[?(@.idDossier==1)].acteursEtapes", everyItem(nullValue())))
                    .andExpect(jsonPath("$[?(@.idDossier==1)].datesEtapes", everyItem(not(nullValue()))));
        }
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imVerificateurCible").value("CTRVER"))
                .andExpect(jsonPath("$.nomVerificateurCible").value("Prenoms NomCTRVER"))
                .andExpect(jsonPath("$.imAssistantCible").value("CTRASS"))
                .andExpect(jsonPath("$.nomAssistantCible").value("Prenoms NomCTRASS"))
                .andExpect(jsonPath("$.acteursEtapes.RECEPTION").value("Prenoms NomCTRSEC"));
    }

    // ------------------------------------------------------------------ 5. observations du PV

    @Test
    @DisplayName("C2.5 — observations-pv : PRMP et UGPM reçoivent statut, précision, itérations et dates, SANS "
            + "l'auteur des décisions (historique[].imVerificateur) ; aucun matricule ni nom de contrôleur dans le "
            + "corps brut ; le contrôleur reçoit l'auteur")
    void observationsPv_sansVerificateurPourPrmpEtUgpm() throws Exception {
        observationMaintenuePuisLevee();

        for (String token : new String[] { tokenPrmp, tokenUgpm }) {
            String corps = mvc.perform(get("/api/observations-pv").param("dossier", "1")
                            .header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].historique", hasSize(2)))
                    .andExpect(jsonPath("$[0].historique[*].imVerificateur", everyItem(nullValue())))
                    // Ce qui reste servi : l'avancement de la rectification, pas qui l'a jugée.
                    .andExpect(jsonPath("$[0].statut").value("LEVEE"))
                    .andExpect(jsonPath("$[0].historique[*].decision", contains("MAINTENUE", "LEVEE")))
                    .andExpect(jsonPath("$[0].historique[0].precision").value("piece justificative manquante"))
                    .andExpect(jsonPath("$[0].historique[*].dateDecision", everyItem(notNullValue())))
                    .andReturn().getResponse().getContentAsString();
            // Corps brut : aucune identité interne, quel que soit le champ qui la porterait.
            for (Controleur c : controleurRepository.findAll()) {
                assertThat(corps).as("matricule d'un contrôleur").doesNotContain(c.getImControleur());
                assertThat(corps).as("nom d'un contrôleur").doesNotContain(c.getNomCont());
            }
        }
        mvc.perform(get("/api/observations-pv").param("dossier", "1").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].historique[*].imVerificateur", contains("CTRVER", "CTRVER")));
    }

    /**
     * Une observation du PV 9801 sur le dossier 1, maintenue par le vérificateur CTRVER à la première itération
     * puis levée à la seconde. Libellé et précision sans identité : c'est le serveur qui ne doit rien ajouter.
     */
    private void observationMaintenuePuisLevee() {
        cnm.prs.entity.ObservationPv observation = new cnm.prs.entity.ObservationPv();
        observation.setIdDossier(1);
        observation.setIdPv(9801);
        observation.setSource("POINT");
        observation.setLibelle("Montant : au lieu de « 500 000 », lire « 5 000 000 »");
        observation.setOrdre(1);
        int id = observationPvRepository.save(observation).getIdObservationPv();
        decision(id, 1, "MAINTENUE", "piece justificative manquante");
        decision(id, 2, "LEVEE", null);
    }

    private void decision(int idObservationPv, int iteration, String decision, String precision) {
        cnm.prs.entity.SuiviObservation suivi = new cnm.prs.entity.SuiviObservation();
        suivi.setIdObservationPv(idObservationPv);
        suivi.setIteration(iteration);
        suivi.setDecision(decision);
        suivi.setPrecision(precision);
        suivi.setImVerificateur("CTRVER");
        suivi.setDateDecision(java.time.LocalDateTime.of(2026, 9, 10 + iteration, 9, 0));
        suiviObservationRepository.save(suivi);
    }
}
