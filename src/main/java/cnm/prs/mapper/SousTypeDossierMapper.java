package cnm.prs.mapper;

import cnm.prs.dto.SousTypeDossierDto;
import cnm.prs.entity.SousTypeDossier;

/**
 * Mapper manuel entre {@link SousTypeDossier} et {@link SousTypeDossierDto}.
 */
public final class SousTypeDossierMapper {

    private SousTypeDossierMapper() {
    }

    public static SousTypeDossierDto toDto(SousTypeDossier entity) {
        if (entity == null) {
            return null;
        }
        SousTypeDossierDto dto = new SousTypeDossierDto();
        dto.setIdSousType(entity.getIdSousType());
        dto.setLibelleSousType(entity.getLibelleSousType());
        dto.setIdTypeDossier(entity.getIdTypeDossier());
        return dto;
    }

    public static SousTypeDossier toEntity(SousTypeDossierDto dto) {
        if (dto == null) {
            return null;
        }
        return new SousTypeDossier(dto.getIdSousType(), dto.getLibelleSousType(), dto.getIdTypeDossier());
    }
}
