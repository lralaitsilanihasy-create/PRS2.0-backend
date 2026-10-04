package cnm.prs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1b, §B3 à §B5) — l'entreprise d'un candidat, ses pièces, la
 * vérification de son NIF et le répertoire des exclusions de l'ARMP.
 */
public final class EntrepriseCandidatDto {

    private EntrepriseCandidatDto() {
    }

    /** {@code EntrepriseDto}. {@code exclusion} : l'exclusion de l'ARMP en cours pour ce NIF, ou {@code null}. */
    public record Entreprise(Integer id, String raisonSociale, String nif, String stat, String rcs, String adresse,
            Representant representant, List<Piece> pieces, Verification verification, Exclusion exclusion) {
    }

    public record Representant(@NotBlank(message = "Le nom du représentant est obligatoire.")
            @Size(max = 100, message = "Le nom du représentant : 100 caractères au plus.") String nom,
            @NotBlank(message = "Le prénom du représentant est obligatoire.")
            @Size(max = 100, message = "Le prénom du représentant : 100 caractères au plus.") String prenom,
            @Size(max = 100, message = "La fonction : 100 caractères au plus.") String fonction) {
    }

    /** {@code PieceEntrepriseDto}. */
    public record Piece(Integer id, String type, String nomFichier, String format, long taille, LocalDateTime dateDepot) {
    }

    /**
     * {@code VerificationNifDto}. {@code statut} : {@code VERIFIE_DGI}, {@code VERIFIE_SUR_PIECES}, {@code INCONNU_DGI},
     * {@code REFUSE_SUR_PIECES}, {@code NON_VERIFIE} ; {@code source} : {@code DGI} ou {@code SUR_PIECES} ; {@code acteur} :
     * l'Administrateur (voie sur pièces) ou {@code DGI}.
     */
    public record Verification(String statut, String source, LocalDateTime date, String acteur, String motif) {
    }

    /** {@code ExclusionDto} ; {@code enCours} au jour de la lecture. */
    public record Exclusion(Integer id, String nif, String raisonSociale, String motif, String referenceDecision,
            LocalDate dateDebut, LocalDate dateFin, boolean enCours, List<LigneJournal> journal) {
    }

    /** Une ligne du journal d'une exclusion : anciennes valeurs ({@code null} à la création) et nouvelles. */
    public record LigneJournal(LocalDateTime date, String acteur, Map<String, Object> anciennes, Map<String, Object> nouvelles) {
    }

    /** Corps de {@code PUT /api/candidat/entreprise}. */
    public record Saisie(
            @NotBlank(message = "La raison sociale est obligatoire.")
            @Size(max = 200, message = "La raison sociale : 200 caractères au plus.") String raisonSociale,
            @NotBlank(message = "Le NIF est obligatoire.") @Size(max = 30, message = "Le NIF : 30 caractères au plus.") String nif,
            @Size(max = 30, message = "Le STAT : 30 caractères au plus.") String stat,
            @Size(max = 50, message = "Le RCS : 50 caractères au plus.") String rcs,
            @NotBlank(message = "L'adresse est obligatoire.") @Size(max = 300, message = "L'adresse : 300 caractères au plus.") String adresse,
            @NotNull(message = "Le représentant est obligatoire.") @Valid Representant representant) {
    }

    /** Corps de {@code POST /api/admin/entreprises/{id}/verification}. */
    public record Decision(@NotBlank(message = "Le statut est obligatoire.") String statut, String motif) {
    }

    /** Corps de {@code POST} / {@code PUT /api/exclusions-armp}. */
    public record SaisieExclusion(
            @NotBlank(message = "Le NIF est obligatoire.") @Size(max = 30, message = "Le NIF : 30 caractères au plus.") String nif,
            @NotBlank(message = "La raison sociale est obligatoire.")
            @Size(max = 200, message = "La raison sociale : 200 caractères au plus.") String raisonSociale,
            @NotBlank(message = "Le motif est obligatoire.") @Size(max = 500, message = "Le motif : 500 caractères au plus.") String motif,
            @NotBlank(message = "La référence de la décision est obligatoire.")
            @Size(max = 100, message = "La référence : 100 caractères au plus.") String referenceDecision,
            @NotNull(message = "La date de début est obligatoire.") LocalDate dateDebut, LocalDate dateFin) {
    }
}
