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

/** ⚠️ V156 (demande front « appel-offres-restreint », §B3) — la liste restreinte saisie d'une version de fiche : sa source. */
@Entity
@Table(name = "t_fiche_liste_restreinte")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FicheListeRestreinte {

    @Id
    @Column(name = "ID_FICHE", nullable = false)
    private Integer idFiche;

    @Column(name = "SOURCE", nullable = false, length = 30)
    private String source;

    @Column(name = "SAISI_LE", nullable = false)
    private LocalDateTime saisiLe;

    @Column(name = "SAISI_PAR", length = 50)
    private String saisiPar;
}
