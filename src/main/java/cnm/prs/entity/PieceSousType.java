package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V95 (manuel de contrôle a priori, tranche M2, §B2) — un type de pièce exigé pour un sous-type de dossier : son obligation, son
 * ordre, et sa condition sur la catégorie de la fiche ({@code FOURNITURES_SERVICES}, {@code TRAVAUX}, {@code PRESTATIONS_INTELLECTUELLES})
 * et sur la forme ({@code CONTRAT_CADRE}, ou {@code AUTRE} : toute autre forme). Un sous-type qui a une liste n'utilise qu'elle.
 */
@Entity
@Table(name = "t_piece_sous_type")
@Getter
@Setter
@NoArgsConstructor
public class PieceSousType {

    public static final String CONTRAT_CADRE = "CONTRAT_CADRE";
    public static final String AUTRE = "AUTRE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ID_TYPE_PIECE", nullable = false)
    private Integer idTypePiece;

    @Column(name = "ID_SOUS_TYPE", nullable = false, length = 20)
    private String idSousType;

    @Column(name = "OBLIGATOIRE", nullable = false)
    private Boolean obligatoire;

    @Column(name = "ORDRE", nullable = false)
    private Integer ordre;

    @Column(name = "CATEGORIE", length = 40)
    private String categorie;

    @Column(name = "FORME", length = 20)
    private String forme;

    public PieceSousType(Integer idTypePiece, String idSousType, boolean obligatoire, int ordre, String categorie, String forme) {
        this.idTypePiece = idTypePiece;
        this.idSousType = idSousType;
        this.obligatoire = obligatoire;
        this.ordre = ordre;
        this.categorie = categorie;
        this.forme = forme;
    }
}
