package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ExamenPieceDto;
import cnm.prs.entity.ExamenPiece;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.ExamenPieceMapper;
import cnm.prs.repository.ExamenPieceRepository;

/**
 * ⚠️ Règle ajoutée (2026-08-01) — logique métier pour {@link ExamenPiece} : examen des pièces jointes
 * d'un dossier, une par une (miroir des {@code examen-details} pour les lignes de marché).
 */
@Service
@Transactional
public class ExamenPieceService {

    private final ExamenPieceRepository repository;

    public ExamenPieceService(ExamenPieceRepository repository) {
        this.repository = repository;
    }

    /** Liste, optionnellement filtrée par examen ({@code ?examen=}). */
    @Transactional(readOnly = true)
    public List<ExamenPieceDto> findAll(Integer examen) {
        List<ExamenPiece> rows = examen == null ? repository.findAll() : repository.findByIdExamen(examen);
        return rows.stream().map(ExamenPieceMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ExamenPieceDto findById(Integer id) {
        ExamenPiece entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Examen de pièce introuvable : " + id));
        return ExamenPieceMapper.toDto(entity);
    }

    public ExamenPieceDto create(ExamenPieceDto dto) {
        exigerUnicite(dto, null);
        ExamenPiece entity = ExamenPieceMapper.toEntity(dto);
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        entity.setIdExamenPiece(ClePrimaire.reallouer(dto.getIdExamenPiece(), repository::existsById, repository::nextIdExamenPiece));
        return ExamenPieceMapper.toDto(repository.save(entity));
    }

    public ExamenPieceDto update(Integer id, ExamenPieceDto dto) {
        ExamenPiece existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Examen de pièce introuvable : " + id));
        exigerUnicite(dto, id);
        existing.setIdExamen(dto.getIdExamen());
        existing.setIdPiece(dto.getIdPiece());
        existing.setConforme(dto.getConforme());
        existing.setObservation(dto.getObservation());
        return ExamenPieceMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Examen de pièce introuvable : " + id);
        }
        repository.deleteById(id);
    }

    /** Un seul résultat par (examen, pièce) — 409 sinon. */
    private void exigerUnicite(ExamenPieceDto dto, Integer selfId) {
        if (repository.compterDoublon(dto.getIdExamen(), dto.getIdPiece(), selfId) > 0) {
            throw new BusinessRuleException(
                    "Cette pièce a déjà un résultat d'examen pour cet examen (corrigez-le via PUT).");
        }
    }
}
