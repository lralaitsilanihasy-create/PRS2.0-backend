package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ActionDossierDto;
import cnm.prs.dto.DossierDto;
import cnm.prs.dto.EchangeDto;
import cnm.prs.dto.RechercheDossierDto;
import cnm.prs.entity.AuditLog;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.entity.Marche;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.Verification;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutLettreRenvoi;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.DossierMapper;
import cnm.prs.repository.AnomalieRepository;
import cnm.prs.repository.AuditLogRepository;
import cnm.prs.repository.ChangementLigneRepository;
import cnm.prs.repository.DemandeRetraitRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.TransmissionSigmpRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.LettreRenvoiRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.MessageRepository;
import cnm.prs.repository.NotificationRepository;
import cnm.prs.repository.PieceDemandeRetraitRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.SousTypeDossierRepository;
import cnm.prs.repository.TypeDossierRepository;
import cnm.prs.repository.TypePieceJointeRepository;
import cnm.prs.repository.VerificationRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link Dossier}.
 */
@Service
@Transactional
public class DossierService {

    /**
     * Journal des transitions du circuit (⚠️ LOT 4 — 2026-08-26). Format homogène à tout le circuit :
     * {@code [CIRCUIT] <transition> dossier={} acteur={} <détail>={}}. Uniquement les transitions
     * RÉUSSIES ; jamais les lectures, jamais de donnée personnelle (identifiants et logins seulement).
     */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DossierService.class);

    /** Famille « Dossier de Planification » (codes centralisés dans {@link DossierIntegriteService}). */
    private static final String FAMILLE_DDP = DossierIntegriteService.FAMILLE_DDP;
    /** Code du type de pièce AGPM dans le référentiel {@code t_type_piece_jointe} (obligation conditionnelle). */
    private static final String CODE_PIECE_AGPM = "AGPM";

    /** ⚠️ Lot D §6 — longueur minimale d'une recherche de référence : en deçà, elle ne discrimine rien. */
    private static final int LONGUEUR_MIN_RECHERCHE = 2;
    /** ⚠️ Lot D §6 — plafond de résultats : une barre de recherche propose, elle ne liste pas. */
    private static final int MAX_RESULTATS_RECHERCHE = 10;

    private final DossierRepository repository;
    private final PpmRepository ppmRepository;
    private final ControleurDirectory controleurDirectory;
    private final NotificationService notificationService;
    private final DossierIntegriteService dossierIntegrite;
    private final VerificationRepository verificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final PrmpRepository prmpRepository;
    private final MarcheRepository marcheRepository;
    private final MarchePrevisionRepository marchePrevisionRepository;
    private final MarcheService marcheService;
    private final DemandeRetraitRepository demandeRetraitRepository;
    private final NotificationRepository notificationRepository;
    private final TypePieceJointeRepository typePieceJointeRepository;
    private final PieceJointeDossierRepository pieceJointeDossierRepository;
    private final TypeDossierRepository typeDossierRepository;
    private final SousTypeDossierRepository sousTypeDossierRepository;
    private final LettreRenvoiRepository lettreRenvoiRepository;

    private final VerificationPieceDepotService verificationPieceDepotService;
    /** ⚠️ 2026-08-05 — figeage du diff et bascule du prédécesseur, à la soumission d'une mise à jour. */
    private final MiseAJourPpmService miseAJourPpmService;
    /** ⚠️ Demande front (2026-08-19) — résolution login → nom lisible pour creePar / soumisPar. */
    private final ActeurDirectory acteurDirectory;
    /** ⚠️ Spec « Mandats PRMP » — journal des actions, horodaté par opérateur courant. */
    private final JournalDossierService journalDossier;
    /** ⚠️ Rattachements (2026-09-01) — résolution EN LOT des cibles Vérificateur / Assistant. */
    private final ExamenRepository examenRepository;
    private final TransmissionSigmpRepository transmissionSigmpRepository;
    private final ControleurRepository controleurRepository;

    /**
     * ⚠️ Audit 2026-08-27 (lot D §2) — fermeture de la cascade de suppression. Le circuit passe par
     * {@link CircuitCascadeService} (ordre FK-safe déjà documenté et testé) ; les cinq repositories qui
     * suivent ferment les tables que la suppression laissait derrière elle (orphelins ou 409 de FK).
     */
    private final CircuitCascadeService circuitCascadeService;
    private final ChangementLigneRepository changementLigneRepository;
    private final MessageRepository messageRepository;
    private final LotRepository lotRepository;
    private final AnomalieRepository anomalieRepository;
    private final PieceDemandeRetraitRepository pieceDemandeRetraitRepository;
    /** ⚠️ Chronométrage des délais (2026-09-01) — suspensions PRMP et date prévisionnelle de fin. */
    private final ChronometrageService chronometrage;
    /** ⚠️ Chronométrage des délais (2026-09-01) — référentiel lu une fois par liste. */
    private final DelaiStandardService delaiStandardService;

    public DossierService(DossierRepository repository, PpmRepository ppmRepository,
            ControleurDirectory controleurDirectory, NotificationService notificationService,
            DossierIntegriteService dossierIntegrite, VerificationRepository verificationRepository,
            AuditLogRepository auditLogRepository, PrmpRepository prmpRepository,
            MarcheRepository marcheRepository, MarchePrevisionRepository marchePrevisionRepository,
            DemandeRetraitRepository demandeRetraitRepository,
            NotificationRepository notificationRepository,
            TypePieceJointeRepository typePieceJointeRepository,
            PieceJointeDossierRepository pieceJointeDossierRepository, MarcheService marcheService,
            TypeDossierRepository typeDossierRepository, SousTypeDossierRepository sousTypeDossierRepository,
            LettreRenvoiRepository lettreRenvoiRepository,
            VerificationPieceDepotService verificationPieceDepotService,
            MiseAJourPpmService miseAJourPpmService, JournalDossierService journalDossier,
            ExamenRepository examenRepository, TransmissionSigmpRepository transmissionSigmpRepository,
            ControleurRepository controleurRepository,
            ActeurDirectory acteurDirectory, CircuitCascadeService circuitCascadeService,
            ChangementLigneRepository changementLigneRepository, MessageRepository messageRepository,
            LotRepository lotRepository, AnomalieRepository anomalieRepository,
            PieceDemandeRetraitRepository pieceDemandeRetraitRepository,
            ChronometrageService chronometrage, DelaiStandardService delaiStandardService) {
        this.delaiStandardService = delaiStandardService;
        this.chronometrage = chronometrage;
        this.circuitCascadeService = circuitCascadeService;
        this.changementLigneRepository = changementLigneRepository;
        this.messageRepository = messageRepository;
        this.lotRepository = lotRepository;
        this.anomalieRepository = anomalieRepository;
        this.pieceDemandeRetraitRepository = pieceDemandeRetraitRepository;
        this.verificationPieceDepotService = verificationPieceDepotService;
        this.miseAJourPpmService = miseAJourPpmService;
        this.journalDossier = journalDossier;
        this.examenRepository = examenRepository;
        this.transmissionSigmpRepository = transmissionSigmpRepository;
        this.controleurRepository = controleurRepository;
        this.acteurDirectory = acteurDirectory;
        this.lettreRenvoiRepository = lettreRenvoiRepository;
        this.repository = repository;
        this.ppmRepository = ppmRepository;
        this.controleurDirectory = controleurDirectory;
        this.notificationService = notificationService;
        this.dossierIntegrite = dossierIntegrite;
        this.verificationRepository = verificationRepository;
        this.auditLogRepository = auditLogRepository;
        this.prmpRepository = prmpRepository;
        this.marcheRepository = marcheRepository;
        this.marchePrevisionRepository = marchePrevisionRepository;
        this.demandeRetraitRepository = demandeRetraitRepository;
        this.notificationRepository = notificationRepository;
        this.typePieceJointeRepository = typePieceJointeRepository;
        this.pieceJointeDossierRepository = pieceJointeDossierRepository;
        this.marcheService = marcheService;
        this.typeDossierRepository = typeDossierRepository;
        this.sousTypeDossierRepository = sousTypeDossierRepository;
    }

    /**
     * Liste des dossiers filtrée par le périmètre de visibilité de l'utilisateur (§1), et
     * <strong>optionnellement par statut</strong> ({@code ?statut=SOUMIS}), par <strong>famille</strong>
     * ({@code ?type=DDP}) et par <strong>sous-type</strong> ({@code ?sousType=PPM-AGPM}) — tous filtrés
     * côté serveur : Président / Administrateur voient tout ; les autres profils ne voient que les
     * dossiers de leur localité. La PRMP voit ses propres dossiers ({@code t_dossier.ID_PRMP} / PPM / marché).
     *
     * @param statut   filtre serveur sur {@code t_dossier.STATUT} ; {@code null}/vide = tous statuts
     * @param type     filtre sur la famille ({@code tr_type_dossier}) ; {@code null}/vide = toutes
     * @param sousType filtre sur le sous-type ({@code tr_sous_type_dossier}) ; {@code null}/vide = tous
     * @throws BadRequestException si un filtre fourni n'est pas une valeur connue (→ 400)
     */
    /**
     * ⚠️ Audit front (2026-08-16) — variante PAGINÉE de la liste des dossiers ({@code ?page=&size=}) :
     * mêmes filtres (périmètre + statut/type/sousType), enveloppe {@code Page}.
     *
     * <p>⚠️ Audit 2026-08-27 (lot D §3) — la page est désormais découpée <strong>en SQL</strong>.
     * Elle passait par {@code Pagination.depuisListe(findAll(…))} : la table scopée entière était
     * chargée et mappée en DTO à chaque appel, pour n'en rendre que vingt lignes — demander la page 3
     * coûtait exactement le même travail serveur que de tout télécharger. Les trois branches de
     * périmètre (Président/Administrateur, PRMP, contrôleur de localité) empruntent maintenant la
     * variante paginée du <em>même</em> prédicat, et les filtres famille/sous-type, jusqu'ici appliqués
     * en mémoire après chargement, sont passés à la requête. Forme de la réponse inchangée.</p>
     */
    @Transactional(readOnly = true)
    public Page<DossierDto> findAllPagine(String statut, String type, String sousType, Pageable pageable) {
        String filtre = normaliserStatut(statut);
        String filtreType = normaliserType(type);
        String filtreSousType = normaliserSousType(sousType);
        Pageable page = Pagination.page(pageable, "idDossier");
        return enrichir(chargerScopeesPaginees(filtre, filtreType, filtreSousType, page)
                .map(DossierMapper::toDto));
    }

    /**
     * Miroir paginé de {@link #chargerScopees} — mêmes branches, mêmes prédicats (§1), le découpage
     * en plus. Toute évolution du périmètre doit toucher les deux, sous peine de servir une page
     * dont le contenu ne correspond plus à la liste plate.
     */
    private Page<Dossier> chargerScopeesPaginees(String statut, String type, String sousType, Pageable page) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == ProfilUtilisateur.PRESIDENT || profil == ProfilUtilisateur.ADMINISTRATEUR) {
            return repository.findParStatutPagine(statut, type, sousType, page);
        }
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().orElse(null);
            if (idPrmp == null || idPrmp.isBlank()) {
                return Page.empty(page);
            }
            return repository.findVisiblesPourPrmpPagine(idPrmp, statut, type, sousType, page);
        }
        String localite = CurrentUser.localite().orElse(null);
        if (localite == null || localite.isBlank()) {
            return Page.empty(page);
        }
        return repository.findVisiblesParLocalitePagine(localite, statut, type, sousType, page);
    }

    /**
     * ⚠️ Audit 2026-08-27 (lot D §6 — <strong>ajout de contrat</strong>) — résolution d'une saisie en
     * dossier(s), pour la barre de recherche de la topbar.
     *
     * <p>Le front téléchargeait, à chaque recherche, la liste COMPLÈTE des dossiers <em>et</em> celle
     * des PPM, puis cherchait la référence en JavaScript ({@code main-layout.ts:138}). Deux listes
     * entières transférées et parsées pour retrouver une ligne. Cet endpoint fait le travail côté
     * serveur et ne rend que l'essentiel.</p>
     *
     * <p><strong>Un seul endpoint</strong>, et non un second pour les PPM : la topbar ne cherche pas
     * des PPM, elle cherche un DOSSIER dont la référence affichée est, selon l'avancement,
     * {@code t_dossier.REFE_DOSSIER} (posée à la réception) ou celle de son PPM. La requête couvre
     * donc les deux, et le champ {@code reference} du résultat rend celle qui est affichée.</p>
     *
     * <p><strong>Périmètre</strong> : celui de {@link #findAll} — Président/Administrateur résolvent
     * tout, la PRMP (et son UGPM) seulement ses dossiers, les contrôleurs ceux de leur localité,
     * brouillons exclus. Recherche insensible à la casse, sous-chaîne, <strong>10 résultats au
     * plus</strong> (une barre de recherche n'est pas une liste), les plus récents d'abord.</p>
     *
     * @param q saisie de l'utilisateur, <strong>2 caractères minimum</strong>
     * @throws BadRequestException si {@code q} est absent ou trop court (→ 400) : sous deux
     *                             caractères, la recherche ramènerait une part arbitraire de la base
     */
    @Transactional(readOnly = true)
    public List<RechercheDossierDto> rechercher(String q) {
        String saisie = q == null ? "" : q.trim();
        if (saisie.length() < LONGUEUR_MIN_RECHERCHE) {
            throw new BadRequestException("La recherche demande au moins " + LONGUEUR_MIN_RECHERCHE
                    + " caractères.");
        }
        String motif = "%" + echapperJokers(saisie.toLowerCase(Locale.ROOT)) + "%";
        // Les plus récents d'abord : sur une barre de recherche, c'est presque toujours le bon.
        Pageable limite = PageRequest.of(0, MAX_RESULTATS_RECHERCHE, Sort.by(Sort.Direction.DESC, "idDossier"));

        List<Dossier> trouves;
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == ProfilUtilisateur.PRESIDENT || profil == ProfilUtilisateur.ADMINISTRATEUR) {
            trouves = repository.rechercherParReference(motif, limite);
        } else if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().orElse(null);
            trouves = (idPrmp == null || idPrmp.isBlank()) ? List.of()
                    : repository.rechercherParReferencePourPrmp(idPrmp, motif, limite);
        } else {
            String localite = CurrentUser.localite().orElse(null);
            trouves = (localite == null || localite.isBlank()) ? List.of()
                    : repository.rechercherParReferenceParLocalite(localite, motif, limite);
        }
        if (trouves.isEmpty()) {
            return List.of();
        }
        // Référence affichée : celle du dossier, sinon celle de son PPM — résolue EN LOT (1 requête).
        Map<Integer, String> refPpm = new HashMap<>();
        for (Ppm ppm : ppmRepository.findByIdDossierIn(trouves.stream().map(Dossier::getIdDossier).toList())) {
            if (ppm.getReference() != null && !ppm.getReference().isBlank()) {
                refPpm.putIfAbsent(ppm.getIdDossier(), ppm.getReference());
            }
        }
        return trouves.stream()
                .map(d -> new RechercheDossierDto(d.getIdDossier(), d.getRefeDossier(),
                        d.getRefeDossier() != null && !d.getRefeDossier().isBlank()
                                ? d.getRefeDossier() : refPpm.get(d.getIdDossier()),
                        d.getIdTypeDossier(), d.getStatut()))
                .toList();
    }

    /** Neutralise les jokers {@code LIKE} d'une saisie libre (voir {@code escape '!'} des requêtes). */
    private static String echapperJokers(String saisie) {
        return saisie.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    @Transactional(readOnly = true)
    public List<DossierDto> findAll(String statut, String type, String sousType) {
        String filtre = normaliserStatut(statut);
        String filtreType = normaliserType(type);
        String filtreSousType = normaliserSousType(sousType);
        return enrichir(chargerScopees(filtre).stream()
                // Filtres famille / sous-type appliqués côté serveur, après le scoping (volumes déjà réduits).
                .filter(d -> filtreType == null || filtreType.equals(d.getIdTypeDossier()))
                .filter(d -> filtreSousType == null || filtreSousType.equals(d.getIdSousType()))
                .map(DossierMapper::toDto).toList());
    }

    /**
     * Dossiers du périmètre de l'appelant (scoping §1), optionnellement restreints à un statut.
     *
     * <p>⚠️ Correctif 2026-08-26 — l'UGPM partage le périmètre de sa tutelle
     * ({@link Visibilite#estPrmp()}), cf. §3.1.</p>
     */
    private List<Dossier> chargerScopees(String filtre) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == ProfilUtilisateur.PRESIDENT || profil == ProfilUtilisateur.ADMINISTRATEUR) {
            return repository.findParStatut(filtre);
        }
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().orElse(null);
            if (idPrmp == null || idPrmp.isBlank()) {
                return List.of();
            }
            return repository.findVisiblesPourPrmpEtStatut(idPrmp, filtre);
        }
        String localite = CurrentUser.localite().orElse(null);
        if (localite == null || localite.isBlank()) {
            return List.of();
        }
        return repository.findVisiblesParLocaliteEtStatut(localite, filtre);
    }

    /** Valide le filtre famille : {@code null}/vide accepté, sinon doit exister dans {@code tr_type_dossier}. */
    private String normaliserType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String code = type.trim();
        if (!typeDossierRepository.existsById(code)) {
            throw new BadRequestException("Famille de dossier inconnue : « " + code + " » (tr_type_dossier).");
        }
        return code;
    }

    /** Valide le filtre sous-type : {@code null}/vide accepté, sinon doit exister dans {@code tr_sous_type_dossier}. */
    private String normaliserSousType(String sousType) {
        if (sousType == null || sousType.isBlank()) {
            return null;
        }
        String code = sousType.trim();
        if (!sousTypeDossierRepository.existsById(code)) {
            throw new BadRequestException(
                    "Sous-type de dossier inconnu : « " + code + " » (référentiel /api/sous-type-dossiers).");
        }
        return code;
    }

    /** Valide le filtre statut : {@code null}/vide accepté (= tous), sinon doit être un {@link StatutDossier}. */
    private String normaliserStatut(String statut) {
        if (statut == null || statut.isBlank()) {
            return null;
        }
        try {
            return StatutDossier.valueOf(statut).name();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Statut inconnu : « " + statut + " ». Valeurs admises : "
                    + java.util.Arrays.toString(StatutDossier.values()) + ".");
        }
    }

    @Transactional(readOnly = true)
    public DossierDto findById(Integer id) {
        Dossier entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + id));
        controlerVisibilite(id);
        return dto(entity);
    }

    /**
     * File « à réceptionner » (§3.4) : dossiers soumis ({@code SOUMIS}) et sans réception, de la
     * localité du contrôleur. Président/Administrateur voient toutes les localités.
     */
    @Transactional(readOnly = true)
    public List<DossierDto> aReceptionner() {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == ProfilUtilisateur.PRESIDENT || profil == ProfilUtilisateur.ADMINISTRATEUR) {
            return enrichir(repository.findAReceptionner().stream().map(DossierMapper::toDto).toList());
        }
        String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (localite == null) {
            return List.of();
        }
        return enrichir(repository.findAReceptionnerParLocalite(localite).stream().map(DossierMapper::toDto).toList());
    }

    /**
     * File « à examiner » du Membre attributaire (§2.4) : ses dossiers {@link StatutDossier#DISPATCHE}
     * (dispatchés vers lui, pas encore examinés) et {@link StatutDossier#A_REEXAMINER} (⚠️ 2026-08-02 —
     * réexamen après lettre de renvoi, pièces complémentaires transmises par la PRMP). Scopée à
     * l'utilisateur courant ({@code Dispatch.imCtrlMembre}).
     */
    @Transactional(readOnly = true)
    public List<DossierDto> aExaminer() {
        String im = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (im == null) {
            return List.of();
        }
        return enrichir(repository
                .findAExaminerParMembre(
                        List.of(StatutDossier.DISPATCHE.name(), StatutDossier.A_REEXAMINER.name()), im)
                .stream().map(DossierMapper::toDto).toList());
    }

    /**
     * Historique « examinés » du Membre attributaire : ses dossiers déjà examinés
     * ({@link StatutDossier#EXAMINE}, {@link StatutDossier#PV_SIGNE},
     * {@link StatutDossier#EN_VERIFICATION}, {@link StatutDossier#CLOTURE}), <strong>paginé</strong>.
     * Exclusif de la file « à examiner » (DISPATCHE).
     */
    @Transactional(readOnly = true)
    public Page<DossierDto> examines(Pageable pageable) {
        String im = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (im == null) {
            return Page.empty(pageable);
        }
        List<String> statuts = List.of(StatutDossier.EXAMINE.name(), StatutDossier.PV_SIGNE.name(),
                StatutDossier.EN_VERIFICATION.name(), StatutDossier.CLOTURE.name());
        return enrichir(repository.findExaminesParMembre(statuts, im, pageable).map(DossierMapper::toDto));
    }

    /** File « à vérifier » du Vérificateur (§3.6) : dossiers EN_VERIFICATION de sa localité. */
    @Transactional(readOnly = true)
    public List<DossierDto> aVerifier() {
        String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (localite == null) {
            return List.of();
        }
        return enrichir(repository.findAVerifierParLocalite(localite).stream().map(DossierMapper::toDto).toList());
    }

    /** Historique « vérifiés / clôturés » du Vérificateur (PV signés clôturés), paginé, lecture seule. */
    @Transactional(readOnly = true)
    public Page<DossierDto> verifies(Pageable pageable) {
        String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (localite == null) {
            return Page.empty(pageable);
        }
        return enrichir(repository.findVerifiesParLocalite(localite, pageable).map(DossierMapper::toDto));
    }

    /** File « En attente PRMP » du Vérificateur (lecture seule) : dossiers EN_ATTENTE_DECISION_PRMP de sa localité. */
    @Transactional(readOnly = true)
    public List<DossierDto> enAttentePrmp() {
        String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        return localite == null ? List.of()
                : enrichir(repository.findEnAttentePrmpParLocalite(localite).stream()
                        .map(DossierMapper::toDto).toList());
    }

    /**
     * Liste déroulante « dossiers retirables » de la PRMP connectée : ses dossiers dont le statut est
     * « avant PV signé » (§3.3). Utilise exactement le même ensemble de statuts que la garde de
     * {@code POST /api/demande-retraits} ({@link StatutDossier#NOMS_AVANT_PV_SIGNE}).
     */
    @Transactional(readOnly = true)
    public List<DossierDto> retirables() {
        String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (idPrmp == null) {
            return List.of();
        }
        return enrichir(repository.findRetirablesPourPrmp(idPrmp, StatutDossier.NOMS_AVANT_PV_SIGNE)
                .stream().map(DossierMapper::toDto).toList());
    }

    /**
     * Conversion d'un dossier + résolution des <strong>noms d'auteur</strong> (⚠️ demande front
     * 2026-08-19) : {@code creeParNom} / {@code soumisParNom} depuis les logins {@code CREE_PAR} /
     * {@code SOUMIS_PAR}. Sans login à résoudre, aucune requête n'est émise.
     */
    private DossierDto dto(Dossier entity) {
        DossierDto dto = DossierMapper.toDto(entity);
        return dto == null ? null : enrichir(List.of(dto)).get(0);
    }

    /**
     * Résolution des noms d'auteur <strong>en lot</strong> : trois requêtes au plus quelle que soit
     * la taille de la liste (l'annuaire regroupe les logins avant d'interroger PRMP / UGPM /
     * contrôleurs). Les DTO sont enrichis sur place ; la liste elle-même n'est pas recréée.
     */
    private List<DossierDto> enrichir(List<DossierDto> dtos) {
        Set<String> logins = new HashSet<>();
        for (DossierDto dto : dtos) {
            if (dto.getCreePar() != null) {
                logins.add(dto.getCreePar());
            }
            if (dto.getSoumisPar() != null) {
                logins.add(dto.getSoumisPar());
            }
        }
        // ⚠️ La sortie anticipée ne vaut QUE pour les noms d'auteur : un dossier sans CREE_PAR ni
        // SOUMIS_PAR n'a aucun login à résoudre, mais il a un examinateur et donc des cibles. Placer
        // l'enrichissement des cibles après ce return les rendait nulles sur tout dossier sans auteur —
        // le dossier 1 du socle de test, entre autres, et le défaut n'apparaissait qu'à la lecture
        // unitaire, la seule que le front utilise pour l'écran de vérification.
        if (!logins.isEmpty()) {
            Map<String, String> noms = acteurDirectory.nomsParLogin(logins);
            for (DossierDto dto : dtos) {
                dto.setCreeParNom(noms.get(dto.getCreePar()));
                dto.setSoumisParNom(noms.get(dto.getSoumisPar()));
            }
        }
        enrichirCibles(dtos);
        return dtos;
    }

    /**
     * ⚠️ Rattachements (2026-09-01) — pose les cibles Vérificateur et Assistant sur chaque dossier.
     *
     * <p><strong>En lot, et c'est le point de vigilance</strong> : trois requêtes au plus quelle que
     * soit la taille de la liste — les Membres examinateurs de tous les dossiers, les transmissions de
     * tous les dossiers, puis l'annuaire des contrôleurs une seule fois. Résoudre la cible dossier par
     * dossier aurait réintroduit exactement le N+1 que cette méthode avait supprimé pour les noms
     * d'auteur.</p>
     *
     * <p>Cibles nulles en repli (chaîne incomplète, examinateur P/CC du circuit court, rattaché
     * supprimé) : c'est un état normal, pas une anomalie.</p>
     */
    private void enrichirCibles(List<DossierDto> dtos) {
        List<Integer> ids = dtos.stream().map(DossierDto::getIdDossier).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        // Dernier examinateur connu par dossier (la requête est ordonnée croissant : le put final gagne).
        Map<Integer, String> examinateurs = new java.util.HashMap<>();
        for (Object[] ligne : examenRepository.findMembresParDossiers(ids)) {
            examinateurs.put((Integer) ligne[0], (String) ligne[1]);
        }
        // Vérificateur ayant effectivement transmis, par dossier (le plus récent gagne).
        Map<Integer, String> valideurs = new java.util.HashMap<>();
        for (cnm.prs.entity.TransmissionSigmp t : transmissionSigmpRepository.findByIdDossierIn(ids)) {
            if (t.getImVerificateur() != null && !t.getImVerificateur().isBlank()) {
                valideurs.put(t.getIdDossier(), t.getImVerificateur());
            }
        }
        Map<String, cnm.prs.entity.Controleur> annuaire = new java.util.HashMap<>();
        for (cnm.prs.entity.Controleur c : controleurRepository.findAll()) {
            annuaire.put(c.getImControleur(), c);
        }
        for (DossierDto dto : dtos) {
            String verificateur = rattacheDans(annuaire, examinateurs.get(dto.getIdDossier()));
            dto.setImVerificateurCible(verificateur);
            dto.setNomVerificateurCible(nomDans(annuaire, verificateur));
            String base = valideurs.get(dto.getIdDossier()) != null
                    ? valideurs.get(dto.getIdDossier()) : verificateur;
            String assistant = rattacheDans(annuaire, base);
            dto.setImAssistantCible(assistant);
            dto.setNomAssistantCible(nomDans(annuaire, assistant));
        }
        enrichirChronometrage(dtos, ids);
    }

    /**
     * ⚠️ Chronométrage (2026-09-01) — date prévisionnelle de fin, étape courante et drapeau d'attente
     * PRMP, posés <strong>en lot</strong> : deux requêtes de plus (les tâches de tous les dossiers, les
     * statuts de PV de tous les dossiers) quelle que soit la taille de la liste.
     *
     * <p>Le calcul lui-même est en mémoire — le référentiel des délais standards est relu par étape,
     * mais il ne compte que huit lignes et Hibernate le sert depuis son cache de premier niveau pour
     * toute la durée de la transaction.</p>
     */
    private void enrichirChronometrage(List<DossierDto> dtos, List<Integer> ids) {
        Map<Integer, List<cnm.prs.entity.TacheDossier>> taches = chronometrage.tachesParDossier(ids);
        Map<Integer, String> statutsPv = chronometrage.statutsPvParDossier(ids);
        // Référentiel lu UNE fois pour toute la liste, en projection scalaire : relire les délais par
        // étape et par dossier chargeait assez d'entités pour faire tomber le contrat de pagination.
        Map<cnm.prs.enums.EtapeCircuit, Integer> delais = delaiStandardService.delais();
        java.time.LocalDateTime maintenant = java.time.LocalDateTime.now();
        for (DossierDto dto : dtos) {
            String statutPv = statutsPv.get(dto.getIdDossier());
            dto.setDatePrevisionnelleFin(chronometrage.datePrevisionnelleFin(dto.getStatut(), statutPv,
                    taches.getOrDefault(dto.getIdDossier(), List.of()), maintenant, delais));
            dto.setAttentePrmp(ChronometrageService.estEnAttentePrmp(dto.getStatut()));
            cnm.prs.enums.EtapeCircuit etape = chronometrage.etapeCourante(dto.getStatut(), statutPv);
            dto.setEtapeCourante(etape == null ? null : etape.name());
        }
    }

    /** Rattaché d'un porteur dans l'annuaire déjà chargé ; {@code null} si absent ou disparu. */
    private static String rattacheDans(Map<String, cnm.prs.entity.Controleur> annuaire, String imPorteur) {
        cnm.prs.entity.Controleur porteur = imPorteur == null ? null : annuaire.get(imPorteur);
        String rattache = porteur == null ? null : porteur.getImRattache();
        return rattache != null && !rattache.isBlank() && annuaire.containsKey(rattache) ? rattache : null;
    }

    private static String nomDans(Map<String, cnm.prs.entity.Controleur> annuaire, String im) {
        cnm.prs.entity.Controleur c = im == null ? null : annuaire.get(im);
        if (c == null) {
            return null;
        }
        String n = ((c.getPrenomsCont() == null ? "" : c.getPrenomsCont()) + " "
                + (c.getNomCont() == null ? "" : c.getNomCont())).trim();
        return n.isBlank() ? im : n;
    }

    /** Même enrichissement sur une page (le contenu est enrichi sur place, la page est renvoyée telle quelle). */
    private Page<DossierDto> enrichir(Page<DossierDto> page) {
        enrichir(page.getContent());
        return page;
    }

    /**
     * Vérifie que le dossier est dans le périmètre de visibilité de l'utilisateur (§1).
     *
     * <p>⚠️ Correctif 2026-08-26 — l'UGPM partage le périmètre de sa tutelle
     * ({@link Visibilite#estPrmp()}), cf. §3.1.</p>
     */
    private void controlerVisibilite(Integer idDossier) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == ProfilUtilisateur.PRESIDENT || profil == ProfilUtilisateur.ADMINISTRATEUR) {
            return;
        }
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().orElse(null);
            if (idPrmp != null && !idPrmp.isBlank() && repository.existsVisiblePourPrmp(idDossier, idPrmp)) {
                return;
            }
            throw new AccessDeniedException("Dossier hors de votre périmètre de visibilité (§1).");
        }
        String localite = CurrentUser.localite().orElse(null);
        if (localite == null || localite.isBlank() || !repository.existsDansLocalite(idDossier, localite)) {
            throw new AccessDeniedException("Dossier hors de votre périmètre de visibilité (§1).");
        }
    }

    public DossierDto create(DossierDto dto) {
        Dossier entity = DossierMapper.toEntity(dto);
        entity.setIdDossier(repository.nextIdDossier().intValue()); // ⚠️ PK serveur (séquence) ; id client ignoré
        return dto(repository.save(entity));
    }

    /**
     * PUT générique du dossier (Administrateur).
     *
     * <p>⚠️ Audit 2026-08-27 (lot B) — le statut du corps était recopié <strong>tel quel</strong> :
     * une valeur hors de {@link StatutDossier} (« RECU », une faute de frappe…) s'installait en base
     * et rendait le dossier invisible de toutes les files, qui filtrent sur des noms de constantes.
     * La valeur est désormais validée contre l'énumération (400 sinon), et un changement de statut
     * par cette porte est <strong>journalisé</strong> {@code [CIRCUIT]} comme toutes les autres
     * transitions — c'est la seule qui n'obéit à aucune règle métier, elle mérite d'autant plus sa
     * ligne de journal.</p>
     */
    public DossierDto update(Integer id, DossierDto dto) {
        Dossier existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + id));
        // ⚠️ Verrou optimiste HTTP (plan §3) : version périmée → 409 CONFLIT_VERSION, avant toute écriture.
        VerrouOptimiste.exigerVersionCourante(dto.getVersion(), existing.getVersion());
        exigerStatutConnu(dto.getStatut());
        String statutPrecedent = existing.getStatut();
        existing.setIdTypeDossier(dto.getIdTypeDossier());
        existing.setIdDossierParent(dto.getIdDossierParent());
        existing.setRefeDossier(dto.getRefeDossier());
        existing.setDateRef(dto.getDateRef());
        existing.setStatut(dto.getStatut());
        if (dto.getStatut() != null && !dto.getStatut().equals(statutPrecedent)) {
            log.info("[CIRCUIT] statut force (PUT administrateur) dossier={} acteur={} statutPrecedent={} statut={}",
                    id, CurrentUser.login().orElse(null), statutPrecedent, dto.getStatut());
        }
        existing.setIdLocalite(dto.getIdLocalite());
        existing.setIdEntiteContract(dto.getIdEntiteContract());
        // ⚠️ saveAndFlush : l'incrément de @Version se fait au flush — sans lui la réponse rendrait
        // l'ancienne version et le client re-conflicterait au PUT suivant (cf. plan §4).
        return dto(repository.saveAndFlush(existing));
    }

    /**
     * ⚠️ Audit 2026-08-27 (lot B) — le statut fourni au {@code PUT} doit être une constante de
     * {@link StatutDossier} : les files, compteurs et gardes du circuit comparent des noms de
     * constantes, une valeur inconnue rendrait le dossier introuvable partout. {@code null} est
     * toléré (champ non renseigné, comportement historique du PUT).
     *
     * @throws ChampsInvalidesException (→ 400 ciblé {@code statut}) valeur hors énumération
     */
    private void exigerStatutConnu(String statut) {
        if (statut == null || statut.isBlank()) {
            return;
        }
        boolean connu = java.util.Arrays.stream(StatutDossier.values())
                .anyMatch(s -> s.name().equals(statut));
        if (!connu) {
            throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError("statut",
                    "Statut de dossier inconnu : « " + statut + " ».")));
        }
    }

    /**
     * ⚠️ Règle ajoutée — suppression d'un dossier depuis « Mes brouillons » (PRMP propriétaire).
     * Un dossier <strong>{@code BROUILLON}</strong> est <strong>toujours supprimable</strong> (même revenu en
     * brouillon après un circuit incomplet via retrait), sinon <strong>409</strong>. Cascade complète en une
     * transaction : <strong>pièces et traces du dossier</strong> → <strong>contenu</strong> (prévisions →
     * marchés → PPM) → <strong>historique de circuit</strong> → dossier. Le <strong>journal d'audit</strong>
     * ({@code t_audit_log}, immuable §3.8, sans FK) est <strong>conservé</strong>.
     *
     * <p>⚠️ Audit 2026-08-27 (lot D §2) — la fermeture était <strong>incomplète</strong> sur deux plans.
     * <em>Orphelins garantis</em> : {@code t_piece_jointe_dossier} (avec son {@code CONTENU} binaire) et
     * {@code t_changement_ligne} survivaient à chaque suppression, sans FK pour le signaler ni écran pour
     * les retrouver. <em>Violations de FK</em> : {@code t_message.ID_DOSSIER}, {@code t_anomalie.ID_DETAIL}
     * et {@code t_echeance.ID_DETAIL} n'étaient jamais purgées — la suppression échouait alors en 409
     * « violation de clé étrangère » dès que le brouillon avait réellement circulé, ce que le retrait
     * accepté rend possible (l'hypothèse « un brouillon n'a jamais dépassé {@code PRET_DISPATCH} » est
     * périmée). L'historique de circuit passe désormais par {@link CircuitCascadeService#purgerCircuit},
     * qui applique l'ordre FK-safe déjà documenté et couvre dispatchs, examens, PV, lettres et copies —
     * pas seulement les réceptions.</p>
     */
    public void delete(Integer id) {
        Dossier dossier = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + id)); // 404
        dossierIntegrite.exigerOperateurHabilite(dossier);                                         // 403 / 409 vacance
        if (!StatutDossier.BROUILLON.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException("Ce dossier ne peut pas être supprimé.");              // 409
        }
        // 1. Pièces jointes et trace de diff du dossier (orphelins garantis avant le lot D).
        pieceJointeDossierRepository.deleteByIdDossier(id);
        changementLigneRepository.deleteByIdDossier(id);
        // 2. Historique de circuit COMPLET, dans l'ordre FK-safe du service dédié (observations, examens,
        //    PV, navettes, vérifications, lettres, copies, dispatchs, réceptions). Un brouillon revenu de
        //    circuit par retrait accepté en porte encore ; un brouillon jamais soumis n'en a aucun (0 ligne).
        circuitCascadeService.purgerCircuit(id);
        // 3. Contenu : sous-lignes de chaque marché (tranches, lots, bénéficiaires, prévisions, DMC,
        //    anomalies, échéances) → marchés → anomalies du PPM → PPM(s).
        List<Marche> marches = marcheRepository.findByIdDossier(id);
        for (Marche m : marches) {
            marcheService.supprimerSousLignes(m.getIdDetail());   // cascade partagée (inclut t_lot) — pas d'orphelin FK
        }
        marcheRepository.deleteAll(marches);
        for (Ppm ppm : ppmRepository.findByIdDossier(id)) {
            anomalieRepository.deleteByIdPpm(ppm.getIdPpm());     // t_anomalie.ID_PPM → t_ppm
            ppmRepository.delete(ppm);
        }
        // 4. Balayage de fermeture des dernières FK vers t_dossier : lots (2ᵉ FK), messages,
        //    notifications, demandes de retrait.
        lotRepository.deleteByIdDossier(id);
        messageRepository.deleteByIdDossier(id);
        notificationRepository.deleteByIdDossier(id);
        pieceDemandeRetraitRepository.deleteParDossier(id);   // PDF des demandes (sans FK) — avant les demandes
        demandeRetraitRepository.deleteByIdDossier(id);
        journalDossier.purger(id);   // le dossier disparaît : son journal d'actions n'a plus d'objet
        repository.deleteById(id);
    }

    /**
     * Soumission officielle d'un dossier par la PRMP (§3.1, Module 03). <strong>Génère la
     * référence unique</strong> {@code REFE_DOSSIER} puis notifie le Secrétaire et le Chef de
     * commission de la localité qu'un dossier est en attente de réception.
     *
     * <p><strong>Localité</strong> : celle du dossier (dérivée de l'entité contractante choisie à la
     * saisie, §1), sinon celle de son PPM ({@code Ppm.idLocalite}) ; il n'y a plus de repli sur une
     * localité « propre » de la PRMP (la PRMP n'en a pas). <strong>Appartenance</strong> : si le dossier est rattaché à
     * un PPM, il doit appartenir à la PRMP courante (sinon 403) ; un dossier sans aucun PPM n'a pas
     * de lien d'appartenance en base et est soumis par la PRMP authentifiée. L'exercice de la
     * référence provient du PPM, ou de l'année courante à défaut.</p>
     *
     * @throws ResourceNotFoundException si le dossier n'existe pas
     * @throws AccessDeniedException     si le dossier (rattaché à un PPM) n'appartient pas à la PRMP courante
     * @throws BusinessRuleException     si le dossier a déjà été soumis (référence déjà générée) → 409
     * @throws BadRequestException       si aucune localité ne peut être déterminée (ni dossier, ni PPM) → 400
     */
    public DossierDto soumettre(Integer idDossier) {
        Dossier dossier = repository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));

        CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Utilisateur PRMP non identifié."));
        // Propriété (PRMP d'attribution OU PRMP en fonction) + habilitation : la soumission est l'acte de
        // signature de la PRMP — sans mandat actif, elle attend la nomination (409 VACANCE_PRMP).
        dossierIntegrite.exigerOperateurHabilite(dossier);
        // Cycle de vie : seul un BROUILLON est soumissible → SOUMIS (pas de re-soumission).
        if (!StatutDossier.BROUILLON.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException(
                    "Dossier non soumissible : statut « " + dossier.getStatut() + " » (attendu BROUILLON).");
        }
        // Cohérence type↔contenu (DDP ⇒ a un PPM ; DMC/DDM ⇒ pas de PPM).
        dossierIntegrite.validerCoherenceAvantSoumission(dossier);
        // Filet de sécurité : le sous-type d'un dossier DDP colle aux marchés au moment de la soumission.
        dossierIntegrite.recalculerSousTypeDdp(idDossier);
        // Pièces jointes obligatoires de la famille de dossier (référentiel) : toutes doivent être présentes.
        validerPiecesObligatoires(dossier);
        // ⚠️ 2026-08-05 — une MISE À JOUR exige en plus le PV du prédécesseur et le PPM daté et signé des
        // versions antérieures. Sans effet sur un dossier initial.
        miseAJourPpmService.exigerDossierHistorique(dossier);

        // Localité : celle du dossier (dérivée de l'entité à la saisie), sinon celle du PPM. Plus de repli PRMP.
        List<Ppm> ppms = ppmRepository.findByIdDossier(idDossier);
        String localite = dossier.getIdLocalite();
        if (localite == null || localite.isBlank()) {
            localite = ppms.stream().map(Ppm::getIdLocalite).filter(l -> l != null && !l.isBlank()).findFirst()
                    .orElse(null);
        }
        if (localite == null) {
            throw new BadRequestException(
                    "Localité indéterminée : elle provient de l'entité contractante choisie à la saisie (§1, §3.1).");
        }
        // (Règle ajoutée) Format CNM-{localité}-{exercice}-{idDossier} ABANDONNÉ. La soumission ne génère
        // plus de référence : refeDossier reste null jusqu'à la réception, qui pose la réf. officielle
        // structurée (xxxxx/type/localité/année).
        dossier.setIdLocalite(localite);             // propage la localité (§C) → visible par le Secrétaire
        dossier.setStatut(StatutDossier.SOUMIS.name());
        dossier.setSoumisPar(CurrentUser.login().orElse(null));   // traçabilité : soumission réservée à la PRMP
        if (dossier.getDateRef() == null) {
            dossier.setDateRef(LocalDate.now());
        }
        repository.save(dossier);
        log.info("[CIRCUIT] soumission dossier={} acteur={} localite={}",
                dossier.getIdDossier(), CurrentUser.login().orElse(null), localite);

        // ⚠️ 2026-08-05 — mise à jour de PPM : c'est ICI, et pas à la création du brouillon, que la
        // nouvelle version devient opposable. On fige la trace du diff et on bascule le prédécesseur en
        // REMPLACE. Sans effet si le dossier n'est pas une mise à jour (pas de dossier parent).
        miseAJourPpmService.figerDiffEtRemplacerParent(idDossier);

        notifierSoumission(dossier, localite);
        journalDossier.tracer(dossier, JournalDossierService.SOUMISSION, "BROUILLON -> SOUMIS");
        return dto(dossier);
    }

    /**
     * Contrôle, à la soumission, que toutes les pièces jointes marquées {@code obligatoire} pour le
     * type du dossier (référentiel {@code t_type_piece_jointe}) sont effectivement attachées
     * ({@code t_piece_jointe_dossier}). Sinon → 400 {@code erreurs:[{champ:"piecesJointes", message}]}.
     */
    private void validerPiecesObligatoires(Dossier dossier) {
        List<ErrorResponse.FieldError> manquantes = new ArrayList<>(typePieceJointeRepository
                .findByIdTypeDossierAndObligatoireTrue(dossier.getIdTypeDossier()).stream()
                .filter(t -> !pieceJointeDossierRepository
                        .existsByIdDossierAndIdTypePiece(dossier.getIdDossier(), t.getIdTypePiece()))
                .map(t -> new ErrorResponse.FieldError("piecesJointes",
                        "La pièce '" + t.getLibellePiece() + "' est obligatoire."))
                .toList());
        // Obligation CONDITIONNELLE de l'AGPM (cas « PPM-AGPM ») : ajoutée au même contrôle de complétude.
        ajouterAgpmManquantSiRequis(dossier, manquantes);
        if (!manquantes.isEmpty()) {
            throw new ChampsInvalidesException(manquantes);
        }
    }

    /**
     * ⚠️ Règle ajoutée — obligation <strong>conditionnelle</strong> de l'AGPM : un dossier de la famille
     * {@code DDP} comportant au moins un marché en « appel d'offres ouvert » ({@code ModePassation.declencheAgpm},
     * soit un sous-type dérivé {@code PPM-AGPM}) doit être accompagné de la pièce AGPM (Avis Général de
     * Passation de Marché). Cette obligation ne peut être portée par le drapeau statique {@code OBLIGATOIRE}
     * du référentiel : elle est évaluée ici. La pièce est repérée par son <strong>code</strong> stable
     * {@code AGPM} (famille DDP). Sans effet si l'AGPM n'est pas requis, ou si le référentiel ne définit
     * pas encore la pièce (config admin).
     */
    private void ajouterAgpmManquantSiRequis(Dossier dossier, List<ErrorResponse.FieldError> manquantes) {
        if (!FAMILLE_DDP.equals(dossier.getIdTypeDossier())
                || !marcheRepository.existsMarcheDeclencheurAgpmByDossier(dossier.getIdDossier())) {
            return;
        }
        typePieceJointeRepository.findFirstByIdTypeDossierAndCode(FAMILLE_DDP, CODE_PIECE_AGPM)
                .filter(t -> !pieceJointeDossierRepository
                        .existsByIdDossierAndIdTypePiece(dossier.getIdDossier(), t.getIdTypePiece()))
                .ifPresent(t -> manquantes.add(new ErrorResponse.FieldError("piecesJointes",
                        "La pièce « AGPM » (Avis Général de Passation de Marché) est obligatoire lorsque le "
                                + "PPM comporte au moins un marché en appel d'offres ouvert.")));
    }

    /** Notifie le Secrétaire et le CC de la localité qu'un dossier est soumis et attend réception. */
    private void notifierSoumission(Dossier dossier, String localite) {
        String titre = "Nouveau dossier soumis à réceptionner";
        String corps = "Le dossier " + dossier.getIdDossier()
                + " a été soumis et attend sa réception dans la localité " + localite + ".";
        for (Controleur sec : controleurDirectory.secretaires(localite)) {
            notificationService.emettre(dossier.getIdDossier(), TypeNotification.DOSSIER_SOUMIS,
                    sec.getImControleur(), sec.getEmailCont(), titre, corps);
        }
        for (Controleur cc : controleurDirectory.chefsCommission(localite)) {
            notificationService.emettre(dossier.getIdDossier(), TypeNotification.DOSSIER_SOUMIS,
                    cc.getImControleur(), cc.getEmailCont(), titre, corps);
        }
    }

    /**
     * ⚠️ Règle ajoutée — resoumission par la PRMP d'un dossier {@code EN_ATTENTE_DECISION_PRMP} après
     * rectification. Motif obligatoire (sinon 400) ; transition → {@code EN_VERIFICATION} ; notifie le
     * vérificateur du dossier ; trace l'événement dans {@code t_audit_log} ; enregistre le motif sur la
     * dernière vérification (passage) pour qu'il soit visible côté vérificateur.
     */
    public DossierDto resoumettre(Integer idDossier, String motifRectification) {
        if (motifRectification == null || motifRectification.isBlank()) {
            throw new BadRequestException("Le motif de rectification est obligatoire.");
        }
        Dossier dossier = repository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Utilisateur PRMP non identifié."));
        dossierIntegrite.exigerOperateurHabilite(dossier);
        if (!StatutDossier.EN_ATTENTE_DECISION_PRMP.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException(
                    "Resoumission impossible : le dossier n'est pas en attente de décision PRMP (statut « "
                            + dossier.getStatut() + " »).");
        }
        dossier.setStatut(StatutDossier.EN_VERIFICATION.name());
        // ⚠️ Chronométrage (2026-09-01) — sortie d'attente PRMP : le compteur net CNM redémarre.
        chronometrage.sortirDAttentePrmp(idDossier);
        repository.save(dossier);
        log.info("[CIRCUIT] resoumission apres decision PRMP dossier={} acteur={} statut={}",
                idDossier, CurrentUser.login().orElse(null), StatutDossier.EN_VERIFICATION.name());

        // Dernière vérification (le passage obsLevees=false qui a déclenché l'attente).
        Verification derniere = verificationRepository.findPassagesDuDossier(idDossier).stream()
                .findFirst().orElse(null);
        if (derniere != null) {
            derniere.setMotifRectif(motifRectification);   // visible dans les passages côté vérificateur
            verificationRepository.save(derniere);
        }
        notifierRectification(dossier, derniere, idPrmp, motifRectification);
        tracerRectification(dossier, idPrmp, motifRectification);
        journalDossier.tracer(dossier, JournalDossierService.RESOUMISSION, motifRectification);
        return dto(dossier);
    }

    /**
     * ⚠️ Spec recevabilité au dépôt (2026-08-02) — le SECRÉTAIRE signale à la PRMP les pièces manquantes /
     * non conformes relevées par son contrôle de complétude ({@code t_verification_piece_depot}) :
     * dossier {@code SOUMIS} → {@code EN_ATTENTE_COMPLEMENTS_DEPOT} (non enregistrable), notification
     * {@code PIECES_MANQUANTES_DEPOT} à la PRMP reprenant la liste des défauts + observations, événement
     * tracé dans {@code t_audit_log}. AUCUN circuit d'archivage (objet distinct de la lettre de renvoi).
     */
    public DossierDto signalerPiecesManquantes(Integer idDossier) {
        Dossier dossier = repository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        if (!StatutDossier.SOUMIS.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException("Signalement impossible : le dossier n'est pas au dépôt (statut « "
                    + dossier.getStatut() + " »).");
        }
        List<String> defauts = verificationPieceDepotService.defautsCourants(idDossier);
        if (defauts.isEmpty()) {
            throw new BusinessRuleException("Aucune pièce manquante ou non conforme relevée : rien à signaler.");
        }
        dossier.setStatut(StatutDossier.EN_ATTENTE_COMPLEMENTS_DEPOT.name());
        // ⚠️ Chronométrage (2026-09-01) — incomplétude au dépôt : la balle repasse à la PRMP.
        chronometrage.entrerEnAttentePrmp(idDossier, StatutDossier.EN_ATTENTE_COMPLEMENTS_DEPOT);
        repository.save(dossier);
        log.info("[CIRCUIT] defauts de depot signales dossier={} acteur={} nbDefauts={}",
                idDossier, CurrentUser.login().orElse(null), defauts.size());

        String ref = dossier.getRefeDossier() != null ? dossier.getRefeDossier() : ("n° " + dossier.getIdDossier());
        String titre = "Pièces manquantes ou non conformes au dépôt";
        String corps = "Dossier " + ref + " — le contrôle de complétude du Secrétaire a relevé : "
                + String.join(" ; ", defauts)
                + ". Déposez les pièces demandées puis transmettez les compléments.";
        for (Ppm ppm : ppmRepository.findByIdDossier(idDossier)) {
            if (ppm.getIdPrmp() != null) {
                String email = prmpRepository.findById(ppm.getIdPrmp()).map(Prmp::getEmailPrmp).orElse(null);
                notificationService.emettrePrmp(TypeNotification.PIECES_MANQUANTES_DEPOT, ppm.getIdPrmp(), email,
                        idDossier, TypeObjet.DOSSIER, idDossier, titre, corps);
            }
        }
        tracerEvenementDossier(dossier, "PIECES_MANQ_DEP", String.join(" ; ", defauts));
        return dto(dossier);
    }

    /**
     * ⚠️ Spec recevabilité au dépôt (2026-08-02) — la PRMP transmet les compléments du DÉPÔT :
     * {@code EN_ATTENTE_COMPLEMENTS_DEPOT} → {@code SOUMIS} ; le Secrétaire reprend le contrôle sur les
     * seules pièces en défaut (les décisions CONFORME restent acquises). Notifie les Secrétaires de la
     * localité ({@code COMPLEMENTS_DEPOT_TRANSMIS}).
     */
    public DossierDto transmettreComplementsDepot(Integer idDossier) {
        Dossier dossier = repository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        dossierIntegrite.exigerOperateurHabilite(dossier);
        if (!StatutDossier.EN_ATTENTE_COMPLEMENTS_DEPOT.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException(
                    "Transmission impossible : le dossier n'est pas en attente de compléments au dépôt (statut « "
                            + dossier.getStatut() + " »).");
        }
        dossier.setStatut(StatutDossier.SOUMIS.name());
        // ⚠️ Chronométrage (2026-09-01) — sortie d'attente PRMP : le compteur net CNM redémarre.
        chronometrage.sortirDAttentePrmp(idDossier);
        repository.save(dossier);
        log.info("[CIRCUIT] complements de depot transmis dossier={} acteur={} statut={}",
                idDossier, CurrentUser.login().orElse(null), StatutDossier.SOUMIS.name());
        String ref = dossier.getRefeDossier() != null ? dossier.getRefeDossier() : ("n° " + dossier.getIdDossier());
        String titre = "Compléments de dépôt transmis";
        String corps = "Dossier " + ref + " — la PRMP a transmis les pièces demandées : reprenez le contrôle de "
                + "complétude (les pièces déjà conformes restent acquises).";
        if (dossier.getIdLocalite() != null) {
            for (Controleur s : controleurDirectory.secretaires(dossier.getIdLocalite())) {
                notificationService.emettre(idDossier, TypeNotification.COMPLEMENTS_DEPOT_TRANSMIS,
                        s.getImControleur(), s.getEmailCont(), titre, corps);
            }
        }
        tracerEvenementDossier(dossier, "COMPL_DEPOT", "Compléments transmis par la PRMP");
        journalDossier.tracer(dossier, JournalDossierService.TRANSMISSION_COMPLEMENTS_DEPOT,
                "EN_ATTENTE_COMPLEMENTS_DEPOT -> SOUMIS");
        return dto(dossier);
    }

    /** Trace un événement du dossier dans {@code t_audit_log} (TYPE_ACTION court, détail en NOUVELLE_VALEUR). */
    private void tracerEvenementDossier(Dossier dossier, String typeAction, String detail) {
        AuditLog log = new AuditLog();
        log.setIdLog(auditLogRepository.nextIdAuditLog());   // PK serveur (sequence)
        log.setDateAction(LocalDateTime.now());
        log.setImActeur(CurrentUser.ref().orElse(null));
        log.setNomTable("t_dossier");
        log.setIdEnregistrement(String.valueOf(dossier.getIdDossier()));
        log.setTypeAction(typeAction);
        log.setChampModifie("STATUT");
        log.setNouvelleValeur(detail);
        auditLogRepository.save(log);
    }

    /**
     * ⚠️ Spec navette (2026-08-01, cas 3) — la PRMP transmet les COMPLÉMENTS demandés par la lettre de
     * renvoi (pièces déposées avec {@code apresLettreRenvoi=true} au préalable).
     *
     * <p>⚠️ Règle MODIFIÉE (2026-08-02, réexamen après lettre de renvoi) : le dossier suspendu
     * ({@code EN_ATTENTE_PIECES}) passe {@code A_REEXAMINER} (plus {@code EXAMINE}) — il revient dans
     * la file « à examiner » du Membre attributaire pour <strong>réexamen</strong> à la lumière des
     * pièces reçues (mêmes dispatch/examen/PV, brouillon serveur conservé). La transmission est
     * refusée (409) tant qu'aucune pièce n'a été déposée pour la lettre de renvoi du cycle courant :
     * le réexamen n'a lieu qu'une fois les pièces nécessaires présentes. Notifie le(s) Membre(s)
     * attributaire(s) ({@code COMPLEMENTS_TRANSMIS}).</p>
     */
    public DossierDto transmettreComplements(Integer idDossier) {
        Dossier dossier = repository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        dossierIntegrite.exigerOperateurHabilite(dossier);
        if (!StatutDossier.EN_ATTENTE_PIECES.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException(
                    "Transmission des compléments impossible : le dossier n'est pas en attente de pièces (statut « "
                            + dossier.getStatut() + " »).");
        }
        Integer idLettre = lettreRenvoiRepository
                .findFirstByIdDossierAndStatutOrderByIdLettreDesc(idDossier, StatutLettreRenvoi.SIGNE.name())
                .map(LettreRenvoi::getIdLettre).orElse(null);
        if (idLettre == null || !pieceJointeDossierRepository
                .existsByIdDossierAndIdLettreAndApresLettreRenvoiTrue(idDossier, idLettre)) {
            throw new BusinessRuleException(
                    "Transmission impossible : aucune pièce complémentaire n'a été déposée pour la lettre de "
                            + "renvoi. Déposez d'abord les pièces demandées — le réexamen du dossier ne peut avoir "
                            + "lieu qu'une fois les pièces nécessaires présentes.");
        }
        dossier.setStatut(StatutDossier.A_REEXAMINER.name());
        // ⚠️ Chronométrage (2026-09-01) — sortie d'attente PRMP : le compteur net CNM redémarre.
        chronometrage.sortirDAttentePrmp(idDossier);
        repository.save(dossier);
        log.info("[CIRCUIT] complements de renvoi transmis dossier={} acteur={} lettre={}",
                idDossier, CurrentUser.login().orElse(null), idLettre);
        String ref = dossier.getRefeDossier() != null ? dossier.getRefeDossier() : ("n° " + dossier.getIdDossier());
        String titre = "Compléments transmis — dossier à réexaminer";
        String corps = "La PRMP a transmis les pièces / informations demandées par la lettre de renvoi pour le dossier "
                + ref + ". Le dossier est de retour dans votre file « à examiner » : réexaminez-le à la lumière des "
                + "pièces reçues puis soumettez de nouveau le projet de PV.";
        for (String im : repository.findMembresAttributaires(idDossier)) {
            notificationService.emettre(idDossier, TypeNotification.COMPLEMENTS_TRANSMIS, im, null, titre, corps);
        }
        tracerEvenementDossier(dossier, "COMPL_TRANSMIS", "EN_ATTENTE_PIECES -> A_REEXAMINER (lettre " + idLettre + ")");
        journalDossier.tracer(dossier, JournalDossierService.TRANSMISSION_COMPLEMENTS,
                "EN_ATTENTE_PIECES -> A_REEXAMINER (lettre " + idLettre + ")");
        return dto(dossier);
    }

    /** Notifie le vérificateur du dossier (dernier vérificateur ; sinon les vérificateurs de la localité). */
    private void notifierRectification(Dossier dossier, Verification derniere, String idPrmp, String motif) {
        String nomPrmp = prmpRepository.findById(idPrmp)
                .map(p -> ((p.getPrenomsPrmp() == null ? "" : p.getPrenomsPrmp() + " ")
                        + (p.getNomPrmp() == null ? "" : p.getNomPrmp())).trim())
                .filter(s -> !s.isBlank()).orElse(idPrmp);
        String ref = dossier.getRefeDossier() != null ? dossier.getRefeDossier() : ("n° " + dossier.getIdDossier());
        String titre = "Dossier rectifié par la PRMP — à re-vérifier";
        String corps = "Dossier " + ref + " — la PRMP " + nomPrmp + " a rectifié le dossier le "
                + LocalDate.now() + ". Motif : " + motif + ". Le dossier revient en vérification.";
        String imVerif = derniere == null ? null : derniere.getImCtrlVerif();
        if (imVerif != null && !imVerif.isBlank()) {
            notificationService.emettre(dossier.getIdDossier(), TypeNotification.RECTIFICATION_PRMP,
                    imVerif, null, titre, corps);
        } else {
            for (Controleur v : controleurDirectory.verificateurs(dossier.getIdLocalite())) {
                notificationService.emettre(dossier.getIdDossier(), TypeNotification.RECTIFICATION_PRMP,
                        v.getImControleur(), v.getEmailCont(), titre, corps);
            }
        }
    }

    /** Trace la rectification dans {@code t_audit_log} (NOM_TABLE=t_dossier, TYPE_ACTION=RECTIFICATION_PRMP). */
    private void tracerRectification(Dossier dossier, String idPrmp, String motif) {
        AuditLog log = new AuditLog();
        log.setIdLog(auditLogRepository.nextIdAuditLog());   // PK serveur (sequence)
        log.setDateAction(LocalDateTime.now());
        log.setImActeur(idPrmp);                          // <id PRMP>
        log.setNomTable("t_dossier");
        log.setIdEnregistrement(String.valueOf(dossier.getIdDossier()));
        log.setTypeAction("RECTIFICATION_PRMP");
        log.setChampModifie("motifRectification");
        log.setNouvelleValeur(motif);
        auditLogRepository.save(log);
    }

    /**
     * ⚠️ Règle ajoutée — historique complet des échanges d'un dossier <strong>clôturé</strong> (§3.6),
     * trié date ASC : observations du vérificateur (t_verification, dont le passage final obsLevees=true)
     * + rectifications de la PRMP (t_audit_log). Accès PRMP / Vérificateur / Admin (rôle au contrôleur) ;
     * 403 si le dossier n'est pas {@code CLOTURE}.
     *
     * <p>⚠️ Audit 2026-08-27 (§3.1 du rapport) — le rôle était contrôlé par le {@code @PreAuthorize} du
     * contrôleur, mais <strong>pas le périmètre</strong> : n'importe quelle PRMP lisait les observations
     * et les rectifications d'un dossier clôturé d'autrui, n'importe quel vérificateur celles d'une autre
     * localité. {@link #controlerVisibilite} est appliqué <strong>avant</strong> la garde de clôture, pour
     * ne rien divulguer (pas même le statut) hors périmètre.</p>
     */
    @Transactional(readOnly = true)
    public List<EchangeDto> historiqueEchanges(Integer idDossier) {
        Dossier dossier = repository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        controlerVisibilite(idDossier);
        if (!StatutDossier.CLOTURE.name().equals(dossier.getStatut())) {
            throw new AccessDeniedException("Historique disponible uniquement pour un dossier clôturé.");
        }
        // Vérifications (passages) par ordre de création croissant (la requête renvoie DESC).
        List<Verification> passages = verificationRepository.findPassagesDuDossier(idDossier).stream()
                .sorted(Comparator.comparing(Verification::getIdVerification))
                .toList();
        // Rectifications PRMP par horodatage croissant → file de réponse.
        Deque<AuditLog> rectifications = new ArrayDeque<>(
                auditLogRepository.findRectificationsDossier(String.valueOf(idDossier)));

        // ⚠️ Fil entrelacé par chaîne de réponse : chaque vérification, puis (si elle porte un motif) la
        // rectification PRMP qui lui répond — la k-ᵉ vérification motivée ↔ la k-ᵉ rectification.
        List<EchangeDto> echanges = new ArrayList<>();
        for (Verification v : passages) {
            echanges.add(new EchangeDto("OBSERVATION",
                    v.getDateVerif() == null ? null : v.getDateVerif().toString(),
                    v.getImCtrlVerif(), v.getObservation(), v.getObsLevees()));
            if (v.getMotifRectif() != null && !v.getMotifRectif().isBlank() && !rectifications.isEmpty()) {
                echanges.add(toRectificationEchange(rectifications.poll()));
            }
        }
        // Sécurité : rectifications restantes (appariement imparfait sur données anciennes) en fin.
        while (!rectifications.isEmpty()) {
            echanges.add(toRectificationEchange(rectifications.poll()));
        }
        return echanges;
    }

    private EchangeDto toRectificationEchange(AuditLog a) {
        return new EchangeDto("RECTIFICATION",
                a.getDateAction() == null ? null : a.getDateAction().toString(),
                a.getImActeur(), a.getNouvelleValeur(), null);
    }

    /**
     * ⚠️ Règle ajoutée (spec « Mandats PRMP ») — journal des actions d'un dossier, chronologique : qui a
     * agi, quand, et <strong>sous quel mandat</strong>. Distinct du journal d'audit technique
     * ({@code t_audit_log}, réservé à l'Administrateur) : celui-ci est lisible par les profils concernés,
     * sous le même périmètre de visibilité que le dossier lui-même (§1).
     *
     * <p>Après un changement de PRMP, l'opérateur des lignes récentes diffère de la PRMP d'attribution du
     * dossier ({@code idPrmp} / {@code idMandatAttrib}, inchangés) : c'est là que la reprise de traitement
     * se lit.</p>
     */
    @Transactional(readOnly = true)
    public List<ActionDossierDto> journal(Integer idDossier) {
        if (!repository.existsById(idDossier)) {
            throw new ResourceNotFoundException("Dossier introuvable : " + idDossier);
        }
        controlerVisibilite(idDossier);
        return journalDossier.journal(idDossier);
    }
}
