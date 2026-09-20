package cnm.prs.dto;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 6) — ce que rend une analyse par
 * l'assistant ({@code POST /api/pre-controle/ppm/{id}/analyse-ia}).
 *
 * @param synthese une phrase qui dit <strong>où regarder d'abord</strong> — la hiérarchisation que le
 *                 plan attend de l'assistant (« sur 120 lignes, regardez ces trois-là »). Elle n'est
 *                 <strong>pas enregistrée</strong> : c'est une aide à la lecture, recalculée à chaque
 *                 analyse, et journalisée comme tout échange avec l'assistant. {@code null} si le modèle
 *                 n'en a pas rendu.
 * @param resume   l'état du plan après l'analyse : les pistes de l'assistant y ont rejoint les constats
 *                 des règles, chacune marquée {@code source = IA}
 */
public record AnalyseIaDto(String synthese, ResumePreControleDto resume) {
}
