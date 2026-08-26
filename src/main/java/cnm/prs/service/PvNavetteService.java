package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.PvNavetteDto;
import cnm.prs.entity.PvNavette;
import cnm.prs.enums.SensNavette;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.PvNavetteMapper;
import cnm.prs.repository.PvNavetteRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link PvNavette}.
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §3.5 « aucune navette ne peut être supprimée » : l'immuabilité était
 * posée sur {@link #delete} mais <strong>contournable par le {@code PUT} générique</strong>, qui
 * réécrivait sens, acteur, date et commentaire d'une navette déjà tracée. {@link #update} refuse
 * désormais pour <strong>tous</strong> les profils, Administrateur compris — cohérent avec la
 * suppression, et c'est le sens même d'une trace.</p>
 *
 * <p>⚠️ LOT 3a — §1 : la navette est une pièce <strong>interne</strong> du circuit. Sa lecture est
 * bornée à la localité du contrôleur (Président/Administrateur : tout) ; la PRMP n'y a pas accès —
 * elle reçoit la synthèse par le PV, pas le détail de la navette (§3.1).</p>
 */
@Service
@Transactional
public class PvNavetteService {

    private final PvNavetteRepository repository;

    public PvNavetteService(PvNavetteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PvNavetteDto> findAll() {
        return Visibilite.filtrer(repository::findAll, repository::findParLocalite)
                .stream().map(PvNavetteMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PvNavetteDto findById(Integer id) {
        PvNavette entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PvNavette introuvable : " + id));
        Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        return PvNavetteMapper.toDto(entity);
    }

    public PvNavetteDto create(PvNavetteDto dto) {
        validateSens(dto.getSens());
        PvNavette entity = PvNavetteMapper.toEntity(dto);
        return PvNavetteMapper.toDto(repository.save(entity));
    }

    /**
     * Modification interdite : la navette est une trace, au même titre qu'elle est insupprimable
     * (§3.5). Les vraies navettes naissent du flux PV (soumission / retour rectification /
     * acceptation) qui les insère lui-même ; rien ne justifie de les réécrire ensuite.
     */
    public PvNavetteDto update(Integer id, PvNavetteDto dto) {
        throw new BusinessRuleException(
                "Une navette de PV ne peut pas être modifiée : la navette est immuable (§3.5 — traçabilité).");
    }

    /**
     * Suppression interdite : la traçabilité de la navette est immuable
     * (§3.5 — « aucune navette ne peut être supprimée »).
     */
    public void delete(Integer id) {
        throw new BusinessRuleException("Une navette de PV ne peut pas être supprimée (§3.5 — traçabilité).");
    }

    private void validateSens(String sens) {
        if (sens == null || sens.isBlank()) {
            throw new BusinessRuleException("Le sens de la navette est obligatoire (SOUMISSION / RETOUR_RECTIF / ACCEPTATION).");
        }
        try {
            SensNavette.valueOf(sens.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Sens de navette invalide : « " + sens + " ».");
        }
    }
}
