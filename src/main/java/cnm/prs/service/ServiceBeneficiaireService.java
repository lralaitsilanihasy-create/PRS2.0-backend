package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ServiceBeneficiaireDto;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.ServiceBeneficiaireMapper;
import cnm.prs.repository.ServiceBeneficiaireRepository;
import cnm.prs.security.PerimetreDossier;

/**
 * Logique métier pour {@link ServiceBeneficiaire}.
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §1/§3.1 : CRUD auparavant sans aucune garde, alors que la ligne porte
 * des montants par service bénéficiaire. Rattachement au dossier via la ligne de marché
 * ({@code ID_DETAIL → t_marche.ID_DOSSIER}) : lectures bornées au périmètre, écritures réservées au
 * brouillon de la PRMP propriétaire.</p>
 */
@Service
@Transactional
public class ServiceBeneficiaireService {

    private final ServiceBeneficiaireRepository repository;
    private final PerimetreDossier perimetre;
    private final EnfantDossierGarde garde;

    public ServiceBeneficiaireService(ServiceBeneficiaireRepository repository, PerimetreDossier perimetre,
            EnfantDossierGarde garde) {
        this.repository = repository;
        this.perimetre = perimetre;
        this.garde = garde;
    }

    @Transactional(readOnly = true)
    public List<ServiceBeneficiaireDto> findAll() {
        return perimetre.filtrer(repository::findAll, repository::findParDossiers)
                .stream().map(ServiceBeneficiaireMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ServiceBeneficiaireDto findById(Integer id) {
        ServiceBeneficiaire entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceBeneficiaire introuvable : " + id));
        perimetre.controler(repository.findIdDossier(id).orElse(null));
        return ServiceBeneficiaireMapper.toDto(entity);
    }

    public ServiceBeneficiaireDto create(ServiceBeneficiaireDto dto) {
        garde.exigerEcritureSurMarche(dto.getIdDetail());
        ServiceBeneficiaire entity = ServiceBeneficiaireMapper.toEntity(dto);
        return ServiceBeneficiaireMapper.toDto(repository.save(entity));
    }

    public ServiceBeneficiaireDto update(Integer id, ServiceBeneficiaireDto dto) {
        ServiceBeneficiaire existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceBeneficiaire introuvable : " + id));
        garde.exigerEcritureSurMarche(existing.getIdDetail());   // marché ACTUEL
        garde.exigerEcritureSurMarche(dto.getIdDetail());        // marché CIBLE demandé
        existing.setAncMontBenef(dto.getAncMontBenef());
        existing.setNouvMontBenef(dto.getNouvMontBenef());
        existing.setSoaCode(dto.getSoaCode());
        existing.setIdDetail(dto.getIdDetail());
        return ServiceBeneficiaireMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        ServiceBeneficiaire existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceBeneficiaire introuvable : " + id));
        garde.exigerEcritureSurMarche(existing.getIdDetail());
        repository.deleteById(id);
    }
}
