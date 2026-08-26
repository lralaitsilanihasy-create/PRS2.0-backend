package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CompteDto;
import cnm.prs.entity.Compte;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.CompteMapper;
import cnm.prs.repository.CompteRepository;

/**
 * Logique métier pour {@link Compte}.
 */
@Service
@Transactional
public class CompteService {

    private final CompteRepository repository;

    public CompteService(CompteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CompteDto> findAll() {
        return repository.findAll().stream().map(CompteMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CompteDto findById(String id) {
        Compte entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compte introuvable : " + id));
        return CompteMapper.toDto(entity);
    }

    public CompteDto create(CompteDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getNumCompte(), repository::existsById, "compte");
        Compte entity = CompteMapper.toEntity(dto);
        return CompteMapper.toDto(repository.save(entity));
    }

    public CompteDto update(String id, CompteDto dto) {
        Compte existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compte introuvable : " + id));
        existing.setLibelle(dto.getLibelle());
        existing.setIdCatCompte(dto.getIdCatCompte());
        return CompteMapper.toDto(repository.save(existing));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Compte introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
