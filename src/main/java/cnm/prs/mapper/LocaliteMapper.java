package cnm.prs.mapper;

import cnm.prs.dto.LocaliteDto;
import cnm.prs.entity.Localite;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link Localite}.
 */
public final class LocaliteMapper {

    private LocaliteMapper() {
    }

    public static LocaliteDto toDto(Localite entity) {
        if (entity == null) {
            return null;
        }
        LocaliteDto dto = new LocaliteDto();
        dto.setIdLocalite(entity.getIdLocalite());
        dto.setLibelleLocalite(entity.getLibelleLocalite());
        dto.setChefLieu(entity.getChefLieu());
        // ⚠️ 2026-09-03 — dérivé, jamais stocké : la centrale est définie par une constante du code
        // (Localite.ID_CENTRALE), pas par une colonne que quelqu’un pourrait cocher de travers.
        dto.setEstCentrale(Localite.estCentrale(entity.getIdLocalite()));
        return dto;
    }

    public static Localite toEntity(LocaliteDto dto) {
        if (dto == null) {
            return null;
        }
        Localite entity = new Localite();
        entity.setIdLocalite(dto.getIdLocalite());
        entity.setLibelleLocalite(dto.getLibelleLocalite());
        entity.setChefLieu(dto.getChefLieu());
        return entity;
    }
}
