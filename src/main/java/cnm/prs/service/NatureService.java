package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.NatureDto;
import cnm.prs.entity.Nature;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.NatureMapper;
import cnm.prs.repository.NatureRepository;

/**
 * Logique métier pour {@link Nature}.
 */
@Service
@Transactional
public class NatureService {

    private final NatureRepository repository;

    public NatureService(NatureRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<NatureDto> findAll() {
        return repository.findAll().stream().map(NatureMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public NatureDto findById(Integer id) {
        Nature entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nature introuvable : " + id));
        return NatureMapper.toDto(entity);
    }

    public NatureDto create(NatureDto dto) {
        Nature entity = NatureMapper.toEntity(dto);
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        entity.setIdNature(ClePrimaire.reallouer(dto.getIdNature(), repository::existsById, repository::nextIdNature));
        return NatureMapper.toDto(repository.save(entity));
    }

    public NatureDto update(Integer id, NatureDto dto) {
        Nature existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nature introuvable : " + id));
        existing.setLibelle(dto.getLibelle());
        existing.setDescription(dto.getDescription());
        return NatureMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Nature introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
