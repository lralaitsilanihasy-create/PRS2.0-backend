package cnm.prs.dto;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M5b, §B6 ; V99) — le délai d'une étape pour un sous-type.
 *
 * @param idSousType     sous-type de dossier
 * @param etape          valeur de {@code EtapeCircuit}
 * @param delaiHeures    délai effectif pour ce sous-type, en heures ouvrées
 * @param standardHeures délai standard de l'étape (tous sous-types)
 * @param surcharge      vrai : le sous-type a son propre délai pour cette étape
 */
public record DelaiSousTypeDto(String idSousType, String etape, Integer delaiHeures, Integer standardHeures, boolean surcharge) {
}
