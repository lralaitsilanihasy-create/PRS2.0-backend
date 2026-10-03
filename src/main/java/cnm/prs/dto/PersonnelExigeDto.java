package cnm.prs.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ⚠️ V60 (demande front du 2026-10-03, matériel et personnel des travaux, §B1.3) — un poste clé exigé d'une fiche DAO de
 * travaux ({@code GET/PUT /api/fiches-marche/{idDmc}/personnel}). {@code nombre} : 1 s'il est absent. {@code parLot} :
 * chaque lot mobilise son équipe.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonnelExigeDto {

    private Integer idPersonnel;
    private Integer ordre;
    private String poste;
    private Integer nombre;
    private String diplome;
    private Integer experienceAnnees;
    private String domaineExperience;
    private String justificatifs;
    private Boolean parLot;

    /** Corps de {@code PUT /api/fiches-marche/{idDmc}/personnel}. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Remplacement {
        private List<PersonnelExigeDto> personnel;
    }
}
