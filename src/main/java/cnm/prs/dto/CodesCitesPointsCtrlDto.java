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
 */
public record CodesCitesPointsCtrlDto(int pointsCites, int codesCites, List<Point> points) {

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
}
