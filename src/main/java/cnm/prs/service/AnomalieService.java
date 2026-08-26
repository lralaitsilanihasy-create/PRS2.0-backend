package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.AnomalieDto;
import cnm.prs.entity.Anomalie;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.AnomalieMapper;
import cnm.prs.repository.AnomalieRepository;

/**
 * Logique métier pour {@link Anomalie}.
 */
@Service
@Transactional
public class AnomalieService {

    private final AnomalieRepository repository;

    public AnomalieService(AnomalieRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AnomalieDto> findAll() {
        return repository.findAll().stream().map(AnomalieMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AnomalieDto findById(Integer id) {
        Anomalie entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Anomalie introuvable : " + id));
        return AnomalieMapper.toDto(entity);
    }

    public AnomalieDto create(AnomalieDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdAnomalie(), repository::existsById, "anomalie");
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
}
