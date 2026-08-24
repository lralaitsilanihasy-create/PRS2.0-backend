package cnm.prs.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.AnomalieDto;
import cnm.prs.entity.Anomalie;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.AnomalieMapper;
import cnm.prs.repository.AnomalieRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link Anomalie}.
 *
 * <p>⚠️ Correction de périmètre — une anomalie signale un défaut sur une <strong>ligne de marché</strong>
 * ({@code t_anomalie.ID_DETAIL}) : elle n'a pas de périmètre propre, elle hérite de celui de la ligne
 * qu'elle vise. Les lectures sont donc scopées via {@link MarcheService#idsMarchesVisibles()}.
 * Auparavant ce service faisait {@code repository.findAll()} nu : les anomalies de toutes les entités —
 * description et commentaire de traitement compris — étaient lisibles par n'importe quel porteur de jeton.</p>
 *
 * <p><strong>Anomalie sans ligne rattachée</strong> ({@code ID_DETAIL} nul, anomalie de niveau PPM) :
 * visible du seul Président/Administrateur. Elle n'a aucun parent dont hériter, et lui dériver un
 * périmètre par {@code ID_PPM} ouvrirait un second chemin de visibilité qu'aucun usage ne vient
 * éprouver ; le refus est ici la lecture sûre.</p>
 */
@Service
@Transactional
public class AnomalieService {

    private final AnomalieRepository repository;
    private final MarcheService marcheService;

    public AnomalieService(AnomalieRepository repository, MarcheService marcheService) {
        this.repository = repository;
        this.marcheService = marcheService;
    }

    @Transactional(readOnly = true)
    public List<AnomalieDto> findAll() {
        if (Visibilite.voitTout()) {
            return repository.findAll().stream().map(AnomalieMapper::toDto).toList();
        }
        List<Integer> visibles = marcheService.idsMarchesVisibles();
        return visibles.isEmpty() ? List.of()
                : repository.findByIdDetailIn(visibles).stream().map(AnomalieMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AnomalieDto findById(Integer id) {
        Anomalie entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Anomalie introuvable : " + id));
        controlerAcces(entity);
        return AnomalieMapper.toDto(entity);
    }

    public AnomalieDto create(AnomalieDto dto) {
        Anomalie entity = AnomalieMapper.toEntity(dto);
        return AnomalieMapper.toDto(repository.save(entity));
    }

    public AnomalieDto update(Integer id, AnomalieDto dto) {
        Anomalie existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Anomalie introuvable : " + id));
        existing.setIdDetail(dto.getIdDetail());
        existing.setIdPpm(dto.getIdPpm());
        existing.setIdRegleAnomalie(dto.getIdRegleAnomalie());
        existing.setTypeAnomalie(dto.getTypeAnomalie());
        existing.setGravite(dto.getGravite());
        existing.setDescription(dto.getDescription());
        existing.setDateDetection(dto.getDateDetection());
        existing.setSource(dto.getSource());
        existing.setStatut(dto.getStatut());
        existing.setImTraitement(dto.getImTraitement());
        existing.setDateTraitement(dto.getDateTraitement());
        existing.setCommentaireTraitement(dto.getCommentaireTraitement());
        return AnomalieMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Anomalie introuvable : " + id);
        }
        repository.deleteById(id);
    }

    /** Périmètre de la ligne de marché visée ; anomalie de niveau PPM → Président/Administrateur seuls. */
    private void controlerAcces(Anomalie anomalie) {
        if (anomalie.getIdDetail() == null) {
            if (!Visibilite.voitTout()) {
                throw new AccessDeniedException(
                        "Anomalie de niveau PPM : consultation réservée au Président et à l'Administrateur (§1).");
            }
            return;
        }
        marcheService.controlerAccesMarche(anomalie.getIdDetail());
    }
}
