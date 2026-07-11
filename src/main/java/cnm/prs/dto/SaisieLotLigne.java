package cnm.prs.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lot d'une ligne de marché à la saisie / import PPM (une ligne {@code t_lot}). Mêmes champs que
 * {@link LotDto} <strong>sans</strong> {@code idLot}/{@code idDossier}/{@code idDetail} (renseignés par le
 * serveur : PK allouée, dossier et marché du contexte). {@code montLot}/{@code qteLot}/{@code uniteLot} sont
 * optionnels et descriptifs (aucun contrôle de somme).
 */
public record SaisieLotLigne(

        @NotBlank
        @Size(max = 200)
        String designationLot,

        BigDecimal montLot,

        Integer qteLot,

        @Size(max = 10)
        String uniteLot) {
}
