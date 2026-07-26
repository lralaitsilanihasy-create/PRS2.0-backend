package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.EntiteContractDto;
import cnm.prs.dto.EntitePubliqueDto;
import cnm.prs.entity.CategorieEntite;
import cnm.prs.entity.EntiteContract;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.EntiteContractMapper;
import cnm.prs.mapper.EntitePubliqueMapper;
import cnm.prs.repository.CategorieEntiteRepository;
import cnm.prs.repository.EntiteContractRepository;

/**
 * Logique métier pour {@link EntiteContract}.
 */
@Service
@Transactional
public class EntiteContractService {

    private final EntiteContractRepository repository;
    private final CategorieEntiteRepository categorieEntiteRepository;

    public EntiteContractService(EntiteContractRepository repository,
            CategorieEntiteRepository categorieEntiteRepository) {
        this.repository = repository;
        this.categorieEntiteRepository = categorieEntiteRepository;
    }

    @Transactional(readOnly = true)
    public List<EntiteContractDto> findAll() {
        return repository.findAll().stream().map(EntiteContractMapper::toDto).toList();
    }

    /** Liste publique réduite (pour l'écran d'inscription PRMP, route non authentifiée). */
    @Transactional(readOnly = true)
    public List<EntitePubliqueDto> listePublique() {
        return repository.findAll().stream().map(EntitePubliqueMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public EntiteContractDto findById(Integer id) {
        EntiteContract entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EntiteContract introuvable : " + id));
        return EntiteContractMapper.toDto(entity);
    }

    public EntiteContractDto create(EntiteContractDto dto) {
        EntiteContract entity = EntiteContractMapper.toEntity(dto);
        deriverNiveau(entity);
        return EntiteContractMapper.toDto(repository.save(entity));
    }

    public EntiteContractDto update(Integer id, EntiteContractDto dto) {
        EntiteContract existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EntiteContract introuvable : " + id));
        existing.setLibelleEntite(dto.getLibelleEntite());
        existing.setAdresse(dto.getAdresse());
        existing.setCategorieEntite(dto.getCategorieEntite());
        existing.setIdOrganigramme(dto.getIdOrganigramme());
        existing.setIdEntiteParent(dto.getIdEntiteParent());
        deriverNiveau(existing);   // niveauHierarchique DÉRIVÉ de la catégorie (source unique) — valeur client ignorée
        return EntiteContractMapper.toDto(repository.save(existing));
    }

    /**
     * ⚠️ Règle ajoutée (2026-07-26) — <strong>dérive</strong> {@code niveauHierarchique} depuis
     * {@code categorieEntite} via le référentiel {@link CategorieEntite} ({@code tr_categorie_entite}) :
     * <strong>source unique</strong>, l'entité et sa catégorie ne peuvent plus diverger. Catégorie absente/vide
     * → niveau {@code null}. Catégorie <strong>inconnue</strong> du référentiel → {@code 400}.
     */
    private void deriverNiveau(EntiteContract entity) {
        String cat = entity.getCategorieEntite();
        if (cat == null || cat.isBlank()) {
            entity.setCategorieEntite(null);
            entity.setNiveauHierarchique(null);
            return;
        }
        CategorieEntite ref = categorieEntiteRepository.findById(cat.trim())
                .orElseThrow(() -> new BadRequestException(
                        "Catégorie d'entité « " + cat + " » inconnue au référentiel (tr_categorie_entite)."));
        entity.setCategorieEntite(ref.getLibelle());
        entity.setNiveauHierarchique(ref.getNiveauHierarchique());
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("EntiteContract introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
