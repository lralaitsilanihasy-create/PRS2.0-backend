package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.TypePieceJointe} (référentiel des pièces par type de dossier).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypePieceJointeDto {

    private Integer idTypePiece;

    @NotNull
    @Size(max = 200)
    private String libellePiece;

    /** Code stable facultatif (ex. {@code AGPM}) — repère la pièce à obligation conditionnelle. */
    @Size(max = 20)
    private String code;

    @NotNull
    private Boolean obligatoire;

    @Size(max = 10)
    private String idTypeDossier;

    private Integer ordre;

    // ⚠️ 2026-10-08 (manuel de contrôle, M2) — servis pour un sous-type (?sousType=) ou un dossier : la condition de la pièce.
    /** {@code FOURNITURES_SERVICES}, {@code TRAVAUX}, {@code PRESTATIONS_INTELLECTUELLES} ; nul : toutes. */
    private String categorie;
    /** {@code CONTRAT_CADRE}, {@code AUTRE} ; nul : toutes formes. */
    private String forme;
    /** ⚠️ R2 (manuel révisé, V102) — la condition de la pièce : {@code PAPIER} (hors ligne seulement), {@code FACULTATIVE_EN_LIGNE}, ou nul. */
    private String condition;

    // ⚠️ 2026-10-17 (« pieces-dmc-par-les-donnees », §B1) — servis pour un dossier né d'une fiche validée : la pièce que l'application
    // fournit par ses données (fiche de présentation, calendrier annuel, contrôle du plan). Elle n'est plus à déposer.
    /** {@code APPLICATION}, ou nul : la pièce est à déposer. */
    private String fourniePar;
    /** La route qui rend la pièce fournie. */
    private String lien;
    /** Le plan de la ligne (calendrier, contrôle du plan). */
    private Integer idDossierPlan;
    /** Le PV signé de l'examen du plan (contrôle du plan). */
    private Integer idPvPlan;
    /**
     * ⚠️ 2026-10-19 (« dossier-marche-lecture », Q4) — {@code PV_CNM_DMC} : le PV signé de la Commission sur le dossier de mise en
     * concurrence, ouvrable même sans document ({@code lien} nul tant que le PV n'a pas de PDF).
     */
    private Integer idPv;
}
