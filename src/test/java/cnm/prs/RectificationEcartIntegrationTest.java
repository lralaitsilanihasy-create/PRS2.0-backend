package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import cnm.prs.entity.Capm;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.Marche;
import cnm.prs.entity.Nature;
import cnm.prs.entity.ObservationPv;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.SuiviObservation;
import cnm.prs.repository.ObservationPvRepository;
import cnm.prs.repository.SuiviObservationRepository;

/**
 * ⚠️ <strong>Rectification avec écart de structure toléré</strong> (règle pilote du 2026-09-06, relayée par
 * le front — {@code docs/demande-backend-2026-09-06-rectification-ecart-3-lignes.md}) : « il est interdit
 * d'ajouter ou de retirer PLUS DE 3 lignes du PPM à rectifier ». La structure n'est plus strictement
 * figée ; l'écart est permis, borné à 3 dans chaque sens, et une ligne portant une observation du PV non
 * levée ne se retire pas.
 *
 * <p>Le dossier est posé directement en {@code EN_ATTENTE_DECISION_PRMP} avec cinq lignes : ce qui est
 * éprouvé est la façade (garde d'écart, créations, retraits en cascade, archivage, diff), pas le circuit
 * qui y mène. Les quatre tests attendus par la demande sont ici.</p>
 */
class RectificationEcartIntegrationTest extends CnmIntegrationTestSupport {

    private static final int DOSSIER = 800;
    private static final int PPM = 800;
    private static final int[] LIGNES = { 8001, 8002, 8003, 8004, 8005 };

    @Autowired
    private ObservationPvRepository observationPvRepository;
    @Autowired
    private SuiviObservationRepository suiviObservationRepository;

    @BeforeEach
    void dossierDeCinqLignesEnAttenteDecisionPrmp() {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        Dossier d = dossierLoc(DOSSIER, "EN_ATTENTE_DECISION_PRMP", "ANT", "PRMP001");
        d.setIdTypeDossier("DDP");
        dossierRepository.save(d);
        ppmRepository.save(ppm(PPM, DOSSIER, "PRMP001"));
        for (int id : LIGNES) {
            Marche m = marche(id, DOSSIER, PPM);
            m.setMontEstim(new BigDecimal("100"));
            m.setIdNature(1);   // même nature que le corps des PUT : une ligne renvoyée à l'identique est INCHANGEE
            marcheRepository.save(m);
        }
    }

    // ------------------------------------------------------------------ 1. écart 3/3 accepté

    @Test
    @DisplayName("Test attendu n° 1 — 3 créations et 3 retraits → 200 : nouveau contenu, version remplacée "
            + "archivée telle quelle (5 lignes), diff NOUVELLE ×3 / SUPPRIMEE ×3")
    void ecartDeTrois_accepte_contenuArchiveEtDiff() throws Exception {
        // Conservées : 8001 (inchangée) et 8002 (montant corrigé) ; retirées : 8003, 8004, 8005 ; 3 créations.
        List<String> lignes = new ArrayList<>();
        lignes.add(ligne(8001, "Marche 8001", "100"));
        lignes.add(ligne(8002, "Marche 8002", "150"));
        lignes.add(creation("Nouveau A", "10"));
        lignes.add(creation("Nouveau B", "20"));
        lignes.add(creation("Nouveau C", "30"));
        rectifier(corps(lignes)).andExpect(status().isOk());

        List<Marche> courantes = marcheRepository.findByIdDossier(DOSSIER);
        assertThat(courantes).hasSize(5);
        assertThat(courantes).extracting(Marche::getIdDetail).contains(8001, 8002).doesNotContain(8003, 8004, 8005);
        assertThat(courantes).extracting(Marche::getDesignationMarche).contains("Nouveau A", "Nouveau B", "Nouveau C");
        // Les enfants des lignes retirées sont partis avec elles (cascade), ceux des créées existent.
        assertThat(marchePrevisionRepository.findByIdDetail(8003)).isEmpty();
        Marche nouveauA = courantes.stream().filter(m -> "Nouveau A".equals(m.getDesignationMarche())).findFirst().orElseThrow();
        assertThat(marchePrevisionRepository.findByIdDetail(nouveauA.getIdDetail())).hasSize(1);

        // La version remplacée est archivée AVANT, telle quelle : les cinq lignes d'origine.
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/versions-archivees").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nbLignes").value(5));
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/versions-archivees/1").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.lignes", hasSize(5)))
                .andExpect(jsonPath("$.lignes[?(@.idDetail==8003)].designationMarche", hasSize(1)));

        // Le diff du cycle restitue l'écart : 3 NOUVELLE (idDetail posé), 3 SUPPRIMEE (idDetail nul,
        // libellé archivé), 1 MODIFIEE (8002), 1 INCHANGEE (8001).
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/diff-rectification").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recap.nouvelles").value(3))
                .andExpect(jsonPath("$.recap.supprimees").value(3))
                .andExpect(jsonPath("$.recap.modifiees").value(1))
                .andExpect(jsonPath("$.recap.inchangees").value(1))
                .andExpect(jsonPath("$.recap.total").value(8))
                .andExpect(jsonPath("$.lignes[?(@.type=='SUPPRIMEE')].designation", hasSize(3)))
                .andExpect(jsonPath("$.lignes[?(@.type=='SUPPRIMEE' && @.designation=='Marche 8003')]", hasSize(1)))
                .andExpect(jsonPath("$.lignes[?(@.type=='NOUVELLE' && @.designation=='Nouveau B')].idDetail", hasSize(1)));
    }

    // ------------------------------------------------------------------ 2. écart de 4 refusé

    @Test
    @DisplayName("Test attendu n° 2 — 4 créations, ou 4 retraits → 400 nommant l'écart ; idDetail étranger → 400 ; "
            + "rien n'est écrit")
    void ecartDeQuatre_refuse_sansEcriture() throws Exception {
        // 4 créations (les 5 lignes conservées).
        List<String> quatreCreations = new ArrayList<>();
        for (int id : LIGNES) quatreCreations.add(ligne(id, "Marche " + id, "100"));
        for (String n : List.of("A", "B", "C", "D")) quatreCreations.add(creation("Nouveau " + n, "10"));
        rectifier(corps(quatreCreations))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("ajoute 4 ligne(s) et en retire 0")))
                .andExpect(jsonPath("$.message", containsString("l'écart maximal autorisé est de 3 dans chaque sens")));

        // 4 retraits (seule 8001 est renvoyée).
        rectifier(corps(List.of(ligne(8001, "Marche 8001", "100"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("ajoute 0 ligne(s) et en retire 4")));

        // idDetail étranger au dossier : ce n'est pas une création, c'est une erreur d'appariement.
        List<String> etranger = new ArrayList<>();
        for (int id : LIGNES) etranger.add(ligne(id, "Marche " + id, "100"));
        etranger.add(ligne(999999, "Intrus", "10"));
        rectifier(corps(etranger))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("n'appartient pas au dossier")));

        // Rien n'a été écrit : cinq lignes intactes, aucune version archivée.
        assertThat(marcheRepository.findByIdDossier(DOSSIER)).hasSize(5);
        assertThat(marcheRepository.findById(8001).orElseThrow().getMontEstim()).isEqualByComparingTo("100");
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/versions-archivees").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ------------------------------------------------------------------ 3. observation non levée

    @Test
    @DisplayName("Test attendu n° 3 — retirer une ligne portant une observation du PV NON LEVÉE → 400 nominatif ; "
            + "une fois l'observation LEVÉE, le retrait passe")
    void retraitLigneAvecObservationNonLevee_refuse_puisAcceptesUneFoisLevee() throws Exception {
        // Circuit minimal pour porter une observation POINT sur la ligne 8003 : réception → dispatch →
        // examen → ligne d'examen (8003 non conforme) → PV signé → observation du PV (snapshot, V19).
        receptionRepository.save(reception(800, DOSSIER, "CTRCC1", true));
        dispatchRepository.save(dispatch(800, 800, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(800, 800, "CTRMEM"));
        if (!pointsCtrlRepository.existsById(990)) {
            PointsCtrl pc = new PointsCtrl();
            pc.setIdPointCtrl(990); pc.setLibelPointCtrl("Contrôle test"); pc.setObligatoire(true);
            pc.setIdTypeDossier("DDP");
            pointsCtrlRepository.save(pc);
        }
        ExamenDetail ed = new ExamenDetail();
        ed.setIdDetailExamen(8990); ed.setIdExamen(800); ed.setIdPtControle(990); ed.setIdDetail(8003);
        ed.setConforme(false); ed.setObsSiNonConforme("montant à justifier");
        examenDetailRepository.save(ed);
        seedPvSigne(800, 800);
        ObservationPv obs = new ObservationPv();
        obs.setIdDossier(DOSSIER); obs.setIdPv(800); obs.setSource("POINT"); obs.setIdDetailExamen(8990);
        obs.setLibelle("Ligne « Marche 8003 » — Contrôle test : montant à justifier"); obs.setOrdre(1);
        obs = observationPvRepository.save(obs);

        // Retrait de 8003 (seule ligne omise) → refusé, nominatif.
        List<String> sans8003 = new ArrayList<>();
        for (int id : LIGNES) if (id != 8003) sans8003.add(ligne(id, "Marche " + id, "100"));
        rectifier(corps(sans8003))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Marche 8003")))
                .andExpect(jsonPath("$.message", containsString("observation du PV non levée")));
        assertThat(marcheRepository.existsById(8003)).isTrue();

        // Le vérificateur lève l'observation (« levée = acquise ») : la ligne n'est plus protégée.
        SuiviObservation levee = new SuiviObservation();
        levee.setIdObservationPv(obs.getIdObservationPv()); levee.setIteration(1); levee.setDecision("LEVEE");
        levee.setImVerificateur("CTRVER"); levee.setDateDecision(LocalDateTime.now());
        suiviObservationRepository.save(levee);
        rectifier(corps(sans8003)).andExpect(status().isOk());
        assertThat(marcheRepository.existsById(8003)).isFalse();
        // L'histoire de l'instruction reste : la ligne d'examen et l'observation survivent au retrait.
        assertThat(examenDetailRepository.existsById(8990)).isTrue();
        assertThat(observationPvRepository.existsById(obs.getIdObservationPv())).isTrue();
    }

    // ------------------------------------------------------------------ 4. structure identique

    @Test
    @DisplayName("Test attendu n° 4 — structure identique : comportement inchangé (200, 5 lignes, version "
            + "archivée, diff sans NOUVELLE ni SUPPRIMEE)")
    void structureIdentique_inchangee() throws Exception {
        List<String> memes = new ArrayList<>();
        for (int id : LIGNES) memes.add(ligne(id, "Marche " + id + (id == 8005 ? " corrigee" : ""), "100"));
        rectifier(corps(memes)).andExpect(status().isOk());
        assertThat(marcheRepository.findByIdDossier(DOSSIER)).hasSize(5);
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/versions-archivees").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/diff-rectification").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recap.nouvelles").value(0))
                .andExpect(jsonPath("$.recap.supprimees").value(0))
                .andExpect(jsonPath("$.recap.modifiees").value(1))
                .andExpect(jsonPath("$.recap.inchangees").value(4));
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions rectifier(String corps) throws Exception {
        return mvc.perform(put("/api/saisies/ppm/" + DOSSIER).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    private static String corps(List<String> lignes) {
        return "{\"exercice\":2026,\"signataire\":\"PRMP Test\",\"dateSignature\":\"2026-06-01\",\"reference\":\"PPM-800\","
                + "\"marches\":[" + String.join(",", lignes) + "]}";
    }

    /** Ligne appariée (mise à jour en place). Enfants absents (null) = conservés. */
    private static String ligne(int idDetail, String designation, String montant) {
        return "{\"idDetail\":" + idDetail + ",\"formeMarche\":\"QUANTITE_FIXE\",\"montEstim\":" + montant
                + ",\"idNature\":1,\"statut\":\"PREVU\",\"designationMarche\":\"" + designation + "\"}";
    }

    /** Création : sans idDetail, avec le processus obligatoire d'une ligne nouvelle. */
    private static String creation(String designation, String montant) {
        return "{\"formeMarche\":\"QUANTITE_FIXE\",\"montEstim\":" + montant + ",\"idNature\":1,\"statut\":\"PREVU\","
                + "\"designationMarche\":\"" + designation + "\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-03-01\"}]}";
    }
}
