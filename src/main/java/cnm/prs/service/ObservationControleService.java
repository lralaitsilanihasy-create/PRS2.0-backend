package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ObservationControleDto;
import cnm.prs.entity.ObservationControle;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.ObservationControleMapper;
import cnm.prs.repository.ObservationControleRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link ObservationControle} (lignes « AU LIEU DE / LIRE » des points de contrôle).
 *
 * <p>⚠️ Audit 2026-08-27, constat C2 — §1/§3.1 : {@code findByDetail} servait les observations
 * <strong>internes</strong> de la commission à tout authentifié, toutes localités confondues et
 * pendant la navette. La lecture est désormais bornée par {@link Visibilite}, sur la même chaîne que
 * le détail d'examen parent — hors localité (et pour la PRMP/UGPM) : liste vide.</p>
 */
@Service
@Transactional
public class ObservationControleService {

    private final ObservationControleRepository repository;

    public ObservationControleService(ObservationControleRepository repository) {
        this.repository = repository;
    }

    /** ⚠️ C2 — lignes du point de contrôle, bornées au périmètre (§1) : vide hors localité / pour la PRMP. */
    @Transactional(readOnly = true)
    public List<ObservationControleDto> findByDetail(Integer idDetail) {
        return Visibilite.filtrer(
                        () -> repository.findByIdDetailOrderByOrdreAsc(idDetail),
                        loc -> repository.findByIdDetailEtLocalite(idDetail, loc))
                .stream().map(ObservationControleMapper::toDto).toList();
    }

    public ObservationControleDto create(ObservationControleDto dto) {
        ObservationControle entity = ObservationControleMapper.toEntity(dto);
        entity.setIdObservation(null);   // PK auto (IDENTITY) ; tout id fourni est ignoré
        return ObservationControleMapper.toDto(repository.save(entity));
    }

    public ObservationControleDto update(Integer id, ObservationControleDto dto) {
        ObservationControle existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Observation introuvable : " + id));
        existing.setIdDetail(dto.getIdDetail());
        existing.setAuLieuDe(dto.getAuLieuDe());
        existing.setLire(dto.getLire());
        existing.setOrdre(dto.getOrdre());
        return ObservationControleMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Observation introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
