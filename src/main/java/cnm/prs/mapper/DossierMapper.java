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
        dto.setIdMandatAttrib(entity.getIdMandatAttrib());
        dto.setIdEntiteContract(entity.getIdEntiteContract());
        // ⚠️ Demande front (2026-08-19) — traçabilité de la saisie. Les logins bruts viennent de
        // l'entité ; les noms lisibles (creeParNom / soumisParNom) sont résolus par le service
        // (annuaire des acteurs), en lot pour les listes.
        dto.setCreePar(entity.getCreePar());
        dto.setSoumisPar(entity.getSoumisPar());
        dto.setVersion(entity.getVersion());   // ⚠️ verrou optimiste (docs/plan-conflit-version.md)
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
