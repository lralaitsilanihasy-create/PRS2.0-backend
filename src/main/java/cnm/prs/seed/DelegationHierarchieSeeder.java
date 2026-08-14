package cnm.prs.seed;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.DelegationProfil;
import cnm.prs.entity.Profile;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.repository.DelegationProfilRepository;
import cnm.prs.repository.ProfileRepository;

/**
 * ⚠️ Règle ajoutée (2026-08-14) — seed des <strong>9 paires officielles</strong> de la délégation
 * ascendante (hiérarchie : Président &gt; Secrétaire &gt; Chef de commission &gt; Membre &gt;
 * Contrôleur vérificateur &gt; Assistant contrôleur ; PRMP, Administrateur et Chargé de publication
 * hors hiérarchie) :
 * <ul>
 *   <li>Président → Secrétaire, Chef de commission, Membre, Vérificateur, Assistant (5) ;</li>
 *   <li>Chef de commission → Secrétaire, Membre, Vérificateur, Assistant (4).</li>
 * </ul>
 *
 * <p><strong>Table explicite, PAS de rang</strong> : le CC est sous le Secrétaire dans la hiérarchie
 * mais hérite de ses droits parce que la paire CC → Secrétaire est LISTÉE — un modèle
 * « rang ≥ rang requis » casserait ce cas. Non transitive.</p>
 *
 * <p><strong>Idempotent et non intrusif</strong> : crée une paire absente ({@code actif = true}) ;
 * ne touche JAMAIS une paire existante — une désactivation posée par l'Administrateur
 * ({@code actif = false}) survit donc aux redémarrages. Profils résolus par libellé
 * ({@code tr_profile.PROFILE}) ; paire ignorée si un profil manque au référentiel.
 * Désactivable avec {@code app.seed.delegations.enabled=false}.</p>
 */
@Component
@ConditionalOnProperty(name = "app.seed.delegations.enabled", havingValue = "true", matchIfMissing = true)
public class DelegationHierarchieSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DelegationHierarchieSeeder.class);

    /** Les 9 paires autorisées : délégant (exerce) → délégués (tâches exercées). */
    private static final Map<ProfilUtilisateur, List<ProfilUtilisateur>> PAIRES = Map.of(
            ProfilUtilisateur.PRESIDENT, List.of(
                    ProfilUtilisateur.SECRETAIRE, ProfilUtilisateur.CHEF_COMMISSION, ProfilUtilisateur.MEMBRE,
                    ProfilUtilisateur.VERIFICATEUR, ProfilUtilisateur.ASSISTANT_CONTROLEUR),
            ProfilUtilisateur.CHEF_COMMISSION, List.of(
                    ProfilUtilisateur.SECRETAIRE, ProfilUtilisateur.MEMBRE,
                    ProfilUtilisateur.VERIFICATEUR, ProfilUtilisateur.ASSISTANT_CONTROLEUR));

    private final ProfileRepository profileRepository;
    private final DelegationProfilRepository delegationRepository;

    public DelegationHierarchieSeeder(ProfileRepository profileRepository,
            DelegationProfilRepository delegationRepository) {
        this.profileRepository = profileRepository;
        this.delegationRepository = delegationRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Profile> profils = profileRepository.findAll();
        int prochainId = delegationRepository.findAll().stream()
                .map(DelegationProfil::getIdDelegation).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        int crees = 0;
        for (Map.Entry<ProfilUtilisateur, List<ProfilUtilisateur>> e : PAIRES.entrySet()) {
            for (ProfilUtilisateur delegue : e.getValue()) {
                for (Integer idDelegant : idsProfil(profils, e.getKey())) {
                    for (Integer idDelegue : idsProfil(profils, delegue)) {
                        if (delegationRepository.existsByIdProfileDelegantAndIdProfileDelegue(idDelegant, idDelegue)) {
                            continue;   // paire existante (active ou désactivée par l'admin) : intouchable
                        }
                        delegationRepository.save(new DelegationProfil(prochainId++, idDelegant, idDelegue,
                                Boolean.TRUE, null, null));
                        crees++;
                    }
                }
            }
        }
        if (crees > 0) {
            log.info("[SEED] Délégations ascendantes : {} paire(s) créée(s) (actif=true) — les paires "
                    + "existantes (dont désactivées) ne sont jamais modifiées.", crees);
        }
    }

    private List<Integer> idsProfil(List<Profile> profils, ProfilUtilisateur cible) {
        return profils.stream()
                .filter(p -> ProfilUtilisateur.resolve(p.getProfile()) == cible)
                .map(Profile::getIdProfile)
                .toList();
    }
}
