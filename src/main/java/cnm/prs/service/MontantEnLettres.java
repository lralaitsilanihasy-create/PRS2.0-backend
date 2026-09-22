package cnm.prs.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * ⚠️ Fiche marché (2026-09-22, §B3) — un <strong>montant en Ariary en toutes lettres</strong>, tel que les documents
 * d'appel d'offres l'écrivent (« huit millions quatre cent mille ariary », « un milliard deux cents millions ariary »).
 * S'appuie sur {@link NombreEnLettres#cardinal(long)}, étendu aux millions et milliards. Les centimes sont ignorés
 * (l'Ariary se libelle en entier) ; un montant nul donne « zéro ariary ».
 */
public final class MontantEnLettres {

    private MontantEnLettres() {
    }

    public static String ariary(BigDecimal montant) {
        if (montant == null) {
            return null;
        }
        long n = montant.setScale(0, RoundingMode.DOWN).longValueExact();
        return NombreEnLettres.cardinal(n) + " ariary";
    }
}
