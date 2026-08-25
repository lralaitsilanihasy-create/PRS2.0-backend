package cnm.prs.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
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
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.LettreRenvoiMapper;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ExamenRepository;
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

    private final LettreRenvoiRepository repository;
    private final ExamenRepository examenRepository;
    private final DossierRepository dossierRepository;
    private final PpmRepository ppmRepository;
    private final PrmpRepository prmpRepository;
    private final ControleurDirectory controleurDirectory;
    private final ControleurRepository controleurRepository;
    private final NotificationService notificationService;
    private final LettreRenvoiLueRepository lueRepository;
    private final LettreRenvoiDocumentService documentService;
    private final ReferenceService referenceService;
    private final PvExamenRepository pvExamenRepository;
    /** ⚠️ 2026-08-19 — génération du PDF hors transaction : publication d'événement + tâche de fond. */
    private final ApplicationEventPublisher evenements;
    private final LettreRenvoiDocumentTache documentTache;

    public LettreRenvoiService(LettreRenvoiRepository repository, ExamenRepository examenRepository,
            DossierRepository dossierRepository, PpmRepository ppmRepository, PrmpRepository prmpRepository,
            ControleurDirectory controleurDirectory, ControleurRepository controleurRepository,
            NotificationService notificationService, LettreRenvoiLueRepository lueRepository,
            LettreRenvoiDocumentService documentService, ReferenceService referenceService,
            PvExamenRepository pvExamenRepository, ApplicationEventPublisher evenements,
            LettreRenvoiDocumentTache documentTache) {
        this.pvExamenRepository = pvExamenRepository;
        this.documentService = documentService;
        this.evenements = evenements;
        this.documentTache = documentTache;
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
        return lettres.stream().map(this::toDtoLecture).toList();
    }

    /** Lettres signées concernant les dossiers de la PRMP connectée (lecture seule). */
    @Transactional(readOnly = true)
    public List<LettreRenvoiDto> mesLettres() {
        String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (idPrmp == null) {
            return List.of();
        }
        return repository.findSigneesPourPrmp(idPrmp).stream().map(this::toDtoLecture).toList();
    }

    /**
     * DTO de <strong>lecture</strong> : nom du signataire, flag « lue » et {@code documentDisponible},
     * plus le rattrapage de génération décrit dans {@link #peuplerDocument}.
     */
    private LettreRenvoiDto toDtoLecture(LettreRenvoi entity) {
        LettreRenvoiDto dto = peuplerLue(peuplerNomSignataire(LettreRenvoiMapper.toDto(entity)));
        peuplerDocument(dto, entity);
        // ⚠️ 2026-08-19 — rattrapage des lettres signées SANS fichier (antérieures à la génération
        // post-commit, ou dont la génération a échoué) : la production part en arrière-plan à la
        // consultation — documentDisponible passera à true au prochain rafraîchissement, sans
        // requête lente. Le registre de la tâche dédoublonne les demandes concurrentes.
        if (Boolean.FALSE.equals(dto.getDocumentDisponible())
                && StatutLettreRenvoi.SIGNE.name().equals(entity.getStatut())
                && !documentTache.estEnCours(entity.getIdLettre())) {
            documentTache.genererEnArrierePlan(entity.getIdLettre());
        }
        return dto;
    }

    /**
     * Renseigne {@code documentDisponible} : « le PDF est prêt à télécharger <em>maintenant</em> »
     * (⚠️ 2026-08-19, même contrat que le PV signé — {@code false} pendant la fenêtre de génération
     * qui suit la signature). Aucun effet de bord.
     */
    private LettreRenvoiDto peuplerDocument(LettreRenvoiDto dto, LettreRenvoi entity) {
        if (dto != null) {
            dto.setDocumentDisponible(documentService.documentDisponible(entity));
        }
        return dto;
    }

    /**
     * Détail d'une lettre. Accès : périmètre de localité habituel <strong>ou</strong> PRMP propriétaire du
     * dossier pour une lettre {@code SIGNE} (sinon la PRMP serait hors périmètre → 403). À cette occasion,
     * la lettre est marquée « lue » pour la PRMP (trace {@code t_lettre_renvoi_lue}, idempotente, silencieuse).
     */
    public LettreRenvoiDto findById(Integer id) {
        LettreRenvoi entity = exigerExistante(id);
        String ref = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        boolean prmpProprietaire = estPrmpProprietaireSignee(entity);
        if (!prmpProprietaire) {
            // Périmètre de localité habituel (la PRMP non propriétaire reste hors périmètre → 403).
            Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        } else if (!lueRepository.existsByIdLettreAndIdPrmp(id, ref)) {
            // Marquage « lu » à la consultation par la PRMP propriétaire (silencieux, anti-doublon).
            lueRepository.save(new LettreRenvoiLue(null, id, ref, LocalDateTime.now()));
        }
        // toDtoLecture pose nomSignataire, le flag « lue » (après le marquage ci-dessus) et
        // documentDisponible, et relance au besoin la production du document en arrière-plan.
        return toDtoLecture(entity);
    }

    /** Vrai si l'appelant est la PRMP propriétaire du dossier d'une lettre {@code SIGNE}. */
    private boolean estPrmpProprietaireSignee(LettreRenvoi entity) {
        String ref = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        return CurrentUser.profil().filter(p -> p == ProfilUtilisateur.PRMP).isPresent()
                && ref != null
                && StatutLettreRenvoi.SIGNE.name().equals(entity.getStatut())
                && ppmRepository.existsByIdDossierAndIdPrmp(entity.getIdDossier(), ref);
    }

    /** Renseigne le flag {@code lue} pour la PRMP courante (trace {@code t_lettre_renvoi_lue}). */
    private LettreRenvoiDto peuplerLue(LettreRenvoiDto dto) {
        String ref = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (dto != null && dto.getIdLettre() != null) {
            dto.setLue(ref != null && lueRepository.existsByIdLettreAndIdPrmp(dto.getIdLettre(), ref));
        }
        return dto;
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
        LettreRenvoi enregistree = repository.save(lettre);
        return peuplerDocument(LettreRenvoiMapper.toDto(enregistree), enregistree);
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
        lettre.setCorpsLettre(dto.getCorpsLettre());
        LettreRenvoi enregistree = repository.save(lettre);
        return peuplerDocument(LettreRenvoiMapper.toDto(enregistree), enregistree);
    }

    /** Soumission par le Membre propriétaire (attributaire de l'examen) : BROUILLON → SOUMIS. */
    public LettreRenvoiDto soumettre(Integer id) {
        LettreRenvoi lettre = exigerExistante(id);
        exigerProprietaire(lettre);
        if (!StatutLettreRenvoi.BROUILLON.name().equals(lettre.getStatut())) {
            throw new BusinessRuleException("Soumission impossible : statut « " + lettre.getStatut() + " » (attendu BROUILLON).");
        }
        lettre.setStatut(StatutLettreRenvoi.SOUMIS.name());
        LettreRenvoi enregistree = repository.save(lettre);
        return peuplerDocument(LettreRenvoiMapper.toDto(enregistree), enregistree);
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
        boolean centrale = Localite.estCentrale(documentService.localiteDeLaLettre(lettre));
        if (!centrale && CurrentUser.profil().orElse(null) != ProfilUtilisateur.CHEF_COMMISSION) {
            throw new AccessDeniedException(
                    "Seul le Chef de Commission peut signer une lettre de renvoi pour une localité régionale.");
        }
        String im = CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Signataire non identifié."));
        lettre.setImSignataire(im);
        lettre.setStatut(StatutLettreRenvoi.SIGNE.name());
        // ⚠️ 2026-08-19 — la génération du PDF (Word piloté localement, plusieurs secondes) est SORTIE
        // du chemin de la signature : la lettre est marquée SIGNE et la réponse part immédiatement ; le
        // document est produit APRÈS COMMIT par LettreRenvoiDocumentTache, qui renseigne CHEMIN_DOCUMENT
        // quand il est prêt (documentDisponible=false entre-temps). Un échec de génération ne peut plus
        // faire échouer la signature — ni laisser un PDF orphelin sur le FSX après un rollback.
        evenements.publishEvent(new LettreRenvoiSigneeEvent(lettre.getIdLettre()));
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
        return peuplerDocument(peuplerNomSignataire(LettreRenvoiMapper.toDto(saved)), saved);
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
        LettreRenvoi saved = repository.save(lettre);
        return peuplerDocument(peuplerNomSignataire(LettreRenvoiMapper.toDto(saved)), saved);
    }

    /**
     * Document PDF de la lettre signée (téléchargement). Accès : périmètre de localité habituel ou PRMP
     * propriétaire (lettre {@code SIGNE}). Lit le fichier sur le FSX ({@code CHEMIN_DOCUMENT}), avec repli
     * sur le contenu en base ({@code DOCUMENT_PDF}).
     *
     * <p>⚠️ 2026-08-19 — fenêtre post-signature : {@code documentDisponible} est {@code false} tant que
     * la génération de fond n'a pas posé {@code CHEMIN_DOCUMENT}, un front à jour n'appelle donc pas ici
     * pendant l'intervalle. Si un client appelle quand même (le front actuel affiche le bouton dès
     * {@code SIGNE}), la <strong>régénération paresseuse</strong> ci-dessous sert le PDF — lentement mais
     * correctement, exactement comme la signature le faisait avant. Elle sert aussi de migration des
     * lettres signées dont la génération de fond a échoué. <strong>404</strong> seulement si la lettre
     * n'est pas signée (un brouillon n'a jamais eu de document).</p>
     */
    @Transactional
    public byte[] telechargerDocument(Integer id) {
        LettreRenvoi lettre = exigerExistante(id);
        if (!estPrmpProprietaireSignee(lettre)) {
            Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        }
        byte[] pdf = lireFsx(lettre.getCheminDocument());
        if (pdf == null && lettre.getDocumentPdf() != null && lettre.getDocumentPdf().length > 0) {
            return lettre.getDocumentPdf();   // repli compatibilité (lettres signées avant le stockage FSX)
        }
        if (pdf == null && StatutLettreRenvoi.SIGNE.name().equals(lettre.getStatut())) {
            String chemin = documentService.genererEtStocker(lettre).orElse(null);
            if (chemin != null) {
                lettre.setCheminDocument(chemin);
                repository.save(lettre);
                pdf = lireFsx(chemin);
            }
        }
        if (pdf == null) {
            throw new ResourceNotFoundException("Aucun document pour la lettre : " + id);
        }
        return pdf;
    }

    /** Contenu du fichier FSX, ou {@code null} si le chemin est vide, absent ou illisible. */
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

    public void delete(Integer id) {
        exigerExistante(id);
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
        if (lettre.getIdDossier() != null) {
            String titre = "Lettre de renvoi reçue";
            String corps = "La lettre de renvoi " + ref + " concernant le dossier " + refDossier + " a été signée.";
            for (Ppm ppm : ppmRepository.findByIdDossier(lettre.getIdDossier())) {
                if (ppm.getIdPrmp() == null) {
                    continue;
                }
                String email = prmpRepository.findById(ppm.getIdPrmp()).map(Prmp::getEmailPrmp).orElse(null);
                notificationService.emettre(lettre.getIdDossier(), TypeNotification.LETTRE_RENVOI_RECUE,
                        null, email, titre, corps);
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
