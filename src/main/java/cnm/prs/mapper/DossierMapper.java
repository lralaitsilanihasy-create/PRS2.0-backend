package cnm.prs.mapper;

import cnm.prs.dto.DossierDto;
import cnm.prs.entity.Dossier;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link Dossier}.
 */
public final class DossierMapper {

    private DossierMapper() {
    }

    public static DossierDto toDto(Dossier entity) {
        if (entity == null) {
            return null;
        }
        DossierDto dto = new DossierDto();
        dto.setIdDossier(entity.getIdDossier());
        dto.setIdTypeDossier(entity.getIdTypeDossier());
        dto.setIdSousType(entity.getIdSousType());
        dto.setIdDossierParent(entity.getIdDossierParent());
        dto.setRefeDossier(entity.getRefeDossier());
        dto.setDateRef(entity.getDateRef());
        dto.setStatut(entity.getStatut());
        dto.setIdLocalite(entity.getIdLocalite());
        dto.setIdPrmp(entity.getIdPrmp());
        dto.setIdEntiteContract(entity.getIdEntiteContract());
        return dto;
    }

    public static Dossier toEntity(DossierDto dto) {
        if (dto == null) {
            return null;
        }
        Dossier entity = new Dossier();
        entity.setIdDossier(dto.getIdDossier());
        entity.setIdTypeDossier(dto.getIdTypeDossier());
        entity.setIdSousType(dto.getIdSousType());
        entity.setIdDossierParent(dto.getIdDossierParent());
        entity.setRefeDossier(dto.getRefeDossier());
        entity.setDateRef(dto.getDateRef());
        entity.setStatut(dto.getStatut());
        entity.setIdLocalite(dto.getIdLocalite());
        entity.setIdPrmp(dto.getIdPrmp());
        entity.setIdEntiteContract(dto.getIdEntiteContract());
        return entity;
    }
}
