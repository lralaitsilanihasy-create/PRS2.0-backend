package cnm.prs.mapper;

import cnm.prs.dto.PointsCtrlDto;
import cnm.prs.entity.PointsCtrl;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link PointsCtrl}.
 */
public final class PointsCtrlMapper {

    private PointsCtrlMapper() {
    }

    public static PointsCtrlDto toDto(PointsCtrl entity) {
        if (entity == null) {
            return null;
        }
        PointsCtrlDto dto = new PointsCtrlDto();
        dto.setIdPointCtrl(entity.getIdPointCtrl());
        dto.setLibelPointCtrl(entity.getLibelPointCtrl());
        dto.setDecriptPointCtrl(entity.getDecriptPointCtrl());
        dto.setOrdrePointCtrl(entity.getOrdrePointCtrl());
        dto.setObligatoire(entity.getObligatoire());
        dto.setIdTypeDossier(entity.getIdTypeDossier());
        dto.setIdSousType(entity.getIdSousType());
        return dto;
    }

    public static PointsCtrl toEntity(PointsCtrlDto dto) {
        if (dto == null) {
            return null;
        }
        PointsCtrl entity = new PointsCtrl();
        entity.setIdPointCtrl(dto.getIdPointCtrl());
        entity.setLibelPointCtrl(dto.getLibelPointCtrl());
        entity.setDecriptPointCtrl(dto.getDecriptPointCtrl());
        entity.setOrdrePointCtrl(dto.getOrdrePointCtrl());
        entity.setObligatoire(dto.getObligatoire());
        entity.setIdTypeDossier(dto.getIdTypeDossier());
        entity.setIdSousType(dto.getIdSousType());
        return entity;
    }
}
