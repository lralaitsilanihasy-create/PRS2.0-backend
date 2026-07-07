package cnm.prs.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Modification (Administrateur) d'une UGPM : champs métier éditables uniquement. L'identifiant
 * {@code idUgpm} (= matricule) est porté par l'URL et n'est pas modifiable ; les identifiants de
 * connexion ({@code login}/{@code motDePasse}) relèvent de la gestion du compte, hors de ce contrat.
 * {@code idPrmpTutelle} peut être réaffecté (la nouvelle PRMP de tutelle doit exister).
 */
public record ModifierUgpmRequest(

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
        String telUgpm) {
}
