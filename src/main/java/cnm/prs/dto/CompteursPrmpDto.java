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
        long recusAValider,
        /** ⚠️ 2026-10-07 (évaluation des offres, §B7) — les demandes aux candidats (précisions, justifications) sans réponse dont le délai
         * court, sur les fiches de la PRMP. */
        long demandesEvaluationEnAttente,
        /** ⚠️ 2026-10-08 (attribution, 2d-1, §B7) — lots à attribuer (avis favorable rendu), lots signables (délai écoulé), avis
         * d'attribution à publier, explications sans réponse. */
        long lotsAAttribuer,
        long lotsSignables,
        long avisAPublier,
        long explicationsSansReponse,
        /** ⚠️ R6c (manuel révisé, lot 2, §B1 règle 8) — marchés notifiés dont l'échéance de 30 jours de l'avis d'attribution est passée sans
         * publication (non bloquant : à traiter). */
        long avisEnRetard,
        /** ⚠️ 2026-10-16 (marchés subséquents, MS-1) — contrats-cadres dont la décision de reconduire approche (30 jours avant la limite
         * du préavis) sans être prise ; contrats en vigueur à moins de 30 jours de leur échéance sans reconduction. */
        long reconductionsADecider,
        long contratsAEcheance,
        /** ⚠️ 2026-10-29 (« chaine-attribution », §B3) — lots attribués dont les candidats ne sont pas encore informés. */
        long candidatsAInformer) {
}
