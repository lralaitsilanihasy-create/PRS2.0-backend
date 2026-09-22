package cnm.prs.enums;

/**
 * D'où vient la valeur d'un champ de la fiche marché ({@code tr_champ_fiche_marche.SOURCE}).
 *
 * <p>Seule {@link #SAISIE} s'écrit dans {@code t_fiche_marche_valeur}. {@link #PPM} est relue à chaque lecture
 * depuis la ligne du PPM (H7 : une information du plan se corrige dans le plan) ; {@link #CADRAGE} est le reflet
 * d'une réponse de cadrage, dérivée, jamais reçue.</p>
 */
public enum SourceChampFiche {
    PPM,
    SAISIE,
    CADRAGE
}
