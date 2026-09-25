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
    /**
     * ⚠️ V45 (2026-09-25) — plusieurs valeurs parmi {@code OPTIONS}, enregistrées séparées par des virgules dans l'ordre
     * des options (« A1,A2,A4 ») ; reçues en tableau ou en chaîne.
     */
    LISTE_MULTIPLE,
    OUI_NON,
    /** Référence d'une pièce (nom ou identifiant), texte libre. */
    PIECE
}
