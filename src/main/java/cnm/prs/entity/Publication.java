package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_publication}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_publication")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Publication {

    @Id
    @Column(name = "ID_PUBLICATION", nullable = false)
    private Integer idPublication;

    @Column(name = "TYPE_OBJET", nullable = false, length = 20)
    private String typeObjet;

    @Column(name = "ID_OBJET", nullable = false)
    private Integer idObjet;

    @Column(name = "DATE_PUBLICATION")
    private LocalDateTime datePublication;

    @Column(name = "IM_PUBLIE_PAR", length = 7)
    private String imPubliePar;

    @Column(name = "STATUT_PUBLI", length = 20)
    private String statutPubli;

    @Column(name = "DATE_RETRAIT")
    private LocalDate dateRetrait;

    @Column(name = "MOTIF_RETRAIT", length = 300)
    private String motifRetrait;

    @Column(name = "NB_CONSULTATIONS")
    private Integer nbConsultations;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_PUBLIE_PAR", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur publiePar;
}
