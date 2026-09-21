package cnm.prs.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Une ligne du journal des actions d'un dossier ({@link cnm.prs.entity.ActionDossier}).
 *
 * <p>{@code idPrmpOperateur} est la PRMP <strong>en fonction</strong> au moment de l'action ; elle diffère
 * de la PRMP d'attribution du dossier après un changement de titulaire — c'est le point de la traçabilité.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionDossierDto {

    private Long idAction;

    private Integer idDossier;

    private LocalDateTime dateAction;

    private String typeAction;

    private String idPrmpOperateur;

    private String nomOperateur;

    private String auteur;

    private Integer idMandatOperateur;

    private String detail;

    /**
     * ⚠️ Intérim désigné (2026-09-21, §B4.8) — geste posé <strong>par intérim</strong> de ce titulaire
     * (matricule) ; l'auteur et le nom de la ligne restent ceux de l'intérimaire, qui a agi. {@code null} sinon.
     * Le front en dérive la visibilité hiérarchique (rang du titulaire) : le serveur ne filtre pas.
     */
    private String interimDe;

    private Integer idInterim;
}
