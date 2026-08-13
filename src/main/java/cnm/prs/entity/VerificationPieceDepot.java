package cnm.prs.entity;

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
 * ⚠️ Spec recevabilité au dépôt (2026-08-02) — vérification PIÈCE PAR PIÈCE des pièces jointes par le
 * SECRÉTAIRE avant enregistrement de la réception (contrôle de complétude). Table <strong>append-only</strong>
 * (historisation : chaque décision est une nouvelle ligne) ; l'état courant d'une pièce attendue est sa
 * DERNIÈRE décision (par {@code ID_DOSSIER} × {@code ID_TYPE_PIECE}). Objet distinct de la lettre de renvoi
 * (contrôle de recevabilité formelle, aucun circuit d'archivage).
 */
@Entity
@Table(name = "t_verification_piece_depot")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationPieceDepot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_VERIF_PIECE", nullable = false)
    private Integer idVerifPiece;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    /** Type de pièce attendu (référentiel {@code t_type_piece_jointe}). */
    @Column(name = "ID_TYPE_PIECE", nullable = false)
    private Integer idTypePiece;

    /** Pièce déposée vérifiée ({@code t_piece_jointe_dossier}) — {@code null} si MANQUANTE. */
    @Column(name = "ID_PIECE")
    private Integer idPiece;

    /** Décision : {@code CONFORME} / {@code NON_CONFORME} / {@code MANQUANTE}. */
    @Column(name = "DECISION", nullable = false, length = 20)
    private String decision;

    /** Observation libre (motif du rejet / nature du manque). */
    @Column(name = "OBSERVATION", length = 500)
    private String observation;

    /** Secrétaire auteur de la décision (matricule, identité JWT). */
    @Column(name = "IM_SECRETAIRE", length = 7)
    private String imSecretaire;

    @Column(name = "DATE_VERIF", nullable = false)
    private LocalDateTime dateVerif;
}
