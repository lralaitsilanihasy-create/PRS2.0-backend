package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CatCompteDto;
import cnm.prs.entity.CatCompte;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.CatCompteMapper;
import cnm.prs.repository.CatCompteRepository;

/**
 * Logique métier pour {@link CatCompte}.
 */
@Service
@Transactional
public class CatCompteService {

    private final CatCompteRepository repository;

    public CatCompteService(CatCompteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CatCompteDto> findAll() {
        return repository.findAll().stream().map(CatCompteMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CatCompteDto findById(String id) {
        CatCompte entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CatCompte introuvable : " + id));
        return CatCompteMapper.toDto(entity);
    }

    public CatCompteDto create(CatCompteDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdCatCompte(), repository::existsById, "catégorie de compte");
        CatCompte entity = CatCompteMapper.toEntity(dto);
        return CatCompteMapper.toDto(repository.save(entity));
    }

    public CatCompteDto update(String id, CatCompteDto dto) {
        CatCompte existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CatCompte introuvable : " + id));
        existing.setCatCompte(dto.getCatCompte());
        return CatCompteMapper.toDto(repository.save(existing));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("CatCompte introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
