package cnm.prs.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.MarchePrevisionDto;
import cnm.prs.entity.Capm;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.MarchePrevisionMapper;
import cnm.prs.repository.CapmRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link MarchePrevision} (dates prévisionnelles des marchés).
 *
 * <p>⚠️ Correction de périmètre — une date prévisionnelle n'a <strong>pas de périmètre propre</strong> :
 * elle hérite de celui de sa <strong>ligne de marché</strong> ({@code t_marche_prevision.ID_DETAIL}).
 * Lectures scopées et écritures contrôlées via {@link MarcheService}. Auparavant ce service faisait
 * {@code repository.findAll()} nu — le calendrier de toutes les entités était lisible par n'importe quel
 * porteur de jeton.</p>
 */
@Service
@Transactional
public class MarchePrevisionService {

    private final MarchePrevisionRepository repository;
    private final CapmRepository capmRepository;
    private final MarcheService marcheService;

    public MarchePrevisionService(MarchePrevisionRepository repository, CapmRepository capmRepository,
            MarcheService marcheService) {
        this.repository = repository;
        this.capmRepository = capmRepository;
        this.marcheService = marcheService;
    }

    @Transactional(readOnly = true)
    public List<MarchePrevisionDto> findAll() {
        if (Visibilite.voitTout()) {
            return repository.findAll().stream().map(MarchePrevisionMapper::toDto).map(this::peuplerOrdre).toList();
        }
        List<Integer> visibles = marcheService.idsMarchesVisibles();
        return visibles.isEmpty() ? List.of()
                : repository.findByIdDetailIn(visibles).stream()
                        .map(MarchePrevisionMapper::toDto).map(this::peuplerOrdre).toList();
    }

    @Transactional(readOnly = true)
    public List<MarchePrevisionDto> findByMarche(Integer idDetail) {
        marcheService.controlerAccesMarche(idDetail);
        // Triées par l'ordre du processus (t_capm.ORDRE) ASC.
        return repository.findByMarcheOrdonne(idDetail).stream()
                .map(MarchePrevisionMapper::toDto).map(this::peuplerOrdre).toList();
    }

    @Transactional(readOnly = true)
    public MarchePrevisionDto findById(Integer id) {
        MarchePrevision entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Date prévisionnelle introuvable : " + id));
        marcheService.controlerAccesMarche(entity.getIdDetail());
        return peuplerOrdre(MarchePrevisionMapper.toDto(entity));
    }

    public MarchePrevisionDto create(MarchePrevisionDto dto) {
        marcheService.controlerAccesMarche(dto.getIdDetail());
        validerChronologie(dto, null);
        MarchePrevision entity = MarchePrevisionMapper.toEntity(dto);
        // PK serveur (max+1) ; id client ignoré — cf. LotService#create. La façade de saisie
        // (SaisieService) passe déjà une séquence calculée serveur : elle est simplement recalculée ici.
        entity.setIdPrevision(repository.findMaxId() + 1);
        return peuplerOrdre(MarchePrevisionMapper.toDto(repository.save(entity)));
    }

    /** Renseigne l'{@code ordre} (lecture seule) depuis le référentiel {@code t_capm}. */
    private MarchePrevisionDto peuplerOrdre(MarchePrevisionDto dto) {
        if (dto != null && dto.getIdCapm() != null) {
            capmRepository.findById(dto.getIdCapm()).ifPresent(c -> dto.setOrdre(c.getOrdre()));
        }
        return dto;
    }

    public MarchePrevisionDto update(Integer id, MarchePrevisionDto dto) {
        MarchePrevision existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Date prévisionnelle introuvable : " + id));
        marcheService.controlerAccesMarche(existing.getIdDetail());   // la ligne éditée
        marcheService.controlerAccesMarche(dto.getIdDetail());        // et le marché de destination
        validerChronologie(dto, id);   // la ligne éditée remplace l'existante dans la séquence
        existing.setIdDetail(dto.getIdDetail());
        existing.setIdCapm(dto.getIdCapm());
        existing.setDateDebut(dto.getDateDebut());
        existing.setDateFin(dto.getDateFin());
        return peuplerOrdre(MarchePrevisionMapper.toDto(repository.save(existing)));
    }

    /**
     * ⚠️ Règle ajoutée — valide la cohérence chronologique des processus du marché après ajout/édition
     * de cette ligne : la prévision (dto) + les autres lignes du marché (hors {@code idAExclure}),
     * triées par ordre CAPM. Violation → 400 (champ {@code dateDebut}/{@code dateFin}).
     */
    private void validerChronologie(MarchePrevisionDto dto, Integer idAExclure) {
        List<ProcessusChronologie.Proc> procs = new ArrayList<>();
        procs.add(procDe(dto.getIdCapm(), dto.getDateDebut(), dto.getDateFin()));
        for (MarchePrevision sib : repository.findByIdDetail(dto.getIdDetail())) {
            if (idAExclure != null && idAExclure.equals(sib.getIdPrevision())) {
                continue;
            }
            procs.add(procDe(sib.getIdCapm(), sib.getDateDebut(), sib.getDateFin()));
        }
        ErrorResponse.FieldError violation = ProcessusChronologie.premiereViolation(procs);
        if (violation != null) {
            throw new ChampsInvalidesException(List.of(violation));
        }
    }

    /** Construit un {@code Proc} (chemin = nom de champ seul) en résolvant ordre/libellé via {@code t_capm}. */
    private ProcessusChronologie.Proc procDe(Integer idCapm, LocalDate dateDebut, LocalDate dateFin) {
        Capm c = idCapm == null ? null : capmRepository.findById(idCapm).orElse(null);
        int ordre = (c == null || c.getOrdre() == null) ? 0 : c.getOrdre();
        String libelle = c == null ? String.valueOf(idCapm) : c.getLibelleProcessus();
        return new ProcessusChronologie.Proc("", ordre, libelle, dateDebut, dateFin);
    }

    public void delete(Integer id) {
        MarchePrevision existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Date prévisionnelle introuvable : " + id));
        marcheService.controlerAccesMarche(existing.getIdDetail());
        repository.deleteById(id);
    }
}
