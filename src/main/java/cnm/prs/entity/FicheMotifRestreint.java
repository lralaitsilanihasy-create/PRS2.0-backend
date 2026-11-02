package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V155 (demande front « appel-offres-restreint », §B2) — le motif du recours à l'appel d'offres restreint d'une version de fiche
 * (loi, art. 38-IV ; décret, art. 10) : l'un des quatre cas et sa justification.
 */
@Entity
@Table(name = "t_fiche_motif_restreint")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FicheMotifRestreint {

    @Id
    @Column(name = "ID_FICHE", nullable = false)
    private Integer idFiche;

    @Column(name = "MOTIF", nullable = false, length = 30)
    private String motif;

    @Column(name = "JUSTIFICATION", nullable = false, length = 4000)
    private String justification;

    @Column(name = "SAISI_LE", nullable = false)
    private LocalDateTime saisiLe;

    @Column(name = "SAISI_PAR", length = 50)
    private String saisiPar;
}
