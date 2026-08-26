package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.TrancheDto;
import cnm.prs.entity.Tranche;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.TrancheMapper;
import cnm.prs.repository.TrancheRepository;
import cnm.prs.security.PerimetreDossier;

/**
 * Logique métier pour {@link Tranche}.
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §1/§3.1 : CRUD auparavant sans aucune garde. La tranche est un
 * petit-enfant du dossier ({@code t_tranche.ID_LOT → t_lot.ID_DOSSIER}) : lectures bornées au
 * périmètre du dossier parent, écritures réservées au brouillon de la PRMP propriétaire.</p>
 */
@Service
@Transactional
public class TrancheService {

    private final TrancheRepository repository;
    private final PerimetreDossier perimetre;
    private final EnfantDossierGarde garde;

    public TrancheService(TrancheRepository repository, PerimetreDossier perimetre, EnfantDossierGarde garde) {
        this.repository = repository;
        this.perimetre = perimetre;
        this.garde = garde;
    }

    @Transactional(readOnly = true)
    public List<TrancheDto> findAll() {
        return perimetre.filtrer(repository::findAll, repository::findParDossiers)
                .stream().map(TrancheMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public TrancheDto findById(Integer id) {
        Tranche entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tranche introuvable : " + id));
        perimetre.controler(repository.findIdDossier(id).orElse(null));
        return TrancheMapper.toDto(entity);
    }

    public TrancheDto create(TrancheDto dto) {
        exigerEcritureSurLot(dto.getIdLot());
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdTranche(), repository::existsById, "tranche");
        Tranche entity = TrancheMapper.toEntity(dto);
        return TrancheMapper.toDto(repository.save(entity));
    }

    public TrancheDto update(Integer id, TrancheDto dto) {
        Tranche existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tranche introuvable : " + id));
        exigerEcritureSurLot(existing.getIdLot());   // lot ACTUEL
        exigerEcritureSurLot(dto.getIdLot());        // lot CIBLE demandé
        existing.setLieuTrc(dto.getLieuTrc());
        existing.setMontTrc(dto.getMontTrc());
        existing.setIdLot(dto.getIdLot());
        return TrancheMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        Tranche existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tranche introuvable : " + id));
        exigerEcritureSurLot(existing.getIdLot());
        repository.deleteById(id);
    }

    /** Garde d'écriture via le dossier du lot porteur (403 hors périmètre / 409 hors brouillon). */
    private void exigerEcritureSurLot(Integer idLot) {
        if (garde.estAdministrateur()) {
            return;
        }
        Integer idDossier = idLot == null ? null
                : repository.findIdDossierParLot(idLot)
                        .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable : " + idLot));
        garde.exigerEcritureSurDossier(idDossier);
    }
}
