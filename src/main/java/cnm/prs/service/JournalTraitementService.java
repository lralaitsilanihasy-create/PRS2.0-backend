package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ActionDossierDto;
import cnm.prs.entity.ActionDossier;
import cnm.prs.entity.DemandeRetrait;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.PvNavette;
import cnm.prs.entity.TransmissionSigmp;
import cnm.prs.entity.Verification;
import cnm.prs.enums.SensNavette;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutPv;
import cnm.prs.enums.StatutRetrait;
import cnm.prs.repository.ActionDossierRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DemandeRetraitRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.PvNavetteRepository;
import cnm.prs.repository.TransmissionSigmpRepository;
import cnm.prs.repository.VerificationRepository;

/**
 * ⚠️ <strong>Les événements de TRAITEMENT du journal du dossier</strong> (règle du pilote, 2026-09-04 —
 * « le journal s'arrête à la réattribution alors que le chronométrage va jusqu'à la co-signature »).
 *
 * <p>Le journal consignait les actes de la PRMP et les gestes de dispatch, puis plus rien : la suite du
 * traitement — examen soumis, retours de navette, transmission au Président, visa, signatures,
 * vérification, SIGMP, archivage — n'y figurait nulle part, alors que le chronométrage la racontait.</p>
 *
 * <p><strong>DÉRIVÉ À LA LECTURE, jamais écrit.</strong> Ces événements sont reconstruits depuis les
 * données qui les portent déjà : {@code t_pv_navette}, les dates du PV, les passages de vérification,
 * les transmissions SIGMP. Deux raisons, et la seconde est décisive :</p>
 * <ul>
 *   <li>les dossiers <strong>déjà traités</strong> deviennent complets d'office — écrire au fil de
 *       l'eau n'aurait raconté que les dossiers à venir, et le constat portait justement sur un
 *       dossier ancien ;</li>
 *   <li>aucune écriture, donc aucune transaction métier alourdie et aucun risque de divergence entre
 *       la trace et la donnée : ici, la donnée EST la trace.</li>
 * </ul>
 *
 * <p><strong>Sur la précision des instants.</strong> Les navettes portent un horodatage complet ; les
 * signatures, la vérification et l'archivage ne portent qu'une <em>date</em>. Ces derniers sont donc
 * placés en <strong>fin de journée</strong> et ordonnés entre eux par le rang du circuit. C'est fidèle :
 * ce sont tous des événements de fin de parcours, qui ne bouclent pas et dont l'ordre est fixe — une
 * signature ne précède jamais son visa. Les placer en début de journée les aurait fait passer avant les
 * navettes du même jour, ce qui aurait été faux.</p>
 */
@Service
public class JournalTraitementService {

    public static final String SOUMISSION_EXAMEN = "SOUMISSION_EXAMEN";
    public static final String RETOUR_RECTIFICATION = "RETOUR_RECTIFICATION";
    public static final String TRANSMISSION_PRESIDENT = "TRANSMISSION_PRESIDENT";
    public static final String VISA = "VISA";
    public static final String SIGNATURE = "SIGNATURE";
    public static final String PV_SIGNE = "PV_SIGNE";
    public static final String DECISION_VERIFICATION = "DECISION_VERIFICATION";
    public static final String TRANSMISSION_SIGMP = "TRANSMISSION_SIGMP";
    public static final String ARCHIVAGE = "ARCHIVAGE";
    /**
     * ⚠️ Recensement des trous (2026-09-07, T1, dossier 100299) — le RETRAIT, geste le plus lourd de la
     * séquence puisqu'il fait reculer le dossier, n'apparaissait nulle part : dérivé de
     * {@code t_demande_retrait}, rétroactif comme le reste. La demande est un acte de la PRMP (opérateur
     * = la PRMP, donc pas de marqueur « opérateur ≠ attributaire ») ; la décision, celle du CC ou du
     * Président. Visibles de tous (comme CREATION / SOUMISSION).
     */
    public static final String DEMANDE_RETRAIT = "DEMANDE_RETRAIT";
    public static final String RETRAIT_ACCEPTE = "RETRAIT_ACCEPTE";
    public static final String RETRAIT_REFUSE = "RETRAIT_REFUSE";

    private final PvNavetteRepository navetteRepository;
    private final PvExamenRepository pvExamenRepository;
    private final VerificationRepository verificationRepository;
    private final TransmissionSigmpRepository transmissionRepository;
    private final ControleurRepository controleurRepository;
    private final DemandeRetraitRepository demandeRetraitRepository;
    private final PrmpRepository prmpRepository;
    private final ActionDossierRepository actionDossierRepository;

    public JournalTraitementService(PvNavetteRepository navetteRepository,
            PvExamenRepository pvExamenRepository, VerificationRepository verificationRepository,
            TransmissionSigmpRepository transmissionRepository, ControleurRepository controleurRepository,
            DemandeRetraitRepository demandeRetraitRepository, PrmpRepository prmpRepository,
            ActionDossierRepository actionDossierRepository) {
        this.navetteRepository = navetteRepository;
        this.pvExamenRepository = pvExamenRepository;
        this.verificationRepository = verificationRepository;
        this.transmissionRepository = transmissionRepository;
        this.controleurRepository = controleurRepository;
        this.demandeRetraitRepository = demandeRetraitRepository;
        this.prmpRepository = prmpRepository;
        this.actionDossierRepository = actionDossierRepository;
    }

    /**
     * Rang du type dans le circuit — départage deux événements du <strong>même instant</strong> (le cas
     * ordinaire en fin de parcours, où plusieurs actes partagent une date sans heure).
     *
     * <p>Les types <em>stockés</em> y figurent aussi : le journal servi mêle les deux familles, et deux
     * échelles de rang se contrediraient au premier acte simultané.</p>
     */
    public static int rang(String typeAction) {
        return switch (typeAction == null ? "" : typeAction) {
            case JournalDossierService.CREATION -> 10;
            case JournalDossierService.MISE_A_JOUR -> 15;
            case JournalDossierService.SOUMISSION, JournalDossierService.RESOUMISSION -> 20;
            case DEMANDE_RETRAIT -> 22;
            case RETRAIT_ACCEPTE, RETRAIT_REFUSE -> 23;
            case JournalDossierService.TRANSMISSION_COMPLEMENTS,
                 JournalDossierService.TRANSMISSION_COMPLEMENTS_DEPOT -> 25;
            case JournalDossierService.RECEPTION -> 30;
            case JournalDossierService.DISPATCH -> 40;
            case JournalDossierService.REATTRIBUTION, JournalDossierService.REPRISE -> 45;
            case JournalDossierService.RETRAIT_DISPATCH -> 47;
            case SOUMISSION_EXAMEN -> 50;
            case RETOUR_RECTIFICATION -> 55;
            case TRANSMISSION_PRESIDENT -> 60;
            case VISA -> 65;
            case SIGNATURE -> 70;
            case PV_SIGNE -> 75;
            case DECISION_VERIFICATION -> 80;
            case TRANSMISSION_SIGMP -> 85;
            case ARCHIVAGE -> 90;
            default -> 100;
        };
    }

    /** Événements de traitement d'un dossier, reconstruits depuis les données ; jamais {@code null}. */
    @Transactional(readOnly = true)
    public List<ActionDossierDto> evenements(Integer idDossier) {
        if (idDossier == null) {
            return List.of();
        }
        List<ActionDossierDto> evenements = new ArrayList<>();
        for (PvNavette n : navetteRepository.findParDossier(idDossier)) {
            ajouterNavette(evenements, idDossier, n);
        }
        for (PvExamen pv : pvExamenRepository.findTousParDossier(idDossier)) {
            ajouterSignatures(evenements, idDossier, pv);
        }
        for (Verification v : verificationRepository.findPassagesDuDossier(idDossier)) {
            ajouterVerification(evenements, idDossier, v);
        }
        for (TransmissionSigmp t : transmissionRepository.findByIdDossier(idDossier)) {
            evenements.add(evenement(idDossier, finDeJournee(t.getDateTransmission().toLocalDate()),
                    TRANSMISSION_SIGMP, t.getImVerificateur(),
                    Boolean.TRUE.equals(t.getLeveeObservations())
                            ? "décision transmise à SIGMP — observations levées"
                            : "décision transmise à SIGMP"));
        }
        for (DemandeRetrait dr : demandeRetraitRepository.findByIdDossierOrderByDateDemandeAsc(idDossier)) {
            ajouterRetrait(evenements, idDossier, dr);
        }
        return evenements;
    }

    /**
     * ⚠️ T1 (2026-09-07) — la demande, puis la décision si elle est prise. Le statut d'AVANT le retrait
     * n'est pas stocké sur la demande : il se relit dans le journal (dernière action de circuit avant la
     * décision), grâce aux copies figées à la purge. À défaut, « retour en BROUILLON » sans état de départ.
     */
    private void ajouterRetrait(List<ActionDossierDto> cible, Integer idDossier, DemandeRetrait dr) {
        String motif = dr.getMotifRetrait() == null || dr.getMotifRetrait().isBlank()
                ? "" : " — motif : " + dr.getMotifRetrait().trim();
        ActionDossierDto demande = evenement(idDossier, dr.getDateDemande(), DEMANDE_RETRAIT, null,
                "Demande de retrait" + motif);
        demande.setIdPrmpOperateur(dr.getIdPrmp());
        demande.setNomOperateur(nomPrmp(dr.getIdPrmp()));
        demande.setAuteur(dr.getIdPrmp());
        cible.add(demande);

        if (dr.getDateDecision() == null) {
            return;
        }
        if (StatutRetrait.ACCEPTEE.name().equals(dr.getStatut())) {
            String avant = statutAvantRetrait(idDossier, dr.getDateDecision());
            cible.add(evenement(idDossier, dr.getDateDecision(), RETRAIT_ACCEPTE, dr.getImCtrlCc(),
                    "Retrait accepté — " + (avant == null ? "retour en " : avant + " -> ")
                            + StatutDossier.BROUILLON.name()));
        } else if (StatutRetrait.REFUSEE.name().equals(dr.getStatut())) {
            String obs = dr.getObsDecision() == null || dr.getObsDecision().isBlank()
                    ? "" : " — " + dr.getObsDecision().trim();
            cible.add(evenement(idDossier, dr.getDateDecision(), RETRAIT_REFUSE, dr.getImCtrlCc(),
                    "Retrait refusé" + obs));
        }
    }

    /** Statut du dossier juste avant une décision de retrait, relu dans les actions consignées. */
    private String statutAvantRetrait(Integer idDossier, LocalDateTime decision) {
        String statut = null;
        for (ActionDossier a : actionDossierRepository.findByIdDossierOrderByDateActionAscIdActionAsc(idDossier)) {
            if (a.getDateAction() == null || a.getDateAction().isAfter(decision)) {
                continue;
            }
            switch (a.getTypeAction() == null ? "" : a.getTypeAction()) {
                case JournalDossierService.SOUMISSION, JournalDossierService.RESOUMISSION,
                     JournalDossierService.TRANSMISSION_COMPLEMENTS_DEPOT -> statut = StatutDossier.SOUMIS.name();
                case JournalDossierService.RECEPTION, JournalDossierService.RETRAIT_DISPATCH ->
                        statut = StatutDossier.PRET_DISPATCH.name();
                case JournalDossierService.DISPATCH, JournalDossierService.REATTRIBUTION,
                     JournalDossierService.REPRISE -> statut = StatutDossier.DISPATCHE.name();
                case SOUMISSION_EXAMEN -> statut = StatutDossier.EXAMINE.name();
                case RETRAIT_ACCEPTE -> statut = null;   // un retrait antérieur : le décor repart de zéro
                default -> { }
            }
        }
        return statut;
    }

    /** « NOM Prénoms » d'une PRMP (convention canonique unique) ; repli sur l'identifiant. */
    private String nomPrmp(String idPrmp) {
        if (idPrmp == null || idPrmp.isBlank()) {
            return null;
        }
        return prmpRepository.findById(idPrmp).map(p -> {
            String complet = ActeurDirectory.nomCanonique(p.getNomPrmp(), p.getPrenomsPrmp());
            return complet.isBlank() ? idPrmp : complet;
        }).orElse(idPrmp);
    }

    /** Un mouvement de navette : c'est lui qui porte l'instant précis, l'acteur et le commentaire. */
    private void ajouterNavette(List<ActionDossierDto> cible, Integer idDossier, PvNavette n) {
        String sens = n.getSens();
        String commentaire = n.getCommentaire() == null || n.getCommentaire().isBlank()
                ? "" : " — « " + n.getCommentaire().trim() + " »";
        if (SensNavette.SOUMISSION.name().equals(sens)) {
            cible.add(evenement(idDossier, n.getDateAction(), SOUMISSION_EXAMEN, n.getImActeur(),
                    "projet de PV soumis (navette n° " + n.getNumNavette() + ")" + commentaire));
        } else if (SensNavette.RETOUR_RECTIF.name().equals(sens)) {
            cible.add(evenement(idDossier, n.getDateAction(), RETOUR_RECTIFICATION, n.getImActeur(),
                    "projet retourné au Membre pour rectification" + commentaire));
        } else if (SensNavette.RETOUR_CC.name().equals(sens)) {
            // ⚠️ Le retour du Président AU CC est un retour lui aussi : lui donner un type à part
            // obligerait le front à en connaître un de plus pour dire la même chose. Le destinataire
            // est dans le détail, où il se lit.
            cible.add(evenement(idDossier, n.getDateAction(), RETOUR_RECTIFICATION, n.getImActeur(),
                    "projet retourné au Chef de commission" + commentaire));
        } else if (SensNavette.TRANSMISSION_PRESIDENT.name().equals(sens)) {
            cible.add(evenement(idDossier, n.getDateAction(), TRANSMISSION_PRESIDENT, n.getImActeur(),
                    "projet accepté et transmis au Président" + commentaire));
        } else if (SensNavette.ACCEPTATION.name().equals(sens)) {
            // L'ACCEPTATION est la trace laissée par le VISA : c'est le même geste, vu de la navette.
            cible.add(evenement(idDossier, n.getDateAction(), VISA, n.getImActeur(),
                    detailDuVisa(idDossier, n.getIdPv()) + commentaire));
        }
    }

    /** « avis arrêté ; co-signataires désignés ; par intérim le cas échéant » — le contenu du visa. */
    private String detailDuVisa(Integer idDossier, Integer idPv) {
        PvExamen pv = pvExamenRepository.findById(idPv).orElse(null);
        if (pv == null) {
            return "projet de PV visé";
        }
        StringBuilder detail = new StringBuilder("projet de PV visé");
        if (pv.getIdAvis() != null && !pv.getIdAvis().isBlank()) {
            detail.append(" — avis ").append(pv.getIdAvis());
        }
        String designes = java.util.stream.Stream.of(pv.getImMembreCoSignataire(), pv.getImCcCoSignataire())
                .filter(java.util.Objects::nonNull).filter(s -> !s.isBlank())
                .map(this::nom).reduce((a, b) -> a + ", " + b).orElse(null);
        if (designes != null) {
            detail.append(" — co-signataire(s) : ").append(designes);
        }
        if (Boolean.TRUE.equals(pv.getViseParInterim())) {
            detail.append(" — par intérim");
        }
        return detail.toString();
    }

    /**
     * Les parts de signature, puis le PV définitif et son archivage.
     *
     * <p>Ces actes ne portent qu'une <strong>date</strong> : ils sont placés en fin de journée et
     * ordonnés entre eux par le rang du circuit (visa &lt; signature &lt; PV signé &lt; archivage).</p>
     */
    private void ajouterSignatures(List<ActionDossierDto> cible, Integer idDossier, PvExamen pv) {
        ajouterPart(cible, idDossier, pv.getDateSignaturePresident(), pv.getImCtrlPresident(), "Président");
        ajouterPart(cible, idDossier, pv.getDateSignatureCc(), pv.getImCtrlCc(), "Chef de commission");
        // ⚠️ La part Membre appartient au DÉSIGNÉ depuis le 2026-08-28 ; les PV antérieurs n'en ont pas,
        // et c'est alors l'attributaire qui l'avait posée. Le repli garde ces PV lisibles.
        String membre = pv.getImMembreCoSignataire() == null || pv.getImMembreCoSignataire().isBlank()
                ? pv.getImCtrlMembre() : pv.getImMembreCoSignataire();
        ajouterPart(cible, idDossier, pv.getDateSignatureMembre(), membre, "Membre");

        if (StatutPv.SIGNE.name().equals(pv.getStatutPv()) && pv.getDatePv() != null) {
            String reference = pv.getRefePv() != null ? pv.getRefePv()
                    : pv.getReferencePv() != null ? pv.getReferencePv() : ("n° " + pv.getIdPv());
            cible.add(evenement(idDossier, finDeJournee(pv.getDatePv()), PV_SIGNE, null,
                    "PV définitif " + reference));
        }
        if (pv.getDateArchivage() != null) {
            cible.add(evenement(idDossier, finDeJournee(pv.getDateArchivage()), ARCHIVAGE,
                    pv.getImArchiveur(), "PV archivé, dossier clos"));
        }
    }

    private void ajouterPart(List<ActionDossierDto> cible, Integer idDossier, LocalDate date,
            String im, String role) {
        if (date == null) {
            return;
        }
        cible.add(evenement(idDossier, finDeJournee(date), SIGNATURE, im, "part " + role + " signée"));
    }

    private void ajouterVerification(List<ActionDossierDto> cible, Integer idDossier, Verification v) {
        if (v.getDateVerif() == null) {
            return;
        }
        cible.add(evenement(idDossier, finDeJournee(v.getDateVerif()), DECISION_VERIFICATION,
                v.getImCtrlVerif(), Boolean.TRUE.equals(v.getObsLevees())
                        ? "vérification : observations levées"
                        : "vérification : observations maintenues"));
    }

    /**
     * Un acte daté sans heure est placé à la fin de sa journée — jamais au début. Placé à l'aube, il
     * serait passé AVANT les navettes du même jour, alors qu'un visa précède toujours la signature
     * qu'il ouvre.
     */
    private static LocalDateTime finDeJournee(LocalDate date) {
        return date.atTime(LocalTime.of(23, 59, 59));
    }

    private ActionDossierDto evenement(Integer idDossier, LocalDateTime instant, String type,
            String imActeur, String detail) {
        ActionDossierDto dto = new ActionDossierDto();
        dto.setIdDossier(idDossier);
        dto.setDateAction(instant);
        dto.setTypeAction(type);
        // ⚠️ Ces événements sont DÉRIVÉS : ils n'ont pas de ligne en base, donc pas d'identifiant. Le
        // front ne doit pas s'en servir comme clé — l'instant et le type les identifient.
        dto.setIdAction(null);
        dto.setNomOperateur(nom(imActeur));
        dto.setAuteur(imActeur);
        // idPrmpOperateur / idMandatOperateur restent nuls : ce sont des gestes de contrôleur, et le
        // marqueur « opérateur ≠ attributaire » du front s'allume sur le premier (règle du 2026-09-04).
        dto.setDetail(detail);
        return dto;
    }

    /** « NOM Prénoms » d'un contrôleur (convention canonique unique) ; repli sur le matricule. */
    private String nom(String im) {
        if (im == null || im.isBlank()) {
            return null;
        }
        return controleurRepository.findById(im).map(c -> {
            String complet = ActeurDirectory.nomCanonique(c.getNomCont(), c.getPrenomsCont());
            return complet.isBlank() ? im : complet;
        }).orElse(im);
    }
}
