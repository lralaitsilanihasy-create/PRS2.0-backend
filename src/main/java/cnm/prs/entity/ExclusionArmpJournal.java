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
 * ⚠️ V64 (demande front du 2026-10-04, soumission en ligne, lot 1b) — une ligne du <strong>journal</strong> d'une exclusion : anciennes et nouvelles valeurs
 * (JSON), qui, quand.
 */
@Entity
@Table(name = "t_exclusion_armp_journal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExclusionArmpJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_JOURNAL", nullable = false)
    private Integer idJournal;

    @Column(name = "ID_EXCLUSION", nullable = false)
    private Integer idExclusion;

    @Column(name = "DATE_ACTION", nullable = false)
    private LocalDateTime dateAction;

    @Column(name = "ACTEUR", length = 100)
    private String acteur;

    @Column(name = "ANCIENNES", length = 2000)
    private String anciennes;

    @Column(name = "NOUVELLES", nullable = false, length = 2000)
    private String nouvelles;
}
