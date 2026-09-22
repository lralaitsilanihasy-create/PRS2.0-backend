package cnm.prs.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un champ du référentiel de la fiche marché ({@link cnm.prs.entity.ChampFicheMarche}) — lecture et écriture
 * (Administrateur). Les listes sont servies et reçues comme des tableaux JSON.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChampFicheMarcheDto {

    /** « B05-GS-02 » : bloc, rubrique, rang. */
    @NotBlank
    @Pattern(regexp = "B\\d{2}-[A-Z0-9]{1,6}-\\d{2}", message = "code attendu de la forme B05-GS-02")
    private String code;

    /** Dérivé du code, lecture seule. */
    private String bloc;

    /** Code complet de la rubrique (« B05-GS »), dérivé du code ; ignoré en écriture. */
    private String rubrique;

    /** Absent en écriture : dérivé du code (« B05-GS-07 » → 7). */
    private Integer rang;

    @NotBlank
    @Size(max = 200)
    private String libelle;

    @NotBlank
    private String type;

    @NotBlank
    private String source;

    private String documentMaitre;

    private List<String> reprises;

    private List<String> typesMarche;

    @Size(max = 300)
    private String condition;

    private Boolean obligatoire;

    @Size(max = 1000)
    private String texteType;

    @Size(max = 60)
    private String controle;

    private List<String> options;

    @Size(max = 40)
    private String cleCadrage;

    @Size(max = 40)
    private String clePpm;

    private Boolean actif;
}
