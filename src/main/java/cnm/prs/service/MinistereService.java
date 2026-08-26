package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.MinistereDto;
import cnm.prs.entity.Ministere;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.MinistereMapper;
import cnm.prs.repository.MinistereRepository;

/**
 * Logique métier pour {@link Ministere}.
 */
@Service
@Transactional
public class MinistereService {

    private final MinistereRepository repository;

    public MinistereService(MinistereRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<MinistereDto> findAll() {
        return repository.findAll().stream().map(MinistereMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public MinistereDto findById(Integer id) {
        Ministere entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ministere introuvable : " + id));
        return MinistereMapper.toDto(entity);
    }

    public MinistereDto create(MinistereDto dto) {
        Ministere entity = MinistereMapper.toEntity(dto);
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        entity.setIdMinistere(ClePrimaire.reallouer(dto.getIdMinistere(), repository::existsById, repository::nextIdMinistere));
        return MinistereMapper.toDto(repository.save(entity));
    }

    public MinistereDto update(Integer id, MinistereDto dto) {
        Ministere existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ministere introuvable : " + id));
        existing.setLibelleMinistere(dto.getLibelleMinistere());
        existing.setSigle(dto.getSigle());
        return MinistereMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Ministere introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
