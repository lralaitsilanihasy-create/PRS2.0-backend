package cnm.prs.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Size;

/**
 * Bénéficiaire d'une ligne de marché à la saisie / import PPM (une ligne {@code t_service_beneficiaire}).
 * {@code soaCode} et {@code numCompte} sont <strong>résolus-ou-créés</strong> côté service (réutilisation de
 * l'existant, jamais de suppression). {@code ancMontBenef}/{@code nouvMontBenef} = montant estimatif (ancien /
 * nouveau) <strong>par bénéficiaire</strong> ; leur somme doit égaler le montant du marché (cohérence validée).
 */
public record SaisieBeneficiaireLigne(

        @Size(max = 25)
        String soaCode,

        @Size(max = 20)
        String numCompte,

        BigDecimal ancMontBenef,

        BigDecimal nouvMontBenef) {
}
