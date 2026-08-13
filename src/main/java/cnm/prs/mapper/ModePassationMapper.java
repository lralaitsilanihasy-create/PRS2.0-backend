package cnm.prs.mapper;

import cnm.prs.dto.ModePassationDto;
import cnm.prs.entity.ModePassation;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link ModePassation}.
 */
public final class ModePassationMapper {

    private ModePassationMapper() {
    }

    public static ModePassationDto toDto(ModePassation entity) {
        if (entity == null) {
            return null;
        }
        ModePassationDto dto = new ModePassationDto();
        dto.setIdMode(entity.getIdMode());
        dto.setLibelle(entity.getLibelle());
        dto.setDescription(entity.getDescription());
        dto.setPubliciteRequise(entity.getPubliciteRequise());
        dto.setDelaiMinJours(entity.getDelaiMinJours());
        dto.setBaseLegale(entity.getBaseLegale());
        dto.setIdTypeDmc(entity.getIdTypeDmc());
        dto.setDeclencheAgpm(entity.getDeclencheAgpm());
        dto.setIdModeModeleCapm(entity.getIdModeModeleCapm());
        dto.setCategorie(entity.getCategorie());
        return dto;
    }

    public static ModePassation toEntity(ModePassationDto dto) {
        if (dto == null) {
            return null;
        }
        ModePassation entity = new ModePassation();
        entity.setIdMode(dto.getIdMode());
        entity.setLibelle(dto.getLibelle());
        entity.setDescription(dto.getDescription());
        entity.setPubliciteRequise(dto.getPubliciteRequise());
        entity.setDelaiMinJours(dto.getDelaiMinJours());
        entity.setBaseLegale(dto.getBaseLegale());
        entity.setIdTypeDmc(dto.getIdTypeDmc());
        entity.setDeclencheAgpm(dto.getDeclencheAgpm());
        entity.setIdModeModeleCapm(dto.getIdModeModeleCapm());
        entity.setCategorie(dto.getCategorie());
        return entity;
    }
}
