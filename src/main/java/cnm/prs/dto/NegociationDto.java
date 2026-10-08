package cnm.prs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-08 (lot 3 PI, tranche PI-d2a, §B6 ; V89) — les négociations de chaque lot, et le candidat avec qui négocier ensuite
 * ({@code prochain}, nul quand une négociation est en cours ou réussie, ou qu'il n'en reste aucun).
 */
public record NegociationDto(Long idDmc, List<Lot> lots) {

    public record Lot(Integer lot, boolean classementArrete, Prochain prochain, boolean conclue, List<Item> negociations) {
    }

    /** {@code financiereOuverte} faux : une séance complémentaire doit d'abord l'ouvrir. */
    public record Prochain(String idOffre, Integer numero, String raisonSociale, Integer rang, boolean financiereOuverte) {
    }

    public record Item(Long id, String idOffre, String idFinanciere, Integer numero, String raisonSociale, Integer rang, String etat,
            LocalDateTime ouverteLe, String ouvertePar, LocalDateTime prevueLe, String lieu, LocalDateTime conclueLe, String concluePar,
            LocalDate dateNegociation, String texte, String motifEchec, String pieceNom, boolean pvDisponible) {
    }

    public record OuvertureRequest(LocalDateTime prevueLe, String lieu) {
    }

    /** {@code resultat} ∈ {@code REUSSIE}, {@code ECHOUEE} ; {@code motif} exigé pour un échec. */
    public record ConclusionRequest(String resultat, LocalDate dateNegociation, String lieu, String texte, String motif) {
    }
}
