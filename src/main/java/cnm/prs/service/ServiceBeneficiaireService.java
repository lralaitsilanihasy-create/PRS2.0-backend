package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ServiceBeneficiaireDto;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.ServiceBeneficiaireMapper;
import cnm.prs.repository.ServiceBeneficiaireRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link ServiceBeneficiaire}.
 *
 * <p>⚠️ Correction de périmètre — un service bénéficiaire n'a <strong>pas de périmètre propre</strong> :
 * il hérite de celui de sa <strong>ligne de marché</strong> ({@code t_service_beneficiaire.ID_DETAIL}),
 * comme un lot. Les lectures sont donc scopées via {@link MarcheService#idsMarchesVisibles()} /
 * {@link MarcheService#controlerAccesMarche(Integer)}, et toute écriture contrôle le marché visé.
 * Auparavant ce service faisait {@code repository.findAll()} nu : la ventilation budgétaire (SOA, compte
 * d'imputation, montants ancien/nouveau) de toutes les entités contractantes était lisible — et
 * supprimable — par n'importe quel porteur de jeton.</p>
 */
@Service
@Transactional
public class ServiceBeneficiaireService {

    private final ServiceBeneficiaireRepository repository;
    private final MarcheService marcheService;

    public ServiceBeneficiaireService(ServiceBeneficiaireRepository repository, MarcheService marcheService) {
        this.repository = repository;
        this.marcheService = marcheService;
    }

    @Transactional(readOnly = true)
    public List<ServiceBeneficiaireDto> findAll() {
        if (Visibilite.voitTout()) {
            return repository.findAll().stream().map(ServiceBeneficiaireMapper::toDto).toList();
        }
        List<Integer> visibles = marcheService.idsMarchesVisibles();
        return visibles.isEmpty() ? List.of()
                : repository.findByIdDetailIn(visibles).stream().map(ServiceBeneficiaireMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ServiceBeneficiaireDto findById(Integer id) {
        ServiceBeneficiaire entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceBeneficiaire introuvable : " + id));
        marcheService.controlerAccesMarche(entity.getIdDetail());
        return ServiceBeneficiaireMapper.toDto(entity);
    }

    public ServiceBeneficiaireDto create(ServiceBeneficiaireDto dto) {
        marcheService.controlerAccesMarche(dto.getIdDetail());
        ServiceBeneficiaire entity = ServiceBeneficiaireMapper.toEntity(dto);
        // PK serveur (seq_service_beneficiaire) ; id client ignoré — même « Voie B » que t_lot. Indispensable
        // depuis que GET /api/service-beneficiaires est scopé : le front alloue son id par max() sur la liste
        // REÇUE, désormais partielle. Un id choisi par le client viserait alors le bénéficiaire d'une autre
        // entité, que save() écraserait (merge sur PK assignée). La séquence remplace le max+1 : deux PRMP
        // ventilant en même temps ne lisent plus le même maximum.
        entity.setIdBenef(repository.nextIdBenef().intValue());
        return ServiceBeneficiaireMapper.toDto(repository.save(entity));
    }

    public ServiceBeneficiaireDto update(Integer id, ServiceBeneficiaireDto dto) {
        ServiceBeneficiaire existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceBeneficiaire introuvable : " + id));
        marcheService.controlerAccesMarche(existing.getIdDetail());   // le bénéficiaire édité
        marcheService.controlerAccesMarche(dto.getIdDetail());        // et la ligne de destination
        existing.setAncMontBenef(dto.getAncMontBenef());
        existing.setNouvMontBenef(dto.getNouvMontBenef());
        existing.setSoaCode(dto.getSoaCode());
        existing.setIdDetail(dto.getIdDetail());
        return ServiceBeneficiaireMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        ServiceBeneficiaire existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceBeneficiaire introuvable : " + id));
        marcheService.controlerAccesMarche(existing.getIdDetail());
        repository.deleteById(id);
    }
}
