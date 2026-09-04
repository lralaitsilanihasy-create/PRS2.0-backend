package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DispatchDto;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Localite;
import cnm.prs.entity.Dispatch;
import cnm.prs.entity.Reception;
import cnm.prs.enums.EtapeCircuit;
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
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.ProfileRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.PermissionService;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link Dispatch}.
 *
 * <p>⚠️ Audit 2026-08-27, lot B — le {@code PUT} générique ne rejouait aucune des préconditions du
 * {@code POST} (statut du dossier, localité, anti-doublon), et le dispatcheur tracé venait du corps
 * de requête. Voir {@link #update} et {@link #dispatcheurAuthentifie()}.</p>
 */
@Service
@Transactional
public class DispatchService {

    /** Journal des transitions du circuit (⚠️ LOT 4 — 2026-08-26), format {@code [CIRCUIT] …}. */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DispatchService.class);

    private final DispatchRepository repository;
    private final ReceptionRepository receptionRepository;
    private final ControleurRepository controleurRepository;
    private final DossierRepository dossierRepository;
    private final NotificationService notificationService;
    private final CircuitCascadeService circuitCascadeService;
    private final ControleurDirectory controleurDirectory;
    private final PermissionService permissionService;
    private final ProfileRepository profileRepository;
    /** ⚠️ Chronométrage des délais (2026-09-01) — clôture de l'étape DISPATCH. */
    private final ChronometrageService chronometrageService;
    /** ⚠️ Journal du circuit (2026-09-04) — dispatch, réattribution, reprise, retrait. */
    private final JournalDossierService journalDossier;
    /** ⚠️ Réattribution (2026-09-03) — refus si un examen est déjà entamé sur le dispatch. */
    private final ExamenRepository examenRepository;

    public DispatchService(DispatchRepository repository, ReceptionRepository receptionRepository,
            ControleurRepository controleurRepository, DossierRepository dossierRepository,
            NotificationService notificationService, CircuitCascadeService circuitCascadeService,
            ControleurDirectory controleurDirectory, PermissionService permissionService,
            ProfileRepository profileRepository, ChronometrageService chronometrageService,
            JournalDossierService journalDossier, ExamenRepository examenRepository) {
        this.journalDossier = journalDossier;
        this.examenRepository = examenRepository;
        this.chronometrageService = chronometrageService;
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
        exigerPresidentSiCentrale(dto.getIdReception(), false);
        exigerDossierPretDispatch(dto.getIdReception());
        interdireDoublonDispatch(dto.getIdReception());
        validerInterimDispatch(dto);
        validerAttributaireMembre(dto);
        Dispatch entity = DispatchMapper.toEntity(dto);
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        entity.setIdDispatch(ClePrimaire.reallouer(dto.getIdDispatch(), repository::existsById, repository::nextIdDispatch));
        entity.setImCtrlDispatch(dispatcheurAuthentifie());   // ⚠️ audit lot B — identité = JWT
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
        // ⚠️ Journal du circuit (2026-09-04) : le dispatch ne garde que son dernier etat — sans cette
        // trace, une reattribution ulterieure effacerait qui l a recu en premier.
        Integer idDossierDispatche = dossierDeLaReception(saved.getIdReception());
        if (idDossierDispatche != null) {
            String detail = "à " + nomControleur(saved.getImCtrlMembre())
                    + (saved.getImCtrlCc() == null ? "" : " · copie à " + nomControleur(saved.getImCtrlCc()))
                    + consigne(saved);
            journalDossier.tracerControleur(idDossierDispatche, JournalDossierService.DISPATCH, detail);
        }
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
                // ⚠️ Chronométrage (2026-09-01) — le dispatch clôt l'étape DISPATCH.
                chronometrageService.cloturer(idDossier, EtapeCircuit.DISPATCH);
                log.info("[CIRCUIT] dispatch dossier={} acteur={} reception={} statut={}",
                        idDossier, CurrentUser.login().orElse(null), idReception,
                        StatutDossier.DISPATCHE.name());
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

    /**
     * ⚠️ Audit 2026-08-27 (lot B) — le dispatcheur tracé est l'<strong>utilisateur authentifié</strong>,
     * jamais le champ {@code imCtrlDispatch} du corps : c'est une trace de circuit (elle décide de la
     * copie {@code DISPATCH_CC} et se lit dans l'historique du dossier). Le front envoyait déjà sa
     * propre {@code ref} — le contrat ne bouge pas, la valeur du corps est simplement ignorée.
     */
    private String dispatcheurAuthentifie() {
        return CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "Dispatcheur non identifié."));
    }

    /**
     * ⚠️ Audit 2026-08-27 (lot B) — précondition du {@code PUT} : l'attribution ne se corrige que tant
     * que le PV n'est pas signé — dossier {@link StatutDossier#PRET_DISPATCH} (dispatch enregistré mais
     * dossier non encore avancé), {@link StatutDossier#DISPATCHE} ou {@link StatutDossier#EXAMINE}.
     * Même frontière que {@link #annuler} : au-delà, l'examen et le PV s'appuient sur l'attributaire.
     */
    private void exigerDossierAvantPvSigne(Integer idReception) {
        String statut = idReception == null ? null
                : dossierRepository.findStatutByReception(idReception).orElse(null);
        boolean corrigeable = StatutDossier.PRET_DISPATCH.name().equals(statut)
                || StatutDossier.DISPATCHE.name().equals(statut)
                || StatutDossier.EXAMINE.name().equals(statut);
        if (!corrigeable) {
            throw new BusinessRuleException(
                    "Correction du dispatch impossible : le dossier doit être au statut PRET_DISPATCH, "
                            + "DISPATCHE ou EXAMINE (avant PV signé), statut actuel « " + statut + " ».");
        }
    }

    /** Anti-doublon (§3.2, « dossiers complets sans dispatch existant ») : un seul dispatch par réception. */
    private void interdireDoublonDispatch(Integer idReception) {
        if (idReception != null && repository.existsByIdReception(idReception)) {
            throw new BusinessRuleException(
                    "Un dispatch existe déjà pour cette réception (§3.2) ; corrigez-le via PUT /api/dispatchs/{id}.");
        }
    }

    /**
     * ⚠️ Audit 2026-08-27 (lot B) — le {@code PUT} n'avait <strong>aucune</strong> des trois gardes du
     * {@code POST} : ni statut du dossier, ni localité, ni anti-doublon. Corriger un dispatch permettait
     * donc de le re-cibler sur la réception d'un autre dossier (créant le second dispatch que le POST
     * interdit), depuis n'importe quelle localité, sur un dossier déjà statué.
     *
     * <p>Sont désormais exigés : le dossier <strong>en place</strong> et le dossier <strong>visé</strong>
     * dans la localité de l'appelant (§3.3), un statut de dossier au plus {@code EXAMINE} (au-delà, le
     * PV est signé et l'attribution est figée — même frontière que {@link #annuler}), et l'anti-doublon
     * rejoué si {@code idReception} change. {@code IM_CTRL_DISPATCH} vient du JWT, comme au POST.</p>
     */
    public DispatchDto update(Integer id, DispatchDto dto) {
        Dispatch existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispatch introuvable : " + id));
        // ⚠️ Dérogation « centrale » (2026-09-03) : le CC ATTRIBUTAIRE COURANT peut réattribuer le
        // dossier que le Président lui a confié. La garde ne le vise donc pas dans ce cas précis.
        // Le CC « concerne » par le dispatch : son ATTRIBUTAIRE courant (le President le lui a confie)
        // OU son DISPATCHEUR. Le second cas n est pas une facilite : le « Retirer » du CC est un PUT de
        // reattribution VERS LUI-MEME, or apres avoir reattribue a un Membre il n est plus attributaire
        // mais dispatcheur. S en tenir a l attributaire lui interdirait de reprendre son propre dossier,
        // ce que la regle prevoit explicitement. Un CC etranger au dispatch reste refuse.
        String moi = CurrentUser.ref().orElse(null);
        boolean jeSuisConcerne = moi != null
                && (moi.equals(existing.getImCtrlMembre()) || moi.equals(existing.getImCtrlDispatch()));
        exigerPresidentSiCentrale(existing.getIdReception(), jeSuisConcerne);
        exigerPresidentSiCentrale(dto.getIdReception(), jeSuisConcerne);
        Visibilite.exigerLocalite(resoudreLocaliteDossier(existing.getIdReception()));
        Visibilite.exigerLocalite(resoudreLocaliteDossier(dto.getIdReception()));
        exigerDossierAvantPvSigne(existing.getIdReception());
        exigerDossierAvantPvSigne(dto.getIdReception());
        if (!java.util.Objects.equals(existing.getIdReception(), dto.getIdReception())) {
            interdireDoublonDispatch(dto.getIdReception());   // re-ciblage : un seul dispatch par réception
        }
        validerInterimDispatch(dto);
        validerAttributaireMembre(dto);
        String ancienAttributaire = existing.getImCtrlMembre();
        boolean changementAttributaire = !java.util.Objects.equals(ancienAttributaire, dto.getImCtrlMembre());
        // ⚠️ Réattribution (2026-09-03) — 409 si l'examen est entamé : le circuit propre passe par
        // « Retirer », qui purge l'aval. Changer l'attributaire ici laisserait à l'arrivant l'examen
        // commencé par un autre, sous son propre nom.
        if (changementAttributaire && examenRepository.existsByIdDispatch(id)) {
            throw new BusinessRuleException("Réattribution impossible : l'examen de ce dossier est déjà "
                    + "entamé. Retirez le dossier au Membre (ce qui purge l'examen) avant de le réattribuer.");
        }
        existing.setIdReception(dto.getIdReception());
        existing.setImCtrlDispatch(dispatcheurAuthentifie());   // ⚠️ audit lot B — identité = JWT
        existing.setImCtrlCc(dto.getImCtrlCc());
        existing.setImCtrlMembre(dto.getImCtrlMembre());
        existing.setDateDispatch(DispatchMapper.toLocalDateTime(dto.getDateDispatch()));
        existing.setDateCtrlAssigne(dto.getDateCtrlAssigne());
        existing.setInstructions(dto.getInstructions());
        existing.setInterimDispatch(dto.getInterimDispatch());
        // Même règle d'association CC qu'au POST (sans auto-association : le PUT respecte le corps).
        normaliserAssociationCc(existing, false);
        Dispatch sauve = repository.save(existing);

        // ⚠️ Réattribution (2026-09-03) — le PUT ne notifiait personne : l'ancien attributaire voyait le
        // dossier disparaître de sa file en silence, le nouveau ne savait pas qu'il l'avait reçu.
        if (changementAttributaire) {
            Integer idDossierReattribue = dossierDeLaReception(sauve.getIdReception());
            notifierReattribution(sauve, ancienAttributaire, idDossierReattribue);
            tracerReattribution(sauve, ancienAttributaire, idDossierReattribue);
            // ⚠️ Chronométrage (règle du pilote, 2026-09-04) — le geste du réattribueur laisse SA ligne.
            // Le journal portait bien DISPATCH puis REATTRIBUTION, mais le chronométrage n'avait qu'une
            // tâche : le passage par le CC n'existait nulle part dans le tableau des passages, alors
            // qu'un retrait suivi d'un re-dispatch, lui, en produisait une. Le chemin réel doit se lire
            // aux deux endroits, et avec les mêmes acteurs.
            //
            // Ce seul appel couvre AUSSI la REPRISE : le « Retirer » du CC est un PUT vers lui-même,
            // donc un changement d'attributaire. Le « rendre » du Membre, lui, n'existe pas comme geste
            // (aucun endpoint) : il reste hors lot, faute d'objet.
            chronometrageService.consignerGesteInstantane(idDossierReattribue, EtapeCircuit.DISPATCH);
        }
        return toDtoComplet(sauve);
    }

    /**
     * Notifie le changement d'attributaire. Le <strong>nouveau</strong> reçoit un {@code EXAMEN_A_FAIRE}
     * comme au POST — <strong>sauf s'il est l'acteur lui-même</strong> (reprise : on ne s'annonce pas à
     * soi-même un dossier qu'on vient de reprendre). L'<strong>ancien</strong> est prévenu du retrait.
     */
    private void notifierReattribution(Dispatch dispatch, String ancienAttributaire, Integer idDossier) {
        if (idDossier == null) {
            return;
        }
        String moi = CurrentUser.ref().orElse(null);
        String nouveau = dispatch.getImCtrlMembre();
        if (nouveau != null && !nouveau.equals(moi)) {
            controleurRepository.findById(nouveau).ifPresent(c -> notificationService.emettre(idDossier,
                    TypeNotification.EXAMEN_A_FAIRE, c.getImControleur(), c.getEmailCont(),
                    "Dossier à examiner",
                    "Le dossier " + idDossier + " vous est attribué pour examen."));
        }
        if (ancienAttributaire != null && !ancienAttributaire.equals(nouveau)) {
            controleurRepository.findById(ancienAttributaire).ifPresent(c -> notificationService.emettre(
                    idDossier, TypeNotification.EXAMEN_A_FAIRE, c.getImControleur(), c.getEmailCont(),
                    "Dossier retiré de votre file",
                    "Le dossier " + idDossier + " ne vous est plus attribué : il a été réattribué."));
        }
    }

    /**
     * Consigne le geste au journal du circuit. <strong>REPRISE</strong> quand l'acteur se réattribue le
     * dossier (le « Retirer » du CC est un PUT vers lui-même, pas une annulation), <strong>
     * REATTRIBUTION</strong> sinon — la distinction est ce que le pilote demandait à voir.
     */
    private void tracerReattribution(Dispatch dispatch, String ancienAttributaire, Integer idDossier) {
        if (idDossier == null) {
            return;
        }
        String moi = CurrentUser.ref().orElse(null);
        String nouveau = dispatch.getImCtrlMembre();
        if (nouveau != null && nouveau.equals(moi)) {
            journalDossier.tracerControleur(idDossier, JournalDossierService.REPRISE,
                    "reprise à " + nomControleur(ancienAttributaire));
        } else {
            journalDossier.tracerControleur(idDossier, JournalDossierService.REATTRIBUTION,
                    "de " + nomControleur(ancienAttributaire) + " à " + nomControleur(nouveau) + consigne(dispatch));
        }
    }

    /**
     * ⚠️ <strong>Pré-dispatch de la CENTRALE réservé au Président</strong> (règle du pilote, 2026-09-03).
     *
     * <p>« Pour le dossier de localité centrale (CNM), le CC ne doit pas voir les dossiers pour
     * pré-dispatch. Seul le Président en a ce privilège. » Les commissions <strong>régionales</strong>
     * sont inchangées : leur CC continue de dispatcher chez lui.</p>
     *
     * <p><strong>Garde par PROFIL COURANT</strong>, et non par la garde centrale de délégation : le
     * dispatch est un droit <em>natif</em> du CC, les paires de {@code t_delegation_profil} n'ont pas à
     * l'ouvrir ni à le fermer — même raisonnement que {@code normaliserAssociationCc}.</p>
     *
     * <p><strong>Dérogation</strong> (précision du même jour) : « le CC peut dispatcher le dossier que
     * le président lui a dispatché ». Le CC <strong>attributaire courant</strong> peut donc RÉATTRIBUER
     * — c'est {@code reattributionParAttributaire} qui le dit. La garde ne vise que le POST initial, un
     * PUT sur un dispatch dont il n'est pas l'attributaire, et l'intérim.</p>
     */
    private void exigerPresidentSiCentrale(Integer idReception, boolean reattributionParAttributaire) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.CHEF_COMMISSION
                || reattributionParAttributaire) {
            return;
        }
        if (Localite.estCentrale(resoudreLocaliteDossier(idReception))) {
            throw new org.springframework.security.access.AccessDeniedException("Le dispatch d'un dossier de la Commission nationale "
                    + "(localité centrale) relève du seul Président.");
        }
    }

    /**
     * ⚠️ <strong>Retrait réservé au dispatcheur</strong> (arbitrage du pilote, 2026-09-03) — garde
     * GÉNÉRALE, toutes localités : « Le CC ne doit pas pouvoir retirer le dossier qu'il n'a pas
     * dispatché. Par contre, il peut retirer le dossier s'il est le dispatcheur de ce dossier. »
     *
     * <p><strong>Pas d'auto-retrait</strong> (confirmé le même jour) : le CC <em>attributaire</em> d'un
     * dossier que le Président lui a confié ne se le retire pas lui-même — c'est le Président, qui l'a
     * dispatché, qui le lui retire. Le cas où il est à la fois dispatcheur ET attributaire (post-reprise)
     * est refusé pour la même raison : rendre le dossier n'est pas un geste qu'on se fait à soi-même.</p>
     *
     * <p>Le Président n'est pas restreint. Une réattribution par le CC pose {@code IM_CTRL_DISPATCH} =
     * son matricule : il peut donc ensuite RETIRER AU MEMBRE, ce qui est voulu.</p>
     */
    private void exigerDispatcheurPourAnnuler(Dispatch dispatch) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.CHEF_COMMISSION) {
            return;
        }
        String moi = CurrentUser.ref().orElse(null);
        boolean dispatcheur = moi != null && moi.equals(dispatch.getImCtrlDispatch());
        boolean attributaire = moi != null && moi.equals(dispatch.getImCtrlMembre());
        if (!dispatcheur) {
            throw new org.springframework.security.access.AccessDeniedException("Retrait réservé au dispatcheur du dossier : vous n'avez pas "
                    + "dispatché ce dossier. Demandez le retrait au Président.");
        }
        if (attributaire) {
            throw new org.springframework.security.access.AccessDeniedException("Pas d'auto-retrait : vous êtes l'attributaire de ce dossier. "
                    + "Demandez le retrait au Président.");
        }
    }

    /**
     * ⚠️ <strong>La consigne au journal</strong> (complément du pilote, 2026-09-04) — « Comment savoir
     * que le dossier a été dispatché au CC avec instruction avant de le dispatcher au membre ? »
     *
     * <p>Le dispatch ne garde que la <strong>dernière</strong> consigne : le PUT de réattribution écrase
     * celle du Président au CC, qui disparaît alors sans trace. La consigner dans le {@code detail} —
     * append-only — la rend définitive, au moment où elle a été donnée et par qui.</p>
     *
     * <p>Rendue vide quand il n'y en a pas, pour ne pas afficher une rubrique creuse. Le
     * {@code detail} est tronqué à 500 caractères par le journal, ce qui borne le cumul.</p>
     */
    private static String consigne(Dispatch dispatch) {
        String instructions = dispatch.getInstructions();
        if (instructions == null || instructions.isBlank()) {
            return "";
        }
        return " — consigne : « " + instructions.trim() + " »";
    }

    /** Nom lisible d'un contrôleur pour le journal ; repli sur le matricule. */
    private String nomControleur(String im) {
        if (im == null) {
            return "—";
        }
        return controleurRepository.findById(im).map(c -> {
            String nom = ((c.getPrenomsCont() == null ? "" : c.getPrenomsCont()) + " "
                    + (c.getNomCont() == null ? "" : c.getNomCont())).trim();
            return nom.isBlank() ? im : nom;
        }).orElse(im);
    }

    /** Dossier porté par une réception, ou {@code null} — repère commun au journal et aux gardes. */
    private Integer dossierDeLaReception(Integer idReception) {
        return idReception == null ? null
                : receptionRepository.findById(idReception).map(Reception::getIdDossier).orElse(null);
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
        exigerDispatcheurPourAnnuler(entity);
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
            log.info("[CIRCUIT] dispatch annule dossier={} acteur={} statutPrecedent={} statut={}",
                    idDossier, CurrentUser.login().orElse(null), statut,
                    StatutDossier.PRET_DISPATCH.name());
        });
        // ⚠️ Journal du circuit (2026-09-04) — la ligne SURVIT a la suppression du dispatch : c est
        // tout l interet d un journal append-only, le dispatch lui-meme ne garde aucune trace du retrait.
        if (idDossier != null) {
            journalDossier.tracerControleur(idDossier, JournalDossierService.RETRAIT_DISPATCH,
                    "retiré à " + nomControleur(entity.getImCtrlMembre()) + " — retour en pré-dispatch");
        }
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
