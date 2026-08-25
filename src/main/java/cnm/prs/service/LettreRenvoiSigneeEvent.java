package cnm.prs.service;

/**
 * Événement applicatif : une lettre de renvoi vient d'être signée (statut {@code SIGNE}).
 * Consommé APRÈS COMMIT par {@link LettreRenvoiDocumentTache} pour produire le PDF hors de la
 * transaction de signature (même motif que {@link PvSigneEvent}, spec du 2026-08-19).
 */
public record LettreRenvoiSigneeEvent(Integer idLettre) {
}
