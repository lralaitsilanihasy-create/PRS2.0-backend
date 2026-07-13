package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.LotDto;
import cnm.prs.entity.Lot;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.LotMapper;
import cnm.prs.repository.LotRepository;

/**
 * Logique métier pour {@link Lot}.
 */
@Service
@Transactional
public class LotService {

    private final LotRepository repository;

    public LotService(LotRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<LotDto> findAll() {
        return repository.findAll().stream().map(LotMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LotDto findById(Integer id) {
        Lot entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable : " + id));
        return LotMapper.toDto(entity);
    }

    /** Lots d'une ligne de marché (liste, éventuellement vide si aucun ou marché inconnu). */
    @Transactional(readOnly = true)
    public List<LotDto> findByMarche(Integer idDetail) {
        return repository.findByIdDetail(idDetail).stream().map(LotMapper::toDto).toList();
    }

    /** Lots d'un dossier — tous les lots de ses lignes de marché (liste, vide si aucun ou dossier inconnu). */
    @Transactional(readOnly = true)
    public List<LotDto> findByDossier(Integer idDossier) {
        return repository.findByIdDossier(idDossier).stream().map(LotMapper::toDto).toList();
    }

    public LotDto create(LotDto dto) {
        Lot entity = LotMapper.toEntity(dto);
        return LotMapper.toDto(repository.save(entity));
    }

    public LotDto update(Integer id, LotDto dto) {
        Lot existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable : " + id));
        existing.setIdDossier(dto.getIdDossier());
        existing.setIdDetail(dto.getIdDetail());
        existing.setDesignationLot(dto.getDesignationLot());
        existing.setMontLot(dto.getMontLot());
        existing.setQteLot(dto.getQteLot());
        existing.setUniteLot(dto.getUniteLot());
        return LotMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Lot introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
