package cnm.prs.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/interims/{id}/revoquer} — fin avant terme (demande front du 2026-09-21, §B2).
 *
 * @param motif          obligatoire
 * @param dateRevocation date d'effet ; à défaut aujourd'hui, jamais antérieure à aujourd'hui (400)
 */
public record RevoquerInterimRequest(
        @NotBlank @Size(max = 255) String motif,
        LocalDate dateRevocation) {
}
