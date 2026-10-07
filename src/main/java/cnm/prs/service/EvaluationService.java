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
 * pré-remplie « non satisfaite » ; la comparaison des prix se fera hors taxes (Q3, tranche suivante).
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

    public EvaluationService(EvaluationRepository evaluations, EvaluationDeclarationRepository declarations, EvaluationEtapeRepository etapes,
            EvaluationDecisionRepository decisions, EvaluationDemandeRepository demandes, EvaluationJournalRepository journal,
            SeanceService seance, ParametresInternesService internes, CaoMembreRepository caoMembres, FicheMarcheService fiches,
            ProceduresEnLigneService procedures, CeremonieService ceremonies, NotificationService notifications, CompteCandidatRepository candidats, OffreRepository offres,
            DossierMecRepository dmcRepository, ParametreService parametres, ObjectMapper mapper, Clock horloge) {
        this.evaluations = evaluations;
        this.declarations = declarations;
        this.etapes = etapes;
        this.decisions = decisions;
        this.demandes = demandes;
        this.journal = journal;
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
        if (!EvaluationEtape.CONFORMITE.equals(et)) {
            throw new BusinessRuleException("Cette étape sera servie par la tranche suivante de l'évaluation.", "ETAPE_NON_DISPONIBLE");
        }
        Map<String, EvaluationDecision> enVigueur = enVigueur(idDmc, et);
        List<Integer> sans = parLot.get(lot).stream().filter(o -> !enVigueur.containsKey(o.idOffre())).map(SeanceDto.OffreLue::numero).toList();
        if (!sans.isEmpty()) {
            throw new BusinessRuleException("Des offres du lot n'ont pas de décision : " + sans.stream().map(n -> "n° " + n)
                    .collect(Collectors.joining(", ")) + ".", "ETAPE_INCOMPLETE", null, Map.of("offres", sans));
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
        tracer(idDmc, "PRECISION_RECUE", "Offre n° " + o.getNumero() + " (" + o.getRaisonSociale() + ") : réponse reçue"
                + (x.getReponseNom() == null ? "" : ", fichier joint"));
        String titre = "Précisions reçues";
        String corps = "Le candidat de l'offre n° " + o.getNumero() + " (procédure " + idDmc + ") a répondu à la demande de précisions.";
        ceremonies.notifierPrmp(idDmc, TypeNotification.PRECISION_RECUE, titre, corps);
        internes.membresCao(idDmc).forEach(k -> internes.notifierMembre(idDmc, k, TypeNotification.PRECISION_RECUE, titre, corps));
        return demandeDto(x, o.getNumero());
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
                EvaluationDto.Ecartement ecartee = d != null && EvaluationDecision.ECARTEE.equals(d.getDecision())
                        ? new EvaluationDto.Ecartement(EvaluationEtape.CONFORMITE, d.getQualification(), d.getMotif(), d.getClause(), d.getPar(),
                                internes.nomMembre(d.getPar()), d.getLe())
                        : null;
                evaluees.add(new EvaluationDto.OffreEvaluee(o.idOffre(), o.numero(), new EvaluationDto.Entreprise(o.entreprise().nif(),
                        o.entreprise().raisonSociale()), conf, null, null, null, null, ecartee,
                        enAttente.getOrDefault(o.idOffre(), 0L).intValue()));
            }
            lots.add(new EvaluationDto.Lot(lot, courante, arretees, evaluees));
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
