package cnm.prs.dto;

/**
 * ⚠️ V50 (2026-09-27, remise électronique, §B5 / §B5.1) — le titulaire du rôle « Responsable de la procédure » :
 * matricule et « NOM Prénoms ». Porté par la fiche ({@code responsableProcedure}, {@code null} sans titulaire) et
 * rendu par {@code POST …/responsable} (201).
 */
public record ResponsableProcedureDto(String im, String nom) {
}
