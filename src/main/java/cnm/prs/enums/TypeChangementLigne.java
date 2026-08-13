package cnm.prs.enums;

/**
 * ⚠️ Règle ajoutée (2026-08-05, mise à jour des PPM) — statut d'une ligne de marché d'une version de PPM
 * <strong>relativement à la version précédente</strong>. Le rapprochement se fait sur
 * {@code Marche.idLigneOrigine} (jamais sur la position : une ligne déplacée reste INCHANGEE).
 */
public enum TypeChangementLigne {

    /** Présente dans les deux versions, aucun champ comparé n'a bougé. */
    INCHANGEE,

    /** Présente dans les deux versions, au moins un champ diffère (détaillé champ par champ). */
    MODIFIEE,

    /** Absente du prédécesseur : ligne ajoutée par cette version. */
    NOUVELLE,

    /** Présente au prédécesseur, marquée supprimée ici (suppression LOGIQUE, restaurable). */
    SUPPRIMEE,

    /**
     * Supprimée dans une version antérieure et remise en service par celle-ci. Distinguée de
     * {@link #NOUVELLE} : la ligne conserve son identité et donc tout son historique.
     */
    RESTAUREE
}
