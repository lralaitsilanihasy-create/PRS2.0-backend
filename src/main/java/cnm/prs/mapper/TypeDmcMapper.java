package cnm.prs.mapper;

import cnm.prs.dto.TypeDmcDto;
import cnm.prs.entity.TypeDmc;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link TypeDmc}.
 */
public final class TypeDmcMapper {

    private TypeDmcMapper() {
    }

    public static TypeDmcDto toDto(TypeDmc entity) {
        if (entity == null) {
            return null;
        }
        TypeDmcDto dto = new TypeDmcDto();
        dto.setIdTypeDmc(entity.getIdTypeDmc());
        dto.setCode(entity.getCode());
        dto.setLibelle(entity.getLibelle());
        dto.setActif(entity.isActif());
        return dto;
    }

    public static TypeDmc toEntity(TypeDmcDto dto) {
        if (dto == null) {
            return null;
        }
        TypeDmc entity = new TypeDmc();
        entity.setIdTypeDmc(dto.getIdTypeDmc());
        entity.setCode(dto.getCode());
        entity.setLibelle(dto.getLibelle());
        entity.setActif(dto.isActif());
        return entity;
    }
}
