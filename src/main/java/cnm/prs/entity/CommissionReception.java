package cnm.prs.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V127 (lot MS-4) — la commission de réception d'un contrat en vigueur (loi n° 2016-055, art. 13) : désignée par décision de la
 * PRMP (réponse Q1 du pilote), présidée par la PRMP ou par un membre qui la représente.
 */
@Entity
@Table(name = "t_commission_reception")
@Getter
@Setter
@NoArgsConstructor
public class CommissionReception {

    @Id
    @Column(name = "ID_CONTRAT", nullable = false)
    private Long idContrat;

    @Column(name = "DECISION_REFERENCE", nullable = false, length = 100)
    private String decisionReference;

    @Column(name = "DECISION_DATE", nullable = false)
    private LocalDate decisionDate;

    @Column(name = "PRESIDEE_PAR_PRMP", nullable = false)
    private boolean presideeParPrmp;

    @Column(name = "MAJ_LE", nullable = false)
    private LocalDateTime majLe;

    @Column(name = "MAJ_PAR", length = 100)
    private String majPar;
}
