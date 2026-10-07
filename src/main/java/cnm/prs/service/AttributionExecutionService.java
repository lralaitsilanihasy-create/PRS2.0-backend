package cnm.prs.service;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.AttributionDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.AttributionPiece;
import cnm.prs.entity.AttributionRecours;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Offre;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.PayloadTropVolumineuxException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AttributionLettreRepository;
import cnm.prs.repository.AttributionPieceRepository;
import cnm.prs.repository.AttributionRecoursRepository;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>L'évaluation des offres, lot 2, tranche 2c</strong> (demande front du 2026-10-07, §B4.3, §B4.4, §B5 ; V81) — de la mise au
 * point à l'avis d'attribution, lot par lot, après l'information des candidats (tranche 2b) :
 * <ul>
 *   <li>la <strong>mise au point</strong> (art. 35-VIII) : un rapport, un fichier facultatif ; le serveur ne juge pas de la substance ;</li>
 *   <li>les <strong>recours</strong> déclarés par la PRMP (Q5, titre VIII de la loi n° 2016-055) : le réexamen (art. 79) n'arrête rien
 *   mais sa réponse est due sous 10 jours ; la révision ARMP (art. 80) et le référé (art. 78) ferment la signature jusqu'à leur décision,
 *   20 jours au plus ;</li>
 *   <li>les <strong>pièces fiscales et sociales</strong> de l'attributaire (art. 20-I), sous 15 jours de la notification de l'attribution
 *   (la lettre d'attribution) : fiscale de moins de six mois, sociale de moins de trois mois ; la PRMP les vérifie ; à défaut, elle
 *   <strong>retire</strong> le marché (la réattribution suit en tranche 2d) ;</li>
 *   <li>la <strong>signature</strong> (après le délai d'attente, sans recours suspensif, les deux pièces reconnues conformes), son
 *   <strong>enregistrement</strong> (date et justificatif, Q6), la <strong>notification</strong> (jamais sans enregistrement) et sa
 *   réception, date d'effet du marché (art. 54) ;</li>
 *   <li>l'<strong>avis d'attribution</strong> (art. 53), sous 30 jours de la notification, produit par le serveur (modèle provisoire, Q7)
 *   et publié sur la page de la procédure.</li>
 * </ul>
 * Tout geste est réservé à la PRMP de la fiche (Q9 : ce qui engage est nominatif) ; la lecture suit celle de l'attribution.
 */
@Service
@Transactional
public class AttributionExecutionService {

    public static final int JOURS_PIECES = 15;
    public static final int MOIS_FISCALE = 6;
    public static final int MOIS_SOCIALE = 3;
    public static final int JOURS_REEXAMEN = 10;
    public static final int JOURS_SUSPENSION = 20;
    public static final int JOURS_AVIS = 30;
    private static final Set<String> TYPES_RECOURS = Set.of(AttributionRecours.REEXAMEN, AttributionRecours.REVISION_ARMP, AttributionRecours.REFERE);
    private static final Set<String> ISSUES = Set.of("REJETE", "ACCUEILLI", "AUTRE");
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    private final AttributionRepository attributions;
    private final AttributionRecoursRepository recoursRepository;
    private final AttributionPieceRepository piecesRepository;
    private final AttributionLettreRepository lettres;
    private final OffreRepository offres;
    private final CompteCandidatRepository candidats;
    private final PrmpRepository prmpRepository;
    private final DossierMecRepository dmcRepository;
    private final NotificationService notifications;
    private final CeremonieService ceremonies;
    private final EvaluationService evaluation;
    private final FicheMarcheService fiches;
    private final ValeursPpmService valeursPpm;
    private final GenerateurDocumentsFiche generateur;
    private final ParametreService parametres;
    private final Clock horloge;

    public AttributionExecutionService(AttributionRepository attributions, AttributionRecoursRepository recoursRepository,
            AttributionPieceRepository piecesRepository, AttributionLettreRepository lettres, OffreRepository offres,
            CompteCandidatRepository candidats, PrmpRepository prmpRepository, DossierMecRepository dmcRepository,
            NotificationService notifications, CeremonieService ceremonies, EvaluationService evaluation, FicheMarcheService fiches,
            ValeursPpmService valeursPpm, GenerateurDocumentsFiche generateur, ParametreService parametres, Clock horloge) {
        this.attributions = attributions;
        this.recoursRepository = recoursRepository;
        this.piecesRepository = piecesRepository;
        this.lettres = lettres;
        this.offres = offres;
        this.candidats = candidats;
        this.prmpRepository = prmpRepository;
        this.dmcRepository = dmcRepository;
        this.notifications = notifications;
        this.ceremonies = ceremonies;
        this.evaluation = evaluation;
        this.fiches = fiches;
        this.valeursPpm = valeursPpm;
        this.generateur = generateur;
        this.parametres = parametres;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ lecture

    /** Les sections de la tranche 2c d'un lot, pour {@code AttributionDto.LotAttribution}. */
    record Suite(AttributionDto.MiseAuPoint miseAuPoint, List<AttributionDto.Recours> recours, AttributionDto.Signature signature,
            AttributionDto.Enregistrement enregistrement, AttributionDto.NotificationMarche notification, AttributionDto.AvisAttribution avis,
            AttributionDto.PiecesAttributaire pieces, AttributionDto.Retrait retrait) {

        static final Suite VIDE = new Suite(null, List.of(), null, null, null, null, null, null);
    }

    /** L'état du lot au-delà de l'information : {@code RETIRE}, {@code PUBLIE}, {@code NOTIFIE}, {@code SIGNE}, sinon l'état d'avant. */
    static String etat(Attribution a, String avant) {
        return a.getRetireLe() != null ? Attribution.RETIRE : a.getAvisPublieLe() != null ? Attribution.PUBLIE
                : a.getNotifieLe() != null ? Attribution.NOTIFIE : a.getSigneLe() != null ? Attribution.SIGNE : avant;
    }

    @Transactional(readOnly = true)
    Suite suite(Attribution a) {
        if (a.getAttribueLe() == null) {
            return Suite.VIDE;
        }
        List<AttributionPiece> toutes = piecesRepository.findByIdDmcAndLotOrderByIdAsc(a.getIdDmc(), a.getLot());
        AttributionDto.MiseAuPoint map = a.getMiseAuPointLe() == null ? null
                : new AttributionDto.MiseAuPoint(a.getRapportMiseAuPoint(), a.getMiseAuPointLe(), a.getMiseAuPointPar(),
                        derniere(toutes, AttributionPiece.MISE_AU_POINT));
        List<AttributionDto.Recours> recours = recoursRepository.findByIdDmcAndLotOrderByDateReceptionAscIdAsc(a.getIdDmc(), a.getLot()).stream()
                .map(r -> recoursDto(r, toutes)).toList();
        AttributionDto.Signature sig = a.getSigneLe() == null ? null
                : new AttributionDto.Signature(a.getDateSignature(), a.getSigneLe(), a.getSignePar(), derniere(toutes, AttributionPiece.MARCHE_SIGNE));
        AttributionDto.Enregistrement enr = a.getEnregistreLe() == null ? null
                : new AttributionDto.Enregistrement(a.getDateEnregistrement(), a.getReferenceEnregistrement(), a.getEnregistreLe(),
                        derniere(toutes, AttributionPiece.ENREGISTREMENT));
        AttributionDto.NotificationMarche notif = a.getNotifieLe() == null ? null
                : new AttributionDto.NotificationMarche(a.getDateNotification(), a.getNotifieLe(), a.getNotifiePar(), a.getNotificationRecueLe(),
                        a.getReceptionDeclaree());
        AttributionDto.AvisAttribution avis = a.getNotifieLe() == null ? null
                : new AttributionDto.AvisAttribution(a.getDateNotification().plusDays(JOURS_AVIS), a.getDatePublicationAvis(), a.getAvisPublieLe(),
                        a.getAvisPubliePar(), a.getAvisPublieLe() != null);
        AttributionDto.Retrait ret = a.getRetireLe() == null ? null : new AttributionDto.Retrait(a.getRetireLe(), a.getRetirePar(), a.getMotifRetrait());
        return new Suite(map, recours, sig, enr, notif, avis, piecesAttributaire(a, toutes), ret);
    }

    /** Les pièces fiscales et sociales, une fois les candidats informés ; nul avant. */
    AttributionDto.PiecesAttributaire piecesAttributaire(Attribution a, List<AttributionPiece> toutes) {
        if (a.getInformeLe() == null) {
            return null;
        }
        LocalDate echeance = echeancePieces(a);
        List<AttributionDto.PieceAttributaire> l = toutes.stream()
                .filter(p -> AttributionPiece.FISCALE.equals(p.getNature()) || AttributionPiece.SOCIALE.equals(p.getNature()))
                .map(p -> new AttributionDto.PieceAttributaire(p.getId(), p.getNature(), p.getDateDelivrance(), p.getNom(), p.getTaille(),
                        p.getDeposeLe(), p.getConforme(), p.getMotif(), p.getVerifieeLe()))
                .toList();
        return new AttributionDto.PiecesAttributaire(echeance, aujourdhui().isAfter(echeance), conforme(toutes, AttributionPiece.FISCALE),
                conforme(toutes, AttributionPiece.SOCIALE), l);
    }

    /** Les pièces de l'attributaire, pour le candidat (son résultat) : nul pour un candidat non retenu. */
    @Transactional(readOnly = true)
    AttributionDto.PiecesAttributaire piecesPourCandidat(Attribution a, String idOffre) {
        return idOffre.equals(a.getIdOffreAttribuee()) ? piecesAttributaire(a, piecesRepository.findByIdDmcAndLotOrderByIdAsc(a.getIdDmc(), a.getLot()))
                : null;
    }

    /** L'accusé de lecture de la notification : la première consultation par l'attributaire, une fois le marché notifié. */
    void accuserNotification(Attribution a, String idOffre) {
        if (a.getNotifieLe() != null && a.getNotificationRecueLe() == null && idOffre.equals(a.getIdOffreAttribuee())) {
            a.setNotificationRecueLe(maintenant());
            a.setReceptionDeclaree(false);
            attributions.save(a);
            evaluation.tracerAttribution(a.getIdDmc(), "NOTIFICATION_RECUE", "Lot " + a.getLot() + " : notification reçue par l'attributaire "
                    + "(accusé de lecture de la plateforme) ; le marché prend effet le " + a.getNotificationRecueLe().format(HORODATAGE));
        }
    }

    // ------------------------------------------------------------------ §B4.3 la mise au point (art. 35-VIII)

    /** La mise au point : rapport obligatoire (400 {@code RAPPORT_OBLIGATOIRE}), fichier facultatif ; 409 {@code NON_ATTRIBUE}, {@code DEJA_SIGNE}, {@code LOT_RETIRE}. */
    public void miseAuPoint(Long idDmc, Integer lot, String rapport, MultipartFile fichier) {
        Attribution a = exigerAttribue(idDmc, lot);
        exigerNonSigne(a);
        String r = nettoyer(rapport);
        if (r == null) {
            throw new BadRequestException("La mise au point exige un rapport.", "RAPPORT_OBLIGATOIRE");
        }
        Televerse t = lire(fichier, false);
        a.setRapportMiseAuPoint(r);
        a.setMiseAuPointLe(maintenant());
        a.setMiseAuPointPar(acteur());
        attributions.save(a);
        enregistrerFichier(a, AttributionPiece.MISE_AU_POINT, null, t);
        evaluation.tracerAttribution(idDmc, "MISE_AU_POINT", "Lot " + lot + " : rapport de mise au point" + (t == null ? "" : ", fichier joint"));
    }

    // ------------------------------------------------------------------ Q5 les recours (titre VIII)

    /**
     * Déclare un recours reçu : {@code type} ∈ {@code REEXAMEN} · {@code REVISION_ARMP} · {@code REFERE} (400 {@code TYPE_INVALIDE}),
     * {@code dateReception} (au plus aujourd'hui), {@code requerant}, {@code objet} (400 {@code CHAMP_OBLIGATOIRE}, {@code DATE_INVALIDE}),
     * fichier facultatif ; 409 {@code NON_ATTRIBUE}.
     */
    public void declarerRecours(Long idDmc, Integer lot, String type, LocalDate dateReception, String requerant, String objet, MultipartFile fichier) {
        Attribution a = exigerAttribue(idDmc, lot);
        String t = type == null ? null : type.trim().toUpperCase(Locale.ROOT);
        if (t == null || !TYPES_RECOURS.contains(t)) {
            throw new BadRequestException("Type de recours inconnu : REEXAMEN, REVISION_ARMP ou REFERE.", "TYPE_INVALIDE");
        }
        if (dateReception == null || nettoyer(requerant) == null || nettoyer(objet) == null) {
            throw new BadRequestException("Le recours exige sa date de réception, le requérant et son objet.", "CHAMP_OBLIGATOIRE");
        }
        if (dateReception.isAfter(aujourdhui())) {
            throw new BadRequestException("La date de réception du recours ne peut être à venir.", "DATE_INVALIDE");
        }
        Televerse f = lire(fichier, false);
        AttributionRecours r = new AttributionRecours();
        r.setIdDmc(idDmc);
        r.setLot(a.getLot());
        r.setType(t);
        r.setDateReception(dateReception);
        r.setRequerant(nettoyer(requerant));
        r.setObjet(nettoyer(objet));
        r.setDeclareLe(maintenant());
        r.setDeclarePar(acteur());
        recoursRepository.save(r);
        enregistrerFichier(a, AttributionPiece.RECOURS, r.getId(), f);
        evaluation.tracerAttribution(idDmc, "RECOURS", "Lot " + lot + " : recours " + t + " de " + r.getRequerant() + ", reçu le "
                + dateReception.format(JOUR) + (suspensif(t) ? " — la signature est suspendue jusqu'à la décision, au plus tard le "
                        + dateReception.plusDays(JOURS_SUSPENSION).format(JOUR) : " — réponse due au plus tard le "
                                + dateReception.plusDays(JOURS_REEXAMEN).format(JOUR)));
    }

    /**
     * La décision d'un recours : {@code issue} ∈ {@code REJETE} · {@code ACCUEILLI} · {@code AUTRE} (400 {@code ISSUE_INVALIDE}), date
     * entre la réception et aujourd'hui (400 {@code DATE_INVALIDE}), motif obligatoire (400 {@code MOTIF_OBLIGATOIRE}), fichier
     * facultatif ; 409 {@code DEJA_DECIDE} ; 404 recours inconnu de la procédure.
     */
    public void deciderRecours(Long idDmc, Long id, LocalDate date, String issue, String motif, MultipartFile fichier) {
        exigerPrmp(idDmc);
        AttributionRecours r = recoursRepository.findById(id).filter(x -> x.getIdDmc().equals(idDmc))
                .orElseThrow(() -> new ResourceNotFoundException("Recours introuvable : " + id + "."));
        if (r.getDecideLe() != null) {
            throw new BusinessRuleException("Ce recours a déjà sa décision.", "DEJA_DECIDE");
        }
        String i = issue == null ? null : issue.trim().toUpperCase(Locale.ROOT);
        if (i == null || !ISSUES.contains(i)) {
            throw new BadRequestException("Issue inconnue : REJETE, ACCUEILLI ou AUTRE.", "ISSUE_INVALIDE");
        }
        if (date == null || date.isBefore(r.getDateReception()) || date.isAfter(aujourdhui())) {
            throw new BadRequestException("La date de la décision se situe entre la réception du recours et aujourd'hui.", "DATE_INVALIDE");
        }
        if (nettoyer(motif) == null) {
            throw new BadRequestException("La décision exige son motif.", "MOTIF_OBLIGATOIRE");
        }
        Televerse f = lire(fichier, false);
        r.setDateDecision(date);
        r.setIssue(i);
        r.setMotifDecision(nettoyer(motif));
        r.setDecideLe(maintenant());
        r.setDecidePar(acteur());
        recoursRepository.save(r);
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, r.getLot())).orElseThrow();
        enregistrerFichier(a, AttributionPiece.DECISION_RECOURS, r.getId(), f);
        evaluation.tracerAttribution(idDmc, "DECISION_RECOURS", "Lot " + r.getLot() + " : recours " + r.getType() + " de " + r.getRequerant()
                + " — décision du " + date.format(JOUR) + " : " + i.toLowerCase(Locale.FRENCH) + " (" + r.getMotifDecision() + ")");
    }

    private static boolean suspensif(String type) {
        return AttributionRecours.REVISION_ARMP.equals(type) || AttributionRecours.REFERE.equals(type);
    }

    /** Un recours suspensif sans décision, dont la suspension (20 jours au plus) court encore à cette date. */
    private static boolean bloquant(AttributionRecours r, LocalDate le) {
        return suspensif(r.getType()) && r.getDecideLe() == null && !le.isAfter(r.getDateReception().plusDays(JOURS_SUSPENSION));
    }

    private AttributionDto.Recours recoursDto(AttributionRecours r, List<AttributionPiece> toutes) {
        boolean s = suspensif(r.getType());
        AttributionDto.DecisionRecours d = r.getDecideLe() == null ? null
                : new AttributionDto.DecisionRecours(r.getDateDecision(), r.getIssue(), r.getMotifDecision(), r.getDecideLe(), r.getDecidePar(),
                        fichiers(toutes, AttributionPiece.DECISION_RECOURS, r.getId()));
        return new AttributionDto.Recours(r.getId(), r.getType(), r.getDateReception(), r.getRequerant(), r.getObjet(), r.getDeclareLe(),
                r.getDeclarePar(), fichiers(toutes, AttributionPiece.RECOURS, r.getId()), s,
                s ? r.getDateReception().plusDays(JOURS_SUSPENSION) : null, s ? null : r.getDateReception().plusDays(JOURS_REEXAMEN),
                bloquant(r, aujourdhui()), d);
    }

    // ------------------------------------------------------------------ §B5 les pièces fiscales et sociales (art. 20-I)

    /** L'échéance des pièces : quinze jours après la notification de l'attribution (l'information des candidats). */
    static LocalDate echeancePieces(Attribution a) {
        return a.getInformeLe().toLocalDate().plusDays(JOURS_PIECES);
    }

    /**
     * L'attributaire dépose une pièce : {@code type} ∈ {@code FISCALE} · {@code SOCIALE} (400 {@code TYPE_INVALIDE}),
     * {@code dateDelivrance} (400 {@code DATE_DELIVRANCE_OBLIGATOIRE}, {@code PIECE_PERIMEE} : fiscale de plus de six mois, sociale de
     * plus de trois mois à la notification de l'attribution, ou date à venir), fichier obligatoire ; 409 {@code NON_ATTRIBUTAIRE},
     * {@code DELAI_DEPASSE}, {@code DEJA_SIGNE}, {@code LOT_RETIRE}.
     */
    public AttributionDto.PiecesAttributaire deposerPiece(String idCandidat, String idOffre, String type, LocalDate dateDelivrance,
            MultipartFile fichier) {
        Offre o = sienne(idCandidat, idOffre);
        Attribution a = attributions.findByIdOffreAttribuee(idOffre).stream().filter(x -> x.getInformeLe() != null)
                .findFirst().orElseThrow(() -> new BusinessRuleException("Votre offre n'est pas attributaire d'un marché.", "NON_ATTRIBUTAIRE"));
        if (a.getRetireLe() != null) {
            throw new BusinessRuleException("Le marché vous a été retiré.", "LOT_RETIRE");
        }
        if (a.getSigneLe() != null) {
            throw new BusinessRuleException("Le marché est signé : les pièces ne se déposent plus.", "DEJA_SIGNE");
        }
        if (aujourdhui().isAfter(echeancePieces(a))) {
            throw new BusinessRuleException("Le délai de dépôt des pièces est dépassé (" + echeancePieces(a).format(JOUR) + ").", "DELAI_DEPASSE");
        }
        String t = type == null ? null : type.trim().toUpperCase(Locale.ROOT);
        if (!AttributionPiece.FISCALE.equals(t) && !AttributionPiece.SOCIALE.equals(t)) {
            throw new BadRequestException("Type de pièce inconnu : FISCALE ou SOCIALE.", "TYPE_INVALIDE");
        }
        if (dateDelivrance == null) {
            throw new BadRequestException("La date de délivrance de la pièce est à indiquer.", "DATE_DELIVRANCE_OBLIGATOIRE");
        }
        LocalDate reference = a.getInformeLe().toLocalDate();
        LocalDate limite = reference.minusMonths(AttributionPiece.FISCALE.equals(t) ? MOIS_FISCALE : MOIS_SOCIALE);
        if (dateDelivrance.isBefore(limite) || dateDelivrance.isAfter(aujourdhui())) {
            throw new BadRequestException("La pièce " + (AttributionPiece.FISCALE.equals(t) ? "fiscale doit dater de moins de six mois"
                    : "sociale doit dater de moins de trois mois") + " à la notification de l'attribution (délivrée le " + limite.format(JOUR)
                    + " au plus tôt).", "PIECE_PERIMEE");
        }
        Televerse f = lire(fichier, true);
        AttributionPiece p = enregistrerFichier(a, t, null, f);
        p.setDateDelivrance(dateDelivrance);
        piecesRepository.save(p);
        evaluation.tracerAttribution(a.getIdDmc(), "PIECE_ATTRIBUTAIRE", "Lot " + a.getLot() + ", offre n° " + o.getNumero() + " : pièce "
                + t.toLowerCase(Locale.FRENCH) + " déposée (délivrée le " + dateDelivrance.format(JOUR) + ")");
        ceremonies.notifierPrmp(a.getIdDmc(), TypeNotification.PIECES_ATTRIBUTAIRE_DEPOSEES, "Pièce de l'attributaire déposée",
                "L'attributaire du lot " + a.getLot() + " (procédure " + a.getIdDmc() + ") a déposé sa pièce " + t.toLowerCase(Locale.FRENCH)
                        + " ; vérifiez-la sur la plateforme.");
        return piecesAttributaire(a, piecesRepository.findByIdDmcAndLotOrderByIdAsc(a.getIdDmc(), a.getLot()));
    }

    /** Les pièces de l'attributaire, pour lui ; 409 {@code NON_ATTRIBUTAIRE}. */
    @Transactional(readOnly = true)
    public AttributionDto.PiecesAttributaire piecesDuCandidat(String idCandidat, String idOffre) {
        sienne(idCandidat, idOffre);
        Attribution a = attributions.findByIdOffreAttribuee(idOffre).stream().filter(x -> x.getInformeLe() != null)
                .findFirst().orElseThrow(() -> new BusinessRuleException("Votre offre n'est pas attributaire d'un marché.", "NON_ATTRIBUTAIRE"));
        return piecesAttributaire(a, piecesRepository.findByIdDmcAndLotOrderByIdAsc(a.getIdDmc(), a.getLot()));
    }

    /**
     * La PRMP vérifie une pièce : {@code conforme} obligatoire (400 {@code CONFORME_OBLIGATOIRE}), un motif pour une pièce non conforme
     * (400 {@code MOTIF_OBLIGATOIRE}) ; 409 {@code DEJA_VERIFIEE} ; 404 pièce inconnue du lot.
     */
    public void verifierPiece(Long idDmc, Integer lot, Long id, AttributionDto.VerificationRequest v) {
        exigerPrmp(idDmc);
        AttributionPiece p = piecesRepository.findById(id).filter(x -> x.getIdDmc().equals(idDmc) && x.getLot().equals(lot)
                && (AttributionPiece.FISCALE.equals(x.getNature()) || AttributionPiece.SOCIALE.equals(x.getNature())))
                .orElseThrow(() -> new ResourceNotFoundException("Pièce de l'attributaire introuvable : " + id + "."));
        if (p.getVerifieeLe() != null) {
            throw new BusinessRuleException("Cette pièce est déjà vérifiée.", "DEJA_VERIFIEE");
        }
        if (v == null || v.conforme() == null) {
            throw new BadRequestException("Dites si la pièce est conforme.", "CONFORME_OBLIGATOIRE");
        }
        if (!v.conforme() && nettoyer(v.motif()) == null) {
            throw new BadRequestException("Une pièce non conforme exige un motif.", "MOTIF_OBLIGATOIRE");
        }
        p.setConforme(v.conforme());
        p.setMotif(nettoyer(v.motif()));
        p.setVerifieeLe(maintenant());
        p.setVerifieePar(acteur());
        piecesRepository.save(p);
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot)).orElseThrow();
        evaluation.tracerAttribution(idDmc, "PIECE_VERIFIEE", "Lot " + lot + " : pièce " + p.getNature().toLowerCase(Locale.FRENCH) + " "
                + (v.conforme() ? "conforme" : "non conforme — " + p.getMotif()));
        notifierAttributaire(a, TypeNotification.PIECE_ATTRIBUTAIRE_VERIFIEE, "Vérification de votre pièce",
                "Votre pièce " + p.getNature().toLowerCase(Locale.FRENCH) + " est reconnue " + (v.conforme() ? "conforme."
                        : "non conforme : " + p.getMotif() + ". Vous pouvez en déposer une nouvelle jusqu'au " + echeancePieces(a).format(JOUR) + "."));
    }

    /**
     * Le retrait du marché, faute de pièces fiscales et sociales conformes à l'échéance (art. 20-I) : motif obligatoire (400
     * {@code MOTIF_OBLIGATOIRE}) ; 409 {@code NON_INFORME}, {@code DEJA_SIGNE}, {@code LOT_RETIRE}, {@code PIECES_CONFORMES},
     * {@code DELAI_EN_COURS}. La réattribution au candidat suivant suit en tranche 2d ; l'infructuosité n'est jamais ouverte après
     * l'attribution (art. 56-VI).
     */
    public void retirer(Long idDmc, Integer lot, AttributionDto.RetraitRequest r) {
        exigerPrmp(idDmc);
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot)).filter(x -> x.getInformeLe() != null)
                .orElseThrow(() -> new BusinessRuleException("Les candidats de ce lot ne sont pas informés.", "NON_INFORME"));
        if (a.getRetireLe() != null) {
            throw new BusinessRuleException("Le marché de ce lot est déjà retiré.", "LOT_RETIRE");
        }
        if (a.getSigneLe() != null) {
            throw new BusinessRuleException("Le marché est signé : il ne se retire plus.", "DEJA_SIGNE");
        }
        String motif = r == null ? null : nettoyer(r.motif());
        if (motif == null) {
            throw new BadRequestException("Le retrait exige un motif.", "MOTIF_OBLIGATOIRE");
        }
        List<AttributionPiece> toutes = piecesRepository.findByIdDmcAndLotOrderByIdAsc(idDmc, lot);
        if (conforme(toutes, AttributionPiece.FISCALE) && conforme(toutes, AttributionPiece.SOCIALE)) {
            throw new BusinessRuleException("Les pièces fiscale et sociale de l'attributaire sont conformes.", "PIECES_CONFORMES");
        }
        if (!aujourdhui().isAfter(echeancePieces(a))) {
            throw new BusinessRuleException("L'attributaire peut déposer ses pièces jusqu'au " + echeancePieces(a).format(JOUR) + ".", "DELAI_EN_COURS");
        }
        a.setRetireLe(maintenant());
        a.setRetirePar(acteur());
        a.setMotifRetrait(motif);
        a.setEtat(Attribution.RETIRE);
        attributions.save(a);
        evaluation.tracerAttribution(idDmc, "RETRAIT", "Lot " + lot + " : marché retiré à l'attributaire — " + motif);
        notifierAttributaire(a, TypeNotification.MARCHE_RETIRE, "Retrait du marché", "Faute de pièces fiscales et sociales conformes "
                + "produites dans le délai, le marché du lot " + lot + " (procédure " + idDmc + ") vous est retiré (art. 20-I) : " + motif + ".");
    }

    // ------------------------------------------------------------------ §B4.3 signature, enregistrement, notification (art. 54)

    /**
     * La signature du marché : {@code dateSignature} (400 {@code DATE_INVALIDE} : absente ou à venir), le marché signé (400
     * {@code FICHIER_OBLIGATOIRE}) ; 409 {@code NON_INFORME}, {@code LOT_RETIRE}, {@code DEJA_SIGNE}, {@code DELAI_ATTENTE} (avant le
     * lendemain du dixième jour franc), {@code RECOURS_EN_COURS} (révision ARMP ou référé sans décision, suspension en cours),
     * {@code PIECES_NON_CONFORMES} (les pièces fiscale et sociale de l'attributaire ne sont pas toutes deux reconnues conformes).
     */
    public void signer(Long idDmc, Integer lot, LocalDate dateSignature, MultipartFile fichier) {
        exigerPrmp(idDmc);
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot)).filter(x -> x.getInformeLe() != null)
                .orElseThrow(() -> new BusinessRuleException("Les candidats de ce lot ne sont pas informés.", "NON_INFORME"));
        exigerNonSigne(a);
        AttributionDto.DelaiAttente d = AttributionService.delai(a, aujourdhui());
        if (dateSignature == null || dateSignature.isAfter(aujourdhui())) {
            throw new BadRequestException("La date de signature est à indiquer, au plus tard aujourd'hui.", "DATE_INVALIDE");
        }
        if (!d.ecoule() || dateSignature.isBefore(d.signableLe())) {
            throw new BusinessRuleException("Le marché ne peut être signé avant le " + d.signableLe().format(JOUR) + " (délai d'attente de "
                    + AttributionService.JOURS_FRANCS + " jours francs, art. 78).", "DELAI_ATTENTE");
        }
        List<AttributionRecours> bloquants = recoursRepository.findByIdDmcAndLotOrderByDateReceptionAscIdAsc(idDmc, lot).stream()
                .filter(r -> bloquant(r, aujourdhui())).toList();
        if (!bloquants.isEmpty()) {
            AttributionRecours r = bloquants.get(0);
            throw new BusinessRuleException("Un recours " + r.getType() + " de " + r.getRequerant() + " suspend la signature jusqu'à sa décision, "
                    + "au plus tard le " + r.getDateReception().plusDays(JOURS_SUSPENSION).format(JOUR) + ".", "RECOURS_EN_COURS");
        }
        List<AttributionPiece> toutes = piecesRepository.findByIdDmcAndLotOrderByIdAsc(idDmc, lot);
        if (!conforme(toutes, AttributionPiece.FISCALE) || !conforme(toutes, AttributionPiece.SOCIALE)) {
            throw new BusinessRuleException("Les pièces fiscale et sociale de l'attributaire doivent être reconnues conformes avant la signature "
                    + "(art. 20-I).", "PIECES_NON_CONFORMES");
        }
        Televerse f = lire(fichier, true);
        a.setDateSignature(dateSignature);
        a.setSigneLe(maintenant());
        a.setSignePar(acteur());
        a.setEtat(Attribution.SIGNE);
        attributions.save(a);
        enregistrerFichier(a, AttributionPiece.MARCHE_SIGNE, null, f);
        evaluation.tracerAttribution(idDmc, "SIGNATURE_MARCHE", "Lot " + lot + " : marché signé le " + dateSignature.format(JOUR));
    }

    /**
     * L'enregistrement du marché (Q6) : la date (entre la signature et aujourd'hui, 400 {@code DATE_INVALIDE}), la référence facultative,
     * le justificatif obligatoire (400 {@code FICHIER_OBLIGATOIRE}) ; 409 {@code NON_SIGNE}, {@code DEJA_ENREGISTRE}.
     */
    public void enregistrer(Long idDmc, Integer lot, LocalDate date, String reference, MultipartFile fichier) {
        exigerPrmp(idDmc);
        Attribution a = exigerSigne(idDmc, lot);
        if (a.getEnregistreLe() != null) {
            throw new BusinessRuleException("Le marché de ce lot est déjà enregistré.", "DEJA_ENREGISTRE");
        }
        if (date == null || date.isBefore(a.getDateSignature()) || date.isAfter(aujourdhui())) {
            throw new BadRequestException("La date d'enregistrement se situe entre la signature et aujourd'hui.", "DATE_INVALIDE");
        }
        Televerse f = lire(fichier, true);
        a.setDateEnregistrement(date);
        a.setReferenceEnregistrement(nettoyer(reference));
        a.setEnregistreLe(maintenant());
        attributions.save(a);
        enregistrerFichier(a, AttributionPiece.ENREGISTREMENT, null, f);
        evaluation.tracerAttribution(idDmc, "ENREGISTREMENT", "Lot " + lot + " : marché enregistré le " + date.format(JOUR)
                + (a.getReferenceEnregistrement() == null ? "" : " (" + a.getReferenceEnregistrement() + ")"));
    }

    /**
     * La notification du marché à l'attributaire : {@code dateNotification} (entre l'enregistrement et aujourd'hui), {@code dateReception}
     * facultative (réception déclarée, entre la notification et aujourd'hui ; sinon l'accusé de lecture de la plateforme) — 400
     * {@code DATE_INVALIDE} ; 409 {@code NON_SIGNE}, {@code NON_ENREGISTRE}, {@code DEJA_NOTIFIE}.
     */
    public void notifier(Long idDmc, Integer lot, AttributionDto.NotificationRequest r) {
        exigerPrmp(idDmc);
        Attribution a = exigerSigne(idDmc, lot);
        if (a.getEnregistreLe() == null) {
            throw new BusinessRuleException("Le marché se notifie après son enregistrement (art. 54).", "NON_ENREGISTRE");
        }
        if (a.getNotifieLe() != null) {
            throw new BusinessRuleException("Le marché de ce lot est déjà notifié.", "DEJA_NOTIFIE");
        }
        LocalDate date = r == null ? null : r.dateNotification();
        if (date == null || date.isBefore(a.getDateEnregistrement()) || date.isAfter(aujourdhui())) {
            throw new BadRequestException("La date de notification se situe entre l'enregistrement et aujourd'hui.", "DATE_INVALIDE");
        }
        LocalDate reception = r.dateReception();
        if (reception != null && (reception.isBefore(date) || reception.isAfter(aujourdhui()))) {
            throw new BadRequestException("La date de réception se situe entre la notification et aujourd'hui.", "DATE_INVALIDE");
        }
        a.setDateNotification(date);
        a.setNotifieLe(maintenant());
        a.setNotifiePar(acteur());
        if (reception != null) {
            a.setNotificationRecueLe(reception.atStartOfDay());
            a.setReceptionDeclaree(true);
        }
        a.setEtat(Attribution.NOTIFIE);
        attributions.save(a);
        evaluation.tracerAttribution(idDmc, "NOTIFICATION", "Lot " + lot + " : marché notifié le " + date.format(JOUR)
                + (reception == null ? "" : ", reçu le " + reception.format(JOUR) + " (réception déclarée)") + " ; avis d'attribution à publier "
                + "au plus tard le " + date.plusDays(JOURS_AVIS).format(JOUR));
        notifierAttributaire(a, TypeNotification.MARCHE_NOTIFIE, "Notification du marché", "Le marché du lot " + lot + " (procédure " + idDmc
                + "), signé le " + a.getDateSignature().format(JOUR) + " et enregistré, vous est notifié. Il prend effet à sa réception ; le marché "
                + "signé est disponible dans votre espace (Mes offres).");
    }

    // ------------------------------------------------------------------ §B4.4 l'avis d'attribution (art. 53)

    /**
     * L'avis d'attribution, produit par le serveur (modèle provisoire, Q7) et publié : {@code datePublication} entre la notification et
     * aujourd'hui (400 {@code DATE_INVALIDE}) ; 409 {@code NON_NOTIFIE}, {@code DEJA_PUBLIE}.
     */
    public void publierAvis(Long idDmc, Integer lot, AttributionDto.AvisRequest r) {
        exigerPrmp(idDmc);
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot)).filter(x -> x.getNotifieLe() != null)
                .orElseThrow(() -> new BusinessRuleException("L'avis d'attribution se publie après la notification du marché.", "NON_NOTIFIE"));
        if (a.getAvisPublieLe() != null) {
            throw new BusinessRuleException("L'avis d'attribution de ce lot est déjà publié.", "DEJA_PUBLIE");
        }
        LocalDate date = r == null ? null : r.datePublication();
        if (date == null || date.isBefore(a.getDateNotification()) || date.isAfter(aujourdhui())) {
            throw new BadRequestException("La date de publication se situe entre la notification et aujourd'hui.", "DATE_INVALIDE");
        }
        a.setDatePublicationAvis(date);
        a.setAvisPublieLe(maintenant());
        a.setAvisPubliePar(acteur());
        for (GenerateurDocumentsFiche.Fichier f : generateur.generer(avis(a))) {
            if ("pdf".equals(f.extension())) {
                a.setAvisPdf(f.contenu());
            } else if ("docx".equals(f.extension())) {
                a.setAvisDocx(f.contenu());
            }
        }
        a.setEtat(Attribution.PUBLIE);
        attributions.save(a);
        evaluation.tracerAttribution(idDmc, "AVIS_ATTRIBUTION", "Lot " + lot + " : avis d'attribution publié le " + date.format(JOUR)
                + (date.isAfter(a.getDateNotification().plusDays(JOURS_AVIS)) ? ", après l'échéance de " + JOURS_AVIS + " jours" : ""));
    }

    /** L'avis d'attribution (PDF, ou Word) : lecteurs de l'attribution ; 404 avant sa publication. */
    @Transactional(readOnly = true)
    public byte[] avis(Long idDmc, Integer lot, boolean docx) {
        evaluation.controlerLecture(idDmc);
        return avisPublie(idDmc, lot, docx);
    }

    /** L'avis d'attribution publié, sans session (page publique de la procédure) ; 404 avant sa publication. */
    @Transactional(readOnly = true)
    public byte[] avisPublie(Long idDmc, Integer lot, boolean docx) {
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot)).filter(x -> x.getAvisPublieLe() != null)
                .orElseThrow(() -> new ResourceNotFoundException("L'avis d'attribution de ce lot n'est pas publié."));
        byte[] b = docx ? a.getAvisDocx() : a.getAvisPdf();
        if (b == null) {
            throw new ResourceNotFoundException("L'avis d'attribution de ce lot n'est pas publié.");
        }
        return b;
    }

    /** L'avis (Q7 : modèle provisoire, à remplacer par le modèle officiel ; mentions à reprendre de l'arrêté du Ministre des Finances). */
    private DocumentLibre avis(Attribution a) {
        DossierMec dmc = dmcRepository.findById(a.getIdDmc()).orElseThrow();
        Map<String, String> plan = valeursPpm.lire(dmc.getIdDetail()).valeurs();
        FicheMarcheService.EtatVersion v = fiches.etatValide(a.getIdDmc()).orElse(null);
        String numero = v == null || v.etat().getValeurs() == null ? null : v.etat().getValeurs().get("B02-OB-03");
        String objet = v == null ? "—" : Objects.toString(v.etat().getDesignationMarche(), "—");
        boolean allotie = attributions.findByIdDmcOrderByLotAsc(a.getIdDmc()).size() > 1 || a.getLot() > 1;
        Offre o = offres.findById(a.getIdOffreAttribuee()).orElseThrow();
        long ouvertes = lettres.findByIdDmcAndLotOrderByIdAsc(a.getIdDmc(), a.getLot()).size();
        List<DocumentLibre.Element> el = new ArrayList<>();
        String autorite = Objects.toString(plan.get("ENTITE"), "L'Autorité contractante");
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, plan.get("MINISTERE") == null ? autorite : plan.get("MINISTERE") + " — " + autorite));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "AVIS D'ATTRIBUTION DE MARCHÉ"));
        para(el, "Appel d'offres" + (numero == null ? "" : " n° " + numero) + " — " + objet + (allotie ? ", lot " + a.getLot() : "") + ".");
        para(el, "Autorité contractante : " + autorite + (plan.get("PRMP") == null ? "" : " ; Personne responsable des marchés publics : "
                + plan.get("PRMP")) + ".");
        para(el, "Nombre d'offres ouvertes en séance : " + ouvertes + ".");
        para(el, "Attributaire : " + o.getRaisonSociale() + ", NIF " + o.getNif() + ".");
        para(el, "Montant du marché : " + (a.getMontant() == null ? "—" : FormulairesEnLigne.lisible(a.getMontant()) + " Ariary hors taxes")
                + (a.getMontantTtc() == null ? "" : " (offre lue : " + FormulairesEnLigne.lisible(a.getMontantTtc()) + " Ariary toutes taxes comprises)")
                + (a.getDelai() == null ? "" : " ; délai d'exécution : " + a.getDelai()) + ".");
        para(el, "Marché signé le " + a.getDateSignature().format(JOUR) + ", enregistré le " + a.getDateEnregistrement().format(JOUR)
                + ", notifié le " + a.getDateNotification().format(JOUR) + ".");
        para(el, "Le présent avis est publié le " + a.getDatePublicationAvis().format(JOUR) + " (art. 53 de la loi n° 2016-055).");
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        para(el, "La Personne responsable des marchés publics, " + nomPrmp() + " — signé électroniquement sur la plateforme le "
                + a.getAvisPublieLe().format(HORODATAGE) + ".");
        return new DocumentLibre("AVIS_ATTRIBUTION", allotie ? a.getLot() : null, el,
                "Procédure " + a.getIdDmc() + (allotie ? ", lot " + a.getLot() : "") + " — avis d'attribution (modèle provisoire)");
    }

    // ------------------------------------------------------------------ fichiers

    /** Un fichier de l'attribution : lecteurs de l'attribution ; 404 inconnu de la procédure. */
    @Transactional(readOnly = true)
    public AttributionPiece fichier(Long idDmc, Long id) {
        evaluation.controlerLecture(idDmc);
        return piecesRepository.findById(id).filter(p -> p.getIdDmc().equals(idDmc))
                .orElseThrow(() -> new ResourceNotFoundException("Fichier introuvable : " + id + "."));
    }

    /** Une de ses pièces, pour l'attributaire ; 404 sinon. */
    @Transactional(readOnly = true)
    public AttributionPiece fichierDuCandidat(String idCandidat, String idOffre, Long id) {
        sienne(idCandidat, idOffre);
        AttributionPiece p = piecesRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Fichier introuvable : " + id + "."));
        Attribution a = attributions.findById(new Attribution.Cle(p.getIdDmc(), p.getLot())).orElseThrow();
        if (!idOffre.equals(a.getIdOffreAttribuee()) || !(AttributionPiece.FISCALE.equals(p.getNature()) || AttributionPiece.SOCIALE.equals(p.getNature()))) {
            throw new ResourceNotFoundException("Fichier introuvable : " + id + ".");
        }
        return p;
    }

    /** Le marché signé, pour l'attributaire ; le lire après la notification vaut accusé de réception. 404 avant la signature. */
    public AttributionPiece marcheDuCandidat(String idCandidat, String idOffre) {
        sienne(idCandidat, idOffre);
        Attribution a = attributions.findByIdOffreAttribuee(idOffre).stream().filter(x -> x.getSigneLe() != null).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Aucun marché signé pour cette offre."));
        accuserNotification(a, idOffre);
        AttributionDto.Fichier f = derniere(piecesRepository.findByIdDmcAndLotOrderByIdAsc(a.getIdDmc(), a.getLot()), AttributionPiece.MARCHE_SIGNE);
        return piecesRepository.findById(f.id()).orElseThrow();
    }

    // ------------------------------------------------------------------ outils

    private record Televerse(String nom, String format, byte[] contenu) {
    }

    /** Un fichier téléversé : PDF, JPEG ou PNG (type lu sur le contenu), à la taille des pièces du candidat. */
    private Televerse lire(MultipartFile fichier, boolean obligatoire) {
        if (fichier == null || fichier.isEmpty()) {
            if (obligatoire) {
                throw new BadRequestException("Le fichier est obligatoire.", "FICHIER_OBLIGATOIRE");
            }
            return null;
        }
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Lecture du fichier impossible.", "FICHIER_ABSENT");
        }
        int mo = parametres.candidats().tailleMaxPieceMo();
        if (contenu.length > mo * 1024L * 1024L) {
            throw new PayloadTropVolumineuxException("Le fichier dépasse " + mo + " Mo.");
        }
        String format = EntrepriseCandidatService.format(contenu);
        if (format == null) {
            throw new BadRequestException("Le fichier doit être un PDF, un JPEG ou un PNG (type lu sur le contenu).", "FORMAT_INVALIDE");
        }
        String nom = fichier.getOriginalFilename() == null || fichier.getOriginalFilename().isBlank() ? "piece"
                : fichier.getOriginalFilename().replaceAll("[\\\\/]", "_");
        return new Televerse(nom.length() > 255 ? nom.substring(nom.length() - 255) : nom, format, contenu);
    }

    private AttributionPiece enregistrerFichier(Attribution a, String nature, Long idRecours, Televerse t) {
        if (t == null) {
            return null;
        }
        AttributionPiece p = new AttributionPiece();
        p.setIdDmc(a.getIdDmc());
        p.setLot(a.getLot());
        p.setNature(nature);
        p.setIdRecours(idRecours);
        p.setNom(t.nom());
        p.setFormat(t.format());
        p.setTaille((long) t.contenu().length);
        p.setContenu(t.contenu());
        p.setDeposeLe(maintenant());
        p.setDeposePar(acteur());
        return piecesRepository.save(p);
    }

    private static AttributionDto.Fichier dto(AttributionPiece p) {
        return new AttributionDto.Fichier(p.getId(), p.getNature(), p.getNom(), p.getFormat(), p.getTaille(), p.getDeposeLe());
    }

    private static AttributionDto.Fichier derniere(List<AttributionPiece> toutes, String nature) {
        return toutes.stream().filter(p -> nature.equals(p.getNature())).reduce((x, y) -> y).map(AttributionExecutionService::dto).orElse(null);
    }

    private static List<AttributionDto.Fichier> fichiers(List<AttributionPiece> toutes, String nature, Long idRecours) {
        return toutes.stream().filter(p -> nature.equals(p.getNature()) && Objects.equals(idRecours, p.getIdRecours()))
                .map(AttributionExecutionService::dto).toList();
    }

    /** La dernière pièce déposée de ce type est reconnue conforme. */
    private static boolean conforme(List<AttributionPiece> toutes, String type) {
        return toutes.stream().filter(p -> type.equals(p.getNature())).reduce((x, y) -> y).map(p -> Boolean.TRUE.equals(p.getConforme()))
                .orElse(false);
    }

    private Attribution exigerAttribue(Long idDmc, Integer lot) {
        exigerPrmp(idDmc);
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot)).filter(x -> x.getAttribueLe() != null)
                .orElseThrow(() -> new BusinessRuleException("Le lot n'est pas attribué.", "NON_ATTRIBUE"));
        if (a.getRetireLe() != null) {
            throw new BusinessRuleException("Le marché de ce lot est retiré.", "LOT_RETIRE");
        }
        return a;
    }

    private static void exigerNonSigne(Attribution a) {
        if (a.getRetireLe() != null) {
            throw new BusinessRuleException("Le marché de ce lot est retiré.", "LOT_RETIRE");
        }
        if (a.getSigneLe() != null) {
            throw new BusinessRuleException("Le marché de ce lot est déjà signé.", "DEJA_SIGNE");
        }
    }

    private Attribution exigerSigne(Long idDmc, Integer lot) {
        return attributions.findById(new Attribution.Cle(idDmc, lot)).filter(x -> x.getSigneLe() != null)
                .orElseThrow(() -> new BusinessRuleException("Le marché de ce lot n'est pas signé.", "NON_SIGNE"));
    }

    private void notifierAttributaire(Attribution a, TypeNotification type, String titre, String corps) {
        Offre o = offres.findById(a.getIdOffreAttribuee()).orElse(null);
        if (o == null) {
            return;
        }
        CompteCandidat c = candidats.findById(o.getIdCandidat()).orElse(null);
        notifications.emettreCandidat(type, o.getIdCandidat(), c == null ? null : c.getEmail(), a.getIdDmc().intValue(), TypeObjet.PROCEDURE,
                titre, corps);
    }

    private Offre sienne(String idCandidat, String idOffre) {
        Offre o = offres.findById(idOffre).orElseThrow(() -> new ResourceNotFoundException("Offre introuvable : " + idOffre + "."));
        if (!o.getIdCandidat().equals(idCandidat)) {
            throw new AccessDeniedException("Cette offre n'est pas la vôtre.");
        }
        return o;
    }

    /** La PRMP de la fiche, seule (ce qui engage est nominatif). */
    private void exigerPrmp(Long idDmc) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException("Ce geste de l'attribution est réservé à la PRMP de la fiche.");
        }
        fiches.controlerLecture(idDmc);
    }

    private String nomPrmp() {
        String ref = CurrentUser.ref().orElse(null);
        return ref == null ? Objects.toString(acteur(), "—") : prmpRepository.findById(ref)
                .map(p -> (Objects.toString(p.getNomPrmp(), "") + " " + Objects.toString(p.getPrenomsPrmp(), "")).trim())
                .filter(s -> !s.isBlank()).orElse(ref);
    }

    private static void para(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, t));
    }

    private LocalDate aujourdhui() {
        return LocalDate.now(horloge);
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).withNano(0);
    }

    private static String acteur() {
        return CurrentUser.ref().or(CurrentUser::login).orElse(null);
    }

    private static String nettoyer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
