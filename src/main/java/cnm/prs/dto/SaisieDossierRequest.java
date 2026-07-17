package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Saisie d'un dossier sans contenu (familles DMC / DDM) via la façade : crée un {@code t_dossier}
 * (famille + sous-type + localité, BROUILLON), propriété de la PRMP courante.
 *
 * <p>⚠️ Règle ajoutée (hiérarchie famille → sous-type) : la PRMP choisit un <strong>sous-type</strong>
 * ({@code idSousType}, référentiel {@code /api/sous-type-dossiers} — ex. {@code DAO}, {@code DAOR},
 * {@code MAOO}, {@code MAOR}) ; la <strong>famille</strong> s'en déduit. Un sous-type de la famille
 * {@code DDP} (planification) est refusé : utiliser {@code POST /api/saisies/ppm}.</p>
 *
 * <p>{@code idTypeDossier} est <strong>déprécié</strong> : accepté en repli quand {@code idSousType}
 * est absent, et interprété comme un code de sous-type (les anciens types {@code DAO}/{@code MAOO}
 * sont devenus des sous-types).</p>
 */
public record SaisieDossierRequest(

        @Size(max = 20)
        String idSousType,

        /** Déprécié — repli : interprété comme {@code idSousType} si celui-ci est absent. */
        @Size(max = 20)
        String idTypeDossier,

        @NotNull
        Integer idEntiteContract) {
}
