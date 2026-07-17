package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Localite}.
 *
 * <p>⚠️ Champ {@code referencement} <strong>retiré du contrat</strong> (2026-07-17) : champ hérité du MLD
 * sans aucune sémantique (jamais lu par la génération de références, les documents ni les jobs ; valeurs
 * dérivables « REF-&lt;id&gt; »). La colonne BD {@code REFERENCEMENT} est dépréciée (rendue nullable,
 * conservée). NB : le segment localité des références officielles (« CRM-ANT ») est bâti sur la
 * <strong>PK {@code idLocalite}</strong>, pas sur le code {@code localite}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocaliteDto {

    private String idLocalite;

    @NotBlank
    @Size(max = 50)
    private String libelleLocalite;

    @NotBlank
    @Size(max = 3)
    private String localite;
}
