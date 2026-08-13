package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité JPA mappée sur la table {@code t_piece_jointe_dossier} : pièce jointe d'un dossier
 * (PPM/DAO/MAOO). {@code apresLettreRenvoi} distingue les pièces ajoutées après réception d'une
 * lettre de renvoi (avec {@code idLettre}) des pièces initiales. PK auto (IDENTITY).
 */
@Entity
@Table(name = "t_piece_jointe_dossier")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PieceJointeDossier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PIECE", nullable = false)
    private Integer idPiece;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "ID_TYPE_PIECE", nullable = false)
    private Integer idTypePiece;

    @Column(name = "NOM_FICHIER", length = 255)
    private String nomFichier;

    /** Contenu binaire du fichier (bytea). */
    @Column(name = "CONTENU")
    @JsonIgnore
    private byte[] contenu;

    @Column(name = "FORMAT", length = 10)
    private String format;

    @Column(name = "TAILLE")
    private Long taille;

    @Column(name = "DATE_UPLOAD")
    private LocalDateTime dateUpload;

    /** Vrai si la pièce a été ajoutée après réception d'une lettre de renvoi. */
    @Column(name = "APRES_LETTRE_RENVOI", nullable = false)
    private Boolean apresLettreRenvoi;

    /** Lettre de renvoi concernée (FK {@code t_lettre_renvoi}), si {@code apresLettreRenvoi}. */
    @Column(name = "ID_LETTRE")
    private Integer idLettre;

    /**
     * ⚠️ 2026-08-03 (demande user) — VERSION CORRIGÉE d'une pièce, déposée pendant la RECTIFICATION
     * (dossier {@code EN_ATTENTE_DECISION_PRMP}, observations du PV) : distinguée de l'originale dans
     * toutes les listes (l'originale est conservée). {@code null} = pièce ordinaire (lignes antérieures).
     */
    @Column(name = "VERSION_CORRIGEE")
    private Boolean versionCorrigee;
}
