package cnm.prs.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Part JSON ({@code data}) de l'auto-inscription UGPM (multipart). Accompagne les fichiers
 * {@code cin} (obligatoire) et {@code photo} (optionnel). Miroir de l'inscription PRMP sans
 * arrêté ni entités : l'UGPM déclare une <strong>PRMP de tutelle</strong> obligatoire dont elle
 * hérite du périmètre.
 */
public record RegisterUgpmRequest(

        @NotBlank @Size(max = 100) String login,
        @NotBlank @MotDePasseValide String motDePasse,

        @NotBlank @Size(max = 10) String idUgpm,
        @Size(max = 150) String libelle,
        @NotBlank @Size(max = 50) String nomUgpm,
        @NotBlank @Size(max = 100) String prenomsUgpm,
        @NotBlank @Size(max = 12) String cin,
        @NotNull LocalDate dateCin,
        @NotBlank @Size(max = 50) String lieuCin,
        @NotBlank @Size(max = 100) String emailUgpm,
        @NotBlank @Size(max = 20) String telUgpm,

        @NotBlank @Size(max = 10) String idPrmpTutelle) {
}
