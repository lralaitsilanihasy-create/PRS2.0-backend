package cnm.prs.mapper;

import cnm.prs.dto.ExamenPieceDto;
import cnm.prs.entity.ExamenPiece;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link ExamenPiece}.
 */
public final class ExamenPieceMapper {

    private ExamenPieceMapper() {
    }

    public static ExamenPieceDto toDto(ExamenPiece entity) {
        if (entity == null) {
            return null;
        }
        ExamenPieceDto dto = new ExamenPieceDto();
        dto.setIdExamenPiece(entity.getIdExamenPiece());
        dto.setIdExamen(entity.getIdExamen());
        dto.setIdPiece(entity.getIdPiece());
        dto.setConforme(entity.getConforme());
        dto.setObservation(entity.getObservation());
        return dto;
    }

    public static ExamenPiece toEntity(ExamenPieceDto dto) {
        if (dto == null) {
            return null;
        }
        ExamenPiece entity = new ExamenPiece();
        entity.setIdExamenPiece(dto.getIdExamenPiece());
        entity.setIdExamen(dto.getIdExamen());
        entity.setIdPiece(dto.getIdPiece());
        entity.setConforme(dto.getConforme());
        entity.setObservation(dto.getObservation());
        return entity;
    }
}
