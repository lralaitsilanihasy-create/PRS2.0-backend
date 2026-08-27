package cnm.prs.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ReceptionDto;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Localite;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Reception;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.TypeNotification;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.ReceptionMapper;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link Reception}.
 */
@Service
@Transactional
public class ReceptionService {

    /** Journal des transitions du circuit (⚠️ LOT 4 — 2026-08-26), format {@code [CIRCUIT] …}. */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReceptionService.class);

    private final ReceptionRepository repository;
    private final DossierRepository dossierRepository;
    private final PpmRepository ppmRepository;
    private final ControleurRepository controleurRepository;
    private final ControleurDirectory controleurDirectory;
    private final NotificationService notificationService;
    private final ReferenceService referenceService;
    private final VerificationPieceDepotService verificationPieceDepotService;

    public ReceptionService(ReceptionRepository repository, DossierRepository dossierRepository,
            PpmRepository ppmRepository, ControleurRepository controleurRepository,
            ControleurDirectory controleurDirectory, NotificationService notificationService,
            ReferenceService referenceService, VerificationPieceDepotService verificationPieceDepotService) {
        this.repository = repository;
        this.dossierRepository = dossierRepository;
        this.ppmRepository = ppmRepository;
        this.controleurRepository = controleurRepository;
        this.controleurDirectory = controleurDirectory;
        this.notificationService = notificationService;
        this.referenceService = referenceService;
        this.verificationPieceDepotService = verificationPieceDepotService;
    }

    @Transactional(readOnly = true)
    public List<ReceptionDto> findAll() {
        return Visibilite.filtrer(repository::findAll, repository::findVisiblesParLocalite)
                .stream().map(this::toDtoComplet).toList();
    }

    /**
     * Réceptions d'un <strong>seul dossier</strong> (filtre serveur {@code ?idDossier=}) — ne charge
     * que l'utile, dans le périmètre de l'appelant. Hors périmètre ou PRMP → liste vide (les
     * réceptions sont une ressource interne au circuit).
     */
    @Transactional(readOnly = true)
    public List<ReceptionDto> findByDossier(Integer idDossier) {
        if (idDossier == null) {
            return findAll();
        }
        if (Visibilite.estPrmp()) {
            return List.of();
        }
        if (!Visibilite.voitTout()) {
            String localite = Visibilite.localite().orElse(null);
            // Hors localité : aucune réception de ce dossier n'est visible.
            if (localite == null || !receptionsDansLocalite(idDossier, localite)) {
                return List.of();
            }
        }
        return repository.findByIdDossier(idDossier).stream().map(this::toDtoComplet).toList();
    }

    /**
     * Test léger « ce dossier est-il déjà réceptionné ? » (avant d'enregistrer une réception) —
     * sans charger l'historique. Renvoie {@code false} si le dossier est hors périmètre.
     */
    @Transactional(readOnly = true)
    public boolean dejaReceptionne(Integer idDossier) {
        if (idDossier == null || Visibilite.estPrmp()) {
            return false;
        }
        if (!Visibilite.voitTout()) {
            String localite = Visibilite.localite().orElse(null);
            if (localite == null || !receptionsDansLocalite(idDossier, localite)) {
                // Hors localité : on ne révèle pas l'état → traité comme « pas réceptionnable par vous ».
                return false;
            }
        }
        return repository.existsByIdDossier(idDossier);
    }

    /** Vrai si les réceptions du dossier (s'il en a) relèvent de la localité — ou s'il n'en a aucune. */
    private boolean receptionsDansLocalite(Integer idDossier, String localite) {
        List<String> locs = repository.findLocalitesByDossier(idDossier);
        return locs.isEmpty() || locs.contains(localite);
    }

    @Transactional(readOnly = true)
    public ReceptionDto findById(Integer id) {
        Reception entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reception introuvable : " + id));
        Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        return toDtoComplet(entity);
    }

    /**
     * Mappe la réception puis enrichit {@code dateSoumission} avec la date/heure de soumission du
     * dossier rattaché ({@code t_dossier.DATE_SOUMISSION}) — {@code null} pour un dossier ancien.
     */
    private ReceptionDto toDtoComplet(Reception entity) {
        ReceptionDto dto = ReceptionMapper.toDto(entity);
        if (dto != null && entity.getIdDossier() != null) {
            dossierRepository.findById(entity.getIdDossier())
                    .map(Dossier::getDateSoumission)
                    .ifPresent(ds -> dto.setDateSoumission(ReceptionMapper.format(ds)));
        }
        return dto;
    }

    public ReceptionDto create(ReceptionDto dto) {
        exigerDossierReceptionnable(dto.getIdDossier());
        // La localité est contrôlée AVANT l'anti-doublon : hors périmètre, on répond 403 sans révéler
        // l'historique de réception du dossier.
        exigerLocaliteDossier(dto.getIdDossier());
        validatePassage(dto);
        interdireDoublonPassageInitial(dto);
        exigerControleCompletude(dto);
        Reception entity = ReceptionMapper.toEntity(dto);
        entity.setIdReception(repository.nextIdReception().intValue());   // PK serveur (sequence), id client ignore (Voie B)
        Reception saved = repository.save(entity);
        String reference = genererReference(saved);   // (regle ajoutee) reference officielle a la reception
        saved.setReference(reference);                 // snapshot immuable persiste sur t_reception (survit aux mutations de refeDossier)
        saved = repository.save(saved);
        declencherPretDispatch(saved);
        return toDtoComplet(saved);                    // le mapper lit desormais reception.reference
    }

    /**
     * (Règle ajoutée) À la réception, génère la référence officielle
     * {@code xxxxx/sous_type/code_localite/annee} et la persiste sur le dossier ({@code REFE_DOSSIER},
     * REFE_DOSSIER restant vide depuis la soumission). ⚠️ Règle modifiée (2026-07-20) — le segment
     * central est le <strong>sous-type</strong> du dossier ({@code ID_SOUS_TYPE} : PPM, PPM-AGPM, DAO,
     * DAOR…), avec repli sur la <strong>famille</strong> ({@code ID_TYPE_DOSSIER}) si le sous-type est
     * absent (dossier historique non repris) ; la <strong>numérotation reste indexée sur la famille</strong>
     * (continuité inchangée).
     *
     * <p>⚠️ Règle CORRIGÉE (2026-08-04, demande user) — le segment localité dépend de la
     * <strong>localité DU DOSSIER</strong>, jamais de celle de l'agent qui enregistre : dossier de la
     * localité <strong>centrale</strong> ({@link Localite#ID_CENTRALE}) → {@code CNM} (ex.
     * {@code 00023/PPM/CNM/2026}) ; dossier régional → {@code CRM-<localité>} (ex.
     * {@code 00023/PPM/CRM-TMS/2026}). Auparavant le test portait sur la localité de l'utilisateur
     * courant : un Secrétaire d'Antananarivo (donc « avec localité ») produisait « CRM-ANT ».</p>
     */
    private String genererReference(Reception reception) {
        Dossier dossier = dossierRepository.findById(reception.getIdDossier()).orElse(null);
        if (dossier == null) {
            return null;
        }
        // ⚠️ Audit 2026-08-27 (lot B) — la référence était régénérée à CHAQUE réception : un passage
        // RETOUR renommait le dossier (sa référence officielle, déjà citée dans le PV, les lettres et
        // les courriers) et consommait un numéro de la séquence de l'année. Elle n'est produite qu'une
        // fois : si REFE_DOSSIER est déjà structurée, la réception en prend le snapshot, sans écriture
        // ni tirage de séquence.
        if (referenceStructuree(dossier.getRefeDossier())) {
            return dossier.getRefeDossier();
        }
        String famille = dossier.getIdTypeDossier();
        if (famille == null || famille.isBlank()) {
            // Dossier sans type : pas de référence structurée, mais la réception reste valide.
            return null;
        }
        // Segment affiché = sous-type ; repli sur la famille si le dossier n'a pas de sous-type.
        String segment = dossier.getIdSousType();
        if (segment == null || segment.isBlank()) {
            segment = famille;
        }
        String localite = localiteDuDossier(reception.getIdDossier());
        boolean estCentrale = Localite.estCentrale(localite);
        int annee = exerciceDuDossier(reception.getIdDossier());
        String reference = referenceService.generer(segment, famille, localite, estCentrale, annee);
        dossier.setRefeDossier(reference);
        dossierRepository.save(dossier);
        return reference;
    }

    /** Exercice budgétaire du dossier (premier PPM), sinon année courante. */
    private int exerciceDuDossier(Integer idDossier) {
        return ppmRepository.findByIdDossier(idDossier).stream()
                .map(Ppm::getExercice).filter(Objects::nonNull)
                .findFirst().orElse(LocalDate.now().getYear());
    }

    /**
     * ⚠️ Audit 2026-08-27 (lot B) — <strong>liste blanche</strong> des statuts qui appellent une
     * réception. La garde ne refusait que {@code BROUILLON} : tout le reste passait, y compris un
     * dossier dont le PV était signé, clôturé ou retiré.
     *
     * <p>Le secrétariat reçoit un dossier <strong>avant que le PV ne soit signé</strong> : les quatre
     * statuts « avant PV signé » (§3.3 — SOUMIS, PRET_DISPATCH, DISPATCHE, EXAMINE, un retour de
     * circuit restant légitime tant que la Commission n'a pas statué) et les trois états d'
     * <strong>attente</strong> qui appellent précisément un nouveau passage : EN_ATTENTE_PIECES et
     * A_REEXAMINER (lettre de renvoi), EN_ATTENTE_COMPLEMENTS_DEPOT (recevabilité au dépôt). Au-delà
     * — PV_SIGNE, EN_VERIFICATION, EN_ATTENTE_DECISION_PRMP, OBSERVATIONS_LEVEES,
     * DECISION_TRANSMISE_SIGMP, CLOTURE, RETIRE, REMPLACE — le dossier a quitté le secrétariat.</p>
     */
    private static final java.util.Set<String> STATUTS_RECEPTIONNABLES = java.util.Set.of(
            StatutDossier.SOUMIS.name(),
            StatutDossier.PRET_DISPATCH.name(),
            StatutDossier.DISPATCHE.name(),
            StatutDossier.EXAMINE.name(),
            StatutDossier.EN_ATTENTE_PIECES.name(),
            StatutDossier.A_REEXAMINER.name(),
            StatutDossier.EN_ATTENTE_COMPLEMENTS_DEPOT.name());

    /**
     * Précondition de circuit (→ 409) : le dossier doit être dans un statut qui appelle une réception
     * ({@link #STATUTS_RECEPTIONNABLES}). Un dossier {@code BROUILLON} (non soumis) reste refusé, avec
     * son message d'origine ; les statuts <strong>aval</strong> le sont désormais aussi.
     */
    private void exigerDossierReceptionnable(Integer idDossier) {
        String statut = idDossier == null ? null
                : dossierRepository.findById(idDossier).map(Dossier::getStatut).orElse(null);
        if (StatutDossier.BROUILLON.name().equals(statut)) {
            throw new BusinessRuleException(
                    "Réception impossible : le dossier est en brouillon (non soumis).");
        }
        if (statut != null && !STATUTS_RECEPTIONNABLES.contains(statut)) {
            throw new BusinessRuleException(
                    "Réception impossible : le dossier a quitté le secrétariat (statut « " + statut
                            + " ») — une réception ne s'enregistre que tant que le PV n'est pas signé.");
        }
    }

    /**
     * ⚠️ Audit 2026-08-27 (lot B) — un dossier n'a qu'<strong>un</strong> passage initial (§3.4) :
     * {@code dejaReceptionne} existait comme test de confort côté écran, mais rien ne l'imposait au
     * POST — deux enregistrements initiaux du même dossier étaient acceptés (doublons de file, de
     * notification et de compteur).
     */
    private void interdireDoublonPassageInitial(ReceptionDto dto) {
        if (!estPassageInitial(dto) || dto.getIdDossier() == null) {
            return;
        }
        if (repository.existsPassageInitial(dto.getIdDossier())) {
            throw new BusinessRuleException("Ce dossier a déjà été réceptionné (passage initial) ; "
                    + "un nouveau passage doit être un RETOUR (NUM_PASSAGE >= 2) (§3.4).");
        }
    }

    /**
     * ⚠️ Audit 2026-08-27 (lot B) — le {@code PUT} ne rejouait <strong>aucune</strong> précondition
     * d'état : corriger une réception rouvrait la porte que le POST venait de fermer.
     */
    public ReceptionDto update(Integer id, ReceptionDto dto) {
        exigerDossierReceptionnable(dto.getIdDossier());
        exigerLocaliteDossier(dto.getIdDossier());
        validatePassage(dto);
        Reception existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reception introuvable : " + id));
        existing.setIdDossier(dto.getIdDossier());
        existing.setNumPassage(dto.getNumPassage());
        existing.setTypePassage(dto.getTypePassage());
        existing.setImCtrlRecept(dto.getImCtrlRecept());
        existing.setDateReception(ReceptionMapper.toLocalDateTime(dto.getDateReception()));
        existing.setObservation(dto.getObservation());
        existing.setComplet(dto.getComplet());
        existing.setIdReceptionPrec(dto.getIdReceptionPrec());
        Reception saved = repository.save(existing);
        declencherPretDispatch(saved);
        return toDtoComplet(saved);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Reception introuvable : " + id);
        }
        repository.deleteById(id);
    }

    /**
     * Comportement {@code [Auto]} (§2.2) : dès qu'une réception est marquée
     * {@code COMPLET = true}, le dossier passe au statut {@code PRET_DISPATCH}.
     *
     * <p>Lors de la <em>transition</em> vers PRET_DISPATCH, une notification est adressée au
     * Président (toutes localités) et au Chef de commission de la localité du dossier
     * (déduite du contrôleur réceptionnaire).</p>
     */
    private void declencherPretDispatch(Reception reception) {
        if (!Boolean.TRUE.equals(reception.getComplet()) || reception.getIdDossier() == null) {
            return;
        }
        dossierRepository.findById(reception.getIdDossier()).ifPresent(dossier -> {
            String statut = dossier.getStatut();
            // ⚠️ Audit 2026-08-27 (lot B) — la garde ne couvrait que RETIRE et CLOTURE : une réception
            // COMPLET faisait REGRESSER en PRET_DISPATCH un dossier dont le PV était signé, en
            // vérification ou déjà transmis à SIGMP (le circuit repartait à zéro sur un dossier statué).
            // Même liste blanche qu'à l'enregistrement : au-delà, aucune transition n'est posée.
            if (statut != null && !STATUTS_RECEPTIONNABLES.contains(statut)) {
                return;
            }
            boolean dejaPret = StatutDossier.PRET_DISPATCH.name().equals(statut);
            dossier.setStatut(StatutDossier.PRET_DISPATCH.name());
            dossierRepository.save(dossier);
            if (!dejaPret) {
                // Log dans cette branche seulement : hors d'elle, le dossier était DÉJÀ PRET_DISPATCH
                // (re-enregistrement d'une réception complète), ce n'est pas une transition.
                log.info("[CIRCUIT] reception complete dossier={} acteur={} reception={} statut={}",
                        dossier.getIdDossier(), cnm.prs.security.CurrentUser.login().orElse(null),
                        reception.getIdReception(), StatutDossier.PRET_DISPATCH.name());
                notifierPretDispatch(reception, dossier.getIdDossier());
            }
        });
    }

    /** Notifie le Président et le CC de la localité du passage d'un dossier en PRET_DISPATCH (§2.2, §3.4). */
    private void notifierPretDispatch(Reception reception, Integer idDossier) {
        String titre = "Dossier prêt à dispatcher";
        String corps = "Le dossier " + idDossier + " est complet et prêt à être dispatché.";

        for (Controleur president : controleurDirectory.presidents()) {
            notificationService.emettre(idDossier, TypeNotification.PRET_DISPATCH,
                    president.getImControleur(), president.getEmailCont(), titre, corps);
        }
        String localite = reception.getImCtrlRecept() == null ? null
                : controleurRepository.findById(reception.getImCtrlRecept())
                        .map(Controleur::getIdLocalite).orElse(null);
        if (localite != null) {
            for (Controleur cc : controleurDirectory.chefsCommission(localite)) {
                notificationService.emettre(idDossier, TypeNotification.PRET_DISPATCH,
                        cc.getImControleur(), cc.getEmailCont(), titre, corps);
            }
        }
    }

    /**
     * Contrainte de localité (§3.3) : un contrôleur n'agit que sur des dossiers de sa
     * localité (sauf Président/Admin) — y compris à la <strong>première</strong> réception, dès
     * lors que la localité du dossier est connue (cf. {@link #localiteDuDossier}). Si elle est
     * indéterminée, aucune contrainte.
     */
    private void exigerLocaliteDossier(Integer idDossier) {
        Visibilite.exigerLocalite(localiteDuDossier(idDossier));
    }

    /**
     * Localité d'un dossier (§1), par ordre de priorité : sa propre localité
     * ({@code t_dossier.ID_LOCALITE}, estampillée à la soumission), sinon celle de son PPM
     * ({@code Ppm.idLocalite}), sinon celle d'une réception existante (contrôleur réceptionnaire).
     * {@code null} si aucune source ne la fournit.
     */
    private String localiteDuDossier(Integer idDossier) {
        if (idDossier == null) {
            return null;
        }
        String loc = dossierRepository.findById(idDossier)
                .map(Dossier::getIdLocalite).filter(l -> l != null && !l.isBlank()).orElse(null);
        if (loc != null) {
            return loc;
        }
        loc = ppmRepository.findByIdDossier(idDossier).stream()
                .map(Ppm::getIdLocalite).filter(l -> l != null && !l.isBlank()).findFirst().orElse(null);
        if (loc != null) {
            return loc;
        }
        return repository.findLocalitesByDossier(idDossier).stream().findFirst().orElse(null);
    }

    /** Valeur de TYPE_PASSAGE pour la réception initiale (§3.4). */
    private static final String TYPE_PASSAGE_INITIAL = "INITIAL";

    /**
     * Une réception est le <strong>passage initial</strong> si elle porte {@code TYPE_PASSAGE = INITIAL}
     * ou {@code NUM_PASSAGE = 1} (le champ absent valant « premier passage »). Prédicat unique du
     * contrôle de complétude au dépôt et de l'anti-doublon (⚠️ audit lot B).
     */
    private boolean estPassageInitial(ReceptionDto dto) {
        return dto.getNumPassage() == null || dto.getNumPassage() == 1
                || TYPE_PASSAGE_INITIAL.equalsIgnoreCase(dto.getTypePassage());
    }

    /**
     * Une référence de dossier est <strong>structurée</strong> si elle a la forme
     * {@code <seq>/<segment>/<localité>/<année>} produite par {@link ReferenceService} — même test que
     * pour dériver la référence d'une lettre de renvoi. Les références historiques, non structurées,
     * sont (re)générées au premier passage qui les rencontre.
     */
    private boolean referenceStructuree(String refeDossier) {
        return refeDossier != null && refeDossier.matches("\\d+/[^/]+/[^/]+/\\d{4}");
    }

    /**
     * ⚠️ Spec recevabilité au dépôt (2026-08-02) — l'ENREGISTREMENT de la réception initiale est BLOQUÉ
     * tant que toutes les pièces OBLIGATOIRES du type n'ont pas été vérifiées et déclarées CONFORMES par
     * le Secrétaire ({@code t_verification_piece_depot}) → 409 listant les pièces en cause.
     */
    private void exigerControleCompletude(ReceptionDto dto) {
        if (!estPassageInitial(dto)) {
            return;
        }
        List<String> bloquantes = verificationPieceDepotService.obligatoiresNonConformes(dto.getIdDossier());
        if (!bloquantes.isEmpty()) {
            throw new BusinessRuleException("Enregistrement impossible — pièces obligatoires non vérifiées "
                    + "conformes : " + String.join(" ; ", bloquantes) + ".");
        }
    }

    /**
     * Cohérence NUM_PASSAGE / TYPE_PASSAGE (§3.4) : la réception initiale porte
     * {@code NUM_PASSAGE = 1} et {@code TYPE_PASSAGE = INITIAL}, et inversement le type
     * INITIAL n'est autorisé qu'au premier passage. NUM_PASSAGE doit être &gt;= 1.
     */
    private void validatePassage(ReceptionDto dto) {
        Integer num = dto.getNumPassage();
        String type = dto.getTypePassage();

        if (num != null && num < 1) {
            throw new BusinessRuleException("NUM_PASSAGE doit être supérieur ou égal à 1.");
        }
        boolean estInitial = TYPE_PASSAGE_INITIAL.equalsIgnoreCase(type);
        if (num != null && num == 1 && !estInitial) {
            throw new BusinessRuleException(
                    "Au premier passage (NUM_PASSAGE = 1), TYPE_PASSAGE doit être INITIAL (§3.4).");
        }
        if (estInitial && num != null && num != 1) {
            throw new BusinessRuleException(
                    "TYPE_PASSAGE = INITIAL n'est autorisé qu'au premier passage (NUM_PASSAGE = 1) (§3.4).");
        }
    }
}
