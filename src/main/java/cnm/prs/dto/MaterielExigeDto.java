package cnm.prs.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ⚠️ V60 (demande front du 2026-10-03, matériel et personnel des travaux, §B1.2) — un engin exigé d'une fiche DAO de
 * travaux ({@code GET/PUT /api/fiches-marche/{idDmc}/materiel}). {@code ordre} : servi ; à l'écriture, la position dans la
 * liste fait foi. {@code minimumEnPropre} : {@code null} = propriété ou location indifférente, égal au nombre = tout en
 * propre. {@code parLot} : le nombre vaut pour chaque lot.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterielExigeDto {

    private Integer idMateriel;
    private Integer ordre;
    private String designation;
    private String caracteristique;
    private Integer nombre;
    private Integer minimumEnPropre;
    private Boolean parLot;

    /** Corps de {@code PUT /api/fiches-marche/{idDmc}/materiel}. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Remplacement {
        private List<MaterielExigeDto> materiel;
    }
}
