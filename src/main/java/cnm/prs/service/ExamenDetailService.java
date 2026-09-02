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
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link ExamenDetail}.
 *
 * <p>Verrou d'édition (§2.6) : un point de contrôle n'est modifiable que tant que le dossier de
 * l'examen est {@link StatutDossier#EXAMINE} (navette ouverte) ; dès la signature du PV
 * ({@link StatutDossier#PV_SIGNE}) l'examen devient définitif et toute écriture est refusée (409).</p>
 *
 * <p>⚠️ Audit 2026-08-27, constat C2 — §1/§3.1 : la <strong>lecture</strong> était totalement
 * ouverte ({@code findAll()} sans filtre) alors que le parent {@code Examen} était correctement
 * scopé. L'évaluation point par point est un travail <strong>interne</strong> de la commission :
 * elle est désormais bornée par {@link Visibilite}, comme {@code ExamenService.findAll} —
 * Président/Administrateur : tout ; contrôleurs : leur localité ; <strong>PRMP/UGPM : rien</strong>
 * (acteur externe ; elle reçoit la synthèse du PV, pas le détail des points de contrôle).</p>
 */
@Service
@Transactional
public class ExamenDetailService {

    private final ExamenDetailRepository repository;
    private final ExamenRepository examenRepository;
    private final ObservationControleRepository observationRepository;
    private final PointsCtrlRepository pointsCtrlRepository;
    private final MarcheRepository marcheRepository;
    /** ⚠️ Audit 2026-08-27 (lot B) — verrou d'état + garde attributaire, partagés avec l'examen parent. */
    private final ExamenGarde garde;

    public ExamenDetailService(ExamenDetailRepository repository, ExamenRepository examenRepository,
            ObservationControleRepository observationRepository, PointsCtrlRepository pointsCtrlRepository,
            MarcheRepository marcheRepository, ExamenGarde garde) {
        this.garde = garde;
        this.repository = repository;
        this.examenRepository = examenRepository;
        this.observationRepository = observationRepository;
        this.pointsCtrlRepository = pointsCtrlRepository;
        this.marcheRepository = marcheRepository;
    }

    /** ⚠️ C2 — liste bornée au périmètre (§1) : localité du contrôleur, vide pour la PRMP/UGPM. */
    @Transactional(readOnly = true)
    public List<ExamenDetailDto> findAll() {
        return Visibilite.filtrer(repository::findAll, repository::findVisiblesParLocalite)
                .stream().map(this::toDtoAvecObservations).toList();
    }

    /** ⚠️ C2 — accès unitaire : 403 hors de la localité de l'examen (et pour la PRMP/UGPM). */
    @Transactional(readOnly = true)
    public ExamenDetailDto findById(Integer id) {
        ExamenDetail entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExamenDetail introuvable : " + id));
        Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        return toDtoAvecObservations(entity);
    }

    public ExamenDetailDto create(ExamenDetailDto dto) {
        // ⚠️ Audit 2026-08-27 (lot B) — l'écriture d'un point de contrôle n'avait AUCUNE garde
        // d'identité : tout Membre, de n'importe quelle localité, évaluait l'examen d'un autre.
        garde.exigerAttributaire(dto.getIdExamen());
        exigerExamenModifiable(dto.getIdExamen());
        validerObservations(dto);
        validerLigneEtUnicite(dto, null);
        ExamenDetail entity = ExamenDetailMapper.toEntity(dto);
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        entity.setIdDetailExamen(ClePrimaire.reallouer(dto.getIdDetailExamen(),
                repository::existsById, repository::nextIdDetailExamen));
        ExamenDetail saved = repository.save(entity);
        remplacerObservations(saved.getIdDetailExamen(), dto.getObservations());
        return toDtoAvecObservations(saved);
    }

    public ExamenDetailDto update(Integer id, ExamenDetailDto dto) {
        ExamenDetail existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExamenDetail introuvable : " + id));
        // ⚠️ Audit lot B — garde d'identité sur la ligne EN PLACE et sur l'examen VISÉ par le corps.
        garde.exigerAttributaire(existing.getIdExamen());
        garde.exigerAttributaire(dto.getIdExamen());
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
        garde.exigerAttributaire(existing.getIdExamen());   // ⚠️ audit lot B
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
            // ⚠️ 2026-09-02 — le test portait sur « == DOSSIER » et laissait donc passer un idDetail sur
            // un point FICHE ou AGPM : un résultat de fiche se serait accroché à une ligne de marché.
            // Le prédicat range toute portée non-LIGNE du côté sûr.
            if (point != null && !point.getPortee().parLigne()) {
                throw champInvalide("idDetail", "Le point « " + point.getLibelPointCtrl()
                        + " » est de portée " + point.getPortee().name() + " : il s'évalue une seule fois, "
                        + "sans ligne de marché (idDetail nul).");
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
     * DISPATCHE / EXAMINE / A_REEXAMINER ; refusée (409) dès {@link StatutDossier#PV_SIGNE}.
     *
     * <p>⚠️ Audit 2026-08-27 (lot B) — la règle, jusque-là recopiée à l'identique dans trois services,
     * est portée par {@link ExamenGarde} (source unique).</p>
     */
    private void exigerExamenModifiable(Integer idExamen) {
        garde.exigerExamenModifiable(idExamen);
    }
}
