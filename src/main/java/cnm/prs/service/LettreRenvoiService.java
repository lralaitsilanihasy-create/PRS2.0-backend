package cnm.prs.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.LettreRenvoiDto;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Examen;
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.entity.LettreRenvoiLue;
import cnm.prs.entity.Localite;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Prmp;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutLettreRenvoi;
import cnm.prs.enums.StatutPv;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.LettreRenvoiMapper;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.entity.EntiteContract;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.LocaliteRepository;
import cnm.prs.repository.LettreRenvoiLueRepository;
import cnm.prs.repository.LettreRenvoiRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link LettreRenvoi} : action séparée pendant l'examen (un examen → N lettres).
 * Circuit {@code BROUILLON → SOUMIS → SIGNE} ; signature par le CC ou le Président uniquement.
 * À la signature : notification de la PRMP du dossier et des Assistants contrôleurs de la localité.
 */
@Service
@Transactional
public class LettreRenvoiService {

    /** Journal des transitions du circuit (⚠️ LOT 4 — 2026-08-26), format {@code [CIRCUIT] …}. */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LettreRenvoiService.class);

    private final LettreRenvoiRepository repository;
    private final ExamenRepository examenRepository;
    private final DossierRepository dossierRepository;
    private final PpmRepository ppmRepository;
    private final PrmpRepository prmpRepository;
    private final ControleurDirectory controleurDirectory;
    private final ControleurRepository controleurRepository;
    private final NotificationService notificationService;
    private final LettreRenvoiLueRepository lueRepository;
    private final EntiteContractRepository entiteContractRepository;
    private final LocaliteRepository localiteRepository;
    private final LettreRenvoiDocumentGenerator documentGenerator;
    private final ReferenceService referenceService;
    private final PvExamenRepository pvExamenRepository;

    @Value("${storage.lettre-renvoi.path:${java.io.tmpdir}/prs-fsx/LR}")
    private String cheminStockageLr;

    public LettreRenvoiService(LettreRenvoiRepository repository, ExamenRepository examenRepository,
            DossierRepository dossierRepository, PpmRepository ppmRepository, PrmpRepository prmpRepository,
            ControleurDirectory controleurDirectory, ControleurRepository controleurRepository,
            NotificationService notificationService, LettreRenvoiLueRepository lueRepository,
            EntiteContractRepository entiteContractRepository, LocaliteRepository localiteRepository,
            LettreRenvoiDocumentGenerator documentGenerator, ReferenceService referenceService,
            PvExamenRepository pvExamenRepository) {
        this.pvExamenRepository = pvExamenRepository;
        this.documentGenerator = documentGenerator;
        this.localiteRepository = localiteRepository;
        this.entiteContractRepository = entiteContractRepository;
        this.repository = repository;
        this.examenRepository = examenRepository;
        this.dossierRepository = dossierRepository;
        this.ppmRepository = ppmRepository;
        this.prmpRepository = prmpRepository;
        this.controleurDirectory = controleurDirectory;
        this.controleurRepository = controleurRepository;
        this.notificationService = notificationService;
        this.lueRepository = lueRepository;
        this.referenceService = referenceService;
    }

    /**
     * Liste filtrée selon le profil : MEMBRE → ses lettres (par ses examens) ; CC → lettres SOUMIS de
     * sa localité ; ASSISTANT_CONTROLEUR → lettres SIGNE de sa localité ; Président/Admin → toutes.
     */
    @Transactional(readOnly = true)
    public List<LettreRenvoiDto> findAll() {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        String loc = CurrentUser.localite().orElse(null);
        List<LettreRenvoi> lettres;
        if (Visibilite.voitTout()) {
            lettres = repository.findAll();                                  // Président / Administrateur
        } else if (profil == ProfilUtilisateur.MEMBRE) {
            lettres = repository.findByMembre(CurrentUser.ref().orElse(null));
        } else if (profil == ProfilUtilisateur.CHEF_COMMISSION) {
            lettres = repository.findByStatutEtLocalite(StatutLettreRenvoi.SOUMIS.name(), loc);
        } else if (profil == ProfilUtilisateur.ASSISTANT_CONTROLEUR) {
            lettres = repository.findByStatutEtLocalite(StatutLettreRenvoi.SIGNE.name(), loc);
        } else {
            lettres = List.of();
        }
        return peuplerEnLot(lettres.stream().map(LettreRenvoiMapper::toDto).toList());
    }

    /** Lettres signées concernant les dossiers de la PRMP connectée (lecture seule). */
    @Transactional(readOnly = true)
    public List<LettreRenvoiDto> mesLettres() {
        String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (idPrmp == null) {
            return List.of();
        }
        return peuplerEnLot(repository.findSigneesPourPrmp(idPrmp).stream()
                .map(LettreRenvoiMapper::toDto).toList());
    }

    /**
     * Détail d'une lettre. Accès : périmètre de localité habituel <strong>ou</strong> branche PRMP
     * propriétaire du dossier pour une lettre {@code SIGNE} (sinon la PRMP serait hors périmètre → 403).
     * À cette occasion, la lettre est marquée « lue » pour l'<strong>agent connecté</strong> (trace
     * {@code t_lettre_renvoi_lue}, idempotente et silencieuse).
     *
     * <p>⚠️ Décision métier 2026-08-27 — la trace porte le <strong>login</strong> de l'agent (claim
     * {@code sub}) et non plus la claim {@code ref} : pour une UGPM, {@code ref} est l'ID_PRMP de sa
     * tutelle, si bien que sa consultation éteignait le badge de la PRMP. La garde d'accès, elle, ne
     * bouge pas : seule la branche propriétaire (PRMP ou son UGPM) laisse une trace (acquis LOT 3a —
     * un contrôleur du périmètre n'en pose jamais).</p>
     */
    public LettreRenvoiDto findById(Integer id) {
        LettreRenvoi entity = exigerExistante(id);
        String login = CurrentUser.login().filter(s -> !s.isBlank()).orElse(null);
        boolean prmpProprietaire = estPrmpProprietaireSignee(entity);
        if (!prmpProprietaire) {
            // Périmètre de localité habituel (la PRMP non propriétaire reste hors périmètre → 403).
            Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        } else if (login != null && !lueRepository.existsByIdLettreAndLoginAgent(id, login)) {
            // Marquage « lu » à la consultation par l'agent propriétaire (silencieux, anti-doublon).
            tracerLecture(id, login, CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null));
        }
        LettreRenvoiDto dto = peuplerNomSignataire(LettreRenvoiMapper.toDto(entity));
        dto.setLue(login != null && lueRepository.existsByIdLettreAndLoginAgent(id, login));
        return dto;
    }

    /**
     * Pose la trace de lecture d'un agent : son {@code login} porte l'unicité
     * ({@code uk_lettre_lue_agent}), {@code idPrmpTutelle} (claim {@code ref}) documente le périmètre
     * de tutelle dans lequel la lecture a eu lieu.
     */
    private void tracerLecture(Integer idLettre, String login, String idPrmpTutelle) {
        LettreRenvoiLue trace = new LettreRenvoiLue();
        trace.setIdLettre(idLettre);
        trace.setLoginAgent(login);
        trace.setIdPrmp(idPrmpTutelle);
        trace.setDateLecture(LocalDateTime.now());
        lueRepository.save(trace);
    }

    /**
     * Vrai si l'appelant est la PRMP propriétaire du dossier d'une lettre {@code SIGNE}.
     *
     * <p>⚠️ Correctif 2026-08-26 — l'UGPM partage le périmètre de sa tutelle
     * ({@link Visibilite#estPrmp()}), cf. §3.1.</p>
     */
    private boolean estPrmpProprietaireSignee(LettreRenvoi entity) {
        String ref = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        return Visibilite.estPrmp()
                && ref != null
                && StatutLettreRenvoi.SIGNE.name().equals(entity.getStatut())
                && ppmRepository.existsByIdDossierAndIdPrmp(entity.getIdDossier(), ref);
    }

    /**
     * Renseigne {@code nomSignataire} et {@code lue} sur une LISTE, <strong>en lot</strong>
     * (⚠️ audit 2026-08-27, lot D §5).
     *
     * <p>Les deux enrichissements se faisaient lettre par lettre : {@code peuplerNomSignataire} allait
     * chercher le contrôleur signataire, {@code peuplerLue} interrogeait la trace de lecture — soit
     * <strong>deux requêtes par lettre</strong>, sur des listes qui grossissent avec chaque examen non
     * conforme. Ils tiennent désormais en <strong>deux requêtes au total</strong>, quelle que soit la
     * taille de la liste, sur le modèle déjà en place dans le dépôt
     * ({@link ActeurDirectory#nomsParLogin}, {@code findMetaByIdDemandeRetraitIn}).</p>
     *
     * <p>Comportement strictement identique à l'ancien enchaînement : un signataire introuvable ou
     * sans nom laisse {@code nomSignataire} à {@code null}, et le flag {@code lue} n'est posé que sur
     * les lettres qui portent un identifiant.</p>
     */
    private List<LettreRenvoiDto> peuplerEnLot(List<LettreRenvoiDto> dtos) {
        if (dtos.isEmpty()) {
            return dtos;
        }
        // 1 requête — noms des signataires (IM_CONTROLEUR est la PK de tr_controleur).
        Set<String> signataires = dtos.stream().map(LettreRenvoiDto::getImSignataire)
                .filter(im -> im != null && !im.isBlank()).collect(Collectors.toSet());
        Map<String, String> noms = new HashMap<>();
        if (!signataires.isEmpty()) {
            for (Controleur c : controleurRepository.findAllById(signataires)) {
                String nom = ((c.getPrenomsCont() == null ? "" : c.getPrenomsCont()) + " "
                        + (c.getNomCont() == null ? "" : c.getNomCont())).trim();
                if (!nom.isBlank()) {
                    noms.put(c.getImControleur(), nom);
                }
            }
        }
        // 1 requête — lettres déjà lues par l'agent connecté (suivi individuel, décision du 2026-08-27).
        String login = CurrentUser.login().filter(s -> !s.isBlank()).orElse(null);
        List<Integer> ids = dtos.stream().map(LettreRenvoiDto::getIdLettre).filter(Objects::nonNull).toList();
        Set<Integer> lues = (login == null || ids.isEmpty()) ? Set.of()
                : Set.copyOf(lueRepository.findIdLettresLuesPourLogin(ids, login));

        for (LettreRenvoiDto dto : dtos) {
            if (dto.getImSignataire() != null) {
                dto.setNomSignataire(noms.get(dto.getImSignataire()));
            }
            if (dto.getIdLettre() != null) {
                dto.setLue(lues.contains(dto.getIdLettre()));
            }
        }
        return dtos;
    }

    /** Renseigne {@code nomSignataire} (« prénoms nom ») depuis {@code tr_controleur} si la lettre est signée. */
    private LettreRenvoiDto peuplerNomSignataire(LettreRenvoiDto dto) {
        if (dto != null && dto.getImSignataire() != null) {
            controleurRepository.findById(dto.getImSignataire()).ifPresent(c -> {
                String n = ((c.getPrenomsCont() == null ? "" : c.getPrenomsCont()) + " "
                        + (c.getNomCont() == null ? "" : c.getNomCont())).trim();
                dto.setNomSignataire(n.isBlank() ? null : n);
            });
        }
        return dto;
    }

    /**
     * Création d'une lettre de renvoi pendant l'examen (Membre), statut BROUILLON. {@code idDossier},
     * {@code dateExamen} et {@code refLettre} (compteur {@code <seq>/LR/<code_localite>/<année>}) sont
     * dérivés de l'examen. Examen inexistant ou hors périmètre → 403.
     */
    public LettreRenvoiDto create(LettreRenvoiDto dto) {
        Integer idExamen = dto.getIdExamen();
        Visibilite.controler(loc -> examenRepository.existsDansLocalite(idExamen, loc));
        Examen examen = examenRepository.findById(idExamen)
                .orElseThrow(() -> new AccessDeniedException("Examen inexistant ou hors de votre périmètre."));
        Integer idDossier = examenRepository.findIdDossierByExamen(idExamen).orElse(null);

        LettreRenvoi lettre = new LettreRenvoi();
        lettre.setIdExamen(idExamen);
        lettre.setIdDossier(idDossier);
        lettre.setCorpsLettre(dto.getCorpsLettre());
        lettre.setRefLettre(genererRefLettre(idExamen));
        lettre.setDateExamen(examen.getDateExamen());
        lettre.setDateLettre(LocalDate.now());
        lettre.setStatut(StatutLettreRenvoi.BROUILLON.name());
        return LettreRenvoiMapper.toDto(repository.save(lettre));
    }

    /**
     * Référence de la lettre : reprend le <strong>type</strong>, la <strong>localité</strong> et l'<strong>année</strong>
     * du dossier ({@code refeDossier} = {@code <seqDossier>/<type>/<codeLocalite>/<année>}), mais avec un
     * <strong>numéro de séquence dédié et GLOBAL aux lettres de renvoi</strong> (par année, indépendant du dossier,
     * de l'entité ou de la localité) → {@code <seqLettre>/<type>/<codeLocalite>/LR/<année>}
     * (ex. {@code 00001/PPM/CRM-ANT/LR/2026}). {@code null} si refeDossier absent ou non structuré.
     */
    private String genererRefLettre(Integer idExamen) {
        String refe = examenRepository.findRefeDossierByExamen(idExamen)
                .filter(s -> s != null && s.matches("\\d+/[^/]+/[^/]+/\\d{4}")).orElse(null);
        if (refe == null) {
            return null;
        }
        String[] p = refe.split("/");          // [seqDossier, type, codeLocalite, année]
        int annee = Integer.parseInt(p[3]);
        return String.format("%05d/%s/%s/LR/%d", referenceService.sequenceLettreRenvoi(annee), p[1], p[2], annee);
    }

    /** Édition du brouillon (corps) par le Membre. L'objet est fixe (« lettre de renvoi »). */
    public LettreRenvoiDto update(Integer id, LettreRenvoiDto dto) {
        LettreRenvoi lettre = exigerExistante(id);
        if (!StatutLettreRenvoi.BROUILLON.name().equals(lettre.getStatut())) {
            throw new BusinessRuleException("Lettre non éditable : statut « " + lettre.getStatut() + " » (attendu BROUILLON).");
        }
        // ⚠️ Verrou optimiste HTTP (plan §3) : version périmée → 409 CONFLIT_VERSION, avant toute écriture.
        VerrouOptimiste.exigerVersionCourante(dto.getVersion(), lettre.getVersion());
        lettre.setCorpsLettre(dto.getCorpsLettre());
        // ⚠️ saveAndFlush : l'incrément de @Version se fait au flush — sans lui la réponse rendrait
        // l'ancienne version et le client re-conflicterait au PUT suivant (cf. plan §4).
        return LettreRenvoiMapper.toDto(repository.saveAndFlush(lettre));
    }

    /** Soumission par le Membre propriétaire (attributaire de l'examen) : BROUILLON → SOUMIS. */
    public LettreRenvoiDto soumettre(Integer id) {
        LettreRenvoi lettre = exigerExistante(id);
        exigerProprietaire(lettre);
        if (!StatutLettreRenvoi.BROUILLON.name().equals(lettre.getStatut())) {
            throw new BusinessRuleException("Soumission impossible : statut « " + lettre.getStatut() + " » (attendu BROUILLON).");
        }
        lettre.setStatut(StatutLettreRenvoi.SOUMIS.name());
        LettreRenvoi soumise = repository.save(lettre);
        log.info("[CIRCUIT] lettre de renvoi soumise dossier={} acteur={} lettre={} statut={}",
                soumise.getIdDossier(), CurrentUser.login().orElse(null), soumise.getIdLettre(),
                StatutLettreRenvoi.SOUMIS.name());
        return LettreRenvoiMapper.toDto(soumise);
    }

    /**
     * Signature par le CC ou le Président : SOUMIS → SIGNE ; {@code imSignataire} = JWT.
     * <strong>Règle de localité (⚠️ ajoutée)</strong> : localité <strong>centrale</strong> ({@code ANT}) →
     * CC ou Président ; localité <strong>régionale</strong> (autre) → <strong>CC uniquement</strong>
     * (Président → 403). La localité est celle du dossier ({@code idLocalite}), avec repli sur la localité
     * de réception si absente. À la signature, le <strong>PDF</strong> de la lettre est généré (modèle
     * centrale/régionale) et stocké. Notifie la PRMP et les Assistants contrôleurs de la localité.
     */
    public LettreRenvoiDto signer(Integer id) {
        LettreRenvoi lettre = exigerExistante(id);
        if (!StatutLettreRenvoi.SOUMIS.name().equals(lettre.getStatut())) {
            throw new BusinessRuleException("Signature impossible : statut « " + lettre.getStatut() + " » (attendu SOUMIS).");
        }
        Dossier dossier = lettre.getIdDossier() == null ? null
                : dossierRepository.findById(lettre.getIdDossier()).orElse(null);
        String localite = dossier == null ? null : dossier.getIdLocalite();
        if (localite == null || localite.isBlank()) {
            localite = repository.findLocaliteByLettre(id).orElse(null);   // repli : localité de réception
        }
        boolean centrale = Localite.estCentrale(localite);   // source unique (cf. références « CNM »)
        if (!centrale && CurrentUser.profil().orElse(null) != ProfilUtilisateur.CHEF_COMMISSION) {
            throw new AccessDeniedException(
                    "Seul le Chef de Commission peut signer une lettre de renvoi pour une localité régionale.");
        }
        // ⚠️ Audit 2026-08-27 (lot B) — la garde s'arrêtait au PROFIL : n'importe quel Chef de commission
        // signait la lettre régionale d'une AUTRE commission (le PDF porte pourtant l'en-tête de la
        // localité du dossier et la ligne « Le Chef de la Commission Régionale des Marchés »). La
        // localité est désormais celle du dossier, comme pour le choix du modèle. Président et
        // Administrateur (sans localité) restent exemptés — le cas régional les a déjà écartés
        // ci-dessus, il ne reste que la lettre centrale, qui relève bien du Président.
        Visibilite.exigerLocalite(localite);
        String im = CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Signataire non identifié."));
        String localiteLibelle = localite == null ? "" : localiteRepository.findById(localite)
                .map(l -> l.getLibelleLocalite() == null ? "" : l.getLibelleLocalite()).orElse("");
        lettre.setImSignataire(im);
        lettre.setStatut(StatutLettreRenvoi.SIGNE.name());
        byte[] pdf = documentGenerator.genererPdf(centrale,
                construireRemplacements(lettre, dossier, nomComplet(im), centrale, localiteLibelle));
        lettre.setCheminDocument(stockerSurFsx(lettre, pdf));   // PDF écrit sur le FSX (répertoire LR/)
        // Log posé APRÈS la génération/écriture du PDF : celles-ci peuvent échouer et faire refluer la
        // signature ; journaliser plus haut annoncerait une transition qui n'a pas eu lieu.
        log.info("[CIRCUIT] lettre de renvoi signee dossier={} acteur={} lettre={} statut={}",
                lettre.getIdDossier(), CurrentUser.login().orElse(null), lettre.getIdLettre(),
                StatutLettreRenvoi.SIGNE.name());
        // ⚠️ Règle MODIFIÉE (2026-08-01, spec navette cas 3) — la lettre signée SUSPEND l'examen : le dossier
        // passe EN_ATTENTE_PIECES (plus modifiable par les Membres, verrous d'examen exclus de ce statut).
        // La PRMP dépose les pièces demandées (apresLettreRenvoi=true) puis déclenche la reprise via
        // POST /api/dossiers/{id}/transmettre-complements → ⚠️ 2026-08-02 : le dossier passe A_REEXAMINER
        // (retour dans la file « à examiner » du Membre pour RÉEXAMEN avec les pièces reçues) ; la navette
        // repart à la re-soumission du projet de PV (→ EXAMINE).
        // (A_REEXAMINER accepté : nouvelle lettre signée pendant un réexamen → re-suspension.)
        if (dossier != null && (StatutDossier.EXAMINE.name().equals(dossier.getStatut())
                || StatutDossier.A_REEXAMINER.name().equals(dossier.getStatut()))) {
            dossier.setStatut(StatutDossier.EN_ATTENTE_PIECES.name());
            dossierRepository.save(dossier);
            log.info("[CIRCUIT] examen suspendu par lettre de renvoi dossier={} acteur={} lettre={} statut={}",
                    dossier.getIdDossier(), CurrentUser.login().orElse(null), lettre.getIdLettre(),
                    StatutDossier.EN_ATTENTE_PIECES.name());
        }
        // ⚠️ Règle ajoutée (2026-08-02, réexamen) — un projet de PV resté PROJET_SOUMIS repasse
        // EN_RECTIFICATION : la lettre de renvoi vaut retour de navette ; sans cela le Membre ne
        // pourrait pas re-soumettre le projet après le réexamen (soumission exige BROUILLON/EN_RECTIFICATION).
        pvExamenRepository.findFirstByIdExamenOrderByIdPvDesc(lettre.getIdExamen()).ifPresent(pv -> {
            if (StatutPv.PROJET_SOUMIS.name().equals(pv.getStatutPv())) {
                pv.setStatutPv(StatutPv.EN_RECTIFICATION.name());
                pvExamenRepository.save(pv);
            }
        });
        LettreRenvoi saved = repository.save(lettre);
        notifierSignature(saved);
        return peuplerNomSignataire(LettreRenvoiMapper.toDto(saved));
    }

    /**
     * ⚠️ Spec navette (2026-08-01) — ARCHIVAGE de la lettre signée par l'Assistant contrôleur (même
     * circuit que les PV) : pose la date/l'auteur d'archivage ; la lettre reste rattachée au dossier.
     */
    public LettreRenvoiDto archiver(Integer id) {
        LettreRenvoi lettre = exigerExistante(id);
        if (!StatutLettreRenvoi.SIGNE.name().equals(lettre.getStatut())) {
            throw new BusinessRuleException("Archivage impossible : la lettre n'est pas signée (statut « "
                    + lettre.getStatut() + " »).");
        }
        if (lettre.getDateArchivage() != null) {
            throw new BusinessRuleException("Cette lettre est déjà archivée (le " + lettre.getDateArchivage() + ").");
        }
        String localite = repository.findLocaliteByLettre(id).orElse(null);
        String maLocalite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (localite != null && !localite.equals(maLocalite)) {
            throw new AccessDeniedException("Archivage réservé à l'Assistant contrôleur de la localité du dossier.");
        }
        lettre.setDateArchivage(LocalDate.now());
        lettre.setImArchiveur(CurrentUser.ref().orElse(null));
        return peuplerNomSignataire(LettreRenvoiMapper.toDto(repository.save(lettre)));
    }

    /** Écrit le PDF dans le répertoire FSX LR/ sous {@code {refLettre nettoyée}.pdf} ; renvoie le chemin. */
    private String stockerSurFsx(LettreRenvoi lettre, byte[] pdf) {
        String base = lettre.getRefLettre() != null && !lettre.getRefLettre().isBlank()
                ? lettre.getRefLettre() : ("lettre-" + lettre.getIdLettre());
        String nomFichier = base.replace('/', '_').replace('\\', '_') + ".pdf";
        try {
            Path dir = Path.of(cheminStockageLr);
            Files.createDirectories(dir);
            Path fichier = dir.resolve(nomFichier);
            Files.write(fichier, pdf);
            return fichier.toString();
        } catch (IOException e) {
            throw new BusinessRuleException("Stockage du document de la lettre impossible : " + e.getMessage());
        }
    }

    /**
     * Document PDF de la lettre signée (téléchargement). Accès : périmètre de localité habituel ou PRMP
     * propriétaire (lettre {@code SIGNE}). Lit le fichier sur le FSX ({@code CHEMIN_DOCUMENT}), avec repli
     * sur le contenu en base ({@code DOCUMENT_PDF}). 404 si la lettre n'a pas de document.
     */
    @Transactional(readOnly = true)
    public byte[] telechargerDocument(Integer id) {
        LettreRenvoi lettre = exigerExistante(id);
        if (!estPrmpProprietaireSignee(lettre)) {
            Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        }
        if (lettre.getCheminDocument() != null && !lettre.getCheminDocument().isBlank()) {
            try {
                return Files.readAllBytes(Path.of(lettre.getCheminDocument()));
            } catch (IOException e) {
                throw new ResourceNotFoundException("Document introuvable sur le FSX pour la lettre : " + id);
            }
        }
        if (lettre.getDocumentPdf() != null && lettre.getDocumentPdf().length > 0) {
            return lettre.getDocumentPdf();   // repli compatibilité (lettres signées avant le stockage FSX)
        }
        throw new ResourceNotFoundException("Aucun document pour la lettre : " + id);
    }

    /**
     * Construit la table des remplacements de placeholders du modèle Word selon la localité.
     * Communs aux deux modèles ; le central a le placeholder « PRESIDENT OU CHEF DE COMMISSION », le
     * régional a « LOCALITE DOSSIER » et « CHEF DE COMMISSION ». Le nom du signataire remplace
     * <strong>uniquement</strong> le placeholder (aucun libellé de rôle ajouté).
     */
    private java.util.Map<String, String> construireRemplacements(LettreRenvoi lettre, Dossier dossier,
            String nomSignataire, boolean centrale, String localiteLibelle) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH);
        String dateLettre = lettre.getDateLettre() == null ? "" : lettre.getDateLettre().format(fmt);
        String dateExamen = lettre.getDateExamen() == null ? "" : lettre.getDateExamen().format(fmt);
        String reference = dossier == null || dossier.getRefeDossier() == null ? "" : dossier.getRefeDossier();
        String entite = dossier == null || dossier.getIdEntiteContract() == null ? ""
                : entiteContractRepository.findById(dossier.getIdEntiteContract())
                        .map(EntiteContract::getLibelleEntite).orElse("");
        String corps = lettre.getCorpsLettre() == null ? "" : lettre.getCorpsLettre();
        String nom = nomSignataire == null ? "" : nomSignataire;

        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("<DATE_LETTRE>", dateLettre);
        m.put("<NOM_ENTITE_CONTRACT>", entite);
        m.put("<REFERENCE DOSSIER>", reference);
        m.put("<DATE EXAMEN>", dateExamen);
        m.put("<CORPS DE LA LETTRE>", corps);
        if (centrale) {
            m.put("<NOM ET PRENOMS DU PRESIDENT OU CHEF DE COMMISSION>", nom);
        } else {
            m.put("<LOCALITE DOSSIER>", localiteLibelle == null ? "" : localiteLibelle.toUpperCase(Locale.FRENCH));
            m.put("<NOM ET PRENOMS DU CHEF DE COMMISSION>", nom);
        }
        return m;
    }

    /** « Prénoms Nom » d'un contrôleur (signataire effectif), ou l'IM si introuvable. */
    private String nomComplet(String im) {
        if (im == null) {
            return "";
        }
        return controleurRepository.findById(im).map(c -> {
            String n = ((c.getPrenomsCont() == null ? "" : c.getPrenomsCont()) + " "
                    + (c.getNomCont() == null ? "" : c.getNomCont())).trim();
            return n.isBlank() ? im : n;
        }).orElse(im);
    }

    /**
     * Suppression d'une lettre (Administrateur).
     *
     * <p>⚠️ Audit 2026-08-27 (lot B) — la suppression n'avait aucune garde d'état : une lettre
     * <strong>signée</strong> partait comme un brouillon, alors qu'elle a été notifiée à la PRMP, a
     * suspendu l'examen (dossier EN_ATTENTE_PIECES) et a son PDF sur le FSX. Seuls les BROUILLON et
     * les SOUMIS (non encore signés) restent supprimables ; au-delà, 409 — comme pour la navette.</p>
     */
    public void delete(Integer id) {
        LettreRenvoi lettre = exigerExistante(id);
        if (StatutLettreRenvoi.SIGNE.name().equals(lettre.getStatut())) {
            throw new BusinessRuleException("Cette lettre de renvoi est signée"
                    + (lettre.getDateArchivage() != null ? " et archivée" : "")
                    + " : une pièce du circuit déjà notifiée à la PRMP ne se supprime pas.");
        }
        repository.deleteById(id);
    }

    /** Notifie la PRMP du dossier (lettre reçue) et les Assistants contrôleurs de la localité (copie). */
    private void notifierSignature(LettreRenvoi lettre) {
        Dossier dossier = lettre.getIdDossier() == null ? null
                : dossierRepository.findById(lettre.getIdDossier()).orElse(null);
        String ref = lettre.getRefLettre() != null ? lettre.getRefLettre() : ("n° " + lettre.getIdLettre());
        String refDossier = dossier == null || dossier.getRefeDossier() == null
                ? (lettre.getIdDossier() == null ? "?" : "n° " + lettre.getIdDossier()) : dossier.getRefeDossier();
        // PRMP du dossier (via PPM).
        // ⚠️ Audit 2026-08-27 (lot B) — la notification partait par E-MAIL SEUL (destinataireRef nul) :
        // la lettre qui suspend l'examen de son dossier n'apparaissait pas dans « mes notifications »
        // dès que l'e-mail du compte diffère de t_prmp.EMAIL_PRMP. Portée par la PRMP et rattachée au
        // dossier, comme PV_SIGNE — donc actionnable côté front.
        if (lettre.getIdDossier() != null) {
            String titre = "Lettre de renvoi reçue";
            String corps = "La lettre de renvoi " + ref + " concernant le dossier " + refDossier + " a été signée.";
            for (Ppm ppm : ppmRepository.findByIdDossier(lettre.getIdDossier())) {
                if (ppm.getIdPrmp() == null) {
                    continue;
                }
                String email = prmpRepository.findById(ppm.getIdPrmp()).map(Prmp::getEmailPrmp).orElse(null);
                notificationService.emettrePrmp(TypeNotification.LETTRE_RENVOI_RECUE, ppm.getIdPrmp(), email,
                        lettre.getIdDossier(), TypeObjet.DOSSIER, lettre.getIdDossier(), titre, corps);
            }
        }
        // Assistants contrôleurs de la localité de circuit (réception de l'examen) (copie).
        String localite = examenRepository.findLocaliteByExamen(lettre.getIdExamen()).orElse(null);
        if (localite != null) {
            String titre = "Copie de lettre de renvoi signée";
            String corps = "Lettre de renvoi signée " + ref + " (dossier " + refDossier + ").";
            for (Controleur a : controleurDirectory.assistantsControleurs(localite)) {
                notificationService.emettre(lettre.getIdDossier(), TypeNotification.LETTRE_RENVOI_COPIE,
                        a.getImControleur(), a.getEmailCont(), titre, corps);
            }
        }
    }

    private LettreRenvoi exigerExistante(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lettre de renvoi introuvable : " + id));
    }

    /** Propriété (§2.4) : seul le Membre attributaire de l'examen (Examen.imCtrlMembre) peut soumettre. */
    private void exigerProprietaire(LettreRenvoi lettre) {
        // ⚠️ Règle élargie (2026-08-01) — la lettre de renvoi est une action du PRÉSIDENT / CC
        // (clôture de navette du projet de PV) ; l'attributaire historique reste toléré en lecture
        // du flux (données existantes).
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == ProfilUtilisateur.PRESIDENT || profil == ProfilUtilisateur.CHEF_COMMISSION) {
            return;
        }
        String attributaire = examenRepository.findById(lettre.getIdExamen())
                .map(Examen::getImCtrlMembre).orElse(null);
        String moi = CurrentUser.ref().orElse(null);
        if (attributaire == null || !attributaire.equals(moi)) {
            throw new AccessDeniedException("Lettre réservée au Président / Chef de Commission (clôture de navette).");
        }
    }
}
