package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * ⚠️ 2026-10-05 (demande front « le dépositaire génère lui-même la part de secours », §B1) — l'espace du dépositaire
 * ({@code /api/depositaire/**}).
 */
public final class DepositaireDto {

    private DepositaireDto() {
    }

    /**
     * {@code GET /api/depositaire/procedures} : une ligne par procédure dont il est le dépositaire désigné. {@code etatPart} : l'état
     * de <em>sa</em> clé de secours ({@code ABSENTE}, {@code PUBLIEE}, {@code VERIFIEE}, {@code PERDUE}) ; {@code generePar} : qui a
     * généré la clé de secours active ({@code RESPONSABLE}, {@code DEPOSITAIRE}, {@code null} sans clé) — une clé active qui n'est
     * pas la sienne se <strong>remplace</strong> ({@code PUT …/cles/secours}) ; {@code secoursDemande} : la demande du responsable
     * en séance, {@code null} sinon.
     */
    public record Procedure(Long idDmc, String reference, String objet, String etatCeremonie, String etatPart, String generePar,
            boolean cleARemplacer, String etatSeance, SecoursDemande secoursDemande) {
    }

    public record SecoursDemande(String motif, LocalDateTime date) {
    }
}
