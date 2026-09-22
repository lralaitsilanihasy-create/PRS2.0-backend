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
        this(message, code, null);
    }

    /**
     * ⚠️ Fiche marché, lot 1b (2026-09-23) — 409 à code stable qui <strong>désigne un dossier</strong>
     * ({@code DOSSIER_EXISTANT}, {@code FICHE_DEJA_LIEE}) : {@code idDossier} passe dans le corps d'erreur.
     */
    public BusinessRuleException(String message, String code, Integer idDossier) {
        super(message);
        this.code = code;
        this.idDossier = idDossier;
    }

    /** Dossier en cause, ou {@code null}. */
    private final Integer idDossier;

    public String getCode() {
        return code;
    }

    public Integer getIdDossier() {
        return idDossier;
    }
}
