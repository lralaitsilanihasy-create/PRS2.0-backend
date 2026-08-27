package cnm.prs.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.DemandeRetraitDto;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.DemandeRetrait;
import cnm.prs.entity.DemandeRetraitVue;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.PieceDemandeRetrait;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Prmp;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutRetrait;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.DemandeRetraitMapper;
import cnm.prs.repository.DemandeRetraitRepository;
import cnm.prs.repository.DemandeRetraitVueRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.PieceDemandeRetraitRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link DemandeRetrait}.
 */
@Service
@Transactional
public class DemandeRetraitService {

    /** Journal des transitions du circuit (⚠️ LOT 4 — 2026-08-26), format {@code [CIRCUIT] …}. */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DemandeRetraitService.class);

    private final DemandeRetraitRepository repository;
    private final DossierRepository dossierRepository;
    private final PrmpRepository prmpRepository;
    private final PpmRepository ppmRepository;
    private final CircuitCascadeService circuitCascade;
    private final NotificationService notificationService;
    private final ControleurDirectory controleurDirectory;
    private final DemandeRetraitVueRepository vueRepository;
    /** ⚠️ Spec « Mandats PRMP » — garde de propriété partagée (attribution OU PRMP en fonction) + vacance. */
    private final DossierIntegriteService dossierIntegrite;
    /** Lettre de demande de retrait (PDF obligatoire à la création — stockage dédié, survit à la purge du circuit). */
    private final PieceDemandeRetraitRepository pieceRepository;

    public DemandeRetraitService(DemandeRetraitRepository repository, DossierRepository dossierRepository,
            PrmpRepository prmpRepository, PpmRepository ppmRepository, CircuitCascadeService circuitCascade,
            NotificationService notificationService,
            ControleurDirectory controleurDirectory, DemandeRetraitVueRepository vueRepository,
            DossierIntegriteService dossierIntegrite, PieceDemandeRetraitRepository pieceRepository) {
        this.dossierIntegrite = dossierIntegrite;
        this.pieceRepository = pieceRepository;
        this.repository = repository;
        this.dossierRepository = dossierRepository;
        this.prmpRepository = prmpRepository;
        this.ppmRepository = ppmRepository;
        this.circuitCascade = circuitCascade;
        this.notificationService = notificationService;
        this.controleurDirectory = controleurDirectory;
        this.vueRepository = vueRepository;
    }

    /**
     * Écran « Mes demandes de retrait » de la PRMP : renvoie ses demandes <strong>et marque l'écran
     * consulté</strong> (UPSERT {@code t_demande_retrait_vue.dateDerniereVue = now} pour cette PRMP),
     * ce qui remet à zéro le compteur de nouveautés. PRMP non identifiée → liste vide.
     */
    public List<DemandeRetraitDto> mesDemandes() {
        String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (idPrmp == null) {
            return List.of();
        }
        List<DemandeRetraitDto> demandes = enrichir(repository.findByIdPrmp(idPrmp).stream()
                .map(DemandeRetraitMapper::toDto).toList());
        DemandeRetraitVue vue = vueRepository.findByIdPrmp(idPrmp)
                .orElseGet(() -> new DemandeRetraitVue(null, idPrmp, null));
        vue.setDateDerniereVue(LocalDateTime.now());
        vueRepository.save(vue);
        return demandes;
    }

    /**
     * Liste filtrée (§1, §3.1) : Président/Admin → tout ; PRMP → ses propres demandes ;
     * autres contrôleurs → demandes de leur localité.
     */
    @Transactional(readOnly = true)
    public List<DemandeRetraitDto> findAll() {
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().orElse(null);
            if (idPrmp == null || idPrmp.isBlank()) {
                return List.of();
            }
            return enrichir(repository.findByIdPrmp(idPrmp).stream().map(DemandeRetraitMapper::toDto).toList());
        }
        return enrichir(Visibilite.filtrer(repository::findAll, repository::findVisiblesParLocalite)
                .stream().map(DemandeRetraitMapper::toDto).toList());
    }

    @Transactional(readOnly = true)
    public DemandeRetraitDto findById(Integer id) {
        DemandeRetrait entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DemandeRetrait introuvable : " + id));
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().orElse(null);
            if (idPrmp == null || !idPrmp.equals(entity.getIdPrmp())) {
                throw new AccessDeniedException("Demande hors de votre périmètre de visibilité (§3.1).");
            }
        } else {
            Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        }
        return enrichir(DemandeRetraitMapper.toDto(entity));
    }

    /**
     * Création d'une demande de retrait par la PRMP. Une nouvelle demande est toujours
     * {@link StatutRetrait#EN_ATTENTE}, sans décision (§3.1). Le motif est obligatoire
     * (déjà imposé par {@code @NotBlank} sur le DTO, MOTIF_RETRAIT NOT NULL).
     *
     * <p>⚠️ Règle ajoutée (2026-08-17) — la PRMP doit joindre sa <strong>lettre de demande de
     * retrait</strong> datée et signée (PDF) : pièce absente, non-PDF ou trop volumineuse → 400.
     * La validation porte sur le contenu réel (magic-bytes), pas sur le Content-Type déclaré.
     * Les demandes créées avant l'obligation restent valides (pièce simplement absente).</p>
     */
    public DemandeRetraitDto create(DemandeRetraitDto dto, MultipartFile fichier) {
        byte[] lettre = validerLettre(fichier);
        String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("PRMP non identifiée."));
        Integer idDossier = dto.getIdDossier();
        // Garde 1 — la PRMP doit être PROPRIÉTAIRE du dossier (§3.1).
        Dossier cible = idDossier == null ? null : dossierRepository.findById(idDossier).orElse(null);
        if (cible == null) {
            throw new AccessDeniedException("Retrait possible uniquement sur l'un de vos dossiers (§3.1).");
        }
        // ⚠️ Spec « Mandats PRMP » — second titre accepté : la PRMP EN FONCTION sur le périmètre du dossier
        // (reprise du traitement après changement de titulaire). Un dossier sans propriétaire ni entité
        // reste hors de portée : ce titre ne relâche pas la garde, il l'élargit à un cas précis.
        if (!dossierRepository.existsVisiblePourPrmp(idDossier, idPrmp)
                && !dossierIntegrite.estPrmpEnFonctionSurLeDossier(cible, idPrmp)) {
            throw new AccessDeniedException("Retrait possible uniquement sur l'un de vos dossiers (§3.1).");
        }
        // Garde 1 bis — sans mandat actif, aucune action de traitement (409 VACANCE_PRMP).
        dossierIntegrite.exigerMandatActif();
        // Garde 2 — dossier éligible : statut « avant PV signé » (§3.3). Le retrait est possible à toute
        // étape du circuit tant que le PV n'est pas signé ; refusé à partir de PV_SIGNE (et au-delà).
        // Même ensemble que GET /api/dossiers/retirables (source unique StatutDossier.NOMS_AVANT_PV_SIGNE).
        String statutDossier = dossierRepository.findById(idDossier).map(Dossier::getStatut).orElse(null);
        if (!StatutDossier.NOMS_AVANT_PV_SIGNE.contains(statutDossier)) {
            throw new BusinessRuleException(
                    "Retrait possible uniquement tant que le PV n'est pas signé (statut « " + statutDossier + " »).");
        }
        // Garde 3 — pas de demande déjà EN_ATTENTE pour ce dossier.
        if (repository.existsByIdDossierAndStatut(idDossier, StatutRetrait.EN_ATTENTE.name())) {
            throw new BusinessRuleException("Une demande de retrait est déjà en attente pour ce dossier.");
        }

        DemandeRetrait entity = new DemandeRetrait();
        entity.setIdDossier(idDossier);
        entity.setIdPrmp(idPrmp);                          // identité = JWT, jamais le corps
        entity.setMotifRetrait(dto.getMotifRetrait());     // @NotBlank
        entity.setStatut(StatutRetrait.EN_ATTENTE.name());
        entity.setDateDemande(LocalDateTime.now());        // date serveur
        DemandeRetrait saved = repository.save(entity);    // ID auto-généré (IDENTITY)
        log.info("[CIRCUIT] demande de retrait dossier={} acteur={} demande={} statut={}",
                idDossier, CurrentUser.login().orElse(null), saved.getIdDemandeRetrait(),
                StatutRetrait.EN_ATTENTE.name());

        PieceDemandeRetrait piece = new PieceDemandeRetrait();
        piece.setIdDemandeRetrait(saved.getIdDemandeRetrait());
        piece.setNomFichier(fichier.getOriginalFilename());
        piece.setFormat("application/pdf");
        piece.setTailleOctets((long) lettre.length);
        piece.setDateDepot(LocalDateTime.now());
        piece.setHashSha256(sha256Hex(lettre));
        piece.setContenu(lettre);
        pieceRepository.save(piece);

        notifierDemandeAValider(saved);
        return enrichir(DemandeRetraitMapper.toDto(saved));
    }

    /** Taille maximale de la lettre (alignée sur {@code spring.servlet.multipart.max-file-size}). */
    private static final int LETTRE_MAX_OCTETS = 10 * 1024 * 1024;

    /**
     * Valide la lettre de demande de retrait : présence, type réel PDF (magic-bytes {@code %PDF-}),
     * taille ≤ {@value #LETTRE_MAX_OCTETS} octets. Sinon <strong>400</strong>.
     */
    private byte[] validerLettre(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException(
                    "La lettre de demande de retrait (PDF, datée et signée) est obligatoire : joignez-la dans la partie « fichier ».");
        }
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Lecture du fichier impossible : " + e.getMessage());
        }
        boolean pdf = contenu.length >= 5 && contenu[0] == '%' && contenu[1] == 'P'
                && contenu[2] == 'D' && contenu[3] == 'F' && contenu[4] == '-';
        if (!pdf) {
            throw new BadRequestException(
                    "La lettre de demande de retrait doit être un PDF (type de fichier non autorisé).");
        }
        if (contenu.length > LETTRE_MAX_OCTETS) {
            throw new BadRequestException("Lettre trop volumineuse (" + contenu.length
                    + " octets ; max " + LETTRE_MAX_OCTETS + ").");
        }
        return contenu;
    }

    private static String sha256Hex(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /**
     * Lecture de la lettre jointe à la demande {@code id} — réservée à la <strong>PRMP
     * demanderesse</strong> (périmètre {@code ref} partagé avec son UGPM) et au
     * <strong>décideur</strong> (CC de la localité du dossier ou Président ; Admin voit tout).
     * Demande sans pièce (antérieure à l'obligation) → <strong>404</strong> explicite.
     */
    @Transactional(readOnly = true)
    public PieceDemandeRetrait document(Integer id) {
        DemandeRetrait demande = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DemandeRetrait introuvable : " + id));
        exigerAccesDocument(demande);
        return pieceRepository.findByIdDemandeRetrait(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune lettre jointe à cette demande (demande antérieure à l'obligation de pièce)."));
    }

    /** Accès à la lettre : PRMP demanderesse, CC de la localité du dossier, Président/Admin. */
    private void exigerAccesDocument(DemandeRetrait demande) {
        if (Visibilite.voitTout()) {
            return;
        }
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            if (idPrmp != null && idPrmp.equals(demande.getIdPrmp())) {
                return;
            }
        } else if (CurrentUser.profil().orElse(null) == ProfilUtilisateur.CHEF_COMMISSION) {
            String localiteDossier = dossierRepository.findById(demande.getIdDossier())
                    .map(Dossier::getIdLocalite).orElse(null);
            String localiteCc = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
            if (localiteDossier != null && localiteDossier.equals(localiteCc)) {
                return;
            }
        }
        throw new AccessDeniedException(
                "Lettre accessible uniquement à la PRMP demanderesse et au décideur (CC de la localité du dossier ou Président).");
    }

    /** [Auto] Notifie le CC de la localité du dossier + le(s) Président(s) qu'une demande attend validation. */
    private void notifierDemandeAValider(DemandeRetrait demande) {
        String localite = dossierRepository.findById(demande.getIdDossier())
                .map(Dossier::getIdLocalite).orElse(null);
        String titre = "Demande de retrait à valider";
        String corps = "La PRMP " + demande.getIdPrmp() + " demande le retrait du dossier "
                + demande.getIdDossier() + ". Motif : " + demande.getMotifRetrait();
        List<Controleur> destinataires = new ArrayList<>(controleurDirectory.presidents());
        if (localite != null) {
            destinataires.addAll(controleurDirectory.chefsCommission(localite));
        }
        for (Controleur c : destinataires) {
            notificationService.emettre(demande.getIdDossier(), TypeNotification.DEMANDE_RETRAIT_A_VALIDER,
                    c.getImControleur(), c.getEmailCont(), titre, corps);
        }
    }

    /**
     * Décision d'<strong>acceptation</strong> d'une demande (CC de la localité ou Président).
     * ⚠️ Règle ajoutée — le dossier repasse en {@link StatutDossier#BROUILLON} ; la PRMP est notifiée.
     *
     * <p>⚠️ Audit 2026-08-27 (C3) — la précondition d'état est <strong>rejouée ici</strong>
     * ({@link #exigerDossierEncoreRetirable}) : le circuit avance pendant l'instruction de la demande,
     * et purger un dossier dont le PV a été signé entre-temps est irréversible.</p>
     */
    public DemandeRetraitDto accepter(Integer id) {
        DemandeRetrait demande = chargerEnAttente(id);
        exigerDecideur(demande);
        exigerDossierEncoreRetirable(demande);
        demande.setStatut(StatutRetrait.ACCEPTEE.name());
        demande.setImCtrlCc(decideurAuthentifie());      // décideur réel (CC ou Président), JWT
        demande.setDateDecision(LocalDateTime.now());
        DemandeRetrait saved = repository.save(demande);
        log.info("[CIRCUIT] retrait accepte dossier={} acteur={} demande={} statut={}",
                saved.getIdDossier(), CurrentUser.login().orElse(null), saved.getIdDemandeRetrait(),
                StatutRetrait.ACCEPTEE.name());
        if (demande.getIdDossier() != null) {
            dossierRepository.findById(demande.getIdDossier()).ifPresent(d -> {
                d.setStatut(StatutDossier.BROUILLON.name());
                // ⚠️ Règle ajoutée — restaure la référence INITIALE du dossier (celle générée à la création,
                // stockée dans t_ppm.REFERENCE, ex. « 00003/DGB/PPM/2026 ») dans refeDossier, invalidant ainsi
                // la référence de réception (ex. « 00002/PPM/CRM-ANT/2026 »). Le dossier redevient un brouillon
                // entièrement modifiable affichant sa référence d'origine. (Pas de PPM → refeDossier remis à null.)
                String refInitiale = ppmRepository.findByIdDossier(d.getIdDossier()).stream()
                        .findFirst().map(Ppm::getReference).orElse(null);
                d.setRefeDossier(refInitiale);
                dossierRepository.save(d);
                // ⚠️ Règle ajoutée (§3.3) — le retrait est désormais possible jusqu'à EXAMINE : le dossier peut
                // porter tout un enchaînement de circuit (réception → dispatch → examen → projet de PV → navettes,
                // + copies / lettres de renvoi / observations). On purge cet historique en une transaction, dans
                // l'ordre FK-safe (cf. CircuitCascadeService) — réceptions comprises. Après resoumission le dossier
                // redevient « SOUMIS sans réception » et réapparaît dans a-receptionner (re-réception INITIAL,
                // passage 1). Cas SOUMIS/PRET_DISPATCH (jamais dispatché) : seules les réceptions feuilles existent,
                // les autres suppressions portent sur 0 ligne. Le journal d'audit (sans FK) est conservé.
                circuitCascade.purgerCircuit(d.getIdDossier());
            });
        }
        notifierDecision(saved, StatutRetrait.ACCEPTEE);
        return enrichir(DemandeRetraitMapper.toDto(saved));
    }

    /**
     * Décision de <strong>refus</strong> (CC de la localité ou Président). Le dossier reste inchangé ;
     * le motif de refus (optionnel) est enregistré ; la PRMP est notifiée.
     */
    public DemandeRetraitDto refuser(Integer id, String motif) {
        DemandeRetrait demande = chargerEnAttente(id);
        exigerDecideur(demande);
        demande.setStatut(StatutRetrait.REFUSEE.name());
        demande.setImCtrlCc(decideurAuthentifie());
        demande.setDateDecision(LocalDateTime.now());
        demande.setObsDecision(motif);
        DemandeRetrait saved = repository.save(demande);
        log.info("[CIRCUIT] retrait refuse dossier={} acteur={} demande={} statut={}",
                saved.getIdDossier(), CurrentUser.login().orElse(null), saved.getIdDemandeRetrait(),
                StatutRetrait.REFUSEE.name());
        notifierDecision(saved, StatutRetrait.REFUSEE);
        return enrichir(DemandeRetraitMapper.toDto(saved));
    }

    /**
     * ⚠️ Audit 2026-08-27, constat critique C3 — la garde « avant PV signé » (§3.3) n'existait qu'à la
     * <strong>création</strong> de la demande. Or une demande {@code EN_ATTENTE} ne suspend pas le
     * circuit (§3.1) : le PV pouvait être signé entre la demande et la décision, et l'acceptation
     * purgeait alors PV, navettes, vérifications et lettres via {@link CircuitCascadeService} — au
     * mépris de §3.5 (immuabilité du PV signé).
     *
     * <p>Le statut est donc <strong>relu en base au moment de la décision</strong> et confronté au même
     * ensemble unique {@link StatutDossier#NOMS_AVANT_PV_SIGNE} qu'à la création. Le dossier ayant
     * progressé : <strong>409</strong>, la demande reste {@code EN_ATTENTE} et peut être refusée
     * ({@link #refuser}), qui ne touche pas au circuit.</p>
     */
    private void exigerDossierEncoreRetirable(DemandeRetrait demande) {
        String statut = demande.getIdDossier() == null ? null
                : dossierRepository.findById(demande.getIdDossier()).map(Dossier::getStatut).orElse(null);
        if (!StatutDossier.NOMS_AVANT_PV_SIGNE.contains(statut)) {
            throw new BusinessRuleException("Le dossier a progressé depuis la demande (statut « " + statut
                    + " ») : la demande de retrait est caduque. Le retrait n'est plus possible à partir de "
                    + "la signature du PV (§3.3) — refusez la demande.");
        }
    }

    /** Charge une demande qui doit être {@code EN_ATTENTE} (sinon 409 : déjà traitée). */
    private DemandeRetrait chargerEnAttente(Integer id) {
        DemandeRetrait demande = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DemandeRetrait introuvable : " + id));
        if (!StatutRetrait.EN_ATTENTE.name().equals(demande.getStatut())) {
            throw new BusinessRuleException("La demande a déjà été traitée (statut « " + demande.getStatut() + " »).");
        }
        return demande;
    }

    /** Décision réservée au CC de la localité du dossier OU au Président (rôle↔localité dans le service). */
    private void exigerDecideur(DemandeRetrait demande) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == ProfilUtilisateur.PRESIDENT) {
            return;
        }
        if (profil == ProfilUtilisateur.CHEF_COMMISSION) {
            String localiteDossier = dossierRepository.findById(demande.getIdDossier())
                    .map(Dossier::getIdLocalite).orElse(null);
            String localiteCc = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
            if (localiteDossier != null && localiteDossier.equals(localiteCc)) {
                return;
            }
        }
        throw new AccessDeniedException("Décision réservée au CC de la localité du dossier ou au Président (§3.3).");
    }

    private String decideurAuthentifie() {
        return CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Décideur non identifié."));
    }

    /**
     * Notifie la PRMP de la décision (RETRAIT_ACCEPTE / RETRAIT_REFUSE).
     *
     * <p>⚠️ Audit 2026-08-27 (lot B) — la notification partait par <strong>e-mail seul</strong>
     * ({@code destinataireRef} nul) : la décision qui renvoie son dossier en brouillon n'apparaissait
     * pas dans « mes notifications » dès que l'e-mail du compte diffère de {@code t_prmp.EMAIL_PRMP}.
     * Elle est désormais portée par la PRMP ({@code ref = ID_PRMP}) et pointe le dossier concerné —
     * même forme que {@code PV_SIGNE}, donc actionnable côté front.</p>
     */
    private void notifierDecision(DemandeRetrait demande, StatutRetrait decision) {
        String emailPrmp = prmpRepository.findById(demande.getIdPrmp())
                .map(Prmp::getEmailPrmp).orElse(null);
        TypeNotification type = decision == StatutRetrait.ACCEPTEE
                ? TypeNotification.RETRAIT_ACCEPTE : TypeNotification.RETRAIT_REFUSE;
        String titre = decision == StatutRetrait.ACCEPTEE
                ? "Demande de retrait acceptée" : "Demande de retrait refusée";
        String corps = decision == StatutRetrait.ACCEPTEE
                ? "Votre demande de retrait du dossier " + demande.getIdDossier()
                        + " a été acceptée ; le dossier repasse en brouillon."
                : "Votre demande de retrait du dossier " + demande.getIdDossier() + " a été refusée."
                        + (demande.getObsDecision() != null ? " Motif : " + demande.getObsDecision() : "");
        notificationService.emettrePrmp(type, demande.getIdPrmp(), emailPrmp,
                demande.getIdDossier(), TypeObjet.DOSSIER, demande.getIdDossier(), titre, corps);
    }

    /** File « à valider » : demandes EN_ATTENTE (Président : toutes ; CC : sa localité de dossier). */
    @Transactional(readOnly = true)
    public List<DemandeRetraitDto> aValider() {
        return parStatuts(List.of(StatutRetrait.EN_ATTENTE.name()));
    }

    /** Historique : demandes décidées (ACCEPTEE / REFUSEE), même scope que « à valider ». */
    @Transactional(readOnly = true)
    public List<DemandeRetraitDto> historique() {
        return parStatuts(List.of(StatutRetrait.ACCEPTEE.name(), StatutRetrait.REFUSEE.name()));
    }

    private List<DemandeRetraitDto> parStatuts(List<String> statuts) {
        List<DemandeRetrait> list;
        if (CurrentUser.profil().orElse(null) == ProfilUtilisateur.PRESIDENT) {
            list = repository.findByStatutIn(statuts);
        } else {
            String loc = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
            list = loc == null ? List.of() : repository.findByStatutsEtLocaliteDossier(statuts, loc);
        }
        return enrichir(list.stream().map(DemandeRetraitMapper::toDto).toList());
    }

    /**
     * Suppression d'une demande de retrait (Administrateur).
     *
     * <p>⚠️ Audit 2026-08-27 (lot B) — la suppression emportait la <strong>lettre de demande de
     * retrait</strong> (règle 2026-08-17), qui « justifie la décision et doit lui survivre » : une
     * demande <strong>décidée</strong> (ACCEPTEE / REFUSEE) partait avec sa pièce justificative, alors
     * qu'une acceptation a purgé tout le circuit du dossier et l'a renvoyé en brouillon. Une demande
     * décidée n'est donc plus supprimable (409) — sa lettre survit parce que la demande survit. Une
     * demande encore EN_ATTENTE n'a rien décidé : elle reste supprimable avec sa pièce, qui ne
     * justifie alors rien et dont l'unicité ({@code ID_DEMANDE_RETRAIT}) interdit l'orphelin.</p>
     */
    public void delete(Integer id) {
        DemandeRetrait demande = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DemandeRetrait introuvable : " + id));
        if (!StatutRetrait.EN_ATTENTE.name().equals(demande.getStatut())) {
            throw new BusinessRuleException("Cette demande de retrait a été traitée (statut « "
                    + demande.getStatut() + " ») : la décision et la lettre qui la justifie sont "
                    + "conservées (§3.3, règle 2026-08-17).");
        }
        pieceRepository.deleteByIdDemandeRetrait(id);   // la lettre suit la demande (pas d'orphelin)
        repository.deleteById(id);
    }

    /** Reporte {@code nomFichier}/{@code tailleFichier} de la lettre jointe sur les DTO (métadonnées seules, jamais le contenu). */
    private List<DemandeRetraitDto> enrichir(List<DemandeRetraitDto> dtos) {
        List<Integer> ids = dtos.stream().map(DemandeRetraitDto::getIdDemandeRetrait).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return dtos;
        }
        Map<Integer, PieceDemandeRetraitRepository.Meta> metas = pieceRepository.findMetaByIdDemandeRetraitIn(ids)
                .stream().collect(Collectors.toMap(PieceDemandeRetraitRepository.Meta::getIdDemandeRetrait, Function.identity()));
        for (DemandeRetraitDto dto : dtos) {
            PieceDemandeRetraitRepository.Meta meta = metas.get(dto.getIdDemandeRetrait());
            if (meta != null) {
                dto.setNomFichier(meta.getNomFichier());
                dto.setTailleFichier(meta.getTailleOctets());
            }
        }
        return dtos;
    }

    private DemandeRetraitDto enrichir(DemandeRetraitDto dto) {
        enrichir(List.of(dto));
        return dto;
    }
}
