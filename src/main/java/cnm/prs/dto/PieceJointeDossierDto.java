package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.PieceJointeDossier} (sans le contenu binaire).
 * {@code libellePiece} (jointure), {@code format/taille/dateUpload/apresLettreRenvoi/idLettre}
 * sont en lecture seule (posés/dérivés par le serveur).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PieceJointeDossierDto {

    private Integer idPiece;

    @NotNull
    private Integer idDossier;

    @NotNull
    private Integer idTypePiece;

    /** Libellé du type de pièce (jointure {@code t_type_piece_jointe}) — lecture seule. */
    private String libellePiece;

    private String nomFichier;

    private String format;

    private Long taille;

    private LocalDateTime dateUpload;

    private Boolean apresLettreRenvoi;

    private Integer idLettre;

    /** ⚠️ 2026-08-03 — version CORRIGÉE déposée pendant la rectification (observations du PV). */
    private Boolean versionCorrigee;

    /**
     * ⚠️ Fiche marché, lot 2a (2026-09-23) — le document de fiche marché dont la pièce est la copie ({@code null} pour une
     * pièce téléversée). Lecture seule : une pièce produite par la fiche ne se supprime pas à la main (409
     * {@code PIECE_PRODUITE_PAR_FICHE}).
     */
    private Integer idDocumentFiche;
}
