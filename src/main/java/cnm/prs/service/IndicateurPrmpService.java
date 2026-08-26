package cnm.prs.service;

import java.util.List;

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
 * <p>⚠️ LOT 3a (2026-08-26) — §3.1 « Mes indicateurs [Lecture] » : la lecture était ouverte à tout
 * authentifié, exposant à chaque PRMP les taux de conformité, retours et retraits de <strong>toutes
 * les autres</strong>. Elle est désormais scopée sur {@code ID_PRMP} = {@code ref} du jeton pour la
 * PRMP (et l'UGPM de sa tutelle) ; seuls le Président et l'Administrateur voient l'ensemble (§3.2).
 * L'écriture générique est réservée à l'Administrateur ({@code @PreAuthorize} sur le contrôleur) :
 * ces lignes sont alimentées par le système depuis {@code v_performance_prmp}.</p>
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
        return indicateursVisibles().stream().map(IndicateurPrmpMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public IndicateurPrmpDto findById(Integer id) {
        IndicateurPrmp entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IndicateurPrmp introuvable : " + id));
        controlerVisibilite(entity);
        return IndicateurPrmpMapper.toDto(entity);
    }

    /** Ensemble lisible : tout (Président/Admin) ou les seules lignes de la PRMP courante (§3.1). */
    private List<IndicateurPrmp> indicateursVisibles() {
        if (Visibilite.voitTout()) {
            return repository.findAll();
        }
        if (!Visibilite.estPrmp()) {
            return List.of();
        }
        return CurrentUser.ref().filter(s -> !s.isBlank())
                .map(repository::findByIdPrmp).orElseGet(List::of);
    }

    /** 403 si la ligne n'appartient pas à la PRMP courante (§3.1). */
    private void controlerVisibilite(IndicateurPrmp entity) {
        if (Visibilite.voitTout()) {
            return;
        }
        boolean sienne = Visibilite.estPrmp()
                && CurrentUser.ref().filter(s -> !s.isBlank())
                        .map(ref -> ref.equals(entity.getIdPrmp())).orElse(false);
        if (!sienne) {
            throw new AccessDeniedException(
                    "Indicateurs hors de votre périmètre : vous ne consultez que les vôtres (§3.1).");
        }
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
}
