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

    /**
     * ⚠️ V44 (2026-09-25) — fiche marché visée : celle du dossier examiné (409 {@code FICHE_HORS_DOSSIER} sinon).
     * Facultative.
     */
    private Long idDmc;

    /**
     * ⚠️ V44 — information de la fiche visée : {@code B04-VO-01}, ou {@code B05-GS-03#2} pour celle d'un lot (mêmes
     * règles de rang qu'à la saisie de la fiche). Exige {@code idDmc}. Exclusif de {@link #champ}.
     */
    private String champFiche;

    /** ⚠️ V44 — lecture seule : libellé du champ au référentiel, figé quand l'observation est posée. */
    private String libelleChampFiche;

    /** ⚠️ V44 — lecture seule : valeur observée (telle que les documents l'impriment), figée à l'observation. */
    private String valeurChampFiche;

    /** ⚠️ V44 — lecture seule : rang du lot de l'information ({@code null} : commune). */
    private Integer lot;
}
