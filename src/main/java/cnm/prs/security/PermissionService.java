package cnm.prs.security;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Profile;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.repository.DelegationProfilRepository;
import cnm.prs.repository.ProfileRepository;

/**
 * Autorisations tenant compte des délégations de profil (§3.2, §3.3, §3.8).
 *
 * <p>Exposé sous le nom {@code perm} pour les expressions {@code @PreAuthorize} :
 * {@code @perm.peutExercer('SECRETAIRE')}.</p>
 *
 * <p><strong>Convention t_delegation_profil (confirmée par le MLD db_ppm110626.pgerd) :</strong>
 * {@code ID_PROFILE_DELEGANT} est le profil qui <em>exerce</em> la tâche par substitution
 * (ex. Président) ; {@code ID_PROFILE_DELEGUE} est le profil <em>dont la tâche est exercée</em>
 * (ex. Secrétaire). Une ligne (délégant = Président, délégué = Secrétaire, actif = true)
 * autorise donc le Président à agir comme Secrétaire.</p>
 */
@Component("perm")
@Transactional(readOnly = true)
public class PermissionService {

    private final ProfileRepository profileRepository;
    private final DelegationProfilRepository delegationRepository;

    public PermissionService(ProfileRepository profileRepository,
            DelegationProfilRepository delegationRepository) {
        this.profileRepository = profileRepository;
        this.delegationRepository = delegationRepository;
    }

    /**
     * Vrai si l'utilisateur courant peut exercer les tâches du profil cible : soit parce qu'il
     * en est titulaire, soit via une délégation active.
     *
     * @param profilCible nom d'un {@link ProfilUtilisateur} (ex. {@code SECRETAIRE})
     */
    public boolean peutExercer(String profilCible) {
        ProfilUtilisateur cible;
        try {
            cible = ProfilUtilisateur.valueOf(profilCible);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return peutExercer(cible);
    }

    /**
     * ⚠️ Règle « délégation ascendante » (2026-08-14) — GARDE CENTRALE unique : titulaire
     * ({@code profil courant == cible}) OU paire (courant → cible) <strong>active</strong> dans
     * {@code t_delegation_profil}. Les paires autorisées sont une <strong>table explicite</strong>
     * (seed {@code DelegationHierarchieSeeder}), jamais une comparaison de rangs : le Chef de
     * commission est SOUS le Secrétaire dans la hiérarchie mais la paire CC → Secrétaire est
     * listée — un modèle « rang ≥ rang requis » casserait ce cas. Non transitive.
     * Surcharge utilisée par les services (les {@code @PreAuthorize} passent par la variante String).
     */
    public boolean peutExercer(ProfilUtilisateur cible) {
        return peutExercer(CurrentUser.profil().orElse(null), cible);
    }

    /**
     * Même garde pour un profil <strong>arbitraire</strong> (pas seulement l'utilisateur courant) —
     * utilisée pour valider l'attributaire d'un dispatch (auto-attribution du Président/CC).
     */
    public boolean peutExercer(ProfilUtilisateur courant, ProfilUtilisateur cible) {
        if (courant == null || cible == null) {
            return false;
        }
        if (courant == cible) {
            return true; // titulaire
        }
        List<Integer> idsCible = idProfiles(cible);
        List<Integer> idsCourant = idProfiles(courant);
        if (idsCible.isEmpty() || idsCourant.isEmpty()) {
            return false;
        }
        // Le profil courant exerce (DELEGANT) la tâche du profil cible (DELEGUE).
        return delegationRepository.existsByActifTrueAndIdProfileDelegantInAndIdProfileDelegueIn(idsCourant, idsCible);
    }

    private List<Integer> idProfiles(ProfilUtilisateur profil) {
        return profileRepository.findAll().stream()
                .filter(p -> ProfilUtilisateur.resolve(p.getProfile()) == profil)
                .map(Profile::getIdProfile)
                .toList();
    }
}
