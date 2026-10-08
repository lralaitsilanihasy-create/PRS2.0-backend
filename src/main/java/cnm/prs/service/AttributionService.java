package cnm.prs.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.AttributionDto;
import cnm.prs.dto.EntrepriseCandidatDto;
import cnm.prs.dto.EvaluationDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.AttributionExplication;
import cnm.prs.entity.AttributionLettre;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Evaluation;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.Offre;
import cnm.prs.entity.PieceJointeDossier;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutFicheMarche;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.PayloadTropVolumineuxException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.TypePieceJointeRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>L'évaluation des offres, lot 2, tranche 2a</strong> (demande front du 2026-10-07 « de la proposition d'attribution à la
 * notification », §B1, §B2 ; V79). Après le rapport d'évaluation signé, chaque lot a son état ; pour un lot attribuable, la PRMP crée
 * le <strong>dossier de marché</strong> (famille {@code DDM}, un par lot : arbitrage Q2 du pilote ; chaque marché en ligne : Q11), qui
 * suit le circuit de la Commission sans changement. Les pièces 14 à 18 y sont jointes d'office, dont le <strong>projet de marché
 * produit par le serveur</strong> (arbitrage Q1). L'avis de la Commission remonte au lot. ⚠️ <strong>Tranche 2b</strong> (§B3, §B4.1, §B4.2 ;
 * V80) : la PRMP attribue à l'offre proposée par la CAO, et à elle seule (Q4), informe les candidats par des lettres qu'elle signe
 * électroniquement (Q9, modèles provisoires : Q7), déclare l'affichage ; le délai d'attente de dix jours francs court de la plus
 * tardive des deux (art. 78) ; les candidats non retenus demandent des explications, la PRMP répond par écrit.
 */
@Service
@Transactional
public class AttributionService {

    private static final Logger log = LoggerFactory.getLogger(AttributionService.class);

    public static final String EN_EVALUATION = "EN_EVALUATION";
    public static final String PROPOSE = "PROPOSE";
    public static final String AVIS_RENDU = "AVIS_RENDU";
    public static final String SIGNABLE = "SIGNABLE";
    static final String AVIS_DEFAVORABLE = "DEF";
    private static final java.time.format.DateTimeFormatter JOUR = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final java.time.format.DateTimeFormatter HORODATAGE = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    private final AttributionRepository attributions;
    private final EvaluationService evaluation;
    private final SeanceService seance;
    private final SaisieService saisie;
    private final ValeursPpmService valeursPpm;
    private final FicheMarcheService fiches;
    private final EntrepriseCandidatService entreprises;
    private final GenerateurDocumentsFiche generateur;
    private final DossierMecRepository dmcRepository;
    private final DossierRepository dossierRepository;
    private final OffreRepository offres;
    private final PvExamenRepository pvRepository;
    private final PieceJointeDossierRepository pieces;
    private final TypePieceJointeRepository typesPiece;
    private final FicheMarcheRepository ficheRepository;
    private final DocumentFicheMarcheRepository documentRepository;
    // ⚠️ Tranche 2b
    private final cnm.prs.repository.AttributionLettreRepository lettres;
    private final cnm.prs.repository.AttributionExplicationRepository explications;
    private final cnm.prs.repository.CompteCandidatRepository candidats;
    private final cnm.prs.repository.PrmpRepository prmpRepository;
    private final NotificationService notifications;
    private final CeremonieService ceremonies;
    private final ParametreService parametres;
    private final java.time.Clock horloge;
    // ⚠️ Tranche 2c
    private final AttributionExecutionService execution;
    /** ⚠️ PI-d2b — le sous-type du dossier de marché d'une consultation de prestations intellectuelles. */
    public static final String SOUS_TYPE_PI = "MPI";
    /** ⚠️ PI-d2b — les motifs de rejet d'une consultation de prestations intellectuelles (note technique, classement, négociation). */
    private final ResultatsPi resultatsPi;
    /** ⚠️ 2d-1 (Q3) — les reprises de l'évaluation après un avis défavorable. */
    private final cnm.prs.repository.AttributionRepriseRepository reprises;
    /** ⚠️ 2d-3 — la déclaration sans suite : elle arrête les gestes d'attribution et s'affiche sur la page publique. */
    private final SansSuiteService sansSuite;

    public AttributionService(AttributionRepository attributions, EvaluationService evaluation, SeanceService seance, SaisieService saisie,
            ValeursPpmService valeursPpm, FicheMarcheService fiches, EntrepriseCandidatService entreprises, GenerateurDocumentsFiche generateur,
            DossierMecRepository dmcRepository, DossierRepository dossierRepository, OffreRepository offres, PvExamenRepository pvRepository,
            PieceJointeDossierRepository pieces, TypePieceJointeRepository typesPiece, FicheMarcheRepository ficheRepository,
            DocumentFicheMarcheRepository documentRepository, cnm.prs.repository.AttributionLettreRepository lettres,
            cnm.prs.repository.AttributionExplicationRepository explications, cnm.prs.repository.CompteCandidatRepository candidats,
            cnm.prs.repository.PrmpRepository prmpRepository, NotificationService notifications, CeremonieService ceremonies,
            ParametreService parametres, java.time.Clock horloge, AttributionExecutionService execution, ResultatsPi resultatsPi,
            cnm.prs.repository.AttributionRepriseRepository reprises, SansSuiteService sansSuite) {
        this.sansSuite = sansSuite;
        this.reprises = reprises;
        this.resultatsPi = resultatsPi;
        this.execution = execution;
        this.attributions = attributions;
        this.evaluation = evaluation;
        this.seance = seance;
        this.saisie = saisie;
        this.valeursPpm = valeursPpm;
        this.fiches = fiches;
        this.entreprises = entreprises;
        this.generateur = generateur;
        this.dmcRepository = dmcRepository;
        this.dossierRepository = dossierRepository;
        this.offres = offres;
        this.pvRepository = pvRepository;
        this.pieces = pieces;
        this.typesPiece = typesPiece;
        this.ficheRepository = ficheRepository;
        this.documentRepository = documentRepository;
        this.lettres = lettres;
        this.explications = explications;
        this.candidats = candidats;
        this.prmpRepository = prmpRepository;
        this.notifications = notifications;
        this.ceremonies = ceremonies;
        this.parametres = parametres;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ §B1 le lot et son état

    /** L'attribution de la procédure, lot par lot : CAO, responsable, PRMP, UGPM ; 404 tant que l'évaluation n'est pas ouverte. */
    @Transactional(readOnly = true)
    public AttributionDto lire(Long idDmc) {
        evaluation.controlerLecture(idDmc);
        EvaluationDto ev = evaluation.vueSansGarde(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("L'évaluation de cette procédure n'est pas ouverte."));
        List<AttributionDto.LotAttribution> lots = new ArrayList<>();
        for (EvaluationDto.Lot l : ev.lots()) {
            // ⚠️ 2d-2 — l'évaluation rouverte (reprise, réattribution) n'efface pas l'état d'un autre lot déjà attribué.
            Attribution engage = attributions.findById(new Attribution.Cle(idDmc, l.lot())).filter(x -> x.getAttribueLe() != null).orElse(null);
            if (!Evaluation.CLOSE.equals(ev.etat()) && engage == null) {
                lots.add(new AttributionDto.LotAttribution(l.lot(), EN_EVALUATION, null, null, false, null, null, null, List.of(), null, List.of(),
                        null, null, null, null, null, null, null, reprises(idDmc, l.lot())));
                continue;
            }
            Attribution a = attributions.findById(new Attribution.Cle(idDmc, l.lot())).orElse(null);
            AttributionDto.DossierMarche dm = a == null || a.getIdDossier() == null ? null : dossierMarche(a);
            AttributionDto.DelaiAttente delai = a == null ? null : delaiAttente(a);
            String etat = a == null ? PROPOSE
                    : delai != null ? (delai.ecoule() ? SIGNABLE : Attribution.INFORME)
                    : a.getAttribueLe() != null ? Attribution.ATTRIBUE
                    : dm != null && dm.avis() != null ? AVIS_RENDU : a.getEtat();
            // ⚠️ Tranche 2c — la suite du lot : signé, notifié, avis publié, ou retiré.
            AttributionExecutionService.Suite s = a == null ? AttributionExecutionService.Suite.VIDE : execution.suite(a);
            if (a != null) {
                etat = a.getInfructueuxLe() != null ? Attribution.INFRUCTUEUX : AttributionExecutionService.etat(a, etat);
            }
            lots.add(new AttributionDto.LotAttribution(l.lot(), etat, l.proposition(), dm, a != null && a.getProjetPdf() != null,
                    a == null ? null : attributaire(a), a == null ? null : information(a), delai, explicationsDto(idDmc, l.lot()), s.miseAuPoint(),
                    s.recours(), s.signature(), s.enregistrement(), s.notification(), s.avis(), s.pieces(), s.retrait(),
                    a == null || a.getInfructueuxLe() == null ? null : new AttributionDto.Infructuosite(a.getInfructueuxLe(), a.getInfructueuxPar(),
                            a.getMotifInfructuosite(), a.getDecisionReference(), a.getDecisionDate(), a.getSuite()),
                    reprises(idDmc, l.lot())));
        }
        return new AttributionDto(idDmc, lots);
    }

    // ------------------------------------------------------------------ ⚠️ tranche 2b, §B3 le choix de l'attributaire (art. 35-VII)

    /**
     * La PRMP attribue le lot à l'offre <strong>proposée par la CAO</strong>, et à elle seule (arbitrage Q4 du pilote) : {@code idOffre}
     * facultatif ; 409 {@code LOT_INFRUCTUEUX}, {@code AVIS_NON_RENDU}, {@code AVIS_DEFAVORABLE}, {@code OFFRE_NON_PROPOSEE},
     * {@code DEJA_ATTRIBUE} ; 403 hors PRMP de la fiche.
     */
    public AttributionDto attribuer(Long idDmc, Integer lot, AttributionDto.AttribuerRequest r) {
        exigerPrmpSeule(idDmc, "Le marché s'attribue par la PRMP de la fiche (art. 35-VII).");
        sansSuite.exigerAucuneDeclaration(idDmc);   // ⚠️ 2d-3 : une procédure déclarée sans suite n'a plus de geste d'attribution
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot)).orElse(null);
        if (a == null || a.getIdDossier() == null) {
            EvaluationDto.Lot l = lotEvalue(idDmc, lot);
            if (l.proposition() != null && l.proposition().infructueux()) {
                throw new BusinessRuleException("Le rapport propose de déclarer ce lot infructueux : il ne s'attribue pas.", "LOT_INFRUCTUEUX");
            }
            throw new BusinessRuleException("L'avis de la Commission sur le dossier de marché n'est pas rendu.", "AVIS_NON_RENDU");
        }
        if (a.getAttribueLe() != null) {
            throw new BusinessRuleException("Ce lot est déjà attribué.", "DEJA_ATTRIBUE");
        }
        AttributionDto.DossierMarche dm = dossierMarche(a);
        if (dm == null || dm.avis() == null) {
            throw new BusinessRuleException("L'avis de la Commission sur le dossier de marché n'est pas rendu.", "AVIS_NON_RENDU");
        }
        if (AVIS_DEFAVORABLE.equals(dm.avis())) {
            throw new BusinessRuleException("La Commission a rendu un avis défavorable sur le dossier de marché : le lot ne s'attribue pas.",
                    "AVIS_DEFAVORABLE");
        }
        String demandee = r == null ? null : nettoyer(r.idOffre());
        if (demandee != null && !demandee.equals(a.getIdOffreProposee())) {
            throw new BusinessRuleException("Le marché s'attribue à l'offre proposée par la commission d'appel d'offres, et à elle seule.",
                    "OFFRE_NON_PROPOSEE");
        }
        EvaluationDto.Proposition p = lotEvalue(idDmc, lot).proposition();
        Offre offre = offres.findById(a.getIdOffreProposee()).orElseThrow();
        a.setIdOffreAttribuee(offre.getIdOffre());
        a.setAttribueLe(maintenant());
        a.setAttribuePar(acteur());
        a.setMotifAttribution(r == null ? null : nettoyer(r.motif()));
        a.setMontant(p == null ? null : p.montant());
        a.setMontantTtc(p == null ? null : p.montantTtc());
        a.setDelai(p == null ? null : p.delai());
        a.setEtat(Attribution.ATTRIBUE);
        attributions.save(a);
        evaluation.tracerAttribution(idDmc, "ATTRIBUTION", "Lot " + lot + " : attribué à l'offre n° " + offre.getNumero() + " ("
                + offre.getRaisonSociale() + ")" + (a.getMontant() == null ? "" : ", " + FormulairesEnLigne.lisible(a.getMontant()) + " Ariary HT")
                + ", avis " + dm.avis() + " de la Commission" + (a.getMotifAttribution() == null ? "" : " — " + a.getMotifAttribution()));
        return lire(idDmc);
    }

    // ------------------------------------------------------------------ ⚠️ tranche 2b, §B4.1 l'information des candidats (art. 52-I, 78)

    /** Le délai d'attente avant la signature : dix jours francs (art. 78 de la loi n° 2016-055). */
    public static final int JOURS_FRANCS = 10;

    /**
     * La PRMP informe les candidats du lot : une lettre par candidat (rejet et motifs, ou attribution), signée électroniquement par elle
     * (Q9 : signature électronique simple, horodatée, journalisée), envoyée sur la plateforme et par courriel ; la date d'affichage du
     * résultat au siège est déclarée ; le délai d'attente court de la plus tardive des deux. 400 {@code DATE_AFFICHAGE_OBLIGATOIRE},
     * {@code DATE_AFFICHAGE_INVALIDE} (avant l'attribution, ou à venir) ; 409 {@code NON_ATTRIBUE}, {@code DEJA_INFORME}.
     */
    public AttributionDto informer(Long idDmc, Integer lot, AttributionDto.InformerRequest r) {
        exigerPrmpSeule(idDmc, "Les candidats sont informés par la PRMP de la fiche (art. 52-I).");
        sansSuite.exigerAucuneDeclaration(idDmc);   // ⚠️ 2d-3 : une procédure déclarée sans suite n'a plus de geste d'attribution
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot)).orElse(null);
        if (a == null || a.getAttribueLe() == null) {
            lotEvalue(idDmc, lot);
            throw new BusinessRuleException("Le lot n'est pas attribué : les candidats s'informent après le choix de l'attributaire.", "NON_ATTRIBUE");
        }
        if (a.getInformeLe() != null) {
            throw new BusinessRuleException("Les candidats de ce lot sont déjà informés.", "DEJA_INFORME");
        }
        LocalDate affichage = r == null ? null : r.dateAffichage();
        if (affichage == null) {
            throw new BadRequestException("La date d'affichage du résultat au siège est à déclarer.", "DATE_AFFICHAGE_OBLIGATOIRE");
        }
        LocalDateTime maintenant = maintenant();
        if (affichage.isBefore(a.getAttribueLe().toLocalDate()) || affichage.isAfter(maintenant.toLocalDate())) {
            throw new BadRequestException("La date d'affichage se situe entre l'attribution (" + a.getAttribueLe().toLocalDate().format(JOUR)
                    + ") et aujourd'hui.", "DATE_AFFICHAGE_INVALIDE");
        }
        a.setInformeLe(maintenant);
        a.setInformePar(acteur());
        a.setSignataire(nomPrmp());
        a.setDateAffichage(affichage);
        a.setEtat(Attribution.INFORME);
        attributions.save(a);
        EvaluationDto ev = evaluation.vueSansGarde(idDmc).orElseThrow();
        EvaluationDto.Lot l = ev.lots().stream().filter(x -> Objects.equals(x.lot(), lot)).findFirst().orElseThrow();
        Entete entete = entete(idDmc, fiches.etatValide(idDmc).orElse(null), lot, ev.lots().size() > 1);
        boolean pi = evaluation.estPi(idDmc);
        Offre attributaire = offres.findById(a.getIdOffreAttribuee()).orElseThrow();
        AttributionDto.DelaiAttente delai = delaiAttente(a);
        int n = 0;
        for (EvaluationDto.OffreEvaluee o : l.offres()) {
            boolean retenue = o.idOffre().equals(a.getIdOffreAttribuee());
            Offre offre = offres.findById(o.idOffre()).orElse(null);
            if (offre == null) {
                continue;
            }
            AttributionLettre x = new AttributionLettre();
            x.setIdDmc(idDmc);
            x.setLot(lot);
            x.setIdOffre(o.idOffre());
            x.setType(retenue ? AttributionLettre.ATTRIBUTION : AttributionLettre.NON_RETENU);
            // ⚠️ PI-d2b — une proposition PI retenue à l'examen préliminaire : sa note technique et son classement.
            x.setMotif(retenue ? null : pi && o.ecartee() == null ? resultatsPi.motifRejet(idDmc, lot, o.idOffre()) : motifRejet(o));
            x.setProduiteLe(maintenant);
            for (GenerateurDocumentsFiche.Fichier f : generateur.generer(lettre(entete, a, x, offre, attributaire, delai))) {
                if ("pdf".equals(f.extension())) {
                    x.setPdf(f.contenu());
                } else if ("docx".equals(f.extension())) {
                    x.setDocx(f.contenu());
                }
            }
            CompteCandidat c = candidats.findById(offre.getIdCandidat()).orElse(null);
            String email = c == null ? null : c.getEmail();
            x.setEnvoyeeLe(email == null || email.isBlank() ? null : maintenant);
            lettres.save(x);
            notifications.emettreCandidat(retenue ? TypeNotification.ATTRIBUTION : TypeNotification.RESULTAT_DISPONIBLE, offre.getIdCandidat(),
                    email, idDmc.intValue(), TypeObjet.PROCEDURE, retenue ? "Attribution du marché" : "Résultat de l'appel d'offres",
                    retenue ? "Votre offre n° " + offre.getNumero() + " est retenue" + entete.surLot() + " de la procédure « " + entete.objet()
                            + " ». La lettre d'attribution, signée de la PRMP, est disponible dans votre espace (Mes offres)."
                            : "Votre offre n° " + offre.getNumero() + entete.surLot() + " de la procédure « " + entete.objet() + " » n'est pas "
                                    + "retenue. La lettre et ses motifs sont disponibles dans votre espace (Mes offres) ; vous pouvez y demander "
                                    + "des explications par écrit. Le marché ne sera pas signé avant le " + delai.signableLe().format(JOUR) + ".");
            n++;
        }
        evaluation.tracerAttribution(idDmc, "INFORMATION", "Lot " + lot + " : " + n + " candidat(s) informé(s), lettres signées électroniquement "
                + "par " + a.getSignataire() + " le " + maintenant.format(HORODATAGE) + " ; affichage au siège le " + affichage.format(JOUR)
                + " ; signature possible à partir du " + delai.signableLe().format(JOUR));
        return lire(idDmc);
    }

    /** Une lettre du lot (PDF, ou Word avec {@code docx}) : mêmes lecteurs que l'attribution ; 404 sans lettre pour cette offre. */
    @Transactional(readOnly = true)
    public AttributionLettre lettre(Long idDmc, Integer lot, String idOffre) {
        evaluation.controlerLecture(idDmc);
        return lettres.findByIdDmcAndLotOrderByIdAsc(idDmc, lot).stream().filter(x -> x.getIdOffre().equals(idOffre)).reduce((x, y) -> y)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune lettre pour cette offre."));
    }

    /**
     * Le résultat de son offre, pour le candidat, après l'information : la première consultation vaut accusé de lecture de la
     * plateforme (art. 52-I, la preuve de la réception) ; 404 avant l'information.
     */
    public AttributionDto.Resultat resultat(String idCandidat, String idOffre) {
        Offre o = sienne(idCandidat, idOffre);
        AttributionLettre x = derniereLettre(idOffre);
        Attribution a = attributions.findById(new Attribution.Cle(x.getIdDmc(), x.getLot())).orElseThrow();
        accuser(x);
        Offre retenue = offres.findById(a.getIdOffreAttribuee()).orElse(null);
        // ⚠️ 2c — consulter son résultat après la notification vaut accusé de réception du marché (date d'effet).
        boolean retenueParMoi = idOffre.equals(a.getIdOffreAttribuee());
        execution.accuserNotification(a, idOffre);
        AttributionDto.DelaiAttente d = delaiAttente(a);
        return new AttributionDto.Resultat(idOffre, o.getNumero(), x.getLot(), AttributionLettre.ATTRIBUTION.equals(x.getType()), x.getMotif(),
                retenue == null ? null : retenue.getRaisonSociale(), a.getMontant(), a.getMontantTtc(), a.getDelai(), x.getPdf() != null,
                a.getInformeLe(), a.getDateAffichage(), d == null ? null : d.fin(), d == null ? null : d.signableLe(), a.getDateSignature(),
                a.getDateNotification(), retenueParMoi ? a.getNotificationRecueLe() : null, retenueParMoi && a.getSigneLe() != null,
                execution.piecesPourCandidat(a, idOffre), retenueParMoi && a.getRetireLe() != null);
    }

    /** La lettre du candidat (PDF, ou Word) ; la lire vaut aussi accusé de lecture. 404 avant l'information. */
    public AttributionLettre lettreDuCandidat(String idCandidat, String idOffre) {
        sienne(idCandidat, idOffre);
        AttributionLettre x = derniereLettre(idOffre);
        accuser(x);
        return x;
    }

    /** Les résultats publiés d'une procédure (page publique), lot par lot, après l'information. */
    @Transactional(readOnly = true)
    public List<AttributionDto.ResultatPublic> resultatsPublics(Long idDmc) {
        // ⚠️ 2d-3 (§B6) — la procédure déclarée sans suite : une seule entrée, ses motifs.
        cnm.prs.entity.SansSuite declaration = sansSuite.declaration(idDmc);
        if (declaration != null) {
            return List.of(new AttributionDto.ResultatPublic(null, null, null, null, null, null, false, false, null, declaration.getDecisionDate(),
                    true, declaration.getMotifs()));
        }
        // ⚠️ 2d-1 (§B6) — un lot déclaré infructueux s'affiche aussi, avec son motif.
        return attributions.findByIdDmcOrderByLotAsc(idDmc).stream().filter(a -> a.getInformeLe() != null || a.getInfructueuxLe() != null).map(a -> {
            if (a.getInfructueuxLe() != null) {
                return new AttributionDto.ResultatPublic(a.getLot(), null, null, null, null, null, false, true, a.getMotifInfructuosite(),
                        a.getDecisionDate(), false, null);
            }
            Offre o = offres.findById(a.getIdOffreAttribuee()).orElse(null);
            return new AttributionDto.ResultatPublic(a.getLot(), o == null ? null : o.getRaisonSociale(), a.getMontant(), a.getInformeLe(),
                    a.getDateAffichage(), a.getDatePublicationAvis(), a.getAvisPublieLe() != null, false, null, null, false, null);
        }).toList();
    }

    // ------------------------------------------------------------------ ⚠️ tranche 2d-1, §B6 l'infructuosité (art. 56), Q3 la reprise

    /** Les suites qu'une déclaration d'infructuosité peut annoncer (déclarées, pas conduites ici). */
    static final java.util.Set<String> SUITES = java.util.Set.of("RELANCE", "RESTREINTE", "NEGOCIEE");

    /**
     * La PRMP déclare le lot infructueux, sur sa décision formelle : pour un lot que le rapport propose infructueux, ou après l'avis
     * défavorable de la Commission sur le dossier de marché (Q3) ; jamais après l'attribution (art. 56-VI). 400
     * {@code MOTIF_OBLIGATOIRE}, {@code DECISION_OBLIGATOIRE}, {@code DECISION_DATE_INVALIDE}, {@code SUITE_INVALIDE} ; 409
     * {@code EVALUATION_NON_CLOSE}, {@code DEJA_ATTRIBUE}, {@code DEJA_INFRUCTUEUX}, {@code INFRUCTUOSITE_NON_PROPOSEE}. Tous les
     * candidats du lot sont notifiés ; le lot s'affiche infructueux sur la page publique des résultats.
     */
    public AttributionDto declarerInfructueux(Long idDmc, Integer lot, AttributionDto.InfructuositeRequest r) {
        exigerPrmpSeule(idDmc, "Le lot se déclare infructueux par la PRMP de la fiche (art. 56).");
        sansSuite.exigerAucuneDeclaration(idDmc);   // ⚠️ 2d-3 : une procédure déclarée sans suite n'a plus de geste d'attribution
        EvaluationDto ev = evaluation.vueSansGarde(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("L'évaluation de cette procédure n'est pas ouverte."));
        if (!Evaluation.CLOSE.equals(ev.etat())) {
            throw new BusinessRuleException("L'infructuosité se déclare après le rapport d'évaluation signé.", "EVALUATION_NON_CLOSE");
        }
        EvaluationDto.Lot l = ev.lots().stream().filter(x -> Objects.equals(x.lot(), lot)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable dans l'évaluation : " + lot + "."));
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot)).orElse(null);
        if (a != null && a.getInfructueuxLe() != null) {
            throw new BusinessRuleException("Ce lot est déjà déclaré infructueux.", "DEJA_INFRUCTUEUX");
        }
        // ⚠️ 2d-2 — une réattribution suit une attribution retirée : l'infructuosité reste exclue (art. 56-VI).
        boolean dejaAttribue = reprises.findByIdDmcAndLotOrderByIdAsc(idDmc, lot).stream()
                .anyMatch(x -> cnm.prs.entity.AttributionReprise.REATTRIBUTION.equals(x.getType()));
        if (a != null && a.getAttribueLe() != null || dejaAttribue) {
            throw new BusinessRuleException("Le lot est attribué : l'infructuosité ne peut en aucun cas intervenir après la décision d'attribution "
                    + "(art. 56-VI).", "DEJA_ATTRIBUE");
        }
        boolean propose = l.proposition() != null && l.proposition().infructueux();
        AttributionDto.DossierMarche dm = a == null || a.getIdDossier() == null ? null : dossierMarche(a);
        boolean defavorable = dm != null && AVIS_DEFAVORABLE.equals(dm.avis());
        if (!propose && !defavorable) {
            throw new BusinessRuleException("L'infructuosité se déclare pour un lot que le rapport propose infructueux, ou après l'avis défavorable "
                    + "de la Commission sur le dossier de marché.", "INFRUCTUOSITE_NON_PROPOSEE");
        }
        String motif = r == null ? null : nettoyer(r.motif());
        if (motif == null) {
            throw new BadRequestException("La déclaration d'infructuosité se motive.", "MOTIF_OBLIGATOIRE");
        }
        String reference = r.decision() == null ? null : nettoyer(r.decision().reference());
        if (reference == null || r.decision().date() == null) {
            throw new BadRequestException("La décision de la PRMP (référence et date) est attendue.", "DECISION_OBLIGATOIRE");
        }
        if (r.decision().date().isAfter(maintenant().toLocalDate())) {
            throw new BadRequestException("La date de la décision ne peut pas être à venir.", "DECISION_DATE_INVALIDE");
        }
        String suite = r.suite() == null || r.suite().isBlank() ? null : r.suite().trim().toUpperCase(Locale.ROOT);
        if (suite != null && !SUITES.contains(suite)) {
            throw new BadRequestException("La suite déclarée est RELANCE, RESTREINTE ou NEGOCIEE.", "SUITE_INVALIDE");
        }
        if (a == null) {
            a = new Attribution();
            a.setIdDmc(idDmc);
            a.setLot(lot);
        }
        a.setEtat(Attribution.INFRUCTUEUX);
        a.setInfructueuxLe(maintenant());
        a.setInfructueuxPar(acteur());
        a.setMotifInfructuosite(motif);
        a.setDecisionReference(reference);
        a.setDecisionDate(r.decision().date());
        a.setSuite(suite);
        attributions.save(a);
        Entete entete = entete(idDmc, fiches.etatValide(idDmc).orElse(null), lot, ev.lots().size() > 1);
        java.util.Set<String> avertis = new java.util.HashSet<>();
        for (Offre o : offres.findByIdDmcOrderByNumeroAscDateCreationAsc(idDmc)) {
            boolean duLot = Objects.equals(o.getLot(), lot) || (o.getLot() == null && ev.lots().size() == 1);
            if (!duLot || o.getDateDepot() == null || Offre.RETIREE.equals(o.getEtat()) || !avertis.add(o.getIdCandidat())) {
                continue;
            }
            CompteCandidat c = candidats.findById(o.getIdCandidat()).orElse(null);
            notifications.emettreCandidat(TypeNotification.PROCEDURE_INFRUCTUEUSE, o.getIdCandidat(), c == null ? null : c.getEmail(),
                    idDmc.intValue(), TypeObjet.PROCEDURE, "Procédure déclarée infructueuse", "La procédure « " + entete.objet() + " »"
                            + entete.surLot() + " est déclarée infructueuse par décision n° " + reference + " du " + r.decision().date().format(JOUR)
                            + " : " + motif + (suite == null ? "" : " Suite annoncée : " + suite.toLowerCase(Locale.FRENCH) + ".") );
        }
        evaluation.tracerAttribution(idDmc, "INFRUCTUEUX", "Lot " + lot + " déclaré infructueux (décision n° " + reference + " du "
                + r.decision().date().format(JOUR) + ", " + (defavorable ? "après l'avis défavorable de la Commission" : "proposé par le rapport")
                + ") : " + motif + " ; " + avertis.size() + " candidat(s) notifié(s)");
        return lire(idDmc);
    }

    /**
     * Q3 — après l'avis défavorable de la Commission sur le dossier de marché, la PRMP reprend l'évaluation (motif) : le rapport signé est
     * archivé, l'évaluation redevient en cours, la commission rouvre l'étape de son choix et produit un nouveau rapport, puis un nouveau
     * dossier de marché se crée pour le lot. 400 {@code MOTIF_OBLIGATOIRE} ; 409 {@code EVALUATION_NON_CLOSE}, {@code AVIS_NON_DEFAVORABLE},
     * {@code DEJA_INFRUCTUEUX}, {@code AUTRES_LOTS_ATTRIBUES} ({@code details.lots} : un autre lot déjà attribué empêche de rouvrir
     * l'évaluation commune).
     */
    public AttributionDto reprendre(Long idDmc, Integer lot, AttributionDto.RepriseRequest r) {
        exigerPrmpSeule(idDmc, "L'évaluation se reprend sur décision de la PRMP de la fiche.");
        sansSuite.exigerAucuneDeclaration(idDmc);   // ⚠️ 2d-3 : une procédure déclarée sans suite n'a plus de geste d'attribution
        EvaluationDto ev = evaluation.vueSansGarde(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("L'évaluation de cette procédure n'est pas ouverte."));
        if (!Evaluation.CLOSE.equals(ev.etat())) {
            throw new BusinessRuleException("L'évaluation n'est pas close : elle est déjà en cours.", "EVALUATION_NON_CLOSE");
        }
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot)).orElse(null);
        if (a != null && a.getInfructueuxLe() != null) {
            throw new BusinessRuleException("Ce lot est déclaré infructueux.", "DEJA_INFRUCTUEUX");
        }
        AttributionDto.DossierMarche dm = a == null || a.getIdDossier() == null ? null : dossierMarche(a);
        if (dm == null || !AVIS_DEFAVORABLE.equals(dm.avis())) {
            throw new BusinessRuleException("L'évaluation se reprend après l'avis défavorable de la Commission sur le dossier de marché.",
                    "AVIS_NON_DEFAVORABLE");
        }
        List<Integer> engages = attributions.findByIdDmcOrderByLotAsc(idDmc).stream()
                .filter(x -> !Objects.equals(x.getLot(), lot) && x.getAttribueLe() != null).map(Attribution::getLot).toList();
        if (!engages.isEmpty()) {
            throw new BusinessRuleException("D'autres lots sont déjà attribués : l'évaluation, commune aux lots, ne se rouvre plus.",
                    "AUTRES_LOTS_ATTRIBUES", null, Map.of("lots", engages));
        }
        String motif = r == null ? null : nettoyer(r.motif());
        if (motif == null) {
            throw new BadRequestException("La reprise de l'évaluation se motive.", "MOTIF_OBLIGATOIRE");
        }
        cnm.prs.entity.AttributionReprise x = new cnm.prs.entity.AttributionReprise();
        x.setIdDmc(idDmc);
        x.setLot(lot);
        x.setIdDossier(a.getIdDossier());
        x.setIdOffreProposee(a.getIdOffreProposee());
        x.setAvis(dm.avis());
        x.setMotif(motif);
        x.setLe(maintenant());
        x.setPar(acteur());
        evaluation.reprendre(idDmc, x);
        reprises.save(x);
        attributions.delete(a);
        evaluation.tracerAttribution(idDmc, "REPRISE", "Lot " + lot + " : le dossier de marché n° " + dm.idDossier() + " (avis défavorable) est "
                + "clos pour l'attribution ; l'évaluation reprend — " + motif);
        return lire(idDmc);
    }

    // ------------------------------------------------------------------ ⚠️ tranche 2d-2, Q8 la réattribution après le retrait

    /** Les délais de validité des offres selon la catégorie de la fiche (jours, comptés depuis la date limite de remise). */
    static final List<String> VALIDITE = List.of("B04-VO-01", "B04-VT-01", "B04-DV-01", "B04-DP-01");

    /**
     * La PRMP réattribue un lot retiré faute de pièces fiscales et sociales (Q8) au candidat suivant : 400 {@code MOTIF_OBLIGATOIRE} ; 409
     * {@code NON_RETIRE}, {@code EVALUATION_NON_CLOSE}, {@code AUCUN_SUIVANT_ELIGIBLE} (la seule sortie est la déclaration sans suite :
     * l'infructuosité est exclue après l'attribution, art. 56-VI), {@code OFFRE_EXPIREE} ({@code details.echeance}). Le rapport signé
     * est archivé, l'évaluation reprend : la post-qualification du suivant (lot 1 : l'offre retirée devient non qualifiée) ou sa
     * négociation (PI : la négociation réussie passe {@code RETIREE}) ; puis un nouveau rapport, un nouveau dossier de marché, une
     * nouvelle attribution, une nouvelle information des candidats et un nouveau délai d'attente. Les pièces, recours et lettres du
     * cycle retiré sont archivés. Sans délai de validité saisi, la validité n'est pas contrôlée (la note de la reprise le dit).
     */
    public AttributionDto reattribuer(Long idDmc, Integer lot, AttributionDto.RepriseRequest r) {
        exigerPrmpSeule(idDmc, "Le marché se réattribue par la PRMP de la fiche.");
        sansSuite.exigerAucuneDeclaration(idDmc);   // ⚠️ 2d-3 : une procédure déclarée sans suite n'a plus de geste d'attribution
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot)).orElse(null);
        if (a == null || a.getRetireLe() == null) {
            throw new BusinessRuleException("Le marché de ce lot n'est pas retiré : la réattribution suit un retrait faute de pièces.", "NON_RETIRE");
        }
        EvaluationDto ev = evaluation.vueSansGarde(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("L'évaluation de cette procédure n'est pas ouverte."));
        if (!Evaluation.CLOSE.equals(ev.etat())) {
            throw new BusinessRuleException("L'évaluation est déjà en cours.", "EVALUATION_NON_CLOSE");
        }
        String motif = r == null ? null : nettoyer(r.motif());
        if (motif == null) {
            throw new BadRequestException("La réattribution se motive.", "MOTIF_OBLIGATOIRE");
        }
        boolean pi = evaluation.estPi(idDmc);
        String retiree = a.getIdOffreAttribuee();
        String suivant = pi ? java.util.Optional.ofNullable(resultatsPi.suivantApresRetrait(idDmc, lot)).map(cnm.prs.dto.FinanciereDto.Ligne::idOffre)
                .orElse(null) : evaluation.suivantApresRetrait(idDmc, lot, retiree).orElse(null);
        if (suivant == null) {
            throw new BusinessRuleException("Aucun candidat suivant n'est éligible : l'infructuosité étant exclue après l'attribution (art. 56-VI), "
                    + "la procédure ne peut se clore que par une déclaration sans suite.", "AUCUN_SUIVANT_ELIGIBLE");
        }
        FicheMarcheService.EtatVersion v = fiches.etatValide(idDmc).orElse(null);
        Integer jours = v == null ? null : VALIDITE.stream().map(c -> ControlesFicheMarche.nombre(v.valeur(c))).filter(Objects::nonNull)
                .map(java.math.BigDecimal::intValue).findFirst().orElse(null);
        LocalDateTime limite = v == null ? null : ProceduresEnLigneService.dateLimite(v);
        String note;
        if (jours == null || limite == null) {
            note = "Validité des offres non contrôlée : aucun délai de validité n'est saisi sur la fiche.";
        } else {
            LocalDate echeance = limite.toLocalDate().plusDays(jours);
            if (maintenant().toLocalDate().isAfter(echeance)) {
                throw new BusinessRuleException("Les offres ne sont plus valides depuis le " + echeance.format(JOUR) + " (délai de " + jours
                        + " jours depuis la date limite de remise).", "OFFRE_EXPIREE", null, Map.of("echeance", echeance.toString()));
            }
            note = "Offres valides jusqu'au " + echeance.format(JOUR) + " (" + jours + " jours depuis la date limite de remise).";
        }
        cnm.prs.entity.AttributionReprise x = new cnm.prs.entity.AttributionReprise();
        x.setIdDmc(idDmc);
        x.setLot(lot);
        x.setType(cnm.prs.entity.AttributionReprise.REATTRIBUTION);
        x.setIdDossier(a.getIdDossier());
        x.setIdOffreProposee(retiree);
        x.setMotif(motif);
        x.setNote(note);
        x.setLe(maintenant());
        x.setPar(acteur());
        evaluation.reprendre(idDmc, x);
        if (pi) {
            resultatsPi.retirerNegociation(idDmc, lot, retiree, a.getMotifRetrait() == null ? motif : a.getMotifRetrait());
        } else {
            evaluation.reattribuer(idDmc, lot, retiree, a.getMotifRetrait() == null ? motif : a.getMotifRetrait());
        }
        reprises.save(x);
        LocalDateTime le = maintenant();
        execution.archiverCycle(idDmc, lot, le);
        lettres.findByIdDmcAndLotOrderByIdAsc(idDmc, lot).forEach(l -> {
            l.setArchiveLe(le);
            lettres.save(l);
        });
        attributions.delete(a);
        evaluation.tracerAttribution(idDmc, "REATTRIBUTION", "Lot " + lot + " : le marché retiré est réattribué au candidat suivant — " + motif
                + ". " + note);
        return lire(idDmc);
    }

    /** Le rapport archivé d'une reprise (PDF, ou Word) : CAO, responsable, PRMP, UGPM ; 404 sans lui. */
    @Transactional(readOnly = true)
    public byte[] rapportArchive(Long idDmc, Long idReprise, boolean docx) {
        evaluation.controlerLecture(idDmc);
        cnm.prs.entity.AttributionReprise x = reprises.findById(idReprise).filter(y -> y.getIdDmc().equals(idDmc))
                .orElseThrow(() -> new ResourceNotFoundException("Reprise introuvable : " + idReprise + "."));
        byte[] b = docx ? x.getRapportDocx() : x.getRapportPdf();
        if (b == null) {
            throw new ResourceNotFoundException("Aucun rapport n'est archivé pour cette reprise.");
        }
        return b;
    }

    private List<AttributionDto.Reprise> reprises(Long idDmc, Integer lot) {
        return reprises.findByIdDmcAndLotOrderByIdAsc(idDmc, lot).stream().map(x -> new AttributionDto.Reprise(x.getId(), x.getLe(), x.getPar(),
                x.getMotif(), x.getIdDossier(), x.getAvis(), x.getRapportPdf() != null, x.getType(),
                cnm.prs.entity.AttributionReprise.REATTRIBUTION.equals(x.getType()) ? x.getIdOffreProposee() : null, x.getNote())).toList();
    }

    // ------------------------------------------------------------------ ⚠️ tranche 2d-1, §B7 compteurs et alertes de la PRMP

    /**
     * Les compteurs de la PRMP sur ses lots : {@code lotsAAttribuer} (avis favorable rendu, non attribués), {@code lotsSignables} (délai
     * d'attente écoulé, non signés), {@code avisAPublier} (marchés notifiés sans avis d'attribution publié), {@code explicationsSansReponse}.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> compteursPrmp(String idPrmp) {
        long aAttribuer = 0;
        long signables = 0;
        long avis = 0;
        long explicationsOuvertes = 0;
        if (idPrmp != null && !idPrmp.isBlank()) {
            for (Attribution a : attributions.pourPrmp(idPrmp)) {
                if (a.getInfructueuxLe() != null || a.getRetireLe() != null) {
                    continue;
                }
                if (a.getAttribueLe() == null && a.getIdDossier() != null) {
                    AttributionDto.DossierMarche dm = dossierMarche(a);
                    if (dm != null && dm.avis() != null && !AVIS_DEFAVORABLE.equals(dm.avis())) {
                        aAttribuer++;
                    }
                }
                AttributionDto.DelaiAttente d = delaiAttente(a);
                if (a.getSigneLe() == null && d != null && d.ecoule()) {
                    signables++;
                }
                if (a.getNotifieLe() != null && a.getAvisPublieLe() == null) {
                    avis++;
                }
                explicationsOuvertes += explications.findByIdDmcAndLotOrderByDemandeeLeAscIdAsc(a.getIdDmc(), a.getLot()).stream()
                        .filter(e -> e.getReponduLe() == null).count();
            }
        }
        return Map.of("lotsAAttribuer", aAttribuer, "lotsSignables", signables, "avisAPublier", avis, "explicationsSansReponse",
                explicationsOuvertes);
    }

    /**
     * Les alertes de la PRMP (§B7), une fois chacune : le délai d'attente écoulé ({@code DELAI_ATTENTE_ECOULE}), l'avis d'attribution
     * à publier sous 5 jours ({@code ECHEANCE_AVIS_ATTRIBUTION}), un réexamen à 2 jours de son échéance ({@code ECHEANCE_REEXAMEN}).
     * Le nombre d'alertes émises.
     */
    public int alerter() {
        int n = 0;
        LocalDate aujourdhui = maintenant().toLocalDate();
        for (Attribution a : attributions.findByInformeLeIsNotNullAndSigneLeIsNullAndRetireLeIsNullAndAlerteDelaiLeIsNull()) {
            AttributionDto.DelaiAttente d = delaiAttente(a);
            if (d == null || !d.ecoule()) {
                continue;
            }
            ceremonies.notifierPrmp(a.getIdDmc(), TypeNotification.DELAI_ATTENTE_ECOULE, "Délai d'attente écoulé", "Procédure " + a.getIdDmc()
                    + ", lot " + a.getLot() + " : le délai d'attente est écoulé depuis le " + d.fin().format(JOUR) + " ; le marché peut être signé "
                    + "(hors recours suspensif et pièces de l'attributaire).");
            a.setAlerteDelaiLe(maintenant());
            attributions.save(a);
            n++;
        }
        for (Attribution a : attributions.findByNotifieLeIsNotNullAndAvisPublieLeIsNullAndAlerteAvisLeIsNull()) {
            LocalDate echeance = a.getDateNotification().plusDays(AttributionExecutionService.JOURS_AVIS);
            if (aujourdhui.isBefore(echeance.minusDays(5))) {
                continue;
            }
            ceremonies.notifierPrmp(a.getIdDmc(), TypeNotification.ECHEANCE_AVIS_ATTRIBUTION, "Avis d'attribution à publier", "Procédure "
                    + a.getIdDmc() + ", lot " + a.getLot() + " : l'avis d'attribution est à publier au plus tard le " + echeance.format(JOUR)
                    + " (art. 53).");
            a.setAlerteAvisLe(maintenant());
            attributions.save(a);
            n++;
        }
        return n + execution.alerterReexamens(aujourdhui);
    }

    // ------------------------------------------------------------------ ⚠️ tranche 2b, §B4.2 les demandes d'explication (art. 52-II)

    /** Un candidat non retenu demande des explications : 400 {@code QUESTION_OBLIGATOIRE} ; 409 {@code NON_INFORME}, {@code OFFRE_RETENUE}. */
    public AttributionDto.Explication demanderExplication(String idCandidat, String idOffre, AttributionDto.ExplicationRequest r) {
        Offre o = sienne(idCandidat, idOffre);
        AttributionLettre x = lettres.findByIdOffreOrderByIdDesc(idOffre).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("Le résultat ne vous est pas encore communiqué.", "NON_INFORME"));
        if (AttributionLettre.ATTRIBUTION.equals(x.getType())) {
            throw new BusinessRuleException("Votre offre est retenue : les explications s'adressent aux candidats non retenus.", "OFFRE_RETENUE");
        }
        String question = r == null ? null : nettoyer(r.question());
        if (question == null) {
            throw new BadRequestException("La demande d'explication exige une question.", "QUESTION_OBLIGATOIRE");
        }
        AttributionExplication e = new AttributionExplication();
        e.setIdDmc(x.getIdDmc());
        e.setLot(x.getLot());
        e.setIdOffre(idOffre);
        e.setQuestion(question);
        e.setDemandeeLe(maintenant());
        explications.save(e);
        evaluation.tracerAttribution(x.getIdDmc(), "EXPLICATION_DEMANDEE", "Lot " + x.getLot() + ", offre n° " + o.getNumero() + " ("
                + o.getRaisonSociale() + ") : " + question);
        ceremonies.notifierPrmp(x.getIdDmc(), TypeNotification.EXPLICATION_DEMANDEE, "Demande d'explication d'un candidat",
                "Le candidat de l'offre n° " + o.getNumero() + " (procédure " + x.getIdDmc() + ", lot " + x.getLot() + ") demande des explications "
                        + "sur le rejet de son offre ; répondez par écrit sur la plateforme (art. 52-II).");
        return explicationDto(e, o);
    }

    /** Les demandes d'explication du candidat pour son offre, et les réponses. */
    @Transactional(readOnly = true)
    public List<AttributionDto.Explication> explicationsDuCandidat(String idCandidat, String idOffre) {
        Offre o = sienne(idCandidat, idOffre);
        return explications.findByIdOffreOrderByDemandeeLeAscIdAsc(idOffre).stream().map(e -> explicationDto(e, o)).toList();
    }

    /** Le fichier joint à une réponse, pour le candidat ; 404 sans fichier. */
    @Transactional(readOnly = true)
    public AttributionExplication fichierExplicationDuCandidat(String idCandidat, String idOffre, Long id) {
        sienne(idCandidat, idOffre);
        return avecFichier(explications.findById(id).filter(e -> e.getIdOffre().equals(idOffre)).orElse(null));
    }

    /** Le fichier joint à une réponse : mêmes lecteurs que l'attribution ; 404 sans fichier. */
    @Transactional(readOnly = true)
    public AttributionExplication fichierExplication(Long idDmc, Long id) {
        evaluation.controlerLecture(idDmc);
        return avecFichier(explications.findById(id).filter(e -> e.getIdDmc().equals(idDmc)).orElse(null));
    }

    /**
     * La réponse écrite de la PRMP : texte obligatoire (400 {@code TEXTE_OBLIGATOIRE}), fichier PDF, JPEG ou PNG facultatif (400
     * {@code FORMAT_INVALIDE}, 413) ; 409 {@code DEJA_REPONDU} ; 404 demande inconnue de la procédure.
     */
    public AttributionDto.Explication repondreExplication(Long idDmc, Long id, String texte, MultipartFile fichier) {
        exigerPrmpSeule(idDmc, "Les explications se donnent par la PRMP de la fiche (art. 52-II).");
        AttributionExplication e = explications.findById(id).filter(x -> x.getIdDmc().equals(idDmc))
                .orElseThrow(() -> new ResourceNotFoundException("Demande d'explication introuvable : " + id + "."));
        if (e.getReponduLe() != null) {
            throw new BusinessRuleException("Cette demande a déjà sa réponse.", "DEJA_REPONDU");
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
        e.setReponse(t);
        e.setReponduLe(maintenant());
        e.setReponduePar(acteur());
        if (contenu != null) {
            String nom = fichier.getOriginalFilename() == null || fichier.getOriginalFilename().isBlank() ? "reponse"
                    : fichier.getOriginalFilename().replaceAll("[\\\\/]", "_");
            e.setReponseNom(nom.length() > 255 ? nom.substring(nom.length() - 255) : nom);
            e.setReponseFormat(format);
            e.setReponseTaille((long) contenu.length);
            e.setReponseContenu(contenu);
        }
        explications.save(e);
        Offre o = offres.findById(e.getIdOffre()).orElseThrow();
        evaluation.tracerAttribution(idDmc, "EXPLICATION_REPONDUE", "Lot " + e.getLot() + ", offre n° " + o.getNumero() + " : réponse écrite"
                + (e.getReponseNom() == null ? "" : ", fichier joint"));
        CompteCandidat c = candidats.findById(o.getIdCandidat()).orElse(null);
        notifications.emettreCandidat(TypeNotification.EXPLICATION_REPONDUE, o.getIdCandidat(), c == null ? null : c.getEmail(), idDmc.intValue(),
                TypeObjet.PROCEDURE, "Réponse à votre demande d'explication", "La PRMP a répondu à votre demande d'explication sur le rejet de "
                        + "votre offre n° " + o.getNumero() + " ; la réponse est dans votre espace (Mes offres).");
        return explicationDto(e, o);
    }

    // ------------------------------------------------------------------ §B2 le dossier de marché

    /**
     * Crée le dossier de marché d'un lot attribuable (PRMP ou son UGPM) et y joint d'office le projet de marché, le cahier des charges
     * (DAO complet), le devis estimatif (bordereau de l'offre proposée), le PV d'ouverture et le rapport d'évaluation : 409
     * {@code EVALUATION_NON_CLOSE}, {@code LOT_INFRUCTUEUX}, {@code DOSSIER_EXISTANT} (avec {@code idDossier}) ; 404 lot inconnu.
     */
    public AttributionDto creerDossier(Long idDmc, Integer lot) {
        exigerPrmp(idDmc);
        sansSuite.exigerAucuneDeclaration(idDmc);   // ⚠️ 2d-3 : une procédure déclarée sans suite n'a plus de geste d'attribution
        EvaluationDto ev = evaluation.vueSansGarde(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("L'évaluation de cette procédure n'est pas ouverte."));
        if (!Evaluation.CLOSE.equals(ev.etat())) {
            throw new BusinessRuleException("Le dossier de marché se crée une fois le rapport d'évaluation signé.", "EVALUATION_NON_CLOSE");
        }
        EvaluationDto.Lot l = ev.lots().stream().filter(x -> Objects.equals(x.lot(), lot)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable dans l'évaluation : " + lot + "."));
        Attribution existante = attributions.findById(new Attribution.Cle(idDmc, lot)).orElse(null);
        if (existante != null && existante.getIdDossier() != null) {
            throw new BusinessRuleException("Le dossier de marché de ce lot existe déjà : " + existante.getIdDossier() + ".", "DOSSIER_EXISTANT",
                    existante.getIdDossier());
        }
        EvaluationDto.Proposition p = l.proposition();
        if (p == null || p.infructueux() || p.idOffre() == null) {
            throw new BusinessRuleException("Le rapport propose de déclarer ce lot infructueux : pas de dossier de marché.", "LOT_INFRUCTUEUX");
        }
        DossierMec dmc = dmcRepository.findById(idDmc).orElseThrow(() -> new ResourceNotFoundException("DMC introuvable : " + idDmc));
        Map<String, String> plan = valeursPpm.lire(dmc.getIdDetail()).valeurs();
        // ⚠️ PI-d2b (arbitrage du pilote, 08/10) — une consultation de prestations intellectuelles : le sous-type MPI (V90).
        String sousType = SousTypesDossier.dossierMarche(plan.get("MODE"), evaluation.estPi(idDmc));   // ⚠️ M1 (manuel de contrôle, §B1)
        ValeursPpmService.EnTete enTete = valeursPpm.enTete(dmc.getIdDetail());
        Dossier dossier = saisie.creerDossierMarche(sousType, enTete.idLocalite(), enTete.idEntiteContract());
        LocalDateTime maintenant = LocalDateTime.now();
        Attribution a = existante != null ? existante : new Attribution();
        a.setIdDmc(idDmc);
        a.setLot(lot);
        a.setEtat(Attribution.AU_CONTROLE);
        a.setIdOffreProposee(p.idOffre());
        a.setIdDossier(dossier.getIdDossier());
        a.setDossierCreeLe(maintenant);
        a.setDossierCreePar(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        // Le projet de marché (Q1) : produit par le serveur, Word et PDF.
        Offre offre = offres.findById(p.idOffre()).orElseThrow();
        for (GenerateurDocumentsFiche.Fichier f : generateur.generer(projet(idDmc, lot, p, offre, plan, ev.lots().size() > 1))) {
            if ("pdf".equals(f.extension())) {
                a.setProjetPdf(f.contenu());
            } else if ("docx".equals(f.extension())) {
                a.setProjetDocx(f.contenu());
            }
        }
        attributions.save(a);
        String suffixe = "_" + idDmc + "_lot" + lot + ".pdf";
        int jointes = 0;
        jointes += joindre(dossier, "PROJET_MARCHE", "projet-de-marche" + suffixe, a.getProjetPdf());
        jointes += joindreCahierDesCharges(dossier, idDmc);
        jointes += joindre(dossier, "DEVIS_ESTIMATIF", "bordereau-offre-" + offre.getNumero() + suffixe,
                seance.bordereauPdf(p.idOffreFinanciere() != null ? p.idOffreFinanciere() : p.idOffre()).orElse(null));
        jointes += joindre(dossier, "PV_OUVERTURE", "pv-ouverture_" + idDmc + ".pdf", seance.pvSigne(idDmc));
        jointes += joindre(dossier, "RAPPORT_ANALYSE", "rapport-evaluation_" + idDmc + ".pdf", evaluation.rapportSigne(idDmc));
        evaluation.tracerAttribution(idDmc, "DOSSIER_MARCHE", "Lot " + lot + " : dossier de marché " + dossier.getIdDossier() + " (" + sousType
                + ") créé pour l'offre n° " + p.numero() + " (" + p.candidat() + "), " + jointes + " pièce(s) jointe(s) d'office");
        return lire(idDmc);
    }

    /** Le projet de marché du lot (PDF, ou Word avec {@code docx}) : mêmes lecteurs ; 404 tant qu'il n'est pas produit. */
    @Transactional(readOnly = true)
    public byte[] projet(Long idDmc, Integer lot, boolean docx) {
        evaluation.controlerLecture(idDmc);
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot))
                .orElseThrow(() -> new ResourceNotFoundException("Le projet de marché de ce lot n'est pas produit."));
        byte[] b = docx ? a.getProjetDocx() : a.getProjetPdf();
        if (b == null) {
            throw new ResourceNotFoundException("Le projet de marché de ce lot n'est pas produit.");
        }
        return b;
    }

    private AttributionDto.DossierMarche dossierMarche(Attribution a) {
        Dossier d = dossierRepository.findById(a.getIdDossier()).orElse(null);
        if (d == null) {
            return null;
        }
        String avis = pvRepository.findSignesParDossierRows(d.getIdDossier()).stream().findFirst().map(PvExamen::getIdAvis).orElse(null);
        return new AttributionDto.DossierMarche(d.getIdDossier(), d.getIdSousType(), d.getStatut(), avis, a.getDossierCreeLe(), a.getDossierCreePar());
    }

    /** Joint une pièce produite au dossier ; 0 sans contenu ou sans type de pièce de ce code au référentiel (journal applicatif). */
    private int joindre(Dossier d, String code, String nom, byte[] contenu) {
        if (contenu == null) {
            return 0;
        }
        TypePieceJointe type = typesPiece.findFirstByCode(code).orElse(null);
        if (type == null) {
            log.warn("[ATTRIBUTION] aucun type de pièce de code {} : pièce non jointe au dossier {}", code, d.getIdDossier());
            return 0;
        }
        PieceJointeDossier p = new PieceJointeDossier();
        p.setIdDossier(d.getIdDossier());
        p.setIdTypePiece(type.getIdTypePiece());
        p.setNomFichier(nom);
        p.setContenu(contenu);
        p.setFormat("PDF");
        p.setTaille((long) contenu.length);
        p.setDateUpload(LocalDateTime.now());
        p.setApresLettreRenvoi(false);
        pieces.save(p);
        return 1;
    }

    /** Le cahier des charges : le DAO complet de la dernière version validée, à défaut ses documents séparés (PDF). */
    private int joindreCahierDesCharges(Dossier d, Long idDmc) {
        FicheMarche validee = ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc).stream()
                .filter(f -> StatutFicheMarche.VALIDEE.name().equals(f.getStatut())).reduce((x, y) -> y).orElse(null);
        if (validee == null) {
            return 0;
        }
        List<DocumentFicheMarche> pdfs = documentRepository.findByIdFicheOrderByIdDocumentAsc(validee.getIdFiche()).stream()
                .filter(x -> "pdf".equals(x.getExtension()) && !DocumentsFicheMarcheService.TYPES_PUBLICATION.contains(x.getType())).toList();
        if (pdfs.stream().anyMatch(x -> DaoCompletService.TYPE.equals(x.getType()))) {
            pdfs = pdfs.stream().filter(x -> DaoCompletService.TYPE.equals(x.getType())).toList();
        }
        int n = 0;
        for (DocumentFicheMarche x : pdfs) {
            n += joindre(d, "CAHIER_CHARGES", x.getNomFichier(), x.getContenu());
        }
        return n;
    }

    /**
     * Le projet de marché (Q1 : produit par le serveur) : les parties, l'objet, les pièces constitutives (le DAO, l'offre retenue,
     * le CCAG), le montant hors taxes (prix corrigé − rabais) et en lettres, le délai de l'acte d'engagement, les signatures.
     */
    private DocumentLibre projet(Long idDmc, Integer lot, EvaluationDto.Proposition p, Offre offre, Map<String, String> plan, boolean allotie) {
        FicheMarcheService.EtatVersion v = fiches.etatValide(idDmc).orElse(null);
        String numero = v == null || v.etat().getValeurs() == null ? null : v.etat().getValeurs().get("B02-OB-03");
        String objet = v == null ? null : v.etat().getDesignationMarche();
        EntrepriseCandidatDto.Entreprise e = null;
        try {
            e = entreprises.lire(offre.getIdCandidat());
        } catch (RuntimeException ignore) {
            e = null;
        }
        List<DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "PROJET DE MARCHÉ"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, (objet == null ? "" : objet) + (allotie ? " — lot " + lot : "")));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        para(el, "Entre :");
        para(el, Objects.toString(plan.get("ENTITE"), "l'Autorité contractante") + (plan.get("MINISTERE") == null ? "" : " (" + plan.get("MINISTERE") + ")")
                + ", représentée par sa Personne responsable des marchés publics" + (plan.get("PRMP") == null ? "" : ", " + plan.get("PRMP"))
                + ", ci-après « l'Autorité contractante »,");
        para(el, "et :");
        para(el, offre.getRaisonSociale() + ", NIF " + offre.getNif() + (e == null || e.adresse() == null ? "" : ", " + e.adresse())
                + (e == null || e.representant() == null ? "" : ", représentée par " + e.representant().prenom() + " " + e.representant().nom()
                        + (e.representant().fonction() == null ? "" : ", " + e.representant().fonction()))
                + ", ci-après « le Titulaire ».");
        sous(el, "Article 1 — Objet");
        para(el, "Le présent marché a pour objet : " + Objects.toString(objet, "—") + (allotie ? ", lot " + lot : "") + ", issu de l'appel d'offres"
                + (numero == null ? "" : " n° " + numero) + ".");
        sous(el, "Article 2 — Pièces constitutives");
        para(el, "Le marché est constitué, par ordre de priorité : l'acte d'engagement du Titulaire ; le cahier des clauses administratives "
                + "particulières (ou le cahier des prescriptions spéciales) et ses annexes, les spécifications techniques ; l'offre du Titulaire "
                + "(offre n° " + offre.getNumero() + "), dont le bordereau des prix ; le cahier des clauses administratives générales — tels "
                + "qu'ils figurent au dossier d'appel d'offres et à l'offre retenue, sans modification substantielle.");
        sous(el, "Article 3 — Montant");
        para(el, "Le montant du marché est fixé à " + (p.montant() == null ? "……" : FormulairesEnLigne.lisible(p.montant()) + " Ariary hors taxes ("
                + NombreEnLettres.cardinal(p.montant().longValue()) + " ariary)") + ", tel qu'il résulte de l'évaluation des offres (prix "
                + "corrigé, rabais déduit).");
        sous(el, "Article 4 — Délai d'exécution");
        para(el, "Le délai d'exécution est celui de l'acte d'engagement : " + Objects.toString(p.delai(), "……") + ".");
        sous(el, "Article 5 — Entrée en vigueur");
        para(el, "Le marché prend effet à sa notification au Titulaire, après son approbation et l'avis de l'organe de contrôle.");
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        para(el, "Pour le Titulaire : ……………………………………  (nom, qualité, date et signature)");
        para(el, "Pour l'Autorité contractante, la Personne responsable des marchés publics : ……………………………………  (date et signature)");
        return new DocumentLibre("PROJET_MARCHE", allotie ? lot : null, el, "Procédure " + idDmc + " — projet de marché" + (allotie ? ", lot " + lot : ""));
    }

    private static void para(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, t));
    }

    private static void sous(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, t));
    }

    // ------------------------------------------------------------------ ⚠️ tranche 2b : outils

    /** Le délai d'attente du lot, une fois les candidats informés : nul avant. */
    private AttributionDto.DelaiAttente delaiAttente(Attribution a) {
        return delai(a, LocalDate.now(horloge));
    }

    /** Le délai d'attente à une date donnée (⚠️ 2c : la signature le relit) ; nul avant l'information. */
    static AttributionDto.DelaiAttente delai(Attribution a, LocalDate aujourdhui) {
        if (a.getInformeLe() == null) {
            return null;
        }
        LocalDate debut = a.getInformeLe().toLocalDate();
        if (a.getDateAffichage() != null && a.getDateAffichage().isAfter(debut)) {
            debut = a.getDateAffichage();
        }
        // Jours francs : ni le jour du point de départ, ni celui de l'échéance ne comptent — la signature vient le lendemain du dixième.
        LocalDate fin = debut.plusDays(JOURS_FRANCS);
        LocalDate signable = fin.plusDays(1);
        return new AttributionDto.DelaiAttente(debut, fin, signable, JOURS_FRANCS, !aujourdhui.isBefore(signable));
    }

    private AttributionDto.Attributaire attributaire(Attribution a) {
        if (a.getAttribueLe() == null) {
            return null;
        }
        Offre o = offres.findById(a.getIdOffreAttribuee()).orElse(null);
        return new AttributionDto.Attributaire(a.getIdOffreAttribuee(), o == null ? null : o.getNumero(), o == null ? null : o.getRaisonSociale(),
                o == null ? null : o.getNif(), a.getMontant(), a.getMontantTtc(), a.getDelai(), a.getMotifAttribution(), a.getAttribueLe(),
                a.getAttribuePar());
    }

    private AttributionDto.Information information(Attribution a) {
        if (a.getInformeLe() == null) {
            return null;
        }
        List<AttributionDto.Lettre> l = lettres.findByIdDmcAndLotOrderByIdAsc(a.getIdDmc(), a.getLot()).stream().map(x -> {
            Offre o = offres.findById(x.getIdOffre()).orElse(null);
            return new AttributionDto.Lettre(x.getId(), x.getIdOffre(), o == null ? null : o.getNumero(), o == null ? null : o.getRaisonSociale(),
                    x.getType(), x.getMotif(), x.getEnvoyeeLe(), x.getLueLe());
        }).toList();
        return new AttributionDto.Information(a.getInformeLe(), a.getInformePar(), a.getSignataire(), a.getDateAffichage(), l);
    }

    private List<AttributionDto.Explication> explicationsDto(Long idDmc, Integer lot) {
        return explications.findByIdDmcAndLotOrderByDemandeeLeAscIdAsc(idDmc, lot).stream()
                .map(e -> explicationDto(e, offres.findById(e.getIdOffre()).orElse(null))).toList();
    }

    private static AttributionDto.Explication explicationDto(AttributionExplication e, Offre o) {
        return new AttributionDto.Explication(e.getId(), e.getIdOffre(), o == null ? null : o.getNumero(), o == null ? null : o.getRaisonSociale(),
                e.getQuestion(), e.getDemandeeLe(), e.getReponduLe() == null ? "EN_ATTENTE" : "REPONDUE", e.getReponse(), e.getReponseNom(),
                e.getReponseTaille(), e.getReponduLe());
    }

    private static final Map<String, String> ETAPES = Map.of("CONFORMITE", "l'examen préliminaire", "EVALUATION", "l'évaluation détaillée",
            "ANORMALES", "l'examen des offres anormalement basses ou hautes", "QUALIFICATION", "la post-qualification");

    /** Les motifs du rejet, tirés du rapport : l'étape qui a écarté l'offre, son motif et sa clause ; sinon, son rang. */
    static String motifRejet(EvaluationDto.OffreEvaluee o) {
        EvaluationDto.Ecartement e = o.ecartee();
        if (e != null) {
            return "Offre écartée à " + ETAPES.getOrDefault(e.etape(), "l'évaluation")
                    + (e.qualification() == null ? "" : " (" + e.qualification().toLowerCase(Locale.FRENCH).replace('_', ' ') + ")")
                    + (e.motif() == null ? "" : " : " + e.motif()) + (e.clause() == null ? "" : " (" + e.clause() + ")") + ".";
        }
        if (o.qualification() != null && EvaluationService.NON_QUALIFIE.equals(o.qualification().decision())) {
            return "Offre non qualifiée à la post-qualification" + (o.qualification().motif() == null ? "" : " : " + o.qualification().motif())
                    + (o.qualification().clause() == null ? "" : " (" + o.qualification().clause() + ")") + ".";
        }
        return "Offre conforme, classée au rang " + (o.rang() == null ? "—" : o.rang()) + " des offres évaluées : l'offre retenue est l'offre "
                + "évaluée la moins-disante reconnue qualifiée.";
    }

    /** Ce que toutes les lettres d'un lot reprennent : autorité contractante, PRMP, référence et objet de l'appel d'offres. */
    private record Entete(String autorite, String ministere, String numero, String objet, Integer lot, boolean allotie) {
        String surLot() {
            return allotie ? " (lot " + lot + ")" : "";
        }
    }

    private Entete entete(Long idDmc, FicheMarcheService.EtatVersion v, Integer lot, boolean allotie) {
        DossierMec dmc = dmcRepository.findById(idDmc).orElseThrow(() -> new ResourceNotFoundException("DMC introuvable : " + idDmc));
        Map<String, String> plan = valeursPpm.lire(dmc.getIdDetail()).valeurs();
        return new Entete(Objects.toString(plan.get("ENTITE"), "L'Autorité contractante"), plan.get("MINISTERE"),
                v == null || v.etat().getValeurs() == null ? null : v.etat().getValeurs().get("B02-OB-03"),
                v == null ? "—" : Objects.toString(v.etat().getDesignationMarche(), "—"), lot, allotie);
    }

    /**
     * Une lettre (Q7 : <strong>modèle provisoire</strong>, remplacé à l'arrivée des modèles officiels) : en-tête, destinataire, objet,
     * le résultat (attribution, ou rejet et motifs), l'attributaire et le montant, le délai d'attente et le droit de demander des
     * explications ; la signature électronique simple de la PRMP, horodatée (Q9).
     */
    private DocumentLibre lettre(Entete en, Attribution a, AttributionLettre x, Offre offre, Offre attributaire, AttributionDto.DelaiAttente d) {
        boolean retenue = AttributionLettre.ATTRIBUTION.equals(x.getType());
        String montant = a.getMontant() == null ? "……" : FormulairesEnLigne.lisible(a.getMontant()) + " Ariary hors taxes"
                + (a.getMontantTtc() == null ? "" : " (" + FormulairesEnLigne.lisible(a.getMontantTtc()) + " Ariary toutes taxes comprises, prix lu)");
        List<DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, en.ministere() == null ? en.autorite() : en.ministere() + " — " + en.autorite()));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "La Personne responsable des marchés publics"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        para(el, "Le " + x.getProduiteLe().format(JOUR));
        para(el, "À " + offre.getRaisonSociale() + ", NIF " + offre.getNif());
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, retenue ? "LETTRE D'ATTRIBUTION" : "LETTRE D'INFORMATION DU REJET DE L'OFFRE"));
        para(el, "Objet : appel d'offres" + (en.numero() == null ? "" : " n° " + en.numero()) + " — " + en.objet() + (en.allotie() ? ", lot " + en.lot() : "")
                + " ; votre offre n° " + offre.getNumero() + ".");
        if (retenue) {
            para(el, "J'ai l'honneur de vous informer que votre offre est retenue et que le marché vous est attribué, pour un montant de " + montant
                    + (a.getDelai() == null ? "" : ", délai d'exécution : " + a.getDelai()) + ", sur la proposition de la commission d'appel "
                    + "d'offres et après l'avis de l'organe de contrôle.");
            para(el, "Le marché ne peut être signé qu'à l'expiration d'un délai de " + JOURS_FRANCS + " jours francs à compter de la plus tardive "
                    + "de l'information des candidats et de l'affichage du résultat (le " + a.getDateAffichage().format(JOUR) + "), soit à partir du "
                    + d.signableLe().format(JOUR) + " (art. 78 de la loi n° 2016-055). Vous serez invité à le signer, après une éventuelle mise au point.");
            para(el, "Je vous rappelle que vous aurez à produire, dans les quinze jours de la notification de l'attribution, les pièces attestant "
                    + "de votre situation fiscale (de moins de six mois) et sociale (de moins de trois mois) régulière (art. 20).");
        } else {
            para(el, "J'ai le regret de vous informer que votre offre n'est pas retenue, pour le motif suivant : " + x.getMotif());
            para(el, "Le marché est attribué à " + attributaire.getRaisonSociale() + " (offre n° " + attributaire.getNumero() + "), pour un montant de "
                    + montant + (a.getDelai() == null ? "" : ", délai d'exécution : " + a.getDelai()) + ".");
            para(el, "Vous pouvez me demander par écrit, sur la plateforme, des explications sur les motifs du rejet de votre offre (art. 52-II). "
                    + "Le marché ne sera pas signé avant l'expiration d'un délai de " + JOURS_FRANCS + " jours francs à compter de la plus tardive "
                    + "de la présente information et de l'affichage du résultat au siège (le " + a.getDateAffichage().format(JOUR) + "), soit pas "
                    + "avant le " + d.signableLe().format(JOUR) + " (art. 78).");
        }
        para(el, "Veuillez agréer l'expression de ma considération distinguée.");
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        para(el, "La Personne responsable des marchés publics, " + a.getSignataire() + " — signé électroniquement sur la plateforme le "
                + a.getInformeLe().format(HORODATAGE) + ".");
        return new DocumentLibre(retenue ? "LETTRE_ATTRIBUTION" : "LETTRE_REJET", en.allotie() ? en.lot() : null, el,
                "Procédure " + a.getIdDmc() + (en.allotie() ? ", lot " + en.lot() : "") + " — " + (retenue ? "lettre d'attribution" : "lettre au candidat non retenu")
                        + " (modèle provisoire)");
    }

    private EvaluationDto.Lot lotEvalue(Long idDmc, Integer lot) {
        EvaluationDto ev = evaluation.vueSansGarde(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("L'évaluation de cette procédure n'est pas ouverte."));
        return ev.lots().stream().filter(x -> Objects.equals(x.lot(), lot)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable dans l'évaluation : " + lot + "."));
    }

    private AttributionLettre derniereLettre(String idOffre) {
        return lettres.findByIdOffreOrderByIdDesc(idOffre).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Le résultat ne vous est pas encore communiqué."));
    }

    /** L'accusé de lecture de la plateforme : la première consultation du résultat ou de la lettre par le candidat. */
    private void accuser(AttributionLettre x) {
        if (x.getLueLe() == null) {
            x.setLueLe(maintenant());
            lettres.save(x);
            Offre o = offres.findById(x.getIdOffre()).orElse(null);
            evaluation.tracerAttribution(x.getIdDmc(), "LETTRE_LUE", "Lot " + x.getLot() + ", offre n° " + (o == null ? "?" : o.getNumero())
                    + " : lettre lue par le candidat sur la plateforme");
        }
    }

    private static AttributionExplication avecFichier(AttributionExplication e) {
        if (e == null || e.getReponseContenu() == null) {
            throw new ResourceNotFoundException("Aucun fichier joint à cette réponse.");
        }
        return e;
    }

    private Offre sienne(String idCandidat, String idOffre) {
        Offre o = offres.findById(idOffre).orElseThrow(() -> new ResourceNotFoundException("Offre introuvable : " + idOffre + "."));
        if (!o.getIdCandidat().equals(idCandidat)) {
            throw new AccessDeniedException("Cette offre n'est pas la vôtre.");
        }
        return o;
    }

    /** Le nom de la PRMP connectée, signataire des lettres (« NOM Prénoms ») ; à défaut, son identifiant. */
    private String nomPrmp() {
        String ref = CurrentUser.ref().orElse(null);
        return ref == null ? acteur() : prmpRepository.findById(ref)
                .map(p -> (Objects.toString(p.getNomPrmp(), "") + " " + Objects.toString(p.getPrenomsPrmp(), "")).trim())
                .filter(s -> !s.isBlank()).orElse(ref);
    }

    /** La PRMP de la fiche, seule (ce qui engage est nominatif : ni l'UGPM, ni la CAO). */
    private void exigerPrmpSeule(Long idDmc, String message) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException(message);
        }
        fiches.controlerLecture(idDmc);
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

    /** La PRMP de la fiche, ou son UGPM (Q : la création d'un dossier leur est ouverte, comme le dossier DAO). */
    private void exigerPrmp(Long idDmc) {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p != ProfilUtilisateur.PRMP && p != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("Le dossier de marché se crée par la PRMP de la fiche (ou son UGPM).");
        }
        fiches.controlerLecture(idDmc);
    }
}
