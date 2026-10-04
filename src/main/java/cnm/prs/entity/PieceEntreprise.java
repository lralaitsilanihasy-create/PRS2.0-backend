package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V64 (demande front du 2026-10-04, soumission en ligne, lot 1b) — une <strong>pièce</strong> de l'entreprise d'un candidat (carte fiscale, statuts, pouvoir,
 * autre) : PDF, JPEG ou PNG, contenu en base, empreinte SHA-256.
 */
@Entity
@Table(name = "t_piece_entreprise")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PieceEntreprise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PIECE", nullable = false)
    private Integer idPiece;

    @Column(name = "ID_ENTREPRISE", nullable = false)
    private Integer idEntreprise;

    @Column(name = "TYPE", nullable = false, length = 20)
    private String type;

    @Column(name = "NOM_FICHIER", length = 255)
    private String nomFichier;

    @Column(name = "FORMAT", nullable = false, length = 50)
    private String format;

    @Column(name = "TAILLE", nullable = false)
    private Long taille;

    @Column(name = "HASH_SHA256", nullable = false, length = 64)
    private String hashSha256;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "CONTENU", nullable = false)
    private byte[] contenu;

    @Column(name = "DATE_DEPOT", nullable = false)
    private LocalDateTime dateDepot;
}
