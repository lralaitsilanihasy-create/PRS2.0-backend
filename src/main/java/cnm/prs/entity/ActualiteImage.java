package cnm.prs.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Image d'une actualité (table {@code t_actualite_image}) — spec du 2026-08-18, même approche
 * que {@code t_piece_demande_retrait} : JPEG uniquement (magic-bytes), ≤ 10 Mo à l'envoi,
 * <strong>redimensionnée au serveur</strong> (largeur max 1600 px) avant stockage en
 * {@code bytea}, SHA-256 du contenu stocké.
 */
@Entity
@Table(name = "t_actualite_image")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActualiteImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_IMAGE", nullable = false)
    private Integer idImage;

    @Column(name = "ID_ACTUALITE", nullable = false)
    private Integer idActualite;

    @Column(name = "NOM_FICHIER", length = 255)
    private String nomFichier;

    /** Type MIME réel ({@code image/jpeg} — seul format accepté). */
    @Column(name = "FORMAT", length = 100)
    private String format;

    /** Taille stockée (octets), après redimensionnement éventuel. */
    @Column(name = "TAILLE_OCTETS")
    private Long tailleOctets;

    /** Empreinte SHA-256 (hex) du contenu stocké. */
    @Column(name = "SHA_256", length = 64)
    private String sha256;

    /** Position dans la mini-page (1, 2, 3…). */
    @Column(name = "ORDRE", nullable = false)
    private Integer ordre;

    @Column(name = "DATE_DEPOT")
    private LocalDateTime dateDepot;

    /**
     * Contenu binaire. {@code columnDefinition} explicite : sans elle, Hibernate émet
     * VARBINARY(255) (tronque au-delà de 255 octets en H2) ou BLOB (inconnu du mode
     * PostgreSQL de H2) ; « bytea » est compris par PostgreSQL et par H2 en mode PostgreSQL.
     */
    @Column(name = "CONTENU", columnDefinition = "bytea")
    @JsonIgnore
    private byte[] contenu;
}
