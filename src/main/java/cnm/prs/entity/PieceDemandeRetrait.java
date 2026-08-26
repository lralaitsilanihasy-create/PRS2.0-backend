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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lettre de demande de retrait (datée et signée, PDF) jointe par la PRMP à sa demande
 * (table {@code t_piece_demande_retrait}, §3.1) — une pièce par demande.
 *
 * <p>Stockage <strong>dédié</strong>, volontairement hors de {@code t_piece_jointe_dossier} :
 * les pièces du dossier sont purgées avec le circuit à l'acceptation du retrait
 * ({@code CircuitCascadeService}), alors que cette lettre <strong>justifie la décision</strong>
 * et doit lui survivre. Table à part (et non des colonnes sur {@code t_demande_retrait}) pour
 * que les listes de demandes ne chargent jamais le contenu binaire.</p>
 */
@Entity
@Table(name = "t_piece_demande_retrait")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PieceDemandeRetrait {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PIECE", nullable = false)
    private Integer idPiece;

    /** Demande propriétaire (une seule pièce par demande). */
    @Column(name = "ID_DEMANDE_RETRAIT", nullable = false, unique = true)
    private Integer idDemandeRetrait;

    /** Nom du fichier tel que déposé par la PRMP (affiché au décideur). */
    @Column(name = "NOM_FICHIER", length = 255)
    private String nomFichier;

    /** Type MIME réel ({@code application/pdf} — seul format accepté). */
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
