package cnm.prs.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.SansSuiteDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Offre;
import cnm.prs.entity.PieceJointeDossier;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.Reception;
import cnm.prs.entity.SansSuite;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.repository.SansSuiteRepository;
import cnm.prs.repository.TypePieceJointeRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>La déclaration sans suite</strong> (art. 55), lot 2, tranche 2d-3 (demande front du 2026-10-07, §B6, Q10 ; V93 ; arbitrages
 * du pilote du 2026-10-08) :
 * <ul>
 *   <li>à tout moment avant la signature d'un marché de la procédure, la PRMP demande l'avis de l'organe de contrôle : ses motifs
 *   d'intérêt général deviennent la pièce {@code MOTIFS_SANS_SUITE} d'un <strong>dossier {@code DSS}</strong> (famille propre), qu'elle
 *   soumet au circuit de la Commission, inchangé (signature comprise) ;</li>
 *   <li>la Commission se prononce sous <strong>5 jours de la réception</strong> ; l'échéance est servie et alertée la veille (PRMP,
 *   membre examinateur) ; deux issues seulement, {@code FAV} ou {@code DEF} (le PV refuse les autres avis) ;</li>
 *   <li>avis <strong>défavorable</strong> : la procédure reprend son cours ; avis <strong>favorable</strong> : la PRMP déclare le sans
 *   suite par sa décision — procédure close sans suite, chaque candidat notifié avec les motifs, page publique.</li>
 * </ul>
 * Tant qu'une demande attend son avis, aucun marché ne se signe ; une fois le sans suite déclaré, plus aucun geste d'attribution.
 */
@Service
@Transactional
public class SansSuiteService {

    public static final String SOUS_TYPE = "DSS";
    public static final String PIECE_MOTIFS = "MOTIFS_SANS_SUITE";
    public static final int JOURS_AVIS = 5;
    static final Set<String> AVIS_PERMIS = Set.of("FAV", "DEF");
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    private final SansSuiteRepository demandes;
    private final AttributionRepository attributions;
    private final EvaluationService evaluation;
    private final FicheMarcheService fiches;
    private final SaisieService saisie;
    private final ValeursPpmService valeursPpm;
    private final DossierMecRepository dmcRepository;
    private final DossierRepository dossierRepository;
    private final PieceJointeDossierRepository pieces;
    private final TypePieceJointeRepository typesPiece;
    private final PvExamenRepository pvRepository;
    private final ReceptionRepository receptions;
    private final DispatchRepository dispatches;
    private final OffreRepository offres;
    private final CompteCandidatRepository candidats;
    private final NotificationService notifications;
    private final CeremonieService ceremonies;
    private final GenerateurDocumentsFiche generateur;
    private final Clock horloge;

    public SansSuiteService(SansSuiteRepository demandes, AttributionRepository attributions, EvaluationService evaluation, FicheMarcheService fiches,
            SaisieService saisie, ValeursPpmService valeursPpm, DossierMecRepository dmcRepository, DossierRepository dossierRepository,
            PieceJointeDossierRepository pieces, TypePieceJointeRepository typesPiece, PvExamenRepository pvRepository, ReceptionRepository receptions,
            DispatchRepository dispatches, OffreRepository offres, CompteCandidatRepository candidats, NotificationService notifications,
            CeremonieService ceremonies, GenerateurDocumentsFiche generateur, Clock horloge) {
        this.demandes = demandes;
        this.attributions = attributions;
        this.evaluation = evaluation;
        this.fiches = fiches;
        this.saisie = saisie;
        this.valeursPpm = valeursPpm;
        this.dmcRepository = dmcRepository;
        this.dossierRepository = dossierRepository;
        this.pieces = pieces;
        this.typesPiece = typesPiece;
        this.pvRepository = pvRepository;
        this.receptions = receptions;
        this.dispatches = dispatches;
        this.offres = offres;
        this.candidats = candidats;
        this.notifications = notifications;
        this.ceremonies = ceremonies;
        this.generateur = generateur;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ lecture

    /** La déclaration sans suite de la procédure : PRMP, UGPM (et les lecteurs de la fiche). */
    @Transactional(readOnly = true)
    public SansSuiteDto lire(Long idDmc) {
        fiches.controlerLecture(idDmc);
        return dto(idDmc);
    }

    @Transactional(readOnly = true)
    public byte[] motifs(Long idDmc, Long id, boolean docx) {
        fiches.controlerLecture(idDmc);
        SansSuite s = demandes.findById(id).filter(x -> x.getIdDmc().equals(idDmc))
                .orElseThrow(() -> new ResourceNotFoundException("Demande de sans suite introuvable : " + id + "."));
        byte[] b = docx ? s.getMotifsDocx() : s.getMotifsPdf();
        if (b == null) {
            throw new ResourceNotFoundException("Les motifs ne sont pas produits.");
        }
        return b;
    }

    // ------------------------------------------------------------------ la demande

    /**
     * La PRMP demande l'avis de l'organe de contrôle : 400 {@code MOTIFS_OBLIGATOIRES} ; 403 ; 409 {@code MARCHE_SIGNE},
     * {@code SANS_SUITE_EN_COURS}, {@code SANS_SUITE_DECLAREE}, {@code SOUS_TYPE_ABSENT}. Le dossier {@code DSS} est créé en brouillon,
     * la pièce des motifs y est jointe ; la PRMP le soumet comme tout dossier.
     */
    public SansSuiteDto demander(Long idDmc, SansSuiteDto.DemandeRequest r) {
        exigerPrmpSeule(idDmc);
        exigerAucuneDeclaration(idDmc);
        if (enCours(idDmc) != null) {
            throw new BusinessRuleException("Une demande de sans suite attend l'avis de la Commission.", "SANS_SUITE_EN_COURS");
        }
        exigerAucunMarcheSigne(idDmc);
        String motifs = r == null || r.motifs() == null || r.motifs().isBlank() ? null : r.motifs().trim();
        if (motifs == null) {
            throw new BadRequestException("La déclaration sans suite exige ses motifs d'intérêt général.", "MOTIFS_OBLIGATOIRES");
        }
        DossierMec dmc = dmcRepository.findById(idDmc).orElseThrow(() -> new ResourceNotFoundException("DMC introuvable : " + idDmc));
        ValeursPpmService.EnTete enTete = valeursPpm.enTete(dmc.getIdDetail());
        Dossier dossier = saisie.creerDossierMarche(SOUS_TYPE, enTete.idLocalite(), enTete.idEntiteContract());
        SansSuite s = new SansSuite();
        s.setIdDmc(idDmc);
        s.setMotifs(motifs);
        s.setDemandeLe(maintenant());
        s.setDemandePar(acteur());
        s.setIdDossier(dossier.getIdDossier());
        for (GenerateurDocumentsFiche.Fichier f : generateur.generer(document(idDmc, s))) {
            if ("pdf".equals(f.extension())) {
                s.setMotifsPdf(f.contenu());
            } else if ("docx".equals(f.extension())) {
                s.setMotifsDocx(f.contenu());
            }
        }
        demandes.save(s);
        joindre(dossier, s.getMotifsPdf());
        evaluation.tracerAttribution(idDmc, "SANS_SUITE_DEMANDE", "Demande de sans suite (dossier n° " + dossier.getIdDossier() + ") : " + motifs);
        return dto(idDmc);
    }

    private DocumentLibre document(Long idDmc, SansSuite s) {
        FicheMarcheService.EtatVersion v = fiches.etatValide(idDmc).orElse(null);
        List<DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "MOTIFS DE LA DÉCLARATION SANS SUITE"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "Procédure " + idDmc + (v == null ? "" : " — "
                + Objects.toString(v.etat().getDesignationMarche(), "")) + "."));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "La Personne Responsable des Marchés Publics envisage de déclarer la "
                + "procédure sans suite pour un motif d'intérêt général (art. 55 de la loi n° 2016-055) et sollicite l'avis de l'organe de "
                + "contrôle, qui se prononce dans les cinq jours de la réception du présent dossier."));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Motifs"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, s.getMotifs()));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "Établi le " + s.getDemandeLe().format(HORODATAGE) + "."));
        return new DocumentLibre("MOTIFS_SANS_SUITE", null, el, "Procédure " + idDmc + " — motifs de la déclaration sans suite");
    }

    private void joindre(Dossier d, byte[] contenu) {
        TypePieceJointe type = typesPiece.findFirstByCode(PIECE_MOTIFS).orElse(null);
        if (type == null || contenu == null) {
            return;
        }
        PieceJointeDossier p = new PieceJointeDossier();
        p.setIdDossier(d.getIdDossier());
        p.setIdTypePiece(type.getIdTypePiece());
        p.setNomFichier("motifs-sans-suite.pdf");
        p.setContenu(contenu);
        p.setFormat("PDF");
        p.setTaille((long) contenu.length);
        p.setDateUpload(LocalDateTime.now());
        p.setApresLettreRenvoi(false);
        pieces.save(p);
    }

    // ------------------------------------------------------------------ la déclaration

    /**
     * Après l'avis favorable de la Commission, la PRMP déclare le sans suite : 400 {@code DECISION_OBLIGATOIRE},
     * {@code DECISION_DATE_INVALIDE} ; 403 ; 409 {@code AUCUNE_DEMANDE}, {@code AVIS_NON_FAVORABLE}, {@code SANS_SUITE_DECLAREE},
     * {@code MARCHE_SIGNE}. Chaque candidat ayant déposé reçoit {@code PROCEDURE_SANS_SUITE} avec les motifs ; la page publique l'affiche.
     */
    public SansSuiteDto declarer(Long idDmc, SansSuiteDto.DeclarationRequest r) {
        exigerPrmpSeule(idDmc);
        exigerAucuneDeclaration(idDmc);
        List<SansSuite> toutes = demandes.findByIdDmcOrderByIdAsc(idDmc);
        SansSuite s = toutes.isEmpty() ? null : toutes.get(toutes.size() - 1);
        if (s == null) {
            throw new BusinessRuleException("Aucune demande de sans suite n'a été soumise à la Commission.", "AUCUNE_DEMANDE");
        }
        if (!"FAV".equals(avis(s))) {
            throw new BusinessRuleException("Le sans suite se déclare après l'avis favorable de la Commission.", "AVIS_NON_FAVORABLE");
        }
        exigerAucunMarcheSigne(idDmc);
        String reference = r == null || r.decision() == null || r.decision().reference() == null || r.decision().reference().isBlank() ? null
                : r.decision().reference().trim();
        if (reference == null || r.decision().date() == null) {
            throw new BadRequestException("La décision de la PRMP (référence et date) est attendue.", "DECISION_OBLIGATOIRE");
        }
        if (r.decision().date().isAfter(maintenant().toLocalDate())) {
            throw new BadRequestException("La date de la décision ne peut pas être à venir.", "DECISION_DATE_INVALIDE");
        }
        s.setAvis("FAV");
        s.setAvisConstateLe(s.getAvisConstateLe() == null ? maintenant() : s.getAvisConstateLe());
        s.setDecisionReference(reference);
        s.setDecisionDate(r.decision().date());
        s.setDeclareLe(maintenant());
        s.setDeclarePar(acteur());
        demandes.save(s);
        Set<String> avertis = new HashSet<>();
        String objet = fiches.etatValide(idDmc).map(v -> Objects.toString(v.etat().getDesignationMarche(), "")).orElse("");
        for (Offre o : offres.findByIdDmcOrderByNumeroAscDateCreationAsc(idDmc)) {
            if (o.getDateDepot() == null || Offre.RETIREE.equals(o.getEtat()) || !avertis.add(o.getIdCandidat())) {
                continue;
            }
            CompteCandidat c = candidats.findById(o.getIdCandidat()).orElse(null);
            notifications.emettreCandidat(TypeNotification.PROCEDURE_SANS_SUITE, o.getIdCandidat(), c == null ? null : c.getEmail(),
                    idDmc.intValue(), TypeObjet.PROCEDURE, "Procédure déclarée sans suite", "La procédure « " + objet + " » est déclarée sans "
                            + "suite par décision n° " + reference + " du " + r.decision().date().format(JOUR) + ", pour un motif d'intérêt "
                            + "général : " + s.getMotifs() + " Aucune indemnité n'est due (art. 55).");
        }
        evaluation.tracerAttribution(idDmc, "SANS_SUITE", "Procédure déclarée sans suite (décision n° " + reference + " du "
                + r.decision().date().format(JOUR) + ") ; " + avertis.size() + " candidat(s) notifié(s)");
        return dto(idDmc);
    }

    // ------------------------------------------------------------------ les gardes, pour l'attribution

    /** 409 {@code SANS_SUITE_DECLAREE} : la procédure est close sans suite, plus aucun geste d'attribution. */
    @Transactional(readOnly = true)
    public void exigerAucuneDeclaration(Long idDmc) {
        if (demandes.findByIdDmcOrderByIdAsc(idDmc).stream().anyMatch(x -> x.getDeclareLe() != null)) {
            throw new BusinessRuleException("La procédure est déclarée sans suite.", "SANS_SUITE_DECLAREE");
        }
    }

    /** 409 {@code SANS_SUITE_EN_COURS} : une demande attend l'avis de la Commission — la signature attend (le sans suite la précède). */
    @Transactional(readOnly = true)
    public void exigerAucuneDemandeEnCours(Long idDmc) {
        exigerAucuneDeclaration(idDmc);
        if (enCours(idDmc) != null) {
            throw new BusinessRuleException("Une demande de sans suite attend l'avis de la Commission : le marché ne se signe pas.",
                    "SANS_SUITE_EN_COURS");
        }
    }

    /** La demande qui attend encore l'avis de la Commission, ou nulle. */
    private SansSuite enCours(Long idDmc) {
        return demandes.findByIdDmcOrderByIdAsc(idDmc).stream().filter(x -> x.getDeclareLe() == null && avis(x) == null).findFirst().orElse(null);
    }

    /** Vrai si la procédure est déclarée sans suite. */
    @Transactional(readOnly = true)
    public boolean declaree(Long idDmc) {
        return demandes.findByIdDmcOrderByIdAsc(idDmc).stream().anyMatch(x -> x.getDeclareLe() != null);
    }

    /** La dernière déclaration, pour la page publique. */
    @Transactional(readOnly = true)
    public SansSuite declaration(Long idDmc) {
        return demandes.findByIdDmcOrderByIdAsc(idDmc).stream().filter(x -> x.getDeclareLe() != null).reduce((a, b) -> b).orElse(null);
    }

    // ------------------------------------------------------------------ le suivi (planificateur)

    /**
     * Le suivi des demandes (planificateur) : l'avis rendu est constaté et la PRMP avertie ({@code SANS_SUITE_AVIS}) ; la veille de
     * l'échéance des 5 jours, la PRMP et le membre examinateur sont alertés ({@code ECHEANCE_SANS_SUITE}), une fois. Le nombre de messages.
     */
    public int suivre() {
        int n = 0;
        LocalDate aujourdhui = maintenant().toLocalDate();
        for (SansSuite s : demandes.findByAvisIsNullAndIdDossierIsNotNull()) {
            String avis = avisRendu(s);
            if (avis != null) {
                s.setAvis(avis);
                s.setAvisConstateLe(maintenant());
                demandes.save(s);
                ceremonies.notifierPrmp(s.getIdDmc(), TypeNotification.SANS_SUITE_AVIS, "Avis sur le sans suite", "La Commission a rendu un avis "
                        + ("FAV".equals(avis) ? "favorable sur la déclaration sans suite de la procédure " + s.getIdDmc() + " : déclarez-la par votre "
                                + "décision." : "défavorable sur la déclaration sans suite de la procédure " + s.getIdDmc() + " : la procédure reprend son "
                                        + "cours."));
                n++;
                continue;
            }
            Reception rec = reception(s);
            if (rec == null || s.getAlerteLe() != null) {
                continue;
            }
            LocalDate echeance = rec.getDateReception().toLocalDate().plusDays(JOURS_AVIS);
            if (aujourdhui.isBefore(echeance.minusDays(1))) {
                continue;
            }
            String corps = "La demande de déclaration sans suite de la procédure " + s.getIdDmc() + " (dossier n° " + s.getIdDossier() + ") attend "
                    + "l'avis de la Commission au plus tard le " + echeance.format(JOUR) + " (art. 55-II).";
            ceremonies.notifierPrmp(s.getIdDmc(), TypeNotification.ECHEANCE_SANS_SUITE, "Échéance de l'avis sur le sans suite", corps);
            dispatches.findFirstByIdReceptionOrderByIdDispatchDesc(rec.getIdReception()).map(d -> d.getImCtrlMembre() != null ? d.getImCtrlMembre()
                    : d.getImCtrlCc()).ifPresent(im -> notifications.emettreControleur(TypeNotification.ECHEANCE_SANS_SUITE, im, null,
                            s.getIdDossier(), TypeObjet.DOSSIER, s.getIdDossier(), "Échéance de l'avis sur le sans suite", corps));
            s.setAlerteLe(maintenant());
            demandes.save(s);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ outils

    private SansSuiteDto dto(Long idDmc) {
        List<SansSuiteDto.Demande> liste = demandes.findByIdDmcOrderByIdAsc(idDmc).stream().map(this::demandeDto).toList();
        return new SansSuiteDto(idDmc, liste.stream().anyMatch(d -> d.declareLe() != null), liste.isEmpty() ? null : liste.get(liste.size() - 1),
                liste);
    }

    private SansSuiteDto.Demande demandeDto(SansSuite s) {
        Dossier d = s.getIdDossier() == null ? null : dossierRepository.findById(s.getIdDossier()).orElse(null);
        Reception rec = reception(s);
        LocalDate echeance = rec == null || rec.getDateReception() == null ? null : rec.getDateReception().toLocalDate().plusDays(JOURS_AVIS);
        String avis = avis(s);
        String etat = s.getDeclareLe() != null ? "DECLAREE" : "FAV".equals(avis) ? "FAVORABLE" : "DEF".equals(avis) ? "DEFAVORABLE"
                : d != null && "BROUILLON".equals(d.getStatut()) ? "A_SOUMETTRE" : "AU_CONTROLE";
        return new SansSuiteDto.Demande(s.getId(), s.getMotifs(), s.getDemandeLe(), s.getDemandePar(), s.getIdDossier(), d == null ? null : d.getStatut(),
                rec == null ? null : rec.getDateReception(), echeance, avis == null && echeance != null && maintenant().toLocalDate().isAfter(echeance),
                avis, etat, s.getDecisionReference(), s.getDecisionDate(), s.getDeclareLe(), s.getMotifsPdf() != null);
    }

    /** L'avis constaté, à défaut celui du PV signé du dossier. */
    private String avis(SansSuite s) {
        return s.getAvis() != null ? s.getAvis() : avisRendu(s);
    }

    private String avisRendu(SansSuite s) {
        return s.getIdDossier() == null ? null
                : pvRepository.findSignesParDossierRows(s.getIdDossier()).stream().findFirst().map(PvExamen::getIdAvis).orElse(null);
    }

    private Reception reception(SansSuite s) {
        return s.getIdDossier() == null ? null : receptions.findByIdDossier(s.getIdDossier()).stream().filter(x -> x.getDateReception() != null)
                .reduce((a, b) -> b).orElse(null);
    }

    private void exigerAucunMarcheSigne(Long idDmc) {
        List<Integer> signes = attributions.findByIdDmcOrderByLotAsc(idDmc).stream().filter(a -> a.getSigneLe() != null).map(Attribution::getLot)
                .toList();
        if (!signes.isEmpty()) {
            throw new BusinessRuleException("Un marché de la procédure est signé : le sans suite n'est plus possible (art. 55).", "MARCHE_SIGNE");
        }
    }

    private void exigerPrmpSeule(Long idDmc) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException("La déclaration sans suite relève de la PRMP de la fiche (art. 55).");
        }
        fiches.controlerLecture(idDmc);
    }

    private static String acteur() {
        return CurrentUser.ref().or(CurrentUser::login).orElse(null);
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).withNano(0);
    }
}
