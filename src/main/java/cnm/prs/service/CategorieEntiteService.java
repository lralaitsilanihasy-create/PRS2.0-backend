package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CategorieEntiteDto;
import cnm.prs.entity.CategorieEntite;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.CategorieEntiteMapper;
import cnm.prs.repository.CategorieEntiteRepository;

/**
 * Logique métier pour {@link CategorieEntite} (référentiel {@code tr_categorie_entite}).
 */
@Service
@Transactional
public class CategorieEntiteService {

    private final CategorieEntiteRepository repository;

    public CategorieEntiteService(CategorieEntiteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CategorieEntiteDto> findAll() {
        return repository.findAll().stream().map(CategorieEntiteMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CategorieEntiteDto findById(String id) {
        CategorieEntite entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie d'entité introuvable : " + id));
        return CategorieEntiteMapper.toDto(entity);
    }

    public CategorieEntiteDto create(CategorieEntiteDto dto) {
        CategorieEntite entity = CategorieEntiteMapper.toEntity(dto);
        return CategorieEntiteMapper.toDto(repository.save(entity));
    }

    public CategorieEntiteDto update(String id, CategorieEntiteDto dto) {
        CategorieEntite existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie d'entité introuvable : " + id));
        existing.setNiveauHierarchique(dto.getNiveauHierarchique());
        return CategorieEntiteMapper.toDto(repository.save(existing));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Catégorie d'entité introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
