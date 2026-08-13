package cnm.prs.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/mandats/{id}/abroger} — fin de mandat avant terme.
 *
 * <p>L'abrogation ne réattribue rien : les dossiers gardent leur mandat d'attribution. Elle ouvre
 * simplement la <strong>vacance</strong> jusqu'à la nomination du successeur.</p>
 *
 * @param motif          motif de l'abrogation (obligatoire, tracé)
 * @param dateAbrogation date d'effet ; à défaut la date du jour
 */
public record AbrogerMandatRequest(
        @NotBlank @Size(max = 255) String motif,
        LocalDate dateAbrogation) {
}
