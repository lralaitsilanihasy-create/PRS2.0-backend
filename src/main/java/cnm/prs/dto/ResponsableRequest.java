package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;

/** ⚠️ V50 (2026-09-27, remise électronique, §B5) — le corps de {@code POST /api/fiches-marche/{idDmc}/responsable} : {@code { im }}. */
public record ResponsableRequest(@NotBlank(message = "le matricule du responsable est requis") String im) {
}
