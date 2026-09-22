package cnm.prs.enums;

/** Type de donnée d'un champ de la fiche marché ({@code tr_champ_fiche_marche.TYPE}). */
public enum TypeChampFiche {
    TEXTE,
    TEXTE_LONG,
    NOMBRE,
    /** Ariary : le nombre est saisi, les lettres sont servies par le serveur ({@code enLettres}). */
    MONTANT,
    POURCENTAGE,
    /** ISO {@code yyyy-MM-dd}. */
    DATE,
    /** Une valeur parmi {@code OPTIONS}. */
    LISTE,
    OUI_NON,
    /** Référence d'une pièce (nom ou identifiant), texte libre. */
    PIECE
}
