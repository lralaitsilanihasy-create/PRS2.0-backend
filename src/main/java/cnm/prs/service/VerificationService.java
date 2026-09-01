package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.VerificationDto;
import cnm.prs.entity.AuditLog;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.Reception;
import cnm.prs.entity.Verification;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutPv;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.VerificationMapper;
import cnm.prs.repository.AuditLogRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ObservationPvRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.repository.VerificationRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link Verification}.
 */
@Service
@Transactional
public class VerificationService {

    /** Journal des transitions du circuit (⚠️ LOT 4 — 2026-08-26), format {@code [CIRCUIT] …}. */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VerificationService.class);

    private final VerificationRepository repository;
    private final ReceptionRepository receptionRepository;
    private final DossierRepository dossierRepository;
    private final PvExamenRepository pvExamenRepository;
    private final ControleurDirectory controleurDirectory;
    private final NotificationService notificationService;
    private final AuditLogRepository auditLogRepository;
    private final PrmpRepository prmpRepository;
    private final ObservationPvRepository observationPvRepository;
    private final cnm.prs.security.PermissionService permissionService;
    /** ⚠️ Chronométrage des délais (2026-09-01) — clôture VERIFICATION et suspension PRMP. */
    private final ChronometrageService chronometrageService;

    public VerificationService(VerificationRepository repository, ReceptionRepository receptionRepository,
            DossierRepository dossierRepository, PvExamenRepository pvExamenRepository,
            ControleurDirectory controleurDirectory, NotificationService notificationService,
            AuditLogRepository auditLogRepository, PrmpRepository prmpRepository,
            ObservationPvRepository observationPvRepository,
            cnm.prs.security.PermissionService permissionService,
            ChronometrageService chronometrageService) {
        this.chronometrageService = chronometrageService;
        this.permissionService = permissionService;
        this.observationPvRepository = observationPvRepository;
        this.repository = repository;
        this.receptionRepository = receptionRepository;
        this.dossierRepository = dossierRepository;
        this.pvExamenRepository = pvExamenRepository;
        this.controleurDirectory = controleurDirectory;
        this.notificationService = notificationService;
        this.auditLogRepository = auditLogRepository;
        this.prmpRepository = prmpRepository;
    }

    @Transactional(readOnly = true)
    public List<VerificationDto> findAll() {
        return Visibilite.filtrer(repository::findAll, repository::findVisiblesParLocalite)
                .stream().map(VerificationMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public VerificationDto findById(Integer id) {
        Verification entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Verification introuvable : " + id));
        Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        return VerificationMapper.toDto(entity);
    }

    public VerificationDto create(VerificationDto dto) {
        Visibilite.exigerLocalite(receptionRepository.findLocaliteById(dto.getIdReception()));
        exigerVerificateur();                  // strict profil VERIFICATEUR (⚠️ règle ajoutée)
        exigerPvSigne(dto.getIdPv());
        exigerCibleVerifiable(dto.getIdPv());  // avis FAVR + dossier non clos (⚠️ règle ajoutée)
        exigerHorsCircuitObservations(dto.getIdPv()); // ⚠️ spec observations FAVR : saisie libre refusée
        Verification entity = VerificationMapper.toEntity(dto);
        entity.setIdVerification(null);                    // ID auto-généré (D6)
        entity.setImCtrlVerif(verificateurAuthentifie());  // identité = JWT, jamais le corps
        entity.setDateVerif(LocalDate.now());              // date serveur
        Verification saved = repository.save(entity);
        traiterApresPassage(saved);
        return VerificationMapper.toDto(saved);
    }

    /**
     * Précondition du circuit (§3.6) : la vérification ne porte que sur un PV au statut
     * {@link StatutPv#SIGNE} (« Travaille uniquement sur PV au statut SIGNE »).
     */
    private void exigerPvSigne(Integer idPv) {
        String statut = idPv == null ? null : pvExamenRepository.findStatutById(idPv).orElse(null);
        if (!StatutPv.SIGNE.name().equals(statut)) {
            throw new BusinessRuleException(
                    "Vérification impossible : le PV doit être au statut SIGNE (§3.6), "
                            + "statut actuel « " + statut + " ».");
        }
    }

    /**
     * Tâche du Contrôleur vérificateur (§3.6) — ⚠️ règle MODIFIÉE (2026-08-14, délégation ascendante) :
     * exercée par le titulaire OU via une paire ACTIVE de {@code t_delegation_profil} (garde centrale
     * {@code PermissionService} — Président et CC via les paires seedées). Périmètre localité inchangé.
     */
    private void exigerVerificateur() {
        if (!permissionService.peutExercer(ProfilUtilisateur.VERIFICATEUR)) {
            throw new AccessDeniedException(
                    "Tâche réservée au Contrôleur vérificateur (titulaire ou délégation active, §3.6).");
        }
    }

    /** Matricule du vérificateur authentifié (principal JWT). */
    private String verificateurAuthentifie() {
        return CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Vérificateur non identifié."));
    }

    /**
     * ⚠️ Règle ajoutée — la vérification ne porte que sur un PV d'avis « favorable avec réserves »
     * (FAVR) dont le dossier n'est pas clôturé ; sinon 409.
     */
    private void exigerCibleVerifiable(Integer idPv) {
        String avis = idPv == null ? null : pvExamenRepository.findIdAvisByPv(idPv).orElse(null);
        if (!"FAVR".equals(avis)) {
            throw new BusinessRuleException(
                    "Vérification réservée aux PV « favorable avec réserves » (FAVR) ; avis actuel « " + avis + " ».");
        }
        Integer idDossier = pvExamenRepository.findIdDossierByPv(idPv).orElse(null);
        String statut = idDossier == null ? null
                : dossierRepository.findById(idDossier).map(Dossier::getStatut).orElse(null);
        // ⚠️ Règle ajoutée — la vérification n'est possible que sur un dossier EN_VERIFICATION :
        // une fois EN_ATTENTE_DECISION_PRMP (obs. non levées) ou CLOTURE, le vérificateur ne peut plus agir.
        if (!StatutDossier.EN_VERIFICATION.name().equals(statut)) {
            throw new BusinessRuleException(
                    "Vérification impossible : le dossier n'est pas en vérification (statut « " + statut + " »).");
        }
    }

    /**
     * ⚠️ Spec « circuit des observations FAVR » (2026-08-02) — dès que le PÉRIMÈTRE FIGÉ des
     * observations du PV existe pour le dossier, la saisie LIBRE d'un passage (texte d'observation
     * rédigé par le client) est REFUSÉE : les observations transmises à la PRMP sont exclusivement
     * celles du PV, statuées une à une via {@code POST /api/observations-pv/passage} (qui crée le
     * passage automatiquement). Rejet côté backend, pas seulement masquage UI.
     */
    private void exigerHorsCircuitObservations(Integer idPv) {
        Integer idDossier = idPv == null ? null : pvExamenRepository.findIdDossierByPv(idPv).orElse(null);
        if (idDossier != null && observationPvRepository.existsByIdDossier(idDossier)) {
            throw new BusinessRuleException(
                    "Saisie libre refusée : le périmètre des observations est figé sur celui du PV — statuez "
                            + "chaque observation (levée / maintenue) via le circuit des observations.");
        }
    }

    /**
     * ⚠️ Spec « circuit des observations FAVR » (2026-08-02) — passage créé PAR LE SYSTÈME depuis les
     * décisions individuelles ({@code ObservationPvService.enregistrerPassage}) : l'observation du
     * passage est le RAPPEL auto-généré des observations maintenues (aucun texte client), puis la
     * transition [Auto] habituelle s'applique (levées → OBSERVATIONS_LEVEES ; sinon →
     * EN_ATTENTE_DECISION_PRMP + notification PRMP + trace d'audit).
     */
    public Verification enregistrerPassageAutomatique(Verification entity) {
        Verification saved = repository.save(entity);
        traiterApresPassage(saved);
        return saved;
    }

    public VerificationDto update(Integer id, VerificationDto dto) {
        Visibilite.exigerLocalite(receptionRepository.findLocaliteById(dto.getIdReception()));
        exigerVerificateur();
        exigerPvSigne(dto.getIdPv());
        exigerCibleVerifiable(dto.getIdPv());
        exigerHorsCircuitObservations(dto.getIdPv()); // ⚠️ spec observations FAVR : saisie libre refusée
        Verification existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Verification introuvable : " + id));
        existing.setIdReception(dto.getIdReception());
        existing.setIdPv(dto.getIdPv());
        existing.setImCtrlVerif(verificateurAuthentifie());  // identité = JWT
        existing.setDateVerif(LocalDate.now());              // date serveur
        existing.setObservation(dto.getObservation());
        existing.setObsLevees(dto.getObsLevees());
        Verification saved = repository.save(existing);
        traiterApresPassage(saved);
        return VerificationMapper.toDto(saved);
    }

    /**
     * Suppression d'un passage de vérification (Administrateur).
     *
     * <p>⚠️ Audit 2026-08-27 (lot B) — la suppression n'avait aucune garde : un passage
     * <strong>décidé</strong> ({@code OBS_LEVEES} renseigné) est une trace du circuit qui a fait
     * bouger le dossier (OBSERVATIONS_LEVEES ou EN_ATTENTE_DECISION_PRMP) et a été notifiée à la
     * PRMP ; l'effacer laisserait un dossier dans un état que plus rien ne justifie. Refusé en 409,
     * comme la navette du PV. Une ligne sans décision (passage inachevé) reste supprimable.</p>
     */
    public void delete(Integer id) {
        Verification existante = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Verification introuvable : " + id));
        if (existante.getObsLevees() != null) {
            throw new BusinessRuleException("Ce passage de vérification porte une décision "
                    + "(observations " + (Boolean.TRUE.equals(existante.getObsLevees()) ? "levées" : "maintenues")
                    + ") : une trace du circuit ne se supprime pas (§3.6).");
        }
        repository.deleteById(id);
    }

    /**
     * Comportement {@code [Auto]} (§3.6) après un passage de vérification, sur un dossier
     * {@code EN_VERIFICATION} (idempotent : les autres statuts ne sont pas réécrits) :
     * <ul>
     *   <li>{@code OBS_LEVEES = true} → ⚠️ règle MODIFIÉE (2026-08-01, spec navette) : dossier
     *       {@code OBSERVATIONS_LEVEES} — le vérificateur doit encore transmettre l'approbation
     *       (+ levée) à SIGMP ; la clôture est posée à l'ARCHIVAGE du PV par l'assistant ;</li>
     *   <li>⚠️ règle ajoutée — {@code OBS_LEVEES = false} → dossier {@code EN_ATTENTE_DECISION_PRMP} :
     *       l'observation est transmise à la PRMP ({@code OBSERVATION_VERIFICATION}) et l'événement est
     *       tracé dans {@code t_audit_log}. Le vérificateur ne peut plus agir tant que la PRMP n'a pas statué.</li>
     * </ul>
     */
    private void traiterApresPassage(Verification verification) {
        if (verification.getIdReception() == null) {
            return;
        }
        Integer idDossier = receptionRepository.findById(verification.getIdReception())
                .map(Reception::getIdDossier).orElse(null);
        if (idDossier == null) {
            return;
        }
        dossierRepository.findById(idDossier).ifPresent(dossier -> {
            if (!StatutDossier.EN_VERIFICATION.name().equals(dossier.getStatut())) {
                return;
            }
            // ⚠️ Chronométrage (2026-09-01) — l'acte de vérification clôt l'étape VERIFICATION, quel
            // que soit son sens. C'est la raison pour laquelle la vérification et la transmission SIGMP
            // sont DEUX étapes : entre elles peut s'ouvrir une attente PRMP, qui ne doit être imputée
            // à personne à la CNM.
            chronometrageService.cloturer(idDossier, EtapeCircuit.VERIFICATION);
            if (Boolean.TRUE.equals(verification.getObsLevees())) {
                dossier.setStatut(StatutDossier.OBSERVATIONS_LEVEES.name());
                dossierRepository.save(dossier);
                log.info("[CIRCUIT] verification observations levees dossier={} acteur={} verification={} statut={}",
                        idDossier, CurrentUser.login().orElse(null), verification.getIdVerification(),
                        StatutDossier.OBSERVATIONS_LEVEES.name());
            } else {
                dossier.setStatut(StatutDossier.EN_ATTENTE_DECISION_PRMP.name());
                dossierRepository.save(dossier);
                // ⚠️ Chronométrage (2026-09-01) — la balle passe à la PRMP : le compteur net CNM se
                // suspend ici et reprendra à la resoumission.
                chronometrageService.entrerEnAttentePrmp(idDossier, StatutDossier.EN_ATTENTE_DECISION_PRMP);
                log.info("[CIRCUIT] verification observations non levees dossier={} acteur={} verification={} statut={}",
                        idDossier, CurrentUser.login().orElse(null), verification.getIdVerification(),
                        StatutDossier.EN_ATTENTE_DECISION_PRMP.name());
                notifierObservationPrmp(dossier, verification);
                tracerObservationNonLevee(dossier, verification);
            }
        });
    }

    /**
     * ⚠️ Règle ajoutée — transmet l'observation non levée à la PRMP du dossier (via PV → PPM → PRMP) :
     * référence dossier, vérificateur, texte de l'observation, date.
     */
    private void notifierObservationPrmp(Dossier dossier, Verification v) {
        String ref = dossier.getRefeDossier() != null ? dossier.getRefeDossier() : ("n° " + dossier.getIdDossier());
        String titre = "Observations de vérification à traiter";
        String corps = "Dossier " + ref + " — le vérificateur " + v.getImCtrlVerif()
                + " a relevé des observations non levées le " + v.getDateVerif()
                + " : « " + (v.getObservation() == null ? "" : v.getObservation())
                + " ». Veuillez rectifier le dossier puis décider de la suite.";
        for (String idPrmp : pvExamenRepository.findIdPrmpByPv(v.getIdPv())) {
            String email = prmpRepository.findById(idPrmp).map(Prmp::getEmailPrmp).orElse(null);
            notificationService.emettrePrmp(TypeNotification.OBSERVATION_VERIFICATION, idPrmp, email,
                    dossier.getIdDossier(), TypeObjet.DOSSIER, dossier.getIdDossier(), titre, corps);
        }
    }

    // (⚠️ 2026-08-01, spec navette) notifierClotureAssistant / notifierClotureEligible : déplacés à
    // l'ARCHIVAGE du PV (PvExamenService.archiver) — la clôture n'est plus posée par la vérification.

    /** ⚠️ Règle ajoutée — trace l'observation non levée dans {@code t_audit_log} (D1, option a). */
    private void tracerObservationNonLevee(Dossier dossier, Verification v) {
        AuditLog log = new AuditLog();
        log.setIdLog(auditLogRepository.nextIdAuditLog());   // PK serveur (sequence)
        log.setDateAction(LocalDateTime.now());
        log.setImActeur(v.getImCtrlVerif());
        log.setNomTable("t_verification");
        log.setIdEnregistrement(v.getIdVerification() == null ? null : String.valueOf(v.getIdVerification()));
        log.setTypeAction("UPDATE");                       // verbe court — libellé complet en CHAMP_MODIFIE
        log.setChampModifie("OBSERVATION_NON_LEVEE");
        log.setNouvelleValeur(v.getObservation());
        auditLogRepository.save(log);
    }

}
