package cnm.prs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-08 (lot 2, tranche 2d-3, §B6, Q10 ; V93) — la déclaration sans suite d'une procédure : la demande en cours (ou la
 * dernière), l'historique, et si le sans suite est déclaré.
 */
public record SansSuiteDto(Long idDmc, boolean declaree, Demande courante, List<Demande> demandes) {

    /**
     * Une demande : {@code etat} ∈ {@code A_SOUMETTRE} (dossier DSS en brouillon), {@code AU_CONTROLE}, {@code FAVORABLE},
     * {@code DEFAVORABLE} (la procédure reprend), {@code DECLAREE} ; {@code echeance} = réception + 5 jours (art. 55-II).
     */
    public record Demande(Long id, String motifs, LocalDateTime demandeLe, String demandePar, Integer idDossier, String statutDossier,
            LocalDateTime recuLe, LocalDate echeance, boolean echeanceDepassee, String avis, String etat, String decisionReference,
            LocalDate decisionDate, LocalDateTime declareLe, boolean motifsDisponibles) {
    }

    /** {@code POST …/sans-suite} : les motifs d'intérêt général de la PRMP. */
    public record DemandeRequest(String motifs) {
    }

    /** {@code POST …/sans-suite/declarer} : la décision de la PRMP. */
    public record DeclarationRequest(AttributionDto.Decision decision) {
    }
}
