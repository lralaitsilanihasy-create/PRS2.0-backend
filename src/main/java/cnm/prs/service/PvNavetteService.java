package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.PvNavetteDto;
import cnm.prs.entity.PvNavette;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.PvNavetteMapper;
import cnm.prs.repository.PvNavetteRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link PvNavette}.
 *
 * <p><strong>Journal de navette immuable (§3.5).</strong> La navette est la trace du va-et-vient d'un
 * projet de PV entre le Membre et le Président/CC : elle a <strong>une seule voie d'écriture</strong>,
 * {@code PvExamenService#ajouterNavette}, appelée par le serveur lui-même à la soumission, au retour en
 * rectification et à l'acceptation. Les trois verbes d'écriture du CRUD générique sont fermés sans
 * exception.</p>
 *
 * <p>⚠️ Le {@code delete()} refusait déjà « aucune navette ne peut être supprimée » — mais
 * {@code update()} réécrivait {@code IM_ACTEUR}, {@code DATE_ACTION}, {@code SENS} et
 * {@code COMMENTAIRE}, et {@code create()} acceptait une navette forgée. Autrement dit : l'historique
 * ne pouvait pas être effacé, mais il pouvait être <strong>réécrit</strong> — ce qui est pire, car la
 * substitution ne laissait elle-même aucune trace. Un acteur pouvait attribuer sa propre demande de
 * rectification à un collègue, ou antidater une acceptation. Même traitement que le journal d'audit :
 * les verbes restent <strong>routés</strong> pour porter un refus explicite en <strong>409</strong>,
 * plutôt qu'un 405 qui ne dirait que « mauvais verbe ».</p>
 *
 * <p>⚠️ Correction de périmètre — la navette n'a pas de périmètre propre : elle hérite de celui de son
 * PV (localité du contrôleur réceptionnaire). Auparavant {@code findAll()} servait la table entière :
 * la PRMP — partie contrôlée — lisait les échanges internes de la commission sur les dossiers de
 * toutes les localités, commentaires de rectification compris.</p>
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
        return Visibilite.filtrer(repository::findAll, repository::findVisiblesParLocalite)
                .stream().map(PvNavetteMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PvNavetteDto findById(Integer id) {
        PvNavette entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PvNavette introuvable : " + id));
        Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        return PvNavetteMapper.toDto(entity);
    }

    /**
     * Création par l'API interdite : une navette n'est jamais déclarée par un client, elle est constatée
     * par le serveur au fil du circuit du PV (§3.5). Laisser un client poser lui-même {@code IM_ACTEUR}
     * et {@code DATE_ACTION} reviendrait à laisser fabriquer un mouvement au nom d'un tiers. Voie
     * d'écriture unique : {@code PvExamenService#ajouterNavette}. → HTTP 409.
     */
    public PvNavetteDto create(PvNavetteDto dto) {
        throw new BusinessRuleException(
                "L'historique des navettes est immuable : création interdite (§3.5 — traçabilité).");
    }

    /**
     * Modification interdite : une navette écrite ne se réécrit pas (§3.5). Sans cette garde, un acteur
     * pouvait réattribuer sa propre demande de rectification à un tiers en remplaçant {@code IM_ACTEUR},
     * ou antidater une acceptation via {@code DATE_ACTION}, sans que la substitution laisse elle-même de
     * trace. → HTTP 409.
     */
    public PvNavetteDto update(Integer id, PvNavetteDto dto) {
        throw new BusinessRuleException(
                "L'historique des navettes est immuable : modification interdite (§3.5 — traçabilité).");
    }

    /**
     * Suppression interdite : la traçabilité de la navette est immuable
     * (§3.5 — « aucune navette ne peut être supprimée »). → HTTP 409.
     */
    public void delete(Integer id) {
        throw new BusinessRuleException("Une navette de PV ne peut pas être supprimée (§3.5 — traçabilité).");
    }
}
