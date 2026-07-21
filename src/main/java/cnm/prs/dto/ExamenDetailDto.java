package cnm.prs.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.ExamenDetail}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamenDetailDto {

    private Integer idDetailExamen;

    @NotNull
    private Integer idExamen;

    /**
     * ⚠️ Règle ajoutée (2026-07-21) — ligne de marché examinée (FK {@code t_marche}, facultatif) : renseignée
     * pour un point de portée {@code LIGNE} (résultat par marché), {@code null} pour un point {@code DOSSIER}
     * (inter-lignes) ou un examen historique. Doit appartenir au dossier de l'examen (sinon 400) ; un point
     * {@code DOSSIER} avec {@code idDetail} renseigné → 400.
     */
    private Integer idDetail;

    @NotNull
    private Integer idPtControle;

    @NotNull
    private Boolean conforme;

    /** Lignes d'observation « AU LIEU DE / LIRE » : {@code []} si conforme, N lignes si non conforme. */
    private List<ObservationControleDto> observations;

    @Size(max = 500)
    private String obsSiNonConforme;
}
