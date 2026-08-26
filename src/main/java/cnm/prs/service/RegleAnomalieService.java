package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.RegleAnomalieDto;
import cnm.prs.entity.RegleAnomalie;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.RegleAnomalieMapper;
import cnm.prs.repository.RegleAnomalieRepository;

/**
 * Logique métier pour {@link RegleAnomalie}.
 */
@Service
@Transactional
public class RegleAnomalieService {

    private final RegleAnomalieRepository repository;

    public RegleAnomalieService(RegleAnomalieRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RegleAnomalieDto> findAll() {
        return repository.findAll().stream().map(RegleAnomalieMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RegleAnomalieDto findById(Integer id) {
        RegleAnomalie entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RegleAnomalie introuvable : " + id));
        return RegleAnomalieMapper.toDto(entity);
    }

    public RegleAnomalieDto create(RegleAnomalieDto dto) {
        RegleAnomalie entity = RegleAnomalieMapper.toEntity(dto);
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        entity.setIdRegleAnomalie(ClePrimaire.reallouer(dto.getIdRegleAnomalie(), repository::existsById, repository::nextIdRegleAnomalie));
        return RegleAnomalieMapper.toDto(repository.save(entity));
    }

    public RegleAnomalieDto update(Integer id, RegleAnomalieDto dto) {
        RegleAnomalie existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RegleAnomalie introuvable : " + id));
        existing.setCodeRegle(dto.getCodeRegle());
        existing.setLibelle(dto.getLibelle());
        existing.setParametreNum(dto.getParametreNum());
        existing.setParametreTxt(dto.getParametreTxt());
        existing.setActif(dto.getActif());
        existing.setGraviteDefaut(dto.getGraviteDefaut());
        return RegleAnomalieMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("RegleAnomalie introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
