package cnm.prs.enums;

/**
 * ⚠️ Règle ajoutée (2026-07-22) — type d'une {@code AnomalieTranscription} d'import PPM (métadonnée de
 * revue côté front). Sérialisé par son nom.
 */
public enum TypeAnomalie {

    /** {@code montEstim} ≠ Σ des montants par bénéficiaire (invariant du document). */
    MONTANT_INCOHERENT,

    /** Objet se terminant par un préfixe de route sans numéro (RN/RNT/RNS…) — probablement tronqué. */
    OBJET_TRONQUE_PROBABLE,

    /** Caractère de remplacement (« ¿ », U+FFFD) subsistant après décodage — transcription douteuse. */
    ENCODAGE_SUSPECT,

    /** SOA / nature / mode / compte non résolu au référentiel. */
    REFERENTIEL_INCONNU,

    /** Champ obligatoire absent (objet, montant, mode). */
    CHAMP_MANQUANT
}
