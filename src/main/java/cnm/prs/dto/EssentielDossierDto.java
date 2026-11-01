package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-11-01 (demande front « essentiel-dossier », §B1) — « L'essentiel » d'un dossier : l'avis du dernier PV, l'attributaire de
 * chaque lot, le contrat, les suites. Chaque bloc vaut {@code null} tant qu'il n'existe pas.
 */
public record EssentielDossierDto(Avis avis, List<Attribution> attributions, Contrat contrat, Suites suites) {

    /** L'avis du dernier PV ({@code definitif} : PV signé). */
    public record Avis(String code, String libelle, String refePv, boolean definitif, Integer idPv) {
    }

    /** L'attributaire d'un lot ; {@code montant} en ariary (numérique). */
    public record Attribution(Integer lot, String attributaire, Integer numeroOffre, BigDecimal montant, Integer idDossierMarche) {
    }

    /** Le contrat du marché. */
    public record Contrat(String forme, String libelleForme, LocalDate signeLe, LocalDate finLe) {
    }

    /** Les suites : bons de commande (ou marchés subséquents), le dernier, et les actes de gestion. */
    public record Suites(int bonsCommande, DernierBon dernierBon, int actesGestion) {
    }

    public record DernierBon(Integer numero, LocalDateTime date) {
    }
}
