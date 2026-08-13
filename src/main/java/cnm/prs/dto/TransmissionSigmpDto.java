package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de {@link cnm.prs.entity.TransmissionSigmp}. Au POST, seul {@code idDossier} est requis :
 * le sens (APPROUVE / NON_APPROUVE), la levée d'observations, la date et l'auteur sont dérivés
 * serveur (avis du PV signé + statut du dossier + JWT).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransmissionSigmpDto {

    private Integer idTransmission;

    @NotNull
    private Integer idDossier;

    private Integer idPv;
    private String sens;
    private Boolean leveeObservations;
    private LocalDateTime dateTransmission;
    private String imVerificateur;
    private String statutEnvoi;
}
