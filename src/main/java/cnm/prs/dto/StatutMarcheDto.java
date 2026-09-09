package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.StatutMarche} — référentiel « Statut de marché ».
 *
 * <p>{@code ordre} et {@code actif} sont facultatifs : sans rang le statut se range en dernier, et sans
 * drapeau il est <strong>actif</strong> — créer un statut pour ne pas s'en servir n'aurait pas de sens.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatutMarcheDto {

    @NotBlank(message = "Le code du statut est obligatoire.")
    @Size(max = 20, message = "Le code du statut ne dépasse pas 20 caractères.")
    private String code;

    @NotBlank(message = "Le libellé du statut est obligatoire.")
    @Size(max = 100, message = "Le libellé du statut ne dépasse pas 100 caractères.")
    private String libelle;

    private Integer ordre;

    private Boolean actif;
}
