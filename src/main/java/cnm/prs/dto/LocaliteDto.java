package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Localite}.
 *
 * <p>⚠️ Champs {@code referencement} puis {@code localite} (code max 3) <strong>retirés du contrat</strong>
 * (2026-07-17) : colonnes héritées du MLD sans aucune sémantique — jamais lues par la génération de
 * références, les documents ni les jobs ; valeurs dupliquant/dérivant la PK. Les colonnes BD sont
 * dépréciées (rendues nullables, conservées). Le contrat se réduit à <strong>id / libellé</strong>.
 * NB : le segment localité des références officielles (« CRM-ANT ») est bâti sur la
 * <strong>PK {@code idLocalite}</strong>.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocaliteDto {

    private String idLocalite;

    @NotBlank
    @Size(max = 50)
    private String libelleLocalite;
}
