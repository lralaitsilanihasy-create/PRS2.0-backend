package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ Spec « circuit des observations FAVR » (2026-08-02) — HISTORIQUE append-only des décisions du
 * vérificateur sur une observation du périmètre figé {@link ObservationPv} : une ligne par décision
 * et par itération (LEVÉE = satisfaite, définitive ; MAINTENUE = rappel, avec précision facultative
 * sur ce qui manque). Auteur + horodatage à chaque ligne (traçabilité §4 de la spec).
 */
@Entity
@Table(name = "t_suivi_observation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SuiviObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_SUIVI", nullable = false)
    private Integer idSuivi;

    @Column(name = "ID_OBSERVATION_PV", nullable = false)
    private Integer idObservationPv;

    /** Numéro d'itération du cycle rectification / vérification (1, 2, …). */
    @Column(name = "ITERATION", nullable = false)
    private Integer iteration;

    /** Décision : {@code LEVEE} ou {@code MAINTENUE}. */
    @Column(name = "DECISION", nullable = false, length = 10)
    private String decision;

    /** Précision du vérificateur sur ce qui manque (MAINTENUE seulement, facultative). */
    @Column(name = "PRECISION_VERIF", length = 500)
    private String precision;

    @Column(name = "IM_VERIFICATEUR", length = 7, nullable = false)
    private String imVerificateur;

    @Column(name = "DATE_DECISION", nullable = false)
    private LocalDateTime dateDecision;
}
