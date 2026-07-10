package cnm.prs.mapper;

import cnm.prs.dto.DmcDto;
import cnm.prs.entity.DossierMec;

/**
 * Convertisseur entité -&gt; DTO pour {@link DossierMec}. Le code/libellé du type sont lus via
 * l'association {@code typeDmc} (chargée dans la transaction du service).
 */
public final class DmcMapper {

    private DmcMapper() {
    }

    public static DmcDto toDto(DossierMec e) {
        if (e == null) {
            return null;
        }
        DmcDto dto = new DmcDto();
        dto.setIdDmc(e.getIdDmc());
        dto.setIdDetail(e.getIdDetail());
        dto.setIdTypeDmc(e.getIdTypeDmc());
        if (e.getTypeDmc() != null) {
            dto.setTypeDmcCode(e.getTypeDmc().getCode());
            dto.setTypeDmcLibelle(e.getTypeDmc().getLibelle());
        }
        dto.setReference(e.getReference());
        dto.setStatut(e.getStatut() != null ? e.getStatut().name() : null);
        dto.setDateCreation(e.getDateCreation());
        return dto;
    }
}
