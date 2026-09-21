package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Controleur}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ControleurDto {

    private String imControleur;

    @Size(max = 100)
    private String nomCont;

    @Size(max = 100)
    private String prenomsCont;

    @Size(max = 100)
    private String emailCont;

    @Size(max = 20)
    private String telCont;

    private Integer idProfile;

    @Size(max = 5)
    private String idLocalite;

    @Size(max = 7)
    private String idSuperieur;

    @NotNull
    private Boolean transversal;

    /**
     * ⚠️ Intérim désigné (2026-09-21, §B5) — lecture seule, ignoré en écriture. Sur un titulaire absent :
     * l'intérim ACTIF qui le supplée aujourd'hui, ou {@code null}. Masqué (null) pour la PRMP et l'UGPM :
     * qui supplée qui à la Commission est une vue interne (règle C2).
     */
    private InterimLiensDto.EnCours interimEnCours;

    /** Lecture seule : les intérims ACTIFS que ce contrôleur exerce aujourd'hui ; vide s'il n'en exerce aucun. */
    private java.util.List<InterimLiensDto.Pour> interimPour;
}
