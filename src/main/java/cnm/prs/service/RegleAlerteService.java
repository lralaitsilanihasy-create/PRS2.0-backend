package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.RegleAlerteDto;
import cnm.prs.entity.RegleAlerte;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.RegleAlerteMapper;
import cnm.prs.repository.RegleAlerteRepository;

/**
 * Logique métier pour {@link RegleAlerte}.
 */
@Service
@Transactional
public class RegleAlerteService {

    private final RegleAlerteRepository repository;

    public RegleAlerteService(RegleAlerteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RegleAlerteDto> findAll() {
        return repository.findAll().stream().map(RegleAlerteMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RegleAlerteDto findById(Integer id) {
        RegleAlerte entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RegleAlerte introuvable : " + id));
        return RegleAlerteMapper.toDto(entity);
    }

    public RegleAlerteDto create(RegleAlerteDto dto) {
        RegleAlerte entity = RegleAlerteMapper.toEntity(dto);
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        entity.setIdRegleAlerte(ClePrimaire.reallouer(dto.getIdRegleAlerte(), repository::existsById, repository::nextIdRegleAlerte));
        return RegleAlerteMapper.toDto(repository.save(entity));
    }

    public RegleAlerteDto update(Integer id, RegleAlerteDto dto) {
        RegleAlerte existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RegleAlerte introuvable : " + id));
        existing.setTypeJalon(dto.getTypeJalon());
        existing.setJoursAvant(dto.getJoursAvant());
        existing.setDestinataireProfil(dto.getDestinataireProfil());
        existing.setActif(dto.getActif());
        return RegleAlerteMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("RegleAlerte introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
