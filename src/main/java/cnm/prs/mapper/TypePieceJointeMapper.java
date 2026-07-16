package cnm.prs.mapper;

import cnm.prs.dto.TypePieceJointeDto;
import cnm.prs.entity.TypePieceJointe;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link TypePieceJointe}.
 */
public final class TypePieceJointeMapper {

    private TypePieceJointeMapper() {
    }

    public static TypePieceJointeDto toDto(TypePieceJointe entity) {
        if (entity == null) {
            return null;
        }
        TypePieceJointeDto dto = new TypePieceJointeDto();
        dto.setIdTypePiece(entity.getIdTypePiece());
        dto.setLibellePiece(entity.getLibellePiece());
        dto.setCode(entity.getCode());
        dto.setObligatoire(entity.getObligatoire());
        dto.setIdTypeDossier(entity.getIdTypeDossier());
        dto.setOrdre(entity.getOrdre());
        return dto;
    }

    public static TypePieceJointe toEntity(TypePieceJointeDto dto) {
        if (dto == null) {
            return null;
        }
        TypePieceJointe entity = new TypePieceJointe();
        entity.setIdTypePiece(dto.getIdTypePiece());
        entity.setLibellePiece(dto.getLibellePiece());
        entity.setCode(dto.getCode());
        entity.setObligatoire(dto.getObligatoire());
        entity.setIdTypeDossier(dto.getIdTypeDossier());
        entity.setOrdre(dto.getOrdre());
        return entity;
    }
}
