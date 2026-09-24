package cnm.prs.mapper;

import cnm.prs.dto.NatureDto;
import cnm.prs.entity.Nature;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link Nature}.
 */
public final class NatureMapper {

    private NatureMapper() {
    }

    public static NatureDto toDto(Nature entity) {
        if (entity == null) {
            return null;
        }
        NatureDto dto = new NatureDto();
        dto.setIdNature(entity.getIdNature());
        dto.setLibelle(entity.getLibelle());
        dto.setDescription(entity.getDescription());
        dto.setCategorieDao(entity.getCategorieDao());
        return dto;
    }

    public static Nature toEntity(NatureDto dto) {
        if (dto == null) {
            return null;
        }
        Nature entity = new Nature();
        entity.setIdNature(dto.getIdNature());
        entity.setLibelle(dto.getLibelle());
        entity.setDescription(dto.getDescription());
        cnm.prs.enums.CategorieDao c = cnm.prs.enums.CategorieDao.depuisCode(dto.getCategorieDao());
        entity.setCategorieDao(c == null ? null : c.name());
        return entity;
    }
}
