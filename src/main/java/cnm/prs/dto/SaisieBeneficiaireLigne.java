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

        /**
         * ⚠️ Règle ajoutée (2026-07-25) — nom du service bénéficiaire (colonne « SERVICE BÉNÉFICIAIRE » en texte
         * libre, sans code SOA). Si {@code soaCode} est absent, le service est <strong>résolu-ou-créé par ce
         * libellé</strong> dans {@code tr_soa_beneficiaire} (le code SOA est alors dérivé). {@code null} si absent.
         */
        @Size(max = 100)
        String soaLibelle,

        @Size(max = 20)
        String numCompte,

        BigDecimal ancMontBenef,

        BigDecimal nouvMontBenef) {
}
