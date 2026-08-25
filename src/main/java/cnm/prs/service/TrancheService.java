package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.TrancheDto;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Tranche;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.TrancheMapper;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.TrancheRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link Tranche}.
 *
 * <p>⚠️ Correction de périmètre — une tranche n'a <strong>pas de périmètre propre</strong> : elle hérite,
 * via son {@link Lot}, de celui de la <strong>ligne de marché</strong>. Toutes les lectures et toutes les
 * écritures remontent donc au marché parent (chaîne {@code t_tranche.ID_LOT → t_lot.ID_DETAIL}).
 * Auparavant ce service faisait {@code repository.findAll()} nu.</p>
 */
@Service
@Transactional
public class TrancheService {

    private final TrancheRepository repository;
    private final LotRepository lotRepository;
    private final MarcheService marcheService;

    public TrancheService(TrancheRepository repository, LotRepository lotRepository, MarcheService marcheService) {
        this.repository = repository;
        this.lotRepository = lotRepository;
        this.marcheService = marcheService;
    }

    @Transactional(readOnly = true)
    public List<TrancheDto> findAll() {
        if (Visibilite.voitTout()) {
            return repository.findAll().stream().map(TrancheMapper::toDto).toList();
        }
        List<Integer> marchesVisibles = marcheService.idsMarchesVisibles();
        if (marchesVisibles.isEmpty()) {
            return List.of();
        }
        List<Integer> lotsVisibles = lotRepository.findByIdDetailIn(marchesVisibles).stream()
                .map(Lot::getIdLot).toList();
        return lotsVisibles.isEmpty() ? List.of()
                : repository.findByIdLotIn(lotsVisibles).stream().map(TrancheMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public TrancheDto findById(Integer id) {
        Tranche entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tranche introuvable : " + id));
        controlerAccesLot(entity.getIdLot());
        return TrancheMapper.toDto(entity);
    }

    public TrancheDto create(TrancheDto dto) {
        controlerAccesLot(dto.getIdLot());
        Tranche entity = TrancheMapper.toEntity(dto);
        // PK serveur (seq_tranche) ; id client ignoré — cf. LotService#create : un id choisi par le client
        // permettrait d'écraser (merge) la tranche d'une autre entité. La séquence remplace le max+1,
        // que deux saisies simultanées lisaient à l'identique.
        entity.setIdTranche(repository.nextIdTranche().intValue());
        return TrancheMapper.toDto(repository.save(entity));
    }

    public TrancheDto update(Integer id, TrancheDto dto) {
        Tranche existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tranche introuvable : " + id));
        controlerAccesLot(existing.getIdLot());   // la tranche éditée
        controlerAccesLot(dto.getIdLot());        // et le lot de destination
        existing.setLieuTrc(dto.getLieuTrc());
        existing.setMontTrc(dto.getMontTrc());
        existing.setIdLot(dto.getIdLot());
        return TrancheMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        Tranche existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tranche introuvable : " + id));
        controlerAccesLot(existing.getIdLot());
        repository.deleteById(id);
    }

    /**
     * Remonte du lot au marché et délègue le contrôle de périmètre. Lot inconnu ⇒ aucune levée : même
     * tolérance que {@link MarcheService#controlerAccesMarche(Integer)} envers un parent inexistant.
     */
    private void controlerAccesLot(Integer idLot) {
        if (idLot == null) {
            return;
        }
        lotRepository.findById(idLot).ifPresent(l -> marcheService.controlerAccesMarche(l.getIdDetail()));
    }
}
