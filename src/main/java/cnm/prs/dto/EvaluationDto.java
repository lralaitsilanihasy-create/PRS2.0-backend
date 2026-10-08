package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ V76 (demande front du 2026-10-07, évaluation des offres, lot 1, §B1) — l'évaluation d'une procédure : son état, les déclarations
 * des membres de la CAO, et par lot l'étape en cours, les étapes arrêtées et les offres évaluées. Les offres non ouvertes (écartées au
 * dépôt, retirées, remplacées) figurent à part, sans être évaluées (H3).
 */
public record EvaluationDto(Long idDmc, String etat, LocalDateTime ouverteLe, String ouvertePar, List<Declaration> declarations,
        List<Lot> lots, List<NonEvaluee> nonEvaluees, Rapport rapport) {

    /** Un membre de la CAO et sa déclaration préalable ({@code signeeLe} nul : pas encore signée). */
    public record Declaration(String membre, String nom, boolean president, LocalDateTime signeeLe, Boolean conflit, String precision) {
    }

    /**
     * Un lot (1 pour une procédure non allotie) : {@code etape} ∈ {@code CONFORMITE} · {@code EVALUATION} · {@code ANORMALES} ·
     * {@code QUALIFICATION} · {@code RAPPORT} (la première étape non arrêtée).
     */
    public record Lot(Integer lot, String etape, List<EtapeArretee> etapesArretees, List<OffreEvaluee> offres, Proposition proposition) {
    }

    public record EtapeArretee(String etape, String par, String nom, LocalDateTime le, String observation) {
    }

    /**
     * Une offre évaluée : une section par étape ({@code null} tant qu'elle n'est pas atteinte ; ⚠️ tranche 1b :
     * {@code conformite} et {@code evaluation} sont servies), {@code rang} (classement du lot, offres retenues seules), {@code exAequo} (à
     * égalité de montant évalué, sans départage), {@code ecartee} : l'étape qui l'a écartée et pourquoi.
     */
    public record OffreEvaluee(String idOffre, Integer numero, Entreprise entreprise, Conformite conformite, Montant evaluation,
            Anormale anormale, Qualification qualification, Integer rang, Boolean exAequo, Ecartement ecartee, int precisionsEnAttente,
            /** ⚠️ 2026-10-07 (rabais structuré) — le rabais déclaré par le candidat, tel que la séance le lit (pré-remplissage). */
            SeanceDto.RabaisLu rabaisDeclare) {
    }

    public record Entreprise(String nif, String raisonSociale) {
    }

    /**
     * L'examen préliminaire d'une offre (étape 2) : la grille des vérifications, pré-remplie par le serveur depuis la lecture de la
     * séance ({@code proposee}), confirmée ou corrigée par la CAO ({@code satisfaite}) ; puis la décision, nulle tant qu'aucun membre ne
     * l'a enregistrée.
     */
    public record Conformite(List<Verification> verifications, String decision, String qualification, String motif, String clause,
            String par, String nom, LocalDateTime le) {
    }

    /**
     * Une vérification : {@code proposee} (vrai, faux, ou nul : sans objet ou à examiner) et {@code constat} viennent de la lecture ;
     * {@code satisfaite} est la valeur retenue — celle de la CAO si elle a décidé, la proposée sinon.
     */
    public record Verification(String code, String libelle, Boolean proposee, String constat, Boolean satisfaite, String observation) {
    }

    public record Ecartement(String etape, String qualification, String motif, String clause, String par, String nom, LocalDateTime le) {
    }

    public record NonEvaluee(Integer numero, String entreprise, String etat, String motif) {
    }

    // ------------------------------------------------------------------ corps des requêtes

    public record DeclarationRequest(Boolean conflit, String precision) {
    }

    public record Arret(String observation) {
    }

    public record Reouverture(String motif) {
    }

    /** {@code PUT …/offres/{idOffre}/conformite}. */
    public record ConformiteRequest(List<VerificationSaisie> verifications, String decision, String qualification, String motif, String clause) {
    }

    public record VerificationSaisie(String code, Boolean satisfaite, String observation) {
    }

    // ------------------------------------------------------------------ demandes de précisions (art. 35-VI)

    public record DemandeRequest(String question, Integer delaiJours) {
    }

    /**
     * Une demande adressée au candidat et sa réponse : {@code etat} ∈ {@code EN_ATTENTE} · {@code REPONDUE} · {@code EXPIREE} ;
     * {@code fichier} : le nom du fichier joint à la réponse, nul sans fichier.
     */
    public record Demande(Long idDemande, String idOffre, Integer numero, String type, String question, Integer delaiJours,
            LocalDateTime echeance, LocalDateTime demandeeLe, String etat, String reponse, String fichier, Long tailleFichier,
            LocalDateTime reponduLe) {
    }

    // ------------------------------------------------------------------ ⚠️ tranche 1b (§B3) : corrections, montant évalué, classement

    /** Une correction arithmétique proposée par le serveur depuis le bordereau scellé ({@code ligne} nulle : le total). */
    public record CorrectionProposee(Integer ligne, String libelle, BigDecimal avant, BigDecimal apres, String regle) {
    }

    /**
     * Une correction, proposée ou saisie par la CAO : {@code regle} ∈ {@code PU_PREVAUT} · {@code LETTRES_PREVALENT} · {@code REPORT} ·
     * {@code AUTRE} ; seules les {@code retenue} comptent, pour {@code apres − avant}.
     */
    public record Correction(Integer ligne, String libelle, BigDecimal avant, BigDecimal apres, String regle, Boolean retenue) {
    }

    /** Le refus d'une correction par le candidat, constaté par la CAO (arbitrage Q2) : l'offre est écartée à cette étape. */
    public record Refus(String motif, String clause) {
    }

    /**
     * Le rabais retenu, hors taxes, et la lecture qu'en fait la CAO. ⚠️ 2026-10-07 (rabais structuré, §B3) — pour un rabais déclaré au
     * format 4 : {@code propose} (le montant proposé par le serveur : pourcentage × prix corrigé, ou le montant déclaré ; nul pour un
     * rabais conditionnel, non appliqué), {@code nature}, {@code valeur}, {@code condition}, {@code lots} ; {@code motif} : celui de la
     * CAO quand elle s'écarte de la proposition. En requête, seuls {@code montant}, {@code lecture} et {@code motif} comptent.
     */
    public record Rabais(BigDecimal montant, String lecture, BigDecimal propose, String nature, BigDecimal valeur, String condition,
            List<Integer> lots, String motif) {
    }

    /** La marge de préférence : {@code taux} (de la fiche) et {@code ajustement} sont calculés par le serveur. */
    public record Preference(Boolean eligible, String motif, BigDecimal taux, BigDecimal ajustement) {
    }

    /** Un critère additionnel monétisé (seulement si la fiche en porte, {@code B06-EO-02}). */
    public record Critere(String libelle, BigDecimal montant, String justification) {
    }

    /**
     * L'évaluation détaillée d'une offre (étape 3), hors taxes (arbitrage Q3) : {@code prixLu} (HT de l'acte d'engagement),
     * {@code prixCorrige} = prix lu + Σ (après − avant) des corrections retenues, {@code montantEvalue} = prix corrigé − rabais +
     * ajustement de préférence + critères ; le montant du marché, lui, ne compte pas la préférence. Nul au refus du candidat.
     */
    public record Montant(BigDecimal prixLu, BigDecimal prixLuTtc, List<Correction> corrections, Refus refusCandidat, Rabais rabais,
            Preference preference, List<Critere> criteres, BigDecimal prixCorrige, BigDecimal montantEvalue, String par, String nom,
            LocalDateTime le) {
    }

    /** {@code PUT …/offres/{idOffre}/montant} ; {@code prixLu} seulement si l'acte d'engagement ne porte pas de montant HT. */
    public record MontantRequest(BigDecimal prixLu, List<Correction> corrections, Refus refusCandidat, Rabais rabais, Preference preference,
            List<Critere> criteres) {
    }

    /** {@code POST …/lots/{lot}/departage} : l'ordre retenu des offres à égalité, du premier au dernier, et son motif. */
    public record DepartageRequest(List<String> ordre, String motif) {
    }

    /**
     * Une ligne du tableau d'évaluation (modèle du guide, p. 9) : toutes les offres évaluées du lot ; {@code conforme} à l'examen
     * préliminaire ; {@code motifRejet} pour une offre écartée (à l'étape qui l'a écartée) ; {@code ajustements} = préférence +
     * critères ; {@code qualifie} : tranche 1c.
     */
    public record LigneTableau(String idOffre, Integer numero, String candidat, BigDecimal prixLu, BigDecimal prixLuTtc, String garantie,
            Boolean conforme, String motifRejet, BigDecimal prixCorrige, BigDecimal rabais, BigDecimal ajustements, BigDecimal montantEvalue,
            Integer rang, Boolean exAequo, Boolean qualifie) {
    }

    // ------------------------------------------------------------------ ⚠️ tranche 1c (§B4, §B5) : anormales, post-qualification

    /**
     * Les indicateurs de prix d'un lot (§B4, des indicateurs, jamais des décisions) : la méthode du DAO ({@code B06-EO-07}, nulle sans
     * méthode), l'estimation du lot, la moyenne des offres retenues, et par offre ses écarts en pour cent.
     */
    public record IndicateursPrix(String methodeDao, BigDecimal estimation, BigDecimal moyenne, List<IndicateurPrix> offres) {
    }

    /** {@code montant} = prix corrigé − rabais (hors taxes) ; écarts arrondis au dixième de point. */
    public record IndicateurPrix(String idOffre, Integer numero, BigDecimal montant, BigDecimal ecartEstimation, BigDecimal ecartMoyenne,
            List<String> alertes) {
    }

    /** {@code POST …/offres/{idOffre}/justification} : les éléments demandés (art. 48) et le délai. */
    public record JustificationRequest(String elements, Integer delaiJours) {
    }

    /** {@code PUT …/offres/{idOffre}/anormale}. */
    public record AnormaleRequest(Boolean suspectee, String decision, String motif) {
    }

    /**
     * L'examen d'une offre au regard de son prix (§B4) : {@code decision} ∈ {@code NON_SUSPECTEE} · {@code SUSPECTEE} (en attente de la
     * justification) · {@code MAINTENUE} · {@code REJETEE} ; {@code justification} : la demande au candidat et sa réponse.
     */
    public record Anormale(Boolean suspectee, String decision, String motif, Demande justification, String par, String nom,
            LocalDateTime le) {
    }

    /**
     * Un critère de post-qualification (§B5), dérivé de la fiche (P1 : rien d'autre) : {@code groupe} ∈ {@code JURIDIQUE} ·
     * {@code FINANCIERE} · {@code TECHNIQUE} ; {@code exigence} : la valeur de la fiche ; {@code constat} et {@code proposee} : le constat
     * de la séance (faux sur une alerte, nul sinon) ; {@code decision} ∈ {@code SATISFAIT} · {@code NON_SATISFAIT}, et son motif.
     */
    public record CritereQualification(String code, String groupe, String libelle, String exigence, String constat, Boolean proposee,
            String decision, String motif) {
    }

    /** La post-qualification d'une offre (§B5) : ses critères, et {@code decision} ∈ {@code QUALIFIE} · {@code NON_QUALIFIE}. */
    public record Qualification(String idOffre, Integer numero, List<CritereQualification> criteres, String decision, String motif,
            String clause, String par, String nom, LocalDateTime le) {
    }

    /** {@code PUT …/offres/{idOffre}/qualification}. */
    public record QualificationRequest(List<CritereSaisi> criteres, String decision, String motif, String clause) {
    }

    public record CritereSaisi(String code, String decision, String motif) {
    }

    /**
     * La proposition d'un lot, une fois la post-qualification arrêtée : l'offre proposée à l'attribution, ou {@code infructueux}
     * quand aucune offre n'est qualifiée ; {@code montant} = prix corrigé − rabais, hors taxes. ⚠️ PI-d2b : {@code idOffreFinanciere}
     * (l'enveloppe financière de la proposition, PI seulement) et {@code motifInfructuosite} (PI : art. 56-II, échec des négociations).
     */
    public record Proposition(String idOffre, Integer numero, String candidat, BigDecimal montant, BigDecimal montantTtc, String delai,
            boolean infructueux, String idOffreFinanciere, String motifInfructuosite) {
    }

    // ------------------------------------------------------------------ ⚠️ tranche 1d (§B6) : le rapport d'évaluation

    /**
     * Le rapport d'évaluation : nul tant qu'il n'est pas produit ; {@code signe} à la dernière signature (l'évaluation est alors
     * {@code CLOSE}) ; {@code signatures} faites (ou empêchements constatés) et {@code signaturesAttendues}.
     */
    public record Rapport(LocalDateTime produitLe, String observations, boolean signe, LocalDateTime signeLe, List<SignatureRapport> signatures,
            List<Attendue> signaturesAttendues) {
    }

    public record SignatureRapport(String im, String nom, boolean president, LocalDateTime date, boolean empechement, String motif,
            String constatePar, String observation) {
    }

    public record Attendue(String im, String nom) {
    }

    /** {@code POST …/rapport} : les observations du responsable. */
    public record RapportRequest(String observations) {
    }

    /** {@code POST …/rapport/signer} : l'observation du membre (désaccord), facultative. */
    public record SignatureRequest(String observation) {
    }

    /** {@code POST …/rapport/empechement}. */
    public record EmpechementRequest(String im, String motif) {
    }

    /** Une ligne du journal de l'évaluation. */
    public record Journal(LocalDateTime date, String acteur, String action, String detail) {
    }
}
