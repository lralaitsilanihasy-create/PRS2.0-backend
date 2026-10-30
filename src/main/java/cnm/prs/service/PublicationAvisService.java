package cnm.prs.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.config.HorlogeRecette;
import cnm.prs.dto.PublicationAvisDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.PublicationAvis;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.PublicationAvisRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ V154 (2026-10-30, demande front « pieces-ddm-donnees », §B2 ; décision du pilote sur Q1 : le journal de publication reste exigé) —
 * la <strong>publication de l'avis</strong> se dépose une seule fois, sur la procédure, après sa publication :
 * <ul>
 *   <li>par la PRMP de la fiche ou son UGPM, une fois l'avis spécifique imprimé (ou, en prestations intellectuelles, les lettres
 *       d'invitation produites) : 409 {@code AVIS_NON_PUBLIE} avant, {@code FICHE_NON_VALIDEE} sans version validée ;</li>
 *   <li>nature {@code JOURNAL} (journal obligatoire) ou, en prestations intellectuelles, {@code LETTRES_INVITATION} (lettres avec
 *       accusé ; journal sans objet) ; une nouvelle publication remplace la précédente ;</li>
 *   <li>le dossier de marché né de l'attribution la reçoit comme pièce fournie {@code PUBLICATION_AVIS} ({@link PiecesFournies}), sans
 *       copie du fichier. L'avis imprimé par l'application et sa date de première impression ne la remplacent pas : ce sont des données
 *       de la procédure, sans valeur de preuve de parution.</li>
 * </ul>
 */
@Service
@Transactional
public class PublicationAvisService {

    public static final String CODE_ABSENTE = "PUBLICATION_AVIS_ABSENTE";

    private final PublicationAvisRepository publications;
    private final FicheMarcheService fiches;
    private final FicheMarcheRepository ficheRepository;
    private final DocumentsFicheMarcheService documents;
    private final DossierRepository dossiers;
    private final AttributionRepository attributions;
    private final JournalDossierService journal;
    private final ObjectProvider<DossierService> dossierService;

    public PublicationAvisService(PublicationAvisRepository publications, FicheMarcheService fiches, FicheMarcheRepository ficheRepository,
            DocumentsFicheMarcheService documents, DossierRepository dossiers, AttributionRepository attributions,
            JournalDossierService journal, ObjectProvider<DossierService> dossierService) {
        this.publications = publications;
        this.fiches = fiches;
        this.ficheRepository = ficheRepository;
        this.documents = documents;
        this.dossiers = dossiers;
        this.attributions = attributions;
        this.journal = journal;
        this.dossierService = dossierService;
    }

    /**
     * Dépose la publication de l'avis de la procédure {@code idDmc} (multipart : {@code fichier} PDF, {@code journal}, {@code numero}
     * facultatif, {@code dateParution} AAAA-MM-JJ) ; 400 nominatif ; 403 ; 409 {@code FICHE_NON_VALIDEE}, {@code AVIS_NON_PUBLIE}.
     * Journal du dossier né de la fiche, s'il existe : {@code PUBLICATION_AVIS_DEPOSEE}.
     */
    public PublicationAvisDto deposer(Long idDmc, MultipartFile fichier, String journalNom, String numero, String dateParution) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil != ProfilUtilisateur.PRMP && profil != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("La publication de l'avis se dépose par la PRMP de la fiche ou son UGPM.");
        }
        fiches.controlerLecture(idDmc);
        FicheMarcheService.EtatVersion etat = fiches.etatValide(idDmc).orElseThrow(() -> new BusinessRuleException(
                "La fiche de la procédure n'a pas de version validée : l'avis n'est pas encore publié.", "FICHE_NON_VALIDEE"));
        boolean lettres = ModelesDao.sigleLettre(etat.categorie()) != null;
        if (documents.listerAvis(ficheRepository.versions(idDmc)).isEmpty()) {
            throw new BusinessRuleException(lettres ? "Les lettres d'invitation ne sont pas encore produites : leur preuve d'envoi se dépose "
                    + "après." : "L'avis spécifique n'est pas encore imprimé : sa publication se dépose après.", "AVIS_NON_PUBLIE");
        }
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        byte[] contenu = null;
        if (fichier == null || fichier.isEmpty()) {
            erreurs.add(new ErrorResponse.FieldError("fichier", lettres ? "Le scan des lettres d'invitation avec accusé est obligatoire."
                    : "Le scan du journal est obligatoire."));
        } else {
            try {
                contenu = fichier.getBytes();
            } catch (IOException e) {
                throw new IllegalStateException("Lecture du fichier impossible.", e);
            }
            if (contenu.length < 4 || contenu[0] != '%' || contenu[1] != 'P' || contenu[2] != 'D' || contenu[3] != 'F') {
                erreurs.add(new ErrorResponse.FieldError("fichier", "Le scan est un PDF."));
            }
        }
        String j = texte(journalNom, "journal", "Le nom du journal", 200, !lettres, erreurs);
        String n = texte(numero, "numero", "Le numéro du journal", 50, false, erreurs);
        LocalDate d = null;
        String champDate = lettres ? "La date d'envoi des lettres" : "La date de parution";
        if (dateParution == null || dateParution.isBlank()) {
            erreurs.add(new ErrorResponse.FieldError("dateParution", champDate + " est obligatoire."));
        } else {
            try {
                d = LocalDate.parse(dateParution.trim());
                if (d.isAfter(LocalDate.now(HorlogeRecette.HORLOGE))) {
                    erreurs.add(new ErrorResponse.FieldError("dateParution", champDate + " n'est pas dans le futur."));
                }
            } catch (DateTimeParseException e) {
                erreurs.add(new ErrorResponse.FieldError("dateParution", champDate + " est au format AAAA-MM-JJ."));
            }
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        LocalDateTime maintenant = LocalDateTime.now(HorlogeRecette.HORLOGE);
        publications.findFirstByIdDmcAndRemplaceeLeIsNull(idDmc).ifPresent(p -> {
            p.setRemplaceeLe(maintenant);
            publications.saveAndFlush(p);
        });
        PublicationAvis p = new PublicationAvis();
        p.setIdDmc(idDmc);
        p.setNature(lettres ? PublicationAvis.LETTRES_INVITATION : PublicationAvis.JOURNAL);
        p.setJournal(j);
        p.setNumero(n);
        p.setDateParution(d);
        String original = fichier.getOriginalFilename();
        p.setNomFichier(original == null || original.isBlank() ? "publication-avis_" + idDmc + ".pdf" : original.trim());
        p.setContenu(contenu);
        p.setTaille((long) contenu.length);
        p.setDeposePar(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        p.setDeposeLe(maintenant);
        p = publications.save(p);
        String quoi = lettres ? "Lettres d'invitation avec accusé déposées, envoyées le " + d
                : "Publication de l'avis déposée : " + j + (n == null ? "" : " n° " + n) + " du " + d;
        dossiers.findIdDossierByIdDmc(idDmc).ifPresent(idDossier -> journal.tracer(idDossier, "PUBLICATION_AVIS_DEPOSEE", quoi));
        return dto(p);
    }

    // ------------------------------------------------------------------ lecture

    /** La publication en vigueur de la procédure (lecteurs de la fiche) ; 404 {@code PUBLICATION_AVIS_ABSENTE}. */
    @Transactional(readOnly = true)
    public PublicationAvisDto lire(Long idDmc) {
        fiches.controlerLecture(idDmc);
        return dto(exiger(idDmc));
    }

    /** Le scan en vigueur de la procédure, même garde que {@link #lire(Long)}. */
    @Transactional(readOnly = true)
    public PublicationAvis contenu(Long idDmc) {
        fiches.controlerLecture(idDmc);
        return exiger(idDmc);
    }

    /** La publication en vigueur, lue depuis un dossier (le dossier né de la fiche, ou le dossier de marché de son attribution). */
    @Transactional(readOnly = true)
    public PublicationAvisDto lireDuDossier(Integer idDossier) {
        return dto(exiger(procedureDuDossier(idDossier)));
    }

    /** Le scan en vigueur, lu depuis un dossier. */
    @Transactional(readOnly = true)
    public PublicationAvis contenuDuDossier(Integer idDossier) {
        return exiger(procedureDuDossier(idDossier));
    }

    /** La publication en vigueur de la procédure, vide sans elle. Sans contrôle de périmètre. */
    @Transactional(readOnly = true)
    public Optional<PublicationAvis> enVigueur(Long idDmc) {
        return idDmc == null ? Optional.empty() : publications.findFirstByIdDmcAndRemplaceeLeIsNull(idDmc);
    }

    private Long procedureDuDossier(Integer idDossier) {
        dossierService.getObject().controlerVisibilite(idDossier);
        Dossier d = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        Long idDmc = d.getIdDmc() != null ? d.getIdDmc() : attributions.findFirstByIdDossier(idDossier).map(a -> a.getIdDmc()).orElse(null);
        if (idDmc == null) {
            throw new ResourceNotFoundException("Ce dossier ne relève d'aucune procédure conduite dans l'application.");
        }
        return idDmc;
    }

    private PublicationAvis exiger(Long idDmc) {
        return enVigueur(idDmc).orElseThrow(() -> new ResourceNotFoundException(
                "Aucune publication de l'avis n'est déposée sur cette procédure.", CODE_ABSENTE));
    }

    private static PublicationAvisDto dto(PublicationAvis p) {
        return new PublicationAvisDto(p.getIdPublication(), p.getIdDmc(), p.getNature(), p.getJournal(), p.getNumero(), p.getDateParution(),
                p.getNomFichier(), p.getTaille(), p.getDeposePar(), p.getDeposeLe());
    }

    private static String texte(String v, String champ, String nom, int max, boolean obligatoire, List<ErrorResponse.FieldError> erreurs) {
        String t = v == null ? null : v.trim();
        if (t == null || t.isEmpty()) {
            if (obligatoire) {
                erreurs.add(new ErrorResponse.FieldError(champ, nom + " est obligatoire."));
            }
            return null;
        }
        if (t.length() > max) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " dépasse " + max + " caractères."));
            return null;
        }
        return t;
    }
}
