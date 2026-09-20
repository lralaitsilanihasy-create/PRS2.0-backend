package cnm.prs.dto;

import java.util.List;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 7) — <strong>le taux d'écartement par
 * règle</strong> ({@code GET /api/pre-controle/statistiques}).
 *
 * <p>Ce tableau de bord n'est pas une amélioration ultérieure : il fait partie du lot (plan, §4, lot 3,
 * 3.e). Il répond au seul risque qui tue ce genre de fonctionnalité — <strong>la fatigue d'alerte</strong>.
 * Si l'outil signale trop, tout le monde écarte tout sans lire, et il meurt en six semaines. Le taux
 * d'écartement est la mesure qui le dit : <strong>une règle écartée dans 80 % des cas est une mauvaise
 * règle</strong>, et on l'éteint — {@code t_regle_anomalie} étant une table, cela se fait sans
 * redéploiement.</p>
 *
 * <p>Il ne porte que des <strong>compteurs</strong> : aucune donnée de plan, aucun libellé de marché,
 * aucun acteur. C'est ce qui permet de l'ouvrir à l'Administrateur, dont le rôle est technique et qui n'a
 * rien à savoir du contenu des dossiers.</p>
 *
 * @param exercice    exercice observé, ou {@code null} pour tous
 * @param total       signalements observés
 * @param ecartes     signalements écartés, tous acteurs confondus
 * @param tauxGlobal  part des signalements écartés, de 0 à 1 ({@code 0} si aucun signalement)
 * @param regles      une ligne par règle, la plus écartée d'abord
 */
public record StatistiquesReglesDto(Integer exercice, int total, int ecartes, double tauxGlobal,
        List<LigneStatistiqueDto> regles) {

    /**
     * Une règle et ce qu'on en fait.
     *
     * @param code    code de la règle ({@code t_regle_anomalie.CODE_REGLE})
     * @param libelle son libellé administrable
     * @param source  {@code REGLE} (fait opposable) ou {@code IA} (piste) — les deux ne se jugent pas de la
     *                même façon : une piste écartée souvent reste une piste, une <em>règle</em> écartée
     *                souvent est un défaut
     * @param actif   la règle est-elle encore allumée ?
     * @param total   signalements produits par cette règle
     * @param ouverts signalements encore à regarder
     * @param ecartes signalements écartés avec motif
     * @param leves   signalements qui ne ressortent plus (le plan a été corrigé) — c'est le
     *                <strong>succès</strong> de la règle, à ne pas confondre avec un écartement
     * @param taux    part d'écartés, de 0 à 1
     * @param suspecte vrai quand le taux d'écartement atteint le seuil d'alerte sur un nombre significatif
     *                de signalements : la règle est à revoir, ou à éteindre
     */
    public record LigneStatistiqueDto(String code, String libelle, String source, boolean actif, int total,
            int ouverts, int ecartes, int leves, double taux, boolean suspecte) {
    }
}
