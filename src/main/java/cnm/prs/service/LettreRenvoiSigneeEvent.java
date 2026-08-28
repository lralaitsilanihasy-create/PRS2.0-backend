package cnm.prs.service;

/**
 * Événement applicatif : une lettre de renvoi vient d'être signée (statut {@code SIGNE}).
 * Consommé APRÈS COMMIT par {@link LettreRenvoiDocumentTache} pour produire le PDF hors de la
 * transaction de signature — alignement sur le PV, dont la génération avait été sortie du chemin
 * de signature le 2026-08-19 ({@link PvSigneEvent}).
 */
public record LettreRenvoiSigneeEvent(Integer idLettre) {
}
