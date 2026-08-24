package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.EcheanceDto;
import cnm.prs.entity.Echeance;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.EcheanceMapper;
import cnm.prs.repository.EcheanceRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link Echeance}.
 *
 * <p>⚠️ Correction de périmètre — un jalon n'a <strong>pas de périmètre propre</strong> : il hérite de
 * celui de sa <strong>ligne de marché</strong> ({@code t_echeance.ID_DETAIL}), au même titre qu'un lot
 * ou qu'une date prévisionnelle. Les lectures sont donc scopées via
 * {@link MarcheService#idsMarchesVisibles()} / {@link MarcheService#controlerAccesMarche(Integer)}.
 * Auparavant ce service faisait {@code repository.findAll()} nu : le calendrier de jalons de toutes
 * les entités était lisible — et modifiable — par n'importe quel porteur de jeton.</p>
 */
@Service
@Transactional
public class EcheanceService {

    private final EcheanceRepository repository;
    private final MarcheService marcheService;

    public EcheanceService(EcheanceRepository repository, MarcheService marcheService) {
        this.repository = repository;
        this.marcheService = marcheService;
    }

    @Transactional(readOnly = true)
    public List<EcheanceDto> findAll() {
        if (Visibilite.voitTout()) {
            return repository.findAll().stream().map(EcheanceMapper::toDto).toList();
        }
        List<Integer> visibles = marcheService.idsMarchesVisibles();
        return visibles.isEmpty() ? List.of()
                : repository.findByIdDetailIn(visibles).stream().map(EcheanceMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public EcheanceDto findById(Integer id) {
        Echeance entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Echeance introuvable : " + id));
        marcheService.controlerAccesMarche(entity.getIdDetail());
        return EcheanceMapper.toDto(entity);
    }

    public EcheanceDto create(EcheanceDto dto) {
        marcheService.controlerAccesMarche(dto.getIdDetail());
        Echeance entity = EcheanceMapper.toEntity(dto);
        return EcheanceMapper.toDto(repository.save(entity));
    }

    public EcheanceDto update(Integer id, EcheanceDto dto) {
        Echeance existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Echeance introuvable : " + id));
        marcheService.controlerAccesMarche(existing.getIdDetail());   // le jalon édité
        marcheService.controlerAccesMarche(dto.getIdDetail());        // et la ligne de destination
        existing.setIdDetail(dto.getIdDetail());
        existing.setTypeJalon(dto.getTypeJalon());
        existing.setDatePrevue(dto.getDatePrevue());
        existing.setDateReelle(dto.getDateReelle());
        existing.setStatutJalon(dto.getStatutJalon());
        existing.setEcartJours(dto.getEcartJours());
        existing.setAlerteEnvoyee(dto.getAlerteEnvoyee());
        return EcheanceMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        Echeance existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Echeance introuvable : " + id));
        marcheService.controlerAccesMarche(existing.getIdDetail());
        repository.deleteById(id);
    }
}
