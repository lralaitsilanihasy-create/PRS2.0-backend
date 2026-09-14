package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.ObservationControle} : une ligne d'observation
 * « AU LIEU DE / LIRE » d'un point de contrôle ({@code idDetail}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObservationControleDto {

    private Integer idObservation;

    @NotNull(message = "Le point de contrôle est obligatoire.")
    private Integer idDetail;

    @Size(max = 500)
    private String auLieuDe;

    @Size(max = 500)
    private String lire;

    @NotNull
    private Integer ordre;

    /**
     * ⚠️ V30 (2026-09-14) — cellule du document visée : code de {@link cnm.prs.enums.ChampCible}
     * ({@code "mode"}, {@code "derogatoires.montEstim"}, {@code "agpm.dateDao"}…), ou {@code null}. Reçu en
     * chaîne pour qu'un code inconnu réponde un 400 ciblé plutôt qu'un corps illisible.
     */
    private String champ;

    /**
     * ⚠️ V30 — ligne de marché visée ({@code t_marche.ID_DETAIL}). Point LIGNE : facultatif, forcé à la ligne
     * du résultat ; points DOSSIER, FICHE, AGPM : obligatoire dès qu'un champ est posé.
     */
    private Integer idMarcheCible;

    /** ⚠️ V30 — bénéficiaire visé ({@code t_service_beneficiaire.ID_BENEF}), colonnes par bénéficiaire seulement. */
    private Integer idBenefCible;
}
