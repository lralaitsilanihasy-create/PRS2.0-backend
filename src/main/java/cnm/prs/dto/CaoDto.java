package cnm.prs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 2a, §B1, §B2 ; Q11 du pilote) — la <strong>commission d'appel
 * d'offres</strong> d'un DAO, acte public de la PRMP : elle se lit par qui lit la fiche.
 *
 * @param etat      {@code ABSENTE} (rien), {@code INCOMPLETE} (une règle manque), {@code COMPLETE}
 * @param anomalies ce qui manque, et les comptes non activés
 */
public record CaoDto(Long idDmc, Decision decision, List<Membre> membres, String etat, List<Anomalie> anomalies) {

    /** La décision de nomination : {@code fichier} dit si le PDF signé est joint. */
    public record Decision(String reference, LocalDate date, boolean fichier) {
    }

    /**
     * Un membre : {@code qualite} ∈ {@code MEMBRE} · {@code EXPERT_ADJOINT} ; {@code origine} (pour un {@code MEMBRE}) ∈
     * {@code ENTITE_CONTRACTANTE} (avec {@code service}) · {@code EXPERT_OBJET} (avec {@code organisme} et {@code domaine}) ;
     * {@code compte} est {@code null} pour un expert adjoint.
     */
    public record Membre(Long id, String nom, String prenom, String email, String telephone, String qualite, String origine,
            String fonction, String service, String organisme, String domaine, boolean president, Compte compte) {
    }

    /** L'état du compte {@code MEMBRE_CAO} : {@code A_INVITER} · {@code INVITE} · {@code ACTIF} · {@code ARCHIVE}. */
    public record Compte(String etat, String idCompte, LocalDateTime dateInvitation, LocalDateTime dateActivation) {
    }

    public record Anomalie(String regle, String message) {
    }

    /** Le corps de {@code PUT …/cao} : un {@code id} absent crée le membre, présent le met à jour, un membre omis est retiré. */
    public record Corps(DecisionCorps decision, List<MembreCorps> membres) {
    }

    public record DecisionCorps(String reference, LocalDate date) {
    }

    public record MembreCorps(Long id, String nom, String prenom, String email, String telephone, String qualite, String origine,
            String fonction, String service, String organisme, String domaine, Boolean president) {
    }

    /** {@code POST /api/cao/activation} (public) : l'adresse, le code reçu par courriel, le mot de passe choisi. */
    public record Activation(@NotBlank String email, @NotBlank String code, @NotBlank @MotDePasseValide String motDePasse) {
    }

    public record EtatCompte(String etat) {
    }

    /** {@code GET /api/cao/mes-procedures} : une ligne par CAO où le membre siège. */
    public record MaProcedure(Long idDmc, String reference, String objet, String autoriteContractante, boolean president,
            String dateLimite, String etatCeremonie, String etatPart) {
    }

    /** {@code GET /api/cao/procedures/{idDmc}} : la vue du membre. */
    public record VueMembre(ProcedureEnLigneDto procedure, boolean president, CaoDto cao) {
    }
}
