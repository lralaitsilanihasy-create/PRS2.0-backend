package cnm.prs.mapper;

import cnm.prs.dto.ServiceBeneficiaireDto;
import cnm.prs.entity.ServiceBeneficiaire;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link ServiceBeneficiaire}.
 */
public final class ServiceBeneficiaireMapper {

    private ServiceBeneficiaireMapper() {
    }

    public static ServiceBeneficiaireDto toDto(ServiceBeneficiaire entity) {
        if (entity == null) {
            return null;
        }
        ServiceBeneficiaireDto dto = new ServiceBeneficiaireDto();
        dto.setIdBenef(entity.getIdBenef());
        dto.setAncMontBenef(entity.getAncMontBenef());
        dto.setNouvMontBenef(entity.getNouvMontBenef());
        dto.setSoaCode(entity.getSoaCode());
        dto.setNumCompte(entity.getNumCompte());
        dto.setIdDetail(entity.getIdDetail());
        return dto;
    }

    public static ServiceBeneficiaire toEntity(ServiceBeneficiaireDto dto) {
        if (dto == null) {
            return null;
        }
        ServiceBeneficiaire entity = new ServiceBeneficiaire();
        entity.setIdBenef(dto.getIdBenef());
        entity.setAncMontBenef(dto.getAncMontBenef());
        entity.setNouvMontBenef(dto.getNouvMontBenef());
        entity.setSoaCode(dto.getSoaCode());
        entity.setNumCompte(dto.getNumCompte());
        entity.setIdDetail(dto.getIdDetail());
        return entity;
    }
}
