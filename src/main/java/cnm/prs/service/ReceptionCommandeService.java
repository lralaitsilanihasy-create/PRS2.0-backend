package cnm.prs.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ReceptionCommandeDto;
import cnm.prs.entity.BonCommande;
import cnm.prs.entity.CommissionReception;
import cnm.prs.entity.CommissionReceptionMembre;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.CompteCao;
import cnm.prs.entity.ContratVigueur;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Offre;
import cnm.prs.entity.ReceptionCommande;
import cnm.prs.entity.ReceptionSignature;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.BonCommandeRepository;
import cnm.prs.repository.CommissionReceptionMembreRepository;
import cnm.prs.repository.CommissionReceptionRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.CompteCaoRepository;
import cnm.prs.repository.ContratVigueurRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.ReceptionCommandeRepository;
import cnm.prs.repository.ReceptionSignatureRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-4 ; V127 ; loi n° 2016-055, art. 13 ; CCAG fournitures, art. 22) — la
 * réception des commandes.
 *
 * <ul>
 *   <li><strong>La commission de réception</strong> (réponse Q1 du pilote) : désignée par décision de la PRMP, par contrat en vigueur,
 *       saisie par la PRMP ou son UGPM ; présidée par la PRMP ou par un membre qui la représente (loi, art. 13-IV) ; chaque membre a
 *       son compte (les comptes K… des commissions, invités par courriel) pour signer.</li>
 *   <li><strong>La livraison</strong> se déclare par le titulaire dans son espace, ou par la PRMP ou l'UGPM.</li>
 *   <li><strong>La réception provisoire</strong> : le président établit le PV — réception, ajournement, réfaction ou rejet (CCAG,
 *       art. 22) — avec le retard et la pénalité <strong>indicative</strong> (jamais retenue) ; chaque membre le signe, et la PRMP
 *       quand elle préside. Un ajournement ou un rejet appelle une nouvelle livraison.</li>
 *   <li><strong>La réception définitive</strong>, au terme du délai de garantie ({@code B09-GP-03} du contrat-cadre, {@code B09-DG-01}
 *       du marché à commandes) ; sans délai de garantie, la réception provisoire vaut définitive.</li>
 * </ul>
 */
@Service
public class ReceptionCommandeService {

    public static final String RECEPTION_DEFINITIVE_A_PRONONCER = "RECEPTION_DEFINITIVE_A_PRONONCER";
    static final String PRMP = "PRMP";
    /** ⚠️ H (à confirmer) : la pénalité journalière du CCAP courant — 1/1000 du montant de la commande (CCAP du jeu 2463, art. 11). */
    static final BigDecimal TAUX_DEFAUT = BigDecimal.ONE;
    private static final Set<String> DECISIONS = Set.of(ReceptionCommande.RECEPTION, ReceptionCommande.AJOURNEMENT, ReceptionCommande.REFACTION,
            ReceptionCommande.REJET);
    private static final Pattern DELAI = Pattern.compile("(\\d+)\\s*(jour|semaine|mois)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private final ReceptionCommandeRepository receptions;
    private final ReceptionSignatureRepository signatures;
    private final CommissionReceptionRepository commissions;
    private final CommissionReceptionMembreRepository membres;
    private final BonCommandeRepository bons;
    private final ContratVigueurRepository contrats;
    private final OffreRepository offres;
    private final CompteCandidatRepository candidats;
    private final CompteCaoRepository comptesCao;
    private final CompteCaoService comptes;
    private final DossierMecRepository dmcRepository;
    private final FicheMarcheService fiches;
    private final ValeursPpmService valeursPpm;
    private final NotificationService notifications;
    private final CeremonieService ceremonies;
    private final EvaluationService evaluation;
    private final GenerateurDocumentsFiche generateur;
    private final Clock horloge;

    public ReceptionCommandeService(ReceptionCommandeRepository receptions, ReceptionSignatureRepository signatures,
            CommissionReceptionRepository commissions, CommissionReceptionMembreRepository membres, BonCommandeRepository bons,
            ContratVigueurRepository contrats, OffreRepository offres, CompteCandidatRepository candidats, CompteCaoRepository comptesCao,
            CompteCaoService comptes, DossierMecRepository dmcRepository, FicheMarcheService fiches, ValeursPpmService valeursPpm,
            NotificationService notifications, CeremonieService ceremonies, EvaluationService evaluation, GenerateurDocumentsFiche generateur,
            Clock horloge) {
        this.receptions = receptions;
        this.signatures = signatures;
        this.commissions = commissions;
        this.membres = membres;
        this.bons = bons;
        this.contrats = contrats;
        this.offres = offres;
        this.candidats = candidats;
        this.comptesCao = comptesCao;
        this.comptes = comptes;
        this.dmcRepository = dmcRepository;
        this.fiches = fiches;
        this.valeursPpm = valeursPpm;
        this.notifications = notifications;
        this.ceremonies = ceremonies;
        this.evaluation = evaluation;
        this.generateur = generateur;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ la commission (Q1)

    @Transactional(readOnly = true)
    public ReceptionCommandeDto.Commission commission(Long idContrat) {
        ContratVigueur c = contrat(idContrat);
        exigerLecteurInterne(c.getIdDmc());
        return commissionDto(idContrat);
    }

    /**
     * La PRMP ou l'UGPM désigne (ou recompose) la commission : 400 champs invalides (décision, membres, présidence) ; 409
     * {@code PV_EN_SIGNATURE} tant qu'un PV attend ses signatures. Les nouveaux membres reçoivent leur invitation et une notification.
     */
    @Transactional
    public ReceptionCommandeDto.Commission designer(Long idContrat, ReceptionCommandeDto.CommissionCorps corps) {
        ContratVigueur c = contrat(idContrat);
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p != ProfilUtilisateur.PRMP && p != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("La commission de réception se désigne par décision de la PRMP (saisie par la PRMP ou son UGPM).");
        }
        fiches.controlerLecture(c.getIdDmc());
        ReceptionCommandeDto.CommissionCorps k = corps == null ? new ReceptionCommandeDto.CommissionCorps(null, null, null) : corps;
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        if (k.decision() == null || nettoyer(k.decision().reference()) == null) {
            erreurs.add(new ErrorResponse.FieldError("decision.reference", "La référence de la décision de la PRMP est obligatoire."));
        }
        if (k.decision() == null || k.decision().date() == null || k.decision().date().isAfter(aujourdhui())) {
            erreurs.add(new ErrorResponse.FieldError("decision.date", "La date de la décision est obligatoire, au plus tard aujourd'hui."));
        }
        boolean parPrmp = !Boolean.FALSE.equals(k.presideeParPrmp());
        List<ReceptionCommandeDto.MembreCorps> saisis = k.membres() == null ? List.of() : k.membres();
        Set<String> adresses = new LinkedHashSet<>();
        int presidents = 0;
        for (int i = 0; i < saisis.size(); i++) {
            ReceptionCommandeDto.MembreCorps m = saisis.get(i);
            String q = "membres[" + i + "].";
            if (m == null) {
                erreurs.add(new ErrorResponse.FieldError("membres[" + i + "]", "Membre vide."));
                continue;
            }
            if (nettoyer(m.nom()) == null) {
                erreurs.add(new ErrorResponse.FieldError(q + "nom", "Le nom est obligatoire."));
            }
            if (nettoyer(m.prenom()) == null) {
                erreurs.add(new ErrorResponse.FieldError(q + "prenom", "Le prénom est obligatoire."));
            }
            String email = CandidatService.normaliserEmail(m.email());
            if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                erreurs.add(new ErrorResponse.FieldError(q + "email", "Une adresse électronique valable est obligatoire."));
            } else if (!adresses.add(email)) {
                erreurs.add(new ErrorResponse.FieldError(q + "email", "Cette adresse figure deux fois dans la commission."));
            }
            presidents += Boolean.TRUE.equals(m.president()) ? 1 : 0;
        }
        if (parPrmp && presidents > 0) {
            erreurs.add(new ErrorResponse.FieldError("membres", "La PRMP préside : aucun membre n'est président."));
        }
        if (!parPrmp && presidents != 1) {
            erreurs.add(new ErrorResponse.FieldError("membres", "Un seul membre préside, représentant la PRMP (loi n° 2016-055, art. 13)."));
        }
        if (saisis.size() + (parPrmp ? 1 : 0) < 2) {
            erreurs.add(new ErrorResponse.FieldError("membres", "La commission compte au moins deux personnes."));
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        for (BonCommande b : bons.findByIdContratOrderByNumeroAsc(idContrat)) {
            if (receptions.findByIdBonOrderByIdAsc(b.getId()).stream().anyMatch(r -> ReceptionCommande.PV_ETABLI.equals(r.getEtat()))) {
                throw new BusinessRuleException("Un PV de réception attend ses signatures : la commission ne se recompose pas avant.", "PV_EN_SIGNATURE");
            }
        }
        CommissionReception cr = commissions.findById(idContrat).orElseGet(() -> {
            CommissionReception n = new CommissionReception();
            n.setIdContrat(idContrat);
            return n;
        });
        cr.setDecisionReference(k.decision().reference().trim());
        cr.setDecisionDate(k.decision().date());
        cr.setPresideeParPrmp(parPrmp);
        cr.setMajLe(maintenant());
        cr.setMajPar(acteur());
        commissions.saveAndFlush(cr);
        Set<String> avant = new LinkedHashSet<>(membres.findByIdContratOrderByRangAscIdAsc(idContrat).stream().map(CommissionReceptionMembre::getIdCompte)
                .toList());
        membres.deleteAll(membres.findByIdContratOrderByRangAscIdAsc(idContrat));
        membres.flush();
        String objet = "réception du contrat de la procédure " + c.getIdDmc() + ", lot " + c.getLot();
        String autorite = Objects.toString(plan(c).get("ENTITE"), "");
        for (int i = 0; i < saisis.size(); i++) {
            ReceptionCommandeDto.MembreCorps m = saisis.get(i);
            CompteCao compte = comptes.creerOuRetrouver(m.email(), m.nom(), m.prenom());
            CommissionReceptionMembre e = new CommissionReceptionMembre();
            e.setIdContrat(idContrat);
            e.setRang(i);
            e.setNom(m.nom().trim());
            e.setPrenom(m.prenom().trim());
            e.setEmail(compte.getEmail());
            e.setFonction(nettoyer(m.fonction()));
            e.setPresident(Boolean.TRUE.equals(m.president()));
            e.setIdCompte(compte.getIdCompte());
            membres.save(e);
            if (!avant.contains(compte.getIdCompte())) {
                if (!CompteCao.ACTIF.equals(compte.getEtat())) {
                    comptes.inviter(compte, objet, autorite);
                }
                notifications.emettreMembreCao(TypeNotification.COMMISSION_RECEPTION, compte.getIdCompte(), compte.getEmail(), c.getIdDmc().intValue(),
                        TypeObjet.PROCEDURE, "Commission de réception : vous êtes désigné membre", "Vous êtes désigné membre" + (e.isPresident()
                                ? " et président" : "") + " de la commission de réception (" + objet + "), par décision n° " + cr.getDecisionReference()
                                + " du " + cr.getDecisionDate().format(JOUR) + ". Vous signerez ses procès-verbaux avec votre compte.");
            }
        }
        evaluation.tracerAttribution(c.getIdDmc(), "COMMISSION_RECEPTION", "Lot " + c.getLot() + " : commission de réception désignée (décision n° "
                + cr.getDecisionReference() + " du " + cr.getDecisionDate().format(JOUR) + "), " + saisis.size() + " membre(s), présidée par "
                + (parPrmp ? "la PRMP" : "un membre la représentant"));
        return commissionDto(idContrat);
    }

    // ------------------------------------------------------------------ livraison

    /** La PRMP ou l'UGPM déclare une livraison (reçue sur papier, ou constatée). */
    @Transactional
    public ReceptionCommandeDto.DuBon declarerLivraison(Long idBon, ReceptionCommandeDto.LivraisonRequest r) {
        BonCommande b = bon(idBon);
        ContratVigueur c = contrat(b.getIdContrat());
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p != ProfilUtilisateur.PRMP && p != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("La livraison se déclare par le titulaire, ou par la PRMP ou son UGPM.");
        }
        fiches.controlerLecture(c.getIdDmc());
        livrer(b, c, r);
        return duBon(b, c);
    }

    /** Le titulaire déclare sa livraison ; 404 hors de ses bons. */
    @Transactional
    public ReceptionCommandeDto.DuBon declarerLivraisonTitulaire(String idCandidat, Long idBon, ReceptionCommandeDto.LivraisonRequest r) {
        BonCommande b = bonDuTitulaire(idCandidat, idBon);
        ContratVigueur c = contrat(b.getIdContrat());
        livrer(b, c, r);
        ceremonies.notifierPrmp(c.getIdDmc(), TypeNotification.LIVRAISON_DECLAREE, "Livraison déclarée", "Le titulaire a déclaré la livraison du "
                + "bon de commande n° " + b.getNumero() + " (lot " + c.getLot() + ", procédure " + c.getIdDmc() + ") : la réception est à établir.");
        return duBon(b, c);
    }

    /**
     * 400 {@code DATE_INVALIDE} (avant l'émission du bon, ou future) ; 409 {@code BON_ANNULE}, {@code RECEPTION_EN_COURS} (une réception
     * non signée), {@code DEJA_RECU}.
     */
    private void livrer(BonCommande b, ContratVigueur c, ReceptionCommandeDto.LivraisonRequest r) {
        if (BonCommande.ANNULE.equals(b.getEtat())) {
            throw new BusinessRuleException("Le bon de commande est annulé.", "BON_ANNULE");
        }
        String etat = etatReception(b, receptions.findByIdBonOrderByIdAsc(b.getId()), garantieMois(c));
        if ("EN_RECEPTION".equals(etat) || "EN_RECEPTION_DEFINITIVE".equals(etat)) {
            throw new BusinessRuleException("Une réception de ce bon est en cours.", "RECEPTION_EN_COURS");
        }
        if ("RECU_PROVISOIRE".equals(etat) || "RECU_DEFINITIF".equals(etat)) {
            throw new BusinessRuleException("La commande est déjà reçue.", "DEJA_RECU");
        }
        LocalDate le = r == null || r.dateLivraison() == null ? aujourdhui() : r.dateLivraison();
        if (le.isAfter(aujourdhui()) || le.isBefore(b.getEmisLe().toLocalDate())) {
            throw new BadRequestException("La date de livraison se situe entre l'émission du bon (" + b.getEmisLe().format(JOUR) + ") et aujourd'hui.",
                    "DATE_INVALIDE");
        }
        ReceptionCommande x = new ReceptionCommande();
        x.setIdBon(b.getId());
        x.setType(ReceptionCommande.PROVISOIRE);
        x.setEtat(ReceptionCommande.A_ETABLIR);
        x.setDateLivraison(le);
        x.setLivraisonObservations(r == null ? null : nettoyer(r.observations()));
        x.setDeclareeLe(maintenant());
        x.setDeclareePar(acteur());
        receptions.save(x);
        for (CommissionReceptionMembre m : membres.findByIdContratOrderByRangAscIdAsc(c.getId())) {
            notifications.emettreMembreCao(TypeNotification.LIVRAISON_DECLAREE, m.getIdCompte(), m.getEmail(), c.getIdDmc().intValue(),
                    TypeObjet.PROCEDURE, "Livraison à réceptionner", "La livraison du bon de commande n° " + b.getNumero() + " « " + b.getObjet()
                            + " » est déclarée (" + le.format(JOUR) + ") : la commission de réception est appelée à la constater.");
        }
        evaluation.tracerAttribution(c.getIdDmc(), "LIVRAISON", "Lot " + c.getLot() + " : livraison du bon de commande n° " + b.getNumero()
                + " déclarée le " + le.format(JOUR));
    }

    /**
     * Le président ouvre la réception définitive, au terme du délai de garantie : 409 {@code NON_RECU} (pas de réception provisoire
     * prononcée), {@code GARANTIE_EN_COURS}, {@code SANS_GARANTIE} (la provisoire vaut définitive), {@code RECEPTION_EN_COURS}.
     */
    @Transactional
    public ReceptionCommandeDto.DuBon ouvrirDefinitive(Long idBon) {
        BonCommande b = bon(idBon);
        ContratVigueur c = contrat(b.getIdContrat());
        exigerPresident(c);
        List<ReceptionCommande> rs = receptions.findByIdBonOrderByIdAsc(idBon);
        Integer garantie = garantieMois(c);
        String etat = etatReception(b, rs, garantie);
        if ("EN_RECEPTION_DEFINITIVE".equals(etat)) {
            throw new BusinessRuleException("La réception définitive est en cours.", "RECEPTION_EN_COURS");
        }
        if (garantie == null || garantie <= 0) {
            throw new BusinessRuleException("Sans délai de garantie, la réception provisoire vaut réception définitive.", "SANS_GARANTIE");
        }
        if (!"RECU_PROVISOIRE".equals(etat)) {
            throw new BusinessRuleException("La réception définitive suit la réception provisoire prononcée.", "NON_RECU");
        }
        LocalDate fin = garantieJusquau(rs, garantie);
        if (aujourdhui().isBefore(fin)) {
            throw new BusinessRuleException("Le délai de garantie court jusqu'au " + fin.format(JOUR) + ".", "GARANTIE_EN_COURS");
        }
        ReceptionCommande x = new ReceptionCommande();
        x.setIdBon(idBon);
        x.setType(ReceptionCommande.DEFINITIVE);
        x.setEtat(ReceptionCommande.A_ETABLIR);
        x.setDeclareeLe(maintenant());
        x.setDeclareePar(acteur());
        receptions.save(x);
        return duBon(b, c);
    }

    // ------------------------------------------------------------------ le PV

    /**
     * Le président établit (ou rétablit, les signatures repartent de zéro) le PV : 400 {@code DECISION_INVALIDE}, {@code MOTIF_OBLIGATOIRE}
     * (ajournement, réfaction, rejet), {@code REFACTION_INVALIDE}, {@code DATE_INVALIDE}, {@code TAUX_INVALIDE} ; 409
     * {@code COMMISSION_NON_DESIGNEE}, {@code PV_SIGNE}.
     */
    @Transactional
    public ReceptionCommandeDto etablir(Long id, ReceptionCommandeDto.PvRequest r) {
        ReceptionCommande x = reception(id);
        BonCommande b = bon(x.getIdBon());
        ContratVigueur c = contrat(b.getIdContrat());
        exigerPresident(c);
        if (ReceptionCommande.SIGNE.equals(x.getEtat())) {
            throw new BusinessRuleException("Le PV est signé : il ne se modifie plus.", "PV_SIGNE");
        }
        boolean definitive = ReceptionCommande.DEFINITIVE.equals(x.getType());
        String decision = r == null || r.decision() == null ? null : r.decision().trim().toUpperCase(Locale.ROOT);
        if (decision == null || !DECISIONS.contains(decision) || definitive && !ReceptionCommande.RECEPTION.equals(decision)
                && !ReceptionCommande.AJOURNEMENT.equals(decision)) {
            throw new BadRequestException(definitive ? "La réception définitive se prononce (RECEPTION) ou s'ajourne (AJOURNEMENT)."
                    : "La commission prononce la réception, l'ajournement, la réfaction ou le rejet (RECEPTION, AJOURNEMENT, REFACTION, REJET).",
                    "DECISION_INVALIDE");
        }
        String motif = nettoyer(r.motif());
        if (!ReceptionCommande.RECEPTION.equals(decision) && motif == null) {
            throw new BadRequestException("L'ajournement, la réfaction et le rejet se motivent.", "MOTIF_OBLIGATOIRE");
        }
        BigDecimal refaction = ReceptionCommande.REFACTION.equals(decision) ? r.montantRefaction() : null;
        if (ReceptionCommande.REFACTION.equals(decision) && (refaction == null || refaction.signum() <= 0 || refaction.compareTo(b.getMontantHt()) >= 0)) {
            throw new BadRequestException("La réfaction est un montant positif, inférieur au montant du bon.", "REFACTION_INVALIDE");
        }
        LocalDate le = r.dateReception() == null ? aujourdhui() : r.dateReception();
        LocalDate plancher = definitive ? x.getDeclareeLe().toLocalDate() : x.getDateLivraison();
        if (le.isAfter(aujourdhui()) || le.isBefore(plancher)) {
            throw new BadRequestException("La date de réception se situe entre le " + plancher.format(JOUR) + " et aujourd'hui.", "DATE_INVALIDE");
        }
        BigDecimal taux = r.tauxJournalier() == null ? TAUX_DEFAUT : r.tauxJournalier();
        if (taux.signum() < 0 || taux.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("Le taux journalier de pénalité s'exprime en pour mille du montant du bon, entre 0 et 100.", "TAUX_INVALIDE");
        }
        x.setDecision(decision);
        x.setDateReception(le);
        x.setMotif(motif);
        x.setReserves(nettoyer(r.reserves()));
        x.setMontantRefaction(refaction);
        if (definitive) {
            x.setJoursRetard(null);
            x.setTauxPenalite(null);
            x.setPenaliteIndicative(null);
        } else {
            LocalDate echeance = echeanceLivraison(b);
            x.setJoursRetard(echeance == null ? null : (int) Math.max(0, ChronoUnit.DAYS.between(echeance, x.getDateLivraison())));
            x.setTauxPenalite(taux);
            x.setPenaliteIndicative(penaliteApplicable(c) && x.getJoursRetard() != null && acceptee(decision)
                    ? penalite(b.getMontantHt(), taux, x.getJoursRetard(), plafond(c)) : null);
        }
        x.setEtabliLe(maintenant());
        x.setEtabliPar(acteur());
        x.setEtat(ReceptionCommande.PV_ETABLI);
        x.setSigneLe(null);
        signatures.deleteByIdReception(id);
        signatures.flush();
        produirePv(x, b, c);
        receptions.save(x);
        for (ReceptionCommandeDto.Attendu a : attendus(c, x)) {
            notifierSignataire(c, a.signataire(), "PV de réception à signer", "Le procès-verbal de réception " + (definitive ? "définitive" : "provisoire")
                    + " du bon de commande n° " + b.getNumero() + " (" + libelle(decision) + ") est établi : il attend votre signature.");
        }
        evaluation.tracerAttribution(c.getIdDmc(), "PV_RECEPTION", "Lot " + c.getLot() + " : PV de réception " + (definitive ? "définitive" : "provisoire")
                + " du bon de commande n° " + b.getNumero() + " établi — " + libelle(decision) + (motif == null ? "" : " (" + motif + ")"));
        return dto(x, b, c);
    }

    /**
     * Un signataire signe : 403 hors des signataires ; 409 {@code PV_NON_ETABLI}, {@code DEJA_SIGNE}. La dernière signature prononce la
     * décision : le titulaire et la PRMP en sont notifiés.
     */
    @Transactional
    public ReceptionCommandeDto signer(Long id) {
        ReceptionCommande x = reception(id);
        BonCommande b = bon(x.getIdBon());
        ContratVigueur c = contrat(b.getIdContrat());
        if (!ReceptionCommande.PV_ETABLI.equals(x.getEtat())) {
            throw new BusinessRuleException("Le PV n'attend pas de signature.", "PV_NON_ETABLI");
        }
        String moi = signataireConnecte(c).orElseThrow(() -> new AccessDeniedException("Le PV se signe par les membres de la commission de réception."));
        if (signatures.findById(cle(id, moi)).isPresent()) {
            throw new BusinessRuleException("Vous avez déjà signé ce PV.", "DEJA_SIGNE");
        }
        List<ReceptionCommandeDto.Attendu> attendus = attendus(c, x);
        ReceptionCommandeDto.Attendu a = attendus.stream().filter(y -> y.signataire().equals(moi)).findFirst().orElseThrow();
        ReceptionSignature s = new ReceptionSignature();
        s.setIdReception(id);
        s.setSignataire(moi);
        s.setNom(a.nom());
        s.setPresident(a.president());
        s.setSigneLe(maintenant());
        signatures.saveAndFlush(s);
        if (attendus(c, x).stream().allMatch(y -> y.signeLe() != null)) {
            x.setEtat(ReceptionCommande.SIGNE);
            x.setSigneLe(maintenant());
            produirePv(x, b, c);
            receptions.save(x);
            String corps = "La réception " + (ReceptionCommande.DEFINITIVE.equals(x.getType()) ? "définitive" : "provisoire") + " du bon de commande n° "
                    + b.getNumero() + " « " + b.getObjet() + " » : " + libelle(x.getDecision()) + (x.getMotif() == null ? "" : " — " + x.getMotif()) + ".";
            Offre o = offres.findById(b.getIdOffre()).orElse(null);
            if (o != null) {
                CompteCandidat cc = candidats.findById(o.getIdCandidat()).orElse(null);
                notifications.emettreCandidat(TypeNotification.RECEPTION_PRONONCEE, o.getIdCandidat(), cc == null ? null : cc.getEmail(),
                        c.getIdDmc().intValue(), TypeObjet.PROCEDURE, "Réception du bon de commande n° " + b.getNumero(), corps
                                + " Le procès-verbal est disponible dans votre espace.");
            }
            ceremonies.notifierPrmp(c.getIdDmc(), TypeNotification.RECEPTION_PRONONCEE, "PV de réception signé", corps);
            evaluation.tracerAttribution(c.getIdDmc(), "RECEPTION", "Lot " + c.getLot() + " : " + corps);
        }
        return dto(x, b, c);
    }

    // ------------------------------------------------------------------ lecture

    /** Les réceptions d'un bon : PRMP et UGPM de la fiche, Commission, Administrateur, membres de la commission de réception. */
    @Transactional(readOnly = true)
    public ReceptionCommandeDto.DuBon duBon(Long idBon) {
        BonCommande b = bon(idBon);
        ContratVigueur c = contrat(b.getIdContrat());
        exigerLecteur(c);
        return duBon(b, c);
    }

    @Transactional(readOnly = true)
    public ReceptionCommandeDto lire(Long id) {
        ReceptionCommande x = reception(id);
        BonCommande b = bon(x.getIdBon());
        ContratVigueur c = contrat(b.getIdContrat());
        exigerLecteur(c);
        return dto(x, b, c);
    }

    /** Le PV (PDF, ou Word) : lecteurs ; 404 avant son établissement. */
    @Transactional(readOnly = true)
    public byte[] pv(Long id, boolean docx) {
        ReceptionCommande x = reception(id);
        exigerLecteur(contrat(bon(x.getIdBon()).getIdContrat()));
        return document(x, docx);
    }

    /** Les réceptions où le membre connecté siège : à établir (président), à signer. */
    @Transactional(readOnly = true)
    public List<ReceptionCommandeDto.Mienne> miennes() {
        String ref = CurrentUser.ref().orElse(null);
        if (ref == null || !TypeActeur.MEMBRE_CAO.name().equals(CurrentUser.acteurType().orElse(null))) {
            throw new AccessDeniedException("Les réceptions à traiter se lisent par les membres d'une commission de réception.");
        }
        List<ReceptionCommandeDto.Mienne> out = new ArrayList<>();
        for (CommissionReceptionMembre m : membres.findByIdCompte(ref)) {
            ContratVigueur c = contrats.findById(m.getIdContrat()).orElse(null);
            if (c == null) {
                continue;
            }
            for (BonCommande b : bons.findByIdContratOrderByNumeroAsc(c.getId())) {
                for (ReceptionCommande x : receptions.findByIdBonOrderByIdAsc(b.getId())) {
                    if (ReceptionCommande.SIGNE.equals(x.getEtat())) {
                        continue;
                    }
                    boolean aSigner = ReceptionCommande.PV_ETABLI.equals(x.getEtat()) && signatures.findById(cle(x.getId(), ref)).isEmpty();
                    Offre o = offres.findById(b.getIdOffre()).orElse(null);
                    out.add(new ReceptionCommandeDto.Mienne(dto(x, b, c), b.getObjet(), o == null ? null : o.getRaisonSociale(), m.isPresident(),
                            m.isPresident() && !ReceptionCommande.SIGNE.equals(x.getEtat()), aSigner));
                }
            }
        }
        return out;
    }

    /** Les réceptions de son bon, pour le titulaire ; 404 hors de ses bons. */
    @Transactional(readOnly = true)
    public ReceptionCommandeDto.DuBon duBonDuTitulaire(String idCandidat, Long idBon) {
        BonCommande b = bonDuTitulaire(idCandidat, idBon);
        return duBon(b, contrat(b.getIdContrat()));
    }

    /** Le PV signé, pour le titulaire ; 404 avant la signature. */
    @Transactional(readOnly = true)
    public byte[] pvDuTitulaire(String idCandidat, Long idBon, Long id, boolean docx) {
        bonDuTitulaire(idCandidat, idBon);
        ReceptionCommande x = reception(id);
        if (!x.getIdBon().equals(idBon) || !ReceptionCommande.SIGNE.equals(x.getEtat())) {
            throw new ResourceNotFoundException("Le procès-verbal n'est pas disponible.");
        }
        return document(x, docx);
    }

    // ------------------------------------------------------------------ dérivations

    /** L'état de réception d'un bon, depuis ses réceptions dans l'ordre. */
    static String etatReception(BonCommande b, List<ReceptionCommande> rs, Integer garantie) {
        if (BonCommande.ANNULE.equals(b.getEtat())) {
            return "ANNULE";
        }
        if (rs.isEmpty()) {
            return "A_LIVRER";
        }
        ReceptionCommande d = rs.get(rs.size() - 1);
        boolean signe = ReceptionCommande.SIGNE.equals(d.getEtat());
        if (ReceptionCommande.DEFINITIVE.equals(d.getType())) {
            if (!signe) {
                return "EN_RECEPTION_DEFINITIVE";
            }
            return ReceptionCommande.RECEPTION.equals(d.getDecision()) ? "RECU_DEFINITIF" : "RECU_PROVISOIRE";
        }
        if (!signe) {
            return "EN_RECEPTION";
        }
        if (ReceptionCommande.AJOURNEMENT.equals(d.getDecision())) {
            return "AJOURNE";
        }
        if (ReceptionCommande.REJET.equals(d.getDecision())) {
            return "REJETE";
        }
        return garantie == null || garantie <= 0 ? "RECU_DEFINITIF" : "RECU_PROVISOIRE";
    }

    /** Le terme du délai de garantie : la réception provisoire prononcée + la garantie. */
    static LocalDate garantieJusquau(List<ReceptionCommande> rs, Integer garantie) {
        if (garantie == null || garantie <= 0) {
            return null;
        }
        return rs.stream().filter(x -> ReceptionCommande.PROVISOIRE.equals(x.getType()) && ReceptionCommande.SIGNE.equals(x.getEtat())
                && acceptee(x.getDecision())).map(ReceptionCommande::getDateReception).max(Comparator.naturalOrder()).map(d -> d.plusMonths(garantie))
                .orElse(null);
    }

    /** Le terme du délai de livraison : la notification du bon (son émission sur la plateforme) + le délai, lu en jours, semaines ou mois. */
    static LocalDate echeanceLivraison(BonCommande b) {
        Matcher m = DELAI.matcher(Objects.toString(b.getDelaiLivraison(), ""));
        if (!m.find()) {
            return null;
        }
        int n = Integer.parseInt(m.group(1));
        LocalDate notification = b.getEmisLe().toLocalDate();
        String u = m.group(2).toLowerCase(Locale.ROOT);
        return u.startsWith("mois") ? notification.plusMonths(n) : u.startsWith("semaine") ? notification.plusWeeks(n) : notification.plusDays(n);
    }

    /** La pénalité indicative : montant × taux (‰) × jours, plafonnée au pourcentage du montant ; nulle sans retard. */
    static BigDecimal penalite(BigDecimal montant, BigDecimal tauxPourMille, int jours, BigDecimal plafondPourcent) {
        if (jours <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal p = montant.multiply(tauxPourMille).multiply(BigDecimal.valueOf(jours)).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
        BigDecimal max = plafondPourcent == null ? null : montant.multiply(plafondPourcent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return max != null && p.compareTo(max) > 0 ? max : p;
    }

    private static boolean acceptee(String decision) {
        return ReceptionCommande.RECEPTION.equals(decision) || ReceptionCommande.REFACTION.equals(decision);
    }

    private Integer garantieMois(ContratVigueur c) {
        String code = ContratVigueur.CONTRAT_CADRE.equals(c.getTypeMarche()) ? "B09-GP-03" : "B09-DG-01";
        BigDecimal n = ControlesFicheMarche.nombre(ContratVigueurService.valeur(fiches.etatValide(c.getIdDmc()).orElse(null), code, c.getLot()));
        return n == null || n.signum() <= 0 ? null : n.intValue();
    }

    /** Contrat-cadre dont les pénalités sont « Non applicables » ({@code B07-PE-01}) : aucune pénalité. */
    private boolean penaliteApplicable(ContratVigueur c) {
        if (!ContratVigueur.CONTRAT_CADRE.equals(c.getTypeMarche())) {
            return true;
        }
        String v = ContratVigueurService.valeur(fiches.etatValide(c.getIdDmc()).orElse(null), "B07-PE-01", c.getLot());
        return v == null || !v.toLowerCase(Locale.FRENCH).startsWith("non applicable");
    }

    /** Le plafond des pénalités, en % : {@code B09-PR-02} (marché à commandes, s'il est saisi), sinon le plafond légal de la catégorie. */
    private BigDecimal plafond(ContratVigueur c) {
        FicheMarcheService.EtatVersion v = fiches.etatValide(c.getIdDmc()).orElse(null);
        BigDecimal saisi = ContratVigueur.A_COMMANDE.equals(c.getTypeMarche()) ? ControlesFicheMarche.nombre(ContratVigueurService.valeur(v, "B09-PR-02",
                c.getLot())) : null;
        return saisi != null ? saisi : ControlesFicheMarche.plafondPenalites(v == null ? null : v.categorie());
    }

    private String penaliteNote(ContratVigueur c, ReceptionCommande x) {
        if (ReceptionCommande.DEFINITIVE.equals(x.getType()) || x.getDecision() == null) {
            return null;
        }
        if (!penaliteApplicable(c)) {
            return "Pénalités non applicables aux marchés subséquents (contrat-cadre, B07-PE-01).";
        }
        if (x.getJoursRetard() == null) {
            return "Le délai du bon ne se lit pas en jours, semaines ou mois : le retard n'est pas calculé.";
        }
        if (!acceptee(x.getDecision())) {
            return "Livraison non acceptée : pas de pénalité calculée.";
        }
        return "À titre indicatif, jamais retenue : " + x.getTauxPenalite().stripTrailingZeros().toPlainString() + " ‰ du montant du bon par jour de "
                + "retard, plafonnée à " + plafond(c).stripTrailingZeros().toPlainString() + " %.";
    }

    // ------------------------------------------------------------------ signataires et droits

    /** Les signataires attendus : chaque membre, et la PRMP quand elle préside. */
    private List<ReceptionCommandeDto.Attendu> attendus(ContratVigueur c, ReceptionCommande x) {
        List<ReceptionCommandeDto.Attendu> out = new ArrayList<>();
        Map<String, ReceptionSignature> faites = new java.util.HashMap<>();
        signatures.findByIdReceptionOrderBySigneLeAsc(x.getId()).forEach(s -> faites.put(s.getSignataire(), s));
        CommissionReception cr = commissions.findById(c.getId()).orElse(null);
        if (cr == null) {
            return out;
        }
        if (cr.isPresideeParPrmp()) {
            ReceptionSignature s = faites.get(PRMP);
            out.add(new ReceptionCommandeDto.Attendu(PRMP, s != null ? s.getNom() : "La PRMP" + (plan(c).get("PRMP") == null ? "" : ", " + plan(c).get("PRMP")),
                    true, s == null ? null : s.getSigneLe()));
        }
        for (CommissionReceptionMembre m : membres.findByIdContratOrderByRangAscIdAsc(c.getId())) {
            ReceptionSignature s = faites.get(m.getIdCompte());
            out.add(new ReceptionCommandeDto.Attendu(m.getIdCompte(), m.getNom().toUpperCase(Locale.FRENCH) + " " + m.getPrenom(), m.isPresident(),
                    s == null ? null : s.getSigneLe()));
        }
        return out;
    }

    /** Le signataire connecté : {@code PRMP} (la PRMP de la fiche, quand elle préside), ou le compte d'un membre. */
    private Optional<String> signataireConnecte(ContratVigueur c) {
        CommissionReception cr = commissions.findById(c.getId()).orElse(null);
        if (cr == null) {
            return Optional.empty();
        }
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p == ProfilUtilisateur.PRMP && cr.isPresideeParPrmp()) {
            fiches.controlerLecture(c.getIdDmc());
            return Optional.of(PRMP);
        }
        String ref = CurrentUser.ref().orElse(null);
        if (ref != null && TypeActeur.MEMBRE_CAO.name().equals(CurrentUser.acteurType().orElse(null))
                && membres.findByIdContratOrderByRangAscIdAsc(c.getId()).stream().anyMatch(m -> ref.equals(m.getIdCompte()))) {
            return Optional.of(ref);
        }
        return Optional.empty();
    }

    /** Le président : la PRMP de la fiche si elle préside, sinon le membre président. 409 {@code COMMISSION_NON_DESIGNEE}. */
    private void exigerPresident(ContratVigueur c) {
        CommissionReception cr = commissions.findById(c.getId())
                .orElseThrow(() -> new BusinessRuleException("La commission de réception n'est pas désignée.", "COMMISSION_NON_DESIGNEE"));
        Optional<String> moi = signataireConnecte(c);
        boolean president = moi.isPresent() && (cr.isPresideeParPrmp() ? PRMP.equals(moi.get())
                : membres.findByIdContratOrderByRangAscIdAsc(c.getId()).stream().anyMatch(m -> m.isPresident() && m.getIdCompte().equals(moi.get())));
        if (!president) {
            throw new AccessDeniedException("Le PV de réception s'établit par le président de la commission de réception.");
        }
    }

    private void exigerLecteur(ContratVigueur c) {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p == ProfilUtilisateur.MEMBRE_CAO) {
            String ref = CurrentUser.ref().orElse(null);
            if (ref == null || membres.findByIdContratOrderByRangAscIdAsc(c.getId()).stream().noneMatch(m -> ref.equals(m.getIdCompte()))) {
                throw new AccessDeniedException("Les réceptions se lisent par les membres de leur commission.");
            }
            return;
        }
        exigerLecteurInterne(c.getIdDmc());
    }

    private void exigerLecteurInterne(Long idDmc) {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p == null || p == ProfilUtilisateur.CANDIDAT || p == ProfilUtilisateur.DEPOSITAIRE || p == ProfilUtilisateur.MEMBRE_CAO) {
            throw new AccessDeniedException("Les réceptions se lisent dans l'espace de l'autorité contractante et de la Commission.");
        }
        if (p == ProfilUtilisateur.PRMP || p == ProfilUtilisateur.UGPM) {
            fiches.controlerLecture(idDmc);
        }
    }

    private void notifierSignataire(ContratVigueur c, String signataire, String titre, String corps) {
        if (PRMP.equals(signataire)) {
            ceremonies.notifierPrmp(c.getIdDmc(), TypeNotification.RECEPTION_A_SIGNER, titre, corps);
            return;
        }
        CompteCao k = comptesCao.findById(signataire).orElse(null);
        notifications.emettreMembreCao(TypeNotification.RECEPTION_A_SIGNER, signataire, k == null ? null : k.getEmail(), c.getIdDmc().intValue(),
                TypeObjet.PROCEDURE, titre, corps);
    }

    // ------------------------------------------------------------------ vues et documents

    private ReceptionCommandeDto.DuBon duBon(BonCommande b, ContratVigueur c) {
        List<ReceptionCommande> rs = receptions.findByIdBonOrderByIdAsc(b.getId());
        Integer garantie = garantieMois(c);
        String etat = etatReception(b, rs, garantie);
        LocalDate jusquau = garantieJusquau(rs, garantie);
        List<String> alertes = new ArrayList<>();
        if ("RECU_PROVISOIRE".equals(etat) && jusquau != null && !aujourdhui().isBefore(jusquau)) {
            alertes.add(RECEPTION_DEFINITIVE_A_PRONONCER);
        }
        Offre o = offres.findById(b.getIdOffre()).orElse(null);
        return new ReceptionCommandeDto.DuBon(b.getId(), b.getNumero(), b.getObjet(), o == null ? null : o.getRaisonSociale(), etat, garantie, jusquau, alertes,
                rs.stream().map(x -> dto(x, b, c)).toList());
    }

    private ReceptionCommandeDto dto(ReceptionCommande x, BonCommande b, ContratVigueur c) {
        BigDecimal recu = x.getDecision() == null || !acceptee(x.getDecision()) ? null
                : x.getMontantRefaction() == null ? b.getMontantHt() : b.getMontantHt().subtract(x.getMontantRefaction());
        boolean provisoire = ReceptionCommande.PROVISOIRE.equals(x.getType());
        return new ReceptionCommandeDto(x.getId(), b.getId(), b.getNumero(), c.getId(), c.getIdDmc(), c.getLot(), x.getType(), x.getEtat(), x.getDateLivraison(),
                x.getLivraisonObservations(), x.getDeclareeLe(), x.getDeclareePar(), x.getDecision(), x.getDateReception(), x.getMotif(), x.getReserves(),
                b.getMontantHt(), x.getMontantRefaction(), recu, provisoire ? echeanceLivraison(b) : null, x.getJoursRetard(), x.getTauxPenalite(),
                provisoire ? plafond(c) : null, x.getPenaliteIndicative(), penaliteNote(c, x), x.getEtabliLe(), x.getEtabliPar(), x.getPvPdf() != null,
                attendus(c, x), x.getSigneLe());
    }

    private void produirePv(ReceptionCommande x, BonCommande b, ContratVigueur c) {
        Map<String, String> plan = plan(c);
        boolean definitive = ReceptionCommande.DEFINITIVE.equals(x.getType());
        CommissionReception cr = commissions.findById(c.getId()).orElseThrow();
        Offre o = offres.findById(b.getIdOffre()).orElse(null);
        List<DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, Objects.toString(plan.get("ENTITE"), "L'Autorité contractante")));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "PROCÈS-VERBAL DE RÉCEPTION " + (definitive ? "DÉFINITIVE" : "PROVISOIRE")));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "Bon de commande n° " + b.getNumero() + " — « " + b.getObjet() + " »"));
        para(el, "Procédure " + c.getIdDmc() + ", lot " + c.getLot() + " (" + (ContratVigueur.CONTRAT_CADRE.equals(c.getTypeMarche()) ? "contrat-cadre"
                : "marché à commandes") + "). Titulaire : " + (o == null ? b.getIdOffre() : o.getRaisonSociale()) + ". Montant du bon : "
                + FormulairesEnLigne.lisible(b.getMontantHt()) + " Ariary hors taxes.");
        para(el, "La commission de réception désignée par décision n° " + cr.getDecisionReference() + " du " + cr.getDecisionDate().format(JOUR)
                + " (loi n° 2016-055, art. 13) s'est réunie le " + x.getDateReception().format(JOUR) + (definitive ? ", au terme du délai de garantie."
                        : " pour constater la livraison déclarée le " + x.getDateLivraison().format(JOUR) + "."));
        if (x.getLivraisonObservations() != null) {
            para(el, "Observations à la livraison : " + x.getLivraisonObservations());
        }
        DocumentLibre.Element titre = new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Décision de la commission");
        el.add(titre);
        para(el, switch (x.getDecision()) {
            case ReceptionCommande.RECEPTION -> definitive ? "La commission prononce la réception définitive des prestations."
                    : "La commission prononce la réception des fournitures livrées.";
            case ReceptionCommande.AJOURNEMENT -> "La commission ajourne la réception : " + x.getMotif() + ". Le titulaire est invité à présenter à "
                    + "nouveau les fournitures (CCAG fournitures, art. 22).";
            case ReceptionCommande.REFACTION -> "La commission prononce la réception avec réfaction de " + FormulairesEnLigne.lisible(x.getMontantRefaction())
                    + " Ariary hors taxes : " + x.getMotif() + ".";
            default -> "La commission rejette les fournitures livrées : " + x.getMotif() + ". Le titulaire les remplace (CCAG fournitures, art. 22).";
        });
        if (x.getReserves() != null) {
            para(el, "Réserves : " + x.getReserves());
        }
        if (!definitive && x.getJoursRetard() != null) {
            LocalDate echeance = echeanceLivraison(b);
            para(el, "Délai de livraison : " + b.getDelaiLivraison() + ", soit au plus tard le " + echeance.format(JOUR) + ". "
                    + (x.getJoursRetard() > 0 ? "Retard constaté : " + x.getJoursRetard() + " jour(s)." : "Livraison dans le délai."));
            if (x.getPenaliteIndicative() != null && x.getPenaliteIndicative().signum() > 0) {
                para(el, "Pénalité de retard, à titre indicatif : " + FormulairesEnLigne.lisible(x.getPenaliteIndicative()) + " Ariary ("
                        + penaliteNote(c, x) + ") La retenue éventuelle relève de la décision de la PRMP.");
            }
        }
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Signatures"));
        for (ReceptionCommandeDto.Attendu a : attendus(c, x)) {
            para(el, "– " + a.nom() + (a.president() ? " (président)" : "") + " : " + (a.signeLe() == null ? "en attente de signature"
                    : "signé électroniquement sur la plateforme le " + a.signeLe().format(HEURE)) + ".");
        }
        for (GenerateurDocumentsFiche.Fichier f : generateur.generer(new DocumentLibre("PV_RECEPTION", null, el, "Procédure " + c.getIdDmc() + ", lot "
                + c.getLot() + " — PV de réception du bon de commande n° " + b.getNumero()))) {
            if ("pdf".equals(f.extension())) {
                x.setPvPdf(f.contenu());
            } else if ("docx".equals(f.extension())) {
                x.setPvDocx(f.contenu());
            }
        }
    }

    private static byte[] document(ReceptionCommande x, boolean docx) {
        byte[] b = docx ? x.getPvDocx() : x.getPvPdf();
        if (b == null) {
            throw new ResourceNotFoundException("Le procès-verbal n'est pas établi.");
        }
        return b;
    }

    private ReceptionCommandeDto.Commission commissionDto(Long idContrat) {
        CommissionReception cr = commissions.findById(idContrat).orElse(null);
        if (cr == null) {
            return new ReceptionCommandeDto.Commission(idContrat, null, null, true, List.of(), null, null);
        }
        List<ReceptionCommandeDto.Membre> ms = membres.findByIdContratOrderByRangAscIdAsc(idContrat).stream().map(m -> new ReceptionCommandeDto.Membre(m.getId(),
                m.getNom(), m.getPrenom(), m.getEmail(), m.getFonction(), m.isPresident(), m.getIdCompte(), comptesCao.findById(m.getIdCompte())
                        .map(k -> CompteCao.ACTIF.equals(k.getEtat())).orElse(false))).toList();
        return new ReceptionCommandeDto.Commission(idContrat, cr.getDecisionReference(), cr.getDecisionDate(), cr.isPresideeParPrmp(), ms, cr.getMajLe(),
                cr.getMajPar());
    }

    private static String libelle(String decision) {
        return switch (decision) {
            case ReceptionCommande.RECEPTION -> "réception prononcée";
            case ReceptionCommande.AJOURNEMENT -> "réception ajournée";
            case ReceptionCommande.REFACTION -> "réception avec réfaction";
            default -> "fournitures rejetées";
        };
    }

    // ------------------------------------------------------------------ outils

    private BonCommande bonDuTitulaire(String idCandidat, Long idBon) {
        BonCommande b = bon(idBon);
        if (!offres.findById(b.getIdOffre()).map(o -> idCandidat.equals(o.getIdCandidat())).orElse(false)) {
            throw new ResourceNotFoundException("Bon de commande introuvable : " + idBon + ".");
        }
        return b;
    }

    private BonCommande bon(Long id) {
        return bons.findById(id).orElseThrow(() -> new ResourceNotFoundException("Bon de commande introuvable : " + id + "."));
    }

    private ReceptionCommande reception(Long id) {
        return receptions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Réception introuvable : " + id + "."));
    }

    private ContratVigueur contrat(Long id) {
        return contrats.findById(id).orElseThrow(() -> new ResourceNotFoundException("Contrat introuvable : " + id + "."));
    }

    private Map<String, String> plan(ContratVigueur c) {
        DossierMec dmc = dmcRepository.findById(c.getIdDmc()).orElse(null);
        return dmc == null || dmc.getIdDetail() == null ? Map.of() : valeursPpm.lire(dmc.getIdDetail()).valeurs();
    }

    private static ReceptionSignature.Cle cle(Long idReception, String signataire) {
        ReceptionSignature.Cle k = new ReceptionSignature.Cle();
        k.setIdReception(idReception);
        k.setSignataire(signataire);
        return k;
    }

    private static String nettoyer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void para(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, t));
    }

    private static String acteur() {
        return CurrentUser.ref().or(CurrentUser::login).orElse(null);
    }

    private LocalDate aujourdhui() {
        return LocalDate.now(horloge);
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).withNano(0);
    }
}
