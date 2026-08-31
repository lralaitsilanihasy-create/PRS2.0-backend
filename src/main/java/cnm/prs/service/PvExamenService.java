package cnm.prs.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.PvActionRequest;
import cnm.prs.dto.PvExamenDto;
import cnm.prs.dto.PvVisaRequest;
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
import cnm.prs.exception.EndpointRetireException;
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
 *
 * <p>⚠️ Audit 2026-08-27, lot B — <strong>gardes des chemins secondaires</strong> : le contrôle
 * d'identité n'existait qu'à la signature. Sont désormais gardés de la même façon
 * <em>l'édition et la soumission</em> du projet (Membre attributaire, ou délégué de la localité —
 * {@link #exigerRedacteurDuProjet}) et la <em>clôture de navette</em> (retour / acceptation, bornée
 * à la localité du dossier — {@link #exigerActeurDeLaLocalite}). L'acteur tracé dans la navette est
 * l'utilisateur authentifié, plus le champ {@code imActeur} du corps de requête.</p>
 */
@Service
@Transactional
public class PvExamenService {

    /** Journal des transitions du circuit (⚠️ LOT 4 — 2026-08-26), format {@code [CIRCUIT] …}. */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PvExamenService.class);

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
    private final cnm.prs.security.PermissionService permissionService;
    /** ⚠️ 2026-08-19 — génération du PDF hors transaction : publication d'événement + tâche de fond. */
    private final org.springframework.context.ApplicationEventPublisher evenements;
    private final PvDocumentTache documentTache;

    public PvExamenService(PvExamenRepository repository, PvNavetteRepository navetteRepository,
            PrmpRepository prmpRepository, NotificationService notificationService,
            ControleurDirectory controleurDirectory, DossierRepository dossierRepository,
            ControleurRepository controleurRepository, PvDocumentService pvDocumentService,
            ExamenDetailRepository examenDetailRepository, ExamenPieceRepository examenPieceRepository,
            ObservationPvService observationPvService,
            cnm.prs.security.PermissionService permissionService,
            org.springframework.context.ApplicationEventPublisher evenements,
            PvDocumentTache documentTache) {
        this.observationPvService = observationPvService;
        this.permissionService = permissionService;
        this.evenements = evenements;
        this.documentTache = documentTache;
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
        PvExamenDto dto = peuplerNoms(PvExamenMapper.toDto(entity));
        dto.setDocumentDisponible(pvDocumentService.documentDisponible(entity));
        // ⚠️ 2026-08-19 — rattrapage des PV signés SANS fichier (antérieurs à la génération post-commit,
        // ou dont la génération a échoué) : s'ils sont éligibles, la production part en arrière-plan à la
        // consultation — documentDisponible passera à true au prochain rafraîchissement, sans requête lente.
        if (Boolean.FALSE.equals(dto.getDocumentDisponible())
                && StatutPv.SIGNE.name().equals(entity.getStatutPv())
                && !documentTache.estEnCours(entity.getIdPv())
                && pvDocumentService.estEligible(entity)) {
            documentTache.genererEnArrierePlan(entity.getIdPv());
        }
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
        // ⚠️ 2026-08-19 — fenêtre post-signature : documentDisponible est false tant que la génération de
        // fond n'a pas posé CHEMIN_DOCUMENT, le front n'appelle donc pas ici pendant l'intervalle. Si un
        // client appelle quand même, la régénération paresseuse ci-dessous sert le PDF (lentement mais
        // correctement) — les conversions concurrentes éventuelles sont sérialisées par documents4j.
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
     * ⚠️ Règle ajoutée — crée le Projet de PV d'un examen à sa soumission avec l'avis choisi et le
     * Vérificateur désigné Secrétaire de séance ({@code idSecretaireSeance}, déjà validé).
     * Réutilise {@link #create(PvExamenDto)}.
     *
     * <p>⚠️ LOT 3b (2026-08-26) — la PK n'est plus pré-allouée ici en {@code max + 1} : le DTO part
     * sans identifiant et {@code create} la tire de la séquence {@code seq_pv_examen}.</p>
     */
    public PvExamenDto creerProjet(Integer idExamen, String idAvis, String idSecretaireSeance) {
        PvExamenDto dto = new PvExamenDto();
        dto.setIdExamen(idExamen);
        dto.setIdAvis(idAvis);
        dto.setIdSecretaireSeance(idSecretaireSeance);
        return create(dto);
    }

    public PvExamenDto create(PvExamenDto dto) {
        PvExamen entity = PvExamenMapper.toEntity(dto);
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        entity.setIdPv(ClePrimaire.reallouer(dto.getIdPv(), repository::existsById, repository::nextIdPv));
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
        return peuplerNoms(PvExamenMapper.toDto(saved));
    }

    /**
     * Renseigne les noms lisibles (« prénoms nom ») des désignations du PV depuis {@code tr_controleur},
     * en lecture seule : le Secrétaire de séance et — ⚠️ depuis le 2026-08-28 — le Membre co-signataire.
     * Sans ce second nom, le front devrait rappeler l'annuaire pour afficher « en attente de la signature
     * de X » ; il charge déjà les contrôleurs, mais la liste n'est pas garantie chargée sur un PV isolé.
     */
    private PvExamenDto peuplerNoms(PvExamenDto dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getIdSecretaireSeance() != null) {
            nomComplet(dto.getIdSecretaireSeance()).ifPresent(dto::setNomSecretaireSeance);
        }
        if (dto.getImMembreCoSignataire() != null) {
            nomComplet(dto.getImMembreCoSignataire()).ifPresent(dto::setNomMembreCoSignataire);
        }
        // ⚠️ Visa unique (2026-08-31) — le dispatcheur n'est pas une colonne du PV : il vient du
        // dispatch, via l'examen. Le front en a besoin sur l'écran du PV pour conditionner « Viser »
        // sans charger le dispatch. Coût : une requête de plus par PV — assumé, cohérent avec la
        // résolution de noms déjà faite ici, mais c'est un N+1 supplémentaire sur les listes.
        if (dto.getIdPv() != null) {
            repository.findImDispatcheurByPv(dto.getIdPv()).filter(s -> !s.isBlank()).ifPresent(im -> {
                dto.setImDispatcheur(im);
                nomComplet(im).ifPresent(dto::setNomDispatcheur);
            });
        }
        return dto;
    }

    /** « Prénoms nom » d'un contrôleur, vide si le matricule est inconnu ou l'état civil absent. */
    private Optional<String> nomComplet(String imControleur) {
        return controleurRepository.findById(imControleur).map(c -> {
            String n = ((c.getPrenomsCont() == null ? "" : c.getPrenomsCont()) + " "
                    + (c.getNomCont() == null ? "" : c.getNomCont())).trim();
            return n.isBlank() ? null : n;
        });
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
        // ⚠️ Audit 2026-08-27 (lot B) — le PUT générique n'exigeait AUCUNE identité : n'importe quel
        // Membre de n'importe quelle localité réécrivait le projet d'un autre.
        exigerRedacteurDuProjet(existing);
        requireStatut(existing, StatutPv.BROUILLON, StatutPv.EN_RECTIFICATION);
        // ⚠️ Verrou optimiste HTTP (plan §3) : version périmée → 409 CONFLIT_VERSION, avant toute écriture.
        VerrouOptimiste.exigerVersionCourante(dto.getVersion(), existing.getVersion());
        existing.setIdExamen(dto.getIdExamen());
        existing.setIdAvis(dto.getIdAvis());
        existing.setImCtrlPresident(dto.getImCtrlPresident());
        existing.setImCtrlCc(dto.getImCtrlCc());
        // ⚠️ Règle ajoutée — imCtrlMembre re-dérivé de l'attribution (dispatch), jamais le corps.
        existing.setImCtrlMembre(attributaireDeLExamen(dto.getIdExamen()));
        existing.setSyntheseObservations(dto.getSyntheseObservations());
        existing.setReferencePv(dto.getReferencePv());
        // ⚠️ saveAndFlush : l'incrément de @Version se fait au flush — sans lui la réponse rendrait
        // l'ancienne version et le client re-conflicterait au PUT suivant (cf. plan §4).
        return PvExamenMapper.toDto(repository.saveAndFlush(existing));
    }

    /**
     * Suppression d'un PV (Administrateur).
     *
     * <p>⚠️ Audit 2026-08-27 (lot B) — un PV <strong>archivé</strong> est une pièce close du circuit
     * (date et auteur d'archivage posés, dossier CLÔTURÉ) : sa suppression est refusée (409), comme
     * celle d'une navette. Un PV <em>signé</em> mais non archivé reste supprimable, à dessein : c'est
     * le cas que {@link #realignerDossierSansPvSigne} rattrape (le dossier redescend à
     * {@link StatutDossier#EXAMINE} pour qu'un PV puisse être reproduit) — le retirer fermerait
     * l'unique porte de sortie d'un PV signé par erreur.</p>
     */
    public void delete(Integer id) {
        PvExamen pv = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PvExamen introuvable : " + id));
        if (pv.getDateArchivage() != null) {
            throw new BusinessRuleException("Ce PV a été archivé le " + pv.getDateArchivage()
                    + " : une pièce archivée du circuit ne se supprime pas (§3.5 — traçabilité).");
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

    /**
     * Dossier porteur du PV — uniquement pour la journalisation du circuit (⚠️ LOT 4, 2026-08-26),
     * afin que toutes les lignes {@code [CIRCUIT]} restent greffables sur le même {@code dossier=}.
     * Le surcoût (un SELECT indexé) est sans portée : ces transitions sont rares par dossier.
     */
    private Integer dossierDuPv(Integer idPv) {
        return repository.findIdDossierByPv(idPv).orElse(null);
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
        // ⚠️ Audit 2026-08-27 (lot B) — la soumission engage le Membre attributaire : elle ne peut
        // pas être posée par un autre Membre (fût-il de la localité).
        exigerRedacteurDuProjet(pv);
        requireStatut(pv, StatutPv.BROUILLON, StatutPv.EN_RECTIFICATION);

        if (pv.getDateSoumissionInitiale() == null) {
            pv.setDateSoumissionInitiale(LocalDate.now());
        }
        pv.setStatutPv(StatutPv.PROJET_SOUMIS.name());
        ajouterNavette(pv, SensNavette.SOUMISSION, req.commentaire());
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
        log.info("[CIRCUIT] navette PV soumission dossier={} acteur={} pv={} statutPv={} navettes={}",
                idDossier, CurrentUser.login().orElse(null), saved.getIdPv(),
                StatutPv.PROJET_SOUMIS.name(), saved.getNbNavettes());
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
    /**
     * ⚠️ 2026-08-31 — même garde, exposée pour la SOUMISSION de l'examen : l'avis y étant désormais
     * émis par le Membre, il doit y être contrôlé. Avant cette date, un avis fourni à la soumission
     * était posé sans aucune vérification.
     */
    public void validerCoherenceAvisPublic(Integer idExamen, String idAvis) {
        validerCoherenceAvis(idExamen, idAvis);
    }

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
        // ⚠️ Règle ÉLARGIE (2026-08-15, décision produit) : Vérificateur TITULAIRE de la localité OU
        // contrôleur couvert par une paire « → Vérificateur » ACTIVE (auto-désignation du Président/CC).
        if (!controleurDirectory.peutEtreSecretaireSeance(idSecretaire, localite)) {
            throw new BusinessRuleException(
                    "Le Secrétaire de séance doit être un Vérificateur de la localité du dossier, "
                            + "ou un contrôleur couvert par une délégation active vers Vérificateur.");
        }
        return idSecretaire.trim();
    }

    /**
     * [Auto] Notifie le <strong>dispatcheur</strong> qu'un projet de PV attend son visa
     * ({@code PV_A_VALIDER}).
     *
     * <p>⚠️ 2026-08-31 — la notification partait à TOUS les Présidents et aux CC de la localité. Depuis
     * que le visa est réservé au dispatcheur (§4), prévenir les autres serait leur annoncer une tâche
     * qu'ils recevront en 403. On cible donc le seul destinataire qui puisse agir.</p>
     *
     * <p>Repli : si le dispatch ne porte pas de dispatcheur (donnée incomplète), on retombe sur
     * l'ancien large — mieux vaut une notification à trop de monde que zéro, un PV sans destinataire
     * resterait indéfiniment en navette sans que personne ne le sache.</p>
     */
    private void notifierPvAValider(PvExamen pv) {
        String localite = repository.findLocaliteByPv(pv.getIdPv()).orElse(null);
        Integer idDossier = repository.findIdDossierByPv(pv.getIdPv()).orElse(null);
        String reference = pv.getReferencePv() != null ? pv.getReferencePv() : ("n° " + pv.getIdPv());
        String titre = "Projet de PV à viser";
        String corps = "Le projet de PV " + reference + " a été soumis et attend votre visa.";

        String dispatcheur = repository.findImDispatcheurByPv(pv.getIdPv()).filter(s -> !s.isBlank()).orElse(null);
        List<Controleur> destinataires;
        if (dispatcheur != null) {
            destinataires = controleurRepository.findById(dispatcheur).map(List::of).orElseGet(List::of);
        } else {
            destinataires = new ArrayList<>(controleurDirectory.presidents());
            if (localite != null) {
                destinataires.addAll(controleurDirectory.chefsCommission(localite));
            }
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

    /**
     * [Auto] ⚠️ Co-signature (2026-08-28) — notifie le Membre DÉSIGNÉ ({@code PV_A_COSIGNER}) : il est
     * seul à pouvoir poser la part Membre et n'a aucun autre moyen d'apprendre qu'on l'attend. La
     * désignation ne figure ni dans sa liste de dossiers ni dans un dispatch — sans cette notification,
     * le PV resterait en attente d'une signature que personne ne sait devoir donner.
     */
    private void notifierMembreCoSignataire(PvExamen pv) {
        String designe = pv.getImMembreCoSignataire();
        if (designe == null || designe.isBlank()) {
            return;
        }
        Integer idDossier = repository.findIdDossierByPv(pv.getIdPv()).orElse(null);
        String titre = "PV à co-signer";
        String corps = "Vous avez été désigné pour co-signer le PV " + referencePv(pv) + " : votre signature est attendue.";
        notificationService.emettreControleur(TypeNotification.PV_A_COSIGNER, designe, null,
                pv.getIdPv(), TypeObjet.PV, idDossier, titre, corps);
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
        // ⚠️ Audit 2026-08-27 (lot B) — le CC ne clôt la navette que dans SA localité (§3.3) ;
        // le Président (toutes localités) reste exempté, comme partout ailleurs.
        exigerActeurDeLaLocalite(pv);
        requireStatut(pv, StatutPv.PROJET_SOUMIS);
        if (req.commentaire() == null || req.commentaire().isBlank()) {
            throw new BusinessRuleException("Le commentaire de rectification est obligatoire (§3.2).");
        }
        pv.setStatutPv(StatutPv.EN_RECTIFICATION.name());
        ajouterNavette(pv, SensNavette.RETOUR_RECTIF, req.commentaire());
        PvExamen saved = repository.save(pv);
        log.info("[CIRCUIT] navette PV retour rectification dossier={} acteur={} pv={} statutPv={} navettes={}",
                dossierDuPv(saved.getIdPv()), CurrentUser.login().orElse(null), saved.getIdPv(),
                StatutPv.EN_RECTIFICATION.name(), saved.getNbNavettes());
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
    /**
     * ⚠️ RETIRÉ le 2026-08-31 (réforme « Visa unique ») — l'acceptation est fusionnée dans
     * {@link #viser(Integer, PvVisaRequest)} : clore la navette et signer sa part étaient deux gestes
     * pour une seule décision. 410 Gone, avec le remplaçant dans le message.
     */
    public PvExamenDto accepter(Integer id, PvActionRequest req) {
        throw new EndpointRetireException(
                "L'acceptation du projet de PV est retirée depuis le 2026-08-31 : elle est fusionnée "
                        + "avec la signature du Président / Chef de commission dans le VISA. "
                        + "Utilisez POST /api/pv-examens/" + id + "/viser (avis, Secrétaire de séance "
                        + "et Membre co-signataire en un seul geste).");
    }

    /**
     * ⚠️ <strong>VISA</strong> (réforme du 2026-08-31, arbitrage du pilote) — clôture de la navette en
     * UN SEUL GESTE : avis éventuellement modifié + Secrétaire de séance + Membre co-signataire + part
     * de signature du rôle. Remplace {@code accepter} suivi de {@code signer(role=PRESIDENT|CC)}.
     *
     * <p><strong>Ce que la réforme inverse.</strong> Depuis le 2026-08-01, l'avis était posé par le
     * P/CC à l'acceptation et le PV naissait sans avis. Le pilote a tranché l'inverse : « le Membre qui
     * fait l'examen émet son avis à la fin de l'examen ; cet avis peut être modifié à la fin de la
     * navette, qui finit par le visa du Président ou du CC qui a fait le dispatch ».</p>
     *
     * <p><strong>Contrainte d'identité (§4 de la spec).</strong> Seul le <em>dispatcheur</em> vise —
     * 403 sinon, <strong>même couvert par une paire de délégation active</strong>. C'est la ligne de
     * l'invariant du 2026-08-15 : la délégation ascendante autorise à exercer une TÂCHE de profil, pas
     * à endosser l'IDENTITÉ de quelqu'un. Viser, comme signer, atteste ; instruire se délègue. C'est
     * aussi pourquoi {@link #retourner} reste ouvert au rôle : un visa bloqué gèle la clôture d'un PV,
     * un retour bloqué gèlerait la navette entière.</p>
     *
     * <p><strong>Ordre des gardes.</strong> L'identité est vérifiée AVANT toute valeur du corps : un
     * non-dispatcheur reçoit 403 sans qu'on lui dise si son secrétaire ou son co-signataire étaient
     * valides.</p>
     *
     * <p><strong>Transition (§6).</strong> Accepté sur {@code PROJET_SOUMIS} et sur un
     * {@code PROJET_ACCEPTE} dont la part du rôle n'est pas encore signée — les PV acceptés sous
     * l'ancien contrat se complètent ainsi sans re-exiger ce qui est déjà posé.</p>
     */
    public PvExamenDto viser(Integer id, PvVisaRequest req) {
        PvExamen pv = load(id);

        // ① Identité d'abord : seul le dispatcheur vise (§4). AVANT le périmètre et le corps.
        String acteur = CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Acteur non identifié."));
        String dispatcheur = repository.findImDispatcheurByPv(id).filter(s -> !s.isBlank()).orElse(null);
        if (dispatcheur == null) {
            throw new BusinessRuleException(
                    "Visa impossible : aucun dispatcheur enregistré pour ce dossier (dispatch incomplet).");
        }
        if (!dispatcheur.equals(acteur)) {
            throw new AccessDeniedException(
                    "Le visa est réservé au Président ou au Chef de commission QUI A FAIT LE DISPATCH "
                            + "de ce dossier (§4). Une délégation active n'y donne pas droit : viser est un "
                            + "acte d'identité. Pour débloquer, re-dispatchez le dossier.");
        }

        // ② Profil : la part signée est dérivée de l'acteur — pas de champ « role » dans le corps.
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil != ProfilUtilisateur.PRESIDENT && profil != ProfilUtilisateur.CHEF_COMMISSION) {
            throw new AccessDeniedException(
                    "Le visa est réservé au Président (§3.2) ou au Chef de commission (§3.3).");
        }
        // ③ Périmètre : le CC ne clôt que dans sa localité ; le Président reste compétent partout.
        exigerActeurDeLaLocalite(pv);

        // ④ État : navette en cours, OU PV déjà accepté dont la part du rôle reste à poser (§6).
        LocalDate dejaSignee = profil == ProfilUtilisateur.PRESIDENT
                ? pv.getDateSignaturePresident() : pv.getDateSignatureCc();
        boolean navetteEnCours = StatutPv.PROJET_SOUMIS.name().equals(pv.getStatutPv());
        boolean accepteNonSigne = StatutPv.PROJET_ACCEPTE.name().equals(pv.getStatutPv()) && dejaSignee == null;
        if (!navetteEnCours && !accepteNonSigne) {
            throw new BusinessRuleException("Visa impossible : statut « " + pv.getStatutPv()
                    + " » (attendu PROJET_SOUMIS, ou PROJET_ACCEPTE dont votre part n'est pas signée).");
        }

        // ⑤ Avis : fourni → remplace après revalidation ; absent → celui du Membre est conservé.
        if (req.idAvis() != null && !req.idAvis().isBlank()) {
            validerCoherenceAvis(pv.getIdExamen(), req.idAvis().trim());
            pv.setIdAvis(req.idAvis().trim());
        } else if (pv.getIdAvis() == null || pv.getIdAvis().isBlank()) {
            // PV en navette au moment du déploiement (§6) : personne n'a jamais posé d'avis.
            throw new BusinessRuleException(
                    "Ce projet de PV ne porte aucun avis : le visa doit en fournir un (« idAvis »).");
        }

        // ⑥ Secrétaire de séance et ⑦ Membre co-signataire : gardes existantes, inchangées.
        pv.setIdSecretaireSeance(validerSecretaireSeance(id, req.idSecretaireSeance()));
        designerMembreCoSignataire(pv, req.imMembreCoSignataire(), acteur);

        // ⑧ Part de signature du rôle — le verrou « une signature par rôle » reste posé.
        LocalDate aujourdhui = LocalDate.now();
        if (profil == ProfilUtilisateur.PRESIDENT) {
            exigerPasEncoreSigne(pv.getDateSignaturePresident(), "Président");
            pv.setDateSignaturePresident(aujourdhui);
            pv.setImCtrlPresident(acteur);
        } else {
            exigerPasEncoreSigne(pv.getDateSignatureCc(), "Chef de commission");
            pv.setDateSignatureCc(aujourdhui);
            pv.setImCtrlCc(acteur);
        }

        // ⑨ Clôture de la navette.
        boolean etaitEnNavette = navetteEnCours;
        pv.setStatutPv(StatutPv.PROJET_ACCEPTE.name());
        pv.setDateAcceptation(aujourdhui);
        if (etaitEnNavette) {
            ajouterNavette(pv, SensNavette.ACCEPTATION, req.commentaire());
        }
        PvExamen saved = repository.save(pv);
        log.info("[CIRCUIT] visa PV dossier={} acteur={} pv={} statutPv={} navettes={}",
                dossierDuPv(saved.getIdPv()), CurrentUser.login().orElse(null), saved.getIdPv(),
                StatutPv.PROJET_ACCEPTE.name(), saved.getNbNavettes());

        // ⑩ Notifications : l'auteur apprend l'acceptation, le désigné qu'on attend sa signature.
        notifierPvAuteur(saved, TypeNotification.PV_ACCEPTE, "Projet de PV visé",
                "Le projet de PV " + referencePv(saved) + " a été visé : il attend la co-signature du Membre désigné.");
        notifierMembreCoSignataire(saved);
        return peuplerNoms(PvExamenMapper.toDto(saved));
    }

    /**
     * Co-signature du PV visé — ⚠️ depuis le 2026-08-31, <strong>part du MEMBRE désigné uniquement</strong>
     * (§3.5). N'est possible qu'au statut {@code PROJET_ACCEPTE} ; bascule en {@code SIGNE} puisque la
     * part du Président ou du CC a déjà été posée par le {@link #viser(Integer, PvVisaRequest) visa}.
     *
     * <p>Les rôles {@code PRESIDENT} et {@code CC} y sont refusés en 409 : leur signature est devenue
     * indissociable du visa, qui seul contrôle l'identité du dispatcheur et la désignation du
     * co-signataire.</p>
     */
    public PvExamenDto signer(Integer id, PvActionRequest req) {
        // ⚠️ Anti-doublon (2026-08-02) — chargement VERROUILLÉ : la génération du PDF rend la signature
        // longue ; des clics répétés lisaient tous PROJET_ACCEPTE et notifiaient plusieurs fois. Le
        // verrou sérialise : la 2ᵉ requête attend, voit SIGNE (ou la date posée) et reçoit 409.
        PvExamen pv = repository.findByIdVerrouille(id)
                .orElseThrow(() -> new ResourceNotFoundException("PvExamen introuvable : " + id));
        requireStatut(pv, StatutPv.PROJET_ACCEPTE);
        // Garde-fou (⚠️ 2026-08-01, reformulé le 2026-08-31) : pas de signature sans avis global. Il est
        // désormais posé par le Membre à la soumission, et confirmé ou remplacé au visa.
        if (pv.getIdAvis() == null || pv.getIdAvis().isBlank()) {
            throw new BusinessRuleException(
                    "Avis global manquant : le projet de PV doit être visé (avis posé) avant la signature.");
        }

        RoleSignataire role = parseRole(req.role());
        // Le signataire est l'utilisateur authentifié (jamais req.imActeur(), falsifiable).
        String signataire = CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Signataire non identifié."));
        LocalDate today = LocalDate.now();
        switch (role) {
            case MEMBRE -> {
                // ⚠️ Co-signature (2026-08-28) — la part Membre appartient au DÉSIGNÉ, à personne d'autre.
                // L'attributaire n'y a plus droit du seul fait d'avoir examiné : il doit avoir été désigné.
                exigerDesignationFaite(pv);
                if (!signataire.equals(pv.getImMembreCoSignataire())) {
                    throw new AccessDeniedException(
                            "La signature Membre est réservée au Membre désigné par le Président ou le Chef "
                                    + "de commission (co-signature, §2.6/§3.5).");
                }
                exigerPasEncoreSigne(pv.getDateSignatureMembre(), "Membre");
                pv.setDateSignatureMembre(today);
                // ⚠️ IM_CTRL_MEMBRE n'est délibérément PAS réécrit ici (il l'était jusqu'au 2026-08-28,
                // sans effet tant que signataire == attributaire). Il désigne QUI A EXAMINÉ le dossier :
                // il est re-dérivé du dispatch à chaque create/update et IMPRIMÉ sur le PV officiel
                // (PvDocumentService#nomMembreAttributaire). Le co-signataire étant désormais un tiers,
                // l'écraser ferait dire au document qu'une autre personne a mené l'examen, détournerait
                // les notifications de l'auteur (notifierPvAuteur) et déplacerait le droit de rédaction
                // (exigerRedacteurDuProjet). Le signataire de la part Membre est tracé par
                // IM_MEMBRE_COSIGNATAIRE, qui suffit.
            }
            // ⚠️ 2026-08-31 — les parts PRÉSIDENT et CC ne se signent plus ici : elles sont posées par
            // le VISA, qui les fusionne avec la clôture de navette. Laisser ces branches ouvertes
            // permettrait de signer sans désigner de co-signataire et sans être le dispatcheur — soit
            // exactement les deux gardes que la réforme installe. 409 orientant vers le bon geste.
            case PRESIDENT, CC -> throw new BusinessRuleException(
                    "La signature du Président et du Chef de commission est retirée de « signer » depuis "
                            + "le 2026-08-31 : elle fait partie du VISA. Utilisez POST /api/pv-examens/"
                            + id + "/viser. « signer » ne porte plus que la part du Membre désigné.");
        }

        boolean membreSigne = pv.getDateSignatureMembre() != null;
        boolean coSigne = pv.getDateSignaturePresident() != null || pv.getDateSignatureCc() != null;
        if (membreSigne && coSigne) {
            pv.setStatutPv(StatutPv.SIGNE.name());
            pv.setDatePv(today);
            log.info("[CIRCUIT] signature PV dossier={} acteur={} pv={} statutPv={}",
                    dossierDuPv(pv.getIdPv()), CurrentUser.login().orElse(null), pv.getIdPv(),
                    StatutPv.SIGNE.name());
            // ⚠️ 2026-08-19 — la génération du PDF (Word piloté localement, plusieurs secondes) est SORTIE
            // du chemin de la signature : le PV est marqué SIGNE et la réponse part immédiatement ; le
            // document est produit APRÈS COMMIT par PvDocumentTache, qui renseigne CHEMIN_DOCUMENT quand
            // il est prêt (documentDisponible=false entre-temps — le front sait l'afficher). Un échec de
            // génération ne peut plus faire échouer la signature.
            evenements.publishEvent(new PvSigneEvent(pv.getIdPv()));
            PvExamenDto dto = PvExamenMapper.toDto(repository.save(pv));
            // [Auto] ⚠️ Règle ajoutée — branchement du circuit selon l'avis du PV.
            brancherSelonAvis(pv);
            return dto;
        }
        PvExamen enregistre = repository.save(pv);
        // ⚠️ Co-signature — le P/CC vient de désigner : on prévient le Membre attendu. Ce chemin est le
        // SEUL possible après une signature P/CC : la part Membre exigeant une désignation préalable
        // (ordre B), elle ne peut pas être déjà posée, donc la bascule en SIGNE ci-dessus n'a pas lieu.
        if (role == RoleSignataire.PRESIDENT || role == RoleSignataire.CC) {
            notifierMembreCoSignataire(enregistre);
        }
        return PvExamenMapper.toDto(enregistre);
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
        log.info("[CIRCUIT] archivage PV et cloture dossier={} acteur={} pv={} statut={}",
                idDossier, CurrentUser.login().orElse(null), saved.getIdPv(),
                StatutDossier.CLOTURE.name());
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

    /**
     * ⚠️ Co-signature — DÉSIGNATION du Membre co-signataire par le Président / le Chef de commission,
     * au moment où ils signent (arbitrage du pilote, 2026-08-28 ; remplace {@code exigerCoSignataireDistinct}).
     *
     * <p><strong>Ce qui est abandonné.</strong> La décision produit du 2026-08-15 laissait un P/CC
     * auto-attribué porter les deux parts, la paire (profil → Membre) active de {@code t_delegation_profil}
     * levant le verrou §2.6. Le pilote a tranché : <strong>l'auto-co-signature n'est jamais autorisée</strong>.
     * La délégation ascendante n'est pas remise en cause pour autant — elle continue de désigner
     * l'attributaire ({@code DispatchService}), le rédacteur ({@link #exigerRedacteurDuProjet}) et le
     * Secrétaire de séance : elle autorise à <em>faire</em> le travail, pas à le contresigner seul.</p>
     *
     * <p><strong>Pourquoi ici et pas à l'acceptation.</strong> La désignation se fait au moment de signer,
     * pas à la clôture de navette — contrairement au Secrétaire de séance. Le choix appartient au P/CC
     * qui signe, et il ne peut pas lui échapper par antériorité : la part Membre n'est ouverte qu'après
     * (voir {@link #exigerDesignationFaite}).</p>
     *
     * <p>Trois refus, tous en 409 (même famille que {@code validerSecretaireSeance}) : désignation
     * absente, désignation de soi-même, désigné qui n'est pas un Membre de la localité du dossier.</p>
     */
    private void designerMembreCoSignataire(PvExamen pv, String imDesigne, String signataire) {
        String designe = imDesigne == null ? null : imDesigne.trim();
        if (designe == null || designe.isEmpty()) {
            throw new BusinessRuleException(
                    "Le Membre co-signataire est obligatoire pour signer : désignez le Membre de la localité "
                            + "du dossier appelé à co-signer le PV (§2.6).");
        }
        if (designe.equals(signataire)) {
            throw new BusinessRuleException(
                    "Vous ne pouvez pas vous désigner vous-même : le PV est co-signé par deux personnes "
                            + "distinctes (auto-co-signature interdite, §2.6).");
        }
        String localite = repository.findLocaliteByPv(pv.getIdPv()).orElse(null);
        if (!controleurDirectory.peutEtreMembreCoSignataire(designe, localite)) {
            throw new BusinessRuleException(
                    "Le Membre co-signataire doit être un Membre de la localité du dossier (§3.3) — « "
                            + designe + " » ne l'est pas.");
        }
        pv.setImMembreCoSignataire(designe);
    }

    /**
     * ⚠️ Ordre B (arbitrage du pilote, 2026-08-28) — la part Membre n'est signable qu'APRÈS la
     * désignation par le Président / le CC. Sans cette barrière, un Membre signant spontanément avant
     * eux viderait leur choix de son objet : la part serait déjà posée. 409, pas 403 : le signataire
     * n'est pas illégitime, c'est le circuit qui n'est pas encore à cette étape.
     */
    private void exigerDesignationFaite(PvExamen pv) {
        if (pv.getImMembreCoSignataire() == null || pv.getImMembreCoSignataire().isBlank()) {
            throw new BusinessRuleException(
                    "La part Membre n'est pas encore ouverte : le Président ou le Chef de commission doit "
                            + "d'abord signer et désigner le Membre co-signataire (§2.6).");
        }
    }

    /**
     * ⚠️ Audit 2026-08-27 (lot B) — <strong>identité du rédacteur</strong> du projet de PV, exigée par
     * {@link #update} et {@link #soumettre} (§2.4, §3.5). Sont admis :
     * <ul>
     *   <li>l'<strong>attributaire lui-même</strong> : la claim {@code ref} du jeton vaut
     *       {@code pv.imCtrlMembre} — quel que soit son profil, ce qui couvre le Président ou le CC
     *       auto-attribué au dispatch (circuit court, décision produit 2026-08-15) ;</li>
     *   <li>un contrôleur d'un <strong>autre profil</strong> couvert par une paire (profil → Membre)
     *       <strong>active</strong> de {@code t_delegation_profil}, à condition d'être <strong>de la
     *       localité du dossier</strong> (§3.3) — même modèle data-driven que
     *       {@link #exigerCoSignataireDistinct}.</li>
     * </ul>
     * Un Membre <em>titulaire</em> qui n'est pas l'attributaire est donc refusé (403) : la délégation
     * ascendante ne joue jamais entre pairs.
     */
    private void exigerRedacteurDuProjet(PvExamen pv) {
        String moi = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (moi != null && moi.equals(pv.getImCtrlMembre())) {
            return;
        }
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil != ProfilUtilisateur.MEMBRE && permissionService.peutExercer(profil, ProfilUtilisateur.MEMBRE)) {
            exigerActeurDeLaLocalite(pv);   // la délégation reste bornée à la localité (§3.3)
            return;
        }
        throw new AccessDeniedException(
                "Projet de PV réservé au Membre attributaire de l'examen (§2.4, §3.5), ou à un contrôleur "
                        + "de la localité du dossier couvert par une délégation active vers Membre.");
    }

    /**
     * ⚠️ Audit 2026-08-27 (lot B) — clôture de navette ({@link #retourner}, {@link #accepter}) bornée à
     * la localité du dossier (§3.3) : un CC d'une autre localité y accédait sans contrôle. Délègue à
     * {@link Visibilite#exigerLocalite} — le Président (et l'Administrateur), sans localité, restent
     * compétents sur toutes les commissions, contrairement à {@link #exigerCcDeLaLocalite} qui ne sert
     * que la co-signature du CC.
     */
    private void exigerActeurDeLaLocalite(PvExamen pv) {
        Visibilite.exigerLocalite(repository.findLocaliteByPv(pv.getIdPv()).orElse(null));
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

    /**
     * Insère un mouvement de navette (PK assignée + NUM_NAVETTE incrémenté) et met à jour NB_NAVETTES.
     *
     * <p>⚠️ Audit 2026-08-27 (lot B) — l'acteur tracé est l'<strong>utilisateur authentifié</strong>
     * ({@code CurrentUser.ref()}), plus le champ {@code imActeur} du corps de requête : une trace de
     * circuit dont l'auteur est déclaré par le client n'en est pas une. Principe déjà appliqué aux
     * signatures du PV. Le front envoyait déjà sa propre {@code ref} — le contrat ne bouge pas, le
     * champ {@code imActeur} de {@code PvActionRequest} est simplement ignoré.</p>
     */
    private void ajouterNavette(PvExamen pv, SensNavette sens, String commentaire) {
        int numNavette = navetteRepository.findMaxNumNavetteByPv(pv.getIdPv()) + 1;

        PvNavette navette = new PvNavette();
        // ⚠️ LOT 3b (2026-08-26) — PK allouée à la séquence seq_pv_navette (max+1 non atomique).
        navette.setIdNavette(navetteRepository.nextIdNavette().intValue());
        navette.setIdPv(pv.getIdPv());
        navette.setNumNavette(numNavette);
        navette.setSens(sens.name());
        navette.setImActeur(CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null));
        navette.setDateAction(LocalDateTime.now());
        navette.setCommentaire(commentaire);
        navetteRepository.save(navette);

        pv.setNbNavettes(numNavette);
    }
}
