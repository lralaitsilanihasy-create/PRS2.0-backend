package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DispatchDto;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dispatch;
import cnm.prs.entity.Reception;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.DispatchMapper;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ProfileRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.PermissionService;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link Dispatch}.
 */
@Service
@Transactional
public class DispatchService {

    private final DispatchRepository repository;
    private final ReceptionRepository receptionRepository;
    private final ControleurRepository controleurRepository;
    private final DossierRepository dossierRepository;
    private final NotificationService notificationService;
    private final CircuitCascadeService circuitCascadeService;
    private final ControleurDirectory controleurDirectory;
    private final PermissionService permissionService;
    private final ProfileRepository profileRepository;

    public DispatchService(DispatchRepository repository, ReceptionRepository receptionRepository,
            ControleurRepository controleurRepository, DossierRepository dossierRepository,
            NotificationService notificationService, CircuitCascadeService circuitCascadeService,
            ControleurDirectory controleurDirectory, PermissionService permissionService,
            ProfileRepository profileRepository) {
        this.repository = repository;
        this.receptionRepository = receptionRepository;
        this.controleurRepository = controleurRepository;
        this.dossierRepository = dossierRepository;
        this.notificationService = notificationService;
        this.circuitCascadeService = circuitCascadeService;
        this.controleurDirectory = controleurDirectory;
        this.permissionService = permissionService;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public List<DispatchDto> findAll() {
        // ⚠️ Règle ajoutée — exclut les dossiers BROUILLON/RETIRE (jamais visibles à l'écran « Dispatch »).
        return Visibilite.filtrer(repository::findVisibles, repository::findVisiblesParLocalite)
                .stream().map(this::toDtoComplet).toList();
    }

    @Transactional(readOnly = true)
    public DispatchDto findById(Integer id) {
        Dispatch entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispatch introuvable : " + id));
        Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        return toDtoComplet(entity);
    }

    /**
     * Mappe le dispatch puis enrichit {@code datePredispatch} = date/heure de réception du dossier
     * par le secrétaire ({@code t_reception.DATE_RECEPTION} la plus récente du dossier rattaché,
     * via la réception du dispatch). {@code null} si aucune réception datée.
     */
    private DispatchDto toDtoComplet(Dispatch entity) {
        DispatchDto dto = DispatchMapper.toDto(entity);
        Integer idDossier = entity.getIdReception() == null ? null
                : receptionRepository.findById(entity.getIdReception())
                        .map(Reception::getIdDossier).orElse(null);
        if (idDossier != null) {
            dto.setDatePredispatch(
                    DispatchMapper.format(receptionRepository.findDerniereDateReceptionByDossier(idDossier)));
        }
        return dto;
    }

    public DispatchDto create(DispatchDto dto) {
        exigerDossierPretDispatch(dto.getIdReception());
        interdireDoublonDispatch(dto.getIdReception());
        validerInterimDispatch(dto);
        validerAttributaireMembre(dto);
        Dispatch entity = DispatchMapper.toEntity(dto);
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        entity.setIdDispatch(ClePrimaire.reallouer(dto.getIdDispatch(), repository::existsById, repository::nextIdDispatch));
        // ⚠️ Règle MODIFIÉE (2026-08-15) — l'association CC ne vaut que quand le Président dispatche
        // à un Membre (le CC suit alors les dossiers de sa commission) : voir normaliserAssociationCc.
        normaliserAssociationCc(entity, true);
        Dispatch saved = repository.save(entity);
        // [Auto] Le dossier avance PRET_DISPATCH → DISPATCHE, dans la même transaction que le dispatch.
        avancerDossierVersDispatche(dto.getIdReception());
        // [Auto] Le Membre assigné est notifié qu'un dossier lui est transmis pour examen.
        notifierMembreAssigne(saved);
        // [Auto] Copie du dispatch au CC associé (sauf s'il est lui-même le dispatcheur).
        notifierCcCopie(saved);
        return toDtoComplet(saved);
    }

    /**
     * ⚠️ Règle MODIFIÉE (2026-08-15, spec dispatch) — l'association/copie CC ne vaut que quand le
     * <strong>Président dispatche à un Membre</strong> (le CC de la localité suit alors les dossiers
     * de sa commission) :
     * <ul>
     *   <li><strong>dispatcheur CC</strong> → aucune association (il est l'acteur du dispatch, quelle
     *       que soit l'attribution — Membre ou lui-même) : un {@code imCtrlCc} envoyé par le client
     *       est <strong>ignoré</strong> (forcé à null, documenté) — jamais de copie de son propre
     *       dispatch ;</li>
     *   <li><strong>Président auto-attributaire</strong> ({@code imCtrlMembre} = lui-même) → pas
     *       d'association non plus (la copie n'a de sens que pour un dispatch « à un Membre ») ;</li>
     *   <li>l'association ne désigne <strong>jamais l'attributaire lui-même</strong> (ex. Président →
     *       CC-par-délégation) — plus de doublon « Rôle Membre + Rôle CC » dans les attributions ;</li>
     *   <li>sinon (Président → Membre) : comportement conservé — {@code imCtrlCc} fourni respecté, à
     *       défaut le CC de la localité du dossier est associé automatiquement (au POST) et reçoit la
     *       copie {@code DISPATCH_CC}.</li>
     * </ul>
     */
    private void normaliserAssociationCc(Dispatch entity, boolean associerParDefaut) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        String moi = CurrentUser.ref().orElse(null);
        boolean autoAttribution = moi != null && moi.equals(entity.getImCtrlMembre());
        if (profil == ProfilUtilisateur.CHEF_COMMISSION || autoAttribution) {
            entity.setImCtrlCc(null);
            return;
        }
        if (associerParDefaut && (entity.getImCtrlCc() == null || entity.getImCtrlCc().isBlank())) {
            String localite = resoudreLocaliteDossier(entity.getIdReception());
            controleurDirectory.chefsCommission(localite).stream().findFirst()
                    .ifPresent(cc -> entity.setImCtrlCc(cc.getImControleur()));
        }
        if (entity.getImCtrlCc() != null && entity.getImCtrlCc().equals(entity.getImCtrlMembre())) {
            entity.setImCtrlCc(null); // jamais la même personne que l'attributaire
        }
    }

    /** [Auto] Copie de dispatch ({@code DISPATCH_CC}) au CC associé — informé du circuit du dossier (§3.3). */
    private void notifierCcCopie(Dispatch dispatch) {
        String imCc = dispatch.getImCtrlCc();
        if (imCc == null || imCc.isBlank() || imCc.equals(dispatch.getImCtrlDispatch())) {
            return;
        }
        Integer idDossier = dispatch.getIdReception() == null ? null
                : receptionRepository.findById(dispatch.getIdReception())
                        .map(Reception::getIdDossier).orElse(null);
        String membre = dispatch.getImCtrlMembre() == null ? null
                : controleurRepository.findById(dispatch.getImCtrlMembre())
                        .map(c -> (c.getNomCont() == null ? "" : c.getNomCont() + " ")
                                + (c.getPrenomsCont() == null ? "" : c.getPrenomsCont()))
                        .map(String::trim).orElse(dispatch.getImCtrlMembre());
        String email = controleurRepository.findById(imCc).map(Controleur::getEmailCont).orElse(null);
        notificationService.emettreControleur(TypeNotification.DISPATCH_CC, imCc, email,
                idDossier, TypeObjet.DOSSIER, idDossier,
                "Copie de dispatch",
                "Le dossier " + idDossier + " a été dispatché"
                        + (membre == null || membre.isBlank() ? "" : " à " + membre) + " pour examen.");
    }

    /** [Auto] Notifie le Membre assigné ({@code EXAMEN_A_FAIRE}) du dossier dispatché. */
    private void notifierMembreAssigne(Dispatch dispatch) {
        String imMembre = dispatch.getImCtrlMembre();
        if (imMembre == null || imMembre.isBlank()) {
            return;
        }
        Integer idDossier = dispatch.getIdReception() == null ? null
                : receptionRepository.findById(dispatch.getIdReception())
                        .map(Reception::getIdDossier).orElse(null);
        String email = controleurRepository.findById(imMembre).map(Controleur::getEmailCont).orElse(null);
        notificationService.emettreControleur(TypeNotification.EXAMEN_A_FAIRE, imMembre, email,
                idDossier, TypeObjet.DOSSIER, idDossier,
                "Dossier à examiner",
                "Le dossier " + idDossier + " vous a été dispatché pour examen.");
    }

    /**
     * [Auto] ⚠️ Règle ajoutée : à la création d'un dispatch, le dossier passe de
     * {@link StatutDossier#PRET_DISPATCH} à {@link StatutDossier#DISPATCHE} (même transaction).
     * La précondition {@link #exigerDossierPretDispatch} garantit l'état de départ ; on ne réécrit
     * que si le dossier est bien PRET_DISPATCH (jamais un dossier déjà clôturé/retiré).
     */
    private void avancerDossierVersDispatche(Integer idReception) {
        if (idReception == null) {
            return;
        }
        Integer idDossier = receptionRepository.findById(idReception)
                .map(Reception::getIdDossier).orElse(null);
        if (idDossier == null) {
            return;
        }
        dossierRepository.findById(idDossier).ifPresent(d -> {
            if (StatutDossier.PRET_DISPATCH.name().equals(d.getStatut())) {
                d.setStatut(StatutDossier.DISPATCHE.name());
                dossierRepository.save(d);
            }
        });
    }

    /**
     * Précondition du circuit (§2.2 → §2.3) : on ne dispatche qu'un dossier au statut
     * {@link StatutDossier#PRET_DISPATCH} (donc complet et pas encore clôturé/retiré).
     */
    private void exigerDossierPretDispatch(Integer idReception) {
        String statut = idReception == null ? null
                : dossierRepository.findStatutByReception(idReception).orElse(null);
        if (!StatutDossier.PRET_DISPATCH.name().equals(statut)) {
            throw new BusinessRuleException(
                    "Dispatch impossible : le dossier doit être au statut PRET_DISPATCH (§2.2/§2.3), "
                            + "statut actuel « " + statut + " ».");
        }
    }

    /** Anti-doublon (§3.2, « dossiers complets sans dispatch existant ») : un seul dispatch par réception. */
    private void interdireDoublonDispatch(Integer idReception) {
        if (idReception != null && repository.existsByIdReception(idReception)) {
            throw new BusinessRuleException(
                    "Un dispatch existe déjà pour cette réception (§3.2) ; corrigez-le via PUT /api/dispatchs/{id}.");
        }
    }

    public DispatchDto update(Integer id, DispatchDto dto) {
        validerInterimDispatch(dto);
        validerAttributaireMembre(dto);
        Dispatch existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispatch introuvable : " + id));
        existing.setIdReception(dto.getIdReception());
        existing.setImCtrlDispatch(dto.getImCtrlDispatch());
        existing.setImCtrlCc(dto.getImCtrlCc());
        existing.setImCtrlMembre(dto.getImCtrlMembre());
        existing.setDateDispatch(DispatchMapper.toLocalDateTime(dto.getDateDispatch()));
        existing.setDateCtrlAssigne(dto.getDateCtrlAssigne());
        existing.setInstructions(dto.getInstructions());
        existing.setInterimDispatch(dto.getInterimDispatch());
        // Même règle d'association CC qu'au POST (sans auto-association : le PUT respecte le corps).
        normaliserAssociationCc(existing, false);
        return toDtoComplet(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Dispatch introuvable : " + id);
        }
        repository.deleteById(id);
    }

    /**
     * ⚠️ Règle ajoutée — annulation d'un dispatch par le Président ou un CC (retrait du dossier au
     * Membre assigné), possible tant que le PV n'est pas signé : dossier {@link StatutDossier#DISPATCHE}
     * <em>ou</em> {@link StatutDossier#EXAMINE} (409 au-delà). Purge tout l'aval du dispatch (examen,
     * détails, observations, projet de PV, navettes, lettres, copies) puis le dispatch — la réception
     * est conservée — et fait revenir le dossier en PRET_DISPATCH (même transaction, re-dispatchable).
     * Périmètre de localité contrôlé (un CC n'annule que dans sa localité) ; le Membre est notifié.
     */
    public void annuler(Integer id) {
        Dispatch entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispatch introuvable : " + id));
        Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        Integer idDossier = entity.getIdReception() == null ? null
                : receptionRepository.findById(entity.getIdReception())
                        .map(Reception::getIdDossier).orElse(null);
        String statut = idDossier == null ? null
                : dossierRepository.findById(idDossier).map(d -> d.getStatut()).orElse(null);
        boolean annulable = StatutDossier.DISPATCHE.name().equals(statut)
                || StatutDossier.EXAMINE.name().equals(statut);
        if (!annulable) {
            throw new BusinessRuleException(
                    "Annulation impossible : le dossier doit être au statut DISPATCHE ou EXAMINE "
                            + "(avant PV signé), statut actuel « " + statut + " ».");
        }
        // Purge de l'aval du dispatch (examen/PV/…) + dispatchs ; la réception est conservée.
        circuitCascadeService.purgerApresDispatch(idDossier);
        dossierRepository.findById(idDossier).ifPresent(d -> {
            d.setStatut(StatutDossier.PRET_DISPATCH.name());
            dossierRepository.save(d);
        });
        notifierMembreRetrait(entity, idDossier);
        notifierCcRetrait(entity, idDossier);
    }

    /** [Auto] Copie de l'annulation au CC associé (sauf s'il est lui-même l'auteur du retrait). */
    private void notifierCcRetrait(Dispatch dispatch, Integer idDossier) {
        String imCc = dispatch.getImCtrlCc();
        String imCourant = CurrentUser.ref().orElse(null);
        if (imCc == null || imCc.isBlank() || imCc.equals(imCourant)) {
            return;
        }
        String email = controleurRepository.findById(imCc).map(Controleur::getEmailCont).orElse(null);
        notificationService.emettreControleur(TypeNotification.DISPATCH_ANNULE, imCc, email,
                idDossier, TypeObjet.DOSSIER, idDossier,
                "Dispatch annulé",
                "Le dispatch du dossier " + idDossier + " a été annulé : le dossier est de retour en pré-dispatch.");
    }

    /** [Auto] Notifie le Membre anciennement assigné que le dossier lui est retiré (dispatch annulé). */
    private void notifierMembreRetrait(Dispatch dispatch, Integer idDossier) {
        String imMembre = dispatch.getImCtrlMembre();
        if (imMembre == null || imMembre.isBlank()) {
            return;
        }
        String email = controleurRepository.findById(imMembre).map(Controleur::getEmailCont).orElse(null);
        notificationService.emettreControleur(TypeNotification.DISPATCH_ANNULE, imMembre, email,
                idDossier, TypeObjet.DOSSIER, idDossier,
                "Dossier retiré",
                "Le dispatch du dossier " + idDossier + " a été annulé : il ne vous est plus attribué.");
    }

    /**
     * Cohérence de {@code INTERIM_DISPATCH} selon le dispatcheur (§3.3, §3.2) :
     * <ul>
     *   <li>Président → dispatch titulaire, {@code INTERIM_DISPATCH = false} ;</li>
     *   <li>Chef de commission dans sa localité → titulaire, {@code false} ;</li>
     *   <li>Chef de commission hors de sa localité → intérim, {@code true} obligatoire.</li>
     * </ul>
     * Si la localité du dossier ne peut être déterminée, aucune contrainte n'est appliquée.
     */
    private void validerInterimDispatch(DispatchDto dto) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        boolean interim = Boolean.TRUE.equals(dto.getInterimDispatch());

        if (profil == ProfilUtilisateur.PRESIDENT) {
            if (interim) {
                throw new BusinessRuleException(
                        "Le Président dispatche en titulaire : INTERIM_DISPATCH doit être false (§3.2).");
            }
            return;
        }
        if (profil == ProfilUtilisateur.CHEF_COMMISSION) {
            String localiteDossier = resoudreLocaliteDossier(dto.getIdReception());
            if (localiteDossier == null) {
                return; // localité indéterminée → pas de contrainte
            }
            String localiteCc = CurrentUser.localite().orElse(null);
            boolean memeLocalite = localiteDossier.equals(localiteCc);
            if (memeLocalite && interim) {
                throw new BusinessRuleException(
                        "Dispatch dans votre localité : INTERIM_DISPATCH doit être false (§3.3).");
            }
            if (!memeLocalite && !interim) {
                throw new BusinessRuleException(
                        "Dispatch hors de votre localité : INTERIM_DISPATCH doit être true (§3.3).");
            }
        }
    }

    /**
     * ⚠️ Règle ajoutée (délégation ascendante, spec §3.5) — cohérence de l'attributaire :
     * {@code IM_CTRL_MEMBRE} doit désigner un contrôleur capable d'exercer la tâche du Membre —
     * titulaire (profil MEMBRE) ou couvert par une paire (profil → Membre) <strong>active</strong>
     * de {@code t_delegation_profil} (auto-attribution du Président/CC au dispatch). Sinon le
     * dossier serait inexaminable — l'examen est réservé à l'attributaire (§2.4) — d'où 409.
     * Data-driven : désactiver/réactiver la paire en base change la réponse sans changement de code.
     */
    private void validerAttributaireMembre(DispatchDto dto) {
        String im = dto.getImCtrlMembre();
        if (im == null || im.isBlank()) {
            return; // dispatch sans attributaire : toléré (l'examen/PV le bloquent en aval)
        }
        Controleur attributaire = controleurRepository.findById(im)
                .orElseThrow(() -> new BusinessRuleException(
                        "Attributaire invalide : aucun contrôleur avec le matricule « " + im + " »."));
        // Résolution par la FK scalaire ID_PROFILE (l'association lazy n'est pas fiable sur une
        // entité déjà en cache de session — elle peut être null alors que la FK est posée).
        ProfilUtilisateur profil = attributaire.getIdProfile() == null ? null
                : profileRepository.findById(attributaire.getIdProfile())
                        .map(p -> ProfilUtilisateur.resolve(p.getProfile())).orElse(null);
        if (!permissionService.peutExercer(profil, ProfilUtilisateur.MEMBRE)) {
            throw new BusinessRuleException(
                    "Attributaire invalide : « " + im + " » n'est ni Membre ni couvert par une délégation "
                            + "active vers Membre (t_delegation_profil) — le dossier serait inexaminable (§2.4).");
        }
    }

    /** Localité d'un dossier via sa réception : réception → contrôleur réceptionnaire → localité. */
    private String resoudreLocaliteDossier(Integer idReception) {
        if (idReception == null) {
            return null;
        }
        return receptionRepository.findById(idReception)
                .map(r -> r.getImCtrlRecept())
                .filter(im -> im != null)
                .flatMap(controleurRepository::findById)
                .map(c -> c.getIdLocalite())
                .orElse(null);
    }
}
