package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.SnapshotStatsDto;
import cnm.prs.entity.SnapshotStats;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.SnapshotStatsMapper;
import cnm.prs.repository.SnapshotStatsRepository;

/**
 * Logique métier pour {@link SnapshotStats}.
 */
@Service
@Transactional
public class SnapshotStatsService {

    private final SnapshotStatsRepository repository;

    public SnapshotStatsService(SnapshotStatsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SnapshotStatsDto> findAll() {
        return repository.findAll().stream().map(SnapshotStatsMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SnapshotStatsDto findById(Integer id) {
        SnapshotStats entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SnapshotStats introuvable : " + id));
        return SnapshotStatsMapper.toDto(entity);
    }

    public SnapshotStatsDto create(SnapshotStatsDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdSnapshot(), repository::existsById, "statistique");
        SnapshotStats entity = SnapshotStatsMapper.toEntity(dto);
        return SnapshotStatsMapper.toDto(repository.save(entity));
    }

    public SnapshotStatsDto update(Integer id, SnapshotStatsDto dto) {
        SnapshotStats existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SnapshotStats introuvable : " + id));
        existing.setDateSnapshot(dto.getDateSnapshot());
        existing.setIdLocalite(dto.getIdLocalite());
        existing.setExercice(dto.getExercice());
        existing.setNbDossiersRecus(dto.getNbDossiersRecus());
        existing.setNbDossiersClotures(dto.getNbDossiersClotures());
        existing.setNbDossiersEnCours(dto.getNbDossiersEnCours());
        existing.setTauxConformite(dto.getTauxConformite());
        existing.setDelaiMoyenJours(dto.getDelaiMoyenJours());
        existing.setMontTotalControle(dto.getMontTotalControle());
        existing.setNbRetoursMoyen(dto.getNbRetoursMoyen());
        return SnapshotStatsMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("SnapshotStats introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
