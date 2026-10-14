package cnm.prs.mapper;

import cnm.prs.dto.MarchePrevisionDto;
import cnm.prs.entity.MarchePrevision;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link MarchePrevision}.
 */
public final class MarchePrevisionMapper {

    private MarchePrevisionMapper() {
    }

    public static MarchePrevisionDto toDto(MarchePrevision entity) {
        if (entity == null) {
            return null;
        }
        MarchePrevisionDto dto = new MarchePrevisionDto();
        dto.setIdPrevision(entity.getIdPrevision());
        dto.setIdDetail(entity.getIdDetail());
        dto.setIdCapm(entity.getIdCapm());
        dto.setDateDebut(entity.getDateDebut());
        dto.setDateFin(entity.getDateFin());
        dto.setSansObjet(entity.isSansObjet());
        dto.setMotifSansObjet(entity.getMotifSansObjet());
        // ordre (lecture seule, porté par t_capm) : peuplé par le service.
        return dto;
    }

    public static MarchePrevision toEntity(MarchePrevisionDto dto) {
        if (dto == null) {
            return null;
        }
        MarchePrevision entity = new MarchePrevision();
        entity.setIdPrevision(dto.getIdPrevision());
        entity.setIdDetail(dto.getIdDetail());
        entity.setIdCapm(dto.getIdCapm());
        entity.setDateDebut(dto.getDateDebut());
        entity.setDateFin(dto.getDateFin());
        entity.setSansObjet(Boolean.TRUE.equals(dto.getSansObjet()));
        entity.setMotifSansObjet(entity.isSansObjet() ? dto.getMotifSansObjet() : null);
        return entity;
    }
}
