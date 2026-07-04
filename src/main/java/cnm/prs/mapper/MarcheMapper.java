package cnm.prs.mapper;

import cnm.prs.dto.MarcheDto;
import cnm.prs.entity.Marche;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link Marche}.
 */
public final class MarcheMapper {

    private MarcheMapper() {
    }

    public static MarcheDto toDto(Marche entity) {
        if (entity == null) {
            return null;
        }
        MarcheDto dto = new MarcheDto();
        dto.setIdDetail(entity.getIdDetail());
        dto.setIdDossier(entity.getIdDossier());
        dto.setIdPpm(entity.getIdPpm());
        dto.setDesignationMarche(entity.getDesignationMarche());
        dto.setNumCompte(entity.getNumCompte());
        dto.setMontEstim(entity.getMontEstim());
        dto.setAncienMontEstim(entity.getAncienMontEstim());
        dto.setNouvMontEstim(entity.getNouvMontEstim());
        dto.setFinancement(entity.getFinancement());
        dto.setStatut(entity.getStatut());
        dto.setIdNature(entity.getIdNature());
        dto.setIdMode(entity.getIdMode());
        return dto;
    }

    public static Marche toEntity(MarcheDto dto) {
        if (dto == null) {
            return null;
        }
        Marche entity = new Marche();
        entity.setIdDetail(dto.getIdDetail());
        entity.setIdDossier(dto.getIdDossier());
        entity.setIdPpm(dto.getIdPpm());
        entity.setDesignationMarche(dto.getDesignationMarche());
        entity.setNumCompte(dto.getNumCompte());
        entity.setMontEstim(dto.getMontEstim());
        entity.setAncienMontEstim(dto.getAncienMontEstim());
        entity.setNouvMontEstim(dto.getNouvMontEstim());
        entity.setFinancement(dto.getFinancement());
        entity.setStatut(dto.getStatut());
        entity.setIdNature(dto.getIdNature());
        entity.setIdMode(dto.getIdMode());
        return entity;
    }
}
