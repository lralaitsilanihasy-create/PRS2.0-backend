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
    /** ⚠️ R6c (règle 8) — l'alerte de l'avis d'attribution à J+20 de la réception de la notification. */
    public static final int JOURS_ALERTE_AVIS = 20;
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
    /** ⚠️ 2d-3 — une demande de sans suite en cours suspend la signature. */
    private final org.springframework.beans.factory.ObjectProvider<SansSuiteService> sansSuite;
    /** ⚠️ R6c (règle 6, QB1 ; V108) — le délai d'attente part de la réception présumée de l'information. */
    private final DelaiAttenteCalcul delais;
    /** ⚠️ 2026-10-16 (tranche MT-b2, V122-V123) — les titulaires d'un contrat-cadre multi-attributaire : pièces, retrait, réception. */
    private final cnm.prs.repository.AttributionTitulaireRepository titulaires;
    /** ⚠️ 2026-10-16 (MS-1, V124) — le contrat en vigueur, créé à la notification (fournisseur paresseux : pas de cycle). */
    private final org.springframework.beans.factory.ObjectProvider<ContratVigueurService> contrats;

    public AttributionExecutionService(AttributionRepository attributions, AttributionRecoursRepository recoursRepository,
            AttributionPieceRepository piecesRepository, AttributionLettreRepository lettres, OffreRepository offres,
            CompteCandidatRepository candidats, PrmpRepository prmpRepository, DossierMecRepository dmcRepository,
            NotificationService notifications, CeremonieService ceremonies, EvaluationService evaluation, FicheMarcheService fiches,
            ValeursPpmService valeursPpm, GenerateurDocumentsFiche generateur, ParametreService parametres, Clock horloge,
            org.springframework.beans.factory.ObjectProvider<SansSuiteService> sansSuite, DelaiAttenteCalcul delais,
            cnm.prs.repository.AttributionTitulaireRepository titulaires,
            org.springframework.beans.factory.ObjectProvider<ContratVigueurService> contrats) {
        this.contrats = contrats;
        this.titulaires = titulaires;
        this.delais = delais;
        this.sansSuite = sansSuite;
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
            AttributionDto.PiecesAttributaire pieces, AttributionDto.Retrait retrait, AttributionDto.Approbation approbation) {

        static final Suite VIDE = new Suite(null, List.of(), null, null, null, null, null, null, null);
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
                : new AttributionDto.AvisAttribution(departAvis(a), echeanceAvis(a), a.getDatePublicationAvis(), a.getAvisPublieLe(),
                        a.getAvisPubliePar(), a.getAvisPublieLe() != null, avisEnRetard(a, aujourdhui()));
        AttributionDto.Retrait ret = a.getRetireLe() == null ? null : new AttributionDto.Retrait(a.getRetireLe(), a.getRetirePar(), a.getMotifRetrait());
        AttributionDto.Approbation app = a.getApprouveLe() == null ? null
                : new AttributionDto.Approbation(a.getApprobationAutorite(), a.getApprobationDate(), a.getApprouveLe(), a.getApprouvePar(),
                        derniere(toutes, AttributionPiece.APPROBATION));
        return new Suite(map, recours, sig, enr, notif, avis, piecesAttributaire(a, toutes), ret, app);
    }

    /** Les pièces fiscales et sociales, une fois les candidats informés ; nul avant. Celles de l'attributaire de tête. */
    AttributionDto.PiecesAttributaire piecesAttributaire(Attribution a, List<AttributionPiece> toutes) {
        return piecesAttributaire(a, toutes, a.getIdOffreAttribuee());
    }

    /**
     * ⚠️ 2026-10-16 (tranche MT-b2, V123, réponse 1 du pilote) — les pièces que produit une offre : chaque titulaire d'un contrat-cadre
     * multi-attributaire a les siennes. Une pièce sans offre (posée avant V123 et non reprise) est celle de l'attributaire de tête.
     */
    static List<AttributionPiece> piecesDe(Attribution a, List<AttributionPiece> toutes, String idOffre) {
        return toutes.stream().filter(p -> !NATURES_ATTRIBUTAIRE.contains(p.getNature()) || Objects.equals(idOffre, p.getIdOffre())
                || p.getIdOffre() == null && Objects.equals(idOffre, a.getIdOffreAttribuee())).toList();
    }

    /** ⚠️ MT-b2 — les titulaires du lot, dans l'ordre du classement ; vide hors multi-attributaire. */
    List<cnm.prs.entity.AttributionTitulaire> titulairesDe(Attribution a) {
        return titulaires.findByIdDmcAndLotOrderByRangAscIdOffreAsc(a.getIdDmc(), a.getLot());
    }

    /** ⚠️ MT-b2 — les titulaires qui ne sont pas retirés. */
    static List<cnm.prs.entity.AttributionTitulaire> actifs(List<cnm.prs.entity.AttributionTitulaire> t) {
        return t.stream().filter(x -> x.getRetireLe() == null).toList();
    }

    /** ⚠️ MT-b2 — le suivi de chaque titulaire (pièces, réception, retrait), pour la vue du lot ; vide hors multi-attributaire. */
    @Transactional(readOnly = true)
    List<AttributionDto.SuiviTitulaire> suiviTitulaires(Attribution a) {
        if (a == null) {
            return List.of();
        }
        List<cnm.prs.entity.AttributionTitulaire> tit = titulairesDe(a);
        if (tit.isEmpty()) {
            return List.of();
        }
        List<AttributionPiece> toutes = piecesRepository.findByIdDmcAndLotOrderByIdAsc(a.getIdDmc(), a.getLot());
        return tit.stream().map(t -> {
            Offre o = offres.findById(t.getIdOffre()).orElse(null);
            return new AttributionDto.SuiviTitulaire(t.getIdOffre(), o == null ? null : o.getNumero(), o == null ? null : o.getRaisonSociale(),
                    t.getRang(), piecesAttributaire(a, toutes, t.getIdOffre()), t.getNotificationRecueLe(),
                    t.getRetireLe() == null ? null : new AttributionDto.Retrait(t.getRetireLe(), t.getRetirePar(), t.getMotifRetrait()));
        }).toList();
    }

    /**
     * ⚠️ MT-b2 — l'attribution dont une offre est l'attributaire de tête ou un titulaire, retenue par {@code filtre} (informée,
     * signée…) ; vide sinon.
     */
    private java.util.Optional<Attribution> attributionDeLOffre(String idOffre, java.util.function.Predicate<Attribution> filtre) {
        java.util.Optional<Attribution> tete = attributions.findByIdOffreAttribuee(idOffre).stream().filter(filtre).findFirst();
        if (tete.isPresent()) {
            return tete;
        }
        return titulaires.findByIdOffre(idOffre).stream().map(t -> attributions.findById(new Attribution.Cle(t.getIdDmc(), t.getLot())).orElse(null))
                .filter(Objects::nonNull).filter(filtre).findFirst();
    }

    /** ⚠️ MT-b2 — le titulaire d'une offre dans ce lot, ou {@code null} (hors multi-attributaire, ou offre non titulaire). */
    private cnm.prs.entity.AttributionTitulaire titulaire(Attribution a, String idOffre) {
        return titulairesDe(a).stream().filter(t -> t.getIdOffre().equals(idOffre)).findFirst().orElse(null);
    }

    /** ⚠️ MT-b2 — les pièces d'une offre, une fois les candidats informés ; nul avant. */
    AttributionDto.PiecesAttributaire piecesAttributaire(Attribution a, List<AttributionPiece> lot, String idOffre) {
        if (a.getInformeLe() == null) {
            return null;
        }
        List<AttributionPiece> toutes = piecesDe(a, lot, idOffre);
        LocalDate echeance = echeancePieces(a);
        List<AttributionDto.PieceAttributaire> l = toutes.stream()
                .filter(p -> NATURES_ATTRIBUTAIRE.contains(p.getNature()))
                .map(p -> new AttributionDto.PieceAttributaire(p.getId(), p.getNature(), p.getDateDelivrance(), p.getNom(), p.getTaille(),
                        p.getDeposeLe(), p.getConforme(), p.getMotif(), p.getVerifieeLe()))
                .toList();
        return new AttributionDto.PiecesAttributaire(echeance, aujourdhui().isAfter(echeance), limiteDelivrance(a, AttributionPiece.FISCALE),
                limiteDelivrance(a, AttributionPiece.SOCIALE), conforme(toutes, AttributionPiece.FISCALE), conforme(toutes, AttributionPiece.SOCIALE), l,
                aeOriginalExige(a.getIdDmc()), conforme(toutes, AttributionPiece.ACTE_ENGAGEMENT_ORIGINAL));
    }

    /** ⚠️ R10 — la date de remise du dernier original de l'acte d'engagement reconnu conforme du lot ; nulle sinon. */
    @Transactional(readOnly = true)
    LocalDate aeOriginalRemisLe(Long idDmc, Integer lot) {
        return piecesRepository.findByIdDmcAndLotOrderByIdAsc(idDmc, lot).stream()
                .filter(p -> AttributionPiece.ACTE_ENGAGEMENT_ORIGINAL.equals(p.getNature()) && p.getArchiveLe() == null
                        && Boolean.TRUE.equals(p.getConforme()))
                .reduce((x, y) -> y).map(AttributionPiece::getDateDelivrance).orElse(null);
    }

    /** Le libellé d'une pièce de l'attributaire dans les messages. */
    static String libellePiece(String nature) {
        return AttributionPiece.ACTE_ENGAGEMENT_ORIGINAL.equals(nature) ? "original signé de l'acte d'engagement"
                : "pièce " + nature.toLowerCase(Locale.FRENCH);
    }

    /** Les pièces de l'attributaire : fiscale, sociale et ⚠️ R10 l'original de l'acte d'engagement. */
    static final Set<String> NATURES_ATTRIBUTAIRE = Set.of(AttributionPiece.FISCALE, AttributionPiece.SOCIALE, AttributionPiece.ACTE_ENGAGEMENT_ORIGINAL);

    /**
     * ⚠️ R10 (manuel révisé, lot 2, §B5 ; H2 du pilote) — l'original signé de l'acte d'engagement est exigé de l'attributaire avant la
     * signature du marché quand l'offre a été déposée <strong>en ligne</strong> (mode figé de la procédure : remise électronique), le dépôt
     * scellé n'ayant valu signature que pendant la procédure. {@code CERTIFIEE} (réservé) ne lève pas l'exigence tant que la signature
     * certifiée n'est pas branchée. Une procédure papier a déjà l'acte signé dans l'offre : rien n'est exigé.
     */
    boolean aeOriginalExige(Long idDmc) {
        return fiches.modeFige(idDmc).map(RemiseElectronique.ELECTRONIQUE::equals).orElse(false);
    }

    /**
     * ⚠️ R6c (règle 7, art. 20-I) — la date de délivrance la plus ancienne admise : l'information (la notification de l'attribution)
     * moins six mois pour la pièce fiscale, trois mois pour la pièce sociale.
     */
    static LocalDate limiteDelivrance(Attribution a, String nature) {
        return a.getInformeLe().toLocalDate().minusMonths(AttributionPiece.FISCALE.equals(nature) ? MOIS_FISCALE : MOIS_SOCIALE);
    }

    /**
     * ⚠️ R6c (règle 8, art. 53-54) — le point de départ des 30 jours de l'avis d'attribution : la réception de la notification par le
     * titulaire (déclarée, ou l'accusé de lecture de la plateforme), à défaut la date de notification.
     */
    static LocalDate departAvis(Attribution a) {
        return a.getNotificationRecueLe() != null ? a.getNotificationRecueLe().toLocalDate() : a.getDateNotification();
    }

    /** L'échéance de l'avis d'attribution : {@link #departAvis} + 30 jours ; nulle avant la notification. */
    static LocalDate echeanceAvis(Attribution a) {
        LocalDate depart = a.getNotifieLe() == null ? null : departAvis(a);
        return depart == null ? null : depart.plusDays(JOURS_AVIS);
    }

    /** L'avis est en retard : publié après l'échéance, ou non publié une fois l'échéance passée. */
    static boolean avisEnRetard(Attribution a, LocalDate aujourdhui) {
        LocalDate echeance = echeanceAvis(a);
        if (echeance == null) {
            return false;
        }
        return a.getDatePublicationAvis() != null ? a.getDatePublicationAvis().isAfter(echeance) : aujourdhui.isAfter(echeance);
    }

    /** Les pièces de l'attributaire, pour le candidat (son résultat) : nul pour un candidat non retenu. */
    @Transactional(readOnly = true)
    AttributionDto.PiecesAttributaire piecesPourCandidat(Attribution a, String idOffre) {
        // ⚠️ MT-b2 — un titulaire d'un contrat-cadre multi-attributaire voit les siennes.
        return idOffre.equals(a.getIdOffreAttribuee()) || titulaire(a, idOffre) != null
                ? piecesAttributaire(a, piecesRepository.findByIdDmcAndLotOrderByIdAsc(a.getIdDmc(), a.getLot()), idOffre) : null;
    }

    /**
     * L'accusé de lecture de la notification : la première consultation par l'attributaire, une fois le marché notifié. ⚠️ MT-b2 — chaque
     * titulaire accuse la sienne ; la première réception fait partir l'échéance de l'avis d'attribution du lot.
     */
    void accuserNotification(Attribution a, String idOffre) {
        cnm.prs.entity.AttributionTitulaire t = titulaire(a, idOffre);
        if (t != null) {
            if (a.getNotifieLe() != null && t.getNotificationRecueLe() == null && t.getRetireLe() == null) {
                t.setNotificationRecueLe(maintenant());
                titulaires.save(t);
                Offre o = offres.findById(idOffre).orElse(null);
                evaluation.tracerAttribution(a.getIdDmc(), "NOTIFICATION_RECUE", "Lot " + a.getLot() + " : notification reçue par le titulaire "
                        + (o == null ? idOffre : "offre n° " + o.getNumero() + " (" + o.getRaisonSociale() + ")") + " (accusé de lecture de la plateforme)");
                if (a.getNotificationRecueLe() == null) {
                    a.setNotificationRecueLe(t.getNotificationRecueLe());
                    a.setReceptionDeclaree(false);
                    attributions.save(a);
                }
            }
            return;
        }
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

    /**
     * ⚠️ R9a (manuel révisé, lot 2, §B4 n° 13 ; V111) — les types suspensifs sont ceux du paramètre {@code RECOURS_SUSPENSIFS}
     * (défaut : révision ARMP et référé), lus au moment de la question : un réglage vaut aussi pour les recours déjà déclarés.
     */
    private boolean suspensif(String type) {
        return parametres.recoursSuspensifs().contains(type);
    }

    /** Un recours suspensif sans décision, dont la suspension (20 jours au plus) court encore à cette date. */
    private boolean bloquant(AttributionRecours r, LocalDate le) {
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
        // ⚠️ MT-b2 — l'attributaire de tête, ou un titulaire d'un contrat-cadre multi-attributaire.
        Attribution a = attributionDeLOffre(idOffre, x -> x.getInformeLe() != null)
                .orElseThrow(() -> new BusinessRuleException("Votre offre n'est pas attributaire d'un marché.", "NON_ATTRIBUTAIRE"));
        cnm.prs.entity.AttributionTitulaire tit = titulaire(a, idOffre);
        if (a.getRetireLe() != null || tit != null && tit.getRetireLe() != null) {
            throw new BusinessRuleException("Le marché vous a été retiré.", "LOT_RETIRE");
        }
        if (a.getSigneLe() != null) {
            throw new BusinessRuleException("Le marché est signé : les pièces ne se déposent plus.", "DEJA_SIGNE");
        }
        String t = type == null ? null : type.trim().toUpperCase(Locale.ROOT);
        if (AttributionPiece.ACTE_ENGAGEMENT_ORIGINAL.equals(t)) {
            // ⚠️ R10 (§B5, H2) — l'original de l'acte d'engagement : sans échéance (il précède la signature), daté de sa remise.
            deposerOriginal(a, dateDelivrance, fichier, "par l'attributaire, offre n° " + o.getNumero(), idOffre);
            ceremonies.notifierPrmp(a.getIdDmc(), TypeNotification.PIECES_ATTRIBUTAIRE_DEPOSEES, "Pièce de l'attributaire déposée",
                    "L'attributaire du lot " + a.getLot() + " (procédure " + a.getIdDmc() + ") a déposé l'original signé de son acte d'engagement ;"
                            + " vérifiez-le sur la plateforme.");
            return piecesAttributaire(a, piecesRepository.findByIdDmcAndLotOrderByIdAsc(a.getIdDmc(), a.getLot()), idOffre);
        }
        if (aujourdhui().isAfter(echeancePieces(a))) {
            throw new BusinessRuleException("Le délai de dépôt des pièces est dépassé (" + echeancePieces(a).format(JOUR) + ").", "DELAI_DEPASSE");
        }
        if (!AttributionPiece.FISCALE.equals(t) && !AttributionPiece.SOCIALE.equals(t)) {
            throw new BadRequestException("Type de pièce inconnu : FISCALE, SOCIALE ou ACTE_ENGAGEMENT_ORIGINAL.", "TYPE_INVALIDE");
        }
        if (dateDelivrance == null) {
            throw new BadRequestException("La date de délivrance de la pièce est à indiquer.", "DATE_DELIVRANCE_OBLIGATOIRE");
        }
        LocalDate limite = limiteDelivrance(a, t);
        if (dateDelivrance.isBefore(limite) || dateDelivrance.isAfter(aujourdhui())) {
            throw new BadRequestException("La pièce " + (AttributionPiece.FISCALE.equals(t) ? "fiscale doit dater de moins de six mois"
                    : "sociale doit dater de moins de trois mois") + " à la notification de l'attribution (délivrée le " + limite.format(JOUR)
                    + " au plus tôt).", "PIECE_PERIMEE");
        }
        Televerse f = lire(fichier, true);
        AttributionPiece p = enregistrerFichier(a, t, null, f);
        p.setDateDelivrance(dateDelivrance);
        p.setIdOffre(idOffre);   // ⚠️ MT-b2 (V123) : la pièce de cette offre
        piecesRepository.save(p);
        evaluation.tracerAttribution(a.getIdDmc(), "PIECE_ATTRIBUTAIRE", "Lot " + a.getLot() + ", offre n° " + o.getNumero() + " : pièce "
                + t.toLowerCase(Locale.FRENCH) + " déposée (délivrée le " + dateDelivrance.format(JOUR) + ")");
        ceremonies.notifierPrmp(a.getIdDmc(), TypeNotification.PIECES_ATTRIBUTAIRE_DEPOSEES, "Pièce de l'attributaire déposée",
                (tit == null ? "L'attributaire" : "Le titulaire " + o.getRaisonSociale()) + " du lot " + a.getLot() + " (procédure " + a.getIdDmc()
                        + ") a déposé sa pièce " + t.toLowerCase(Locale.FRENCH) + " ; vérifiez-la sur la plateforme.");
        return piecesAttributaire(a, piecesRepository.findByIdDmcAndLotOrderByIdAsc(a.getIdDmc(), a.getLot()), idOffre);
    }

    /**
     * ⚠️ R10 (manuel révisé, lot 2, §B5 ; H2) — la PRMP enregistre l'original signé de l'acte d'engagement qu'elle a reçu : {@code dateRemise}
     * (au plus aujourd'hui, défaut aujourd'hui ; 400 {@code DATE_INVALIDE}), le scan (400 {@code FICHIER_OBLIGATOIRE}) ; 409
     * {@code NON_INFORME}, {@code DEJA_SIGNE}, {@code LOT_RETIRE}. Il se vérifie ensuite comme les autres pièces.
     */
    public void deposerOriginalParPrmp(Long idDmc, Integer lot, LocalDate dateRemise, MultipartFile fichier) {
        deposerOriginalParPrmp(idDmc, lot, dateRemise, fichier, null);
    }

    /**
     * ⚠️ 2026-10-16 (tranche MT-b2) — {@code idOffre} : pour un contrat-cadre multi-attributaire, le titulaire dont l'original est remis
     * (400 {@code TITULAIRE_OBLIGATOIRE} ; 409 {@code NON_TITULAIRE} hors des titulaires non retirés) ; ignoré sinon.
     */
    public void deposerOriginalParPrmp(Long idDmc, Integer lot, LocalDate dateRemise, MultipartFile fichier, String idOffre) {
        exigerPrmp(idDmc);
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot)).filter(x -> x.getInformeLe() != null)
                .orElseThrow(() -> new BusinessRuleException("Les candidats de ce lot ne sont pas informés.", "NON_INFORME"));
        if (a.getRetireLe() != null) {
            throw new BusinessRuleException("Le marché de ce lot est retiré.", "LOT_RETIRE");
        }
        exigerNonSigne(a);
        deposerOriginal(a, dateRemise, fichier, "par la PRMP", titulaireActifDemande(a, idOffre));
    }

    /**
     * ⚠️ MT-b2 — l'offre visée par un geste de la PRMP sur un titulaire : hors multi-attributaire, l'attributaire de tête ; sinon
     * {@code idOffre}, obligatoire (400 {@code TITULAIRE_OBLIGATOIRE}) et titulaire non retiré du lot (409 {@code NON_TITULAIRE}).
     */
    private String titulaireActifDemande(Attribution a, String idOffre) {
        List<cnm.prs.entity.AttributionTitulaire> tit = titulairesDe(a);
        if (tit.isEmpty()) {
            return a.getIdOffreAttribuee();
        }
        String id = nettoyer(idOffre);
        if (id == null) {
            throw new BadRequestException("Contrat-cadre multi-attributaire : précisez le titulaire visé (idOffre).", "TITULAIRE_OBLIGATOIRE");
        }
        if (actifs(tit).stream().noneMatch(t -> t.getIdOffre().equals(id))) {
            throw new BusinessRuleException("Cette offre n'est pas un titulaire en cours du contrat-cadre.", "NON_TITULAIRE");
        }
        return id;
    }

    /** Le dépôt de l'original de l'acte d'engagement, quel qu'en soit l'auteur : date de remise au plus aujourd'hui, fichier obligatoire. */
    private void deposerOriginal(Attribution a, LocalDate dateRemise, MultipartFile fichier, String par, String idOffre) {
        LocalDate remise = dateRemise == null ? aujourdhui() : dateRemise;
        if (remise.isAfter(aujourdhui())) {
            throw new BadRequestException("La date de remise de l'original ne peut être à venir.", "DATE_INVALIDE");
        }
        Televerse f = lire(fichier, true);
        AttributionPiece p = enregistrerFichier(a, AttributionPiece.ACTE_ENGAGEMENT_ORIGINAL, null, f);
        p.setDateDelivrance(remise);
        p.setIdOffre(idOffre);   // ⚠️ MT-b2 (V123)
        piecesRepository.save(p);
        evaluation.tracerAttribution(a.getIdDmc(), "PIECE_ATTRIBUTAIRE", "Lot " + a.getLot() + " : original signé de l'acte d'engagement déposé "
                + par + " (remis le " + remise.format(JOUR) + ")");
    }

    /** Les pièces de l'attributaire, pour lui ; 409 {@code NON_ATTRIBUTAIRE}. */
    @Transactional(readOnly = true)
    public AttributionDto.PiecesAttributaire piecesDuCandidat(String idCandidat, String idOffre) {
        sienne(idCandidat, idOffre);
        Attribution a = attributionDeLOffre(idOffre, x -> x.getInformeLe() != null)   // ⚠️ MT-b2 : ou un titulaire
                .orElseThrow(() -> new BusinessRuleException("Votre offre n'est pas attributaire d'un marché.", "NON_ATTRIBUTAIRE"));
        return piecesAttributaire(a, piecesRepository.findByIdDmcAndLotOrderByIdAsc(a.getIdDmc(), a.getLot()), idOffre);
    }

    /**
     * La PRMP vérifie une pièce : {@code conforme} obligatoire (400 {@code CONFORME_OBLIGATOIRE}), un motif pour une pièce non conforme
     * (400 {@code MOTIF_OBLIGATOIRE}) ; ⚠️ R6c (règle 7, art. 20-I) : une pièce fiscale de plus de six mois ou sociale de plus de trois
     * mois à l'information ne peut être reconnue conforme (400 {@code PIECE_TROP_ANCIENNE}, {@code details} nature / dateDelivrance /
     * limite / reference) ; 409 {@code DEJA_VERIFIEE} ; 404 pièce inconnue du lot.
     */
    public void verifierPiece(Long idDmc, Integer lot, Long id, AttributionDto.VerificationRequest v) {
        exigerPrmp(idDmc);
        AttributionPiece p = piecesRepository.findById(id).filter(x -> x.getIdDmc().equals(idDmc) && x.getLot().equals(lot)
                && NATURES_ATTRIBUTAIRE.contains(x.getNature()))
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
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot)).orElseThrow();
        // ⚠️ R10 — l'original de l'acte d'engagement n'a pas d'ancienneté limite.
        LocalDate limite = a.getInformeLe() == null || AttributionPiece.ACTE_ENGAGEMENT_ORIGINAL.equals(p.getNature()) ? null
                : limiteDelivrance(a, p.getNature());
        if (v.conforme() && limite != null && p.getDateDelivrance() != null && p.getDateDelivrance().isBefore(limite)) {
            java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
            details.put("nature", p.getNature());
            details.put("dateDelivrance", p.getDateDelivrance().toString());
            details.put("limite", limite.toString());
            details.put("reference", a.getInformeLe().toLocalDate().toString());
            throw new BadRequestException("La pièce " + p.getNature().toLowerCase(Locale.FRENCH) + " est délivrée le "
                    + p.getDateDelivrance().format(JOUR) + ", avant le " + limite.format(JOUR) + " : plus de "
                    + (AttributionPiece.FISCALE.equals(p.getNature()) ? MOIS_FISCALE : MOIS_SOCIALE) + " mois à la notification de l'attribution ("
                    + a.getInformeLe().toLocalDate().format(JOUR) + "), elle ne peut être reconnue conforme (art. 20-I).", "PIECE_TROP_ANCIENNE",
                    details);
        }
        p.setConforme(v.conforme());
        p.setMotif(nettoyer(v.motif()));
        p.setVerifieeLe(maintenant());
        p.setVerifieePar(acteur());
        piecesRepository.save(p);
        evaluation.tracerAttribution(idDmc, "PIECE_VERIFIEE", "Lot " + lot + " : " + libellePiece(p.getNature()) + " "
                + (v.conforme() ? "conforme" : "non conforme — " + p.getMotif()));
        notifierOffre(a, p.getIdOffre() != null ? p.getIdOffre() : a.getIdOffreAttribuee(), TypeNotification.PIECE_ATTRIBUTAIRE_VERIFIEE,
                "Vérification de votre pièce",
                "Votre " + libellePiece(p.getNature()) + " est reconnu(e) " + (v.conforme() ? "conforme."
                        : "non conforme : " + p.getMotif() + ". Vous pouvez en déposer une nouvelle " + (AttributionPiece.ACTE_ENGAGEMENT_ORIGINAL.equals(p.getNature()) ? "avant la signature du marché." : "jusqu'au " + echeancePieces(a).format(JOUR) + ".")));
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
        if (!titulairesDe(a).isEmpty()) {
            retirerTitulaire(a, titulaireActifDemande(a, r.idOffre()), motif);
            return;
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

    /**
     * ⚠️ 2026-10-16 (tranche MT-b2, réponse 2 du pilote ; décret n° 2019-1064, art. 17-18) — le retrait d'un titulaire d'un contrat-cadre
     * multi-attributaire, faute de ses pièces conformes à l'échéance : le contrat-cadre continue avec les autres (le suivant prend la
     * tête s'il le faut) ; s'il n'en reste qu'un, la PRMP peut déclarer la procédure infructueuse (art. 17) ; s'il n'en reste aucun, le lot
     * est retiré. 409 {@code PIECES_CONFORMES}, {@code DELAI_EN_COURS}.
     */
    private void retirerTitulaire(Attribution a, String idOffre, String motif) {
        List<AttributionPiece> siennes = piecesDe(a, piecesRepository.findByIdDmcAndLotOrderByIdAsc(a.getIdDmc(), a.getLot()), idOffre);
        if (conforme(siennes, AttributionPiece.FISCALE) && conforme(siennes, AttributionPiece.SOCIALE)) {
            throw new BusinessRuleException("Les pièces fiscale et sociale de ce titulaire sont conformes.", "PIECES_CONFORMES");
        }
        if (!aujourdhui().isAfter(echeancePieces(a))) {
            throw new BusinessRuleException("Le titulaire peut déposer ses pièces jusqu'au " + echeancePieces(a).format(JOUR) + ".", "DELAI_EN_COURS");
        }
        cnm.prs.entity.AttributionTitulaire t = titulaire(a, idOffre);
        t.setRetireLe(maintenant());
        t.setRetirePar(acteur());
        t.setMotifRetrait(motif);
        titulaires.save(t);
        List<cnm.prs.entity.AttributionTitulaire> restants = actifs(titulairesDe(a));
        Offre o = offres.findById(idOffre).orElse(null);
        String qui = o == null ? idOffre : "offre n° " + o.getNumero() + " (" + o.getRaisonSociale() + ")";
        if (restants.isEmpty()) {
            a.setRetireLe(t.getRetireLe());
            a.setRetirePar(t.getRetirePar());
            a.setMotifRetrait(motif);
            a.setEtat(Attribution.RETIRE);
        } else if (idOffre.equals(a.getIdOffreAttribuee())) {
            cnm.prs.entity.AttributionTitulaire tete = restants.get(0);
            a.setIdOffreAttribuee(tete.getIdOffre());
            a.setMontant(tete.getMontant());
            a.setMontantTtc(tete.getMontantTtc());
            a.setDelai(tete.getDelai());
        }
        attributions.save(a);
        evaluation.tracerAttribution(a.getIdDmc(), "RETRAIT", "Lot " + a.getLot() + " : titulaire " + qui + " retiré du contrat-cadre — " + motif
                + " ; " + (restants.isEmpty() ? "plus aucun titulaire, le lot est retiré"
                        : restants.size() + " titulaire(s) restant(s)" + (restants.size() == 1
                                ? " : la PRMP peut déclarer la procédure infructueuse (décret n° 2019-1064, art. 17)" : "")));
        notifierOffre(a, idOffre, TypeNotification.MARCHE_RETIRE, "Retrait du contrat-cadre", "Faute de pièces fiscales et sociales conformes "
                + "produites dans le délai, vous êtes retiré des titulaires du contrat-cadre du lot " + a.getLot() + " (procédure " + a.getIdDmc()
                + ", art. 20-I) : " + motif + ".");
    }

    /**
     * ⚠️ MT-b2 — avant la signature d'un contrat-cadre multi-attributaire : 409 {@code PIECES_NON_CONFORMES} ou {@code AE_ORIGINAL_MANQUANT},
     * {@code details.offres} = les numéros des titulaires en défaut.
     */
    private void exigerPiecesTitulaires(Attribution a, List<AttributionPiece> toutes, List<cnm.prs.entity.AttributionTitulaire> tit) {
        List<Integer> sansPieces = new ArrayList<>();
        List<Integer> sansOriginal = new ArrayList<>();
        boolean original = aeOriginalExige(a.getIdDmc());
        for (cnm.prs.entity.AttributionTitulaire t : tit) {
            List<AttributionPiece> siennes = piecesDe(a, toutes, t.getIdOffre());
            Integer numero = offres.findById(t.getIdOffre()).map(Offre::getNumero).orElse(null);
            if (!conforme(siennes, AttributionPiece.FISCALE) || !conforme(siennes, AttributionPiece.SOCIALE)) {
                sansPieces.add(numero);
            } else if (original && !conforme(siennes, AttributionPiece.ACTE_ENGAGEMENT_ORIGINAL)) {
                sansOriginal.add(numero);
            }
        }
        if (!sansPieces.isEmpty()) {
            throw new BusinessRuleException("Les pièces fiscale et sociale de chaque titulaire doivent être reconnues conformes avant la signature "
                    + "du contrat-cadre (art. 20-I) : offre(s) n° " + sansPieces.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "))
                    + ".", "PIECES_NON_CONFORMES", null, Map.of("offres", sansPieces));
        }
        if (!sansOriginal.isEmpty()) {
            throw new BusinessRuleException("Chaque titulaire remet l'original signé de son acte d'engagement, reconnu conforme, avant la signature "
                    + "du contrat-cadre (art. 31) : offre(s) n° " + sansOriginal.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "))
                    + ".", "AE_ORIGINAL_MANQUANT", null, Map.of("offres", sansOriginal));
        }
    }

    /** ⚠️ MT-b2 (art. 17) — un contrat-cadre multi-attributaire dont un seul titulaire reste après les retraits, avant la signature. */
    boolean seulTitulaireRestant(Attribution a) {
        List<cnm.prs.entity.AttributionTitulaire> tit = titulairesDe(a);
        return tit.size() > 1 && actifs(tit).size() == 1 && a.getSigneLe() == null;
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
        sansSuite.getObject().exigerAucuneDemandeEnCours(idDmc);   // ⚠️ 2d-3 : le sans suite précède la signature (art. 55)
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot)).filter(x -> x.getInformeLe() != null)
                .orElseThrow(() -> new BusinessRuleException("Les candidats de ce lot ne sont pas informés.", "NON_INFORME"));
        exigerNonSigne(a);
        AttributionDto.DelaiAttente d = delais.calculer(a, aujourdhui());
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
        // ⚠️ 2026-10-16 (tranche MT-b2, réponse 1 du pilote ; décret n° 2019-1064, art. 15) — un contrat-cadre multi-attributaire est
        // signé par la PRMP et tous les titulaires : les pièces de CHACUN, et son original de l'acte d'engagement, sont exigés.
        List<cnm.prs.entity.AttributionTitulaire> tit = actifs(titulairesDe(a));
        if (!tit.isEmpty()) {
            exigerPiecesTitulaires(a, toutes, tit);
            Televerse f = lire(fichier, true);
            a.setDateSignature(dateSignature);
            a.setSigneLe(maintenant());
            a.setSignePar(acteur());
            a.setEtat(Attribution.SIGNE);
            attributions.save(a);
            enregistrerFichier(a, AttributionPiece.MARCHE_SIGNE, null, f);
            evaluation.tracerAttribution(idDmc, "SIGNATURE_MARCHE", "Lot " + lot + " : contrat-cadre signé le " + dateSignature.format(JOUR)
                    + " par la PRMP et ses " + tit.size() + " titulaire(s)");
            return;
        }
        if (!conforme(toutes, AttributionPiece.FISCALE) || !conforme(toutes, AttributionPiece.SOCIALE)) {
            throw new BusinessRuleException("Les pièces fiscale et sociale de l'attributaire doivent être reconnues conformes avant la signature "
                    + "(art. 20-I).", "PIECES_NON_CONFORMES");
        }
        // ⚠️ R10 (manuel révisé, lot 2, §B5 ; H2) — offre déposée en ligne : l'original signé de l'acte d'engagement, reconnu conforme.
        if (aeOriginalExige(idDmc) && !conforme(toutes, AttributionPiece.ACTE_ENGAGEMENT_ORIGINAL)) {
            throw new BusinessRuleException("L'attributaire remet l'original signé de son acte d'engagement, reconnu conforme, avant la signature "
                    + "du marché : son dépôt scellé n'a valu signature que pendant la procédure (art. 31).", "AE_ORIGINAL_MANQUANT");
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
     * ⚠️ R9a (manuel révisé, lot 2, §B4 n° 14 ; art. 54 ; V111) — l'approbation du marché par l'autorité compétente : {@code autorite}
     * (400 {@code CHAMP_OBLIGATOIRE}), {@code dateApprobation} entre la signature et aujourd'hui (400 {@code DATE_INVALIDE}), la décision
     * d'approbation (400 {@code FICHIER_OBLIGATOIRE}) ; PRMP ; 409 {@code NON_SIGNE}, {@code DEJA_APPROUVE}, {@code DEJA_NOTIFIE}.
     * Indépendante de l'enregistrement ; la notification exige les deux.
     */
    public void approuver(Long idDmc, Integer lot, String autorite, LocalDate date, MultipartFile fichier) {
        exigerPrmp(idDmc);
        Attribution a = exigerSigne(idDmc, lot);
        if (a.getApprouveLe() != null) {
            throw new BusinessRuleException("Le marché de ce lot est déjà approuvé.", "DEJA_APPROUVE");
        }
        if (a.getNotifieLe() != null) {
            throw new BusinessRuleException("Le marché de ce lot est déjà notifié.", "DEJA_NOTIFIE");
        }
        String qui = nettoyer(autorite);
        if (qui == null) {
            throw new BadRequestException("L'approbation nomme l'autorité qui approuve le marché.", "CHAMP_OBLIGATOIRE");
        }
        if (qui.length() > 200) {
            throw new BadRequestException("L'autorité tient en 200 caractères.", "CHAMP_TROP_LONG");
        }
        if (date == null || date.isBefore(a.getDateSignature()) || date.isAfter(aujourdhui())) {
            throw new BadRequestException("La date d'approbation se situe entre la signature et aujourd'hui.", "DATE_INVALIDE");
        }
        Televerse f = lire(fichier, true);
        a.setApprobationAutorite(qui);
        a.setApprobationDate(date);
        a.setApprouveLe(maintenant());
        a.setApprouvePar(acteur());
        attributions.save(a);
        enregistrerFichier(a, AttributionPiece.APPROBATION, null, f);
        evaluation.tracerAttribution(idDmc, "APPROBATION", "Lot " + lot + " : marché approuvé le " + date.format(JOUR) + " par " + qui);
    }

    /**
     * La notification du marché à l'attributaire : {@code dateNotification} (entre l'enregistrement et aujourd'hui, et pas avant
     * l'approbation), {@code dateReception} facultative (réception déclarée, entre la notification et aujourd'hui ; sinon l'accusé de
     * lecture de la plateforme) — 400 {@code DATE_INVALIDE} ; 409 {@code NON_SIGNE}, {@code MARCHE_NON_APPROUVE} (⚠️ R9a),
     * {@code NON_ENREGISTRE}, {@code DEJA_NOTIFIE}.
     */
    public void notifier(Long idDmc, Integer lot, AttributionDto.NotificationRequest r) {
        exigerPrmp(idDmc);
        Attribution a = exigerSigne(idDmc, lot);
        if (a.getApprouveLe() == null) {
            throw new BusinessRuleException("Le marché se notifie après son approbation par l'autorité compétente (art. 54).", "MARCHE_NON_APPROUVE");
        }
        if (a.getEnregistreLe() == null) {
            throw new BusinessRuleException("Le marché se notifie après son enregistrement (art. 54).", "NON_ENREGISTRE");
        }
        if (a.getNotifieLe() != null) {
            throw new BusinessRuleException("Le marché de ce lot est déjà notifié.", "DEJA_NOTIFIE");
        }
        LocalDate date = r == null ? null : r.dateNotification();
        LocalDate plancher = a.getApprobationDate().isAfter(a.getDateEnregistrement()) ? a.getApprobationDate() : a.getDateEnregistrement();
        if (date == null || date.isBefore(plancher) || date.isAfter(aujourdhui())) {
            throw new BadRequestException("La date de notification se situe entre l'approbation et l'enregistrement (le plus tardif des deux) "
                    + "et aujourd'hui.", "DATE_INVALIDE");
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
        // ⚠️ 2026-10-16 (marchés subséquents, MS-1, V124) — un contrat-cadre ou un marché à commandes entre en vigueur à sa notification.
        contrats.getObject().creerALaNotification(a);
        evaluation.tracerAttribution(idDmc, "NOTIFICATION", "Lot " + lot + " : marché notifié le " + date.format(JOUR)
                + (reception == null ? "" : ", reçu le " + reception.format(JOUR) + " (réception déclarée)") + " ; avis d'attribution à publier "
                + "au plus tard le " + echeanceAvis(a).format(JOUR));
        // ⚠️ 2026-10-16 (tranche MT-b2 ; décret n° 2019-1064, art. 15) — le contrat-cadre est notifié à chacun de ses titulaires.
        List<cnm.prs.entity.AttributionTitulaire> tit = actifs(titulairesDe(a));
        if (!tit.isEmpty()) {
            for (cnm.prs.entity.AttributionTitulaire t : tit) {
                notifierOffre(a, t.getIdOffre(), TypeNotification.MARCHE_NOTIFIE, "Notification du contrat-cadre", "Le contrat-cadre du lot " + lot
                        + " (procédure " + idDmc + "), signé le " + a.getDateSignature().format(JOUR) + " par la PRMP et ses " + tit.size()
                        + " titulaire(s), approuvé le " + a.getApprobationDate().format(JOUR) + " et enregistré, vous est notifié. Il prend effet à "
                        + "sa réception ; le contrat-cadre signé est disponible dans votre espace (Mes offres).");
            }
            return;
        }
        notifierAttributaire(a, TypeNotification.MARCHE_NOTIFIE, "Notification du marché", "Le marché du lot " + lot + " (procédure " + idDmc
                + "), signé le " + a.getDateSignature().format(JOUR) + ", approuvé le " + a.getApprobationDate().format(JOUR) + " et enregistré, vous est notifié. Il prend effet à sa réception ; le marché "
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
                + (date.isAfter(echeanceAvis(a)) ? ", après l'échéance de " + JOURS_AVIS + " jours (le " + echeanceAvis(a).format(JOUR) + ")" : ""));
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
        List<cnm.prs.entity.AttributionTitulaire> tit = actifs(titulairesDe(a));
        if (!tit.isEmpty()) {
            // ⚠️ 2026-10-16 (tranche MT-b2 ; décret n° 2019-1064, art. 15) — l'avis du contrat-cadre liste ses titulaires et leurs offres
            // indicatives ; le montant à payer est défini dans chaque marché subséquent (art. 16).
            para(el, "Contrat-cadre attribué à " + tit.size() + " titulaire(s) :");
            for (cnm.prs.entity.AttributionTitulaire t : tit) {
                Offre x = offres.findById(t.getIdOffre()).orElse(null);
                para(el, "– " + (x == null ? t.getIdOffre() : x.getRaisonSociale() + ", NIF " + x.getNif()) + " : offre indicative de "
                        + (t.getMontant() == null ? "—" : FormulairesEnLigne.lisible(t.getMontant()) + " Ariary hors taxes")
                        + (t.getDelai() == null ? "" : ", délai : " + t.getDelai()) + ".");
            }
            para(el, "Le montant à payer est défini dans chaque marché subséquent, après remise en concurrence des titulaires (décret n° 2019-1064, "
                    + "art. 16).");
        } else {
            para(el, "Attributaire : " + o.getRaisonSociale() + ", NIF " + o.getNif() + ".");
            para(el, "Montant du marché : " + (a.getMontant() == null ? "—" : FormulairesEnLigne.lisible(a.getMontant()) + " Ariary hors taxes")
                    + (a.getMontantTtc() == null ? "" : " (offre lue : " + FormulairesEnLigne.lisible(a.getMontantTtc()) + " Ariary toutes taxes comprises)")
                    + (a.getDelai() == null ? "" : " ; délai d'exécution : " + a.getDelai()) + ".");
        }
        para(el, "Marché signé le " + a.getDateSignature().format(JOUR)
                + (a.getApprobationDate() == null ? "" : ", approuvé le " + a.getApprobationDate().format(JOUR))   // ⚠️ R9a : nul avant V111
                + ", enregistré le " + a.getDateEnregistrement().format(JOUR)
                + ", notifié le " + a.getDateNotification().format(JOUR) + ".");
        para(el, "Le présent avis est publié le " + a.getDatePublicationAvis().format(JOUR) + " (art. 53 de la loi n° 2016-055).");
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        para(el, "La Personne responsable des marchés publics, " + nomPrmp() + " — signé électroniquement sur la plateforme le "
                + a.getAvisPublieLe().format(HORODATAGE) + ".");
        return new DocumentLibre("AVIS_ATTRIBUTION", allotie ? a.getLot() : null, el,
                "Procédure " + a.getIdDmc() + (allotie ? ", lot " + a.getLot() : "") + " — avis d'attribution (modèle provisoire)");
    }

    // ------------------------------------------------------------------ ⚠️ tranche 2d-1, §B7 l'alerte des réexamens

    /** Un réexamen sans réponse, à 2 jours de son échéance (10 jours) : la PRMP est alertée, une fois. Le nombre d'alertes émises. */
    int alerterReexamens(LocalDate aujourdhui) {
        int n = 0;
        for (AttributionRecours r : recoursRepository.findByTypeAndDecideLeIsNullAndAlerteLeIsNullAndArchiveLeIsNull(AttributionRecours.REEXAMEN)) {
            LocalDate echeance = r.getDateReception().plusDays(JOURS_REEXAMEN);
            if (aujourdhui.isBefore(echeance.minusDays(2))) {
                continue;
            }
            ceremonies.notifierPrmp(r.getIdDmc(), TypeNotification.ECHEANCE_REEXAMEN, "Réexamen à répondre", "Procédure " + r.getIdDmc() + ", lot "
                    + r.getLot() + " : la demande de réexamen de « " + r.getRequerant() + " » attend votre réponse au plus tard le "
                    + echeance.format(JOUR) + " (art. 79).");
            r.setAlerteLe(LocalDateTime.now(horloge).withNano(0));
            recoursRepository.save(r);
            n++;
        }
        return n;
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
        // ⚠️ MT-b2 — la pièce de son offre (un titulaire d'un contrat-cadre multi-attributaire voit les siennes).
        boolean sienne = idOffre.equals(p.getIdOffre()) || p.getIdOffre() == null && idOffre.equals(a.getIdOffreAttribuee());
        if (!sienne || !(AttributionPiece.FISCALE.equals(p.getNature()) || AttributionPiece.SOCIALE.equals(p.getNature()))) {
            throw new ResourceNotFoundException("Fichier introuvable : " + id + ".");
        }
        return p;
    }

    /** Le marché signé, pour l'attributaire ; le lire après la notification vaut accusé de réception. 404 avant la signature. */
    public AttributionPiece marcheDuCandidat(String idCandidat, String idOffre) {
        sienne(idCandidat, idOffre);
        Attribution a = attributionDeLOffre(idOffre, x -> x.getSigneLe() != null)   // ⚠️ MT-b2 : ou un titulaire non retiré
                .filter(x -> titulaire(x, idOffre) == null || titulaire(x, idOffre).getRetireLe() == null)
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
        notifierOffre(a, a.getIdOffreAttribuee(), type, titre, corps);
    }

    /** ⚠️ MT-b2 — la notification du candidat d'une offre précise (un titulaire d'un contrat-cadre multi-attributaire). */
    private void notifierOffre(Attribution a, String idOffre, TypeNotification type, String titre, String corps) {
        Offre o = idOffre == null ? null : offres.findById(idOffre).orElse(null);
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

    // ------------------------------------------------------------------ ⚠️ tranche 2d-2 : l'archivage du cycle retiré

    /** Les pièces et recours du cycle retiré du lot sont archivés : le nouveau cycle (réattribution) part d'une page blanche. */
    void archiverCycle(Long idDmc, Integer lot, LocalDateTime le) {
        piecesRepository.findByIdDmcAndLotOrderByIdAsc(idDmc, lot).forEach(p -> {
            p.setArchiveLe(le);
            piecesRepository.save(p);
        });
        recoursRepository.findByIdDmcAndLotOrderByDateReceptionAscIdAsc(idDmc, lot).forEach(r -> {
            r.setArchiveLe(le);
            recoursRepository.save(r);
        });
    }
}
