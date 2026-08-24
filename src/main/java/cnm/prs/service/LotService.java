package cnm.prs.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.LotDto;
import cnm.prs.entity.Lot;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.LotMapper;
import cnm.prs.repository.LotRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link Lot}.
 *
 * <p>⚠️ Correction de périmètre — un lot n'a <strong>pas de périmètre propre</strong> : il hérite de
 * celui de sa <strong>ligne de marché</strong> ({@code t_lot.ID_DETAIL}). Toutes les lectures sont donc
 * scopées via {@link MarcheService#idsMarchesVisibles()} / {@link MarcheService#controlerAccesMarche(Integer)},
 * et toute écriture contrôle le marché visé. Auparavant ce service faisait {@code repository.findAll()}
 * nu : n'importe quel porteur de jeton lisait — et supprimait — les lots de toutes les entités.</p>
 */
@Service
@Transactional
public class LotService {

    private final LotRepository repository;
    private final MarcheService marcheService;

    public LotService(LotRepository repository, MarcheService marcheService) {
        this.repository = repository;
        this.marcheService = marcheService;
    }

    @Transactional(readOnly = true)
    public List<LotDto> findAll() {
        if (Visibilite.voitTout()) {
            return repository.findAll().stream().map(LotMapper::toDto).toList();
        }
        List<Integer> visibles = marcheService.idsMarchesVisibles();
        return visibles.isEmpty() ? List.of()
                : repository.findByIdDetailIn(visibles).stream().map(LotMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LotDto findById(Integer id) {
        Lot entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable : " + id));
        marcheService.controlerAccesMarche(entity.getIdDetail());
        return LotMapper.toDto(entity);
    }

    /** Lots d'une ligne de marché (liste, éventuellement vide si aucun ou marché inconnu). */
    @Transactional(readOnly = true)
    public List<LotDto> findByMarche(Integer idDetail) {
        marcheService.controlerAccesMarche(idDetail);
        return repository.findByIdDetail(idDetail).stream().map(LotMapper::toDto).toList();
    }

    /**
     * Lots d'un dossier — tous les lots de ses lignes de marché (liste, vide si aucun ou dossier inconnu).
     *
     * <p>Le dossier n'est pas l'unité de périmètre ici : chaque lot est filtré sur la visibilité de
     * <em>sa</em> ligne de marché. Un dossier hors périmètre ne rend donc aucun lot (liste vide, pas 403 —
     * la sémantique « filtre » de cet endpoint est conservée).</p>
     */
    @Transactional(readOnly = true)
    public List<LotDto> findByDossier(Integer idDossier) {
        List<Lot> lots = repository.findByIdDossier(idDossier);
        if (Visibilite.voitTout() || lots.isEmpty()) {
            return lots.stream().map(LotMapper::toDto).toList();
        }
        Set<Integer> visibles = Set.copyOf(marcheService.idsMarchesVisibles());
        return lots.stream().filter(l -> visibles.contains(l.getIdDetail())).map(LotMapper::toDto).toList();
    }

    public LotDto create(LotDto dto) {
        marcheService.controlerAccesMarche(dto.getIdDetail());
        Lot entity = LotMapper.toEntity(dto);
        // PK serveur (max+1) ; id client ignoré — même « Voie B » que t_marche. Indispensable depuis que
        // GET /api/lots est scopé : le front alloue son id par max() sur la liste REÇUE, désormais partielle.
        // Un id choisi par le client viserait alors le lot d'une autre entité, que save() écraserait (merge).
        entity.setIdLot(repository.findMaxIdLot() + 1);
        return LotMapper.toDto(repository.save(entity));
    }

    public LotDto update(Integer id, LotDto dto) {
        Lot existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable : " + id));
        marcheService.controlerAccesMarche(existing.getIdDetail());   // le lot édité
        marcheService.controlerAccesMarche(dto.getIdDetail());        // et le marché de destination
        existing.setIdDossier(dto.getIdDossier());
        existing.setIdDetail(dto.getIdDetail());
        existing.setDesignationLot(dto.getDesignationLot());
        existing.setMontLot(dto.getMontLot());
        existing.setQteLot(dto.getQteLot());
        existing.setUniteLot(dto.getUniteLot());
        return LotMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        Lot existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable : " + id));
        marcheService.controlerAccesMarche(existing.getIdDetail());
        repository.deleteById(id);
    }
}
