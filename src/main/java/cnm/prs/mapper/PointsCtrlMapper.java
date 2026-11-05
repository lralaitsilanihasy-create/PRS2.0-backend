package cnm.prs.mapper;

import cnm.prs.dto.PointsCtrlDto;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.enums.PorteePointCtrl;

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
        dto.setPortee(entity.getPortee().name());   // getter coalescent → jamais null
        dto.setCategorie(entity.getCategorie());
        dto.setForme(entity.getForme());
        dto.setElements(cnm.prs.service.ElementsDossier.lire(entity.getElements()));   // ⚠️ V166
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
        entity.setPortee(PorteePointCtrl.depuisCodeOuDefaut(dto.getPortee()));   // absent → LIGNE, inconnu → 400
        entity.setCategorie(dto.getCategorie());
        entity.setForme(dto.getForme());
        entity.setElements(cnm.prs.service.ElementsDossier.ecrire(dto.getElements()));   // ⚠️ V166 : 400 elements
        return entity;
    }
}
