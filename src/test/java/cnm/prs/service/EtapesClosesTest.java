package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.entity.Attribution;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Evaluation;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.AttributionRepriseRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.EvaluationRepository;

/** ⚠️ R9c (manuel révisé, lot 2, §B4 n° 15 ; question du front du 14/10) — le verrou des étapes closes et sa levée tracée. */
class EtapesClosesTest {

    private final AttributionRepository attributions = mock(AttributionRepository.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final AttributionRepriseRepository reprises = mock(AttributionRepriseRepository.class);
    private final EvaluationRepository evaluations = mock(EvaluationRepository.class);
    private final EtapesCloses etapes = new EtapesCloses(attributions, dossiers, reprises, evaluations);
    private final LocalDateTime soumisLe = LocalDateTime.of(2026, 10, 10, 9, 0);

    private void ddm(String statut, LocalDateTime soumission) {
        Attribution a = new Attribution();
        a.setIdDmc(5L);
        a.setLot(1);
        a.setIdDossier(77);
        Dossier d = new Dossier();
        d.setIdDossier(77);
        d.setStatut(statut);
        d.setDateSoumission(soumission);
        when(attributions.findByIdDmcOrderByLotAsc(5L)).thenReturn(List.of(a));
        when(dossiers.findById(77)).thenReturn(Optional.of(d));
    }

    private void evaluation(String etat) {
        Evaluation e = new Evaluation();
        e.setEtat(etat);
        when(evaluations.findById(5L)).thenReturn(Optional.of(e));
    }

    @Test
    @DisplayName("DDM brouillon : rien n'est verrouillé ; DDM soumis : 409 ETAPE_CLOSE avec le dossier et le lot")
    void verrou() {
        ddm("BROUILLON", null);
        assertThatCode(() -> etapes.exigerOuvertes(5L)).doesNotThrowAnyException();
        ddm("EXAMINE", soumisLe);
        BusinessRuleException e = catchThrowableOfType(BusinessRuleException.class, () -> etapes.exigerOuvertes(5L));
        assertThat(e.getCode()).isEqualTo(EtapesCloses.CODE);
        assertThat(e.getDetails()).containsEntry("idDossierMarche", 77).containsEntry("lot", 1);
    }

    @Test
    @DisplayName("Levée tracée : une reprise postérieure à la soumission et l'évaluation rouverte lèvent le verrou ; sa clôture le rétablit")
    void leveeTracee() {
        ddm("EXAMINE", soumisLe);
        when(reprises.existsByIdDmcAndLeAfter(5L, soumisLe)).thenReturn(true);
        evaluation(Evaluation.EN_COURS);
        assertThatCode(() -> etapes.exigerOuvertes(5L)).doesNotThrowAnyException();
        evaluation(Evaluation.CLOSE);
        assertThat(catchThrowableOfType(BusinessRuleException.class, () -> etapes.exigerOuvertes(5L)).getCode()).isEqualTo(EtapesCloses.CODE);
        when(reprises.existsByIdDmcAndLeAfter(5L, soumisLe)).thenReturn(false);
        evaluation(Evaluation.EN_COURS);
        assertThat(catchThrowableOfType(BusinessRuleException.class, () -> etapes.exigerOuvertes(5L)).getCode()).isEqualTo(EtapesCloses.CODE);
    }
}
