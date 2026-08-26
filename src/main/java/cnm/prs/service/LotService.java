package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.LotDto;
import cnm.prs.entity.Lot;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.LotMapper;
import cnm.prs.repository.LotRepository;
import cnm.prs.security.PerimetreDossier;

/**
 * Logique métier pour {@link Lot}.
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §1/§3.1 : le CRUD était <strong>totalement ouvert</strong> (tout
 * authentifié lisait et écrivait les lots de n'importe quel dossier). Les lectures sont désormais
 * bornées au périmètre du <strong>dossier parent</strong> ({@link PerimetreDossier}) et les écritures
 * au brouillon de la PRMP propriétaire ({@link EnfantDossierGarde}).</p>
 */
@Service
@Transactional
public class LotService {

    private final LotRepository repository;
    private final PerimetreDossier perimetre;
    private final EnfantDossierGarde garde;

    public LotService(LotRepository repository, PerimetreDossier perimetre, EnfantDossierGarde garde) {
        this.repository = repository;
        this.perimetre = perimetre;
        this.garde = garde;
    }

    /** Lots du périmètre de l'appelant (Président/Admin : tous ; CC : sa localité ; PRMP : ses dossiers). */
    @Transactional(readOnly = true)
    public List<LotDto> findAll() {
        return perimetre.filtrer(repository::findAll, repository::findByIdDossierIn)
                .stream().map(LotMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LotDto findById(Integer id) {
        Lot entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable : " + id));
        perimetre.controler(entity.getIdDossier());
        return LotMapper.toDto(entity);
    }

    /**
     * Lots d'une ligne de marché (liste, éventuellement vide si aucun ou marché inconnu).
     *
     * <p>⚠️ LOT 3a — marché hors périmètre : <strong>403</strong>. Le contrat « aucun/inconnu → liste
     * vide (pas de 404) » est conservé : sans lot, il n'y a rien à protéger ni à divulguer.</p>
     */
    @Transactional(readOnly = true)
    public List<LotDto> findByMarche(Integer idDetail) {
        return exposer(repository.findByIdDetail(idDetail));
    }

    /** Lots d'un dossier — tous les lots de ses lignes de marché (liste, vide si aucun ou dossier inconnu). */
    @Transactional(readOnly = true)
    public List<LotDto> findByDossier(Integer idDossier) {
        return exposer(repository.findByIdDossier(idDossier));
    }

    /** Contrôle le périmètre du dossier parent avant d'exposer une liste non vide (§1). */
    private List<LotDto> exposer(List<Lot> lots) {
        if (lots.isEmpty()) {
            return List.of();
        }
        perimetre.controler(lots.get(0).getIdDossier());
        return lots.stream().map(LotMapper::toDto).toList();
    }

    public LotDto create(LotDto dto) {
        exigerEcriture(dto.getIdDetail(), dto.getIdDossier());
        Lot entity = LotMapper.toEntity(dto);
        entity.setIdLot(prochaineCle(dto.getIdLot()));
        return LotMapper.toDto(repository.save(entity));
    }

    public LotDto update(Integer id, LotDto dto) {
        Lot existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable : " + id));
        garde.exigerEcritureSurDossier(existing.getIdDossier());   // dossier ACTUEL du lot
        exigerEcriture(dto.getIdDetail(), dto.getIdDossier());     // dossier CIBLE demandé
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
        garde.exigerEcritureSurDossier(existing.getIdDossier());
        repository.deleteById(id);
    }

    /**
     * Garde d'écriture sur les <strong>deux</strong> rattachements du lot : la ligne de marché (dossier
     * faisant autorité) et l'{@code ID_DOSSIER} porté par la ligne. Les contrôler séparément empêche de
     * déclarer un dossier à soi tout en rattachant le lot au marché d'autrui, ou l'inverse.
     */
    private void exigerEcriture(Integer idDetail, Integer idDossierDto) {
        Integer idDossierDuMarche = garde.dossierDuMarche(idDetail);
        garde.exigerEcritureSurDossier(idDossierDuMarche);
        if (idDossierDto != null && !idDossierDto.equals(idDossierDuMarche)) {
            garde.exigerEcritureSurDossier(idDossierDto);
        }
    }

    /**
     * ⚠️ LOT 3a (2026-08-26) — PK anti-collision. Le front alloue {@code ID_LOT} à partir du
     * {@code max} de <strong>la liste qu'il reçoit</strong> ; celle-ci étant désormais scopée, le max
     * vu par une PRMP n'est plus le max global. La PK proposée est donc conservée si elle est libre,
     * et réallouée par le serveur ({@code max + 1}, motif « Voie B » déjà en place dans
     * {@code SaisieService}) sinon — au lieu d'écraser silencieusement le lot d'autrui.
     */
    private Integer prochaineCle(Integer idPropose) {
        if (idPropose != null && !repository.existsById(idPropose)) {
            return idPropose;
        }
        return repository.findMaxIdLot() + 1;
    }
}
