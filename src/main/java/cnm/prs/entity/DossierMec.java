package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import cnm.prs.enums.StatutDmc;

/**
 * Dossier de mise en concurrence (DMC) : <strong>un par ligne de marché</strong> (relation 1-1 sur
 * {@code ID_DETAIL}). Son type ({@code ID_TYPE_DMC}) est <strong>dérivé du mode de passation</strong>
 * du marché (mapping {@code tr_mode_passation.ID_TYPE_DMC}). Table {@code t_dossier_mec}.
 */
@Entity
@Table(name = "t_dossier_mec")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DossierMec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    /** Ligne de marché rattachée (unique — relation 1-1 stricte). */
    @Column(name = "ID_DETAIL", nullable = false, unique = true)
    private Integer idDetail;

    /** Type de DMC dérivé du mode de passation du marché. */
    @Column(name = "ID_TYPE_DMC", nullable = false)
    private Long idTypeDmc;

    @Column(name = "REFERENCE", length = 60)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUT", nullable = false, length = 30)
    private StatutDmc statut = StatutDmc.A_PREPARER;

    @Column(name = "DATE_CREATION", nullable = false)
    private LocalDateTime dateCreation;

    /** Type de DMC (lecture seule — code/libellé pour l'affichage). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_TYPE_DMC", insertable = false, updatable = false)
    private TypeDmc typeDmc;
}
