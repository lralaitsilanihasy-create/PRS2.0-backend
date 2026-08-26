package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.LocaliteDto;
import cnm.prs.entity.Localite;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.LocaliteMapper;
import cnm.prs.repository.LocaliteRepository;

/**
 * Logique métier pour {@link Localite}.
 */
@Service
@Transactional
public class LocaliteService {

    private final LocaliteRepository repository;

    public LocaliteService(LocaliteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<LocaliteDto> findAll() {
        return repository.findAll().stream().map(LocaliteMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LocaliteDto findById(String id) {
        Localite entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Localite introuvable : " + id));
        return LocaliteMapper.toDto(entity);
    }

    public LocaliteDto create(LocaliteDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdLocalite(), repository::existsById, "localité");
        Localite entity = LocaliteMapper.toEntity(dto);
        return LocaliteMapper.toDto(repository.save(entity));
    }

    public LocaliteDto update(String id, LocaliteDto dto) {
        Localite existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Localite introuvable : " + id));
        existing.setLibelleLocalite(dto.getLibelleLocalite());
        existing.setChefLieu(dto.getChefLieu());   // ⚠️ 2026-08-03 — chef-lieu (documents officiels)
        return LocaliteMapper.toDto(repository.save(existing));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Localite introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
