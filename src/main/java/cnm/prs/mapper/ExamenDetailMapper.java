package cnm.prs.mapper;

import cnm.prs.dto.ExamenDetailDto;
import cnm.prs.entity.ExamenDetail;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link ExamenDetail}.
 */
public final class ExamenDetailMapper {

    private ExamenDetailMapper() {
    }

    public static ExamenDetailDto toDto(ExamenDetail entity) {
        if (entity == null) {
            return null;
        }
        ExamenDetailDto dto = new ExamenDetailDto();
        dto.setIdDetailExamen(entity.getIdDetailExamen());
        dto.setIdExamen(entity.getIdExamen());
        dto.setIdDetail(entity.getIdDetail());
        dto.setIdPtControle(entity.getIdPtControle());
        dto.setConforme(entity.getConforme());
        // observations (1,N) : peuplées par le service depuis t_observation_controle.
        dto.setObsSiNonConforme(entity.getObsSiNonConforme());
        return dto;
    }

    public static ExamenDetail toEntity(ExamenDetailDto dto) {
        if (dto == null) {
            return null;
        }
        ExamenDetail entity = new ExamenDetail();
        entity.setIdDetailExamen(dto.getIdDetailExamen());
        entity.setIdExamen(dto.getIdExamen());
        entity.setIdDetail(dto.getIdDetail());
        entity.setIdPtControle(dto.getIdPtControle());
        entity.setConforme(dto.getConforme());
        entity.setObsSiNonConforme(dto.getObsSiNonConforme());
        return entity;
    }
}
