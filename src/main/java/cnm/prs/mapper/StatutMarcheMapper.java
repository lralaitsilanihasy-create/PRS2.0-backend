package cnm.prs.mapper;

import cnm.prs.dto.StatutMarcheDto;
import cnm.prs.entity.StatutMarche;

/** Convertisseur entité &lt;-&gt; DTO pour {@link StatutMarche}. */
public final class StatutMarcheMapper {

    private StatutMarcheMapper() {
    }

    public static StatutMarcheDto toDto(StatutMarche entity) {
        if (entity == null) {
            return null;
        }
        StatutMarcheDto dto = new StatutMarcheDto();
        dto.setCode(entity.getCode());
        dto.setLibelle(entity.getLibelle());
        dto.setOrdre(entity.getOrdre());
        dto.setActif(entity.getActif());
        return dto;
    }

    public static StatutMarche toEntity(StatutMarcheDto dto) {
        if (dto == null) {
            return null;
        }
        StatutMarche entity = new StatutMarche();
        entity.setCode(dto.getCode() == null ? null : dto.getCode().trim());
        entity.setLibelle(dto.getLibelle() == null ? null : dto.getLibelle().trim());
        entity.setOrdre(dto.getOrdre());
        // Absent = actif : on ne crée pas un statut pour ne pas s'en servir.
        entity.setActif(dto.getActif() == null ? Boolean.TRUE : dto.getActif());
        return entity;
    }
}
