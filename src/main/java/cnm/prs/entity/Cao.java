package cnm.prs.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V67 (demande front du 2026-10-04, soumission en ligne, lot 2a, §B1 ; Q11 du pilote) — la <strong>commission d'appel
 * d'offres</strong> d'un DAO : la décision de nomination de la PRMP (référence, date, PDF facultatif). Une CAO par DAO ;
 * ses membres sont dans {@link CaoMembre}.
 */
@Entity
@Table(name = "t_cao")
@Getter
@Setter
@NoArgsConstructor
public class Cao {

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "DECISION_REFERENCE", nullable = false, length = 100)
    private String decisionReference;

    @Column(name = "DECISION_DATE", nullable = false)
    private LocalDate decisionDate;

    @Column(name = "DECISION_NOM_FICHIER", length = 255)
    private String decisionNomFichier;

    @Column(name = "DECISION_TAILLE")
    private Long decisionTaille;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "DECISION_FICHIER")
    private byte[] decisionFichier;

    @Column(name = "DATE_MAJ", nullable = false)
    private LocalDateTime dateMaj;

    @Column(name = "ID_PRMP_MAJ", length = 10)
    private String idPrmpMaj;
}
