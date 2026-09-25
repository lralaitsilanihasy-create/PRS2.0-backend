package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * ⚠️ Fiche marché, lot 2a (demande front du 2026-09-23, §B2) — un document généré d'une version validée
 * ({@code GET /api/fiches-marche/{idDmc}/documents}) ; le binaire se lit par
 * {@code GET /api/fiches-marche/documents/{idDocument}/contenu}, sous {@code nomFichier}.
 *
 * @param type    {@code DPAO} · {@code DPAC} · {@code CCAP} · {@code AE}
 * @param libelle intitulé du document (« Acte d'engagement »…)
 * @param extension {@code docx} ou {@code pdf}
 * @param version numéro de la version de la fiche qui porte le document
 * @param lot     ⚠️ 2026-09-25 — rang du lot d'un document établi par lot (acte d'engagement d'une ligne allotie,
 *                {@code libelle} « Acte d'engagement — lot 2 ») ; {@code null} : document commun
 */
public record DocumentFicheDto(Integer idDocument, String type, String libelle, String extension, String nomFichier,
        Long tailleOctets, LocalDateTime dateGeneration, Integer version, Integer lot) {
}
