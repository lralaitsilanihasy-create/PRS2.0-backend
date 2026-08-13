package cnm.prs.mapper;

import cnm.prs.dto.CapmDto;
import cnm.prs.entity.Capm;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link Capm}.
 */
public final class CapmMapper {

    private CapmMapper() {
    }

    public static CapmDto toDto(Capm entity) {
        if (entity == null) {
            return null;
        }
        CapmDto dto = new CapmDto();
        dto.setIdCapm(entity.getIdCapm());
        dto.setLibelleProcessus(entity.getLibelleProcessus());
        dto.setOrdre(entity.getOrdre());
        dto.setIdMode(entity.getIdMode());
        dto.setGroupe(entity.getGroupe());
        return dto;
    }

    public static Capm toEntity(CapmDto dto) {
        if (dto == null) {
            return null;
        }
        Capm entity = new Capm();
        entity.setIdCapm(dto.getIdCapm());
        entity.setLibelleProcessus(dto.getLibelleProcessus());
        entity.setOrdre(dto.getOrdre());
        entity.setIdMode(dto.getIdMode());
        entity.setGroupe(dto.getGroupe());
        return entity;
    }
}
