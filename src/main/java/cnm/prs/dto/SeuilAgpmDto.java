package cnm.prs.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * ⚠️ Arbitrage pilote (2026-09-07, suite) — <strong>seuil de montant</strong> du déclenchement AGPM
 * <em>conditionnel</em> : un marché passé selon un mode marqué {@code agpmSiSeuil} (l'appel à manifestation
 * d'intérêt) ne rend l'AGPM requis que si son montant estimé l'atteint.
 *
 * <p>Administrable sans redéploiement ({@code GET}/{@code PUT /api/parametres/agpm-seuil-montant}) : la
 * valeur vit dans l'administration, jamais dans le code. Bornes calées sur la colonne des montants
 * ({@code numeric(38,2)}), comme les montants des marchés.</p>
 */
public record SeuilAgpmDto(
        @NotNull(message = "Le seuil est obligatoire.")
        @PositiveOrZero(message = "Le seuil ne peut pas être négatif.")
        @Digits(integer = 36, fraction = 2, message = "Seuil hors format (36 chiffres, 2 décimales).")
        BigDecimal seuil) {
}
