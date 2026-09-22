package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Anomalie;
import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.ExamenPiece;
import cnm.prs.entity.ObservationControle;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.RegleAnomalie;
import cnm.prs.entity.TacheDossier;
import cnm.prs.repository.AnomalieRepository;
import cnm.prs.repository.RegleAnomalieRepository;
import cnm.prs.repository.TacheDossierRepository;

/**
 * ⚠️ <strong>Réinitialiser un examen en cours</strong> (demande front du 2026-09-21, question du pilote sur le dossier
 * 00002) — les huit cas de la recette. L'attributaire efface d'un geste tout son brouillon d'examen ; la ligne
 * d'examen, le chronométrage et le pré-contrôle restent ; le journal garde la trace.
 */
class ReinitialisationExamenIntegrationTest extends CnmIntegrationTestSupport {

    private static final int POINT = 960;

    @Autowired private TacheDossierRepository tacheDossierRepository;
    @Autowired private AnomalieRepository anomalieRepository;
    @Autowired private RegleAnomalieRepository regleAnomalieRepository;

    @Test
    @DisplayName("1 — Attributaire, dossier DISPATCHE, 3 points + 4 observations + 2 pièces → 200, tout effacé, examen "
            + "conservé, dossier toujours DISPATCHE, chronométrage identique, journal +1 REINITIALISATION_EXAMEN")
    void attributaire_reinitialise() throws Exception {
        brouillon(9800, "CTRMEM", 3, 4, 2);
        passage(9800, "DISPATCH", "CTRPRE");
        String chronoAvant = chronoClos(9800);
        long journalAvant = journal(9800).size();

        mvc.perform(post("/api/examens/9800/reinitialiser").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idExamen").value(9800))
                .andExpect(jsonPath("$.idDispatch").value(9800))
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRMEM"))
                .andExpect(jsonPath("$.avisSuggere").isEmpty());

        assertThat(examenDetailRepository.countByIdExamen(9800)).isZero();
        assertThat(observationControleRepository.countParExamen(9800)).isZero();
        assertThat(examenPieceRepository.countByIdExamen(9800)).isZero();
        assertThat(examenRepository.findById(9800)).isPresent();
        mvc.perform(get("/api/dossiers/9800").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.statut").value("DISPATCHE"));
        mvc.perform(get("/api/examens/9800").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.avisSuggere").isEmpty());
        assertThat(chronoClos(9800)).isEqualTo(chronoAvant);

        List<Object> lignes = journal(9800);
        assertThat(lignes).hasSize((int) journalAvant + 1);
        String corps = JsonPath.parse(lignes).jsonString();
        assertThat(JsonPath.<List<String>>read(corps, "$[?(@.typeAction=='REINITIALISATION_EXAMEN')].nomOperateur"))
                .containsExactly("NomCTRMEM Prenoms");
        assertThat(JsonPath.<List<String>>read(corps, "$[?(@.typeAction=='REINITIALISATION_EXAMEN')].detail"))
                .containsExactly("3 point(s) et 2 pièce(s) effacés (4 observation(s))");
    }

    @Test
    @DisplayName("2 — Le dispatcheur non attributaire (Président) → 403 nominatif ; le CC en copie → 403 ; rien d'effacé")
    void dispatcheurEtCopie_403() throws Exception {
        brouillon(9801, "CTRMEM", 2, 1, 1);
        mvc.perform(post("/api/examens/9801/reinitialiser").header("Authorization", tokenPresident))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("NomCTRMEM Prenoms")));
        mvc.perform(post("/api/examens/9801/reinitialiser").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
        assertThat(examenDetailRepository.countByIdExamen(9801)).isEqualTo(2);
        assertThat(examenPieceRepository.countByIdExamen(9801)).isEqualTo(1);
    }

    @Test
    @DisplayName("3 — Le P/CC attributaire par délégation (dispatché à lui-même) → 200")
    void ccAttributaire_200() throws Exception {
        brouillon(9802, "CTRCC1", 2, 0, 0);
        mvc.perform(post("/api/examens/9802/reinitialiser").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        assertThat(examenDetailRepository.countByIdExamen(9802)).isZero();
    }

    @Test
    @DisplayName("4 — Dossier EXAMINE avec projet de PV → 409 nominatif, rien d'effacé")
    void examenSoumis_409() throws Exception {
        brouillon(9803, "CTRMEM", 2, 1, 1);
        dossierRepository.findById(9803).ifPresent(d -> { d.setStatut("EXAMINE"); dossierRepository.save(d); });
        PvExamen pv = new PvExamen();
        pv.setIdPv(9813); pv.setIdExamen(9803); pv.setIdAvis("FAV"); pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("PROJET_SOUMIS"); pv.setNbNavettes(0);
        pvExamenRepository.save(pv);
        mvc.perform(post("/api/examens/9803/reinitialiser").header("Authorization", tokenMembre))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("L'examen a été soumis")));
        assertThat(examenDetailRepository.countByIdExamen(9803)).isEqualTo(2);
        assertThat(observationControleRepository.countParExamen(9803)).isEqualTo(1);
    }

    @Test
    @DisplayName("5 — Examen inexistant → 404 ; anonyme → 401")
    void inexistant_404_anonyme_401() throws Exception {
        mvc.perform(post("/api/examens/999999/reinitialiser").header("Authorization", tokenMembre))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/examens/1/reinitialiser")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("6 — Second appel sur l'examen déjà vide → 200, journal SANS nouvelle entrée")
    void dejaVide_200_sansTrace() throws Exception {
        brouillon(9804, "CTRMEM", 1, 0, 0);
        mvc.perform(post("/api/examens/9804/reinitialiser").header("Authorization", tokenMembre))
                .andExpect(status().isOk());
        int apresPremier = journal(9804).size();
        mvc.perform(post("/api/examens/9804/reinitialiser").header("Authorization", tokenMembre))
                .andExpect(status().isOk());
        assertThat(journal(9804)).hasSize(apresPremier);
        String corps = JsonPath.parse(journal(9804)).jsonString();
        assertThat(JsonPath.<List<String>>read(corps, "$[?(@.typeAction=='REINITIALISATION_EXAMEN')].typeAction")).hasSize(1);
    }

    @Test
    @DisplayName("7 — Un signalement de pré-contrôle écarté avant la réinitialisation reste écarté après (Q1)")
    void preControle_intact() throws Exception {
        brouillon(9805, "CTRMEM", 1, 1, 0);
        RegleAnomalie regle = new RegleAnomalie();
        regle.setIdRegleAnomalie(9805); regle.setCodeRegle("MONTANT_HORS_SEUIL"); regle.setLibelle("Montant hors seuil");
        regle.setActif(true);
        regleAnomalieRepository.save(regle);
        Anomalie ecartee = new Anomalie();
        ecartee.setIdAnomalie(anomalieRepository.nextIdAnomalie().intValue());
        ecartee.setIdPpm(9805);
        ecartee.setIdRegleAnomalie(9805);
        ecartee.setDateDetection(LocalDateTime.of(2026, 9, 20, 10, 0));
        ecartee.setStatut("ECARTE");
        ecartee.setImTraitement("CTRMEM");
        ecartee.setCleSignalement("MONTANT_HORS_SEUIL#9805");
        Integer idAnomalie = anomalieRepository.save(ecartee).getIdAnomalie();

        mvc.perform(post("/api/examens/9805/reinitialiser").header("Authorization", tokenMembre))
                .andExpect(status().isOk());
        Anomalie apres = anomalieRepository.findById(idAnomalie).orElseThrow();
        assertThat(apres.getStatut()).isEqualTo("ECARTE");
        assertThat(anomalieRepository.findByIdPpmOrderByIdAnomalie(9805)).hasSize(1);
    }

    @Test
    @DisplayName("8 — Une transaction, pas de suppression ligne à ligne : le nombre d'ordres SQL ne dépend pas du volume")
    void ordresSql_constants() throws Exception {
        brouillon(9806, "CTRMEM", 3, 3, 2);
        brouillon(9807, "CTRMEM", 30, 30, 12);
        long petit = ordresSql(9806);
        long grand = ordresSql(9807);
        assertThat(grand).isEqualTo(petit).isLessThanOrEqualTo(15);
        assertThat(examenDetailRepository.countByIdExamen(9807)).isZero();
        assertThat(observationControleRepository.countParExamen(9807)).isZero();
        assertThat(examenPieceRepository.countByIdExamen(9807)).isZero();
    }

    // ================================================================== décor et gestes

    /**
     * Un dossier {@code DISPATCHE} de la Centrale (dispatcheur CTRPRE, réception CTRCC1) attribué à {@code membre},
     * son examen {@code id} (même numéro), et un brouillon : {@code points} points de contrôle, {@code observations}
     * lignes « Au lieu de / Lire » réparties sur le premier point, {@code pieces} résultats de pièces.
     */
    private void brouillon(int id, String membre, int points, int observations, int pieces) {
        if (!pointsCtrlRepository.existsById(POINT)) {
            PointsCtrl pc = new PointsCtrl();
            pc.setIdPointCtrl(POINT); pc.setLibelPointCtrl("Montant"); pc.setObligatoire(true); pc.setIdTypeDossier("DDP");
            pointsCtrlRepository.save(pc);
        }
        dossierRepository.save(dossierLoc(id, "DISPATCHE", "ANT", "PRMP001"));
        ppmRepository.save(ppm(id, id, "PRMP001"));
        receptionRepository.save(reception(id, id, "CTRCC1", true));
        dispatchRepository.save(dispatch(id, id, "CTRCC1".equals(membre) ? null : "CTRCC1", membre, "CTRPRE"));
        examenRepository.save(examen(id, id, membre));
        for (int i = 0; i < points; i++) {
            ExamenDetail d = new ExamenDetail();
            d.setIdDetailExamen(id * 100 + i); d.setIdExamen(id); d.setIdPtControle(POINT);
            d.setIdDetail(null); d.setConforme(i == 0 ? Boolean.FALSE : Boolean.TRUE);
            d.setObsSiNonConforme(i == 0 ? "Montant erroné" : null);
            examenDetailRepository.save(d);
        }
        for (int i = 0; i < observations; i++) {
            ObservationControle o = new ObservationControle();
            o.setIdDetail(id * 100); o.setAuLieuDe("500000"); o.setLire("5000000"); o.setOrdre(i + 1);
            observationControleRepository.save(o);
        }
        for (int i = 0; i < pieces; i++) {
            ExamenPiece p = new ExamenPiece();
            p.setIdExamenPiece(id * 100 + i); p.setIdExamen(id); p.setIdPiece(id * 100 + i);
            p.setConforme(false); p.setObservation("Pièce manquante");
            examenPieceRepository.save(p);
        }
        entityManager.flush();
    }

    /** Un passage clos du chronométrage, pour que « identique avant/après » compare quelque chose. */
    private void passage(int idDossier, String etape, String acteur) {
        TacheDossier t = new TacheDossier();
        t.setIdTache(tacheDossierRepository.nextId());
        t.setIdDossier(idDossier); t.setEtape(etape); t.setOccurrence(1); t.setImActeur(acteur);
        t.setProfil("PRESIDENT"); t.setDateFin(LocalDateTime.of(2026, 9, 18, 10, 0));
        tacheDossierRepository.save(t);
    }

    /** Les passages CLOS et l'étape courante du chronométrage — ce qui ne doit pas bouger. */
    private String chronoClos(int idDossier) throws Exception {
        String corps = mvc.perform(get("/api/dossiers/" + idDossier + "/chronometrage").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.parse(corps).read("$.etapes[?(@.enCours==false)]").toString()
                + "|" + JsonPath.read(corps, "$.etapeCourante");
    }

    private List<Object> journal(int idDossier) throws Exception {
        String corps = mvc.perform(get("/api/dossiers/" + idDossier + "/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corps, "$");
    }

    /** Ordres SQL préparés pendant la réinitialisation, compteur Hibernate remis à zéro juste avant. */
    private long ordresSql(int idExamen) throws Exception {
        org.hibernate.stat.Statistics stats = entityManager.getEntityManagerFactory()
                .unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        entityManager.flush();
        entityManager.clear();
        stats.clear();
        mvc.perform(post("/api/examens/" + idExamen + "/reinitialiser").header("Authorization", tokenMembre))
                .andExpect(status().isOk());
        return stats.getPrepareStatementCount();
    }
}
