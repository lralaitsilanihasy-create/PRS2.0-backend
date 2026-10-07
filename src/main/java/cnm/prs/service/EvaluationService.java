package cnm.prs.service;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.EvaluationDto;
import cnm.prs.dto.OffreDto;
import cnm.prs.dto.SeanceDto;
import cnm.prs.entity.CaoMembre;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Evaluation;
import cnm.prs.entity.EvaluationDeclaration;
import cnm.prs.entity.EvaluationDecision;
import cnm.prs.entity.EvaluationDemande;
import cnm.prs.entity.EvaluationEtape;
import cnm.prs.entity.EvaluationJournal;
import cnm.prs.entity.Offre;
import cnm.prs.entity.Seance;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.AccesReserveException;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.PayloadTropVolumineuxException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CaoMembreRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.EvaluationDecisionRepository;
import cnm.prs.repository.EvaluationDeclarationRepository;
import cnm.prs.repository.EvaluationDemandeRepository;
import cnm.prs.repository.EvaluationEtapeRepository;
import cnm.prs.repository.EvaluationJournalRepository;
import cnm.prs.repository.EvaluationRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.security.CurrentUser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ <strong>L'évaluation des offres, lot 1, tranche 1a</strong> (demande front du 2026-10-07, §B1, §B2, §B7 ; V76). Une fois le PV
 * d'ouverture signé, le responsable de la procédure ouvre l'évaluation ; chaque membre de la CAO signe sa déclaration préalable
 * (P6) ; l'examen préliminaire (étape 2 du guide) se fait offre par offre sur une grille <strong>pré-remplie</strong> depuis la
 * lecture de la séance (P2 : un constat, jamais une décision) ; tout membre déclaré sans conflit saisit, le <strong>président</strong>
 * arrête l'étape du lot, ce qui fige ses décisions (arbitrage du pilote du 07/10, Q1). La PRMP demande des précisions (art. 35-VI), le
 * candidat y répond en ligne. Chaque décision est motivée, tracée, jamais effacée (P3).
 * <p>
 * Arbitrages du 07/10 : la CAO décide toujours, même d'une offre altérée ou sans frais de dossier réglés (Q6, Q7) — la vérification est
 * pré-remplie « non satisfaite ».
 * <p>
 * ⚠️ <strong>Tranche 1b</strong> (§B3 ; V77) : les corrections arithmétiques proposées depuis le bordereau scellé, que la CAO retient ou
 * non ; le montant évalué <strong>hors taxes</strong> (Q3) = prix lu + corrections retenues − rabais + ajustement de préférence +
 * critères du DAO ; le refus d'une correction par le candidat, constaté par la CAO (Q2), écarte l'offre ; le classement par montant
 * évalué croissant, l'égalité en tête départagée par la CAO avec un motif (Q5) avant l'arrêt de l'étape.
 * <p>
 * ⚠️ <strong>Tranche 1c</strong> (§B4, §B5) : les indicateurs de prix (écarts à l'estimation et à la moyenne) ; aucune offre rejetée
 * pour prix anormal sans justification demandée par la PRMP, puis reçue ou expirée (art. 48) ; la post-qualification du premier classé,
 * puis du suivant s'il échoue, sur les seuls critères de la fiche (art. 20-II) ; la proposition d'attribution du lot, ou l'infructuosité.
 */
@Service
@Transactional
public class EvaluationService {

    /** Les vérifications de l'examen préliminaire, dans l'ordre de la grille (§B2). */
    public static final Map<String, String> VERIFICATIONS = ordre(
            "AE_PRIX", "Acte d'engagement présent, avec le prix (art. 46)",
            "GARANTIE", "Garantie de soumission, si le DAO l'exige (art. 46)",
            "OFFRE_UNIQUE", "Une seule offre par candidat et par lot, seul ou en groupement (art. 22-V, 43)",
            "EXCLUSION", "Aucun cas d'exclusion (art. 21)",
            "POUVOIRS", "Signataire habilité, pouvoirs joints",
            "PIECES", "Pièces exigées par le DAO",
            "CONFORMITE_TECHNIQUE", "Conformité pour l'essentiel aux spécifications",
            "INTEGRITE", "Intégrité de l'offre",
            "FRAIS_DOSSIER", "Frais de dossier réglés");
    /** Les qualifications d'une offre écartée (article 1 de la loi, repris par le guide, §2.3). */
    public static final Set<String> QUALIFICATIONS = Set.of("IRRECEVABLE", "NON_CONFORME", "INAPPROPRIEE", "INACCEPTABLE");
    /** Délai de réponse aux demandes de la PRMP : fournitures, puis travaux. */
    static final List<String> CHAMPS_DELAI = List.of("B06-EP-01", "B06-RC-01");

    private final EvaluationRepository evaluations;
    private final EvaluationDeclarationRepository declarations;
    private final EvaluationEtapeRepository etapes;
    private final EvaluationDecisionRepository decisions;
    private final EvaluationDemandeRepository demandes;
    private final EvaluationJournalRepository journal;
    private final cnm.prs.repository.EvaluationDepartageRepository departages;
    private final SeanceService seance;
    private final ParametresInternesService internes;
    private final CaoMembreRepository caoMembres;
    private final FicheMarcheService fiches;
    private final ProceduresEnLigneService procedures;
    private final CeremonieService ceremonies;
    private final NotificationService notifications;
    private final CompteCandidatRepository candidats;
    private final OffreRepository offres;
    private final DossierMecRepository dmcRepository;
    private final ParametreService parametres;
    private final ObjectMapper mapper;
    private final Clock horloge;
    private final cnm.prs.repository.LotRepository lotRepository;
    private final ValeursPpmService valeursPpm;

    public EvaluationService(EvaluationRepository evaluations, EvaluationDeclarationRepository declarations, EvaluationEtapeRepository etapes,
            EvaluationDecisionRepository decisions, EvaluationDemandeRepository demandes, EvaluationJournalRepository journal,
            cnm.prs.repository.EvaluationDepartageRepository departages, SeanceService seance, ParametresInternesService internes, CaoMembreRepository caoMembres, FicheMarcheService fiches,
            ProceduresEnLigneService procedures, CeremonieService ceremonies, NotificationService notifications, CompteCandidatRepository candidats, OffreRepository offres,
            DossierMecRepository dmcRepository, ParametreService parametres, ObjectMapper mapper, Clock horloge,
            cnm.prs.repository.LotRepository lotRepository, ValeursPpmService valeursPpm) {
        this.evaluations = evaluations;
        this.declarations = declarations;
        this.etapes = etapes;
        this.decisions = decisions;
        this.demandes = demandes;
        this.journal = journal;
        this.departages = departages;
        this.seance = seance;
        this.internes = internes;
        this.caoMembres = caoMembres;
        this.fiches = fiches;
        this.procedures = procedures;
        this.ceremonies = ceremonies;
        this.notifications = notifications;
        this.candidats = candidats;
        this.offres = offres;
        this.dmcRepository = dmcRepository;
        this.parametres = parametres;
        this.mapper = mapper;
        this.horloge = horloge;
        this.lotRepository = lotRepository;
        this.valeursPpm = valeursPpm;
    }

    // ------------------------------------------------------------------ §B1 l'évaluation

    @Transactional(readOnly = true)
    public EvaluationDto lire(Long idDmc) {
        exigerLecteur(idDmc);
        return dto(idDmc, exigerEvaluation(idDmc));
    }

    /** Ouvre l'évaluation : responsable (titulaire) ; 409 {@code SEANCE_NON_CLOSE}, {@code EVALUATION_DEJA_OUVERTE}. */
    public EvaluationDto ouvrir(Long idDmc) {
        exigerDmc(idDmc);
        if (CurrentUser.profil().isEmpty() || !internes.estTitulaire(idDmc)) {
            throw new AccessDeniedException("L'évaluation s'ouvre par le responsable de la procédure.");
        }
        if (evaluations.existsById(idDmc)) {
            throw new BusinessRuleException("L'évaluation de cette procédure est déjà ouverte.", "EVALUATION_DEJA_OUVERTE");
        }
        if (!Seance.CLOSE.equals(seance.etat(idDmc))) {
            throw new BusinessRuleException("L'évaluation s'ouvre une fois le PV d'ouverture des plis signé.", "SEANCE_NON_CLOSE");
        }
        Evaluation e = evaluations.save(new Evaluation(idDmc, Evaluation.EN_COURS, maintenant(), acteur()));
        tracer(idDmc, "OUVERTURE", "Évaluation ouverte");
        String titre = "Évaluation des offres ouverte";
        String corps = "L'évaluation des offres de la procédure " + idDmc + " est ouverte : signez votre déclaration préalable, puis "
                + "procédez à l'examen préliminaire.";
        internes.membresCao(idDmc).forEach(k -> internes.notifierMembre(idDmc, k, TypeNotification.EVALUATION_OUVERTE, titre, corps));
        ceremonies.notifierPrmp(idDmc, TypeNotification.EVALUATION_OUVERTE, titre,
                "L'évaluation des offres de la procédure " + idDmc + " est ouverte par la commission d'appel d'offres.");
        return dto(idDmc, e);
    }

    /** P6 : la déclaration préalable du membre appelant ; 409 {@code DEJA_DECLARE}. */
    public EvaluationDto declarer(Long idDmc, EvaluationDto.DeclarationRequest d) {
        Evaluation e = exigerEvaluation(idDmc);
        String k = membreAppelant(idDmc);
        if (k == null) {
            throw new AccessDeniedException("La déclaration préalable se signe par les membres de la commission d'appel d'offres.");
        }
        if (declarations.findByIdDmcAndIm(idDmc, k).isPresent()) {
            throw new BusinessRuleException("Vous avez déjà signé votre déclaration : elle ne se reprend pas.", "DEJA_DECLARE");
        }
        boolean conflit = d != null && Boolean.TRUE.equals(d.conflit());
        String precision = d == null ? null : nettoyer(d.precision());
        declarations.save(new EvaluationDeclaration(null, idDmc, k, maintenant(), conflit, precision));
        tracer(idDmc, "DECLARATION", internes.nomMembre(k) + (conflit ? " déclare un conflit d'intérêts" + (precision == null ? "" : " : "
                + precision) : " déclare l'absence de conflit d'intérêts et s'engage à la confidentialité"));
        return dto(idDmc, e);
    }

    /**
     * Arrête l'étape d'un lot : président de la CAO (déclaré, sans conflit) ; 400 {@code ETAPE_INCONNUE} ; 409 {@code EVALUATION_CLOSE},
     * {@code ETAPE_ARRETEE}, {@code ETAPE_PRECEDENTE_OUVERTE}, {@code ETAPE_INCOMPLETE} (détails : les offres sans décision),
     * {@code ETAPE_NON_DISPONIBLE} (étapes 3 à 5 : tranche suivante).
     */
    public EvaluationDto arreter(Long idDmc, Integer lot, String etape, EvaluationDto.Arret a) {
        Evaluation e = exigerEnCours(idDmc);
        String k = exigerPresident(idDmc);
        String et = exigerEtape(etape);
        Map<Integer, List<SeanceDto.OffreLue>> parLot = parLot(idDmc);
        if (!parLot.containsKey(lot)) {
            throw new ResourceNotFoundException("Lot introuvable dans l'évaluation : " + lot + ".");
        }
        Map<String, EvaluationEtape> arretees = arretees(idDmc, lot);
        if (arretees.containsKey(et)) {
            throw new BusinessRuleException("Cette étape est déjà arrêtée.", "ETAPE_ARRETEE");
        }
        int rang = EvaluationEtape.ORDRE.indexOf(et);
        for (int i = 0; i < rang; i++) {
            if (!arretees.containsKey(EvaluationEtape.ORDRE.get(i))) {
                throw new BusinessRuleException("L'étape " + EvaluationEtape.ORDRE.get(i) + " du lot " + lot + " n'est pas arrêtée.",
                        "ETAPE_PRECEDENTE_OUVERTE");
            }
        }
        if (EvaluationEtape.CONFORMITE.equals(et)) {
            exigerDecisions(idDmc, parLot.get(lot), et);
        } else if (EvaluationEtape.EVALUATION.equals(et)) {
            // ⚠️ Tranche 1b — chaque offre retenue à l'examen préliminaire a son montant évalué ; pas d'égalité en tête sans départage.
            exigerDecisions(idDmc, retenuesConformite(idDmc, parLot.get(lot)), et);
            List<Integer> egales = classer(idDmc, lot, parLot.get(lot)).entrySet().stream()
                    .filter(x -> x.getValue().rang() == 1 && x.getValue().exAequo()).map(x -> numero(parLot.get(lot), x.getKey())).sorted().toList();
            if (!egales.isEmpty()) {
                throw new BusinessRuleException("Des offres sont classées premières à égalité de montant évalué : départagez-les avec un motif.",
                        "EGALITE_A_DEPARTAGER", null, Map.of("offres", egales));
            }
        } else if (EvaluationEtape.ANORMALES.equals(et)) {
            // ⚠️ Tranche 1c — chaque offre classée examinée au regard de son prix, aucune laissée « suspectée » sans décision ; puis
            // le reclassement (une offre rejetée en sort) ne laisse pas d'égalité en tête.
            List<SeanceDto.OffreLue> classees = parLot.get(lot).stream().filter(o -> classerAvantAnormales(idDmc, lot, parLot.get(lot))
                    .containsKey(o.idOffre())).toList();
            exigerDecisions(idDmc, classees, et);
            Map<String, EvaluationDecision> anormales = enVigueur(idDmc, et);
            List<Integer> enSuspens = classees.stream().filter(o -> SUSPECTEE.equals(anormales.get(o.idOffre()).getDecision()))
                    .map(SeanceDto.OffreLue::numero).toList();
            if (!enSuspens.isEmpty()) {
                throw new BusinessRuleException("Des offres suspectées attendent leur décision (maintenue ou rejetée).", "ETAPE_INCOMPLETE", null,
                        Map.of("offres", enSuspens));
            }
            List<Integer> egales = classer(idDmc, lot, parLot.get(lot)).entrySet().stream()
                    .filter(x -> x.getValue().rang() == 1 && x.getValue().exAequo()).map(x -> numero(parLot.get(lot), x.getKey())).sorted().toList();
            if (!egales.isEmpty()) {
                throw new BusinessRuleException("Des offres sont classées premières à égalité de montant évalué : départagez-les avec un motif.",
                        "EGALITE_A_DEPARTAGER", null, Map.of("offres", egales));
            }
        } else {
            // ⚠️ Tranche 1c — la post-qualification s'arrête quand une offre est qualifiée, ou quand toutes ont échoué (infructueux).
            Tour t = tour(idDmc, lot, parLot.get(lot));
            if (t.courante() != null && t.decision() == null) {
                throw new BusinessRuleException("L'offre classée n° " + t.position() + " attend sa post-qualification.", "ETAPE_INCOMPLETE", null,
                        Map.of("offres", List.of(numero(parLot.get(lot), t.courante()))));
            }
        }
        String observation = a == null ? null : nettoyer(a.observation());
        etapes.save(new EvaluationEtape(null, idDmc, lot, et, maintenant(), k, observation, null, null, null));
        tracer(idDmc, "ARRET", "Lot " + lot + ", étape " + et + " arrêtée" + (observation == null ? "" : " : " + observation));
        return dto(idDmc, e);
    }

    /** Rouvre une étape arrêtée et les suivantes du lot : président, motif obligatoire ; 409 {@code RAPPORT_SIGNE}, {@code ETAPE_NON_ARRETEE}. */
    public EvaluationDto rouvrir(Long idDmc, Integer lot, String etape, EvaluationDto.Reouverture r) {
        Evaluation e = exigerEvaluation(idDmc);
        if (!Evaluation.EN_COURS.equals(e.getEtat())) {
            throw new BusinessRuleException("Le rapport d'évaluation est produit : les étapes ne se rouvrent plus.", "RAPPORT_SIGNE");
        }
        String k = exigerPresident(idDmc);
        String et = exigerEtape(etape);
        String motif = r == null ? null : nettoyer(r.motif());
        if (motif == null) {
            throw new BadRequestException("La réouverture d'une étape exige un motif.", "MOTIF_OBLIGATOIRE");
        }
        Map<String, EvaluationEtape> arretees = arretees(idDmc, lot);
        if (!arretees.containsKey(et)) {
            throw new BusinessRuleException("Cette étape n'est pas arrêtée.", "ETAPE_NON_ARRETEE");
        }
        LocalDateTime maintenant = maintenant();
        List<String> rouvertes = new ArrayList<>();
        for (String x : EvaluationEtape.ORDRE.subList(EvaluationEtape.ORDRE.indexOf(et), EvaluationEtape.ORDRE.size())) {
            EvaluationEtape a = arretees.get(x);
            if (a != null) {
                a.setRouverteLe(maintenant);
                a.setRouvertePar(k);
                a.setMotifReouverture(motif);
                etapes.save(a);
                rouvertes.add(x);
            }
        }
        tracer(idDmc, "REOUVERTURE", "Lot " + lot + ", étape(s) " + String.join(", ", rouvertes) + " rouverte(s) : " + motif);
        return dto(idDmc, e);
    }

    @Transactional(readOnly = true)
    public List<EvaluationDto.Journal> journal(Long idDmc) {
        exigerEvaluation(idDmc);
        if (membreAppelant(idDmc) == null && !internes.estTitulaire(idDmc) && CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException("Le journal de l'évaluation se lit par la commission, le responsable et la PRMP.");
        }
        if (CurrentUser.profil().orElse(null) == ProfilUtilisateur.PRMP) {
            fiches.controlerLecture(idDmc);
        }
        return journal.findByIdDmcOrderByDateAscIdAsc(idDmc).stream()
                .map(j -> new EvaluationDto.Journal(j.getDate(), j.getActeur(), j.getAction(), j.getDetail())).toList();
    }

    // ------------------------------------------------------------------ §B2 l'examen préliminaire

    /**
     * La décision d'examen préliminaire d'une offre : membre déclaré sans conflit ; 400 {@code DECISION_INVALIDE},
     * {@code VERIFICATION_INCONNUE}, {@code MOTIF_OBLIGATOIRE}, {@code CLAUSE_OBLIGATOIRE}, {@code QUALIFICATION_OBLIGATOIRE} ;
     * 409 {@code ETAPE_ARRETEE}. Remplace la décision en vigueur, qui reste au registre.
     */
    public EvaluationDto conformite(Long idDmc, String idOffre, EvaluationDto.ConformiteRequest c) {
        Evaluation e = exigerEnCours(idDmc);
        String k = exigerDecideur(idDmc);
        SeanceDto.OffreLue o = offreEvaluee(idDmc, idOffre);
        Integer lot = lotDe(o);
        if (arretees(idDmc, lot).containsKey(EvaluationEtape.CONFORMITE)) {
            throw new BusinessRuleException("L'examen préliminaire de ce lot est arrêté : rouvrez-le pour changer une décision.", "ETAPE_ARRETEE");
        }
        String decision = c == null || c.decision() == null ? null : c.decision().trim().toUpperCase(Locale.ROOT);
        if (!EvaluationDecision.CONFORME.equals(decision) && !EvaluationDecision.ECARTEE.equals(decision)) {
            throw new BadRequestException("La décision est CONFORME ou ECARTEE.", "DECISION_INVALIDE");
        }
        String motif = nettoyer(c.motif());
        String clause = nettoyer(c.clause());
        String qualification = c.qualification() == null ? null : c.qualification().trim().toUpperCase(Locale.ROOT);
        if (EvaluationDecision.ECARTEE.equals(decision)) {
            if (motif == null) {
                throw new BadRequestException("Écarter une offre exige un motif.", "MOTIF_OBLIGATOIRE");
            }
            if (clause == null) {
                throw new BadRequestException("Écarter une offre exige la clause du DAO visée.", "CLAUSE_OBLIGATOIRE");
            }
            if (qualification == null || !QUALIFICATIONS.contains(qualification)) {
                throw new BadRequestException("Une offre écartée est qualifiée : IRRECEVABLE, NON_CONFORME, INAPPROPRIEE ou INACCEPTABLE.",
                        "QUALIFICATION_OBLIGATOIRE");
            }
        } else {
            qualification = null;
        }
        Map<String, EvaluationDto.VerificationSaisie> saisies = new LinkedHashMap<>();
        for (EvaluationDto.VerificationSaisie v : c.verifications() == null ? List.<EvaluationDto.VerificationSaisie>of() : c.verifications()) {
            if (v == null || v.code() == null || !VERIFICATIONS.containsKey(v.code())) {
                throw new BadRequestException("Vérification inconnue : " + (v == null ? null : v.code()) + ".", "VERIFICATION_INCONNUE");
            }
            saisies.put(v.code(), v);
        }
        // La grille retenue : la saisie de la CAO, à défaut la proposition du serveur.
        Map<String, Proposition> proposees = proposer(idDmc, o);
        List<Map<String, Object>> grille = new ArrayList<>();
        for (String code : VERIFICATIONS.keySet()) {
            EvaluationDto.VerificationSaisie s = saisies.get(code);
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("code", code);
            ligne.put("satisfaite", s != null && s.satisfaite() != null ? s.satisfaite() : proposees.get(code).valeur());
            ligne.put("observation", s == null ? null : nettoyer(s.observation()));
            grille.add(ligne);
        }
        LocalDateTime maintenant = maintenant();
        EvaluationDecision avant = remplacer(o.idOffre(), EvaluationEtape.CONFORMITE, maintenant);
        decisions.save(new EvaluationDecision(null, idDmc, o.idOffre(), lot, EvaluationEtape.CONFORMITE, decision, qualification, motif, clause,
                ecrire(grille), k, maintenant, null));
        tracer(idDmc, "CONFORMITE", "Offre n° " + o.numero() + " (" + o.entreprise().raisonSociale() + ") : "
                + (avant == null ? "" : avant.getDecision() + " → ") + decision
                + (qualification == null ? "" : " (" + qualification + ")") + (motif == null ? "" : ", motif : " + motif)
                + (clause == null ? "" : ", clause : " + clause));
        return dto(idDmc, e);
    }

    /** Une proposition du serveur : vrai, faux, ou nul (sans objet, ou à examiner par la CAO), et le constat qui la fonde. */
    record Proposition(Boolean valeur, String constat) {
    }

    /** La grille pré-remplie depuis la lecture de la séance (P2 : des constats ; la CAO décide). */
    Map<String, Proposition> proposer(Long idDmc, SeanceDto.OffreLue o) {
        return proposer(idDmc, o, parLot(idDmc).getOrDefault(lotDe(o), List.of()), nifsParOffre(idDmc), attendues(idDmc), garantieExigee(idDmc));
    }

    static Map<String, Proposition> proposer(Long idDmc, SeanceDto.OffreLue o, List<SeanceDto.OffreLue> memeLot,
            Map<String, Set<String>> nifsParOffre, List<OffreDto.PieceAttendue> attendues, boolean garantieExigee) {
        Map<String, Proposition> p = new LinkedHashMap<>();
        List<String> types = o.alertes() == null ? List.of() : o.alertes().stream().map(SeanceDto.Alerte::type).toList();
        Map<String, Object> ae = o.acteEngagement();
        Object ht = ae == null ? null : ae.get("montantHt");
        Object ttc = ae == null ? null : ae.get("montantTtc");
        boolean prix = ht != null && !String.valueOf(ht).isBlank() || ttc != null && !String.valueOf(ttc).isBlank();
        p.put("AE_PRIX", new Proposition(ae != null && prix, ae == null ? "Acte d'engagement absent du manifeste."
                : prix ? "Montant lu : " + (ht == null ? "" : "HT " + ht) + (ttc == null ? "" : (ht == null ? "" : ", ") + "TTC " + ttc) + "."
                        : "Acte d'engagement sans montant."));
        if (!garantieExigee) {
            p.put("GARANTIE", new Proposition(null, "Non exigée par le DAO."));
        } else if (o.garantie() == null || !o.garantie().presente()) {
            p.put("GARANTIE", new Proposition(false, "Garantie de soumission absente."));
        } else if (types.contains("GARANTIE_INSUFFISANTE")) {
            p.put("GARANTIE", new Proposition(false, message(o, "GARANTIE_INSUFFISANTE")));
        } else {
            p.put("GARANTIE", new Proposition(true, "Garantie présente" + (o.garantie().montant() == null ? "."
                    : " : " + o.garantie().montant().stripTrailingZeros().toPlainString() + (o.garantie().monnaie() == null ? "" : " "
                            + o.garantie().monnaie()) + (o.garantie().emetteur() == null ? "" : ", " + o.garantie().emetteur()) + ".")));
        }
        List<String> doublons = new ArrayList<>();
        Set<String> nifs = nifsParOffre.getOrDefault(o.idOffre(), Set.of());
        for (SeanceDto.OffreLue autre : memeLot) {
            if (!autre.idOffre().equals(o.idOffre()) && nifsParOffre.getOrDefault(autre.idOffre(), Set.of()).stream().anyMatch(nifs::contains)) {
                doublons.add("n° " + autre.numero() + " (" + autre.entreprise().raisonSociale() + ")");
            }
        }
        p.put("OFFRE_UNIQUE", new Proposition(doublons.isEmpty(), doublons.isEmpty() ? "Aucune autre offre du lot ne porte ses NIF."
                : "Un NIF de l'offre figure aussi dans l'offre " + String.join(", ", doublons) + "."));
        boolean exclue = types.contains("EXCLUSION") || o.entreprise() != null && o.entreprise().exclusion() != null;
        p.put("EXCLUSION", new Proposition(!exclue, exclue ? Optional.ofNullable(message(o, "EXCLUSION")).orElse("Exclusion de l'ARMP en cours.")
                : "Aucune exclusion de l'ARMP en cours."));
        OffreDto.PieceAttendue pouvoir = attendues.stream().filter(a -> a.libelle() != null
                && a.libelle().toLowerCase(Locale.FRENCH).contains("pouvoir")).findFirst().orElse(null);
        if (pouvoir == null) {
            p.put("POUVOIRS", new Proposition(null, "Pièce de pouvoir non exigée par le DAO : à vérifier sur l'acte d'engagement."));
        } else {
            boolean manque = o.piecesManquantes() != null && o.piecesManquantes().contains(pouvoir.libelle());
            p.put("POUVOIRS", new Proposition(!manque, manque ? "Manquante : " + pouvoir.libelle() + "." : "Jointe : " + pouvoir.libelle() + "."));
        }
        List<String> manquantes = o.piecesManquantes() == null ? List.of() : o.piecesManquantes();
        p.put("PIECES", new Proposition(manquantes.isEmpty(), manquantes.isEmpty() ? "Toutes les pièces exigées sont jointes."
                : "Manquantes : " + String.join(", ", manquantes) + "."));
        List<String> techniques = o.alertes() == null ? List.of() : o.alertes().stream()
                .filter(a -> Set.of("NON_CONFORME", "LIVRAISON_HORS_DELAI", "PLAFOND_DEPASSE").contains(a.type())).map(SeanceDto.Alerte::message).toList();
        p.put("CONFORMITE_TECHNIQUE", !techniques.isEmpty() ? new Proposition(false, String.join(" ", techniques))
                : o.formulaires() ? new Proposition(true, "Aucun écart relevé par les formulaires en ligne.")
                        : new Proposition(null, "Offre sans formulaire en ligne : conformité à examiner sur les pièces."));
        boolean intacte = "INTACTE".equals(o.integrite());
        p.put("INTEGRITE", new Proposition(intacte, intacte ? "Offre intacte (empreintes conformes)."
                : "Offre " + o.integrite() + (o.motif() == null ? "" : " : " + o.motif()) + "."));
        if (o.fraisDossier() == null) {
            p.put("FRAIS_DOSSIER", new Proposition(null, "Retrait du dossier sans frais."));
        } else {
            p.put("FRAIS_DOSSIER", new Proposition(o.fraisDossier().regle(), o.fraisDossier().regle()
                    ? "Reçu validé" + (o.fraisDossier().referencePaiement() == null ? "." : " : " + o.fraisDossier().referencePaiement() + ".")
                    : "Aucun reçu de frais de dossier validé pour l'entreprise."));
        }
        return p;
    }

    // ------------------------------------------------------------------ §B2 les demandes de précisions (art. 35-VI)

    /**
     * La PRMP demande des précisions au candidat d'une offre : 400 {@code QUESTION_OBLIGATOIRE}, {@code DELAI_OBLIGATOIRE} (ni saisi
     * ni fixé par la fiche, {@code B06-EP-01} / {@code B06-RC-01}) ; 409 {@code EVALUATION_CLOSE}.
     */
    public EvaluationDto.Demande demanderPrecisions(Long idDmc, String idOffre, EvaluationDto.DemandeRequest d) {
        exigerPrmp(idDmc);
        exigerEnCours(idDmc);
        SeanceDto.OffreLue o = offreEvaluee(idDmc, idOffre);
        String question = d == null ? null : nettoyer(d.question());
        if (question == null) {
            throw new BadRequestException("La demande de précisions exige une question.", "QUESTION_OBLIGATOIRE");
        }
        Integer delai = d.delaiJours() != null ? d.delaiJours() : delaiFiche(idDmc);
        if (delai == null || delai < 1) {
            throw new BadRequestException("Le délai de réponse (en jours) est à fixer : la fiche ne le donne pas.", "DELAI_OBLIGATOIRE");
        }
        LocalDateTime maintenant = maintenant();
        EvaluationDemande x = demandes.save(new EvaluationDemande(null, idDmc, idOffre, EvaluationDemande.PRECISION, question, delai,
                maintenant.plusDays(delai), maintenant, acteur(), null, null, null, null, null, null));
        tracer(idDmc, "PRECISION_DEMANDEE", "Offre n° " + o.numero() + " (" + o.entreprise().raisonSociale() + "), réponse sous " + delai
                + " jour(s) : " + question);
        Offre offre = offres.findById(idOffre).orElseThrow();
        CompteCandidat c = candidats.findById(offre.getIdCandidat()).orElse(null);
        notifications.emettreCandidat(TypeNotification.PRECISION_DEMANDEE, offre.getIdCandidat(), c == null ? null : c.getEmail(),
                idDmc.intValue(), TypeObjet.PROCEDURE, "Demande de précisions sur votre offre", "La personne responsable des marchés "
                        + "publics vous demande des précisions sur votre offre n° " + o.numero() + " ; répondez sur la plateforme avant le "
                        + x.getEcheance().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        + ". Une précision ne peut changer ni le prix ni la substance de l'offre.");
        return demandeDto(x, o.numero());
    }

    /** Les demandes de précisions d'une offre et leurs réponses : CAO, responsable, PRMP, UGPM. */
    @Transactional(readOnly = true)
    public List<EvaluationDto.Demande> precisions(Long idDmc, String idOffre) {
        exigerLecteur(idDmc);
        exigerEvaluation(idDmc);
        SeanceDto.OffreLue o = offreEvaluee(idDmc, idOffre);
        return demandes.findByIdOffreAndTypeOrderByDemandeeLeAscIdAsc(idOffre, EvaluationDemande.PRECISION).stream()
                .map(x -> demandeDto(x, o.numero())).toList();
    }

    /** Le fichier joint à une réponse : CAO, responsable, PRMP, UGPM ; 404 sans fichier. */
    @Transactional(readOnly = true)
    public EvaluationDemande fichierReponse(Long idDmc, Long idDemande) {
        exigerLecteur(idDmc);
        EvaluationDemande x = demandes.findById(idDemande).filter(d -> d.getIdDmc().equals(idDmc))
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable : " + idDemande + "."));
        if (x.getReponseContenu() == null) {
            throw new ResourceNotFoundException("Aucun fichier joint à cette réponse.");
        }
        return x;
    }

    /** Les demandes reçues par le candidat pour son offre (et leurs réponses). */
    @Transactional(readOnly = true)
    public List<EvaluationDto.Demande> precisionsDuCandidat(String idCandidat, String idOffre) {
        Offre o = sienne(idCandidat, idOffre);
        return demandes.findByIdOffreAndTypeOrderByDemandeeLeAscIdAsc(idOffre, EvaluationDemande.PRECISION).stream()
                .map(x -> demandeDto(x, o.getNumero())).toList();
    }

    /**
     * La réponse du candidat : texte obligatoire (400 {@code TEXTE_OBLIGATOIRE}), fichier PDF, JPEG ou PNG facultatif (400
     * {@code FORMAT_INVALIDE}, 413) ; 409 {@code DELAI_DEPASSE}, {@code DEJA_REPONDU}.
     */
    public EvaluationDto.Demande repondre(String idCandidat, String idOffre, Long idDemande, String texte, MultipartFile fichier) {
        Offre o = sienne(idCandidat, idOffre);
        EvaluationDemande x = demandes.findById(idDemande).filter(d -> d.getIdOffre().equals(idOffre))
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable : " + idDemande + "."));
        if (x.getReponduLe() != null) {
            throw new BusinessRuleException("Vous avez déjà répondu à cette demande.", "DEJA_REPONDU");
        }
        LocalDateTime maintenant = maintenant();
        if (maintenant.isAfter(x.getEcheance())) {
            throw new BusinessRuleException("Le délai de réponse est dépassé.", "DELAI_DEPASSE");
        }
        String t = nettoyer(texte);
        if (t == null) {
            throw new BadRequestException("La réponse exige un texte.", "TEXTE_OBLIGATOIRE");
        }
        // Le fichier se contrôle avant toute écriture : un refus laisse la demande sans réponse.
        byte[] contenu = null;
        String format = null;
        if (fichier != null && !fichier.isEmpty()) {
            try {
                contenu = fichier.getBytes();
            } catch (IOException e) {
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
        x.setReponse(t);
        x.setReponduLe(maintenant);
        if (contenu != null) {
            String nom = fichier.getOriginalFilename() == null || fichier.getOriginalFilename().isBlank() ? "reponse"
                    : fichier.getOriginalFilename().replaceAll("[\\\\/]", "_");
            x.setReponseNom(nom.length() > 255 ? nom.substring(nom.length() - 255) : nom);
            x.setReponseFormat(format);
            x.setReponseTaille((long) contenu.length);
            x.setReponseContenu(contenu);
        }
        demandes.save(x);
        Long idDmc = x.getIdDmc();
        // ⚠️ Tranche 1c — la même réponse sert la justification d'un prix (art. 48).
        boolean justification = EvaluationDemande.JUSTIFICATION.equals(x.getType());
        tracer(idDmc, justification ? "JUSTIFICATION_RECUE" : "PRECISION_RECUE", "Offre n° " + o.getNumero() + " (" + o.getRaisonSociale()
                + ") : réponse reçue" + (x.getReponseNom() == null ? "" : ", fichier joint"));
        TypeNotification type = justification ? TypeNotification.JUSTIFICATION_RECUE : TypeNotification.PRECISION_RECUE;
        String titre = justification ? "Justification du prix reçue" : "Précisions reçues";
        String corps = "Le candidat de l'offre n° " + o.getNumero() + " (procédure " + idDmc + ") a répondu à la demande de "
                + (justification ? "justification de son prix." : "précisions.");
        ceremonies.notifierPrmp(idDmc, type, titre, corps);
        internes.membresCao(idDmc).forEach(k -> internes.notifierMembre(idDmc, k, type, titre, corps));
        return demandeDto(x, o.getNumero());
    }

    // ------------------------------------------------------------------ ⚠️ tranche 1b (§B3) : corrections, montant évalué, classement

    /** Les règles d'une correction : proposées par le serveur ({@code PU_PREVAUT}, {@code LETTRES_PREVALENT}) ou saisies par la CAO. */
    public static final Set<String> REGLES_CORRECTION = Set.of("PU_PREVAUT", "LETTRES_PREVALENT", "REPORT", "AUTRE");
    /** La décision de l'étape 3 pour une offre évaluée (l'autre est {@code ECARTEE}, au refus d'une correction par le candidat). */
    public static final String EVALUEE = "EVALUEE";
    /** Le plafond de la marge de préférence retenu par le guide (§3.4). */
    static final java.math.BigDecimal PLAFOND_PREFERENCE = new java.math.BigDecimal("15");

    /** Les corrections arithmétiques proposées d'une offre (§B3.1) : CAO, responsable, PRMP, UGPM ; 409 {@code OFFRE_ECARTEE}. */
    @Transactional(readOnly = true)
    public List<EvaluationDto.CorrectionProposee> correctionsProposees(Long idDmc, String idOffre) {
        exigerLecteur(idDmc);
        exigerEvaluation(idDmc);
        offreEvaluee(idDmc, idOffre);
        EvaluationDecision conf = enVigueur(idDmc, EvaluationEtape.CONFORMITE).get(idOffre);
        if (conf != null && EvaluationDecision.ECARTEE.equals(conf.getDecision())) {
            throw new BusinessRuleException("Cette offre est écartée à l'examen préliminaire : elle n'est pas évaluée.", "OFFRE_ECARTEE");
        }
        return seance.correctionsProposees(idOffre).stream()
                .map(c -> new EvaluationDto.CorrectionProposee(c.ligne(), c.libelle(), c.avant(), c.apres(), c.regle())).toList();
    }

    /**
     * L'évaluation détaillée d'une offre (§B3.2) : membre déclaré sans conflit ; l'examen préliminaire du lot arrêté (409
     * {@code ETAPE_PRECEDENTE_OUVERTE}), l'offre retenue (409 {@code OFFRE_ECARTEE}), l'étape non arrêtée (409 {@code ETAPE_ARRETEE}) ;
     * 400 {@code PRIX_LU_OBLIGATOIRE}, {@code CORRECTION_INVALIDE}, {@code REGLE_INCONNUE}, {@code RABAIS_INVALIDE},
     * {@code PREFERENCE_NON_PREVUE}, {@code MOTIF_OBLIGATOIRE}, {@code CLAUSE_OBLIGATOIRE}, {@code CRITERE_HORS_DAO},
     * {@code CRITERE_INVALIDE}. Le montant évalué est calculé par le serveur, hors taxes (arbitrage Q3).
     */
    public EvaluationDto montant(Long idDmc, String idOffre, EvaluationDto.MontantRequest m) {
        Evaluation e = exigerEnCours(idDmc);
        String k = exigerDecideur(idDmc);
        SeanceDto.OffreLue o = offreEvaluee(idDmc, idOffre);
        Integer lot = lotDe(o);
        Map<String, EvaluationEtape> arr = arretees(idDmc, lot);
        if (!arr.containsKey(EvaluationEtape.CONFORMITE)) {
            throw new BusinessRuleException("L'examen préliminaire du lot " + lot + " n'est pas arrêté.", "ETAPE_PRECEDENTE_OUVERTE");
        }
        if (arr.containsKey(EvaluationEtape.EVALUATION)) {
            throw new BusinessRuleException("L'évaluation détaillée de ce lot est arrêtée : rouvrez-la pour changer un montant.", "ETAPE_ARRETEE");
        }
        EvaluationDecision conf = enVigueur(idDmc, EvaluationEtape.CONFORMITE).get(idOffre);
        if (conf == null || !EvaluationDecision.CONFORME.equals(conf.getDecision())) {
            throw new BusinessRuleException("Cette offre est écartée à l'examen préliminaire : elle n'est pas évaluée.", "OFFRE_ECARTEE");
        }
        if (m == null) {
            throw new BadRequestException("Le corps de l'évaluation est attendu.", "CORRECTION_INVALIDE");
        }
        java.math.BigDecimal lu = o.acteEngagement() == null ? null : SeanceService.montant(o.acteEngagement().get("montantHt"));
        java.math.BigDecimal prixLu = lu != null ? lu : m.prixLu();
        if (prixLu == null || prixLu.signum() < 0) {
            throw new BadRequestException("L'acte d'engagement ne porte pas de montant HT : saisissez le prix lu hors taxes.", "PRIX_LU_OBLIGATOIRE");
        }
        java.math.BigDecimal prixLuTtc = o.acteEngagement() == null ? null : SeanceService.montant(o.acteEngagement().get("montantTtc"));
        List<EvaluationDto.Correction> corrections = new ArrayList<>();
        java.math.BigDecimal prixCorrige = prixLu;
        for (EvaluationDto.Correction c : m.corrections() == null ? List.<EvaluationDto.Correction>of() : m.corrections()) {
            if (c == null || c.avant() == null || c.apres() == null || nettoyer(c.libelle()) == null) {
                throw new BadRequestException("Une correction porte un libellé, un montant avant et un montant après.", "CORRECTION_INVALIDE");
            }
            String regle = c.regle() == null ? null : c.regle().trim().toUpperCase(Locale.ROOT);
            if (!REGLES_CORRECTION.contains(regle)) {
                throw new BadRequestException("Règle de correction inconnue : " + c.regle() + " (PU_PREVAUT, LETTRES_PREVALENT, REPORT, AUTRE).",
                        "REGLE_INCONNUE");
            }
            boolean retenue = Boolean.TRUE.equals(c.retenue());
            corrections.add(new EvaluationDto.Correction(c.ligne(), c.libelle().trim(), c.avant(), c.apres(), regle, retenue));
            if (retenue) {
                prixCorrige = prixCorrige.add(c.apres().subtract(c.avant()));
            }
        }
        EvaluationDto.Refus refus = null;
        if (m.refusCandidat() != null) {
            String motif = nettoyer(m.refusCandidat().motif());
            String clause = nettoyer(m.refusCandidat().clause());
            if (motif == null) {
                throw new BadRequestException("Le refus d'une correction par le candidat exige un motif.", "MOTIF_OBLIGATOIRE");
            }
            if (clause == null) {
                throw new BadRequestException("Le refus d'une correction écarte l'offre si les IC le prévoient : citez la clause.", "CLAUSE_OBLIGATOIRE");
            }
            refus = new EvaluationDto.Refus(motif, clause);
        }
        java.math.BigDecimal rabais = m.rabais() == null || m.rabais().montant() == null ? java.math.BigDecimal.ZERO : m.rabais().montant();
        if (rabais.signum() < 0) {
            throw new BadRequestException("Le rabais est un montant positif, hors taxes.", "RABAIS_INVALIDE");
        }
        Map<String, String> v = valeursFiche(idDmc);
        boolean prevue = "OUI".equals(v.get("B06-PN-01")) || "OUI".equals(v.get("B03-CQ-08"));
        boolean eligible = m.preference() != null && Boolean.TRUE.equals(m.preference().eligible());
        String motifPreference = m.preference() == null ? null : nettoyer(m.preference().motif());
        if (eligible && !prevue) {
            throw new BadRequestException("Le DAO ne prévoit pas de marge de préférence.", "PREFERENCE_NON_PREVUE");
        }
        if (eligible && motifPreference == null) {
            throw new BadRequestException("L'éligibilité à la marge de préférence se motive.", "MOTIF_OBLIGATOIRE");
        }
        List<EvaluationDto.Critere> criteres = new ArrayList<>();
        for (EvaluationDto.Critere c : m.criteres() == null ? List.<EvaluationDto.Critere>of() : m.criteres()) {
            if (nettoyer(v.get("B06-EO-02")) == null) {
                throw new BadRequestException("Le DAO ne porte pas de critère additionnel d'évaluation (règle d'or : rien hors du DAO).",
                        "CRITERE_HORS_DAO");
            }
            if (c == null || nettoyer(c.libelle()) == null || c.montant() == null || nettoyer(c.justification()) == null) {
                throw new BadRequestException("Un critère monétisé porte son libellé, son montant et sa justification.", "CRITERE_INVALIDE");
            }
            criteres.add(new EvaluationDto.Critere(c.libelle().trim(), c.montant(), c.justification().trim()));
        }
        java.math.BigDecimal net = prixCorrige.subtract(rabais);
        java.math.BigDecimal taux = prevue ? tauxPreference(v) : null;
        java.math.BigDecimal ajustement = prevue && !eligible && taux != null
                ? net.multiply(taux).divide(java.math.BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP) : java.math.BigDecimal.ZERO;
        java.math.BigDecimal montantEvalue = refus != null ? null
                : net.add(ajustement).add(criteres.stream().map(EvaluationDto.Critere::montant).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        EvaluationDto.Montant saisi = new EvaluationDto.Montant(prixLu, prixLuTtc, corrections, refus,
                new EvaluationDto.Rabais(rabais, m.rabais() == null ? null : nettoyer(m.rabais().lecture())),
                new EvaluationDto.Preference(prevue ? eligible : null, motifPreference, taux, ajustement), criteres, prixCorrige, montantEvalue,
                null, null, null);
        LocalDateTime maintenant = maintenant();
        EvaluationDecision avant = remplacer(idOffre, EvaluationEtape.EVALUATION, maintenant);
        String decision = refus != null ? EvaluationDecision.ECARTEE : EVALUEE;
        decisions.save(new EvaluationDecision(null, idDmc, idOffre, lot, EvaluationEtape.EVALUATION, decision, null,
                refus == null ? null : refus.motif(), refus == null ? null : refus.clause(), ecrire(saisi), k, maintenant, null));
        tracer(idDmc, "MONTANT", "Offre n° " + o.numero() + " (" + o.entreprise().raisonSociale() + ") : "
                + (refus != null ? "écartée, le candidat refuse la correction : " + refus.motif() + " (" + refus.clause() + ")"
                        : "prix lu " + FormulairesEnLigne.lisible(prixLu) + ", corrigé " + FormulairesEnLigne.lisible(prixCorrige)
                                + ", montant évalué " + FormulairesEnLigne.lisible(montantEvalue))
                + (avant == null ? "" : " (remplace la saisie précédente)"));
        return dto(idDmc, e);
    }

    /**
     * Départage des offres classées à égalité (§B3.6, Q5 : la CAO départage, avec un motif) : membre déclaré sans conflit ; 400
     * {@code ORDRE_INVALIDE}, {@code MOTIF_OBLIGATOIRE} ; 409 {@code ETAPE_ARRETEE}, {@code ETAPE_PRECEDENTE_OUVERTE}.
     */
    public EvaluationDto departager(Long idDmc, Integer lot, EvaluationDto.DepartageRequest d) {
        Evaluation e = exigerEnCours(idDmc);
        String k = exigerDecideur(idDmc);
        List<SeanceDto.OffreLue> liste = parLot(idDmc).get(lot);
        if (liste == null) {
            throw new ResourceNotFoundException("Lot introuvable dans l'évaluation : " + lot + ".");
        }
        Map<String, EvaluationEtape> arr = arretees(idDmc, lot);
        if (!arr.containsKey(EvaluationEtape.CONFORMITE)) {
            throw new BusinessRuleException("L'examen préliminaire du lot " + lot + " n'est pas arrêté.", "ETAPE_PRECEDENTE_OUVERTE");
        }
        if (arr.containsKey(EvaluationEtape.EVALUATION)) {
            throw new BusinessRuleException("L'évaluation détaillée de ce lot est arrêtée.", "ETAPE_ARRETEE");
        }
        String motif = d == null ? null : nettoyer(d.motif());
        if (motif == null) {
            throw new BadRequestException("Le départage exige un motif.", "MOTIF_OBLIGATOIRE");
        }
        List<String> ordre = d.ordre() == null ? List.of() : d.ordre();
        Set<String> classees = classer(idDmc, lot, liste).keySet();
        if (ordre.size() < 2 || new java.util.HashSet<>(ordre).size() != ordre.size() || !classees.containsAll(ordre)) {
            throw new BadRequestException("L'ordre cite au moins deux offres évaluées du lot, chacune une fois.", "ORDRE_INVALIDE");
        }
        LocalDateTime maintenant = maintenant();
        for (cnm.prs.entity.EvaluationDepartage x : departages.findByIdDmcAndRemplaceLeIsNull(idDmc)) {
            if (Objects.equals(x.getLot(), lot)) {
                x.setRemplaceLe(maintenant);
                departages.saveAndFlush(x);
            }
        }
        departages.save(new cnm.prs.entity.EvaluationDepartage(null, idDmc, lot, String.join(",", ordre), motif, k, maintenant, null));
        tracer(idDmc, "DEPARTAGE", "Lot " + lot + " : " + ordre.stream().map(id -> "n° " + numero(liste, id)).collect(Collectors.joining(" puis "))
                + " — " + motif);
        return dto(idDmc, e);
    }

    /** Le tableau d'évaluation du lot (modèle du guide, p. 9) : CAO, responsable, PRMP, UGPM. */
    @Transactional(readOnly = true)
    public List<EvaluationDto.LigneTableau> tableau(Long idDmc, Integer lot) {
        exigerLecteur(idDmc);
        exigerEvaluation(idDmc);
        List<SeanceDto.OffreLue> liste = parLot(idDmc).get(lot);
        if (liste == null) {
            throw new ResourceNotFoundException("Lot introuvable dans l'évaluation : " + lot + ".");
        }
        Map<String, EvaluationDecision> conformites = enVigueur(idDmc, EvaluationEtape.CONFORMITE);
        Map<String, EvaluationDecision> montants = enVigueur(idDmc, EvaluationEtape.EVALUATION);
        Map<String, EvaluationDecision> anormalesEnVigueur = enVigueur(idDmc, EvaluationEtape.ANORMALES);
        Map<String, EvaluationDecision> qualifications = enVigueur(idDmc, EvaluationEtape.QUALIFICATION);
        Map<String, Classe> classement = classer(idDmc, lot, liste);
        List<EvaluationDto.LigneTableau> out = new ArrayList<>();
        for (SeanceDto.OffreLue o : liste) {
            EvaluationDecision c = conformites.get(o.idOffre());
            EvaluationDto.Montant mt = montantDto(montants.get(o.idOffre()));
            EvaluationDto.Ecartement ec = ecartement(c, montants.get(o.idOffre()), anormalesEnVigueur.get(o.idOffre()), qualifications.get(o.idOffre()));
            Classe cl = classement.get(o.idOffre());
            java.math.BigDecimal ajustements = mt == null ? null : (mt.preference() == null || mt.preference().ajustement() == null
                    ? java.math.BigDecimal.ZERO : mt.preference().ajustement())
                    .add(mt.criteres() == null ? java.math.BigDecimal.ZERO : mt.criteres().stream().map(EvaluationDto.Critere::montant)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
            out.add(new EvaluationDto.LigneTableau(o.idOffre(), o.numero(), o.entreprise().raisonSociale(),
                    mt != null ? mt.prixLu() : o.acteEngagement() == null ? null : SeanceService.montant(o.acteEngagement().get("montantHt")),
                    mt != null ? mt.prixLuTtc() : o.acteEngagement() == null ? null : SeanceService.montant(o.acteEngagement().get("montantTtc")),
                    garantieLue(o), c == null ? null : EvaluationDecision.CONFORME.equals(c.getDecision()),
                    ec == null ? null : ec.motif() + (ec.clause() == null ? "" : " (" + ec.clause() + ")"),
                    mt == null ? null : mt.prixCorrige(), mt == null || mt.rabais() == null ? null : mt.rabais().montant(), ajustements,
                    mt == null ? null : mt.montantEvalue(), cl == null ? null : cl.rang(), cl == null ? null : cl.exAequo(), qualifie(qualifications.get(o.idOffre()))));
        }
        out.sort(java.util.Comparator.comparing((EvaluationDto.LigneTableau x) -> x.rang() == null ? Integer.MAX_VALUE : x.rang())
                .thenComparing(EvaluationDto.LigneTableau::numero));
        return out;
    }

    /** Le rang d'une offre dans son lot ; {@code exAequo} : à égalité de montant évalué, sans départage. */
    record Classe(int rang, boolean exAequo) {
    }

    /**
     * Le classement d'un lot (§B3.6) : les offres retenues à l'examen préliminaire et évaluées (non écartées à l'étape 3), par montant
     * évalué croissant ; à égalité, le départage en vigueur ordonne les offres qu'il cite toutes, sinon elles partagent leur rang.
     */
    Map<String, Classe> classer(Long idDmc, Integer lot, List<SeanceDto.OffreLue> liste) {
        return classer(idDmc, lot, liste, true);
    }

    /** Le classement de l'étape 3, avant l'exclusion des offres rejetées pour prix anormal (étape 4). */
    Map<String, Classe> classerAvantAnormales(Long idDmc, Integer lot, List<SeanceDto.OffreLue> liste) {
        return classer(idDmc, lot, liste, false);
    }

    private Map<String, Classe> classer(Long idDmc, Integer lot, List<SeanceDto.OffreLue> liste, boolean sansRejetees) {
        Map<String, EvaluationDecision> conformites = enVigueur(idDmc, EvaluationEtape.CONFORMITE);
        Map<String, EvaluationDecision> montants = enVigueur(idDmc, EvaluationEtape.EVALUATION);
        Map<String, EvaluationDecision> anormales = sansRejetees ? enVigueur(idDmc, EvaluationEtape.ANORMALES) : Map.of();
        List<Map.Entry<String, java.math.BigDecimal>> evaluees = new ArrayList<>();
        for (SeanceDto.OffreLue o : liste) {
            EvaluationDecision c = conformites.get(o.idOffre());
            EvaluationDecision m = montants.get(o.idOffre());
            EvaluationDto.Montant mt = montantDto(m);
            if (c != null && EvaluationDecision.CONFORME.equals(c.getDecision()) && m != null && EVALUEE.equals(m.getDecision())
                    && mt != null && mt.montantEvalue() != null
                    && (anormales.get(o.idOffre()) == null || !REJETEE.equals(anormales.get(o.idOffre()).getDecision()))) {
                evaluees.add(Map.entry(o.idOffre(), mt.montantEvalue()));
            }
        }
        evaluees.sort(Map.Entry.comparingByValue());
        List<String> ordre = departages.findByIdDmcAndRemplaceLeIsNull(idDmc).stream().filter(x -> Objects.equals(x.getLot(), lot))
                .findFirst().map(x -> ChampFicheMarche.liste(x.getOrdre())).orElse(List.of());
        Map<String, Classe> out = new LinkedHashMap<>();
        int rang = 1;
        int i = 0;
        while (i < evaluees.size()) {
            int j = i;
            while (j + 1 < evaluees.size() && evaluees.get(j + 1).getValue().compareTo(evaluees.get(i).getValue()) == 0) {
                j++;
            }
            List<String> groupe = new ArrayList<>(evaluees.subList(i, j + 1).stream().map(Map.Entry::getKey).toList());
            if (groupe.size() == 1) {
                out.put(groupe.get(0), new Classe(rang, false));
            } else if (ordre.containsAll(groupe)) {
                groupe.sort(java.util.Comparator.comparingInt(ordre::indexOf));
                for (int n = 0; n < groupe.size(); n++) {
                    out.put(groupe.get(n), new Classe(rang + n, false));
                }
            } else {
                for (String id : groupe) {
                    out.put(id, new Classe(rang, true));
                }
            }
            rang += groupe.size();
            i = j + 1;
        }
        return out;
    }

    /** Les offres du lot retenues à l'examen préliminaire. */
    private List<SeanceDto.OffreLue> retenuesConformite(Long idDmc, List<SeanceDto.OffreLue> liste) {
        Map<String, EvaluationDecision> conformites = enVigueur(idDmc, EvaluationEtape.CONFORMITE);
        return liste.stream().filter(o -> conformites.get(o.idOffre()) != null
                && EvaluationDecision.CONFORME.equals(conformites.get(o.idOffre()).getDecision())).toList();
    }

    /** 409 {@code ETAPE_INCOMPLETE} (détails : les numéros) si une offre de la liste n'a pas de décision en vigueur à l'étape. */
    private void exigerDecisions(Long idDmc, List<SeanceDto.OffreLue> liste, String etape) {
        Map<String, EvaluationDecision> enVigueur = enVigueur(idDmc, etape);
        List<Integer> sans = liste.stream().filter(o -> !enVigueur.containsKey(o.idOffre())).map(SeanceDto.OffreLue::numero).toList();
        if (!sans.isEmpty()) {
            throw new BusinessRuleException("Des offres du lot n'ont pas de décision : " + sans.stream().map(n -> "n° " + n)
                    .collect(Collectors.joining(", ")) + ".", "ETAPE_INCOMPLETE", null, Map.of("offres", sans));
        }
    }

    private EvaluationDto.Montant montantDto(EvaluationDecision d) {
        if (d == null || d.getContenu() == null) {
            return null;
        }
        EvaluationDto.Montant m = mapper.readValue(d.getContenu(), EvaluationDto.Montant.class);
        return new EvaluationDto.Montant(m.prixLu(), m.prixLuTtc(), m.corrections(), m.refusCandidat(), m.rabais(), m.preference(), m.criteres(),
                m.prixCorrige(), m.montantEvalue(), d.getPar(), internes.nomMembre(d.getPar()), d.getLe());
    }

    /**
     * L'étape qui a écarté l'offre, dans l'ordre des étapes : l'examen préliminaire, l'évaluation détaillée (refus d'une
     * correction), le prix anormal (rejet), la post-qualification (non qualifiée).
     */
    private EvaluationDto.Ecartement ecartement(EvaluationDecision... parEtape) {
        EvaluationDecision d = null;
        for (EvaluationDecision x : parEtape) {
            if (x != null && Set.of(EvaluationDecision.ECARTEE, REJETEE, NON_QUALIFIE).contains(x.getDecision())) {
                d = x;
                break;
            }
        }
        return d == null ? null : new EvaluationDto.Ecartement(d.getEtape(), d.getQualification(), d.getMotif(), d.getClause(), d.getPar(),
                internes.nomMembre(d.getPar()), d.getLe());
    }

    private static String garantieLue(SeanceDto.OffreLue o) {
        if (o.garantie() == null || !o.garantie().presente()) {
            return "absente";
        }
        return o.garantie().montant() == null ? "présente" : FormulairesEnLigne.lisible(o.garantie().montant())
                + (o.garantie().monnaie() == null ? "" : " " + o.garantie().monnaie());
    }

    private Map<String, String> valeursFiche(Long idDmc) {
        Map<String, String> v = fiches.etatValide(idDmc).map(x -> x.etat().getValeurs()).orElse(null);
        return v == null ? Map.of() : v;
    }

    /** Le taux de la marge de préférence de la fiche ({@code B06-PN-02} travaux, {@code B06-EO-09} fournitures), plafonné à 15 %. */
    private static java.math.BigDecimal tauxPreference(Map<String, String> v) {
        for (String code : List.of("B06-PN-02", "B06-EO-09")) {
            java.math.BigDecimal t = SeanceService.montant(v.get(code) == null ? null : v.get(code).replace("%", "").trim());
            if (t != null) {
                return t.min(PLAFOND_PREFERENCE);
            }
        }
        return null;
    }

    private static Integer numero(List<SeanceDto.OffreLue> liste, String idOffre) {
        return liste.stream().filter(o -> o.idOffre().equals(idOffre)).map(SeanceDto.OffreLue::numero).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ ⚠️ tranche 1c (§B4) : offres anormalement basses ou hautes

    public static final String NON_SUSPECTEE = "NON_SUSPECTEE";
    public static final String SUSPECTEE = "SUSPECTEE";
    public static final String MAINTENUE = "MAINTENUE";
    public static final String REJETEE = "REJETEE";

    /** Les indicateurs de prix d'un lot (§B4, art. 48) : écarts à l'estimation et à la moyenne des offres classées, jamais une décision. */
    @Transactional(readOnly = true)
    public EvaluationDto.IndicateursPrix indicateursPrix(Long idDmc, Integer lot) {
        exigerLecteur(idDmc);
        exigerEvaluation(idDmc);
        List<SeanceDto.OffreLue> liste = exigerLot(idDmc, lot);
        Map<String, EvaluationDecision> montants = enVigueur(idDmc, EvaluationEtape.EVALUATION);
        Map<String, Classe> classees = classerAvantAnormales(idDmc, lot, liste);
        Map<String, java.math.BigDecimal> parOffre = new LinkedHashMap<>();
        liste.stream().filter(o -> classees.containsKey(o.idOffre())).sorted(java.util.Comparator.comparingInt(o -> classees.get(o.idOffre()).rang()))
                .forEach(o -> parOffre.put(o.idOffre(), montantDuMarche(montantDto(montants.get(o.idOffre())))));
        java.math.BigDecimal moyenne = parOffre.isEmpty() ? null : parOffre.values().stream().reduce(java.math.BigDecimal.ZERO,
                java.math.BigDecimal::add).divide(java.math.BigDecimal.valueOf(parOffre.size()), 0, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal estimation = estimation(idDmc, lot);
        List<EvaluationDto.IndicateurPrix> out = new ArrayList<>();
        parOffre.forEach((id, m) -> {
            SeanceDto.OffreLue o = liste.stream().filter(x -> x.idOffre().equals(id)).findFirst().orElseThrow();
            List<String> alertes = o.alertes() == null ? List.of() : o.alertes().stream().filter(a -> "SOUS_DETAIL_INCOHERENT".equals(a.type()))
                    .map(SeanceDto.Alerte::message).toList();
            out.add(new EvaluationDto.IndicateurPrix(id, o.numero(), m, ecart(m, estimation), ecart(m, moyenne), alertes));
        });
        return new EvaluationDto.IndicateursPrix(nettoyer(valeursFiche(idDmc).get("B06-EO-07")), estimation, moyenne, out);
    }

    /**
     * La PRMP demande au candidat de justifier son prix (art. 48, sur proposition de la CAO) : l'évaluation détaillée arrêtée, l'étape 4
     * ouverte, l'offre classée ; 400 {@code ELEMENTS_OBLIGATOIRES}, {@code DELAI_OBLIGATOIRE} ; 409 {@code DEJA_DEMANDEE}.
     */
    public EvaluationDto.Demande demanderJustification(Long idDmc, String idOffre, EvaluationDto.JustificationRequest j) {
        exigerPrmp(idDmc);
        exigerEnCours(idDmc);
        SeanceDto.OffreLue o = offreEvaluee(idDmc, idOffre);
        exigerEtapeAnormales(idDmc, o);
        if (!demandes.findByIdOffreAndTypeOrderByDemandeeLeAscIdAsc(idOffre, EvaluationDemande.JUSTIFICATION).isEmpty()) {
            throw new BusinessRuleException("La justification du prix de cette offre est déjà demandée.", "DEJA_DEMANDEE");
        }
        String elements = j == null ? null : nettoyer(j.elements());
        if (elements == null) {
            throw new BadRequestException("Dites les éléments à justifier (prix unitaires, sous-détails, moyens).", "ELEMENTS_OBLIGATOIRES");
        }
        Integer delai = j.delaiJours() != null ? j.delaiJours() : delaiFiche(idDmc);
        if (delai == null || delai < 1) {
            throw new BadRequestException("Le délai de réponse (en jours) est à fixer : la fiche ne le donne pas.", "DELAI_OBLIGATOIRE");
        }
        LocalDateTime maintenant = maintenant();
        EvaluationDemande x = demandes.save(new EvaluationDemande(null, idDmc, idOffre, EvaluationDemande.JUSTIFICATION, elements, delai,
                maintenant.plusDays(delai), maintenant, acteur(), null, null, null, null, null, null));
        tracer(idDmc, "JUSTIFICATION_DEMANDEE", "Offre n° " + o.numero() + " (" + o.entreprise().raisonSociale() + "), réponse sous " + delai
                + " jour(s) : " + elements);
        Offre offre = offres.findById(idOffre).orElseThrow();
        CompteCandidat c = candidats.findById(offre.getIdCandidat()).orElse(null);
        notifications.emettreCandidat(TypeNotification.JUSTIFICATION_DEMANDEE, offre.getIdCandidat(), c == null ? null : c.getEmail(),
                idDmc.intValue(), TypeObjet.PROCEDURE, "Justification du prix de votre offre", "La personne responsable des marchés publics "
                        + "vous demande de justifier le prix de votre offre n° " + o.numero() + " ; répondez sur la plateforme avant le "
                        + x.getEcheance().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + ".");
        return demandeDto(x, o.numero());
    }

    /** Les demandes de justification d'une offre et leurs réponses : CAO, responsable, PRMP, UGPM. */
    @Transactional(readOnly = true)
    public List<EvaluationDto.Demande> justifications(Long idDmc, String idOffre) {
        exigerLecteur(idDmc);
        exigerEvaluation(idDmc);
        SeanceDto.OffreLue o = offreEvaluee(idDmc, idOffre);
        return demandes.findByIdOffreAndTypeOrderByDemandeeLeAscIdAsc(idOffre, EvaluationDemande.JUSTIFICATION).stream()
                .map(x -> demandeDto(x, o.numero())).toList();
    }

    /** Les demandes de justification reçues par le candidat pour son offre. */
    @Transactional(readOnly = true)
    public List<EvaluationDto.Demande> justificationsDuCandidat(String idCandidat, String idOffre) {
        Offre o = sienne(idCandidat, idOffre);
        return demandes.findByIdOffreAndTypeOrderByDemandeeLeAscIdAsc(idOffre, EvaluationDemande.JUSTIFICATION).stream()
                .map(x -> demandeDto(x, o.getNumero())).toList();
    }

    /** La réponse du candidat à la demande de justification de son offre ; 404 sans demande. Mêmes règles que les précisions. */
    public EvaluationDto.Demande repondreJustification(String idCandidat, String idOffre, String texte, MultipartFile fichier) {
        sienne(idCandidat, idOffre);
        EvaluationDemande x = demandes.findByIdOffreAndTypeOrderByDemandeeLeAscIdAsc(idOffre, EvaluationDemande.JUSTIFICATION).stream()
                .reduce((a, b) -> b).orElseThrow(() -> new ResourceNotFoundException("Aucune justification n'est demandée pour cette offre."));
        return repondre(idCandidat, idOffre, x.getId(), texte, fichier);
    }

    /**
     * L'examen d'une offre au regard de son prix (§B4) : membre déclaré sans conflit. Non suspectée ; suspectée (motif) ; puis
     * maintenue ou rejetée (motif). <strong>Aucun rejet sans demande écrite</strong> : 409 {@code JUSTIFICATION_NON_DEMANDEE}, et tant
     * que le candidat n'a pas répondu et que le délai court, 409 {@code DELAI_EN_COURS}. 400 {@code DECISION_INVALIDE},
     * {@code MOTIF_OBLIGATOIRE} ; 409 {@code ETAPE_PRECEDENTE_OUVERTE}, {@code ETAPE_ARRETEE}, {@code OFFRE_ECARTEE}.
     */
    public EvaluationDto anormale(Long idDmc, String idOffre, EvaluationDto.AnormaleRequest r) {
        Evaluation e = exigerEnCours(idDmc);
        String k = exigerDecideur(idDmc);
        SeanceDto.OffreLue o = offreEvaluee(idDmc, idOffre);
        exigerEtapeAnormales(idDmc, o);
        boolean suspectee = r != null && Boolean.TRUE.equals(r.suspectee());
        String motif = r == null ? null : nettoyer(r.motif());
        String d = r == null || r.decision() == null ? null : r.decision().trim().toUpperCase(Locale.ROOT);
        String decision;
        if (!suspectee) {
            if (d != null) {
                throw new BadRequestException("Une offre non suspectée n'est ni maintenue ni rejetée.", "DECISION_INVALIDE");
            }
            decision = NON_SUSPECTEE;
        } else if (d == null) {
            decision = SUSPECTEE;
        } else if (MAINTENUE.equals(d) || REJETEE.equals(d)) {
            decision = d;
        } else {
            throw new BadRequestException("La décision est MAINTENUE ou REJETEE.", "DECISION_INVALIDE");
        }
        if (suspectee && motif == null) {
            throw new BadRequestException("Suspecter, maintenir ou rejeter une offre se motive (comparaison à l'estimation, réponse du candidat).",
                    "MOTIF_OBLIGATOIRE");
        }
        if (REJETEE.equals(decision)) {
            EvaluationDemande j = demandes.findByIdOffreAndTypeOrderByDemandeeLeAscIdAsc(idOffre, EvaluationDemande.JUSTIFICATION).stream()
                    .reduce((a, b) -> b).orElseThrow(() -> new BusinessRuleException("Aucun rejet sans demande écrite de justification au "
                            + "candidat (art. 48).", "JUSTIFICATION_NON_DEMANDEE"));
            if (j.getReponduLe() == null && !maintenant().isAfter(j.getEcheance())) {
                throw new BusinessRuleException("Le candidat n'a pas encore répondu et son délai court jusqu'au "
                        + j.getEcheance().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + ".", "DELAI_EN_COURS");
            }
        }
        LocalDateTime maintenant = maintenant();
        EvaluationDecision avant = remplacer(idOffre, EvaluationEtape.ANORMALES, maintenant);
        decisions.save(new EvaluationDecision(null, idDmc, idOffre, lotDe(o), EvaluationEtape.ANORMALES, decision, null, motif, null,
                ecrire(Map.of("suspectee", suspectee)), k, maintenant, null));
        tracer(idDmc, "ANORMALE", "Offre n° " + o.numero() + " (" + o.entreprise().raisonSociale() + ") : "
                + (avant == null ? "" : avant.getDecision() + " → ") + decision + (motif == null ? "" : " — " + motif));
        return dto(idDmc, e);
    }

    /** L'étape 4 du lot de l'offre : l'étape 3 arrêtée, la 4 ouverte, l'offre classée à l'étape 3. */
    private void exigerEtapeAnormales(Long idDmc, SeanceDto.OffreLue o) {
        Integer lot = lotDe(o);
        Map<String, EvaluationEtape> arr = arretees(idDmc, lot);
        if (!arr.containsKey(EvaluationEtape.EVALUATION)) {
            throw new BusinessRuleException("L'évaluation détaillée du lot " + lot + " n'est pas arrêtée.", "ETAPE_PRECEDENTE_OUVERTE");
        }
        if (arr.containsKey(EvaluationEtape.ANORMALES)) {
            throw new BusinessRuleException("L'examen des prix de ce lot est arrêté : rouvrez-le pour changer une décision.", "ETAPE_ARRETEE");
        }
        if (!classerAvantAnormales(idDmc, lot, parLot(idDmc).get(lot)).containsKey(o.idOffre())) {
            throw new BusinessRuleException("Cette offre n'est pas classée : elle est écartée.", "OFFRE_ECARTEE");
        }
    }

    // ------------------------------------------------------------------ ⚠️ tranche 1c (§B5) : post-qualification

    public static final String QUALIFIE = "QUALIFIE";
    public static final String NON_QUALIFIE = "NON_QUALIFIE";
    public static final String SATISFAIT = "SATISFAIT";
    public static final String NON_SATISFAIT = "NON_SATISFAIT";
    /** Les critères de capacité financière de la fiche (travaux, fournitures), dans cet ordre. */
    static final List<String> CRITERES_FINANCIERS = List.of("B03-QT-07", "B03-QT-14", "B03-QT-15", "B03-QT-16", "B03-QT-17", "B03-QT-18",
            "B03-CQ-03", "B03-CQ-10");
    /** Les critères de capacité technique et d'expérience de la fiche. */
    static final List<String> CRITERES_TECHNIQUES = List.of("B03-QT-08", "B03-QT-12", "B03-QT-19", "B03-QT-20", "B03-CQ-02", "B03-CQ-04",
            "B03-CQ-06");

    /** L'offre dont c'est le tour, sa décision (nulle si elle attend), son rang ; {@code courante} nulle : toutes ont échoué. */
    record Tour(String courante, String decision, int position, List<String> ordre) {
    }

    /** Le tour de post-qualification : la première offre classée qui n'est pas « non qualifiée ». */
    Tour tour(Long idDmc, Integer lot, List<SeanceDto.OffreLue> liste) {
        Map<String, Classe> classement = classer(idDmc, lot, liste);
        List<String> ordre = classement.entrySet().stream().sorted(java.util.Comparator.comparingInt(x -> x.getValue().rang()))
                .map(Map.Entry::getKey).toList();
        Map<String, EvaluationDecision> qualifications = enVigueur(idDmc, EvaluationEtape.QUALIFICATION);
        for (int i = 0; i < ordre.size(); i++) {
            EvaluationDecision d = qualifications.get(ordre.get(i));
            if (d == null || !NON_QUALIFIE.equals(d.getDecision())) {
                return new Tour(ordre.get(i), d == null ? null : d.getDecision(), i + 1, ordre);
            }
        }
        return new Tour(null, null, ordre.size() + 1, ordre);
    }

    /**
     * La post-qualification du lot (§B5) : l'offre dont c'est le tour et ses critères ; {@code idOffre} nul quand toutes les offres
     * classées ont échoué (proposition d'infructuosité, lot 2). 409 {@code CLASSEMENT_NON_ARRETE} tant que l'étape 4 n'est pas arrêtée.
     */
    @Transactional(readOnly = true)
    public EvaluationDto.Qualification qualificationCourante(Long idDmc, Integer lot) {
        exigerLecteur(idDmc);
        exigerEvaluation(idDmc);
        List<SeanceDto.OffreLue> liste = exigerLot(idDmc, lot);
        if (!arretees(idDmc, lot).containsKey(EvaluationEtape.ANORMALES)) {
            throw new BusinessRuleException("Le classement du lot " + lot + " n'est pas arrêté (étape 4).", "CLASSEMENT_NON_ARRETE");
        }
        Tour t = tour(idDmc, lot, liste);
        if (t.courante() == null) {
            return new EvaluationDto.Qualification(null, null, List.of(), null, null, null, null, null, null);
        }
        SeanceDto.OffreLue o = liste.stream().filter(x -> x.idOffre().equals(t.courante())).findFirst().orElseThrow();
        EvaluationDto.Qualification q = qualificationDto(enVigueur(idDmc, EvaluationEtape.QUALIFICATION).get(o.idOffre()), o);
        return q != null ? q : new EvaluationDto.Qualification(o.idOffre(), o.numero(), criteres(idDmc, o, Map.of()), null, null, null, null,
                null, null);
    }

    /**
     * La post-qualification d'une offre (§B5) : membre déclaré sans conflit ; l'offre dont c'est le tour, ou une offre déjà examinée
     * (409 {@code PAS_LE_TOUR_DE_CETTE_OFFRE}) ; chaque critère du DAO décidé (400 {@code CRITERE_INCONNU}, {@code CRITERES_INCOMPLETS},
     * {@code MOTIF_OBLIGATOIRE}) ; 400 {@code DECISION_INVALIDE}, {@code QUALIFICATION_INCOHERENTE} (qualifiée malgré un critère non
     * satisfait), {@code CLAUSE_OBLIGATOIRE} ; 409 {@code CLASSEMENT_NON_ARRETE}, {@code ETAPE_ARRETEE}.
     */
    public EvaluationDto qualifier(Long idDmc, String idOffre, EvaluationDto.QualificationRequest q) {
        Evaluation e = exigerEnCours(idDmc);
        String k = exigerDecideur(idDmc);
        SeanceDto.OffreLue o = offreEvaluee(idDmc, idOffre);
        Integer lot = lotDe(o);
        Map<String, EvaluationEtape> arr = arretees(idDmc, lot);
        if (!arr.containsKey(EvaluationEtape.ANORMALES)) {
            throw new BusinessRuleException("Le classement du lot " + lot + " n'est pas arrêté (étape 4).", "CLASSEMENT_NON_ARRETE");
        }
        if (arr.containsKey(EvaluationEtape.QUALIFICATION)) {
            throw new BusinessRuleException("La post-qualification de ce lot est arrêtée : rouvrez-la pour changer une décision.", "ETAPE_ARRETEE");
        }
        Tour t = tour(idDmc, lot, parLot(idDmc).get(lot));
        int position = t.ordre().indexOf(idOffre);
        if (position < 0 || position + 1 > t.position()) {
            throw new BusinessRuleException("Ce n'est pas le tour de cette offre : la post-qualification suit le classement.",
                    "PAS_LE_TOUR_DE_CETTE_OFFRE");
        }
        String decision = q == null || q.decision() == null ? null : q.decision().trim().toUpperCase(Locale.ROOT);
        if (!QUALIFIE.equals(decision) && !NON_QUALIFIE.equals(decision)) {
            throw new BadRequestException("La décision est QUALIFIE ou NON_QUALIFIE.", "DECISION_INVALIDE");
        }
        List<EvaluationDto.CritereQualification> criteres = criteres(idDmc, o, Map.of());
        Set<String> codes = criteres.stream().map(EvaluationDto.CritereQualification::code).collect(Collectors.toSet());
        Map<String, Map<String, Object>> saisis = new LinkedHashMap<>();
        for (EvaluationDto.CritereSaisi c : q.criteres() == null ? List.<EvaluationDto.CritereSaisi>of() : q.criteres()) {
            if (c == null || !codes.contains(c.code())) {
                throw new BadRequestException("Critère inconnu : " + (c == null ? null : c.code()) + " (rien hors du DAO).", "CRITERE_INCONNU");
            }
            String dc = c.decision() == null ? null : c.decision().trim().toUpperCase(Locale.ROOT);
            if (!SATISFAIT.equals(dc) && !NON_SATISFAIT.equals(dc)) {
                continue;
            }
            if (NON_SATISFAIT.equals(dc) && nettoyer(c.motif()) == null) {
                throw new BadRequestException("Un critère non satisfait se motive : " + c.code() + ".", "MOTIF_OBLIGATOIRE");
            }
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("code", c.code());
            l.put("decision", dc);
            l.put("motif", nettoyer(c.motif()));
            saisis.put(c.code(), l);
        }
        List<String> manquants = codes.stream().filter(c -> !saisis.containsKey(c)).sorted().toList();
        if (!manquants.isEmpty()) {
            throw new BadRequestException("Chaque critère du DAO se décide : " + String.join(", ", manquants) + ".", "CRITERES_INCOMPLETS");
        }
        boolean unEchec = saisis.values().stream().anyMatch(l -> NON_SATISFAIT.equals(l.get("decision")));
        if (QUALIFIE.equals(decision) && unEchec) {
            throw new BadRequestException("Une offre qualifiée satisfait chaque critère.", "QUALIFICATION_INCOHERENTE");
        }
        String motif = nettoyer(q.motif());
        String clause = nettoyer(q.clause());
        if (NON_QUALIFIE.equals(decision) && motif == null) {
            throw new BadRequestException("La non-qualification se motive.", "MOTIF_OBLIGATOIRE");
        }
        if (NON_QUALIFIE.equals(decision) && clause == null) {
            throw new BadRequestException("La non-qualification cite la clause du DAO.", "CLAUSE_OBLIGATOIRE");
        }
        LocalDateTime maintenant = maintenant();
        EvaluationDecision avant = remplacer(idOffre, EvaluationEtape.QUALIFICATION, maintenant);
        decisions.save(new EvaluationDecision(null, idDmc, idOffre, lot, EvaluationEtape.QUALIFICATION, decision, null, motif, clause,
                ecrire(new ArrayList<>(saisis.values())), k, maintenant, null));
        tracer(idDmc, "QUALIFICATION", "Offre n° " + o.numero() + " (" + o.entreprise().raisonSociale() + ") : "
                + (avant == null ? "" : avant.getDecision() + " → ") + decision + (motif == null ? "" : " — " + motif)
                + (clause == null ? "" : " (" + clause + ")"));
        return dto(idDmc, e);
    }

    /** Les critères de post-qualification de l'offre, dérivés de la fiche (art. 20-II : rien d'autre), avec les décisions retenues. */
    List<EvaluationDto.CritereQualification> criteres(Long idDmc, SeanceDto.OffreLue o, Map<String, Map<String, Object>> retenus) {
        FicheMarcheService.EtatVersion v = fiches.etatValide(idDmc).orElse(null);
        Map<String, String> valeurs = v == null || v.etat().getValeurs() == null ? Map.of() : v.etat().getValeurs();
        Integer lot = lotDe(o);
        Set<String> types = o.alertes() == null ? Set.of() : o.alertes().stream().map(SeanceDto.Alerte::type).collect(Collectors.toSet());
        List<EvaluationDto.CritereQualification> out = new ArrayList<>();
        boolean exclue = types.contains("EXCLUSION") || o.entreprise() != null && o.entreprise().exclusion() != null;
        boolean manque = o.piecesManquantes() != null && !o.piecesManquantes().isEmpty();
        String cq01 = valeurLot(valeurs, "B03-CQ-01", lot);
        out.add(critere("JURIDIQUE", "JURIDIQUE", "Capacité juridique : pièces administratives, absence d'exclusion, pouvoirs",
                cq01 == null ? "Pièces administratives exigées par le DAO" : cq01,
                exclue ? "Exclusion de l'ARMP en cours." : manque ? "Pièces manquantes : " + String.join(", ", o.piecesManquantes()) + "." : null,
                exclue || manque ? Boolean.FALSE : null, retenus));
        for (String code : CRITERES_FINANCIERS) {
            String x = valeurLot(valeurs, code, lot);
            if (x != null) {
                String alerte = Set.of("B03-QT-14", "B03-QT-15").contains(code) ? "LIQUIDITE_INSUFFISANTE" : "CA_INSUFFISANT";
                out.add(critere(code, "FINANCIERE", libelle(v, code), x, message(o, alerte), types.contains(alerte) ? Boolean.FALSE : null, retenus));
            }
        }
        for (String code : CRITERES_TECHNIQUES) {
            String x = valeurLot(valeurs, code, lot);
            if (x != null) {
                boolean references = !Set.of("B03-CQ-02", "B03-CQ-06").contains(code);
                out.add(critere(code, "TECHNIQUE", libelle(v, code), x, references ? message(o, "REFERENCES_INSUFFISANTES") : null,
                        references && types.contains("REFERENCES_INSUFFISANTES") ? Boolean.FALSE : null, retenus));
            }
        }
        String materiel = valeurLot(valeurs, "B03-QT-09", lot);
        if (materiel != null || types.contains("MATERIEL_INCOMPLET")) {
            out.add(critere("MATERIEL", "TECHNIQUE", "Matériel exigé", materiel == null ? "Liste du matériel du DAO" : materiel,
                    message(o, "MATERIEL_INCOMPLET"), types.contains("MATERIEL_INCOMPLET") ? Boolean.FALSE : null, retenus));
        }
        String personnel = valeurLot(valeurs, "B03-QT-13", lot);
        if (personnel != null || types.contains("PERSONNEL_INCOMPLET")) {
            out.add(critere("PERSONNEL", "TECHNIQUE", "Personnel clé exigé", personnel == null ? "Liste du personnel du DAO" : personnel,
                    message(o, "PERSONNEL_INCOMPLET"), types.contains("PERSONNEL_INCOMPLET") ? Boolean.FALSE : null, retenus));
        }
        return out;
    }

    private static EvaluationDto.CritereQualification critere(String code, String groupe, String libelle, String exigence, String constat,
            Boolean proposee, Map<String, Map<String, Object>> retenus) {
        Map<String, Object> r = retenus.get(code);
        return new EvaluationDto.CritereQualification(code, groupe, libelle, exigence,
                constat == null ? "Aucune alerte à la séance : à vérifier sur l'offre." : constat, proposee,
                r == null ? null : (String) r.get("decision"), r == null ? null : (String) r.get("motif"));
    }

    private static String valeurLot(Map<String, String> valeurs, String code, Integer lot) {
        String x = valeurs.get(code + "#" + lot);
        if (x == null || x.isBlank()) {
            x = valeurs.get(code);
        }
        return x == null || x.isBlank() ? null : x.trim();
    }

    private static String libelle(FicheMarcheService.EtatVersion v, String code) {
        return v == null || v.champs().get(code) == null ? code : v.champs().get(code).getLibelle();
    }

    private EvaluationDto.Anormale anormaleDto(EvaluationDecision d, SeanceDto.OffreLue o) {
        if (d == null) {
            return null;
        }
        Map<String, Object> c = d.getContenu() == null ? Map.of() : mapper.readValue(d.getContenu(), new TypeReference<Map<String, Object>>() {
        });
        EvaluationDto.Demande j = demandes.findByIdOffreAndTypeOrderByDemandeeLeAscIdAsc(o.idOffre(), EvaluationDemande.JUSTIFICATION).stream()
                .reduce((a, b) -> b).map(x -> demandeDto(x, o.numero())).orElse(null);
        return new EvaluationDto.Anormale(Boolean.TRUE.equals(c.get("suspectee")), d.getDecision(), d.getMotif(), j, d.getPar(),
                internes.nomMembre(d.getPar()), d.getLe());
    }

    private EvaluationDto.Qualification qualificationDto(EvaluationDecision d, SeanceDto.OffreLue o) {
        if (d == null) {
            return null;
        }
        Map<String, Map<String, Object>> retenus = new HashMap<>();
        if (d.getContenu() != null) {
            for (Map<String, Object> l : mapper.readValue(d.getContenu(), new TypeReference<List<Map<String, Object>>>() {
            })) {
                retenus.put(String.valueOf(l.get("code")), l);
            }
        }
        return new EvaluationDto.Qualification(o.idOffre(), o.numero(), criteres(d.getIdDmc(), o, retenus), d.getDecision(), d.getMotif(),
                d.getClause(), d.getPar(), internes.nomMembre(d.getPar()), d.getLe());
    }

    /** La proposition du lot, la post-qualification arrêtée : l'offre qualifiée, ou l'infructuosité. */
    private EvaluationDto.Proposition proposition(Long idDmc, Integer lot, List<SeanceDto.OffreLue> liste) {
        Tour t = tour(idDmc, lot, liste);
        if (t.courante() == null || !QUALIFIE.equals(t.decision())) {
            return new EvaluationDto.Proposition(null, null, null, null, null, null, true);
        }
        SeanceDto.OffreLue o = liste.stream().filter(x -> x.idOffre().equals(t.courante())).findFirst().orElseThrow();
        EvaluationDto.Montant m = montantDto(enVigueur(idDmc, EvaluationEtape.EVALUATION).get(o.idOffre()));
        Object delai = o.acteEngagement() == null ? null : o.acteEngagement().get("delai");
        return new EvaluationDto.Proposition(o.idOffre(), o.numero(), o.entreprise().raisonSociale(), montantDuMarche(m),
                m == null ? null : m.prixLuTtc(), delai == null ? null : String.valueOf(delai), false);
    }

    private static Boolean qualifie(EvaluationDecision d) {
        return d == null ? null : QUALIFIE.equals(d.getDecision());
    }

    /** Le montant du marché d'une offre évaluée : prix corrigé − rabais, hors taxes (la préférence n'y entre jamais). */
    private static java.math.BigDecimal montantDuMarche(EvaluationDto.Montant m) {
        if (m == null || m.prixCorrige() == null) {
            return null;
        }
        return m.prixCorrige().subtract(m.rabais() == null || m.rabais().montant() == null ? java.math.BigDecimal.ZERO : m.rabais().montant());
    }

    /** L'écart en pour cent, au dixième : (montant − référence) / référence ; nul sans référence. */
    private static java.math.BigDecimal ecart(java.math.BigDecimal m, java.math.BigDecimal ref) {
        if (m == null || ref == null || ref.signum() == 0) {
            return null;
        }
        return m.subtract(ref).multiply(java.math.BigDecimal.valueOf(100)).divide(ref, 1, java.math.RoundingMode.HALF_UP);
    }

    /** L'estimation du lot : le montant du lot au plan (ligne allotie), sinon le montant estimatif de la ligne. */
    private java.math.BigDecimal estimation(Long idDmc, Integer lot) {
        Integer idDetail = fiches.etatValide(idDmc).map(v -> v.etat().getIdDetail()).orElse(null);
        if (idDetail == null) {
            return null;
        }
        List<cnm.prs.entity.Lot> lots = new ArrayList<>(lotRepository.findByIdDetail(idDetail));
        lots.sort(java.util.Comparator.comparing(cnm.prs.entity.Lot::getIdLot));
        if (lots.size() > 1 && lot != null && lot >= 1 && lot <= lots.size() && lots.get(lot - 1).getMontLot() != null) {
            return lots.get(lot - 1).getMontLot();
        }
        try {
            cnm.prs.entity.Marche ligne = valeursPpm.lire(idDetail).ligne();
            return ligne == null ? null : ligne.getMontEstim();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<SeanceDto.OffreLue> exigerLot(Long idDmc, Integer lot) {
        List<SeanceDto.OffreLue> liste = parLot(idDmc).get(lot);
        if (liste == null) {
            throw new ResourceNotFoundException("Lot introuvable dans l'évaluation : " + lot + ".");
        }
        return liste;
    }

    // ------------------------------------------------------------------ la vue

    private EvaluationDto dto(Long idDmc, Evaluation e) {
        Map<String, CaoMembre> membres = new LinkedHashMap<>();
        caoMembres.findByIdDmcOrderByRangAscIdMembreAsc(idDmc).stream().filter(m -> m.estMembre() && m.getIdCompte() != null)
                .forEach(m -> membres.put(m.getIdCompte(), m));
        Map<String, EvaluationDeclaration> signees = new HashMap<>();
        declarations.findByIdDmcOrderBySigneeLeAscIdAsc(idDmc).forEach(d -> signees.put(d.getIm(), d));
        List<EvaluationDto.Declaration> decl = new ArrayList<>();
        membres.forEach((k, m) -> {
            EvaluationDeclaration d = signees.get(k);
            decl.add(new EvaluationDto.Declaration(k, internes.nomMembre(k), Boolean.TRUE.equals(m.getPresident()), d == null ? null : d.getSigneeLe(),
                    d == null ? null : d.getConflit(), d == null ? null : d.getPrecision()));
        });
        SeanceDto.Lecture lecture = seance.lecturePourEvaluation(idDmc);
        Map<Integer, List<SeanceDto.OffreLue>> parLot = parLot(lecture);
        Map<String, EvaluationDecision> conformites = enVigueur(idDmc, EvaluationEtape.CONFORMITE);
        Map<String, EvaluationDecision> montants = enVigueur(idDmc, EvaluationEtape.EVALUATION);
        Map<String, EvaluationDecision> anormalesEnVigueur = enVigueur(idDmc, EvaluationEtape.ANORMALES);
        Map<String, EvaluationDecision> qualifications = enVigueur(idDmc, EvaluationEtape.QUALIFICATION);
        List<OffreDto.PieceAttendue> attendues = attendues(idDmc);
        boolean garantie = garantieExigee(idDmc);
        Map<String, Set<String>> nifs = nifsParOffre(idDmc);
        Map<String, Long> enAttente = demandes.findByIdDmcOrderByDemandeeLeAscIdAsc(idDmc).stream()
                .filter(x -> x.getReponduLe() == null && !maintenant().isAfter(x.getEcheance()))
                .collect(Collectors.groupingBy(EvaluationDemande::getIdOffre, Collectors.counting()));
        Map<Integer, Map<String, EvaluationEtape>> arretsParLot = new HashMap<>();
        etapes.findByIdDmcAndRouverteLeIsNullOrderByArreteeLeAscIdAsc(idDmc)
                .forEach(a -> arretsParLot.computeIfAbsent(a.getLot(), x -> new LinkedHashMap<>()).put(a.getEtape(), a));
        List<EvaluationDto.Lot> lots = new ArrayList<>();
        parLot.forEach((lot, liste) -> {
            Map<String, Classe> classement = classer(idDmc, lot, liste);
            Map<String, EvaluationEtape> arrets = arretsParLot.getOrDefault(lot, Map.of());
            String courante = EvaluationEtape.ORDRE.stream().filter(x -> !arrets.containsKey(x)).findFirst().orElse(EvaluationEtape.RAPPORT);
            List<EvaluationDto.EtapeArretee> arretees = EvaluationEtape.ORDRE.stream().filter(arrets::containsKey).map(arrets::get)
                    .map(a -> new EvaluationDto.EtapeArretee(a.getEtape(), a.getArreteePar(), internes.nomMembre(a.getArreteePar()), a.getArreteeLe(),
                            a.getObservation())).toList();
            List<EvaluationDto.OffreEvaluee> evaluees = new ArrayList<>();
            for (SeanceDto.OffreLue o : liste) {
                Map<String, Proposition> proposees = proposer(idDmc, o, liste, nifs, attendues, garantie);
                EvaluationDecision d = conformites.get(o.idOffre());
                Map<String, Map<String, Object>> retenues = new HashMap<>();
                if (d != null && d.getContenu() != null) {
                    for (Map<String, Object> ligne : mapper.readValue(d.getContenu(), new TypeReference<List<Map<String, Object>>>() {
                    })) {
                        retenues.put(String.valueOf(ligne.get("code")), ligne);
                    }
                }
                List<EvaluationDto.Verification> verifs = new ArrayList<>();
                VERIFICATIONS.forEach((code, libelle) -> {
                    Proposition p = proposees.get(code);
                    Map<String, Object> r = retenues.get(code);
                    Boolean satisfaite = r != null && r.containsKey("satisfaite") ? (Boolean) r.get("satisfaite") : p.valeur();
                    verifs.add(new EvaluationDto.Verification(code, libelle, p.valeur(), p.constat(), satisfaite,
                            r == null ? null : (String) r.get("observation")));
                });
                EvaluationDto.Conformite conf = new EvaluationDto.Conformite(verifs, d == null ? null : d.getDecision(),
                        d == null ? null : d.getQualification(), d == null ? null : d.getMotif(), d == null ? null : d.getClause(),
                        d == null ? null : d.getPar(), d == null ? null : internes.nomMembre(d.getPar()), d == null ? null : d.getLe());
                EvaluationDecision ev = montants.get(o.idOffre());
                EvaluationDto.Ecartement ecartee = ecartement(d, ev, anormalesEnVigueur.get(o.idOffre()), qualifications.get(o.idOffre()));
                Classe cl = classement.get(o.idOffre());
                evaluees.add(new EvaluationDto.OffreEvaluee(o.idOffre(), o.numero(), new EvaluationDto.Entreprise(o.entreprise().nif(),
                        o.entreprise().raisonSociale()), conf, montantDto(ev), anormaleDto(anormalesEnVigueur.get(o.idOffre()), o),
                        qualificationDto(qualifications.get(o.idOffre()), o), cl == null ? null : cl.rang(),
                        cl == null ? null : cl.exAequo(), ecartee, enAttente.getOrDefault(o.idOffre(), 0L).intValue()));
            }
            lots.add(new EvaluationDto.Lot(lot, courante, arretees, evaluees, arrets.containsKey(EvaluationEtape.QUALIFICATION)
                    ? proposition(idDmc, lot, liste) : null));
        });
        List<EvaluationDto.NonEvaluee> non = lecture.nonOuvertes().stream()
                .map(n -> new EvaluationDto.NonEvaluee(n.numero(), n.entreprise(), n.etat(), n.motif())).toList();
        return new EvaluationDto(idDmc, e.getEtat(), e.getOuverteLe(), e.getOuvertePar(), decl, lots, non);
    }

    private EvaluationDto.Demande demandeDto(EvaluationDemande x, Integer numero) {
        String etat = x.getReponduLe() != null ? "REPONDUE" : maintenant().isAfter(x.getEcheance()) ? "EXPIREE" : "EN_ATTENTE";
        return new EvaluationDto.Demande(x.getId(), x.getIdOffre(), numero, x.getType(), x.getQuestion(), x.getDelaiJours(), x.getEcheance(),
                x.getDemandeeLe(), etat, x.getReponse(), x.getReponseNom(), x.getReponseTaille(), x.getReponduLe());
    }

    // ------------------------------------------------------------------ outils

    /** Les offres évaluées par lot (les offres ouvertes ; une procédure non allotie a un lot 1). */
    private Map<Integer, List<SeanceDto.OffreLue>> parLot(Long idDmc) {
        return parLot(seance.lecturePourEvaluation(idDmc));
    }

    private static Map<Integer, List<SeanceDto.OffreLue>> parLot(SeanceDto.Lecture l) {
        Map<Integer, List<SeanceDto.OffreLue>> m = new TreeMap<>();
        l.offres().forEach(o -> m.computeIfAbsent(lotDe(o), x -> new ArrayList<>()).add(o));
        return m;
    }

    static Integer lotDe(SeanceDto.OffreLue o) {
        return o.lot() == null ? 1 : o.lot();
    }

    private SeanceDto.OffreLue offreEvaluee(Long idDmc, String idOffre) {
        return seance.lecturePourEvaluation(idDmc).offres().stream().filter(o -> o.idOffre().equals(idOffre)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable dans l'évaluation : " + idOffre + "."));
    }

    private Map<String, EvaluationEtape> arretees(Long idDmc, Integer lot) {
        Map<String, EvaluationEtape> m = new LinkedHashMap<>();
        etapes.findByIdDmcAndRouverteLeIsNullOrderByArreteeLeAscIdAsc(idDmc).stream().filter(a -> Objects.equals(a.getLot(), lot))
                .forEach(a -> m.put(a.getEtape(), a));
        return m;
    }

    private Map<String, EvaluationDecision> enVigueur(Long idDmc, String etape) {
        Map<String, EvaluationDecision> m = new HashMap<>();
        decisions.findByIdDmcAndRemplaceeLeIsNullOrderByLeAscIdAsc(idDmc).stream().filter(d -> etape.equals(d.getEtape()))
                .forEach(d -> m.put(d.getIdOffre(), d));
        return m;
    }

    /** Marque remplacée la décision en vigueur de l'offre à l'étape ; la rend (nulle s'il n'y en avait pas). */
    private EvaluationDecision remplacer(String idOffre, String etape, LocalDateTime le) {
        EvaluationDecision avant = null;
        for (EvaluationDecision d : decisions.findByIdOffreAndEtapeAndRemplaceeLeIsNull(idOffre, etape)) {
            d.setRemplaceeLe(le);
            decisions.saveAndFlush(d);
            avant = d;
        }
        return avant;
    }

    /** Les NIF de chaque offre déposée de la procédure : son entreprise et les membres du groupement déclarés au dépôt. */
    private Map<String, Set<String>> nifsParOffre(Long idDmc) {
        Map<String, Set<String>> m = new HashMap<>();
        for (Offre o : offres.findByIdDmcOrderByNumeroAscDateCreationAsc(idDmc)) {
            Set<String> s = new java.util.HashSet<>();
            if (o.getNif() != null) {
                s.add(o.getNif().trim());
            }
            ChampFicheMarche.liste(o.getGroupementNifs()).forEach(n -> s.add(n.trim()));
            m.put(o.getIdOffre(), s);
        }
        return m;
    }

    private static String message(SeanceDto.OffreLue o, String type) {
        return o.alertes() == null ? null : o.alertes().stream().filter(a -> type.equals(a.type())).map(SeanceDto.Alerte::message).findFirst().orElse(null);
    }

    private List<OffreDto.PieceAttendue> attendues(Long idDmc) {
        try {
            return procedures.piecesAttendues(idDmc);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private boolean garantieExigee(Long idDmc) {
        return fiches.etatValide(idDmc).map(v -> v.etat().getCadrage()).map(c -> "OUI".equals(String.valueOf(c.get("garantieSoumission"))))
                .orElse(false);
    }

    private Integer delaiFiche(Long idDmc) {
        Map<String, String> v = fiches.etatValide(idDmc).map(x -> x.etat().getValeurs()).orElse(Map.of());
        for (String code : CHAMPS_DELAI) {
            String x = v == null ? null : v.get(code);
            if (x != null && !x.isBlank()) {
                try {
                    return Integer.valueOf(x.trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private Offre sienne(String idCandidat, String idOffre) {
        Offre o = offres.findById(idOffre).orElseThrow(() -> new ResourceNotFoundException("Offre introuvable : " + idOffre + "."));
        if (!o.getIdCandidat().equals(idCandidat)) {
            throw new AccessDeniedException("Cette offre n'est pas la vôtre.");
        }
        return o;
    }

    private Evaluation exigerEvaluation(Long idDmc) {
        exigerDmc(idDmc);
        return evaluations.findById(idDmc).orElseThrow(() -> new ResourceNotFoundException("L'évaluation de cette procédure n'est pas ouverte."));
    }

    private Evaluation exigerEnCours(Long idDmc) {
        Evaluation e = exigerEvaluation(idDmc);
        if (!Evaluation.EN_COURS.equals(e.getEtat())) {
            throw new BusinessRuleException("L'évaluation est close.", "EVALUATION_CLOSE");
        }
        return e;
    }

    private static String exigerEtape(String etape) {
        String e = etape == null ? null : etape.trim().toUpperCase(Locale.ROOT);
        if (e == null || !EvaluationEtape.ORDRE.contains(e)) {
            throw new BadRequestException("Étape inconnue : " + etape + " (CONFORMITE, EVALUATION, ANORMALES, QUALIFICATION).", "ETAPE_INCONNUE");
        }
        return e;
    }

    /** Un membre de la CAO déclaré sans conflit : 403 hors CAO, 409 {@code DECLARATION_MANQUANTE}, 403 {@code MEMBRE_EN_CONFLIT}. */
    private String exigerDecideur(Long idDmc) {
        String k = membreAppelant(idDmc);
        if (k == null) {
            throw new AccessDeniedException("Les décisions d'évaluation se prennent par les membres de la commission d'appel d'offres.");
        }
        EvaluationDeclaration d = declarations.findByIdDmcAndIm(idDmc, k).orElseThrow(() -> new BusinessRuleException(
                "Signez d'abord votre déclaration d'absence de conflit d'intérêts et de confidentialité.", "DECLARATION_MANQUANTE"));
        if (Boolean.TRUE.equals(d.getConflit())) {
            throw new AccesReserveException("Vous avez déclaré un conflit d'intérêts : vous ne décidez rien sur cette procédure.", "MEMBRE_EN_CONFLIT");
        }
        return k;
    }

    /** Le président de la CAO, déclaré sans conflit : 403 sinon. */
    private String exigerPresident(Long idDmc) {
        String k = exigerDecideur(idDmc);
        boolean president = caoMembres.findByIdDmcOrderByRangAscIdMembreAsc(idDmc).stream()
                .anyMatch(m -> k.equals(m.getIdCompte()) && Boolean.TRUE.equals(m.getPresident()));
        if (!president) {
            throw new AccessDeniedException("Les étapes de l'évaluation s'arrêtent et se rouvrent par le président de la commission.");
        }
        return k;
    }

    private String membreAppelant(Long idDmc) {
        String ref = CurrentUser.ref().orElse(null);
        return ref != null && TypeActeur.MEMBRE_CAO.name().equals(CurrentUser.acteurType().orElse(null))
                && internes.membresCao(idDmc).contains(ref) ? ref : null;
    }

    /** CAO, responsable, PRMP et UGPM de la fiche (P5). */
    private void exigerLecteur(Long idDmc) {
        exigerDmc(idDmc);
        if (membreAppelant(idDmc) != null || CurrentUser.profil().isPresent() && internes.estTitulaire(idDmc)) {
            return;
        }
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p == ProfilUtilisateur.PRMP || p == ProfilUtilisateur.UGPM) {
            fiches.controlerLecture(idDmc);
            return;
        }
        throw new AccessDeniedException("L'évaluation se lit par la commission, le responsable de la procédure, la PRMP et l'UGPM.");
    }

    private void exigerPrmp(Long idDmc) {
        exigerDmc(idDmc);
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException("Les demandes aux candidats se font par la PRMP de la fiche (art. 35-VI).");
        }
        fiches.controlerLecture(idDmc);
    }

    private void exigerDmc(Long idDmc) {
        if (idDmc == null || !dmcRepository.existsById(idDmc)) {
            throw new ResourceNotFoundException("DMC introuvable : " + idDmc);
        }
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).withNano(0);
    }

    private static String acteur() {
        return CurrentUser.ref().or(CurrentUser::login).orElse(null);
    }

    private void tracer(Long idDmc, String action, String detail) {
        journal.save(new EvaluationJournal(null, idDmc, maintenant(), acteur(), action, detail));
    }

    private String ecrire(Object o) {
        return mapper.writeValueAsString(o);
    }

    private static String nettoyer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Map<String, String> ordre(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

}
