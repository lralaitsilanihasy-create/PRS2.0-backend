package cnm.prs.mapper;

import cnm.prs.dto.PrmpDto;
import cnm.prs.entity.Prmp;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link Prmp}.
 */
public final class PrmpMapper {

    private PrmpMapper() {
    }

    public static PrmpDto toDto(Prmp entity) {
        if (entity == null) {
            return null;
        }
        PrmpDto dto = new PrmpDto();
        dto.setIdPrmp(entity.getIdPrmp());
        dto.setNomPrmp(entity.getNomPrmp());
        dto.setPrenomsPrmp(entity.getPrenomsPrmp());
        dto.setArreteNomin(entity.getArreteNomin());
        dto.setDateNomin(entity.getDateNomin());
        dto.setCin(entity.getCin());
        dto.setDateCin(entity.getDateCin());
        dto.setLieuCin(entity.getLieuCin());
        dto.setEmailPrmp(entity.getEmailPrmp());
        dto.setTelPrmp(entity.getTelPrmp());
        return dto;
    }

    public static Prmp toEntity(PrmpDto dto) {
        if (dto == null) {
            return null;
        }
        Prmp entity = new Prmp();
        entity.setIdPrmp(dto.getIdPrmp());
        entity.setNomPrmp(dto.getNomPrmp());
        entity.setPrenomsPrmp(dto.getPrenomsPrmp());
        entity.setArreteNomin(dto.getArreteNomin());
        entity.setDateNomin(dto.getDateNomin());
        entity.setCin(dto.getCin());
        entity.setDateCin(dto.getDateCin());
        entity.setLieuCin(dto.getLieuCin());
        entity.setEmailPrmp(dto.getEmailPrmp());
        entity.setTelPrmp(dto.getTelPrmp());
        return entity;
    }
}
