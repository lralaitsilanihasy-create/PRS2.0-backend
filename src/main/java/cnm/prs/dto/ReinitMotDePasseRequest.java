package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Réinitialisation d'un mot de passe par l'Administrateur (nouveau mot de passe imposé).
 *
 * @param nouveauMotDePasse nouveau mot de passe (cf. {@link MotDePasseValide}) — la politique
 *                          s'applique aussi à l'Administrateur : rien ne justifie qu'un mot de
 *                          passe imposé à un tiers soit plus faible que celui qu'il se choisirait
 */
public record ReinitMotDePasseRequest(

        @NotBlank
        @MotDePasseValide
        String nouveauMotDePasse) {
}
