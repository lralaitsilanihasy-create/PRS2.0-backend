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

    @NotNull
    private Integer idDossier;

    @NotNull
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
