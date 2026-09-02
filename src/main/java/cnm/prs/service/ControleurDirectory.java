package cnm.prs.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Controleur;
import cnm.prs.entity.Profile;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.ProfileRepository;
import cnm.prs.security.PermissionService;

/**
 * Annuaire des contrôleurs par profil métier. Résout le(s) {@code ID_PROFILE} d'un
 * {@link ProfilUtilisateur} à partir du libellé {@code tr_profile.PROFILE} (même logique
 * que l'authentification), puis retourne les contrôleurs correspondants. Utilisé pour
 * adresser les notifications (Président, Chef de commission d'une localité…).
 */
@Component
@Transactional(readOnly = true)
public class ControleurDirectory {

    private final ProfileRepository profileRepository;
    private final ControleurRepository controleurRepository;
    private final PermissionService permissionService;

    public ControleurDirectory(ProfileRepository profileRepository, ControleurRepository controleurRepository,
            PermissionService permissionService) {
        this.profileRepository = profileRepository;
        this.controleurRepository = controleurRepository;
        this.permissionService = permissionService;
    }

    /**
     * Profil métier d'un contrôleur — résolution par la <strong>FK scalaire</strong> {@code ID_PROFILE}
     * (l'association lazy n'est pas fiable sur une entité déjà en cache de session).
     */
    public Optional<ProfilUtilisateur> profilDe(String imControleur) {
        if (imControleur == null || imControleur.isBlank()) {
            return Optional.empty();
        }
        return controleurRepository.findById(imControleur.trim())
                .map(Controleur::getIdProfile)
                .flatMap(profileRepository::findById)
                .map(p -> ProfilUtilisateur.resolve(p.getProfile()));
    }

    /**
     * ⚠️ Co-signature (2026-08-28, arbitrage du pilote) — Membre désignable pour co-signer un PV :
     * <strong>Membre titulaire</strong> de la <strong>localité du dossier</strong>.
     *
     * <p>Deux écarts délibérés avec l’ancienne garde du Secrétaire de séance (retirée le 2026-09-02
     * avec la notion elle-même), dont elle était proche :</p>
     * <ul>
     *   <li><strong>Titulaire, pas de délégation.</strong> On ne passe pas par
     *       {@code peutExercer(profil, MEMBRE)} : les paires (Président → Membre) et
     *       (CC → Membre) rendraient un second P/CC désignable, et l'on retomberait sur deux
     *       signatures de même rang — exactement ce que l'arbitrage ferme.</li>
     *   <li><strong>Aucune exemption de localité.</strong> L’ancienne garde tolérait un
     *       contrôleur sans localité (le Président, compétent partout) ; ici cette tolérance
     *       rouvrirait la porte que §3.3 referme. Localité nulle → refus.</li>
     * </ul>
     */
    public boolean peutEtreMembreCoSignataire(String imControleur, String localiteDossier) {
        if (imControleur == null || imControleur.isBlank() || localiteDossier == null) {
            return false;
        }
        Controleur controleur = controleurRepository.findById(imControleur.trim()).orElse(null);
        if (controleur == null) {
            return false;
        }
        return profilDe(imControleur).orElse(null) == ProfilUtilisateur.MEMBRE
                && localiteDossier.equals(controleur.getIdLocalite());
    }

    /** Tous les Présidents (visibilité toutes localités). */
    public List<Controleur> presidents() {
        return parProfil(ProfilUtilisateur.PRESIDENT);
    }

    /** Tous les Chargés de publication. */
    public List<Controleur> chargesPublication() {
        return parProfil(ProfilUtilisateur.CHARGE_PUBLICATION);
    }

    /** Tous les Administrateurs. */
    public List<Controleur> administrateurs() {
        return parProfil(ProfilUtilisateur.ADMINISTRATEUR);
    }

    /** Les Chefs de commission d'une localité donnée. */
    public List<Controleur> chefsCommission(String idLocalite) {
        List<Integer> ids = idProfiles(ProfilUtilisateur.CHEF_COMMISSION);
        if (ids.isEmpty() || idLocalite == null) {
            return List.of();
        }
        return controleurRepository.findByIdProfileInAndIdLocalite(ids, idLocalite);
    }

    /** Les Secrétaires d'une localité donnée (réception des dossiers, §3.4). */
    public List<Controleur> secretaires(String idLocalite) {
        List<Integer> ids = idProfiles(ProfilUtilisateur.SECRETAIRE);
        if (ids.isEmpty() || idLocalite == null) {
            return List.of();
        }
        return controleurRepository.findByIdProfileInAndIdLocalite(ids, idLocalite);
    }

    /** Les Contrôleurs vérificateurs d'une localité donnée (§3.6, transmission du PV signé). */
    public List<Controleur> verificateurs(String idLocalite) {
        List<Integer> ids = idProfiles(ProfilUtilisateur.VERIFICATEUR);
        if (ids.isEmpty() || idLocalite == null) {
            return List.of();
        }
        return controleurRepository.findByIdProfileInAndIdLocalite(ids, idLocalite);
    }

    /** Les Assistants contrôleurs d'une localité donnée (copies lettres de renvoi / PV définitifs). */
    public List<Controleur> assistantsControleurs(String idLocalite) {
        List<Integer> ids = idProfiles(ProfilUtilisateur.ASSISTANT_CONTROLEUR);
        if (ids.isEmpty() || idLocalite == null) {
            return List.of();
        }
        return controleurRepository.findByIdProfileInAndIdLocalite(ids, idLocalite);
    }

    /** ⚠️ 2026-09-01 — ouverte au package pour le service des rattachements (liste des porteurs). */
    List<Controleur> parProfil(ProfilUtilisateur profil) {
        List<Integer> ids = idProfiles(profil);
        return ids.isEmpty() ? List.of() : controleurRepository.findByIdProfileIn(ids);
    }

    private List<Integer> idProfiles(ProfilUtilisateur profil) {
        return profileRepository.findAll().stream()
                .filter(p -> ProfilUtilisateur.resolve(p.getProfile()) == profil)
                .map(Profile::getIdProfile)
                .toList();
    }
}
