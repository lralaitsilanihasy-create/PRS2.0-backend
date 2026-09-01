package cnm.prs.service;

import java.util.List;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Controleur;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.TransmissionSigmpRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>Rattachements Membre → Vérificateur → Assistant</strong> (arbitrage du pilote, 2026-09-01).
 *
 * <p><strong>Règle du pilote</strong> : « Chaque Membre a un contrôleur Vérificateur rattaché à lui, et
 * chaque Vérificateur a lui-même un Assistant contrôleur rattaché à lui. Pour la vérification des
 * documents témoins rectifiés par la PRMP […], c'est le Vérificateur rattaché au Membre ayant examiné
 * le dossier qui se charge de la vérification et de la validation sur SIGMP/eGP, et l'Assistant
 * rattaché à ce Vérificateur se charge de l'archivage. »</p>
 *
 * <p><strong>⚠️ CIBLAGE, PAS HABILITATION (arbitrage 1).</strong> Le rattaché est le destinataire par
 * <em>défaut</em> — files et notifications. Aucun {@code exiger…} n'est posé ici : un autre
 * Vérificateur ou Assistant de la localité peut toujours agir, et l'acteur réel est déjà journalisé.
 * C'est l'esprit « instruction délégable » du 2026-08-15, par opposition aux actes d'identité (viser,
 * signer) qui, eux, ne se délèguent pas.</p>
 *
 * <p><strong>Repli (arbitrage 2).</strong> Chaîne non définie — circuit court avec un P/CC
 * auto-attribué, rattachement absent, rattaché supprimé du référentiel — le comportement d'avant
 * s'applique : tout Vérificateur de la localité, tout Assistant. Aucun blocage, jamais.</p>
 */
@Service
@Transactional(readOnly = true)
public class RattachementService {

    private final ControleurRepository controleurRepository;
    private final ExamenRepository examenRepository;
    private final TransmissionSigmpRepository transmissionRepository;
    private final ControleurDirectory controleurDirectory;

    public RattachementService(ControleurRepository controleurRepository, ExamenRepository examenRepository,
            TransmissionSigmpRepository transmissionRepository, ControleurDirectory controleurDirectory) {
        this.controleurRepository = controleurRepository;
        this.examenRepository = examenRepository;
        this.transmissionRepository = transmissionRepository;
        this.controleurDirectory = controleurDirectory;
    }

    // ------------------------------------------------------------------
    // Ciblage
    // ------------------------------------------------------------------

    /** Rattaché d'un contrôleur, ou vide si aucun (ou si le rattaché a disparu du référentiel). */
    public Optional<String> rattacheDe(String imPorteur) {
        if (imPorteur == null || imPorteur.isBlank()) {
            return Optional.empty();
        }
        return controleurRepository.findById(imPorteur.trim())
                .map(Controleur::getImRattache)
                .filter(im -> im != null && !im.isBlank())
                .filter(controleurRepository::existsById);
    }

    /**
     * Vérificateur <strong>cible</strong> d'un dossier en vérification : le rattaché du Membre ayant
     * EXAMINÉ le dossier.
     *
     * <p>⚠️ L'examinateur est {@code imCtrlMembre} de l'examen — <strong>jamais le co-signataire</strong>
     * du PV. Les deux sont des personnes différentes depuis le 2026-08-28, et c'est l'examinateur qui
     * porte l'instruction : c'est sa chaîne qui vérifie.</p>
     */
    public Optional<String> verificateurCible(Integer idDossier) {
        return examenRepository.findImCtrlMembreParDossier(idDossier).stream().findFirst()
                .flatMap(this::rattacheDe);
    }

    /**
     * Assistant <strong>cible</strong> pour l'archivage : le rattaché du Vérificateur qui a
     * <strong>effectivement</strong> validé — celui qui a transmis à SIGMP.
     *
     * <p>Suivre l'acteur effectif plutôt que la chaîne nominale est la recommandation de la spec, et
     * elle est juste : si un suppléant a validé à la place du vérificateur rattaché, c'est SA chaîne
     * qui doit archiver — sans quoi on notifierait l'assistant d'un vérificateur qui n'a rien fait.</p>
     *
     * <p>Replis, dans l'ordre : rattaché du valideur effectif → rattaché du vérificateur cible →
     * vide (tout Assistant, comportement d'avant).</p>
     */
    public Optional<String> assistantCible(Integer idDossier) {
        Optional<String> parValideurEffectif = transmissionRepository.findImVerificateurParDossier(idDossier)
                .stream().findFirst().flatMap(this::rattacheDe);
        if (parValideurEffectif.isPresent()) {
            return parValideurEffectif;
        }
        return verificateurCible(idDossier).flatMap(this::rattacheDe);
    }

    // ------------------------------------------------------------------
    // Écriture
    // ------------------------------------------------------------------

    /**
     * Pose ou retire le rattaché d'un contrôleur (arbitrage 3).
     *
     * <p><strong>Qui écrit</strong> : Administrateur partout, Président partout, Chef de commission
     * dans SA localité seulement (403 sinon).</p>
     *
     * <p><strong>Gardes sur le rattaché</strong>, toutes en 409 :</p>
     * <ul>
     *   <li>profil du porteur : seuls un MEMBRE et un VERIFICATEUR en portent un — la chaîne a deux
     *       maillons, un Assistant n'a pas de rattaché ;</li>
     *   <li>profil du rattaché : Vérificateur pour un Membre, Assistant pour un Vérificateur ;</li>
     *   <li>même localité que le porteur — ⚠️ APPLIQUÉE et non seulement recommandée : un rattachement
     *       inter-localités ciblerait des files qu'aucune règle de visibilité ne rend accessibles, et
     *       produirait un destinataire qui ne voit pas le dossier qu'on lui adresse. Un contrôleur sans
     *       localité ne déclenche aucune vérification, comme partout ailleurs
     *       ({@code Visibilite.exigerLocalite(null)}) ;</li>
     *   <li>pas d'auto-rattachement.</li>
     * </ul>
     *
     * @param imRattache matricule du rattaché, ou {@code null}/vide pour DÉTACHER
     */
    @Transactional
    public Controleur definirRattachement(String imPorteur, String imRattache) {
        Controleur porteur = controleurRepository.findById(imPorteur)
                .orElseThrow(() -> new ResourceNotFoundException("Contrôleur introuvable : " + imPorteur));
        exigerHabiliteSur(porteur);

        if (imRattache == null || imRattache.isBlank()) {
            porteur.setImRattache(null);   // détachement : toujours permis, le repli reprend la main
            return controleurRepository.save(porteur);
        }
        String cible = imRattache.trim();
        if (cible.equals(porteur.getImControleur())) {
            throw new BusinessRuleException("Un contrôleur ne peut pas être rattaché à lui-même.");
        }
        ProfilUtilisateur profilPorteur = controleurDirectory.profilDe(imPorteur).orElse(null);
        ProfilUtilisateur profilAttendu = profilAttenduDuRattache(profilPorteur);
        Controleur rattache = controleurRepository.findById(cible)
                .orElseThrow(() -> new BusinessRuleException("Rattaché invalide : aucun contrôleur « " + cible + " »."));
        ProfilUtilisateur profilRattache = controleurDirectory.profilDe(cible).orElse(null);
        if (profilRattache != profilAttendu) {
            throw new BusinessRuleException("Rattaché invalide : un " + libelle(profilPorteur)
                    + " se rattache un " + libelle(profilAttendu) + ", or « " + cible + " » est "
                    + (profilRattache == null ? "sans profil" : libelle(profilRattache)) + ".");
        }
        if (porteur.getIdLocalite() != null && rattache.getIdLocalite() != null
                && !porteur.getIdLocalite().equals(rattache.getIdLocalite())) {
            throw new BusinessRuleException("Rattachement inter-localités refusé : « " + cible + " » est de la "
                    + "localité " + rattache.getIdLocalite() + ", le porteur de la localité "
                    + porteur.getIdLocalite() + ". Le rattaché ne verrait pas les dossiers qu'on lui adresse.");
        }
        porteur.setImRattache(cible);
        return controleurRepository.save(porteur);
    }

    /**
     * Tableau des rattachements pour l'écran d'administration : les porteurs d'une chaîne (Membres et
     * Vérificateurs) du périmètre de l'acteur, avec leur rattaché résolu.
     *
     * <p>Un {@code imRattache} nul <strong>signale une chaîne incomplète</strong> — ce que la spec
     * demande d'exposer. Ce n'est pas une anomalie à corriger d'urgence : le repli localité fonctionne,
     * et une chaîne vide est l'état initial de tout le référentiel au déploiement.</p>
     *
     * <p>Résolution des noms <strong>en lot</strong> : un seul chargement de l'annuaire, quel que soit
     * le nombre de porteurs.</p>
     */
    public List<cnm.prs.dto.RattachementDto> tableau() {
        List<Controleur> membres = controleurDirectory.parProfil(ProfilUtilisateur.MEMBRE);
        List<Controleur> verificateurs = controleurDirectory.parProfil(ProfilUtilisateur.VERIFICATEUR);
        java.util.List<Controleur> porteurs = new java.util.ArrayList<>(membres);
        porteurs.addAll(verificateurs);
        String localite = localiteDeLActeur();
        if (localite != null) {
            porteurs = new java.util.ArrayList<>(
                    porteurs.stream().filter(c -> localite.equals(c.getIdLocalite())).toList());
        }
        java.util.Map<String, Controleur> annuaire = new java.util.HashMap<>();
        for (Controleur c : controleurRepository.findAll()) {
            annuaire.put(c.getImControleur(), c);
        }
        java.util.Set<String> idsMembres = membres.stream().map(Controleur::getImControleur)
                .collect(java.util.stream.Collectors.toSet());
        return porteurs.stream().map(p -> {
            boolean estMembre = idsMembres.contains(p.getImControleur());
            String attendu = (estMembre ? ProfilUtilisateur.VERIFICATEUR : ProfilUtilisateur.ASSISTANT_CONTROLEUR).name();
            Controleur r = p.getImRattache() == null ? null : annuaire.get(p.getImRattache());
            return new cnm.prs.dto.RattachementDto(p.getImControleur(), nomComplet(p),
                    (estMembre ? ProfilUtilisateur.MEMBRE : ProfilUtilisateur.VERIFICATEUR).name(),
                    p.getIdLocalite(), r == null ? null : r.getImControleur(), r == null ? null : nomComplet(r),
                    attendu);
        }).toList();
    }

    private static String nomComplet(Controleur c) {
        String n = ((c.getPrenomsCont() == null ? "" : c.getPrenomsCont()) + " "
                + (c.getNomCont() == null ? "" : c.getNomCont())).trim();
        return n.isBlank() ? c.getImControleur() : n;
    }

    private ProfilUtilisateur profilAttenduDuRattache(ProfilUtilisateur profilPorteur) {
        if (profilPorteur == ProfilUtilisateur.MEMBRE) {
            return ProfilUtilisateur.VERIFICATEUR;
        }
        if (profilPorteur == ProfilUtilisateur.VERIFICATEUR) {
            return ProfilUtilisateur.ASSISTANT_CONTROLEUR;
        }
        throw new BusinessRuleException("Seuls un Membre (→ Vérificateur) et un Vérificateur (→ Assistant) "
                + "portent un rattachement : la chaîne n'a que deux maillons.");
    }

    /** Localité qui borne l'acteur : nulle pour l'Administrateur et le Président (compétents partout). */
    private String localiteDeLActeur() {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == ProfilUtilisateur.ADMINISTRATEUR || profil == ProfilUtilisateur.PRESIDENT) {
            return null;
        }
        return CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
    }

    private void exigerHabiliteSur(Controleur porteur) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == ProfilUtilisateur.ADMINISTRATEUR || profil == ProfilUtilisateur.PRESIDENT) {
            return;
        }
        if (profil != ProfilUtilisateur.CHEF_COMMISSION) {
            throw new AccessDeniedException("Les rattachements sont administrés par l'Administrateur, le "
                    + "Président, ou le Chef de commission dans sa localité (§3.8).");
        }
        String maLocalite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (maLocalite == null || !maLocalite.equals(porteur.getIdLocalite())) {
            throw new AccessDeniedException("Le Chef de commission n'administre les rattachements que dans "
                    + "SA localité (§3.3).");
        }
    }

    private static String libelle(ProfilUtilisateur profil) {
        if (profil == ProfilUtilisateur.MEMBRE) {
            return "Membre";
        }
        if (profil == ProfilUtilisateur.VERIFICATEUR) {
            return "Vérificateur";
        }
        if (profil == ProfilUtilisateur.ASSISTANT_CONTROLEUR) {
            return "Assistant contrôleur";
        }
        return String.valueOf(profil);
    }
}
