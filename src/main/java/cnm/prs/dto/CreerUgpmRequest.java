package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Création (Administrateur) d'une UGPM + son compte d'authentification actif. {@code idPrmpTutelle} doit
 * référencer une PRMP existante ; {@code idUgpm} et {@code login} doivent être uniques.
 */
public record CreerUgpmRequest(

        @NotBlank @Size(max = 10)
        String idUgpm,

        @Size(max = 100)
        String libelle,

        @NotBlank @Size(max = 10)
        String idPrmpTutelle,

        @NotBlank @Size(max = 100)
        String login,

        @NotBlank
        String motDePasse) {
}
