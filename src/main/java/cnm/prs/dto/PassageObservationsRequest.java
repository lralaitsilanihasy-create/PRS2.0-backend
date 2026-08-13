package cnm.prs.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * ⚠️ Spec « circuit des observations FAVR » (2026-08-02) — un PASSAGE de vérification = une décision
 * individuelle (LEVEE | MAINTENUE) pour CHAQUE observation restante du périmètre figé du PV. Aucune
 * création d'observation n'est possible par ce canal (toute référence hors périmètre → 409).
 *
 * @param idDossier dossier vérifié ({@code EN_VERIFICATION})
 * @param decisions décisions individuelles (toutes les observations non levées doivent être statuées)
 */
public record PassageObservationsRequest(
        @NotNull(message = "Le dossier est obligatoire.") Integer idDossier,
        @NotEmpty(message = "Au moins une décision est requise.") @Valid List<ObservationDecision> decisions) {

    /**
     * Décision sur UNE observation du périmètre.
     *
     * @param idObservationPv observation visée (du périmètre figé du dossier)
     * @param decision        {@code LEVEE} (satisfaite, définitive) ou {@code MAINTENUE} (rappel)
     * @param precision       précision facultative sur ce qui manque (MAINTENUE seulement)
     */
    public record ObservationDecision(
            @NotNull(message = "L'observation est obligatoire.") Integer idObservationPv,
            @NotNull(message = "La décision est obligatoire.") String decision,
            @Size(max = 500, message = "La précision ne doit pas dépasser 500 caractères.") String precision) {
    }
}
