package cnm.prs.mapper;

import cnm.prs.dto.PieceJointeDossierDto;
import cnm.prs.entity.PieceJointeDossier;

/**
 * Convertisseur entité -&gt; DTO pour {@link PieceJointeDossier} (sans le contenu binaire ;
 * {@code libellePiece} peuplé par le service).
 */
public final class PieceJointeDossierMapper {

    private PieceJointeDossierMapper() {
    }

    public static PieceJointeDossierDto toDto(PieceJointeDossier entity) {
        if (entity == null) {
            return null;
        }
        PieceJointeDossierDto dto = new PieceJointeDossierDto();
        dto.setIdPiece(entity.getIdPiece());
        dto.setIdDossier(entity.getIdDossier());
        dto.setIdTypePiece(entity.getIdTypePiece());
        dto.setNomFichier(entity.getNomFichier());
        dto.setFormat(entity.getFormat());
        dto.setTaille(entity.getTaille());
        dto.setDateUpload(entity.getDateUpload());
        dto.setApresLettreRenvoi(entity.getApresLettreRenvoi());
        dto.setIdLettre(entity.getIdLettre());
        dto.setVersionCorrigee(entity.getVersionCorrigee());
        return dto;
    }
}
