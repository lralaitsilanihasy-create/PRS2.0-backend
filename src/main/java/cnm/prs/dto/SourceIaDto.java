package cnm.prs.dto;

/**
 * Un extrait du corpus fourni au modèle pour répondre, tel que la réponse le cite : {@code [numero]}.
 * Envoyé au front avant la réponse (événement {@code sources}) pour que chaque citation soit
 * vérifiable.
 *
 * @param numero    rang cité dans la réponse ({@code [1]}, {@code [2]}…)
 * @param document  document d'origine (ex. « Manuel de contrôle a priori (CNM, février 2026) »)
 * @param reference emplacement dans le document (ex. « p. 15 »)
 * @param extrait   texte fourni au modèle
 */
public record SourceIaDto(int numero, String document, String reference, String extrait) {
}
