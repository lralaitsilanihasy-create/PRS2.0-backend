package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ V76 (demande front du 2026-10-07, évaluation des offres, lot 1, §B1) — l'évaluation d'une procédure : son état, les déclarations
 * des membres de la CAO, et par lot l'étape en cours, les étapes arrêtées et les offres évaluées. Les offres non ouvertes (écartées au
 * dépôt, retirées, remplacées) figurent à part, sans être évaluées (H3).
 */
public record EvaluationDto(Long idDmc, String etat, LocalDateTime ouverteLe, String ouvertePar, List<Declaration> declarations,
        List<Lot> lots, List<NonEvaluee> nonEvaluees) {

    /** Un membre de la CAO et sa déclaration préalable ({@code signeeLe} nul : pas encore signée). */
    public record Declaration(String membre, String nom, boolean president, LocalDateTime signeeLe, Boolean conflit, String precision) {
    }

    /**
     * Un lot (1 pour une procédure non allotie) : {@code etape} ∈ {@code CONFORMITE} · {@code EVALUATION} · {@code ANORMALES} ·
     * {@code QUALIFICATION} · {@code RAPPORT} (la première étape non arrêtée).
     */
    public record Lot(Integer lot, String etape, List<EtapeArretee> etapesArretees, List<OffreEvaluee> offres) {
    }

    public record EtapeArretee(String etape, String par, String nom, LocalDateTime le, String observation) {
    }

    /**
     * Une offre évaluée : une section par étape ({@code null} tant qu'elle n'est pas atteinte ; dans cette tranche, seule
     * {@code conformite} est servie), {@code rang} (tranche suivante), {@code ecartee} : l'étape qui l'a écartée et pourquoi.
     */
    public record OffreEvaluee(String idOffre, Integer numero, Entreprise entreprise, Conformite conformite, Object evaluation,
            Object anormale, Object qualification, Integer rang, Ecartement ecartee, int precisionsEnAttente) {
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

    /** Une ligne du journal de l'évaluation. */
    public record Journal(LocalDateTime date, String acteur, String action, String detail) {
    }
}
