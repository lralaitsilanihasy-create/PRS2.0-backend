package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.SousTypeDossierDto;
import cnm.prs.entity.SousTypeDossier;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.SousTypeDossierMapper;
import cnm.prs.repository.SousTypeDossierRepository;
import cnm.prs.repository.TypeDossierRepository;

/**
 * Logique métier pour {@link SousTypeDossier} (⚠️ règle ajoutée — référentiel des sous-types de
 * dossier, hiérarchie famille → sous-type). Liste ouverte, administrable (écritures réservées
 * ADMINISTRATEUR via SecurityConfig) ; lecture ouverte, y compris « par famille ».
 */
@Service
@Transactional
public class SousTypeDossierService {

    private final SousTypeDossierRepository repository;
    private final TypeDossierRepository typeDossierRepository;

    public SousTypeDossierService(SousTypeDossierRepository repository,
            TypeDossierRepository typeDossierRepository) {
        this.repository = repository;
        this.typeDossierRepository = typeDossierRepository;
    }

    @Transactional(readOnly = true)
    public List<SousTypeDossierDto> findAll() {
        return repository.findAll().stream().map(SousTypeDossierMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SousTypeDossierDto findById(String id) {
        SousTypeDossier entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sous-type de dossier introuvable : " + id));
        return SousTypeDossierMapper.toDto(entity);
    }

    /** Sous-types d'une famille (ex. {@code DDP} → PPM, PPM-AGPM) — 404 si la famille n'existe pas. */
    @Transactional(readOnly = true)
    public List<SousTypeDossierDto> findParFamille(String idTypeDossier) {
        if (!typeDossierRepository.existsById(idTypeDossier)) {
            throw new ResourceNotFoundException("TypeDossier (famille) introuvable : " + idTypeDossier);
        }
        return repository.findByIdTypeDossierOrderByIdSousType(idTypeDossier).stream()
                .map(SousTypeDossierMapper::toDto).toList();
    }

    public SousTypeDossierDto create(SousTypeDossierDto dto) {
        exigerFamille(dto.getIdTypeDossier());
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdSousType(), repository::existsById, "sous-type de dossier");
        SousTypeDossier entity = SousTypeDossierMapper.toEntity(dto);
        return SousTypeDossierMapper.toDto(repository.save(entity));
    }

    public SousTypeDossierDto update(String id, SousTypeDossierDto dto) {
        SousTypeDossier existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sous-type de dossier introuvable : " + id));
        exigerFamille(dto.getIdTypeDossier());
        existing.setLibelleSousType(dto.getLibelleSousType());
        existing.setIdTypeDossier(dto.getIdTypeDossier());
        return SousTypeDossierMapper.toDto(repository.save(existing));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Sous-type de dossier introuvable : " + id);
        }
        repository.deleteById(id);   // référencé par un dossier → DataIntegrityViolation → 409 (handler global)
    }

    /** La famille de rattachement doit exister (404 explicite plutôt qu'une violation FK). */
    private void exigerFamille(String idTypeDossier) {
        if (idTypeDossier == null || !typeDossierRepository.existsById(idTypeDossier)) {
            throw new ResourceNotFoundException("TypeDossier (famille) introuvable : " + idTypeDossier);
        }
    }
}
