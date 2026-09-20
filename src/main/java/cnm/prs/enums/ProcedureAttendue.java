package cnm.prs.enums;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3) — <strong>procédure que les seuils
 * appellent</strong> pour un montant donné, d'après l'arrêté n° 13 156/2019-MEF (art. 2, 2°).
 *
 * <p>« Appellent » et non « imposent », sauf pour l'appel d'offres ouvert : l'arrêté écrit que les
 * marchés atteignant le premier seuil font <strong>obligatoirement</strong> l'objet d'un appel d'offres
 * ouvert (sauf exceptions des articles 38 et 39 du code des marchés publics), que ceux du palier
 * inférieur <strong>peuvent</strong> faire l'objet d'une consultation, et que ceux d'en dessous
 * <strong>peuvent</strong> être exécutés directement par bon de commande.</p>
 *
 * <p>Cette lecture ne <strong>détermine</strong> jamais le mode d'un marché : le mode reste saisi par la
 * PRMP, et le pré-contrôle s'en sert seulement pour signaler un écart, avec l'article et le seuil qui le
 * fondent. Les exceptions des articles 38 et 39 (modes dérogatoires, déjà justifiés dans la fiche de
 * présentation) sont précisément la raison pour laquelle un signalement s'écarte.</p>
 */
public enum ProcedureAttendue {

    /** Le montant atteint le seuil d'appel d'offres ouvert : la procédure est obligatoire. */
    APPEL_OFFRES_OUVERT,

    /** Le montant autorise une consultation d'entrepreneurs, de fournisseurs ou de prestataires. */
    CONSULTATION,

    /** En dessous du seuil de consultation : exécution directe par bon de commande réglementaire. */
    ACHAT_DIRECT
}
