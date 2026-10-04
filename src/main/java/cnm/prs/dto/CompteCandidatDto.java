package cnm.prs.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1a, §B2) — les corps et réponses des routes publiques du
 * compte candidat ({@code /api/candidats/**}).
 */
public final class CompteCandidatDto {

    private CompteCandidatDto() {
    }

    /** {@code POST /api/candidats/inscription}. Le mot de passe suit la politique des comptes internes. */
    public record Inscription(
            @NotBlank(message = "L'adresse électronique est obligatoire.")
            @Email(message = "L'adresse électronique n'est pas valide.")
            @Size(max = 100, message = "L'adresse électronique : 100 caractères au plus.") String email,
            @NotBlank(message = "Le téléphone est obligatoire.")
            @Pattern(regexp = "^\\+?[0-9][0-9 .-]{6,18}[0-9]$", message = "Le téléphone n'est pas valide.") String telephone,
            @NotBlank(message = "Le mot de passe est obligatoire.") @MotDePasseValide String motDePasse,
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 100, message = "Le nom : 100 caractères au plus.") String nom,
            @NotBlank(message = "Le prénom est obligatoire.")
            @Size(max = 100, message = "Le prénom : 100 caractères au plus.") String prenom) {
    }

    /** Réponse 201 de l'inscription. */
    public record Inscrit(String idCompte, String etat) {
    }

    /**
     * {@code POST /api/candidats/confirmation}. {@code codeTelephone} n'est exigé que si le paramètre
     * {@code CANDIDAT_CONFIRMATION_TELEPHONE} vaut {@code OUI} (et jamais pour réactiver un compte archivé).
     */
    public record Confirmation(@NotBlank(message = "L'adresse électronique est obligatoire.") String email,
            @NotBlank(message = "Le code reçu par courriel est obligatoire.") String codeEmail, String codeTelephone) {
    }

    /** Réponse de la confirmation. */
    public record Etat(String etat) {
    }

    /** {@code POST /api/candidats/codes} : renvoi des codes. */
    public record RenvoiCodes(@NotBlank(message = "L'adresse électronique est obligatoire.") String email) {
    }
}
