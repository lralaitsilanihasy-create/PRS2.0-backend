package cnm.prs.mapper;

import cnm.prs.dto.CategorieEntiteDto;
import cnm.prs.entity.CategorieEntite;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link CategorieEntite}.
 */
public final class CategorieEntiteMapper {

    private CategorieEntiteMapper() {
    }

    public static CategorieEntiteDto toDto(CategorieEntite entity) {
        if (entity == null) {
            return null;
        }
        CategorieEntiteDto dto = new CategorieEntiteDto();
        dto.setLibelle(entity.getLibelle());
        dto.setNiveauHierarchique(entity.getNiveauHierarchique());
        return dto;
    }

    public static CategorieEntite toEntity(CategorieEntiteDto dto) {
        if (dto == null) {
            return null;
        }
        CategorieEntite entity = new CategorieEntite();
        entity.setLibelle(dto.getLibelle());
        entity.setNiveauHierarchique(dto.getNiveauHierarchique());
        return entity;
    }
}
