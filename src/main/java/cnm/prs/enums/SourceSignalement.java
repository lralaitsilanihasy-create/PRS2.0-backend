package cnm.prs.enums;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3) — <strong>d'où vient un signalement</strong>
 * ({@code t_anomalie.SOURCE}).
 *
 * <p>C'est l'arbitrage structurant du lot (plan, §4, lot 3, 3.a) : <strong>les règles détectent, l'IA
 * explique</strong>. Un fait opposable — « l'article X impose le mode Y au-delà du seuil Z » — se défend
 * devant un contrôleur ; « le modèle estime que » ne se défend pas. Les deux ne doivent donc
 * <strong>jamais</strong> être présentés de la même façon dans un écran, et une PRMP qui écarte à raison
 * une intuition fausse du modèle ne doit pas en porter la marque (3.f, condition 4).</p>
 */
public enum SourceSignalement {

    /** Détecté par une règle : reproductible, opposable, cité avec sa base légale. */
    REGLE,

    /** Piste proposée par l'assistant : à lire comme une suggestion, jamais comme un fait. */
    IA
}
