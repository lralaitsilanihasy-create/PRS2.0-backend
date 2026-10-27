package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.EvaluationDto;
import cnm.prs.dto.QuorumDto;
import cnm.prs.dto.SeanceDto;
import cnm.prs.entity.Dossier;

/**
 * ⚠️ R5 (manuel révisé pour l'application, lot 1, §B6) — les deux premières règles branchées sur le socle, évaluées sur des écrans
 * construits : 2-III-A P5 (documents d'évaluation signés) et 2-III-C P3 / P14 (quorum, président, empêchements).
 */
class ControlesAutomatiquesTest {

    private final ControleDocumentsEvaluationSignes documents = new ControleDocumentsEvaluationSignes();
    private final ControleQuorumCommission quorum = new ControleQuorumCommission();

    @Test
    @DisplayName("Les deux règles ne concernent que le dossier de marché ; hors de l'application : NON_APPLICABLE")
    void perimetre() {
        assertThat(documents.concerne(dossier("DDM"))).isTrue();
        assertThat(documents.concerne(dossier("DMC"))).isFalse();
        assertThat(quorum.concerne(dossier("DGC"))).isFalse();
        assertThat(documents.bloquant()).isTrue();
        assertThat(quorum.bloquant()).isTrue();
        ControlesContexte horsLigne = new ControlesContexte(dossier("DDM"), null, false, () -> null, () -> null);
        assertThat(documents.evaluer(dossier("DDM"), horsLigne).resultat()).isEqualTo("NON_APPLICABLE");
        assertThat(quorum.evaluer(dossier("DDM"), horsLigne).resultat()).isEqualTo("NON_APPLICABLE");
        assertThat(documents.evaluer(dossier("DDM"), horsLigne).valeurs()).containsEntry("procedureEnLigne", false);
    }

    @Test
    @DisplayName("P5 : séance et évaluation absentes → NON_CONFORME (valeurs nommées) ; PV signé et rapport signé → CONFORME")
    void documentsSignes() {
        ControlesContexte vide = new ControlesContexte(dossier("DDM"), 7L, true, () -> null, () -> null);
        ControleAutomatique.Resultat r = documents.evaluer(dossier("DDM"), vide);
        assertThat(r.resultat()).isEqualTo("NON_CONFORME");
        assertThat(r.valeurs()).containsEntry("seanceOuverte", false).containsEntry("pvOuvertureSigne", false)
                .containsEntry("evaluationOuverte", false).containsEntry("rapportSigne", false);

        ControlesContexte partiel = new ControlesContexte(dossier("DDM"), 7L, true, () -> seance(true, null), () -> evaluation(false, null));
        assertThat(documents.evaluer(dossier("DDM"), partiel).resultat()).isEqualTo("NON_CONFORME");

        ControlesContexte complet = new ControlesContexte(dossier("DDM"), 7L, true, () -> seance(true, null), () -> evaluation(true, null));
        ControleAutomatique.Resultat ok = documents.evaluer(dossier("DDM"), complet);
        assertThat(ok.resultat()).isEqualTo("CONFORME");
        assertThat(ok.valeurs()).containsEntry("pvOuvertureSigne", true).containsEntry("rapportSigne", true).containsKey("rapportSigneLe");
    }

    @Test
    @DisplayName("P3 / P14 : quorum atteint avec le président aux deux étapes → CONFORME ; non atteint, ou président requis sans "
            + "signature, ou quorum non constaté → NON_CONFORME ; le représentant est nommé dans les valeurs")
    void quorumCommission() {
        QuorumDto bon = new QuorumDto("MAJORITE", 3, 3, true, true, true, null, null);
        QuorumDto representant = new QuorumDto("MAJORITE", 3, 3, true, true, true, "M2", "RABE Paul");
        QuorumDto insuffisant = new QuorumDto("MAJORITE", 3, 2, false, true, true, null, null);
        QuorumDto sansPresident = new QuorumDto("TOUS", 4, 4, true, true, false, null, null);

        ControlesContexte ok = new ControlesContexte(dossier("DDM"), 7L, true, () -> seance(true, bon), () -> evaluation(true, representant));
        ControleAutomatique.Resultat r = quorum.evaluer(dossier("DDM"), ok);
        assertThat(r.resultat()).isEqualTo("CONFORME");
        assertThat(r.valeurs()).containsKeys("pvOuverture", "rapport");
        assertThat(r.valeurs().get("rapport").toString()).contains("representant=RABE Paul", "atteint=true");

        assertThat(quorum.evaluer(dossier("DDM"), new ControlesContexte(dossier("DDM"), 7L, true, () -> seance(true, insuffisant),
                () -> evaluation(true, bon))).resultat()).isEqualTo("NON_CONFORME");
        assertThat(quorum.evaluer(dossier("DDM"), new ControlesContexte(dossier("DDM"), 7L, true, () -> seance(true, bon),
                () -> evaluation(true, sansPresident))).resultat()).isEqualTo("NON_CONFORME");
        ControleAutomatique.Resultat absent = quorum.evaluer(dossier("DDM"), new ControlesContexte(dossier("DDM"), 7L, true,
                () -> seance(true, null), () -> null));
        assertThat(absent.resultat()).isEqualTo("NON_CONFORME");
        assertThat(absent.valeurs().get("rapport").toString()).contains("constate=false");
        assertThat(ControleQuorumCommission.satisfait(new QuorumDto("1", 1, 1, true, false, false, null, null))).isTrue();
    }

    private static Dossier dossier(String famille) {
        Dossier d = new Dossier();
        d.setIdDossier(1);
        d.setIdTypeDossier(famille);
        return d;
    }

    private static SeanceDto seance(boolean pvSigne, QuorumDto q) {
        SeanceDto.Pv pv = new SeanceDto.Pv(true, true, pvSigne, List.of(), List.of(), q, List.of());
        return new SeanceDto(7L, "CLOSE", null, null, null, null, List.of(), List.of(), false, List.of(), null, pv, null, null);
    }

    private static EvaluationDto evaluation(boolean rapportSigne, QuorumDto q) {
        EvaluationDto.Rapport rapport = new EvaluationDto.Rapport(LocalDateTime.of(2026, 10, 1, 10, 0), null, rapportSigne,
                rapportSigne ? LocalDateTime.of(2026, 10, 2, 9, 0) : null, List.of(), List.of(), q);
        return new EvaluationDto(7L, "CLOSE", null, null, List.of(), List.of(), List.of(), rapport, List.of());
    }
}
