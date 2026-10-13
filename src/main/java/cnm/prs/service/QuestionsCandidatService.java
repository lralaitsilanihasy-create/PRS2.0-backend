package cnm.prs.service;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.QuestionCandidatDto;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Entreprise;
import cnm.prs.entity.QuestionCandidat;
import cnm.prs.entity.RetraitDao;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.AccesReserveException;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.PayloadTropVolumineuxException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.EntrepriseRepository;
import cnm.prs.repository.QuestionCandidatRepository;
import cnm.prs.repository.RetraitDaoRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ R7 (manuel révisé pour l'application, lot 2, §B2 ; V109, 2026-10-13) — <strong>les questions des candidats avant la date
 * limite</strong> (art. 35-II de la loi n° 2016-055 ; délai légal n° 3 : recevables jusqu'à <strong>J−10</strong>). Arbitrages au
 * backend du 13/10 : seul un candidat <strong>ayant retiré le DAO</strong> (registre des retraits) pose une question ; les questions
 * répondues paraissent, <strong>anonymisées</strong>, sur la page publique de la procédure, et chaque candidat ayant retiré le dossier
 * est notifié ; <strong>répondre = diffuser</strong> (une réponse par question, définitive, texte obligatoire, fichier facultatif).
 * Les questions et réponses sont jointes d'office au dossier de marché (pièce {@code QUESTIONS_REPONSES}).
 */
@Service
@Transactional
public class QuestionsCandidatService {

    public static final String PIECE_QUESTIONS = "QUESTIONS_REPONSES";
    /** Dix jours avant la date limite (délai légal n° 3). */
    public static final int JOURS_AVANT_LIMITE = DelaisLegaux.par(DelaisLegaux.QUESTIONS_CANDIDATS).orElseThrow().valeur();
    static final int LONGUEUR_MAX = 4000;
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    private final QuestionCandidatRepository questions;
    private final ProceduresEnLigneService procedures;
    private final FicheMarcheService fiches;
    private final RetraitDaoRepository retraits;
    private final CompteCandidatRepository candidats;
    private final EntrepriseRepository entreprises;
    private final NotificationService notifications;
    private final CeremonieService ceremonies;
    private final EvaluationService evaluation;
    private final GenerateurDocumentsFiche generateur;
    private final ParametreService parametres;
    private final Clock horloge;

    public QuestionsCandidatService(QuestionCandidatRepository questions, ProceduresEnLigneService procedures, FicheMarcheService fiches,
            RetraitDaoRepository retraits, CompteCandidatRepository candidats, EntrepriseRepository entreprises, NotificationService notifications,
            CeremonieService ceremonies, EvaluationService evaluation, GenerateurDocumentsFiche generateur, ParametreService parametres,
            Clock horloge) {
        this.questions = questions;
        this.procedures = procedures;
        this.fiches = fiches;
        this.retraits = retraits;
        this.candidats = candidats;
        this.entreprises = entreprises;
        this.notifications = notifications;
        this.ceremonies = ceremonies;
        this.evaluation = evaluation;
        this.generateur = generateur;
        this.parametres = parametres;
        this.horloge = horloge;
    }

    /** La fenêtre des questions : la date limite de remise, le dernier jour recevable (J−10), closes ? */
    public record Fenetre(LocalDateTime dateLimite, LocalDate clotureLe, boolean closes) {
    }

    // ------------------------------------------------------------------ le candidat

    /**
     * Un candidat pose une question : réservé au candidat ayant retiré au moins un document du DAO (403 {@code DOSSIER_NON_RETIRE}) ;
     * 400 {@code QUESTION_OBLIGATOIRE}, {@code QUESTION_TROP_LONGUE} ; 409 {@code QUESTIONS_CLOSES} après J−10 (details dateLimite,
     * clotureLe), {@code DATE_LIMITE_INCONNUE} ; 404 hors d'une procédure en ligne.
     */
    public QuestionCandidatDto.Question poser(String idCandidat, Long idDmc, QuestionCandidatDto.QuestionRequest r) {
        ProceduresEnLigneService.Lue l = procedures.exiger(idDmc);
        if (!retraits.existsByIdDmcAndIdCandidat(idDmc, idCandidat)) {
            throw new AccesReserveException("Les questions sont réservées aux candidats ayant retiré le dossier d'appel d'offres : retirez au "
                    + "moins un document du dossier, puis posez votre question.", "DOSSIER_NON_RETIRE");
        }
        String texte = nettoyer(r == null ? null : r.question());
        if (texte == null) {
            throw new BadRequestException("La question exige un texte.", "QUESTION_OBLIGATOIRE");
        }
        if (texte.length() > LONGUEUR_MAX) {
            throw new BadRequestException("La question ne dépasse pas " + LONGUEUR_MAX + " caractères.", "QUESTION_TROP_LONGUE");
        }
        Fenetre f = fenetre(l.etat());
        if (f.dateLimite() == null) {
            throw new BusinessRuleException("La date limite de remise des offres n'est pas connue : les questions ne sont pas ouvertes.",
                    "DATE_LIMITE_INCONNUE");
        }
        if (f.closes()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("dateLimite", RemiseElectronique.isoMinute(f.dateLimite()));
            details.put("clotureLe", f.clotureLe().toString());
            details.put("joursAvantLimite", JOURS_AVANT_LIMITE);
            throw new BusinessRuleException("Les questions ne sont plus recevables : elles le sont jusqu'au " + f.clotureLe().format(JOUR)
                    + ", " + JOURS_AVANT_LIMITE + " jours avant la date limite de remise des offres (" + f.dateLimite().toLocalDate().format(JOUR)
                    + ", art. 35-II).", "QUESTIONS_CLOSES", null, details);
        }
        QuestionCandidat q = new QuestionCandidat();
        q.setIdDmc(idDmc);
        q.setIdCandidat(idCandidat);
        q.setIdEntreprise(entreprises.findByIdCandidat(idCandidat).map(Entreprise::getIdEntreprise).orElse(null));
        q.setQuestion(texte);
        q.setPoseeLe(maintenant());
        questions.save(q);
        evaluation.tracerAttribution(idDmc, "QUESTION_POSEE", "Question n° " + q.getId() + " posée par " + auteur(q) + " ; réponse attendue "
                + "avant la date limite du " + f.dateLimite().toLocalDate().format(JOUR));
        ceremonies.notifierPrmp(idDmc, TypeNotification.QUESTION_POSEE, "Question d'un candidat", "Un candidat ayant retiré le dossier de la "
                + "procédure " + idDmc + " pose une question (n° " + q.getId() + ") ; répondez-y sur la plateforme : la réponse sera diffusée à "
                + "tous les candidats ayant retiré le dossier (art. 35-II).");
        return dto(q, true, idCandidat);
    }

    /** Les questions du candidat connecté (toutes, répondues ou non), avec la fenêtre ; 404 hors d'une procédure en ligne. */
    @Transactional(readOnly = true)
    public QuestionCandidatDto.Questions miennes(String idCandidat, Long idDmc) {
        ProceduresEnLigneService.Lue l = procedures.exiger(idDmc);
        return liste(fenetre(l.etat()), questions.findByIdDmcAndIdCandidatOrderByPoseeLeAscIdAsc(idDmc, idCandidat), false, idCandidat);
    }

    // ------------------------------------------------------------------ la page publique

    /** Les questions répondues, anonymisées, avec la fenêtre : sans session ; 404 hors d'une procédure en ligne. */
    @Transactional(readOnly = true)
    public QuestionCandidatDto.Questions publiques(Long idDmc) {
        ProceduresEnLigneService.Lue l = procedures.exiger(idDmc);
        return liste(fenetre(l.etat()), questions.findByIdDmcAndPublieeLeIsNotNullOrderByPoseeLeAscIdAsc(idDmc), false, null);
    }

    /** Le fichier joint à une réponse diffusée : sans session ; 404 sans fichier ou avant la diffusion. */
    @Transactional(readOnly = true)
    public QuestionCandidat fichier(Long idDmc, Long id) {
        QuestionCandidat q = questions.findById(id).filter(x -> x.getIdDmc().equals(idDmc)).orElse(null);
        if (q == null || q.getPublieeLe() == null || q.getReponseContenu() == null) {
            throw new ResourceNotFoundException("Aucun fichier joint à cette réponse.");
        }
        return q;
    }

    // ------------------------------------------------------------------ la PRMP

    /** Toutes les questions, avec leur auteur : lecteurs internes de la fiche (PRMP, UGPM, responsable…) ; 404 DMC inconnu. */
    @Transactional(readOnly = true)
    public QuestionCandidatDto.Questions lister(Long idDmc) {
        fiches.controlerLecture(idDmc);
        Fenetre f = fiches.etatValide(idDmc).map(this::fenetre).orElse(new Fenetre(null, null, true));
        return liste(f, questions.findByIdDmcOrderByPoseeLeAscIdAsc(idDmc), true, null);
    }

    /**
     * La PRMP répond, et diffuse : {@code texte} obligatoire (400 {@code TEXTE_OBLIGATOIRE}), {@code fichier} facultatif (PDF, JPEG ou PNG,
     * taille des pièces du candidat ; 400 {@code FORMAT_INVALIDE}) ; 409 {@code DEJA_REPONDUE} ; 403 hors PRMP ; 404 question inconnue.
     * Chaque candidat ayant retiré le dossier reçoit {@code QUESTION_REPONDUE}.
     */
    public QuestionCandidatDto.Question repondre(Long idDmc, Long id, String texte, MultipartFile fichier) {
        exigerPrmp(idDmc);
        QuestionCandidat q = questions.findById(id).filter(x -> x.getIdDmc().equals(idDmc))
                .orElseThrow(() -> new ResourceNotFoundException("Question introuvable : " + id + "."));
        if (q.getRepondueLe() != null) {
            throw new BusinessRuleException("Cette question a déjà sa réponse.", "DEJA_REPONDUE");
        }
        String t = nettoyer(texte);
        if (t == null) {
            throw new BadRequestException("La réponse exige un texte.", "TEXTE_OBLIGATOIRE");
        }
        byte[] contenu = null;
        String format = null;
        if (fichier != null && !fichier.isEmpty()) {
            try {
                contenu = fichier.getBytes();
            } catch (IOException ex) {
                throw new BadRequestException("Lecture du fichier impossible.", "FICHIER_ABSENT");
            }
            int mo = parametres.candidats().tailleMaxPieceMo();
            if (contenu.length > mo * 1024L * 1024L) {
                throw new PayloadTropVolumineuxException("Le fichier dépasse " + mo + " Mo.");
            }
            format = EntrepriseCandidatService.format(contenu);
            if (format == null) {
                throw new BadRequestException("Le fichier doit être un PDF, un JPEG ou un PNG (type lu sur le contenu).", "FORMAT_INVALIDE");
            }
        }
        LocalDateTime maintenant = maintenant();
        q.setReponse(t);
        q.setRepondueLe(maintenant);
        q.setReponduePar(acteur());
        q.setPublieeLe(maintenant);
        if (contenu != null) {
            String nom = fichier.getOriginalFilename() == null || fichier.getOriginalFilename().isBlank() ? "reponse"
                    : fichier.getOriginalFilename().replaceAll("[\\\\/]", "_");
            q.setReponseNom(nom.length() > 255 ? nom.substring(nom.length() - 255) : nom);
            q.setReponseFormat(format);
            q.setReponseTaille((long) contenu.length);
            q.setReponseContenu(contenu);
        }
        questions.save(q);
        evaluation.tracerAttribution(idDmc, "QUESTION_REPONDUE", "Question n° " + q.getId() + " : réponse diffusée à tous les candidats ayant "
                + "retiré le dossier" + (q.getReponseNom() == null ? "" : ", fichier joint"));
        diffuser(q);
        return dto(q, true, null);
    }

    /** Les questions et réponses en PDF : lecteurs internes de la fiche ; 404 sans question. */
    @Transactional(readOnly = true)
    public byte[] pdf(Long idDmc) {
        fiches.controlerLecture(idDmc);
        List<QuestionCandidat> toutes = questions.findByIdDmcOrderByPoseeLeAscIdAsc(idDmc);
        if (toutes.isEmpty()) {
            throw new ResourceNotFoundException("Aucune question posée sur la procédure " + idDmc + ".");
        }
        return pdf(idDmc, toutes);
    }

    /** La pièce « Questions et réponses » du dossier de marché : les questions répondues ; {@code null} s'il n'y en a pas. */
    @Transactional(readOnly = true)
    public byte[] pdfOuRien(Long idDmc) {
        List<QuestionCandidat> repondues = questions.findByIdDmcAndPublieeLeIsNotNullOrderByPoseeLeAscIdAsc(idDmc);
        return repondues.isEmpty() ? null : pdf(idDmc, repondues);
    }

    // ------------------------------------------------------------------ outils

    Fenetre fenetre(FicheMarcheService.EtatVersion etat) {
        LocalDateTime limite = ProceduresEnLigneService.dateLimite(etat);
        if (limite == null) {
            return new Fenetre(null, null, true);
        }
        LocalDate cloture = limite.toLocalDate().minusDays(JOURS_AVANT_LIMITE);
        return new Fenetre(limite, cloture, LocalDate.now(horloge).isAfter(cloture));
    }

    private void diffuser(QuestionCandidat q) {
        Set<String> destinataires = new LinkedHashSet<>();
        for (RetraitDao r : retraits.findByIdDmcOrderByDateRetraitAscIdRetraitAsc(q.getIdDmc())) {
            destinataires.add(r.getIdCandidat());
        }
        String titre = "Réponse à une question sur la procédure " + q.getIdDmc();
        String corps = "La PRMP a répondu à une question posée sur la procédure " + q.getIdDmc() + " (question n° " + q.getId()
                + ") ; la réponse est diffusée à tous les candidats ayant retiré le dossier, sur la page de la procédure (art. 35-II).";
        for (String idCandidat : destinataires) {
            CompteCandidat c = candidats.findById(idCandidat).orElse(null);
            notifications.emettreCandidat(TypeNotification.QUESTION_REPONDUE, idCandidat, c == null ? null : c.getEmail(), q.getIdDmc().intValue(),
                    TypeObjet.PROCEDURE, titre, corps);
        }
    }

    private QuestionCandidatDto.Questions liste(Fenetre f, List<QuestionCandidat> liste, boolean interne, String idCandidat) {
        List<QuestionCandidatDto.Question> out = new ArrayList<>();
        for (QuestionCandidat q : liste) {
            out.add(dto(q, interne, idCandidat));
        }
        return new QuestionCandidatDto.Questions(f.dateLimite() == null ? null : RemiseElectronique.isoMinute(f.dateLimite()), f.clotureLe(),
                f.closes(), JOURS_AVANT_LIMITE, out);
    }

    private QuestionCandidatDto.Question dto(QuestionCandidat q, boolean interne, String idCandidat) {
        boolean mienne = idCandidat != null && idCandidat.equals(q.getIdCandidat());
        return new QuestionCandidatDto.Question(q.getId(), q.getIdDmc(), q.getQuestion(), q.getPoseeLe(), interne ? auteur(q) : null,
                interne ? compte(q) : null, mienne, q.getRepondueLe() == null ? "EN_ATTENTE" : "REPONDUE", q.getReponse(), q.getReponseNom(),
                q.getReponseTaille(), q.getRepondueLe(), q.getPublieeLe());
    }

    private String auteur(QuestionCandidat q) {
        Optional<Entreprise> e = q.getIdEntreprise() == null ? Optional.empty() : entreprises.findById(q.getIdEntreprise());
        return e.map(Entreprise::getRaisonSociale).filter(s -> s != null && !s.isBlank()).orElseGet(() -> compte(q));
    }

    private String compte(QuestionCandidat q) {
        return candidats.findById(q.getIdCandidat()).map(CompteCandidat::getEmail).orElse(q.getIdCandidat());
    }

    private byte[] pdf(Long idDmc, List<QuestionCandidat> liste) {
        for (GenerateurDocumentsFiche.Fichier f : generateur.generer(document(idDmc, liste))) {
            if ("pdf".equals(f.extension())) {
                return f.contenu();
            }
        }
        return null;
    }

    private DocumentLibre document(Long idDmc, List<QuestionCandidat> liste) {
        List<DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "QUESTIONS DES CANDIDATS ET RÉPONSES"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "Procédure " + idDmc + " — questions posées par les candidats ayant retiré "
                + "le dossier d'appel d'offres et réponses de la Personne responsable des marchés publics, diffusées à l'ensemble des candidats "
                + "ayant retiré le dossier (art. 35-II de la loi n° 2016-055)."));
        int n = 0;
        for (QuestionCandidat q : liste) {
            n++;
            el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Question n° " + n + " (posée le " + q.getPoseeLe().format(HORODATAGE)
                    + ")"));
            el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, q.getQuestion()));
            if (q.getRepondueLe() == null) {
                el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "Réponse : en attente."));
            } else {
                el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "Réponse (diffusée le " + q.getPublieeLe().format(HORODATAGE) + ") : "
                        + q.getReponse() + (q.getReponseNom() == null ? "" : " [pièce jointe : " + q.getReponseNom() + "]")));
            }
        }
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "Établi le " + maintenant().format(HORODATAGE) + "."));
        return new DocumentLibre(PIECE_QUESTIONS, null, el, "Procédure " + idDmc + " — questions et réponses des candidats");
    }

    private void exigerPrmp(Long idDmc) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException("Les réponses aux questions des candidats se donnent par la PRMP de la fiche (art. 35-II).");
        }
        fiches.controlerLecture(idDmc);
    }

    private static String nettoyer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String acteur() {
        return CurrentUser.ref().or(CurrentUser::login).orElse(null);
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).withNano(0);
    }
}
