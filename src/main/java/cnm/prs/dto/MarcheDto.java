package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Marche}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarcheDto {

    private Integer idDetail;

    /**
     * ⚠️ 2026-08-05 — identité de la ligne à travers les versions du PPM. Posée par le serveur à la copie
     * de version ; permet au client d'apparier une ligne à son entrée de diff.
     */
    private Integer idLigneOrigine;

    /** ⚠️ 2026-08-05 — ligne supprimée logiquement dans cette version (restaurable, jamais effacée). */
    private Boolean supprimee;

    /**
     * Rattachement figé au dossier. Exigé en création/mise à jour ({@link GroupesValidation.Identite}) ;
     * non exigé en rectification, où le serveur conserve la valeur existante.
     */
    @NotNull(groups = GroupesValidation.Identite.class)
    private Integer idDossier;

    @NotNull(groups = GroupesValidation.Identite.class)
    private Integer idPpm;

    @Size(max = 500)
    private String designationMarche;

    @Size(max = 20)
    private String numCompte;

    private BigDecimal montEstim;

    private BigDecimal ancienMontEstim;

    private BigDecimal nouvMontEstim;

    @Size(max = 20)
    private String financement;

    @Size(max = 20)
    private String statut;

    private Integer idNature;

    private Integer idMode;

    /**
     * ⚠️ Règle ajoutée (2026-07-18) — forme du marché : {@code A_COMMANDE} (« Marché à commande »),
     * {@code CONTRAT_CADRE} (« Contrat cadre »), {@code QUANTITE_FIXE} (« À quantité fixe »).
     * Optionnel en entrée (absent/vide → défaut QUANTITE_FIXE, code inconnu → 400 ciblé) ;
     * toujours renseigné en sortie (jamais null).
     */
    @Size(max = 20)
    private String formeMarche;
}
