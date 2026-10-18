package cnm.prs.dto;

import java.util.List;

/**
 * ⚠️ 2026-10-18 (demande front « grille-contrat-cadre », §B2) — le bilan des codes de la fiche que citent les points de contrôle
 * ({@code GET /api/points-ctrls/controle-codes}) : un code cité doit exister, actif, au référentiel {@code /api/champs-fiche-marche}
 * pour chaque forme à laquelle le point s'applique (contrat-cadre, autres formes) et dans au moins une de ses catégories.
 *
 * @param pointsCites le nombre de points qui citent au moins un code
 * @param codesCites  le nombre de citations (un code par point)
 * @param points      les seuls points en écart, dans l'ordre de la grille
 * @param alertes     ⚠️ « grille-travaux », §B2 — les notions qui ne mènent à rien pour une catégorie et une forme (sans blocage)
 */
public record CodesCitesPointsCtrlDto(int pointsCites, int codesCites, List<Point> points, List<Alerte> alertes) {

    /** Un point dont un code cité au moins est en écart. */
    public record Point(Integer idPointCtrl, String libelPointCtrl, String idTypeDossier, String idSousType, String forme,
            String categorie, List<Ecart> ecarts) {
    }

    /**
     * Un code en écart.
     *
     * @param code  le code tel que le texte le cite ({@code B05-GS-03}, une rubrique {@code B08-AV}, un bloc {@code B12})
     * @param motif {@code INCONNU} (absent du référentiel), {@code INACTIF} (retiré), {@code HORS_CATEGORIE} (aucune catégorie du
     *              point), {@code HORS_FORME} (absent pour la forme {@code forme}), ⚠️ §B3 {@code HORS_RUBRIQUE} (présent pour la
     *              forme, mais sa rubrique ou son bloc ne l'est pas : le champ n'est pas affiché)
     * @param forme pour {@code HORS_FORME} et {@code HORS_RUBRIQUE} : {@code CONTRAT_CADRE} ou {@code AUTRE} ; nul sinon
     */
    public record Ecart(String code, String motif, String forme) {
    }

    /**
     * ⚠️ 2026-10-18 (demande front « grille-travaux », §B2) — une notion du point (une parenthèse de son texte) dont aucun code
     * n'existe pour une forme et une catégorie que le point touche, sans la mention « &lt;catégorie&gt; : non saisi ».
     *
     * @param idPointCtrl    le point
     * @param libelPointCtrl son libellé
     * @param idSousType     son sous-type
     * @param formePoint     sa forme ({@code CONTRAT_CADRE}, {@code AUTRE}, nul : toutes)
     * @param codes          les codes de la notion
     * @param forme          la forme de fiche en défaut : {@code CONTRAT_CADRE} ou {@code AUTRE}
     * @param categorie      la catégorie de fiche en défaut
     * @param motif          {@code SANS_EQUIVALENT_CATEGORIE}
     */
    public record Alerte(Integer idPointCtrl, String libelPointCtrl, String idSousType, String formePoint, List<String> codes,
            String forme, String categorie, String motif) {
    }
}
