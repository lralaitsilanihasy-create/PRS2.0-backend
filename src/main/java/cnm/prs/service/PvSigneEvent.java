package cnm.prs.service;

/**
 * Événement applicatif : un PV vient de recevoir sa signature finale (statut {@code SIGNE}).
 * Consommé APRÈS COMMIT par {@link PvDocumentTache} pour produire le PDF hors de la
 * transaction de signature (spec du 2026-08-19).
 */
public record PvSigneEvent(Integer idPv) {
}
