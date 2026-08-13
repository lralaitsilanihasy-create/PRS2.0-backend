package cnm.prs.dto;

/**
 * Compteurs de contenu par section du menu Contrôleur vérificateur — filtrés sur sa localité,
 * miroir de ses trois worklists ({@code /api/dossiers/a-verifier}, {@code /verifies},
 * {@code /en-attente-prmp}).
 *
 * @param aVerifier     dossiers restant à traiter ({@code EN_VERIFICATION}, {@code EN_ATTENTE_DECISION_PRMP}
 *                      ou {@code OBSERVATIONS_LEVEES}) — ⚠️ 2026-08-04 : plus {@code DECISION_TRANSMISE_SIGMP},
 *                      le dossier sort de la file dès la transmission de la décision à SIGMP
 * @param verifies      dossiers vérifiés/clôturés ({@code DECISION_TRANSMISE_SIGMP} ou {@code CLOTURE},
 *                      avec PV signé)
 * @param enAttentePrmp dossiers en attente de décision PRMP ({@code EN_ATTENTE_DECISION_PRMP})
 */
public record CompteursVerificateurDto(
        long aVerifier,
        long verifies,
        long enAttentePrmp) {
}
