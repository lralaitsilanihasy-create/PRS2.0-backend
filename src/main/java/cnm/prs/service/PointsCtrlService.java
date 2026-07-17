package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.PointsCtrlDto;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.SousTypeDossier;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.PointsCtrlMapper;
import cnm.prs.repository.PointsCtrlRepository;
import cnm.prs.repository.SousTypeDossierRepository;

/**
 * Logique métier pour {@link PointsCtrl} — grille de contrôle par <strong>famille</strong> de dossier,
 * affinée par <strong>sous-type</strong> (⚠️ règle ajoutée) : un point à {@code idSousType} null est
 * commun à toute la famille ; renseigné, il ne vaut que pour ce sous-type. La grille effective d'un
 * dossier (écran d'examen) = communs + spécifiques de son sous-type.
 */
@Service
@Transactional
public class PointsCtrlService {

    private final PointsCtrlRepository repository;
    private final SousTypeDossierRepository sousTypeDossierRepository;

    public PointsCtrlService(PointsCtrlRepository repository,
            SousTypeDossierRepository sousTypeDossierRepository) {
        this.repository = repository;
        this.sousTypeDossierRepository = sousTypeDossierRepository;
    }

    /**
     * Liste des points, filtrable côté serveur :
     * <ul>
     *   <li>sans paramètre → tout le référentiel (écran admin global) ;</li>
     *   <li>{@code ?typeDossier=DDP} → tous les points de la famille (admin), spécifiques compris ;</li>
     *   <li>{@code ?sousType=PPM-AGPM} → <strong>grille effective</strong> du sous-type (famille déduite) :
     *       points communs + points spécifiques — la requête de l'écran d'examen ;</li>
     *   <li>les deux → grille effective, avec contrôle de cohérence sous-type ∈ famille.</li>
     * </ul>
     *
     * @throws BadRequestException valeur inconnue du référentiel, ou sous-type hors de la famille (→ 400)
     */
    @Transactional(readOnly = true)
    public List<PointsCtrlDto> findAll(String typeDossier, String sousType) {
        String famille = typeDossier == null || typeDossier.isBlank() ? null : typeDossier.trim();
        if (sousType != null && !sousType.isBlank()) {
            SousTypeDossier st = sousTypeConnu(sousType.trim());
            if (famille != null && !famille.equals(st.getIdTypeDossier())) {
                throw new BadRequestException("Le sous-type « " + st.getIdSousType()
                        + " » n'appartient pas à la famille « " + famille + " » (famille : "
                        + st.getIdTypeDossier() + ").");
            }
            return repository.findGrilleEffective(st.getIdTypeDossier(), st.getIdSousType())
                    .stream().map(PointsCtrlMapper::toDto).toList();
        }
        if (famille != null) {
            return repository.findByIdTypeDossierOrderByOrdrePointCtrlAsc(famille)
                    .stream().map(PointsCtrlMapper::toDto).toList();
        }
        return repository.findAll().stream().map(PointsCtrlMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PointsCtrlDto findById(Integer id) {
        PointsCtrl entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PointsCtrl introuvable : " + id));
        return PointsCtrlMapper.toDto(entity);
    }

    public PointsCtrlDto create(PointsCtrlDto dto) {
        exigerCoherenceSousType(dto.getIdTypeDossier(), dto.getIdSousType());
        PointsCtrl entity = PointsCtrlMapper.toEntity(dto);
        return PointsCtrlMapper.toDto(repository.save(entity));
    }

    public PointsCtrlDto update(Integer id, PointsCtrlDto dto) {
        PointsCtrl existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PointsCtrl introuvable : " + id));
        exigerCoherenceSousType(dto.getIdTypeDossier(), dto.getIdSousType());
        existing.setLibelPointCtrl(dto.getLibelPointCtrl());
        existing.setDecriptPointCtrl(dto.getDecriptPointCtrl());
        existing.setOrdrePointCtrl(dto.getOrdrePointCtrl());
        existing.setObligatoire(dto.getObligatoire());
        existing.setIdTypeDossier(dto.getIdTypeDossier());
        existing.setIdSousType(dto.getIdSousType());
        return PointsCtrlMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("PointsCtrl introuvable : " + id);
        }
        repository.deleteById(id);
    }

    /** Si un sous-type est ciblé : il doit exister ET appartenir à la famille du point (sinon 400). */
    private void exigerCoherenceSousType(String famille, String idSousType) {
        if (idSousType == null || idSousType.isBlank()) {
            return;   // point commun à toute la famille
        }
        SousTypeDossier st = sousTypeConnu(idSousType.trim());
        if (!st.getIdTypeDossier().equals(famille)) {
            throw new BadRequestException("Le sous-type « " + st.getIdSousType()
                    + " » n'appartient pas à la famille « " + famille + " » (famille : "
                    + st.getIdTypeDossier() + ").");
        }
    }

    private SousTypeDossier sousTypeConnu(String idSousType) {
        return sousTypeDossierRepository.findById(idSousType)
                .orElseThrow(() -> new BadRequestException(
                        "Sous-type de dossier inconnu : « " + idSousType + " » (référentiel /api/sous-type-dossiers)."));
    }
}
