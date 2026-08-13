package cnm.prs.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.PvActionRequest;
import cnm.prs.dto.PvExamenDto;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.PvNavette;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.RoleSignataire;
import cnm.prs.enums.SensNavette;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutPv;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.PvExamenMapper;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ExamenDetailRepository;
import cnm.prs.repository.ExamenPieceRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.PvNavetteRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link PvExamen}.
 *
 * <p>Outre le CRUD, ce service porte le <strong>cycle de vie du projet de PV</strong>
 * (circuit de contrôle §2, §3.2, §3.5). Le statut ({@code STATUT_PV}) et les dates de
 * workflow ne sont modifiables que via les transitions dédiées
 * ({@link #soumettre}, {@link #retourner}, {@link #accepter}, {@link #signer}) —
 * jamais par le {@code PUT} générique.</p>
 */
@Service
@Transactional
public class PvExamenService {

    private final PvExamenRepository repository;
    private final PvNavetteRepository navetteRepository;
    private final PrmpRepository prmpRepository;
    private final NotificationService notificationService;
    private final ControleurDirectory controleurDirectory;
    private final DossierRepository dossierRepository;
    private final ControleurRepository controleurRepository;
    private final PvDocumentService pvDocumentService;
    private final ExamenDetailRepository examenDetailRepository;
    private final ExamenPieceRepository examenPieceRepository;
    private final ObservationPvService observationPvService;

    public PvExamenService(PvExamenRepository repository, PvNavetteRepository navetteRepository,
            PrmpRepository prmpRepository, NotificationService notificationService,
            ControleurDirectory controleurDirectory, DossierRepository dossierRepository,
            ControleurRepository controleurRepository, PvDocumentService pvDocumentService,
            ExamenDetailRepository examenDetailRepository, ExamenPieceRepository examenPieceRepository,
            ObservationPvService observationPvService) {
        this.observationPvService = observationPvService;
        this.repository = repository;
        this.navetteRepository = navetteRepository;
        this.prmpRepository = prmpRepository;
        this.notificationService = notificationService;
        this.controleurDirectory = controleurDirectory;
        this.dossierRepository = dossierRepository;
        this.controleurRepository = controleurRepository;
        this.pvDocumentService = pvDocumentService;
        this.examenDetailRepository = examenDetailRepository;
        this.examenPieceRepository = examenPieceRepository;
    }

    /** Projets de PV : tous les PV NON signés (les signés sont exposés par {@link #definitifs()}). */
    @Transactional(readOnly = true)
    public List<PvExamenDto> projets() {
        return Visibilite.filtrer(repository::findProjets, repository::findProjetsParLocalite)
                .stream().map(this::toDtoLecture).toList();
    }

    /**
     * PV définitifs : uniquement les PV signés ({@code statutPv = SIGNE}).
     *
     * <p>⚠️ 2026-08-02 — la <strong>PRMP</strong> (exclue du périmètre localité) voit les PV signés de
     * <strong>ses dossiers</strong> (via PPM, même périmètre que « Mes lettres de renvoi ») : elle en a
     * besoin pour rectifier selon les observations du PV. Les projets restent internes (invisibles).</p>
     */
    @Transactional(readOnly = true)
    public List<PvExamenDto> definitifs() {
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            return idPrmp == null ? List.of()
                    : repository.findDefinitifsPourPrmp(idPrmp).stream().map(this::toDtoLecture).toList();
        }
        return Visibilite.filtrer(repository::findDefinitifs, repository::findDefinitifsParLocalite)
                .stream().map(this::toDtoLecture).toList();
    }

    @Transactional(readOnly = true)
    public PvExamenDto findById(Integer id) {
        PvExamen entity = load(id);
        controlerAcces(id);
        return toDtoLecture(entity);
    }

    /**
     * Accès de lecture à un PV : périmètre de localité habituel, <strong>ou</strong> PRMP propriétaire
     * du dossier pour un PV <strong>SIGNÉ</strong> (⚠️ 2026-08-02 — sinon la PRMP serait hors périmètre
     * → 403, alors qu'elle doit consulter le PV définitif pour rectifier).
     */
    private void controlerAcces(Integer idPv) {
        if (Visibilite.estPrmp()) {
            boolean autorise = CurrentUser.ref().filter(s -> !s.isBlank())
                    .map(idPrmp -> repository.estSignePourPrmp(idPv, idPrmp)).orElse(false);
            if (!autorise) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "PV hors de votre périmètre (seuls les PV signés de vos dossiers sont consultables).");
            }
            return;
        }
        Visibilite.controler(loc -> repository.existsDansLocalite(idPv, loc));
    }

    /** Mappe un PV en DTO de lecture : nom du secrétaire + {@code documentDisponible} (chemin stocké ou PV éligible). */
    private PvExamenDto toDtoLecture(PvExamen entity) {
        PvExamenDto dto = peuplerNomSecretaire(PvExamenMapper.toDto(entity));
        dto.setDocumentDisponible(pvDocumentService.documentDisponible(entity));
        return dto;
    }

    /**
     * Document PDF du PV (téléchargement). Accès : périmètre de localité habituel (même contrôle que
     * {@link #findById}). Lit le fichier sur le FSX ({@code CHEMIN_DOCUMENT}). Si le chemin est absent
     * (PV signé <strong>avant</strong> ce correctif) ou le fichier introuvable, tente une
     * <strong>régénération paresseuse</strong> (si le PV est éligible) — ce qui sert aussi de migration des
     * anciens PV signés sans document. 404 seulement si le PV n'est pas éligible à la génération.
     */
    @Transactional
    public byte[] telechargerDocument(Integer id) {
        PvExamen pv = load(id);
        controlerAcces(id); // périmètre localité, OU PRMP propriétaire d'un PV SIGNÉ (2026-08-02)
        byte[] pdf = lireFsx(pv.getCheminDocument());
        if (pdf == null) {
            String chemin = pvDocumentService.genererSiEligible(pv).orElse(null);
            if (chemin != null) {
                pv.setCheminDocument(chemin);
                repository.save(pv);
                pdf = lireFsx(chemin);
            }
        }
        if (pdf == null) {
            throw new ResourceNotFoundException("Aucun document pour le PV : " + id);
        }
        return pdf;
    }

    /**
     * ⚠️ 2026-08-05 (versionnement des PPM) — PDF d'un PV pour <strong>composition serveur</strong> :
     * même lecture, et même régénération paresseuse, que {@link #telechargerDocument}, mais SANS contrôle
     * d'accès — l'appelant agit pour le compte de la PRMP propriétaire, à qui l'accès à son PV signé est
     * ouvert — et sans 404 : renvoie {@code null} si le PV n'a pas de document. Sert à joindre le PV du
     * dossier prédécesseur au dossier de mise à jour.
     */
    @Transactional
    public byte[] documentPourHistorique(Integer idPv) {
        PvExamen pv = repository.findById(idPv).orElse(null);
        if (pv == null) {
            return null;
        }
        byte[] pdf = lireFsx(pv.getCheminDocument());
        if (pdf == null) {
            String chemin = pvDocumentService.genererSiEligible(pv).orElse(null);
            if (chemin != null) {
                pv.setCheminDocument(chemin);
                repository.save(pv);
                pdf = lireFsx(chemin);
            }
        }
        return pdf;
    }

    /** Lit le PDF sur le FSX, ou {@code null} si chemin vide / fichier absent / illisible. */
    private byte[] lireFsx(String chemin) {
        if (chemin == null || chemin.isBlank()) {
            return null;
        }
        try {
            Path p = Path.of(chemin);
            return Files.exists(p) ? Files.readAllBytes(p) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Création d'un projet de PV. Un nouveau projet démarre toujours en
     * {@link StatutPv#BROUILLON} (§3.5), sans navette ni signature — quel que soit le
     * statut transmis : impossible de créer un PV directement accepté ou signé.
     */
    /**
     * ⚠️ Règle ajoutée — crée le Projet de PV d'un examen à sa soumission (PK {@code idPv} allouée
     * serveur, max+1) avec l'avis choisi et le Vérificateur désigné Secrétaire de séance
     * ({@code idSecretaireSeance}, déjà validé). Réutilise {@link #create(PvExamenDto)}.
     */
    public PvExamenDto creerProjet(Integer idExamen, String idAvis, String idSecretaireSeance) {
        PvExamenDto dto = new PvExamenDto();
        dto.setIdExamen(idExamen);
        dto.setIdAvis(idAvis);
        dto.setIdSecretaireSeance(idSecretaireSeance);
        dto.setIdPv(repository.findMaxId() + 1);
        return create(dto);
    }

    public PvExamenDto create(PvExamenDto dto) {
        PvExamen entity = PvExamenMapper.toEntity(dto);
        // ⚠️ Règle ajoutée — l'imCtrlMembre est l'attributaire de l'examen (dispatch), jamais le corps.
        entity.setImCtrlMembre(attributaireDeLExamen(dto.getIdExamen()));
        entity.setStatutPv(StatutPv.BROUILLON.name());
        entity.setNbNavettes(0);
        entity.setDateSoumissionInitiale(null);
        entity.setDateAcceptation(null);
        entity.setDateSignatureMembre(null);
        entity.setDateSignaturePresident(null);
        entity.setDateSignatureCc(null);
        entity.setDatePv(null);
        // ⚠️ Règle ajoutée — refePv dérivée du dossier (refeDossier au format .../YYYY), unique.
        String refePv = genererRefePv(dto.getIdExamen());
        if (refePv != null && repository.existsByRefePv(refePv)) {
            throw new BusinessRuleException(
                    "Un PV existe déjà pour ce dossier (référence " + refePv + ").");
        }
        entity.setRefePv(refePv);
        PvExamen saved = repository.save(entity);
        return peuplerNomSecretaire(PvExamenMapper.toDto(saved));
    }

    /** Renseigne {@code nomSecretaireSeance} (« prénoms nom ») depuis {@code tr_controleur} (lecture seule). */
    private PvExamenDto peuplerNomSecretaire(PvExamenDto dto) {
        if (dto != null && dto.getIdSecretaireSeance() != null) {
            controleurRepository.findById(dto.getIdSecretaireSeance()).ifPresent(c -> {
                String n = ((c.getPrenomsCont() == null ? "" : c.getPrenomsCont()) + " "
                        + (c.getNomCont() == null ? "" : c.getNomCont())).trim();
                dto.setNomSecretaireSeance(n.isBlank() ? null : n);
            });
        }
        return dto;
    }

    /**
     * ⚠️ Règle ajoutée — dérive la référence du PV depuis {@code refeDossier} du dossier rattaché :
     * insère {@code /PV} avant l'année. Uniquement si refeDossier est au format {@code .../YYYY}
     * (sinon {@code null} — les anciennes références ne sont pas dérivables).
     */
    private String genererRefePv(Integer idExamen) {
        String refe = repository.findRefeDossierByExamen(idExamen)
                .filter(s -> s != null && s.matches(".*/\\d{4}$")).orElse(null);
        return refe == null ? null : refe.replaceFirst("/(\\d{4})$", "/PV/$1");
    }

    /**
     * Mise à jour du contenu éditable du projet (avis, synthèse, signataires désignés,
     * référence). Ne touche <strong>pas</strong> au statut, au nombre de navettes ni aux
     * dates de workflow : ces champs sont pilotés exclusivement par les transitions.
     *
     * <p>Le projet n'est modifiable que tant qu'il n'a pas été soumis, c'est-à-dire aux
     * statuts {@link StatutPv#BROUILLON} ou {@link StatutPv#EN_RECTIFICATION} (§3.5).</p>
     */
    public PvExamenDto update(Integer id, PvExamenDto dto) {
        PvExamen existing = load(id);
        requireStatut(existing, StatutPv.BROUILLON, StatutPv.EN_RECTIFICATION);
        existing.setIdExamen(dto.getIdExamen());
        existing.setIdAvis(dto.getIdAvis());
        existing.setImCtrlPresident(dto.getImCtrlPresident());
        existing.setImCtrlCc(dto.getImCtrlCc());
        // ⚠️ Règle ajoutée — imCtrlMembre re-dérivé de l'attribution (dispatch), jamais le corps.
        existing.setImCtrlMembre(attributaireDeLExamen(dto.getIdExamen()));
        existing.setSyntheseObservations(dto.getSyntheseObservations());
        existing.setReferencePv(dto.getReferencePv());
        return PvExamenMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("PvExamen introuvable : " + id);
        }
        Integer idDossier = repository.findIdDossierByPv(id).orElse(null);
        repository.deleteById(id);
        realignerDossierSansPvSigne(idDossier);
    }

    /**
     * ⚠️ Garde-fou de cohérence dossier↔PV (règle ajoutée) : un dossier ne doit pas rester
     * {@link StatutDossier#EN_VERIFICATION} si son PV signé n'existe plus (supprimé / redescendu en projet).
     * Si, après retrait d'un PV, le dossier n'a <strong>plus aucun PV {@code SIGNE}</strong> et se trouve encore
     * {@code EN_VERIFICATION}, on le ramène à {@link StatutDossier#EXAMINE} (état « examiné, en attente de PV »),
     * ce qui débloque l'écran de vérification et permet de reproduire un PV. Les autres statuts sont laissés tels quels.
     */
    private void realignerDossierSansPvSigne(Integer idDossier) {
        if (idDossier == null || repository.countSignesParDossier(idDossier) > 0) {
            return;
        }
        dossierRepository.findById(idDossier).ifPresent(d -> {
            if (StatutDossier.EN_VERIFICATION.name().equals(d.getStatut())) {
                d.setStatut(StatutDossier.EXAMINE.name());
                dossierRepository.save(d);
            }
        });
    }

    /** Matricule du Membre attributaire de l'examen (dispatch) ; refuse si l'examen n'a pas d'attributaire. */
    private String attributaireDeLExamen(Integer idExamen) {
        return repository.findImCtrlMembreByExamen(idExamen)
                .filter(im -> im != null && !im.isBlank())
                .orElseThrow(() -> new BusinessRuleException(
                        "PV impossible : l'examen " + idExamen + " n'a pas de Membre attributaire (dispatch)."));
    }

    // ----------------------------------------------------------------------
    // Transitions du circuit de contrôle (workflow)
    // ----------------------------------------------------------------------

    /**
     * Soumission du projet par le Membre (§3.5) : BROUILLON | EN_RECTIFICATION → PROJET_SOUMIS.
     * Insère une navette SENS = SOUMISSION et incrémente NUM_NAVETTE.
     */
    public PvExamenDto soumettre(Integer id, PvActionRequest req) {
        PvExamen pv = load(id);
        requireStatut(pv, StatutPv.BROUILLON, StatutPv.EN_RECTIFICATION);

        if (pv.getDateSoumissionInitiale() == null) {
            pv.setDateSoumissionInitiale(LocalDate.now());
        }
        pv.setStatutPv(StatutPv.PROJET_SOUMIS.name());
        ajouterNavette(pv, SensNavette.SOUMISSION, req.imActeur(), req.commentaire());
        PvExamen saved = repository.save(pv);
        // ⚠️ Règle ajoutée (2026-08-02, réexamen après lettre de renvoi) — la re-soumission du projet
        // de PV CLÔT LE RÉEXAMEN : le dossier A_REEXAMINER redevient EXAMINE (même transaction), la
        // navette reprend son circuit normal (acceptation P/CC → signature).
        Integer idDossier = repository.findIdDossierByPv(saved.getIdPv()).orElse(null);
        if (idDossier != null) {
            dossierRepository.findById(idDossier).ifPresent(d -> {
                if (StatutDossier.A_REEXAMINER.name().equals(d.getStatut())) {
                    d.setStatut(StatutDossier.EXAMINE.name());
                    dossierRepository.save(d);
                }
            });
        }
        // [Auto] Le CC et le Président de la localité sont notifiés qu'un projet de PV attend validation.
        notifierPvAValider(saved);
        return PvExamenMapper.toDto(saved);
    }

    /** ⚠️ Règle ajoutée (2026-08-01) — le Secrétaire de séance doit être un Vérificateur de la localité du dossier. */
    /**
     * ⚠️ Règle ajoutée (2026-08-01) — COHÉRENCE AVIS ↔ OBSERVATIONS de l'examen (points de contrôle
     * ET pièces jointes) à la clôture de navette : s'il existe au moins une observation, l'avis
     * « Favorable » (sans réserve, {@code FAV}) est refusé ; s'il n'en existe aucune, l'avis
     * « Favorable avec réserves » ({@code FAVR}) est refusé. {@code DEF}/{@code NSP} restent libres
     * (appréciation souveraine de la Commission).
     */
    private void validerCoherenceAvis(Integer idExamen, String idAvis) {
        long observations = examenDetailRepository.findByIdExamen(idExamen).stream()
                .filter(d -> Boolean.FALSE.equals(d.getConforme())).count()
                + examenPieceRepository.findByIdExamen(idExamen).stream()
                .filter(p -> Boolean.FALSE.equals(p.getConforme())).count();
        if (observations > 0 && "FAV".equals(idAvis)) {
            throw new BusinessRuleException("L'examen comporte " + observations
                    + " observation(s) (points de contrôle ou pièces jointes) : l'avis « Favorable » (sans réserve)"
                    + " est incohérent — choisissez « Favorable avec réserves » ou « Défavorable ».");
        }
        if (observations == 0 && "FAVR".equals(idAvis)) {
            throw new BusinessRuleException(
                    "Aucune observation relevée à l'examen : l'avis « Favorable avec réserves » est incohérent"
                            + " — choisissez « Favorable ».");
        }
    }

    private String validerSecretaireSeance(Integer idPv, String idSecretaire) {
        String localite = repository.findLocaliteByPv(idPv).orElse(null);
        boolean valide = localite != null && controleurDirectory.verificateurs(localite).stream()
                .anyMatch(c -> idSecretaire.trim().equals(c.getImControleur()));
        if (!valide) {
            throw new BusinessRuleException(
                    "Le Secrétaire de séance doit être un Vérificateur de la localité du dossier.");
        }
        return idSecretaire.trim();
    }

    /** [Auto] Notifie le CC et le Président de la localité du dossier ({@code PV_A_VALIDER}). */
    private void notifierPvAValider(PvExamen pv) {
        String localite = repository.findLocaliteByPv(pv.getIdPv()).orElse(null);
        Integer idDossier = repository.findIdDossierByPv(pv.getIdPv()).orElse(null);
        String reference = pv.getReferencePv() != null ? pv.getReferencePv() : ("n° " + pv.getIdPv());
        String titre = "Projet de PV à valider";
        String corps = "Le projet de PV " + reference + " a été soumis et attend votre validation.";

        List<Controleur> destinataires = new ArrayList<>(controleurDirectory.presidents());
        if (localite != null) {
            destinataires.addAll(controleurDirectory.chefsCommission(localite));
        }
        for (Controleur c : destinataires) {
            notificationService.emettreControleur(TypeNotification.PV_A_VALIDER, c.getImControleur(),
                    c.getEmailCont(), pv.getIdPv(), TypeObjet.PV, idDossier, titre, corps);
        }
    }

    /** [Auto] Notifie le Membre auteur du PV ({@code imCtrlMembre}), objet PV. */
    private void notifierPvAuteur(PvExamen pv, TypeNotification type, String titre, String corps) {
        String imAuteur = pv.getImCtrlMembre();
        if (imAuteur == null || imAuteur.isBlank()) {
            return;
        }
        Integer idDossier = repository.findIdDossierByPv(pv.getIdPv()).orElse(null);
        notificationService.emettreControleur(type, imAuteur, null, pv.getIdPv(), TypeObjet.PV, idDossier, titre, corps);
    }

    private String referencePv(PvExamen pv) {
        return pv.getReferencePv() != null ? pv.getReferencePv() : ("n° " + pv.getIdPv());
    }

    /** Code d'avis « favorable avec réserves » (tr_avis) : seul cas ouvrant la vérification. */
    private static final String AVIS_FAVORABLE_RESERVE = "FAVR";

    /**
     * [Auto] ⚠️ Règle ajoutée — à la signature du PV, le circuit se branche selon l'avis
     * ({@code t_pv_examen.ID_AVIS}) :
     * <ul>
     *   <li>{@code FAVR} (favorable avec réserves) → dossier {@link StatutDossier#EN_VERIFICATION}
     *       (vérification ouverte) ; le vérificateur est notifié « à vérifier ».</li>
     *   <li>{@code FAV} / {@code DEF} / {@code NSP} → dossier {@link StatutDossier#CLOTURE} (auto) ;
     *       le vérificateur est notifié « pour information » (lecture seule).</li>
     * </ul>
     * Idempotent : on ne réécrit le statut que si le dossier est bien {@code EXAMINE}. Dans tous les
     * cas, le PV est transmis à la PRMP ({@link #notifierPvSigne}).
     */
    /**
     * ⚠️ Règle MODIFIÉE (2026-08-01, spec navette) — à la signature, TOUS les avis passent par le
     * VÉRIFICATEUR (dossier {@code EN_VERIFICATION}) : FAVR → boucle de rectification (cas 2) ;
     * FAV/DEF/NSP → transmission du sens de la décision à SIGMP puis archivage (cas 1). La clôture
     * n'intervient plus ici : elle est posée à l'ARCHIVAGE du PV par l'Assistant contrôleur.
     */
    private void brancherSelonAvis(PvExamen pv) {
        boolean reserve = AVIS_FAVORABLE_RESERVE.equals(repository.findIdAvisByPv(pv.getIdPv()).orElse(null));
        Integer idDossier = repository.findIdDossierByPv(pv.getIdPv()).orElse(null);
        if (idDossier != null) {
            dossierRepository.findById(idDossier).ifPresent(d -> {
                if (StatutDossier.EXAMINE.name().equals(d.getStatut())) {
                    d.setStatut(StatutDossier.EN_VERIFICATION.name());
                    dossierRepository.save(d);
                }
            });
        }
        if (reserve) {
            // ⚠️ Spec « circuit des observations FAVR » (2026-08-02) — le PÉRIMÈTRE des observations
            // transmises à la PRMP est FIGÉ dès l'émission du PV : snapshot des observations de
            // l'examen arrêtées au PV (première transmission = ces observations, rien d'autre).
            observationPvService.genererPourPv(pv);
        }
        notifierPvSigne(pv);                            // PRMP (transmission systématique)
        notifierVerificateur(pv, reserve, idDossier);
    }

    /**
     * Notifie le(s) vérificateur(s) de la localité du dossier : {@code PV_A_VERIFIER} si l'avis est
     * favorable avec réserves (boucle de rectification), sinon {@code DECISION_A_TRANSMETTRE}
     * (⚠️ spec navette 2026-08-01 : le vérificateur transmet le sens de la décision à SIGMP).
     */
    private void notifierVerificateur(PvExamen pv, boolean reserve, Integer idDossier) {
        String localite = repository.findLocaliteByPv(pv.getIdPv()).orElse(null);
        if (localite == null) {
            return;
        }
        String reference = referencePv(pv);
        TypeNotification type = reserve ? TypeNotification.PV_A_VERIFIER : TypeNotification.DECISION_A_TRANSMETTRE;
        String titre = reserve ? "PV à vérifier" : "Décision à transmettre à SIGMP";
        String corps = reserve
                ? "Le PV " + reference + " (favorable avec réserves) est à vérifier."
                : "Le PV " + reference + " est signé : transmettez le sens de la décision à SIGMP, puis le PV à l'assistant pour archivage.";
        for (Controleur v : controleurDirectory.verificateurs(localite)) {
            notificationService.emettre(idDossier, type, v.getImControleur(), v.getEmailCont(), titre, corps);
        }
    }

    /**
     * Retour du projet pour correction par le Président / CC (§3.2) :
     * PROJET_SOUMIS → EN_RECTIFICATION. Commentaire de rectification obligatoire.
     * Insère une navette SENS = RETOUR_RECTIF.
     */
    public PvExamenDto retourner(Integer id, PvActionRequest req) {
        PvExamen pv = load(id);
        requireStatut(pv, StatutPv.PROJET_SOUMIS);
        if (req.commentaire() == null || req.commentaire().isBlank()) {
            throw new BusinessRuleException("Le commentaire de rectification est obligatoire (§3.2).");
        }
        pv.setStatutPv(StatutPv.EN_RECTIFICATION.name());
        ajouterNavette(pv, SensNavette.RETOUR_RECTIF, req.imActeur(), req.commentaire());
        PvExamen saved = repository.save(pv);
        // [Auto] Le Membre auteur est notifié du retour pour rectification, avec le commentaire.
        notifierPvAuteur(saved, TypeNotification.PV_A_RECTIFIER, "Projet de PV à rectifier",
                "Le projet de PV " + referencePv(saved) + " a été retourné pour rectification : " + req.commentaire());
        return PvExamenMapper.toDto(saved);
    }

    /**
     * Acceptation du projet par le Président / CC (§3.2) :
     * PROJET_SOUMIS → PROJET_ACCEPTE (le PV devient signable).
     * Insère une navette SENS = ACCEPTATION.
     */
    public PvExamenDto accepter(Integer id, PvActionRequest req) {
        PvExamen pv = load(id);
        requireStatut(pv, StatutPv.PROJET_SOUMIS);

        // ⚠️ Règle ajoutée (2026-08-01) — la CLÔTURE DE NAVETTE (acceptation, Président/CC) pose
        // l'AVIS GLOBAL et le SECRÉTAIRE DE SÉANCE : l'examen soumis par le Membre ne porte que les
        // résultats des points de contrôle et la synthèse des observations.
        if (req.idAvis() == null || req.idAvis().isBlank()) {
            throw new BusinessRuleException(
                    "L'avis global est obligatoire pour clore la navette (acceptation du projet de PV).");
        }
        validerCoherenceAvis(pv.getIdExamen(), req.idAvis().trim());
        pv.setIdAvis(req.idAvis().trim());
        if (req.idSecretaireSeance() != null && !req.idSecretaireSeance().isBlank()) {
            pv.setIdSecretaireSeance(validerSecretaireSeance(id, req.idSecretaireSeance()));
        } else if (pv.getIdSecretaireSeance() == null || pv.getIdSecretaireSeance().isBlank()) {
            throw new BusinessRuleException(
                    "Le Secrétaire de séance (Vérificateur de la localité du dossier) est obligatoire pour clore la navette.");
        }

        pv.setStatutPv(StatutPv.PROJET_ACCEPTE.name());
        pv.setDateAcceptation(LocalDate.now());
        ajouterNavette(pv, SensNavette.ACCEPTATION, req.imActeur(), req.commentaire());
        PvExamen saved = repository.save(pv);
        // [Auto] Le Membre auteur est notifié de l'acceptation du projet de PV.
        notifierPvAuteur(saved, TypeNotification.PV_ACCEPTE, "Projet de PV accepté",
                "Le projet de PV " + referencePv(saved) + " a été accepté.");
        return PvExamenMapper.toDto(saved);
    }

    /**
     * Co-signature du PV accepté (§3.2, §3.3, §3.5). N'est possible qu'au statut
     * PROJET_ACCEPTE. Renseigne la date de signature du rôle indiqué, puis bascule en
     * SIGNE dès que le Membre <em>et</em> (le Président <em>ou</em> le CC) ont signé.
     */
    public PvExamenDto signer(Integer id, PvActionRequest req) {
        // ⚠️ Anti-doublon (2026-08-02) — chargement VERROUILLÉ : la génération du PDF rend la signature
        // longue ; des clics répétés lisaient tous PROJET_ACCEPTE et notifiaient plusieurs fois. Le
        // verrou sérialise : la 2ᵉ requête attend, voit SIGNE (ou la date posée) et reçoit 409.
        PvExamen pv = repository.findByIdVerrouille(id)
                .orElseThrow(() -> new ResourceNotFoundException("PvExamen introuvable : " + id));
        requireStatut(pv, StatutPv.PROJET_ACCEPTE);
        // Garde-fou (⚠️ règle ajoutée 2026-08-01) : pas de signature sans avis global (posé à l'acceptation).
        if (pv.getIdAvis() == null || pv.getIdAvis().isBlank()) {
            throw new BusinessRuleException(
                    "Avis global manquant : clôturez la navette (acceptation avec avis) avant la signature.");
        }

        RoleSignataire role = parseRole(req.role());
        // Le signataire est l'utilisateur authentifié (jamais req.imActeur(), falsifiable).
        String signataire = CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Signataire non identifié."));
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        LocalDate today = LocalDate.now();
        switch (role) {
            case MEMBRE -> {
                // Signature Membre réservée au Membre attributaire du PV (§3.5, pas de délégation).
                if (!signataire.equals(pv.getImCtrlMembre())) {
                    throw new AccessDeniedException(
                            "La signature Membre est réservée au Membre attributaire du PV (§3.5).");
                }
                exigerPasEncoreSigne(pv.getDateSignatureMembre(), "Membre");
                pv.setDateSignatureMembre(today);
                pv.setImCtrlMembre(signataire);
            }
            case PRESIDENT -> {
                if (profil != ProfilUtilisateur.PRESIDENT) {
                    throw new AccessDeniedException("La signature Président est réservée à un Président (§3.2).");
                }
                exigerCoSignataireDistinct(signataire, pv);
                exigerPasEncoreSigne(pv.getDateSignaturePresident(), "Président");
                pv.setDateSignaturePresident(today);
                pv.setImCtrlPresident(signataire);
            }
            case CC -> {
                if (profil != ProfilUtilisateur.CHEF_COMMISSION) {
                    throw new AccessDeniedException("La signature CC est réservée à un Chef de commission (§3.3).");
                }
                exigerCcDeLaLocalite(pv);
                exigerCoSignataireDistinct(signataire, pv);
                exigerPasEncoreSigne(pv.getDateSignatureCc(), "Chef de commission");
                pv.setDateSignatureCc(today);
                pv.setImCtrlCc(signataire);
            }
        }

        boolean membreSigne = pv.getDateSignatureMembre() != null;
        boolean coSigne = pv.getDateSignaturePresident() != null || pv.getDateSignatureCc() != null;
        if (membreSigne && coSigne) {
            pv.setStatutPv(StatutPv.SIGNE.name());
            pv.setDatePv(today);
            // ⚠️ Règle ajoutée — à la signature finale, génère et stocke le PDF du PV (présents complets)
            // si éligible (avis FAVR + localité centrale + lignes de marché en appel d'offres ouvert).
            pvDocumentService.genererSiEligible(pv).ifPresent(pv::setCheminDocument);
            PvExamenDto dto = PvExamenMapper.toDto(repository.save(pv));
            // [Auto] ⚠️ Règle ajoutée — branchement du circuit selon l'avis du PV.
            brancherSelonAvis(pv);
            return dto;
        }
        return PvExamenMapper.toDto(repository.save(pv));
    }

    /**
     * ⚠️ Spec navette (2026-08-01) — ARCHIVAGE du PV par l'Assistant contrôleur (circuit unique PV/lettres) :
     * PV {@code SIGNE} + dossier {@code DECISION_TRANSMISE_SIGMP} (la décision doit avoir été transmise à
     * SIGMP par le vérificateur). Pose la date/l'auteur d'archivage, CLÔT le dossier et émet l'alerte
     * {@code CLOTURE_ELIGIBLE} (chargés de publication) — déplacée ici depuis la vérification.
     */
    public PvExamenDto archiver(Integer id) {
        PvExamen pv = load(id);
        if (!StatutPv.SIGNE.name().equals(pv.getStatutPv())) {
            throw new BusinessRuleException("Archivage impossible : le PV n'est pas signé (statut « "
                    + pv.getStatutPv() + " »).");
        }
        if (pv.getDateArchivage() != null) {
            throw new BusinessRuleException("Ce PV est déjà archivé (le " + pv.getDateArchivage() + ").");
        }
        String localite = repository.findLocaliteByPv(id).orElse(null);
        String maLocalite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (localite != null && !localite.equals(maLocalite)) {
            throw new AccessDeniedException("Archivage réservé à l'Assistant contrôleur de la localité du dossier.");
        }
        Integer idDossier = repository.findIdDossierByPv(id).orElse(null);
        Dossier dossier = idDossier == null ? null : dossierRepository.findById(idDossier).orElse(null);
        if (dossier == null || !StatutDossier.DECISION_TRANSMISE_SIGMP.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException("Archivage impossible : la décision du dossier n'a pas encore été "
                    + "transmise à SIGMP par le vérificateur (statut « "
                    + (dossier == null ? "?" : dossier.getStatut()) + " »).");
        }
        pv.setDateArchivage(LocalDate.now());
        pv.setImArchiveur(CurrentUser.ref().orElse(null));
        PvExamen saved = repository.save(pv);
        dossier.setStatut(StatutDossier.CLOTURE.name());
        dossierRepository.save(dossier);
        notifierClotureEligible(idDossier);
        return toDtoLecture(saved);
    }

    /** Alerte de clôture éligible (§3.7) — émise à l'ARCHIVAGE du PV (clôture du dossier). */
    private void notifierClotureEligible(Integer idDossier) {
        String titre = "Dossier clôturé éligible à publication";
        String corps = "Le dossier " + idDossier + " est clôturé conforme et éligible à publication.";
        for (Controleur charge : controleurDirectory.chargesPublication()) {
            notificationService.emettre(idDossier, TypeNotification.CLOTURE_ELIGIBLE,
                    charge.getImControleur(), charge.getEmailCont(), titre, corps);
        }
    }

    /** Le co-signataire (Président/CC) doit être une personne différente du Membre (§2.6). */
    private void exigerCoSignataireDistinct(String coSignataire, PvExamen pv) {
        if (coSignataire.equals(pv.getImCtrlMembre())) {
            throw new BusinessRuleException(
                    "Le co-signataire doit être différent du Membre signataire (auto-co-signature interdite, §2.6).");
        }
    }

    /** Un Chef de commission ne co-signe que les PV de sa localité (§3.3). */
    private void exigerCcDeLaLocalite(PvExamen pv) {
        String localiteDossier = repository.findLocaliteByPv(pv.getIdPv()).orElse(null);
        String localiteCc = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (localiteDossier != null && !localiteDossier.equals(localiteCc)) {
            throw new AccessDeniedException("Le CC ne peut co-signer que les PV de sa localité (§3.3).");
        }
    }

    /**
     * Notifie la PRMP du dossier dès que le PV atteint le statut SIGNE (§3.1). La PRMP ne
     * reçoit que PV_SIGNE, pas les statuts internes de la navette.
     */
    private void notifierPvSigne(PvExamen pv) {
        String titre = "PV signé";
        String reference = pv.getRefePv() != null ? pv.getRefePv()
                : pv.getReferencePv() != null ? pv.getReferencePv() : ("n° " + pv.getIdPv());
        String corps = "Le PV " + reference + " a été signé. Il est consultable dans « PV définitifs » "
                + "(base de la rectification selon les observations).";
        // ⚠️ 2026-08-02 — notification ACTIONNABLE : portée par la PRMP (ref=idPrmp) avec objet PV +
        // dossier (avant : émission par e-mail seul, sans objet — clic inerte côté front).
        Integer idDossier = repository.findIdDossierByPv(pv.getIdPv()).orElse(null);
        for (String idPrmp : repository.findIdPrmpByPv(pv.getIdPv())) {
            String email = prmpRepository.findById(idPrmp).map(Prmp::getEmailPrmp).orElse(null);
            notificationService.emettrePrmp(TypeNotification.PV_SIGNE, idPrmp, email,
                    pv.getIdPv(), TypeObjet.PV, idDossier, titre, corps);
        }
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private PvExamen load(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PvExamen introuvable : " + id));
    }

    /**
     * ⚠️ Règle ajoutée (2026-08-02) — chaque rôle ne signe qu'UNE fois : re-signer écraserait la date
     * de signature déjà posée (409 ; le front désactive le bouton, le backend reste l'autorité).
     */
    private void exigerPasEncoreSigne(LocalDate dateSignature, String roleLibelle) {
        if (dateSignature != null) {
            throw new BusinessRuleException("Le PV est déjà signé pour le rôle " + roleLibelle
                    + " (le " + dateSignature + ") : une signature ne se pose qu'une fois.");
        }
    }

    /** Vérifie que le PV est dans l'un des statuts attendus, sinon HTTP 409. */
    private void requireStatut(PvExamen pv, StatutPv... attendus) {
        String courant = pv.getStatutPv();
        for (StatutPv s : attendus) {
            if (s.name().equals(courant)) {
                return;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < attendus.length; i++) {
            if (i > 0) {
                sb.append(" ou ");
            }
            sb.append(attendus[i].name());
        }
        throw new BusinessRuleException(
                "Action impossible : le PV est au statut « " + courant + " », attendu « " + sb + " ».");
    }

    private RoleSignataire parseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new BusinessRuleException("Le rôle du signataire (MEMBRE / PRESIDENT / CC) est obligatoire.");
        }
        try {
            return RoleSignataire.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Rôle de signataire invalide : « " + role + " ».");
        }
    }

    /** Insère un mouvement de navette (PK assignée + NUM_NAVETTE incrémenté) et met à jour NB_NAVETTES. */
    private void ajouterNavette(PvExamen pv, SensNavette sens, String imActeur, String commentaire) {
        int numNavette = navetteRepository.findMaxNumNavetteByPv(pv.getIdPv()) + 1;

        PvNavette navette = new PvNavette();
        navette.setIdNavette(navetteRepository.findMaxIdNavette() + 1);
        navette.setIdPv(pv.getIdPv());
        navette.setNumNavette(numNavette);
        navette.setSens(sens.name());
        navette.setImActeur(imActeur);
        navette.setDateAction(LocalDateTime.now());
        navette.setCommentaire(commentaire);
        navetteRepository.save(navette);

        pv.setNbNavettes(numNavette);
    }
}
