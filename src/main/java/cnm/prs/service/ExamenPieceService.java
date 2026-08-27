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
import cnm.prs.security.Visibilite;

/**
 * ⚠️ Règle ajoutée (2026-08-01) — logique métier pour {@link ExamenPiece} : examen des pièces jointes
 * d'un dossier, une par une (miroir des {@code examen-details} pour les lignes de marché).
 *
 * <p>⚠️ Audit 2026-08-27, constat C2 — §1/§3.1 : la lecture ({@code findAll}, avec ou sans
 * {@code ?examen=}, et l'accès unitaire) était ouverte à tout authentifié. Comme les détails
 * d'examen, elle est bornée par {@link Visibilite} : Président/Administrateur tout, contrôleurs
 * leur localité, <strong>PRMP/UGPM rien</strong> (le constat pièce par pièce est interne).</p>
 *
 * <p>⚠️ Audit 2026-08-27, lot B — l'<strong>écriture</strong>, elle, n'avait ni verrou d'état ni
 * garde d'identité : un résultat de pièce restait modifiable <em>après la signature du PV</em>
 * (alors que le détail des points de contrôle, lui, était verrouillé — asymétrie manifeste), et par
 * n'importe quel Membre de n'importe quelle localité. Les deux gardes de {@link ExamenGarde}
 * s'appliquent désormais à la création, à la modification et à la suppression.</p>
 */
@Service
@Transactional
public class ExamenPieceService {

    private final ExamenPieceRepository repository;
    /** ⚠️ Audit 2026-08-27 (lot B) — verrou d'état + garde attributaire, partagés avec l'examen parent. */
    private final ExamenGarde garde;

    public ExamenPieceService(ExamenPieceRepository repository, ExamenGarde garde) {
        this.repository = repository;
        this.garde = garde;
    }

    /**
     * Liste, optionnellement filtrée par examen ({@code ?examen=}) — ⚠️ C2 : bornée au périmètre (§1)
     * dans les deux cas, le filtre d'examen ne relâchant jamais la garde de localité.
     */
    @Transactional(readOnly = true)
    public List<ExamenPieceDto> findAll(Integer examen) {
        List<ExamenPiece> rows = examen == null
                ? Visibilite.filtrer(repository::findAll, repository::findVisiblesParLocalite)
                : Visibilite.filtrer(() -> repository.findByIdExamen(examen),
                        loc -> repository.findByIdExamenEtLocalite(examen, loc));
        return rows.stream().map(ExamenPieceMapper::toDto).toList();
    }

    /** ⚠️ C2 — accès unitaire : 403 hors de la localité de l'examen (et pour la PRMP/UGPM). */
    @Transactional(readOnly = true)
    public ExamenPieceDto findById(Integer id) {
        ExamenPiece entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Examen de pièce introuvable : " + id));
        Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        return ExamenPieceMapper.toDto(entity);
    }

    public ExamenPieceDto create(ExamenPieceDto dto) {
        garde.exigerAttributaire(dto.getIdExamen());        // ⚠️ audit lot B — localité + attributaire
        garde.exigerExamenModifiable(dto.getIdExamen());    // ⚠️ audit lot B — figé après signature du PV
        exigerUnicite(dto, null);
        ExamenPiece entity = ExamenPieceMapper.toEntity(dto);
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        entity.setIdExamenPiece(ClePrimaire.reallouer(dto.getIdExamenPiece(), repository::existsById, repository::nextIdExamenPiece));
        return ExamenPieceMapper.toDto(repository.save(entity));
    }

    public ExamenPieceDto update(Integer id, ExamenPieceDto dto) {
        ExamenPiece existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Examen de pièce introuvable : " + id));
        // ⚠️ Audit lot B — gardes sur la ligne EN PLACE et sur l'examen VISÉ par le corps.
        garde.exigerAttributaire(existing.getIdExamen());
        garde.exigerAttributaire(dto.getIdExamen());
        garde.exigerExamenModifiable(existing.getIdExamen());
        exigerUnicite(dto, id);
        existing.setIdExamen(dto.getIdExamen());
        existing.setIdPiece(dto.getIdPiece());
        existing.setConforme(dto.getConforme());
        existing.setObservation(dto.getObservation());
        return ExamenPieceMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        ExamenPiece existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Examen de pièce introuvable : " + id));
        garde.exigerAttributaire(existing.getIdExamen());        // ⚠️ audit lot B
        garde.exigerExamenModifiable(existing.getIdExamen());    // ⚠️ audit lot B
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
