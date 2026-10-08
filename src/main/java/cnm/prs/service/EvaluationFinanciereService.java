package cnm.prs.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.EvaluationDto;
import cnm.prs.dto.FinanciereDto;
import cnm.prs.dto.TechniqueDto;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.ClassementPi;
import cnm.prs.entity.EvaluationFinanciere;
import cnm.prs.entity.EvaluationJournal;
import cnm.prs.entity.Negociation;
import cnm.prs.entity.Offre;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ClassementPiRepository;
import cnm.prs.repository.EvaluationFinanciereRepository;
import cnm.prs.repository.EvaluationJournalRepository;
import cnm.prs.repository.NegociationRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.SeanceFinanciereRepository;
import cnm.prs.security.CurrentUser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ <strong>L'évaluation financière et le classement des prestations intellectuelles</strong>, lot 3, tranche PI-d2a (demande front du
 * 2026-10-07, §B5 ; V89 ; Q5 tranchée par le dossier type, DPIC-PI §9.4-9.5 ; arbitrage du pilote du 2026-10-08 : les dépenses
 * remboursables sont saisies par la commission) :
 * <ul>
 *   <li>un membre déclaré sans conflit saisit chaque proposition financière <strong>ouverte</strong> : prix lu hors taxes (celui de
 *   l'acte d'engagement, à défaut saisi), corrections arithmétiques (règles du lot 1), dépenses remboursables ; ou le refus d'une
 *   correction par le candidat, qui l'écarte ;</li>
 *   <li>le serveur classe selon la méthode ({@code B02-MS-01}) : <em>qualité-coût</em> {@code Sf = 100 × Fm / F} (F : prix corrigé hors
 *   dépenses remboursables, Fm : le plus bas) et {@code S = T × wT + Sf × wF} ({@code B06-CS-02}, {@code B06-CS-03}, totalisant 1 ou
 *   100) ; <em>budget prédéterminé</em> la meilleure note technique parmi les propositions dans le budget ({@code B05-PF-13}, prix
 *   corrigé hors taxes) ; <em>moindre coût</em> le montant comparé le plus bas ; <em>qualité technique exclusivement</em> et
 *   <em>qualification du consultant</em> la note technique (la financière n'entre que dans la négociation) ;</li>
 *   <li>une égalité se départage par un membre, avec un motif ; le président arrête le classement du lot, ou le rouvre (motif) tant
 *   qu'aucune négociation n'est engagée.</li>
 * </ul>
 */
@Service
@Transactional
public class EvaluationFinanciereService {

    public static final String QUALITE_COUT = "QUALITE_COUT";
    public static final String BUDGET = "BUDGET";
    public static final String MOINDRE_COUT = "MOINDRE_COUT";
    public static final String QUALITE_TECHNIQUE = "QUALITE_TECHNIQUE";
    public static final String QUALIFICATION = "QUALIFICATION";

    public static final String NON_OUVERTE = "NON_OUVERTE";
    public static final String A_EVALUER = "A_EVALUER";
    public static final String EVALUEE = "EVALUEE";
    public static final String ECARTEE = "ECARTEE";
    public static final String HORS_BUDGET = "HORS_BUDGET";

    static final String METHODE = "B02-MS-01";
    static final String POIDS_T = "B06-CS-02";
    static final String POIDS_F = "B06-CS-03";
    static final String BUDGET_DISPONIBLE = "B05-PF-13";
    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final EvaluationTechniqueService technique;
    private final EvaluationService evaluation;
    private final SeanceService seance;
    private final OffreRepository offres;
    private final EvaluationFinanciereRepository saisies;
    private final ClassementPiRepository classements;
    private final NegociationRepository negociations;
    private final SeanceFinanciereRepository seancesFinancieres;
    private final FicheMarcheService fiches;
    private final ParametresInternesService internes;
    private final EvaluationJournalRepository journal;
    private final ObjectMapper mapper;
    private final Clock horloge;

    public EvaluationFinanciereService(EvaluationTechniqueService technique, EvaluationService evaluation, SeanceService seance,
            OffreRepository offres, EvaluationFinanciereRepository saisies, ClassementPiRepository classements, NegociationRepository negociations,
            SeanceFinanciereRepository seancesFinancieres, FicheMarcheService fiches, ParametresInternesService internes,
            EvaluationJournalRepository journal, ObjectMapper mapper, Clock horloge) {
        this.technique = technique;
        this.evaluation = evaluation;
        this.seance = seance;
        this.offres = offres;
        this.saisies = saisies;
        this.classements = classements;
        this.negociations = negociations;
        this.seancesFinancieres = seancesFinancieres;
        this.fiches = fiches;
        this.internes = internes;
        this.journal = journal;
        this.mapper = mapper;
        this.horloge = horloge;
    }

    /** La méthode de sélection, en code ; nul si la fiche n'en porte pas une connue. */
    static String codeMethode(String methode) {
        if (methode == null) {
            return null;
        }
        String m = methode.trim().toLowerCase(Locale.FRENCH);
        if (m.startsWith("qualité technique exclusivement")) {
            return QUALITE_TECHNIQUE;
        }
        if (m.startsWith("qualité technique")) {
            return QUALITE_COUT;
        }
        if (m.startsWith("budget")) {
            return BUDGET;
        }
        if (m.startsWith("meilleure proposition financière") || m.startsWith("moindre coût")) {
            return MOINDRE_COUT;
        }
        if (m.startsWith("qualification")) {
            return QUALIFICATION;
        }
        return null;
    }

    /** Les méthodes où seule la financière du premier classé s'ouvre, et où le classement est celui de la note technique (Q1). */
    static boolean parNoteTechnique(String code) {
        return QUALITE_TECHNIQUE.equals(code) || QUALIFICATION.equals(code);
    }

    // ------------------------------------------------------------------ lecture

    /** L'évaluation financière et le classement : CAO, responsable, PRMP, UGPM ; 409 {@code CATEGORIE_SANS_NOTATION_TECHNIQUE}. */
    @Transactional(readOnly = true)
    public FinanciereDto lire(Long idDmc) {
        evaluation.controlerLecture(idDmc);
        return calculer(idDmc);
    }

    /** Les corrections arithmétiques proposées d'une proposition financière ouverte (depuis ses formulaires) ; 404 hors du classement. */
    @Transactional(readOnly = true)
    public List<EvaluationDto.CorrectionProposee> correctionsProposees(Long idDmc, String idFinanciere) {
        evaluation.controlerLecture(idDmc);
        ligne(calculer(idDmc), idFinanciere);
        return seance.correctionsProposees(idFinanciere).stream()
                .map(c -> new EvaluationDto.CorrectionProposee(c.ligne(), c.libelle(), c.avant(), c.apres(), c.regle())).toList();
    }

    // ------------------------------------------------------------------ la saisie

    /**
     * La saisie d'une proposition financière ouverte : membre déclaré sans conflit ; 404 hors du classement ; 409
     * {@code FINANCIERE_NON_OUVERTE}, {@code CLASSEMENT_ARRETE}, {@code NEGOCIATION_CONCLUE}, {@code EVALUATION_CLOSE} ; 400
     * {@code PRIX_LU_OBLIGATOIRE}, {@code CORRECTION_INVALIDE}, {@code REGLE_INCONNUE}, {@code REMBOURSABLES_INVALIDES},
     * {@code MOTIF_OBLIGATOIRE}, {@code CLAUSE_OBLIGATOIRE}. Une saisie remplace la précédente.
     */
    public FinanciereDto saisir(Long idDmc, String idFinanciere, FinanciereDto.SaisieRequest r) {
        String k = technique.exigerDecideur(idDmc);
        technique.exigerEnCours(idDmc);
        FinanciereDto d = calculer(idDmc);
        LigneDuLot x = ligne(d, idFinanciere);
        if (!x.ligne().financiereOuverte()) {
            throw new BusinessRuleException("Cette proposition financière n'est pas ouverte.", "FINANCIERE_NON_OUVERTE");
        }
        if (parNoteTechnique(d.codeMethode())) {
            boolean conclue = negociations.findByIdDmcAndLotOrderByIdAsc(idDmc, x.lot()).stream()
                    .anyMatch(n -> Negociation.REUSSIE.equals(n.getEtat()) && n.getIdOffre().equals(x.ligne().idOffre()));
            if (conclue) {
                throw new BusinessRuleException("La négociation avec ce candidat est conclue : son montant ne change plus.", "NEGOCIATION_CONCLUE");
            }
        } else if (arrete(idDmc, x.lot())) {
            throw new BusinessRuleException("Le classement de ce lot est arrêté : le président le rouvre, avec un motif.", "CLASSEMENT_ARRETE");
        }
        if (r == null) {
            throw new BadRequestException("Le corps de la saisie est attendu.", "CORRECTION_INVALIDE");
        }
        Offre fin = offres.findById(idFinanciere).orElseThrow();
        Map<String, Object> ae = acteEngagement(fin);
        BigDecimal lu = ae == null ? null : SeanceService.montant(ae.get("montantHt"));
        BigDecimal prixLu = lu != null ? lu : r.prixLu();
        if (prixLu == null || prixLu.signum() < 0) {
            throw new BadRequestException("L'acte d'engagement ne porte pas de montant HT : saisissez le prix lu hors taxes.", "PRIX_LU_OBLIGATOIRE");
        }
        List<EvaluationDto.Correction> corrections = new ArrayList<>();
        BigDecimal prixCorrige = prixLu;
        for (EvaluationDto.Correction c : r.corrections() == null ? List.<EvaluationDto.Correction>of() : r.corrections()) {
            if (c == null || c.avant() == null || c.apres() == null || nettoyer(c.libelle()) == null) {
                throw new BadRequestException("Une correction porte un libellé, un montant avant et un montant après.", "CORRECTION_INVALIDE");
            }
            String regle = c.regle() == null ? null : c.regle().trim().toUpperCase(Locale.ROOT);
            if (!EvaluationService.REGLES_CORRECTION.contains(regle)) {
                throw new BadRequestException("Règle de correction inconnue : " + c.regle() + " (PU_PREVAUT, LETTRES_PREVALENT, REPORT, AUTRE).",
                        "REGLE_INCONNUE");
            }
            boolean retenue = Boolean.TRUE.equals(c.retenue());
            corrections.add(new EvaluationDto.Correction(c.ligne(), c.libelle().trim(), c.avant(), c.apres(), regle, retenue));
            if (retenue) {
                prixCorrige = prixCorrige.add(c.apres().subtract(c.avant()));
            }
        }
        BigDecimal remboursables = r.remboursables() == null ? BigDecimal.ZERO : r.remboursables();
        if (remboursables.signum() < 0 || remboursables.compareTo(prixCorrige) > 0) {
            throw new BadRequestException("Les dépenses remboursables sont un montant hors taxes, positif, au plus le prix corrigé.",
                    "REMBOURSABLES_INVALIDES");
        }
        String refusMotif = null;
        String refusClause = null;
        if (r.refusCandidat() != null) {
            refusMotif = nettoyer(r.refusCandidat().motif());
            refusClause = nettoyer(r.refusCandidat().clause());
            if (refusMotif == null) {
                throw new BadRequestException("Le refus d'une correction par le candidat exige un motif.", "MOTIF_OBLIGATOIRE");
            }
            if (refusClause == null) {
                throw new BadRequestException("Le refus d'une correction écarte la proposition si les IC le prévoient : citez la clause.",
                        "CLAUSE_OBLIGATOIRE");
            }
        }
        EvaluationFinanciere s = saisies.findByIdOffre(idFinanciere).orElseGet(EvaluationFinanciere::new);
        boolean remplace = s.getId() != null;
        s.setIdDmc(idDmc);
        s.setIdOffre(idFinanciere);
        s.setLot(fin.getLot());
        s.setPrixLu(prixLu);
        s.setPrixLuTtc(ae == null ? null : SeanceService.montant(ae.get("montantTtc")));
        s.setCorrections(corrections.isEmpty() ? null : mapper.writeValueAsString(corrections));
        s.setPrixCorrige(prixCorrige);
        s.setRemboursables(remboursables);
        s.setMotifRemboursables(nettoyer(r.motifRemboursables()));
        s.setRefusMotif(refusMotif);
        s.setRefusClause(refusClause);
        s.setPar(k);
        s.setLe(maintenant());
        saisies.save(s);
        tracer(idDmc, "MONTANT_FINANCIER", "Proposition n° " + x.ligne().numero() + " (" + x.ligne().raisonSociale() + ") : "
                + (refusMotif != null ? "écartée, le candidat refuse la correction : " + refusMotif + " (" + refusClause + ")"
                        : "prix lu " + lisible(prixLu) + ", corrigé " + lisible(prixCorrige) + ", dont dépenses remboursables " + lisible(remboursables))
                + (remplace ? " (remplace la saisie précédente)" : ""));
        return calculer(idDmc);
    }

    // ------------------------------------------------------------------ le départage, l'arrêt, la réouverture

    /**
     * Le départage d'une égalité (Q5 du lot 1 : la commission départage, avec un motif) : membre déclaré sans conflit ; 400
     * {@code ORDRE_INVALIDE}, {@code MOTIF_OBLIGATOIRE} ; 409 {@code CLASSEMENT_ARRETE}. L'ordre cite les propositions techniques.
     */
    public FinanciereDto departager(Long idDmc, Integer lot, FinanciereDto.DepartageRequest r) {
        String k = technique.exigerDecideur(idDmc);
        technique.exigerEnCours(idDmc);
        FinanciereDto.Lot l = lot(calculer(idDmc), lot);
        if (arrete(idDmc, lot)) {
            throw new BusinessRuleException("Le classement de ce lot est arrêté.", "CLASSEMENT_ARRETE");
        }
        String motif = r == null ? null : nettoyer(r.motif());
        if (motif == null) {
            throw new BadRequestException("Le départage exige un motif.", "MOTIF_OBLIGATOIRE");
        }
        List<String> ordre = r.ordre() == null ? List.of() : r.ordre();
        Set<String> classees = new HashSet<>(l.propositions().stream().filter(p -> p.rang() != null).map(FinanciereDto.Ligne::idOffre).toList());
        if (ordre.size() < 2 || new HashSet<>(ordre).size() != ordre.size() || !classees.containsAll(ordre)) {
            throw new BadRequestException("L'ordre cite au moins deux propositions classées du lot, chacune une fois.", "ORDRE_INVALIDE");
        }
        ClassementPi c = classements.findById(new ClassementPi.Cle(idDmc, lot)).orElseGet(() -> new ClassementPi(idDmc, lot));
        c.setDepartage(String.join(",", ordre));
        c.setMotifDepartage(motif);
        c.setDepartagePar(k);
        c.setDepartageLe(maintenant());
        classements.save(c);
        tracer(idDmc, "DEPARTAGE_FINANCIER", "Lot " + lot + " : " + String.join(" puis ", ordre.stream().map(id -> "n° " + l.propositions().stream()
                .filter(p -> p.idOffre().equals(id)).findFirst().map(FinanciereDto.Ligne::numero).orElse(null)).toList()) + " — " + motif);
        return calculer(idDmc);
    }

    /**
     * Le président arrête le classement d'un lot : 409 {@code SEANCE_FINANCIERE_NON_OUVERTE}, {@code CLASSEMENT_ARRETE},
     * {@code EVALUATION_FINANCIERE_INCOMPLETE} ({@code details.offres} : numéros), {@code POIDS_INVALIDES},
     * {@code BUDGET_DISPONIBLE_ABSENT}, {@code EGALITE_NON_DEPARTAGEE} ({@code details.offres}), {@code AUCUNE_PROPOSITION_CLASSEE}.
     */
    public FinanciereDto arreter(Long idDmc, Integer lot, TechniqueDto.ArretRequest r) {
        String k = technique.exigerPresident(idDmc);
        technique.exigerEnCours(idDmc);
        if (!seancesFinancieres.existsByIdDmc(idDmc)) {
            throw new BusinessRuleException("La seconde séance n'est pas ouverte.", "SEANCE_FINANCIERE_NON_OUVERTE");
        }
        FinanciereDto d = calculer(idDmc);
        FinanciereDto.Lot l = lot(d, lot);
        if (arrete(idDmc, lot)) {
            throw new BusinessRuleException("Le classement de ce lot est arrêté.", "CLASSEMENT_ARRETE");
        }
        if (!parNoteTechnique(d.codeMethode())) {
            List<Integer> manquantes = l.propositions().stream().filter(p -> A_EVALUER.equals(p.statut())).map(FinanciereDto.Ligne::numero).toList();
            if (!manquantes.isEmpty()) {
                throw new BusinessRuleException("Chaque proposition financière ouverte doit être évaluée : n° " + manquantes + ".",
                        "EVALUATION_FINANCIERE_INCOMPLETE", null, Map.of("offres", manquantes));
            }
        }
        if (QUALITE_COUT.equals(d.codeMethode()) && d.poidsTechnique() == null) {
            throw new BusinessRuleException("Les poids des propositions technique et financière (" + POIDS_T + ", " + POIDS_F + ") doivent "
                    + "totaliser 1 (ou 100).", "POIDS_INVALIDES");
        }
        if (BUDGET.equals(d.codeMethode()) && d.budget() == null) {
            throw new BusinessRuleException("La méthode du budget prédéterminé exige le budget disponible (" + BUDGET_DISPONIBLE + ").",
                    "BUDGET_DISPONIBLE_ABSENT");
        }
        List<Integer> egales = l.propositions().stream().filter(FinanciereDto.Ligne::egalite).map(FinanciereDto.Ligne::numero).toList();
        if (!egales.isEmpty()) {
            throw new BusinessRuleException("Des propositions sont à égalité : départagez-les d'abord (n° " + egales + ").", "EGALITE_NON_DEPARTAGEE",
                    null, Map.of("offres", egales));
        }
        if (l.propositions().stream().noneMatch(p -> p.rang() != null)) {
            throw new BusinessRuleException("Aucune proposition n'est classée sur ce lot.", "AUCUNE_PROPOSITION_CLASSEE");
        }
        ClassementPi c = classements.findById(new ClassementPi.Cle(idDmc, lot)).orElseGet(() -> new ClassementPi(idDmc, lot));
        c.setArreteLe(maintenant());
        c.setArretePar(k);
        c.setObservation(r == null ? null : nettoyer(r.observation()));
        classements.save(c);
        FinanciereDto apres = calculer(idDmc);
        tracer(idDmc, "CLASSEMENT_ARRETE", "Lot " + lot + " : " + String.join(" ; ", lot(apres, lot).propositions().stream()
                .filter(p -> p.rang() != null).sorted(Comparator.comparing(FinanciereDto.Ligne::rang))
                .map(p -> "rang " + p.rang() + " n° " + p.numero() + (p.scoreCombine() == null ? "" : " (" + lisible(p.scoreCombine()) + ")")).toList()));
        return apres;
    }

    /** Le président rouvre le classement d'un lot (motif) : 409 {@code CLASSEMENT_NON_ARRETE}, {@code NEGOCIATION_ENGAGEE}. */
    public FinanciereDto rouvrir(Long idDmc, Integer lot, TechniqueDto.ReouvertureRequest r) {
        technique.exigerPresident(idDmc);
        technique.exigerEnCours(idDmc);
        lot(calculer(idDmc), lot);
        String motif = r == null ? null : nettoyer(r.motif());
        if (motif == null) {
            throw new BadRequestException("La réouverture exige son motif.", "MOTIF_OBLIGATOIRE");
        }
        ClassementPi c = classements.findById(new ClassementPi.Cle(idDmc, lot)).filter(x -> x.getArreteLe() != null)
                .orElseThrow(() -> new BusinessRuleException("Le classement de ce lot n'est pas arrêté.", "CLASSEMENT_NON_ARRETE"));
        if (!negociations.findByIdDmcAndLotOrderByIdAsc(idDmc, lot).isEmpty()) {
            throw new BusinessRuleException("Une négociation est engagée sur ce lot : le classement ne se rouvre plus.", "NEGOCIATION_ENGAGEE");
        }
        c.setArreteLe(null);
        c.setArretePar(null);
        c.setRouvertLe(maintenant());
        c.setMotifReouverture(motif);
        classements.save(c);
        tracer(idDmc, "CLASSEMENT_ROUVERT", "Lot " + lot + " : " + motif);
        return calculer(idDmc);
    }

    // ------------------------------------------------------------------ le calcul

    /** Le classement, sans garde (la négociation et la séance complémentaire le lisent). */
    @Transactional(readOnly = true)
    public FinanciereDto calculer(Long idDmc) {
        FicheMarcheService.EtatVersion v = fiches.etatValide(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("La fiche de la procédure " + idDmc + " n'a pas de version validée."));
        TechniqueDto t = technique.resultats(idDmc);
        String methode = v.valeur(METHODE);
        String code = codeMethode(methode);
        BigDecimal[] poids = poids(v.valeur(POIDS_T), v.valeur(POIDS_F));
        BigDecimal budget = ControlesFicheMarche.nombre(v.valeur(BUDGET_DISPONIBLE));
        List<Offre> toutes = offres.findByIdDmcOrderByNumeroAscDateCreationAsc(idDmc);
        Map<String, EvaluationFinanciere> parOffre = new HashMap<>();
        saisies.findByIdDmc(idDmc).forEach(s -> parOffre.put(s.getIdOffre(), s));
        List<FinanciereDto.Lot> lots = new ArrayList<>();
        for (TechniqueDto.Lot l : t.lots()) {
            ClassementPi c = classements.findById(new ClassementPi.Cle(idDmc, l.lot())).orElse(null);
            List<FinanciereDto.Ligne> lignes = new ArrayList<>();
            for (TechniqueDto.Offre o : l.offres()) {
                if (!EvaluationTechniqueService.QUALIFIEE.equals(o.statut())) {
                    continue;
                }
                Offre tech = toutes.stream().filter(x -> x.getIdOffre().equals(o.idOffre())).findFirst().orElse(null);
                Offre fin = financiere(toutes, tech);
                boolean ouverte = fin != null && fin.getOuverteLe() != null && fin.getLecture() != null;
                EvaluationFinanciere s = fin == null ? null : parOffre.get(fin.getIdOffre());
                String statut;
                String motif = null;
                BigDecimal compare = null;
                if (!ouverte) {
                    statut = NON_OUVERTE;
                    motif = fin == null ? "Aucune enveloppe financière déposée." : "Enveloppe financière non ouverte.";
                } else if (s == null) {
                    statut = A_EVALUER;
                } else if (s.getRefusMotif() != null) {
                    statut = ECARTEE;
                    motif = "Le candidat refuse la correction : " + s.getRefusMotif() + " (" + s.getRefusClause() + ").";
                } else {
                    compare = s.getPrixCorrige().subtract(s.getRemboursables());
                    if (BUDGET.equals(code) && budget != null && s.getPrixCorrige().compareTo(budget) > 0) {
                        statut = HORS_BUDGET;
                        motif = "Prix corrigé de " + lisible(s.getPrixCorrige()) + " Ariary HT, au-delà du budget disponible de " + lisible(budget) + ".";
                    } else {
                        statut = EVALUEE;
                    }
                }
                lignes.add(new FinanciereDto.Ligne(o.idOffre(), fin == null ? null : fin.getIdOffre(), o.numero(), o.nif(), o.raisonSociale(),
                        o.total(), o.rang(), ouverte, s == null ? null : saisieDto(s), statut, motif, compare, null, null, null, false));
            }
            lots.add(new FinanciereDto.Lot(l.lot(), c == null ? null : arretDto(c), c == null || c.getDepartage() == null ? null
                    : new FinanciereDto.Departage(ChampFicheMarche.liste(c.getDepartage()), c.getMotifDepartage(), c.getDepartagePar(),
                            internes.nomMembre(c.getDepartagePar()), c.getDepartageLe()),
                    classer(code, poids, lignes, c == null ? List.of() : ChampFicheMarche.liste(c.getDepartage()))));
        }
        return new FinanciereDto(idDmc, methode, code, poids == null ? null : poids[0], poids == null ? null : poids[1], budget, lots);
    }

    /** Les scores et le rang, selon la méthode ; l'égalité non départagée garde le même rang et se signale. */
    static List<FinanciereDto.Ligne> classer(String code, BigDecimal[] poids, List<FinanciereDto.Ligne> lignes, List<String> departage) {
        List<FinanciereDto.Ligne> out = new ArrayList<>(lignes);
        if (code == null) {
            return out;
        }
        boolean parT = parNoteTechnique(code);
        List<FinanciereDto.Ligne> classables = out.stream().filter(p -> parT ? !ECARTEE.equals(p.statut()) : EVALUEE.equals(p.statut())).toList();
        Map<String, BigDecimal> sf = new HashMap<>();
        Map<String, BigDecimal> s = new HashMap<>();
        if (QUALITE_COUT.equals(code)) {
            BigDecimal fm = classables.stream().map(FinanciereDto.Ligne::montantCompare).filter(m -> m.signum() > 0).min(BigDecimal::compareTo).orElse(null);
            for (FinanciereDto.Ligne p : classables) {
                BigDecimal f = p.montantCompare();
                BigDecimal score = fm == null || f.signum() <= 0 ? null : fm.multiply(CENT).divide(f, 2, RoundingMode.HALF_UP);
                sf.put(p.idOffre(), score);
                if (score != null && poids != null) {
                    s.put(p.idOffre(), p.noteTechnique().multiply(poids[0]).add(score.multiply(poids[1])).setScale(2, RoundingMode.HALF_UP));
                }
            }
            if (poids == null) {
                classables = List.of();
            }
        }
        Comparator<FinanciereDto.Ligne> ordre = switch (code) {
            case QUALITE_COUT -> Comparator.comparing((FinanciereDto.Ligne p) -> s.getOrDefault(p.idOffre(), BigDecimal.ZERO)).reversed();
            case MOINDRE_COUT -> Comparator.comparing(FinanciereDto.Ligne::montantCompare);
            default -> Comparator.comparing(FinanciereDto.Ligne::noteTechnique).reversed();
        };
        java.util.function.Function<FinanciereDto.Ligne, BigDecimal> cle = switch (code) {
            case QUALITE_COUT -> p -> s.getOrDefault(p.idOffre(), BigDecimal.ZERO);
            case MOINDRE_COUT -> FinanciereDto.Ligne::montantCompare;
            default -> FinanciereDto.Ligne::noteTechnique;
        };
        List<FinanciereDto.Ligne> tries = classables.stream().sorted(ordre).toList();
        Map<String, Integer> rangs = new LinkedHashMap<>();
        Set<String> egales = new HashSet<>();
        int i = 0;
        while (i < tries.size()) {
            int j = i;
            while (j + 1 < tries.size() && cle.apply(tries.get(j + 1)).compareTo(cle.apply(tries.get(i))) == 0) {
                j++;
            }
            List<FinanciereDto.Ligne> groupe = tries.subList(i, j + 1);
            List<String> ids = groupe.stream().map(FinanciereDto.Ligne::idOffre).toList();
            if (groupe.size() > 1 && departage.containsAll(ids)) {
                List<String> tri = ids.stream().sorted(Comparator.comparing(departage::indexOf)).toList();
                for (int n = 0; n < tri.size(); n++) {
                    rangs.put(tri.get(n), i + 1 + n);
                }
            } else {
                for (String id : ids) {
                    rangs.put(id, i + 1);
                }
                if (groupe.size() > 1) {
                    egales.addAll(ids);
                }
            }
            i = j + 1;
        }
        return out.stream().map(p -> new FinanciereDto.Ligne(p.idOffre(), p.idFinanciere(), p.numero(), p.nif(), p.raisonSociale(), p.noteTechnique(),
                p.rangTechnique(), p.financiereOuverte(), p.saisie(), p.statut(), p.motif(), p.montantCompare(), sf.get(p.idOffre()), s.get(p.idOffre()),
                rangs.get(p.idOffre()), egales.contains(p.idOffre()))).toList();
    }

    /** Les poids T et F : totalisant 1, ou 100 (ramenés à 1) ; nul sinon. */
    static BigDecimal[] poids(String t, String f) {
        BigDecimal wt = ControlesFicheMarche.nombre(t);
        BigDecimal wf = ControlesFicheMarche.nombre(f);
        if (wt == null || wf == null || wt.signum() < 0 || wf.signum() < 0) {
            return null;
        }
        BigDecimal somme = wt.add(wf);
        if (somme.compareTo(BigDecimal.ONE) == 0) {
            return new BigDecimal[] { wt, wf };
        }
        if (somme.compareTo(CENT) == 0) {
            return new BigDecimal[] { wt.divide(CENT, 4, RoundingMode.HALF_UP).stripTrailingZeros(), wf.divide(CENT, 4, RoundingMode.HALF_UP).stripTrailingZeros() };
        }
        return null;
    }

    // ------------------------------------------------------------------ pour la négociation et la séance complémentaire

    /** Le classement arrêté d'un lot, dans l'ordre des rangs ; vide tant qu'il n'est pas arrêté. */
    @Transactional(readOnly = true)
    public List<FinanciereDto.Ligne> classementArrete(Long idDmc, Integer lot) {
        if (!arrete(idDmc, lot)) {
            return List.of();
        }
        return lot(calculer(idDmc), lot).propositions().stream().filter(p -> p.rang() != null).sorted(Comparator.comparing(FinanciereDto.Ligne::rang))
                .toList();
    }

    boolean arrete(Long idDmc, Integer lot) {
        return classements.findById(new ClassementPi.Cle(idDmc, lot)).map(c -> c.getArreteLe() != null).orElse(false);
    }

    /** L'enveloppe financière jumelle d'une proposition technique (même entreprise, même lot), déposée. */
    static Offre financiere(List<Offre> toutes, Offre tech) {
        return tech == null ? null : toutes.stream().filter(x -> Offre.FINANCIERE.equals(x.getEnveloppe()) && Offre.DEPOSEE.equals(x.getEtat())
                && Objects.equals(x.getIdEntreprise(), tech.getIdEntreprise()) && Objects.equals(x.getLot(), tech.getLot())).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ outils

    private record LigneDuLot(Integer lot, FinanciereDto.Ligne ligne) {
    }

    private static LigneDuLot ligne(FinanciereDto d, String idFinanciere) {
        for (FinanciereDto.Lot l : d.lots()) {
            for (FinanciereDto.Ligne p : l.propositions()) {
                if (idFinanciere.equals(p.idFinanciere())) {
                    return new LigneDuLot(l.lot(), p);
                }
            }
        }
        throw new ResourceNotFoundException("Proposition financière introuvable dans le classement : " + idFinanciere + ".");
    }

    static FinanciereDto.Lot lot(FinanciereDto d, Integer lot) {
        return d.lots().stream().filter(l -> Objects.equals(l.lot(), lot)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable dans l'évaluation : " + lot + "."));
    }

    private FinanciereDto.Arret arretDto(ClassementPi c) {
        return new FinanciereDto.Arret(c.getArreteLe(), c.getArretePar(), c.getArretePar() == null ? null : internes.nomMembre(c.getArretePar()),
                c.getObservation(), c.getRouvertLe(), c.getMotifReouverture());
    }

    private FinanciereDto.Saisie saisieDto(EvaluationFinanciere s) {
        List<EvaluationDto.Correction> corrections = s.getCorrections() == null ? List.of()
                : mapper.readValue(s.getCorrections(), new TypeReference<List<EvaluationDto.Correction>>() {
                });
        return new FinanciereDto.Saisie(s.getPrixLu(), s.getPrixLuTtc(), corrections, s.getRefusMotif() == null ? null
                : new EvaluationDto.Refus(s.getRefusMotif(), s.getRefusClause()), s.getPrixCorrige(), s.getRemboursables(), s.getMotifRemboursables(),
                s.getPar(), internes.nomMembre(s.getPar()), s.getLe());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> acteEngagement(Offre o) {
        if (o.getLecture() == null) {
            return null;
        }
        Map<String, Object> l = mapper.readValue(o.getLecture(), new TypeReference<Map<String, Object>>() {
        });
        return (Map<String, Object>) l.get("acteEngagement");
    }

    private static String nettoyer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String lisible(BigDecimal b) {
        return b == null ? "—" : b.stripTrailingZeros().toPlainString();
    }

    private void tracer(Long idDmc, String action, String detail) {
        journal.save(new EvaluationJournal(null, idDmc, maintenant(), CurrentUser.ref().or(CurrentUser::login).orElse(null), action, detail));
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).withNano(0);
    }
}
