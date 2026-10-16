package cnm.prs.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ⚠️ V126 (lot MS-3) — un article d'un bon de commande : le prix unitaire repris du bordereau scellé quand il est lisible. */
@Entity
@Table(name = "t_bon_commande_ligne")
@Getter
@Setter
@NoArgsConstructor
public class BonCommandeLigne {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_BON", nullable = false)
    private Long idBon;

    @Column(name = "RANG", nullable = false)
    private int rang;

    @Column(name = "ID_ARTICLE")
    private Integer idArticle;

    @Column(name = "DESIGNATION", nullable = false)
    private String designation;

    @Column(name = "UNITE", length = 50)
    private String unite;

    @Column(name = "QUANTITE", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantite;

    @Column(name = "PRIX_UNITAIRE", nullable = false, precision = 18, scale = 2)
    private BigDecimal prixUnitaire;

    @Column(name = "MONTANT", nullable = false, precision = 18, scale = 2)
    private BigDecimal montant;

    @Column(name = "PRIX_DU_BORDEREAU", nullable = false)
    private boolean prixDuBordereau;
}
