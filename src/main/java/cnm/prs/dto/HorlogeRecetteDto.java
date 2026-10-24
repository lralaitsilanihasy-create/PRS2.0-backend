package cnm.prs.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** ⚠️ 2026-10-24 (demande front « horloge-recette », R1) — l'horloge de recette, vue de l'Administrateur. */
public final class HorlogeRecetteDto {

    private HorlogeRecetteDto() {
    }

    /** L'état : l'heure du serveur, l'heure réelle et l'écart entre les deux (ISO-8601 et en minutes). */
    public record Etat(LocalDateTime maintenant, LocalDateTime heureReelle, String decalage, long decalageMinutes,
            boolean decalee) {
    }

    /**
     * Le réglage : {@code maintenant} (l'heure que le serveur doit lire désormais) <strong>ou</strong> {@code avancer} (une durée
     * ISO-8601 ajoutée au décalage, « PT2H », « P3D », négative pour reculer) ; {@code motif} obligatoire.
     */
    public record Reglage(LocalDateTime maintenant, String avancer, @NotBlank @Size(max = 500) String motif) {
    }
}
