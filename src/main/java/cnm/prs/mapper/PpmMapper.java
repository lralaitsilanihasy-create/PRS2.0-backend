package cnm.prs.mapper;

import cnm.prs.dto.PpmDto;
import cnm.prs.entity.Ppm;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link Ppm}.
 */
public final class PpmMapper {

    private PpmMapper() {
    }

    public static PpmDto toDto(Ppm entity) {
        if (entity == null) {
            return null;
        }
        PpmDto dto = new PpmDto();
        dto.setIdPpm(entity.getIdPpm());
        dto.setIdDossier(entity.getIdDossier());
        dto.setExercice(entity.getExercice());
        dto.setSignataire(entity.getSignataire());
        dto.setDateSignature(entity.getDateSignature());
        dto.setDatePpmInit(entity.getDatePpmInit());
        dto.setNumMajPrec(entity.getNumMajPrec());
        dto.setDateMajPrec(entity.getDateMajPrec());
        dto.setNumMaj(entity.getNumMaj());
        dto.setDateMaj(entity.getDateMaj());
        dto.setReference(entity.getReference());
        dto.setLibelle(entity.getLibelle());
        dto.setDateReceptionCnm(entity.getDateReceptionCnm());
        dto.setIdLocalite(entity.getIdLocalite());
        dto.setVu(entity.getVu());
        dto.setIdPrmp(entity.getIdPrmp());
        dto.setMotifMaj(entity.getMotifMaj());
        dto.setVersion(entity.getVersion());   // ⚠️ verrou optimiste (docs/plan-conflit-version.md)
        return dto;
    }

    public static Ppm toEntity(PpmDto dto) {
        if (dto == null) {
            return null;
        }
        Ppm entity = new Ppm();
        entity.setIdPpm(dto.getIdPpm());
        entity.setIdDossier(dto.getIdDossier());
        entity.setExercice(dto.getExercice());
        entity.setSignataire(dto.getSignataire());
        entity.setDateSignature(dto.getDateSignature());
        entity.setDatePpmInit(dto.getDatePpmInit());
        entity.setNumMajPrec(dto.getNumMajPrec());
        entity.setDateMajPrec(dto.getDateMajPrec());
        entity.setNumMaj(dto.getNumMaj());
        entity.setDateMaj(dto.getDateMaj());
        entity.setReference(dto.getReference());
        entity.setLibelle(dto.getLibelle());
        entity.setDateReceptionCnm(dto.getDateReceptionCnm());
        entity.setIdLocalite(dto.getIdLocalite());
        entity.setVu(dto.getVu());
        entity.setIdPrmp(dto.getIdPrmp());
        entity.setMotifMaj(dto.getMotifMaj());
        return entity;
    }
}
