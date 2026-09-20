package cnm.prs.dto;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 6) — ce que rend une analyse par
 * l'assistant ({@code POST /api/pre-controle/ppm/{id}/analyse-ia}).
 *
 * @param synthese        une phrase qui dit <strong>où regarder d'abord</strong> — la hiérarchisation que
 *                        le plan attend de l'assistant. Elle n'est <strong>pas enregistrée</strong> :
 *                        c'est une aide à la lecture, recalculée à chaque analyse, et journalisée comme
 *                        tout échange avec l'assistant. {@code null} s'il n'y a aucune piste.
 * @param lignesAnalysees combien de lignes l'assistant a réellement <strong>lues</strong>
 * @param lignesDuPlan    combien le plan en porte. Les deux sont servis, et l'écran le dit : une
 *                        couverture partielle <strong>annoncée</strong> vaut mieux qu'une couverture
 *                        totale supposée. Un modèle local ne peut pas lire un plan de 130 lignes d'un
 *                        coup — défaut trouvé en recette le 2026-09-20, où l'analyse répondait « rien à
 *                        signaler » sur un plan qu'elle n'avait pas lu.
 * @param resume          l'état du plan après l'analyse : les pistes de l'assistant y ont rejoint les
 *                        constats des règles, chacune marquée {@code source = IA}
 */
public record AnalyseIaDto(String synthese, int lignesAnalysees, int lignesDuPlan,
        ResumePreControleDto resume) {
}
