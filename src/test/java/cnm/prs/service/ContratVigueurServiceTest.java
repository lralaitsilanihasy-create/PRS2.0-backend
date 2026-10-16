package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.ContratReconduction;
import cnm.prs.entity.ContratVigueur;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.AttributionTitulaireRepository;
import cnm.prs.repository.ContratReconductionRepository;
import cnm.prs.repository.ContratVigueurRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.OffreRepository;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-1) — le contrat en vigueur : sa création à la notification depuis la fiche
 * validée (contrat-cadre, marché à commandes, rien pour une autre forme), la fin de période, la reconduction possible et les alertes.
 */
class ContratVigueurServiceTest {

    private final ContratVigueurRepository contrats = mock(ContratVigueurRepository.class);
    private final ContratReconductionRepository reconductions = mock(ContratReconductionRepository.class);
    private final FicheMarcheService fiches = mock(FicheMarcheService.class);
    private final EvaluationService evaluation = mock(EvaluationService.class);
    private final LocalDate aujourdhui = LocalDate.of(2026, 10, 16);
    private final ContratVigueurService service = new ContratVigueurService(contrats, reconductions, mock(AttributionRepository.class),
            mock(AttributionTitulaireRepository.class), mock(OffreRepository.class), mock(DossierMecRepository.class), fiches,
            mock(ValeursPpmService.class), evaluation, Clock.fixed(aujourdhui.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()),
            mock(cnm.prs.repository.BonCommandeRepository.class));

    private static FicheMarcheService.EtatVersion etat(String type, Map<String, String> valeurs) {
        FicheMarcheDto d = new FicheMarcheDto();
        d.setValeurs(new HashMap<>(valeurs));
        return new FicheMarcheService.EtatVersion(1, d, Map.of(), type, "FOURNITURES_SERVICES");
    }

    private static Attribution notifiee(LocalDate le) {
        Attribution a = new Attribution();
        a.setIdDmc(7L);
        a.setLot(1);
        a.setDateNotification(le);
        return a;
    }

    @Test
    @DisplayName("Contrat-cadre notifié : période, durée maximale, reconductions, préavis, montants et rythme figés depuis la fiche validée")
    void creationContratCadre() {
        when(fiches.etatValide(7L)).thenReturn(Optional.of(etat("CONTRAT_CADRE", Map.of("B02-DC-02", "12", "B02-DC-01", "24", "B02-DC-03",
                "OUI", "B07-DU-04", "1", "B07-DU-07", "3", "B05-MT-01", "250000000", "B05-MT-03", "5000000", "B07-FS-01",
                "Marchés uniques non fractionnés", "B02-PC-02", "Au fur et à mesure des besoins"))));
        when(contrats.findByIdDmcAndLot(7L, 1)).thenReturn(Optional.empty());
        service.creerALaNotification(notifiee(LocalDate.of(2026, 1, 15)));
        ArgumentCaptor<ContratVigueur> c = ArgumentCaptor.forClass(ContratVigueur.class);
        verify(contrats).save(c.capture());
        ContratVigueur x = c.getValue();
        assertThat(x.getTypeMarche()).isEqualTo("CONTRAT_CADRE");
        assertThat(x.getDateEffet()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(x.getDateFin()).isEqualTo(LocalDate.of(2027, 1, 14));
        assertThat(x.getDureeMaxMois()).isEqualTo(24);
        assertThat(x.isReconductible()).isTrue();
        assertThat(x.getReconductionsMax()).isEqualTo(1);
        assertThat(x.getPreavisMois()).isEqualTo(3);
        assertThat(x.getMontantEstime()).isEqualByComparingTo("250000000");
        assertThat(x.getMontantMinimum()).isEqualByComparingTo("5000000");
        assertThat(x.getFormeSubsequents()).isEqualTo("Marchés uniques non fractionnés");
        assertThat(x.getRythme()).isEqualTo("Au fur et à mesure des besoins");
        assertThat(ContratVigueurService.finMaximale(x)).isEqualTo(LocalDate.of(2028, 1, 14));
        assertThat(ContratVigueurService.dateLimiteDecision(x)).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    @DisplayName("Marché à commandes : minimum et maximum annuels, sans reconduction, trois ans au plus (loi art. 30) ; autre forme : rien")
    void creationACommandeEtAutresFormes() {
        when(fiches.etatValide(7L)).thenReturn(Optional.of(etat("A_COMMANDE", Map.of("B02-AU-04", "48", "B05-TP-02", "10000000",
                "B05-TP-03", "40000000"))));
        when(contrats.findByIdDmcAndLot(7L, 1)).thenReturn(Optional.empty());
        service.creerALaNotification(notifiee(LocalDate.of(2026, 1, 1)));
        ArgumentCaptor<ContratVigueur> c = ArgumentCaptor.forClass(ContratVigueur.class);
        verify(contrats).save(c.capture());
        assertThat(c.getValue().getPeriodeMois()).isEqualTo(36);
        assertThat(c.getValue().getDateFin()).isEqualTo(LocalDate.of(2028, 12, 31));
        assertThat(c.getValue().isReconductible()).isFalse();
        assertThat(c.getValue().getMontantMinimum()).isEqualByComparingTo("10000000");
        assertThat(c.getValue().getMontantMaximum()).isEqualByComparingTo("40000000");

        // Un marché à quantité fixe n'a pas de contrat en vigueur.
        when(fiches.etatValide(8L)).thenReturn(Optional.of(etat("QUANTITE_FIXE", Map.of())));
        Attribution qf = notifiee(LocalDate.of(2026, 1, 1));
        qf.setIdDmc(8L);
        service.creerALaNotification(qf);
        verify(contrats, never()).findByIdDmcAndLot(8L, 1);
    }

    @Test
    @DisplayName("Alertes : reconduction à décider dès 30 jours avant la limite du préavis ; échéance proche à 30 jours sans reconduction ; "
            + "échu après la fin ; plus de reconduction une fois épuisées ou au-delà de la durée maximale")
    void alertesEtReconductionPossible() {
        when(reconductions.findByIdContratOrderByLeAscIdAsc(anyLong())).thenReturn(List.of());
        ContratVigueur c = new ContratVigueur();
        c.setId(1L);
        c.setDateEffet(LocalDate.of(2026, 1, 15));
        c.setDateFin(LocalDate.of(2027, 1, 14));
        c.setPeriodeMois(12);
        c.setDureeMaxMois(24);
        c.setReconductible(true);
        c.setReconductionsMax(1);
        c.setPreavisMois(3);
        assertThat(ContratVigueurService.reconductionPossible(c)).isTrue();
        assertThat(service.alertes(c)).containsExactly(ContratVigueurService.RECONDUCTION_A_DECIDER);   // limite le 14/10/2026
        c.setDateFin(LocalDate.of(2026, 11, 1));
        assertThat(service.alertes(c)).contains(ContratVigueurService.ECHEANCE_PROCHE);
        c.setReconductions(1);
        assertThat(ContratVigueurService.reconductionPossible(c)).isFalse();
        c.setDateFin(LocalDate.of(2026, 10, 15));
        assertThat(service.alertes(c)).containsExactly(ContratVigueurService.ECHU);
        // Décidé pour la période en cours (non reconduit) : plus d'alerte de reconduction, l'échéance reste signalée.
        c.setReconductions(0);
        c.setDateFin(LocalDate.of(2026, 11, 1));
        ContratReconduction non = new ContratReconduction();
        non.setDecision(ContratReconduction.NE_PAS_RECONDUIRE);
        non.setFinAvant(LocalDate.of(2026, 11, 1));
        when(reconductions.findByIdContratOrderByLeAscIdAsc(1L)).thenReturn(List.of(non));
        assertThat(service.alertes(c)).containsExactly(ContratVigueurService.ECHEANCE_PROCHE);
        assertThat(ContratVigueurService.finDePeriode(LocalDate.of(2026, 1, 31), 1)).isEqualTo(LocalDate.of(2026, 2, 27));
        assertThat(ContratVigueurService.finDePeriode(LocalDate.of(2026, 1, 15), null)).isEqualTo(LocalDate.of(2026, 1, 15));
        verify(contrats, never()).save(any());
    }
}
