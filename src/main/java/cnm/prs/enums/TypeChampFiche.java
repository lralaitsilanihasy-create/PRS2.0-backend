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
    /**
     * ⚠️ V50 (2026-09-27, remise électronique, §B1.2) — date et heure locales ISO {@code yyyy-MM-dd'T'HH:mm} (la valeur
     * d'un {@code datetime-local}) ; imprimée {@code JJ/MM/AAAA HH:MM}.
     */
    DATE_HEURE,
    /** ⚠️ V50 — adresse absolue {@code http} / {@code https}, 500 caractères au plus ; imprimée telle quelle. */
    URL,
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
