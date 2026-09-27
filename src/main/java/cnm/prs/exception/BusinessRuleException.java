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
        this(message, code, idDossier, null);
    }

    /**
     * ⚠️ Lot C (2026-09-27, rectification d'un dossier DAO) — 409 à code stable qui porte quelques <strong>détails</strong>
     * nommés ({@code DOSSIER_EN_EXAMEN} : {@code statut} ; {@code FICHE_NON_REVISEE} : {@code versionSoumise},
     * {@code versionCourante}, {@code statutFiche}), servis dans {@code details} du corps d'erreur — sans nouvelle forme.
     */
    public BusinessRuleException(String message, String code, Integer idDossier, java.util.Map<String, Object> details) {
        super(message);
        this.code = code;
        this.idDossier = idDossier;
        this.details = details;
    }

    /** Dossier en cause, ou {@code null}. */
    private final Integer idDossier;

    /** Détails nommés du 409, ou {@code null}. */
    private final java.util.Map<String, Object> details;

    public String getCode() {
        return code;
    }

    public Integer getIdDossier() {
        return idDossier;
    }

    public java.util.Map<String, Object> getDetails() {
        return details;
    }
}
