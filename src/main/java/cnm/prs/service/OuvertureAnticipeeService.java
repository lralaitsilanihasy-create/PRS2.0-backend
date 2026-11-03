package cnm.prs.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.config.HorlogeRecette;
import cnm.prs.dto.OuvertureAnticipeeDto;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.ConsentementAnticipation;
import cnm.prs.entity.Invitation;
import cnm.prs.entity.Offre;
import cnm.prs.entity.OuvertureAnticipee;
import cnm.prs.entity.SeanceJournal;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.ConsentementAnticipationRepository;
import cnm.prs.repository.InvitationRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.OuvertureAnticipeeRepository;
import cnm.prs.repository.SeanceJournalRepository;
import cnm.prs.repository.SeanceRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ V160 (2026-11-03, demande front « appel-offres-restreint », §B7 ; arbitrage Q4 du pilote ; loi n° 2016-055, art. 38-VI ; décret
 * n° 2019-1310, art. 14 ; guide de la CAO, § 4.5.1) — l'<strong>ouverture anticipée des plis</strong> d'un appel d'offres restreint en
 * ligne.
 * <ul>
 *   <li><strong>Demande</strong> (PRMP) : avant la date limite, quand <strong>tous les invités</strong> ont déposé une offre ; chaque
 *       invité est sollicité (notification) ; dès la demande, les offres sont <strong>figées</strong> (ni dépôt, ni remplacement, ni
 *       retrait : 409 {@code OUVERTURE_ANTICIPEE}).</li>
 *   <li><strong>Consentement</strong> (chaque invité, depuis son espace) : un geste écrit, horodaté et nominatif, au journal de la
 *       séance ; un <strong>refus</strong> clôt la demande ({@code REFUSEE}) : la date initiale demeure, les offres redeviennent libres ;
 *       tous d'accord : {@code ACCORDEE}.</li>
 *   <li><strong>Fixation</strong> (PRMP) : la nouvelle heure d'ouverture, après maintenant et avant l'heure initiale, notifiée aux invités
 *       et à la Commission ({@code FIXEE}). La séance s'ouvre alors à cette heure, la date limite n'étant pas passée ; les rappels de la
 *       séance la suivent ; le PV d'ouverture mentionne l'ouverture anticipée et les consentements.</li>
 * </ul>
 * Hors du champ : les prestations intellectuelles (deux enveloppes, deux séances), les procédures ouvertes, la remise papier.
 */
@Service
@Transactional
public class OuvertureAnticipeeService {

    public static final String CODE_FIGEE = "OUVERTURE_ANTICIPEE";
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private final OuvertureAnticipeeRepository demandes;
    private final ConsentementAnticipationRepository consentements;
    private final ProceduresEnLigneService procedures;
    private final FicheMarcheService fiches;
    private final OffreRepository offres;
    private final InvitationRepository invitations;
    private final CompteCandidatRepository comptes;
    private final SeanceRepository seances;
    private final SeanceJournalRepository journal;
    private final NotificationService notifications;
    private final ParametresInternesService internes;

    public OuvertureAnticipeeService(OuvertureAnticipeeRepository demandes, ConsentementAnticipationRepository consentements,
            ProceduresEnLigneService procedures, FicheMarcheService fiches, OffreRepository offres, InvitationRepository invitations,
            CompteCandidatRepository comptes, SeanceRepository seances, SeanceJournalRepository journal, NotificationService notifications,
            ParametresInternesService internes) {
        this.demandes = demandes;
        this.consentements = consentements;
        this.procedures = procedures;
        this.fiches = fiches;
        this.offres = offres;
        this.invitations = invitations;
        this.comptes = comptes;
        this.seances = seances;
        this.journal = journal;
        this.notifications = notifications;
        this.internes = internes;
    }

    private static LocalDateTime maintenant() {
        return LocalDateTime.now(HorlogeRecette.HORLOGE);
    }

    // ------------------------------------------------------------------ lecture

    /** La demande de la procédure (lecteurs de la fiche) ; {@code null} sans demande. */
    @Transactional(readOnly = true)
    public OuvertureAnticipeeDto lire(Long idDmc) {
        fiches.controlerLecture(idDmc);
        return demandes.findById(idDmc).map(d -> dto(d, null)).orElse(null);
    }

    /** La demande vue par un invité : son seul consentement ; {@code null} sans demande ; 404 hors des invités. */
    @Transactional(readOnly = true)
    public OuvertureAnticipeeDto lirePourCandidat(String idCandidat, Long idDmc) {
        procedures.exigerInvite(lue(idDmc), idCandidat);
        return demandes.findById(idDmc).map(d -> dto(d, idCandidat)).orElse(null);
    }

    /** L'heure d'ouverture fixée par anticipation, vide sans elle (lue par la séance). */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> heureFixee(Long idDmc) {
        return demandes.findById(idDmc).filter(d -> OuvertureAnticipee.FIXEE.equals(d.getEtat()))
                .map(OuvertureAnticipee::getNouvelleOuverture);
    }

    /** Pour le PV d'ouverture : la demande aboutie et ses consentements, vide sans elle. */
    @Transactional(readOnly = true)
    public Optional<OuvertureAnticipeeDto> fixee(Long idDmc) {
        return demandes.findById(idDmc).filter(d -> OuvertureAnticipee.FIXEE.equals(d.getEtat())).map(d -> dto(d, null));
    }

    /** Les offres sont figées par une demande en cours ou aboutie : 409 {@code OUVERTURE_ANTICIPEE} (lue par le dépôt des offres). */
    @Transactional(readOnly = true)
    public void exigerOffresLibres(Long idDmc) {
        if (demandes.findById(idDmc).filter(OuvertureAnticipee::enCours).isPresent()) {
            throw new BusinessRuleException("Une ouverture anticipée des plis est demandée : les offres déposées sont figées (ni dépôt, ni "
                    + "remplacement, ni retrait).", CODE_FIGEE);
        }
    }

    // ------------------------------------------------------------------ PRMP

    /**
     * Demande l'ouverture anticipée (PRMP) : 409 {@code HORS_RESTREINT} (pas un appel d'offres restreint en ligne), {@code DEPOTS_CLOS}
     * (date limite passée), {@code SEANCE_DEJA_OUVERTE}, {@code DEJA_DEMANDEE}, {@code DEPOTS_INCOMPLETS} ({@code details.manquants} :
     * les invités sans offre).
     */
    public OuvertureAnticipeeDto demander(Long idDmc) {
        exigerPrmp(idDmc);
        ProceduresEnLigneService.Lue l = lue(idDmc);
        if (!ProceduresEnLigneService.restreinte(l) || ProceduresEnLigneService.pi(l)) {
            throw new BusinessRuleException("L'ouverture anticipée vaut pour un appel d'offres restreint en ligne.", "HORS_RESTREINT");
        }
        if (!ProceduresEnLigneService.OUVERTE.equals(l.dto().etat())) {
            throw new BusinessRuleException("Les dépôts ne sont pas ouverts : " + (ProceduresEnLigneService.CLOSE.equals(l.dto().etat())
                    ? "la date limite est passée, la séance s'ouvre à la date prévue." : "rien n'est encore déposé."), "DEPOTS_CLOS");
        }
        if (seances.existsById(idDmc)) {
            throw new BusinessRuleException("La séance est déjà ouverte.", "SEANCE_DEJA_OUVERTE");
        }
        if (demandes.existsById(idDmc)) {
            throw new BusinessRuleException("Une ouverture anticipée a déjà été demandée pour cette procédure.", "DEJA_DEMANDEE");
        }
        Map<String, String> deposants = deposants(idDmc);
        List<String> manquants = new ArrayList<>();
        Map<String, String> invites = new LinkedHashMap<>();   // idCandidat → nom
        for (Invitation i : invitations.findByIdDmcOrderByRangAsc(idDmc)) {
            String compte = compteDe(i, deposants);
            if (compte == null) {
                manquants.add(i.getNom());
            } else {
                invites.put(compte, i.getNom());
            }
        }
        if (invites.isEmpty() || !manquants.isEmpty()) {
            throw new BusinessRuleException("L'ouverture anticipée suppose que tous les invités aient remis leur offre"
                    + (manquants.isEmpty() ? "." : " ; manquent : " + String.join(", ", manquants) + "."), "DEPOTS_INCOMPLETS", null,
                    Map.of("manquants", manquants));
        }
        OuvertureAnticipee d = new OuvertureAnticipee();
        d.setIdDmc(idDmc);
        d.setEtat(OuvertureAnticipee.DEMANDEE);
        d.setOuvertureInitiale(heureInitiale(idDmc));
        d.setDemandeeLe(maintenant());
        d.setDemandeePar(acteur());
        demandes.saveAndFlush(d);
        invites.forEach((compte, nom) -> {
            consentements.save(new ConsentementAnticipation(null, idDmc, compte, nom, null, null, null));
            notifierCandidat(compte, idDmc, "Ouverture anticipée des plis : votre consentement", "Tous les invités ont remis leur offre pour « "
                    + l.dto().objet() + " ». La personne responsable des marchés publics propose d'ouvrir les plis avant la date prévue"
                    + (d.getOuvertureInitiale() == null ? "" : " (" + d.getOuvertureInitiale().format(HORODATAGE) + ")") + " : consentez-vous "
                    + "par écrit à cette ouverture anticipée ? Votre offre est figée en attendant les réponses ; un seul refus maintient la date "
                    + "initiale.");
        });
        tracer(idDmc, "ANTICIPATION_DEMANDEE", invites.size() + " invité(s) sollicité(s) ; offres figées");
        return dto(d, null);
    }

    /**
     * Fixe la nouvelle heure d'ouverture (PRMP), tous les invités ayant consenti : 400 {@code nouvelleOuverture} (absente, illisible, pas
     * après maintenant, pas avant l'heure initiale) ; 404 sans demande ; 409 {@code CONSENTEMENTS_INCOMPLETS}, {@code ANTICIPATION_REFUSEE},
     * {@code SEANCE_DEJA_OUVERTE}. Une heure déjà fixée se change tant que la séance n'est pas ouverte.
     */
    public OuvertureAnticipeeDto fixer(Long idDmc, OuvertureAnticipeeDto.Fixation f) {
        exigerPrmp(idDmc);
        OuvertureAnticipee d = demandes.findById(idDmc).orElseThrow(() -> new ResourceNotFoundException(
                "Aucune ouverture anticipée n'est demandée pour cette procédure."));
        if (OuvertureAnticipee.REFUSEE.equals(d.getEtat())) {
            throw new BusinessRuleException("Un invité a refusé l'ouverture anticipée : la date initiale demeure.", "ANTICIPATION_REFUSEE");
        }
        if (OuvertureAnticipee.DEMANDEE.equals(d.getEtat())) {
            throw new BusinessRuleException("Tous les invités n'ont pas encore consenti par écrit.", "CONSENTEMENTS_INCOMPLETS");
        }
        if (seances.existsById(idDmc)) {
            throw new BusinessRuleException("La séance est déjà ouverte.", "SEANCE_DEJA_OUVERTE");
        }
        LocalDateTime nouvelle;
        try {
            nouvelle = f == null || f.nouvelleOuverture() == null || f.nouvelleOuverture().isBlank() ? null
                    : LocalDateTime.parse(f.nouvelleOuverture().trim());
        } catch (DateTimeParseException e) {
            throw invalide("nouvelleOuverture", "La nouvelle heure d'ouverture est au format AAAA-MM-JJTHH:MM.");
        }
        if (nouvelle == null) {
            throw invalide("nouvelleOuverture", "La nouvelle heure d'ouverture est obligatoire.");
        }
        if (!nouvelle.isAfter(maintenant())) {
            throw invalide("nouvelleOuverture", "La nouvelle heure d'ouverture est à venir.");
        }
        if (d.getOuvertureInitiale() != null && !nouvelle.isBefore(d.getOuvertureInitiale())) {
            throw invalide("nouvelleOuverture", "La nouvelle heure d'ouverture précède l'heure initiale ("
                    + d.getOuvertureInitiale().format(HORODATAGE) + ").");
        }
        d.setNouvelleOuverture(nouvelle.withSecond(0).withNano(0));
        d.setEtat(OuvertureAnticipee.FIXEE);
        d.setFixeeLe(maintenant());
        d.setFixeePar(acteur());
        demandes.save(d);
        String quand = d.getNouvelleOuverture().format(HORODATAGE);
        for (ConsentementAnticipation c : consentements.findByIdDmcOrderByIdAsc(idDmc)) {
            notifierCandidat(c.getIdCandidat(), idDmc, "Ouverture anticipée des plis : nouvelle date", "Tous les invités ayant consenti, les plis "
                    + "seront ouverts le " + quand + " (heure du serveur). Vous pouvez assister à la séance.");
        }
        internes.notifierCommission(idDmc, TypeNotification.OUVERTURE_ANTICIPEE, "Ouverture anticipée des plis", "Tous les invités ont "
                + "consenti : la séance d'ouverture des plis de la procédure " + idDmc + " est avancée au " + quand + " (heure du serveur).", true);
        tracer(idDmc, "ANTICIPATION_FIXEE", "nouvelle ouverture le " + quand);
        return dto(d, null);
    }

    // ------------------------------------------------------------------ invité

    /**
     * La réponse écrite d'un invité : 400 {@code accord} absent ; 404 hors des invités sollicités ; 409 {@code ANTICIPATION_CLOSE}
     * (demande refusée ou aboutie), {@code DEJA_REPONDU}.
     */
    public OuvertureAnticipeeDto repondre(String idCandidat, Long idDmc, OuvertureAnticipeeDto.Reponse r) {
        procedures.exigerInvite(lue(idDmc), idCandidat);
        OuvertureAnticipee d = demandes.findById(idDmc).orElseThrow(() -> new ResourceNotFoundException(
                "Aucune ouverture anticipée n'est demandée pour cette procédure."));
        ConsentementAnticipation c = consentements.findByIdDmcAndIdCandidat(idDmc, idCandidat).orElseThrow(() ->
                new ResourceNotFoundException("Votre consentement n'est pas sollicité pour cette procédure."));
        if (r == null || r.accord() == null) {
            throw invalide("accord", "Dites si vous consentez (accord = vrai) ou non (faux) à l'ouverture anticipée.");
        }
        if (!OuvertureAnticipee.DEMANDEE.equals(d.getEtat())) {
            throw new BusinessRuleException("La demande d'ouverture anticipée est close (" + d.getEtat().toLowerCase(Locale.ROOT) + ").",
                    "ANTICIPATION_CLOSE");
        }
        if (c.getAccord() != null) {
            throw new BusinessRuleException("Vous avez déjà répondu.", "DEJA_REPONDU");
        }
        c.setAccord(r.accord());
        c.setReponduLe(maintenant());
        c.setReponduPar(acteur());
        consentements.save(c);
        tracer(idDmc, r.accord() ? "CONSENTEMENT" : "REFUS_ANTICIPATION", (c.getNom() == null ? idCandidat : c.getNom())
                + (r.accord() ? " consent par écrit à l'ouverture anticipée" : " refuse l'ouverture anticipée"));
        if (!r.accord()) {
            d.setEtat(OuvertureAnticipee.REFUSEE);
            demandes.save(d);
            tracer(idDmc, "ANTICIPATION_REFUSEE", "la date initiale demeure ; offres de nouveau libres");
        } else if (consentements.findByIdDmcOrderByIdAsc(idDmc).stream().allMatch(x -> Boolean.TRUE.equals(x.getAccord()))) {
            d.setEtat(OuvertureAnticipee.ACCORDEE);
            demandes.save(d);
            tracer(idDmc, "ANTICIPATION_ACCORDEE", "tous les invités ont consenti");
        }
        return dto(d, idCandidat);
    }

    // ------------------------------------------------------------------ outils

    private ProceduresEnLigneService.Lue lue(Long idDmc) {
        return procedures.trouver(idDmc).orElseThrow(() -> new ResourceNotFoundException("Procédure en ligne introuvable : " + idDmc + "."));
    }

    private void exigerPrmp(Long idDmc) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException("Seule la PRMP de la fiche demande et fixe l'ouverture anticipée.");
        }
        fiches.controlerLecture(idDmc);
    }

    /** Les candidats qui ont une offre déposée (idCandidat → adresse électronique du compte). */
    private Map<String, String> deposants(Long idDmc) {
        Map<String, String> out = new LinkedHashMap<>();
        offres.findByIdDmcOrderByNumeroAscDateCreationAsc(idDmc).stream().filter(o -> Offre.DEPOSEE.equals(o.getEtat()))
                .map(Offre::getIdCandidat).distinct()
                .forEach(id -> out.put(id, comptes.findById(id).map(CompteCandidat::getEmail).orElse(null)));
        return out;
    }

    /** Le compte de l'invité s'il a déposé : par son compte, ou par l'adresse électronique de l'invitation ; nul sinon. */
    private static String compteDe(Invitation i, Map<String, String> deposants) {
        if (i.getIdCandidat() != null && deposants.containsKey(i.getIdCandidat())) {
            return i.getIdCandidat();
        }
        return i.getEmail() == null ? null : deposants.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().equalsIgnoreCase(i.getEmail())).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private LocalDateTime heureInitiale(Long idDmc) {
        return fiches.etatValide(idDmc).map(e -> e.etat().getValeurs() == null ? null : RemiseElectronique.echeance(
                e.etat().getValeurs().get(RemiseElectronique.DATE_OUVERTURE_PLIS), e.etat().getValeurs().get(RemiseElectronique.HEURE_OUVERTURE_PLIS)))
                .orElse(null);
    }

    private void notifierCandidat(String idCandidat, Long idDmc, String titre, String corps) {
        notifications.emettreCandidat(TypeNotification.OUVERTURE_ANTICIPEE, idCandidat, comptes.findById(idCandidat).map(CompteCandidat::getEmail)
                .orElse(null), idDmc.intValue(), TypeObjet.PROCEDURE, titre, corps);
    }

    private void tracer(Long idDmc, String action, String detail) {
        journal.save(new SeanceJournal(null, idDmc, maintenant(), acteur(), action, detail));
    }

    /** Un 400 nominatif. */
    private static ChampsInvalidesException invalide(String champ, String message) {
        return new ChampsInvalidesException(List.of(new ErrorResponse.FieldError(champ, message)));
    }

    private static String acteur() {
        return CurrentUser.ref().or(CurrentUser::login).orElse(null);
    }

    private OuvertureAnticipeeDto dto(OuvertureAnticipee d, String seulement) {
        List<OuvertureAnticipeeDto.Consentement> cs = consentements.findByIdDmcOrderByIdAsc(d.getIdDmc()).stream()
                .filter(c -> seulement == null || seulement.equals(c.getIdCandidat()))
                .map(c -> new OuvertureAnticipeeDto.Consentement(c.getIdCandidat(), c.getNom(), c.getAccord(), c.getReponduLe())).toList();
        return new OuvertureAnticipeeDto(d.getIdDmc(), d.getEtat(), d.getOuvertureInitiale(), d.getNouvelleOuverture(), d.getDemandeeLe(),
                d.getDemandeePar(), d.getFixeeLe(), d.getFixeePar(), cs);
    }
}
