package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.AttributionDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.AttributionPiece;
import cnm.prs.entity.DelaiParametre;
import cnm.prs.repository.DelaiParametreRepository;

/**
 * ⚠️ R6c (manuel révisé pour l'application, lot 2, §B1 règles 6 à 8 ; V108) — les calculs purs : le délai d'attente depuis la réception
 * présumée (paramètre {@code RECEPTION_LETTRE}), le point de départ et l'échéance de l'avis d'attribution, la limite de délivrance des
 * pièces de l'attributaire.
 */
class DelaisR6cTest {

    private static DelaiAttenteCalcul calcul(Integer jours, boolean actif) {
        DelaiParametreRepository repo = mock(DelaiParametreRepository.class);
        if (jours == null) {
            when(repo.findById(DelaiAttenteCalcul.RECEPTION_LETTRE)).thenReturn(Optional.empty());
        } else {
            DelaiParametre p = new DelaiParametre();
            p.setCode(DelaiAttenteCalcul.RECEPTION_LETTRE);
            p.setValeur(jours);
            p.setUnite("JOURS");
            p.setActif(actif);
            when(repo.findById(DelaiAttenteCalcul.RECEPTION_LETTRE)).thenReturn(Optional.of(p));
        }
        return new DelaiAttenteCalcul(repo);
    }

    @Test
    @DisplayName("Règle 6 (QB1) : le délai d'attente part de l'information + n jours (réception présumée), ou de l'affichage s'il est plus tardif ; "
            + "paramètre inactif, nul ou absent = l'envoi vaut réception ; nul avant l'information")
    void delaiAttente() {
        Attribution a = new Attribution();
        assertThat(calcul(3, true).calculer(a, LocalDate.of(2026, 10, 14))).isNull();
        a.setInformeLe(LocalDateTime.of(2026, 10, 1, 10, 0));
        a.setDateAffichage(LocalDate.of(2026, 10, 1));
        AttributionDto.DelaiAttente d = calcul(3, true).calculer(a, LocalDate.of(2026, 10, 14));
        assertThat(d.joursReception()).isEqualTo(3);
        assertThat(d.receptionLe()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(d.debut()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(d.fin()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(d.signableLe()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(d.jours()).isEqualTo(10);
        assertThat(d.ecoule()).isFalse();
        assertThat(calcul(3, true).calculer(a, LocalDate.of(2026, 10, 15)).ecoule()).isTrue();

        a.setDateAffichage(LocalDate.of(2026, 10, 6));
        d = calcul(3, true).calculer(a, LocalDate.of(2026, 10, 14));
        assertThat(d.receptionLe()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(d.debut()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(d.signableLe()).isEqualTo(LocalDate.of(2026, 10, 17));

        a.setDateAffichage(null);
        assertThat(calcul(3, false).calculer(a, LocalDate.of(2026, 10, 14)).signableLe()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(calcul(0, true).calculer(a, LocalDate.of(2026, 10, 14)).joursReception()).isZero();
        d = calcul(null, true).calculer(a, LocalDate.of(2026, 10, 14));
        assertThat(d.joursReception()).isZero();
        assertThat(d.receptionLe()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(d.debut()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(d.signableLe()).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    @DisplayName("Règle 8 : l'avis d'attribution part de la réception de la notification par le titulaire, à défaut de la notification ; "
            + "échéance à 30 jours ; en retard une fois l'échéance passée sans publication, ou publié après elle")
    void avisAttribution() {
        Attribution a = new Attribution();
        assertThat(AttributionExecutionService.echeanceAvis(a)).isNull();
        assertThat(AttributionExecutionService.avisEnRetard(a, LocalDate.of(2026, 12, 1))).isFalse();
        a.setNotifieLe(LocalDateTime.of(2026, 10, 1, 9, 0));
        a.setDateNotification(LocalDate.of(2026, 10, 1));
        assertThat(AttributionExecutionService.departAvis(a)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(AttributionExecutionService.echeanceAvis(a)).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(AttributionExecutionService.avisEnRetard(a, LocalDate.of(2026, 10, 31))).isFalse();
        assertThat(AttributionExecutionService.avisEnRetard(a, LocalDate.of(2026, 11, 1))).isTrue();
        a.setNotificationRecueLe(LocalDateTime.of(2026, 10, 3, 15, 30));
        assertThat(AttributionExecutionService.departAvis(a)).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(AttributionExecutionService.echeanceAvis(a)).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(AttributionExecutionService.avisEnRetard(a, LocalDate.of(2026, 11, 1))).isFalse();
        a.setDatePublicationAvis(LocalDate.of(2026, 11, 2));
        assertThat(AttributionExecutionService.avisEnRetard(a, LocalDate.of(2026, 12, 1))).isFalse();
        a.setDatePublicationAvis(LocalDate.of(2026, 11, 3));
        assertThat(AttributionExecutionService.avisEnRetard(a, LocalDate.of(2026, 11, 3))).isTrue();
    }

    @Test
    @DisplayName("Règle 7 : la date de délivrance la plus ancienne admise — information moins six mois (fiscale), moins trois mois (sociale)")
    void limiteDelivrance() {
        Attribution a = new Attribution();
        a.setInformeLe(LocalDateTime.of(2026, 10, 1, 9, 0));
        assertThat(AttributionExecutionService.limiteDelivrance(a, AttributionPiece.FISCALE)).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(AttributionExecutionService.limiteDelivrance(a, AttributionPiece.SOCIALE)).isEqualTo(LocalDate.of(2026, 7, 1));
    }
}
