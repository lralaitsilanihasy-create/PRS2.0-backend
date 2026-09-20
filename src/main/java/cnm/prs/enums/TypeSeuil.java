package cnm.prs.enums;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3) — <strong>nature du seuil</strong> lu dans le
 * référentiel {@code tr_seuil_marche}. Un seuil dit toujours la même chose : « à partir de ce montant
 * hors taxes, telle conséquence ».
 */
public enum TypeSeuil {

    /**
     * Au-delà, le marché est soumis au <strong>contrôle a priori</strong> de la Commission compétente
     * (arrêté n° 13 156/2019-MEF, art. 2, 1°). C'est ce seuil qui rend un fractionnement grave : trois
     * lignes sous le seuil dont le cumul le franchit soustraient le marché au contrôle.
     */
    CONTROLE_A_PRIORI,

    /** Au-delà, l'<strong>appel d'offres ouvert</strong> est obligatoire (art. 2, 2°, i°). */
    APPEL_OFFRES_OUVERT,

    /**
     * Au-delà — et en deçà du seuil d'appel d'offres ouvert — le marché peut faire l'objet d'une
     * <strong>consultation</strong> d'entrepreneurs, de fournisseurs ou de prestataires de services
     * (art. 2, 2°, ii°). En dessous, il peut être exécuté directement par bon de commande réglementaire
     * (art. 2, 2°, iii°).
     */
    CONSULTATION,

    /**
     * Prestations intellectuelles : au-delà, l'appel à manifestation d'intérêt se publie <strong>par voie
     * de presse</strong>, avec le délai minimal porté par le seuil. Hors arrêté — décret n° 2019-1310
     * modifié par le décret n° 2022-1091.
     */
    MANIFESTATION_INTERET_PRESSE,

    /**
     * Prestations intellectuelles : en deçà du seuil de la voie de presse, l'appel à manifestation
     * d'intérêt peut se publier <strong>par affichage</strong>, avec un délai minimal plus court. Semé à
     * un montant de zéro — il s'applique depuis le premier ariary.
     */
    MANIFESTATION_INTERET_AFFICHAGE
}
