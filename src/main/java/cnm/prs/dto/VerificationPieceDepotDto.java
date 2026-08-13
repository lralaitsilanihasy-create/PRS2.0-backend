package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de {@link cnm.prs.entity.VerificationPieceDepot}. Au POST : {@code idDossier}, {@code idTypePiece},
 * {@code decision} (CONFORME / NON_CONFORME / MANQUANTE) requis ; {@code idPiece} / {@code observation}
 * facultatifs. Auteur et horodatage posés serveur (JWT).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationPieceDepotDto {

    private Integer idVerifPiece;

    @NotNull
    private Integer idDossier;

    @NotNull
    private Integer idTypePiece;

    private Integer idPiece;

    @NotBlank
    @Size(max = 20)
    private String decision;

    @Size(max = 500)
    private String observation;

    private String imSecretaire;
    private LocalDateTime dateVerif;
}
