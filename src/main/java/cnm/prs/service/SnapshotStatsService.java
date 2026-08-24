package cnm.prs.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.SnapshotStatsDto;
import cnm.prs.entity.SnapshotStats;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.SnapshotStatsMapper;
import cnm.prs.repository.SnapshotStatsRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link SnapshotStats}.
 *
 * <p>⚠️ Correction de périmètre — l'instantané statistique porte une localité
 * ({@code t_snapshot_stats.ID_LOCALITE}) : c'est exactement le motif habituel des ressources internes
 * du circuit (§1) — Président/Administrateur voient tout, un contrôleur voit sa localité, la PRMP ne
 * voit rien. Auparavant ce service faisait {@code repository.findAll()} nu : le bilan chiffré de toutes
 * les localités du CNM sortait sur simple présentation d'un jeton, PRMP comprise.</p>
 *
 * <p><strong>Instantané national</strong> ({@code ID_LOCALITE} nul, agrégat toutes localités) : réservé
 * au Président/Administrateur — il n'appartient à aucune localité, l'ouvrir à un contrôleur reviendrait
 * à lui livrer la consolidation nationale sous couvert d'un périmètre local.</p>
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
        return Visibilite.filtrer(repository::findAll, repository::findByIdLocalite)
                .stream().map(SnapshotStatsMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SnapshotStatsDto findById(Integer id) {
        SnapshotStats entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SnapshotStats introuvable : " + id));
        if (entity.getIdLocalite() == null && !Visibilite.voitTout()) {
            throw new AccessDeniedException(
                    "Instantané national : consultation réservée au Président et à l'Administrateur (§1).");
        }
        Visibilite.controler(loc -> repository.existsByIdSnapshotAndIdLocalite(id, loc));
        return SnapshotStatsMapper.toDto(entity);
    }

    public SnapshotStatsDto create(SnapshotStatsDto dto) {
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
