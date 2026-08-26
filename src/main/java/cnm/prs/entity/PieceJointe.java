package cnm.prs.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pièce jointe à une inscription PRMP (table {@code t_piece_jointe}, §3.1) : arrêté de
 * nomination, CIN, photo.
 *
 * <p>Le contenu binaire est stocké en base ({@code bytea}) ; on conserve la taille, le format
 * (MIME) et l'empreinte SHA-256. Une seule pièce <strong>active</strong> par couple
 * ({@code LOGIN}, {@code TYPE_PIECE}) : un re-dépôt remplace la pièce existante (la trace des
 * dépôts est dans le journal d'audit).</p>
 */
@Entity
@Table(name = "t_piece_jointe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PieceJointe {

    @Id
    @Column(name = "ID_PIECE", nullable = false)
    private Integer idPiece;

    /** Login de l'inscription/compte propriétaire de la pièce. */
    @Column(name = "LOGIN", nullable = false, length = 100)
    private String login;

    /** Type de pièce (cf. {@code cnm.prs.enums.TypePieceJointe} : ARRETE_NOMIN, CIN, PHOTO). */
    @Column(name = "TYPE_PIECE", nullable = false, length = 20)
    private String typePiece;

    @Column(name = "LIBELLE", length = 200)
    private String libelle;

    /** Type MIME réel du fichier (ex. {@code application/pdf}, {@code image/jpeg}). */
    @Column(name = "FORMAT", length = 100)
    private String format;

    @Column(name = "TAILLE_OCTETS")
    private Long tailleOctets;

    @Column(name = "DATE_DEPOT")
    private LocalDateTime dateDepot;

    /** Empreinte SHA-256 (hex) du contenu, pour le contrôle d'intégrité. */
    @Column(name = "HASH_SHA256", length = 64)
    private String hashSha256;

    /** Contenu binaire (PostgreSQL {@code bytea}). Jamais sérialisé en JSON. */
    @Column(name = "CONTENU")
    @JsonIgnore
    private byte[] contenu;
}
