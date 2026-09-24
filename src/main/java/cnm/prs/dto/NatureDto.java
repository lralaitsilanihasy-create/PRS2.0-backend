package cnm.prs.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Nature}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NatureDto {

    private Integer idNature;

    @Size(max = 100)
    private String libelle;

    @Size(max = 500)
    private String description;

    /**
     * ⚠️ Fiche DAO, lot 5 (2026-09-24) — catégorie de fiche DAO des lignes de cette nature : {@code FOURNITURES_SERVICES},
     * {@code TRAVAUX}, {@code PRESTATIONS_INTELLECTUELLES} ; 400 sinon. En {@code PUT}, absente = inchangée, vide = retirée.
     */
    private String categorieDao;
}
