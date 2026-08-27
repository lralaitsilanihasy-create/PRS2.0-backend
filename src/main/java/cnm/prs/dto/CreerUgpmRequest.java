package cnm.prs.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Création (Administrateur) d'une UGPM + son compte d'authentification actif. {@code idPrmpTutelle} doit
 * référencer une PRMP existante ; {@code idUgpm} et {@code login} doivent être uniques. Les champs d'identité
 * (mêmes que la PRMP, sauf arrêté/date de nomination) sont obligatoires.
 */
public record CreerUgpmRequest(

        // idUgpm = matricule de l'UGPM (identifiant unifié).
        @NotBlank @Size(max = 10)
        String idUgpm,

        @Size(max = 100)
        String libelle,

        @NotBlank @Size(max = 10)
        String idPrmpTutelle,

        @NotBlank @Size(max = 50)
        String nomUgpm,

        @NotBlank @Size(max = 100)
        String prenomsUgpm,

        @NotBlank @Size(max = 12)
        String cin,

        @NotNull
        LocalDate dateCin,

        @NotBlank @Size(max = 50)
        String lieuCin,

        @NotBlank @Size(max = 100)
        String emailUgpm,

        @NotBlank @Size(max = 20)
        String telUgpm,

        @NotBlank @Size(max = 100)
        String login,

        @NotBlank
        @MotDePasseValide
        String motDePasse) {
}
