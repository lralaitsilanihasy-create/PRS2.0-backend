package cnm.prs.dto;

/**
 * Compteurs de contenu par section du menu PRMP — tous filtrés sur la PRMP authentifiée (JWT).
 *
 * @param brouillons          mes dossiers en brouillon ({@code t_dossier.STATUT = BROUILLON})
 * @param ppmMarches          mes PPM &amp; marchés (PPM de la PRMP, {@code t_ppm.ID_PRMP})
 * @param dossiersARectifier  mes dossiers à rectifier non traités
 *                            ({@code t_dossier.STATUT = EN_ATTENTE_DECISION_PRMP})
 * @param dossiersVerifies    mes dossiers vérifiés ({@code t_dossier.STATUT IN (PV_SIGNE, CLOTURE)})
 * @param lettresRenvoi       mes lettres de renvoi signées non lues ({@code STATUT = SIGNE} sans trace de lecture)
 * @param demandesRetraitNouvelles mes demandes de retrait passées à {@code ACCEPTEE}/{@code REFUSEE}
 *                            depuis ma dernière consultation de l'écran « Demandes de retrait »
 */
public record CompteursPrmpDto(
        long brouillons,
        long ppmMarches,
        long dossiersARectifier,
        long dossiersVerifies,
        long lettresRenvoi,
        long demandesRetraitNouvelles,
        /** ⚠️ 2026-10-06 (compteurs, §B1) — les reçus de frais de dossier {@code EN_ATTENTE} sur les fiches de la PRMP. */
        long recusAValider) {
}
