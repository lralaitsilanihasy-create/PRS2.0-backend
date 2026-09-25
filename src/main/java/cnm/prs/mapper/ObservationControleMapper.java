package cnm.prs.mapper;

import cnm.prs.dto.ObservationControleDto;
import cnm.prs.entity.ObservationControle;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link ObservationControle}.
 */
public final class ObservationControleMapper {

    private ObservationControleMapper() {
    }

    public static ObservationControleDto toDto(ObservationControle entity) {
        if (entity == null) {
            return null;
        }
        ObservationControleDto dto = new ObservationControleDto();
        dto.setIdObservation(entity.getIdObservation());
        dto.setIdDetail(entity.getIdDetail());
        dto.setAuLieuDe(entity.getAuLieuDe());
        dto.setLire(entity.getLire());
        dto.setOrdre(entity.getOrdre());
        dto.setChamp(entity.getChampCible());
        dto.setIdMarcheCible(entity.getIdMarcheCible());
        dto.setIdBenefCible(entity.getIdBenefCible());
        // ⚠️ V44 (2026-09-25) — l'information de la fiche visée, libellé et valeur figés.
        dto.setIdDmc(entity.getIdDmcFiche());
        dto.setChampFiche(entity.getChampFiche());
        dto.setLibelleChampFiche(entity.getLibelleChampFiche());
        dto.setValeurChampFiche(entity.getValeurChampFiche());
        dto.setLot(cnm.prs.service.LotsFiche.lotDe(entity.getChampFiche()));
        return dto;
    }

    public static ObservationControle toEntity(ObservationControleDto dto) {
        if (dto == null) {
            return null;
        }
        ObservationControle entity = new ObservationControle();
        entity.setIdObservation(dto.getIdObservation());
        entity.setIdDetail(dto.getIdDetail());
        entity.setAuLieuDe(dto.getAuLieuDe());
        entity.setLire(dto.getLire());
        entity.setOrdre(dto.getOrdre());
        entity.setChampCible(dto.getChamp());
        entity.setIdMarcheCible(dto.getIdMarcheCible());
        entity.setIdBenefCible(dto.getIdBenefCible());
        // ⚠️ V44 — libellé et valeur viennent du validateur (jamais du client), qui les a posés dans le DTO.
        entity.setIdDmcFiche(dto.getIdDmc());
        entity.setChampFiche(dto.getChampFiche());
        entity.setLibelleChampFiche(dto.getLibelleChampFiche());
        entity.setValeurChampFiche(dto.getValeurChampFiche());
        return entity;
    }
}
