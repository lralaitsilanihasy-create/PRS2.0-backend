package cnm.prs.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Demande d'auto-inscription d'une PRMP (route publique). Crée la fiche PRMP et un compte
 * d'authentification <strong>inactif</strong>, en attente de validation par l'Administrateur.
 *
 * <p>Le profil/rôle n'est pas demandé : une PRMP est un acteur externe (type d'acteur PRMP),
 * pas un profil de contrôleur. Le compte ne peut pas se connecter tant qu'il n'est pas activé.</p>
 */
public record RegisterPrmpRequest(

        @NotBlank @Size(max = 100) String login,
        @NotBlank @Size(min = 8, max = 72) String motDePasse,

        @NotBlank @Size(max = 10) String idPrmp,
        @NotBlank @Size(max = 100) String nomPrmp,
        @NotBlank @Size(max = 100) String prenomsPrmp,
        @NotBlank @Size(max = 100) String arreteNomin,
        @NotNull LocalDate dateNomin,
        @NotBlank @Size(max = 12) String cin,
        @NotNull LocalDate dateCin,
        @NotBlank @Size(max = 50) String lieuCin,
        @NotBlank @Size(max = 100) String emailPrmp,
        @NotBlank @Size(max = 20) String telPrmp) {
}
