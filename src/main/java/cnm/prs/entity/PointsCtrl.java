package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité JPA mappée sur la table {@code tr_points_ctrl}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "tr_points_ctrl")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointsCtrl {

    @Id
    @Column(name = "ID_POINT_CTRL", nullable = false)
    private Integer idPointCtrl;

    @Column(name = "LIBEL_POINT_CTRL")
    private String libelPointCtrl;

    @Column(name = "DECRIPT_POINT_CTRL")
    private String decriptPointCtrl;

    @Column(name = "ORDRE_POINT_CTRL")
    private Integer ordrePointCtrl;

    @Column(name = "OBLIGATOIRE", nullable = false)
    private Boolean obligatoire;

    @Column(name = "ID_TYPE_DOSSIER", nullable = false)
    private String idTypeDossier;

    /**
     * ⚠️ Règle ajoutée — <strong>sous-type ciblé</strong> (FK {@code tr_sous_type_dossier}, facultatif) :
     * {@code null} = point <strong>commun</strong> à toute la famille ({@code idTypeDossier}) ; renseigné =
     * point <strong>spécifique</strong> à ce sous-type (ex. contrôle AGPM du seul {@code PPM-AGPM}). La
     * grille effective d'un dossier = points communs de sa famille + points spécifiques de son sous-type.
     */
    @Column(name = "ID_SOUS_TYPE", length = 20)
    private String idSousType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_TYPE_DOSSIER", insertable = false, updatable = false)
    @JsonIgnore
    private TypeDossier typeDossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_SOUS_TYPE", insertable = false, updatable = false)
    @JsonIgnore
    private SousTypeDossier sousTypeDossier;
}
