package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.PrmpDto;
import cnm.prs.entity.Prmp;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.PrmpMapper;
import cnm.prs.repository.PrmpRepository;

/**
 * Logique métier pour {@link Prmp}.
 */
@Service
@Transactional
public class PrmpService {

    private final PrmpRepository repository;

    public PrmpService(PrmpRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PrmpDto> findAll() {
        return repository.findAll().stream().map(PrmpMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PrmpDto findById(String id) {
        Prmp entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prmp introuvable : " + id));
        return PrmpMapper.toDto(entity);
    }

    public PrmpDto create(PrmpDto dto) {
        Prmp entity = PrmpMapper.toEntity(dto);
        return PrmpMapper.toDto(repository.save(entity));
    }

    public PrmpDto update(String id, PrmpDto dto) {
        Prmp existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prmp introuvable : " + id));
        existing.setNomPrmp(dto.getNomPrmp());
        existing.setPrenomsPrmp(dto.getPrenomsPrmp());
        existing.setArreteNomin(dto.getArreteNomin());
        existing.setDateNomin(dto.getDateNomin());
        existing.setCin(dto.getCin());
        existing.setDateCin(dto.getDateCin());
        existing.setLieuCin(dto.getLieuCin());
        existing.setEmailPrmp(dto.getEmailPrmp());
        existing.setTelPrmp(dto.getTelPrmp());
        return PrmpMapper.toDto(repository.save(existing));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Prmp introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
