package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table référentielle {@code t_type_piece_jointe} : types de pièces jointes
 * attendues par type de dossier (PPM, DAO, MAOO…), avec caractère obligatoire et ordre d'affichage.
 * PK auto (IDENTITY). <em>Distincte de l'enum {@code cnm.prs.enums.TypePieceJointe}</em> (inscription PRMP).
 */
@Entity
@Table(name = "t_type_piece_jointe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TypePieceJointe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_TYPE_PIECE", nullable = false)
    private Integer idTypePiece;

    @Column(name = "LIBELLE_PIECE", length = 200, nullable = false)
    private String libellePiece;

    /**
     * Code stable (administrable, facultatif) identifiant le type de pièce indépendamment du libellé —
     * ex. {@code AGPM}. Permet au serveur de repérer une pièce à obligation <strong>conditionnelle</strong>
     * (AGPM requis ssi le PPM comporte un marché en appel d'offres ouvert) et au front de retrouver son
     * {@code idTypePiece} sans dépendre du libellé.
     */
    @Column(name = "CODE", length = 20)
    private String code;

    @Column(name = "OBLIGATOIRE", nullable = false)
    private Boolean obligatoire;

    /** Type de dossier concerné (FK {@code tr_type_dossier.ID_TYPE_DOSSIER}). */
    @Column(name = "ID_TYPE_DOSSIER", length = 10)
    private String idTypeDossier;

    @Column(name = "ORDRE")
    private Integer ordre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_TYPE_DOSSIER", insertable = false, updatable = false)
    @JsonIgnore
    private TypeDossier typeDossier;
}
