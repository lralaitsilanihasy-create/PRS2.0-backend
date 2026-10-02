package cnm.prs.dto;

/**
 * ⚠️ 2026-10-02 (demande front « gabarits ») — une phrase d'un modèle qui imprime un champ : le texte du paragraphe avant
 * et après le jeton, tel que dans le modèle recopié (les autres jetons remplacés par {@code ___}, les balises
 * {@code {{SI:…}}} / {@code {{FINSI:…}}} retirées). Le front y montre la saisie à sa place.
 *
 * @param document la pièce, en code court ({@code DPAO}, {@code DPAC}, {@code DPIC}, {@code AE}, {@code CCAP}, {@code CPS},
 *                 {@code AVIS}, {@code LETTRE}) : le sigle du modèle sans sa catégorie
 * @param avant    le texte du paragraphe avant le jeton
 * @param apres    le texte du paragraphe après le jeton
 * @param suffixe  le suffixe du jeton ({@code lettres}, {@code chiffres}, {@code parLot}, {@code heure}…), {@code null}
 *                 pour le jeton nu
 */
public record GabaritDto(String document, String avant, String apres, String suffixe) {
}
