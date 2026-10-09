package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle, M4, §B4) — un motif-type de renvoi ou d'avis défavorable. {@code texte} est ce que le front insère
 * dans le projet de PV ou de lettre de renvoi ; {@code libelle} nomme le motif dans la liste.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MotifTypeDto {

    private Integer idMotif;

    @NotBlank
    @Size(max = 10)
    private String idTypeDossier;

    /** Nul : commun à la famille. */
    @Size(max = 20)
    private String idSousType;

    /** {@code RENVOI} | {@code AVIS_DEFAVORABLE}. */
    @NotBlank
    private String nature;

    @NotBlank
    @Size(max = 200)
    private String libelle;

    @NotBlank
    private String texte;

    private Integer ordre;

    /** {@code FOURNITURES_SERVICES} | {@code TRAVAUX} | {@code PRESTATIONS_INTELLECTUELLES} ; nul : toutes. */
    private String categorie;

    /** {@code CONTRAT_CADRE} | {@code AUTRE} ; nul : toutes. */
    private String forme;

    /** Absent à l'écriture : actif (création) ou inchangé (modification). */
    private Boolean actif;
}
