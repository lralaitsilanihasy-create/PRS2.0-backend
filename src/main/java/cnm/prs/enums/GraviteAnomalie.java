package cnm.prs.enums;

/**
 * ⚠️ Règle ajoutée (2026-07-22) — gravité d'une {@code AnomalieTranscription} d'import PPM.
 * {@link #BLOQUANT} = à corriger avant enregistrement ; {@link #A_VERIFIER} = à confirmer par l'humain
 * (dont les auto-corrections du backend). Sérialisé par son nom.
 */
public enum GraviteAnomalie {

    BLOQUANT,
    A_VERIFIER
}
