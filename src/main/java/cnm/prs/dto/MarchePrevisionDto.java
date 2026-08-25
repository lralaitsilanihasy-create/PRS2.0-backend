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

    /**
     * PK de la ligne — <strong>allouée par le serveur</strong> ({@code seq_marche_prevision}) ; toute
     * valeur envoyée en entrée est <strong>ignorée</strong>, et l'id réel figure dans la réponse.
     *
     * <p>⚠️ Correction (2026-08-25) — ce champ portait encore {@code @NotNull}, hérité de l'époque où la
     * PK était assignée par le client. Depuis le passage aux séquences, l'omettre renvoyait <strong>400
     * sur une valeur que le serveur allait de toute façon écraser</strong> : l'appelant devait inventer un
     * nombre quelconque pour que sa requête passe. C'était la dernière exception au régime 1 (PK par
     * séquence) — les onze autres ressources acceptaient déjà l'absence du champ.</p>
     */
    private Integer idPrevision;

    @NotNull
    private Integer idDetail;

    @NotNull
    private Integer idCapm;

    @NotNull
    private LocalDate dateDebut;

    /** dateFin OPTIONNELLE (fin non connue / ouverte) ; chronologie vérifiée seulement si présente. */
    private LocalDate dateFin;

    /** Ordre d'affichage du processus, porté par {@code t_capm.ORDRE} (lecture seule). */
    private Integer ordre;
}
