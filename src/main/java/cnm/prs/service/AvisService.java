package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.AvisDto;
import cnm.prs.entity.Avis;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.AvisMapper;
import cnm.prs.repository.AvisRepository;

/**
 * Logique métier pour {@link Avis}.
 */
@Service
@Transactional
public class AvisService {

    private final AvisRepository repository;

    public AvisService(AvisRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AvisDto> findAll() {
        return repository.findAll().stream().map(AvisMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AvisDto findById(String id) {
        Avis entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Avis introuvable : " + id));
        return AvisMapper.toDto(entity);
    }

    public AvisDto create(AvisDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdAvis(), repository::existsById, "avis");
        Avis entity = AvisMapper.toEntity(dto);
        return AvisMapper.toDto(repository.save(entity));
    }

    public AvisDto update(String id, AvisDto dto) {
        Avis existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Avis introuvable : " + id));
        existing.setLibelleAvis(dto.getLibelleAvis());
        return AvisMapper.toDto(repository.save(existing));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Avis introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
