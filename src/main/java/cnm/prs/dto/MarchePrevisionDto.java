package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.MarchePrevision} : date prévisionnelle d'un marché
 * pour un processus ({@code idCapm}). {@code ordre} est en lecture seule (porté par {@code t_capm}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarchePrevisionDto {

    @NotNull
    private Integer idPrevision;

    @NotNull
    private Integer idDetail;

    @NotNull
    private Integer idCapm;

    /** ⚠️ V114 — obligatoire sauf processus sans objet (vérifié par le service). */
    private LocalDate dateDebut;

    /** dateFin OPTIONNELLE (fin non connue / ouverte) ; chronologie vérifiée seulement si présente. */
    private LocalDate dateFin;

    /** Ordre d'affichage du processus, porté par {@code t_capm.ORDRE} (lecture seule). */
    private Integer ordre;

    /** ⚠️ V114 (2026-10-14, demande « q-piece », §B1, H2) — processus du modèle sans objet pour ce marché (aucune date). */
    private Boolean sansObjet;

    /** ⚠️ V114 — motif du « sans objet », exigé avec lui. */
    private String motifSansObjet;

    public MarchePrevisionDto(Integer idPrevision, Integer idDetail, Integer idCapm, LocalDate dateDebut, LocalDate dateFin,
            Integer ordre) {
        this(idPrevision, idDetail, idCapm, dateDebut, dateFin, ordre, null, null);
    }
}
