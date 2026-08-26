package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DelegationProfilDto;
import cnm.prs.entity.DelegationProfil;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.DelegationProfilMapper;
import cnm.prs.repository.DelegationProfilRepository;

/**
 * Logique métier pour {@link DelegationProfil}.
 */
@Service
@Transactional
public class DelegationProfilService {

    private final DelegationProfilRepository repository;

    public DelegationProfilService(DelegationProfilRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DelegationProfilDto> findAll() {
        return repository.findAll().stream().map(DelegationProfilMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public DelegationProfilDto findById(Integer id) {
        DelegationProfil entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DelegationProfil introuvable : " + id));
        return DelegationProfilMapper.toDto(entity);
    }

    public DelegationProfilDto create(DelegationProfilDto dto) {
        DelegationProfil entity = DelegationProfilMapper.toEntity(dto);
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        entity.setIdDelegation(ClePrimaire.reallouer(dto.getIdDelegation(), repository::existsById, repository::nextIdDelegation));
        return DelegationProfilMapper.toDto(repository.save(entity));
    }

    public DelegationProfilDto update(Integer id, DelegationProfilDto dto) {
        DelegationProfil existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DelegationProfil introuvable : " + id));
        existing.setIdProfileDelegant(dto.getIdProfileDelegant());
        existing.setIdProfileDelegue(dto.getIdProfileDelegue());
        existing.setActif(dto.getActif());
        return DelegationProfilMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("DelegationProfil introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
