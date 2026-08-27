package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Demande de changement de mot de passe de l'utilisateur authentifié.
 *
 * @param ancienMotDePasse  mot de passe actuel (vérifié) — ⚠️ jamais soumis à
 *                          {@link MotDePasseValide} : il n'est qu'une preuve d'identité, et les
 *                          mots de passe créés avant cette politique doivent rester présentables
 * @param nouveauMotDePasse nouveau mot de passe (cf. {@link MotDePasseValide})
 */
public record ChangePasswordRequest(

        @NotBlank
        String ancienMotDePasse,

        @NotBlank
        @MotDePasseValide
        String nouveauMotDePasse) {
}
