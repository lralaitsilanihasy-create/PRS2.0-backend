package cnm.prs.entity;

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
 * ⚠️ 2026-10-10 (manuel révisé, lot 1, R1, §B2 ; V101) — la signature électronique simple du PV de la seconde séance (propositions
 * financières, PI) par un membre présent, ou son empêchement constaté ; sur le modèle de {@link SeanceSignature}, par ronde.
 */
@Entity
@Table(name = "t_seance_financiere_signature")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SeanceFinanciereSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "RONDE", nullable = false)
    private Integer ronde;

    @Column(name = "IM", nullable = false, length = 20)
    private String im;

    @Column(name = "DATE", nullable = false)
    private LocalDateTime date;

    @Column(name = "EMPECHEMENT", nullable = false)
    private Boolean empechement = Boolean.FALSE;

    @Column(name = "MOTIF")
    private String motif;

    @Column(name = "CONSTATE_PAR", length = 100)
    private String constatePar;
}
