package cnm.prs.exception;

/**
 * Levée lorsqu'une règle de gestion (transition d'état interdite, contrainte
 * métier non respectée…) empêche d'exécuter une action. Traduite en HTTP 409.
 */
public class BusinessRuleException extends RuntimeException {

    /** Code métier stable, ou {@code null} (cas général : le champ {@code code} reste absent du JSON). */
    private final String code;

    public BusinessRuleException(String message) {
        this(message, null);
    }

    /**
     * ⚠️ Fiche marché DAO (2026-09-22, §B2) — 409 <strong>à code stable</strong> ({@code MODE_NON_DAO},
     * {@code PV_NON_SIGNE}, {@code DAO_EXISTANT}, {@code LIGNE_RETIREE}, {@code CONTROLES_BLOQUANTS}), sur le modèle
     * de {@code VACANCE_PRMP} / {@code CONFLIT_VERSION} que le front lit déjà : c'est le « 409 nominatif » de la
     * demande, sans introduire une seconde forme de corps d'erreur ({@code erreurs[]} reste réservé au 400).
     */
    public BusinessRuleException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
