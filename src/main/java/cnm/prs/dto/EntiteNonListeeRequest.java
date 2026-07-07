package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Entité contractante <strong>non listée</strong> proposée par la PRMP à l'inscription :
 * l'Administrateur la créera (ou non) à la validation. Ne porte pas d'identifiant (l'entité
 * n'existe pas encore dans le référentiel).
 */
public record EntiteNonListeeRequest(

        @NotBlank @Size(max = 150) String libelle,

        @NotBlank @Size(max = 200) String adresse,

        @Size(max = 5) String idLocalite,

        @Size(max = 20) String categorie) {
}
