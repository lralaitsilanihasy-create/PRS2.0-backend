package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.TypeDossierDto;
import cnm.prs.entity.TypeDossier;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.TypeDossierMapper;
import cnm.prs.repository.TypeDossierRepository;

/**
 * Logique métier pour {@link TypeDossier}.
 */
@Service
@Transactional
public class TypeDossierService {

    private final TypeDossierRepository repository;

    public TypeDossierService(TypeDossierRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TypeDossierDto> findAll() {
        return repository.findAll().stream().map(TypeDossierMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public TypeDossierDto findById(String id) {
        TypeDossier entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TypeDossier introuvable : " + id));
        return TypeDossierMapper.toDto(entity);
    }

    public TypeDossierDto create(TypeDossierDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdTypeDossier(), repository::existsById, "type de dossier");
        TypeDossier entity = TypeDossierMapper.toEntity(dto);
        return TypeDossierMapper.toDto(repository.save(entity));
    }

    public TypeDossierDto update(String id, TypeDossierDto dto) {
        TypeDossier existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TypeDossier introuvable : " + id));
        existing.setLibelleType(dto.getLibelleType());
        return TypeDossierMapper.toDto(repository.save(existing));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("TypeDossier introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
