package cnm.prs.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Création (Administrateur) d'une PRMP. Champs d'identité (mêmes que {@link PrmpDto}) + <strong>credentials
 * optionnels</strong> ({@code login}/{@code motDePasse}) : s'ils sont fournis, un compte PRMP <strong>actif</strong>
 * est créé en même temps que la fiche (parité avec {@code POST /api/ugpms}). Absents → fiche seule (rétro-compat).
 * {@code login} et {@code motDePasse} doivent être fournis <strong>ensemble</strong> (sinon 400).
 */
public record CreerPrmpRequest(

        @NotBlank @Size(max = 10) String idPrmp,
        @NotBlank @Size(max = 100) String nomPrmp,
        @NotBlank @Size(max = 100) String prenomsPrmp,
        @NotBlank @Size(max = 100) String arreteNomin,
        @NotNull LocalDate dateNomin,
        @NotBlank @Size(max = 12) String cin,
        @NotNull LocalDate dateCin,
        @NotBlank @Size(max = 50) String lieuCin,
        @NotBlank @Size(max = 100) String emailPrmp,
        @NotBlank @Size(max = 20) String telPrmp,

        // Credentials optionnels — si présents, créent le compte PRMP actif (refActeur = idPrmp).
        @Size(max = 100) String login,
        @Size(min = 8, max = 72) String motDePasse) {
}
