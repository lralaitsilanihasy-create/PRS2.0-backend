package cnm.prs.service;

import java.util.List;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.IndicateurPrmpDto;
import cnm.prs.entity.IndicateurPrmp;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.IndicateurPrmpMapper;
import cnm.prs.repository.IndicateurPrmpRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link IndicateurPrmp}.
 *
 * <p>⚠️ Correction de périmètre — le bilan annuel d'une PRMP (taux de conformité, nb de retours, nb de
 * retraits, montant total soumis) la <strong>juge</strong> : le comparatif inter-PRMP est une vue de
 * pilotage du Président/Administrateur, pas une donnée publique du CNM. Le périmètre est donc celui de
 * la <strong>propriété</strong> : Président/Administrateur voient tout, la PRMP (et l'UGPM de sa
 * tutelle, dont le claim {@code ref} porte l'ID_PRMP) ne voit que <strong>les siens</strong>, tout autre
 * profil ne voit rien. Auparavant ce service faisait {@code repository.findAll()} nu : une PRMP lisait
 * le palmarès de toutes ses homologues.</p>
 */
@Service
@Transactional
public class IndicateurPrmpService {

    private final IndicateurPrmpRepository repository;

    public IndicateurPrmpService(IndicateurPrmpRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<IndicateurPrmpDto> findAll() {
        if (Visibilite.voitTout()) {
            return repository.findAll().stream().map(IndicateurPrmpMapper::toDto).toList();
        }
        return idPrmpAppelant()
                .map(ref -> repository.findByIdPrmp(ref).stream().map(IndicateurPrmpMapper::toDto).toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public IndicateurPrmpDto findById(Integer id) {
        IndicateurPrmp entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IndicateurPrmp introuvable : " + id));
        if (!Visibilite.voitTout()
                && !idPrmpAppelant().map(ref -> ref.equals(entity.getIdPrmp())).orElse(false)) {
            throw new AccessDeniedException(
                    "Indicateur d'une autre PRMP : hors de votre périmètre de visibilité (§1).");
        }
        return IndicateurPrmpMapper.toDto(entity);
    }

    public IndicateurPrmpDto create(IndicateurPrmpDto dto) {
        IndicateurPrmp entity = IndicateurPrmpMapper.toEntity(dto);
        return IndicateurPrmpMapper.toDto(repository.save(entity));
    }

    public IndicateurPrmpDto update(Integer id, IndicateurPrmpDto dto) {
        IndicateurPrmp existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IndicateurPrmp introuvable : " + id));
        existing.setIdPrmp(dto.getIdPrmp());
        existing.setExercice(dto.getExercice());
        existing.setNbPpmSoumis(dto.getNbPpmSoumis());
        existing.setNbDossiersSoumis(dto.getNbDossiersSoumis());
        existing.setNbDossiersConformes(dto.getNbDossiersConformes());
        existing.setNbDossiersNonConformes(dto.getNbDossiersNonConformes());
        existing.setNbRetours(dto.getNbRetours());
        existing.setNbRetraits(dto.getNbRetraits());
        existing.setTauxConformite(dto.getTauxConformite());
        existing.setDelaiMoyCorrectionJours(dto.getDelaiMoyCorrectionJours());
        existing.setMontTotalSoumis(dto.getMontTotalSoumis());
        existing.setDateMaj(dto.getDateMaj());
        return IndicateurPrmpMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("IndicateurPrmp introuvable : " + id);
        }
        repository.deleteById(id);
    }

    /** ID_PRMP de l'appelant (claim {@code ref}) s'il est PRMP ou UGPM de tutelle ; vide sinon. */
    private Optional<String> idPrmpAppelant() {
        return Visibilite.estPrmp() ? CurrentUser.ref().filter(s -> !s.isBlank()) : Optional.empty();
    }
}
