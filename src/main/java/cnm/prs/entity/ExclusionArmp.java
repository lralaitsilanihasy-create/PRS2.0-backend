package cnm.prs.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V64 (demande front du 2026-10-04, soumission en ligne, lot 1b) — une <strong>exclusion</strong> des marchés publics prononcée par l'ARMP, tenue par l'Administrateur ;
 * jamais supprimée (on la corrige, ou on avance sa date de fin).
 */
@Entity
@Table(name = "t_exclusion_armp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExclusionArmp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_EXCLUSION", nullable = false)
    private Integer idExclusion;

    @Column(name = "NIF", nullable = false, length = 30)
    private String nif;

    @Column(name = "RAISON_SOCIALE", nullable = false, length = 200)
    private String raisonSociale;

    @Column(name = "MOTIF", nullable = false, length = 500)
    private String motif;

    @Column(name = "REFERENCE_DECISION", nullable = false, length = 100)
    private String referenceDecision;

    @Column(name = "DATE_DEBUT", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "DATE_FIN")
    private LocalDate dateFin;

    @Column(name = "DATE_CREATION", nullable = false)
    private LocalDateTime dateCreation;
}
