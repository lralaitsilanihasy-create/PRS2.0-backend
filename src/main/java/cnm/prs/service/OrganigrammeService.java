package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.OrganigrammeDto;
import cnm.prs.entity.Organigramme;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.OrganigrammeMapper;
import cnm.prs.repository.OrganigrammeRepository;

/**
 * Logique métier pour {@link Organigramme}.
 */
@Service
@Transactional
public class OrganigrammeService {

    private final OrganigrammeRepository repository;

    public OrganigrammeService(OrganigrammeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<OrganigrammeDto> findAll() {
        return repository.findAll().stream().map(OrganigrammeMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public OrganigrammeDto findById(Integer id) {
        Organigramme entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organigramme introuvable : " + id));
        return OrganigrammeMapper.toDto(entity);
    }

    public OrganigrammeDto create(OrganigrammeDto dto) {
        Organigramme entity = OrganigrammeMapper.toEntity(dto);
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        entity.setIdOrganigramme(ClePrimaire.reallouer(dto.getIdOrganigramme(), repository::existsById, repository::nextIdOrganigramme));
        return OrganigrammeMapper.toDto(repository.save(entity));
    }

    public OrganigrammeDto update(Integer id, OrganigrammeDto dto) {
        Organigramme existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organigramme introuvable : " + id));
        existing.setIdMinistere(dto.getIdMinistere());
        existing.setLibelle(dto.getLibelle());
        existing.setVersion(dto.getVersion());
        existing.setDateValidation(dto.getDateValidation());
        existing.setActif(dto.getActif());
        return OrganigrammeMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Organigramme introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
