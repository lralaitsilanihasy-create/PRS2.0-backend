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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_marche_prevision}.
 *
 * <p>Dates prévisionnelles d'un marché par <strong>processus</strong>. Relation 1,N : un marché
 * ({@link Marche}) possède une ligne par processus ({@link Capm}, via {@code ID_CAPM}), chacune
 * portant une {@code DATE_DEBUT} et une {@code DATE_FIN}. L'ordre d'affichage est porté par
 * {@code t_capm.ORDRE}.</p>
 */
@Entity
@Table(name = "t_marche_prevision")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarchePrevision {

    @Id
    @Column(name = "ID_PREVISION", nullable = false)
    private Integer idPrevision;

    /** Marché concerné (FK vers {@code t_marche.ID_DETAIL}). */
    @Column(name = "ID_DETAIL", nullable = false)
    private Integer idDetail;

    /** Processus de marché (FK vers {@code t_capm.ID_CAPM}). */
    @Column(name = "ID_CAPM", nullable = false)
    private Integer idCapm;

    /** Date prévisionnelle de début du processus. */
    @Column(name = "DATE_DEBUT")
    private LocalDate dateDebut;

    /** Date prévisionnelle de fin du processus. */
    @Column(name = "DATE_FIN")
    private LocalDate dateFin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_CAPM", insertable = false, updatable = false)
    @JsonIgnore
    private Capm capm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DETAIL", insertable = false, updatable = false)
    @JsonIgnore
    private Marche marche;
}
