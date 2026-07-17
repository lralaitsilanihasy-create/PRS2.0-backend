package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DossierDto;
import cnm.prs.dto.EchangeDto;
import cnm.prs.entity.AuditLog;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.Verification;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.TypeNotification;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.DossierMapper;
import cnm.prs.repository.AuditLogRepository;
import cnm.prs.repository.DemandeRetraitRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.NotificationRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.repository.SousTypeDossierRepository;
import cnm.prs.repository.TypeDossierRepository;
import cnm.prs.repository.TypePieceJointeRepository;
import cnm.prs.repository.VerificationRepository;
import cnm.prs.security.CurrentUser;

/**
 * Logique métier pour {@link Dossier}.
 */
@Service
@Transactional
public class DossierService {

    /** Famille « Dossier de Planification » (codes centralisés dans {@link DossierIntegriteService}). */
    private static final String FAMILLE_DDP = DossierIntegriteService.FAMILLE_DDP;
    /** Code du type de pièce AGPM dans le référentiel {@code t_type_piece_jointe} (obligation conditionnelle). */
    private static final String CODE_PIECE_AGPM = "AGPM";

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
    private final ReceptionRepository receptionRepository;
    private final DemandeRetraitRepository demandeRetraitRepository;
    private final NotificationRepository notificationRepository;
    private final TypePieceJointeRepository typePieceJointeRepository;
    private final PieceJointeDossierRepository pieceJointeDossierRepository;
    private final TypeDossierRepository typeDossierRepository;
    private final SousTypeDossierRepository sousTypeDossierRepository;

    public DossierService(DossierRepository repository, PpmRepository ppmRepository,
            ControleurDirectory controleurDirectory, NotificationService notificationService,
            DossierIntegriteService dossierIntegrite, VerificationRepository verificationRepository,
            AuditLogRepository auditLogRepository, PrmpRepository prmpRepository,
            MarcheRepository marcheRepository, MarchePrevisionRepository marchePrevisionRepository,
            ReceptionRepository receptionRepository, DemandeRetraitRepository demandeRetraitRepository,
            NotificationRepository notificationRepository,
            TypePieceJointeRepository typePieceJointeRepository,
            PieceJointeDossierRepository pieceJointeDossierRepository, MarcheService marcheService,
            TypeDossierRepository typeDossierRepository, SousTypeDossierRepository sousTypeDossierRepository) {
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
        this.receptionRepository = receptionRepository;
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
    @Transactional(readOnly = true)
    public List<DossierDto> findAll(String statut, String type, String sousType) {
        String filtre = normaliserStatut(statut);
        String filtreType = normaliserType(type);
        String filtreSousType = normaliserSousType(sousType);
        return chargerScopees(filtre).stream()
                // Filtres famille / sous-type appliqués côté serveur, après le scoping (volumes déjà réduits).
                .filter(d -> filtreType == null || filtreType.equals(d.getIdTypeDossier()))
                .filter(d -> filtreSousType == null || filtreSousType.equals(d.getIdSousType()))
                .map(DossierMapper::toDto).toList();
    }

    /** Dossiers du périmètre de l'appelant (scoping §1), optionnellement restreints à un statut. */
    private List<Dossier> chargerScopees(String filtre) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == ProfilUtilisateur.PRESIDENT || profil == ProfilUtilisateur.ADMINISTRATEUR) {
            return repository.findParStatut(filtre);
        }
        if (profil == ProfilUtilisateur.PRMP) {
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
        return DossierMapper.toDto(entity);
    }

    /**
     * File « à réceptionner » (§3.4) : dossiers soumis ({@code SOUMIS}) et sans réception, de la
     * localité du contrôleur. Président/Administrateur voient toutes les localités.
     */
    @Transactional(readOnly = true)
    public List<DossierDto> aReceptionner() {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == ProfilUtilisateur.PRESIDENT || profil == ProfilUtilisateur.ADMINISTRATEUR) {
            return repository.findAReceptionner().stream().map(DossierMapper::toDto).toList();
        }
        String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (localite == null) {
            return List.of();
        }
        return repository.findAReceptionnerParLocalite(localite).stream().map(DossierMapper::toDto).toList();
    }

    /**
     * File « à examiner » du Membre attributaire (§2.4) : ses dossiers au statut
     * {@link StatutDossier#DISPATCHE} (dispatchés vers lui, pas encore examinés). Scopée à
     * l'utilisateur courant ({@code Dispatch.imCtrlMembre}).
     */
    @Transactional(readOnly = true)
    public List<DossierDto> aExaminer() {
        String im = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (im == null) {
            return List.of();
        }
        return repository.findAExaminerParMembre(StatutDossier.DISPATCHE.name(), im)
                .stream().map(DossierMapper::toDto).toList();
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
        return repository.findExaminesParMembre(statuts, im, pageable).map(DossierMapper::toDto);
    }

    /** File « à vérifier » du Vérificateur (§3.6) : dossiers EN_VERIFICATION de sa localité. */
    @Transactional(readOnly = true)
    public List<DossierDto> aVerifier() {
        String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (localite == null) {
            return List.of();
        }
        return repository.findAVerifierParLocalite(localite).stream().map(DossierMapper::toDto).toList();
    }

    /** Historique « vérifiés / clôturés » du Vérificateur (PV signés clôturés), paginé, lecture seule. */
    @Transactional(readOnly = true)
    public Page<DossierDto> verifies(Pageable pageable) {
        String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (localite == null) {
            return Page.empty(pageable);
        }
        return repository.findVerifiesParLocalite(localite, pageable).map(DossierMapper::toDto);
    }

    /** File « En attente PRMP » du Vérificateur (lecture seule) : dossiers EN_ATTENTE_DECISION_PRMP de sa localité. */
    @Transactional(readOnly = true)
    public List<DossierDto> enAttentePrmp() {
        String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        return localite == null ? List.of()
                : repository.findEnAttentePrmpParLocalite(localite).stream().map(DossierMapper::toDto).toList();
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
        return repository.findRetirablesPourPrmp(idPrmp, StatutDossier.NOMS_AVANT_PV_SIGNE)
                .stream().map(DossierMapper::toDto).toList();
    }

    /** Vérifie que le dossier est dans le périmètre de visibilité de l'utilisateur (§1). */
    private void controlerVisibilite(Integer idDossier) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == ProfilUtilisateur.PRESIDENT || profil == ProfilUtilisateur.ADMINISTRATEUR) {
            return;
        }
        if (profil == ProfilUtilisateur.PRMP) {
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
        return DossierMapper.toDto(repository.save(entity));
    }

    public DossierDto update(Integer id, DossierDto dto) {
        Dossier existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + id));
        existing.setIdTypeDossier(dto.getIdTypeDossier());
        existing.setIdDossierParent(dto.getIdDossierParent());
        existing.setRefeDossier(dto.getRefeDossier());
        existing.setDateRef(dto.getDateRef());
        existing.setStatut(dto.getStatut());
        existing.setIdLocalite(dto.getIdLocalite());
        existing.setIdEntiteContract(dto.getIdEntiteContract());
        return DossierMapper.toDto(repository.save(existing));
    }

    /**
     * ⚠️ Règle ajoutée — suppression d'un dossier depuis « Mes brouillons » (PRMP propriétaire).
     * Un dossier <strong>{@code BROUILLON}</strong> est <strong>toujours supprimable</strong> (même revenu en
     * brouillon après un circuit incomplet via retrait), sinon <strong>409</strong>. Cascade complète en une
     * transaction : <strong>contenu</strong> (prévisions → marchés → PPM) + <strong>historique de circuit</strong>
     * (notifications, demandes de retrait, réceptions — un brouillon n'a jamais dépassé {@code PRET_DISPATCH},
     * donc des réceptions <em>feuilles</em>, sans dispatch/examen/PV/vérification). Le <strong>journal d'audit</strong>
     * ({@code t_audit_log}, immuable §3.8, sans FK) est <strong>conservé</strong>.
     */
    public void delete(Integer id) {
        Dossier dossier = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + id)); // 404
        dossierIntegrite.exigerProprietaire(dossier);                                              // 403
        if (!StatutDossier.BROUILLON.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException("Ce dossier ne peut pas être supprimé.");              // 409
        }
        // Contenu : sous-lignes de chaque marché (tranches, lots, bénéficiaires, prévisions, DMC) → marchés → PPM(s).
        List<Marche> marches = marcheRepository.findByIdDossier(id);
        for (Marche m : marches) {
            marcheService.supprimerSousLignes(m.getIdDetail());   // cascade partagée (inclut t_lot) — pas d'orphelin FK
        }
        marcheRepository.deleteAll(marches);
        ppmRepository.deleteAll(ppmRepository.findByIdDossier(id));
        // Historique de circuit (un brouillon ≤ PRET_DISPATCH → réceptions sans dispatch/vérification).
        notificationRepository.deleteByIdDossier(id);
        demandeRetraitRepository.deleteByIdDossier(id);
        receptionRepository.deleteByIdDossier(id);
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
        // Propriété : seule la PRMP propriétaire (t_dossier.ID_PRMP) peut soumettre.
        dossierIntegrite.exigerProprietaire(dossier);
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

        notifierSoumission(dossier, localite);
        return DossierMapper.toDto(dossier);
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
        dossierIntegrite.exigerProprietaire(dossier);
        if (!StatutDossier.EN_ATTENTE_DECISION_PRMP.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException(
                    "Resoumission impossible : le dossier n'est pas en attente de décision PRMP (statut « "
                            + dossier.getStatut() + " »).");
        }
        dossier.setStatut(StatutDossier.EN_VERIFICATION.name());
        repository.save(dossier);

        // Dernière vérification (le passage obsLevees=false qui a déclenché l'attente).
        Verification derniere = verificationRepository.findPassagesDuDossier(idDossier).stream()
                .findFirst().orElse(null);
        if (derniere != null) {
            derniere.setMotifRectif(motifRectification);   // visible dans les passages côté vérificateur
            verificationRepository.save(derniere);
        }
        notifierRectification(dossier, derniere, idPrmp, motifRectification);
        tracerRectification(dossier, idPrmp, motifRectification);
        return DossierMapper.toDto(dossier);
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
        log.setIdLog(auditLogRepository.findMaxId() + 1);
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
     */
    @Transactional(readOnly = true)
    public List<EchangeDto> historiqueEchanges(Integer idDossier) {
        Dossier dossier = repository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
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
}
