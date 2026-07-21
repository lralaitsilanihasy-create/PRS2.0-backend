package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ExamenDetailDto;
import cnm.prs.dto.ObservationControleDto;
import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.ObservationControle;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.enums.PorteePointCtrl;
import cnm.prs.enums.StatutDossier;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.ExamenDetailMapper;
import cnm.prs.mapper.ObservationControleMapper;
import cnm.prs.repository.ExamenDetailRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.ObservationControleRepository;
import cnm.prs.repository.PointsCtrlRepository;

/**
 * Logique métier pour {@link ExamenDetail}.
 *
 * <p>Verrou d'édition (§2.6) : un point de contrôle n'est modifiable que tant que le dossier de
 * l'examen est {@link StatutDossier#EXAMINE} (navette ouverte) ; dès la signature du PV
 * ({@link StatutDossier#PV_SIGNE}) l'examen devient définitif et toute écriture est refusée (409).</p>
 */
@Service
@Transactional
public class ExamenDetailService {

    private final ExamenDetailRepository repository;
    private final ExamenRepository examenRepository;
    private final ObservationControleRepository observationRepository;
    private final PointsCtrlRepository pointsCtrlRepository;
    private final MarcheRepository marcheRepository;

    public ExamenDetailService(ExamenDetailRepository repository, ExamenRepository examenRepository,
            ObservationControleRepository observationRepository, PointsCtrlRepository pointsCtrlRepository,
            MarcheRepository marcheRepository) {
        this.repository = repository;
        this.examenRepository = examenRepository;
        this.observationRepository = observationRepository;
        this.pointsCtrlRepository = pointsCtrlRepository;
        this.marcheRepository = marcheRepository;
    }

    @Transactional(readOnly = true)
    public List<ExamenDetailDto> findAll() {
        return repository.findAll().stream().map(this::toDtoAvecObservations).toList();
    }

    @Transactional(readOnly = true)
    public ExamenDetailDto findById(Integer id) {
        ExamenDetail entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExamenDetail introuvable : " + id));
        return toDtoAvecObservations(entity);
    }

    public ExamenDetailDto create(ExamenDetailDto dto) {
        exigerExamenModifiable(dto.getIdExamen());
        validerObservations(dto);
        validerLigneEtUnicite(dto, null);
        ExamenDetail saved = repository.save(ExamenDetailMapper.toEntity(dto));
        remplacerObservations(saved.getIdDetailExamen(), dto.getObservations());
        return toDtoAvecObservations(saved);
    }

    public ExamenDetailDto update(Integer id, ExamenDetailDto dto) {
        ExamenDetail existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExamenDetail introuvable : " + id));
        exigerExamenModifiable(existing.getIdExamen());
        validerObservations(dto);
        validerLigneEtUnicite(dto, id);
        existing.setIdExamen(dto.getIdExamen());
        existing.setIdDetail(dto.getIdDetail());
        existing.setIdPtControle(dto.getIdPtControle());
        existing.setConforme(dto.getConforme());
        existing.setObsSiNonConforme(dto.getObsSiNonConforme());
        ExamenDetail saved = repository.save(existing);
        remplacerObservations(saved.getIdDetailExamen(), dto.getObservations());
        return toDtoAvecObservations(saved);
    }

    public void delete(Integer id) {
        ExamenDetail existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExamenDetail introuvable : " + id));
        exigerExamenModifiable(existing.getIdExamen());
        observationRepository.deleteByIdDetail(id);   // cascade des lignes d'observation
        repository.delete(existing);
    }

    /**
     * ⚠️ Règle ajoutée — un point de contrôle <strong>non conforme</strong> ({@code conforme=false})
     * doit comporter au moins une ligne d'observation, sinon 400 (champ {@code observations}).
     */
    private void validerObservations(ExamenDetailDto dto) {
        if (Boolean.FALSE.equals(dto.getConforme())
                && (dto.getObservations() == null || dto.getObservations().isEmpty())) {
            throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError(
                    "observations",
                    "Au moins une ligne d'observation est obligatoire si le point est non conforme.")));
        }
    }

    /**
     * ⚠️ Règle ajoutée (2026-07-21) — cohérence portée/ligne + unicité du triplet.
     * <ul>
     *   <li>Si {@code idDetail} est renseigné : le point doit être de portée {@code LIGNE} (un point
     *       {@code DOSSIER} s'évalue une seule fois, {@code idDetail} nul), et {@code idDetail} doit être une
     *       ligne de marché du dossier de l'examen — sinon 400 ciblé {@code idDetail}.</li>
     *   <li>Unicité applicative de ({@code idExamen}, {@code idDetail}, {@code idPtControle}) — 400
     *       {@code idPtControle} en cas de doublon (couvre {@code idDetail} nul, non géré par une contrainte
     *       SQL sous PostgreSQL).</li>
     * </ul>
     * <p><strong>Lénient</strong> : {@code idDetail} nul reste accepté (examen historique / point DOSSIER) —
     * l'exigence « une ligne par point LIGNE » est vérifiée à la <em>soumission</em> (complétude).</p>
     */
    private void validerLigneEtUnicite(ExamenDetailDto dto, Integer selfId) {
        Integer idDetail = dto.getIdDetail();
        if (idDetail != null) {
            PointsCtrl point = pointsCtrlRepository.findById(dto.getIdPtControle()).orElse(null);
            if (point != null && point.getPortee() == PorteePointCtrl.DOSSIER) {
                throw champInvalide("idDetail", "Le point « " + point.getLibelPointCtrl()
                        + " » est de portée DOSSIER : il s'évalue une seule fois, sans ligne de marché (idDetail nul).");
            }
            Integer idDossier = examenRepository.findIdDossierByExamen(dto.getIdExamen()).orElse(null);
            boolean estLigneDuDossier = idDossier != null && marcheRepository.findByIdDossier(idDossier).stream()
                    .anyMatch(m -> idDetail.equals(m.getIdDetail()));
            if (!estLigneDuDossier) {
                throw champInvalide("idDetail",
                        "La ligne de marché " + idDetail + " n'appartient pas au dossier de l'examen.");
            }
        }
        if (repository.compterDoublon(dto.getIdExamen(), dto.getIdPtControle(), idDetail, selfId) > 0) {
            throw champInvalide("idPtControle", "Un résultat existe déjà pour ce point de contrôle"
                    + (idDetail == null ? " au niveau dossier" : " sur cette ligne de marché")
                    + " (unicité idExamen + idDetail + idPtControle).");
        }
    }

    private ChampsInvalidesException champInvalide(String champ, String message) {
        return new ChampsInvalidesException(List.of(new ErrorResponse.FieldError(champ, message)));
    }

    /** Remplace les lignes d'observation du point de contrôle par celles fournies (replace-on-save). */
    private void remplacerObservations(Integer idDetail, List<ObservationControleDto> observations) {
        observationRepository.deleteByIdDetail(idDetail);
        if (observations == null) {
            return;
        }
        for (ObservationControleDto ligne : observations) {
            ObservationControle entity = ObservationControleMapper.toEntity(ligne);
            entity.setIdObservation(null);   // PK auto (IDENTITY)
            entity.setIdDetail(idDetail);
            observationRepository.save(entity);
        }
    }

    /** DTO du point de contrôle enrichi de ses lignes d'observation (triées par ordre). */
    private ExamenDetailDto toDtoAvecObservations(ExamenDetail entity) {
        ExamenDetailDto dto = ExamenDetailMapper.toDto(entity);
        dto.setObservations(observationRepository.findByIdDetailOrderByOrdreAsc(entity.getIdDetailExamen())
                .stream().map(ObservationControleMapper::toDto).toList());
        return dto;
    }

    /**
     * Verrou (§2.6) : écriture d'un détail d'examen possible uniquement tant que le dossier est
     * {@link StatutDossier#EXAMINE} ; refusée (409) dès {@link StatutDossier#PV_SIGNE}.
     */
    private void exigerExamenModifiable(Integer idExamen) {
        String statut = idExamen == null ? null
                : examenRepository.findStatutDossierByExamen(idExamen).orElse(null);
        if (!StatutDossier.EXAMINE.name().equals(statut)) {
            throw new BusinessRuleException(
                    "Examen verrouillé : modification possible uniquement tant que le dossier est EXAMINE "
                            + "(statut actuel « " + statut + " », examen définitif après signature du PV, §2.6).");
        }
    }
}
