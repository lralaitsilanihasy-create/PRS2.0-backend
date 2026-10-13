package cnm.prs.dto;

/**
 * ⚠️ R8 (manuel révisé pour l'application, lot 2, §B3 ; V110, 2026-10-13) — {@code POST /api/fiches-marche/{idDmc}/migrer-mode} :
 * la migration documentée du mode de remise par l'Administrateur ({@code modeRemise} ∈ {@code PAPIER} · {@code ELECTRONIQUE},
 * {@code motif} obligatoire).
 */
public record MigrationModeRequest(String modeRemise, String motif) {
}
