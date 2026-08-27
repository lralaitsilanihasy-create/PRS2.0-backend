package cnm.prs.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Saisie d'un PPM en un seul appel (façade transactionnelle, §3.1 M02) : crée le dossier
 * (type PPM, BROUILLON), le PPM et ses lignes de marché. La PRMP propriétaire est forcée à
 * l'utilisateur courant ; le mode de passation de chaque ligne est déterminé automatiquement.
 */
public record SaisiePpmRequest(

        @NotNull
        Integer idEntiteContract,

        @NotNull
        Integer exercice,

        @NotNull
        LocalDate dateSignature,

        // ⚠️ @Valid porte sur le PARAMÈTRE DE TYPE, pas sur la List (Bean Validation 2.0) : sur le
        // conteneur, il est déprécié (Hibernate Validator HV000271). Cascade et chemins de violation
        // (« marches[0].champ ») inchangés.
        List<@Valid SaisieMarcheLigne> marches) {
}
