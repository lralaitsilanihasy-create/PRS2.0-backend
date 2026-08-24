package cnm.prs.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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

    /**
     * Montant estimatif du marché (Ariary). <strong>Facultatif</strong> — une ligne non chiffrée le laisse
     * {@code null} (l'import PPM émet alors l'anomalie {@code CHAMP_MANQUANT}) ; le zéro reste accepté.
     *
     * <p>⚠️ Contraintes ajoutées (2026-08-24) — un montant <strong>négatif</strong> n'a aucun sens ici : il
     * fausse silencieusement l'invariant du document ({@code montEstim = Σ des montants par bénéficiaire},
     * cf. {@code SaisieService}) et les écarts du diff de rectification. Aucune moins-value ne s'exprime par un
     * montant négatif : une baisse se saisit comme un {@code nouvMontEstim} plus petit, jamais comme un delta.
     * D'où {@code @PositiveOrZero} — et non {@code @Positive}, qui trancherait à tort le cas d'une ligne à
     * zéro. {@code @Digits} reflète la colonne {@code t_marche.MONT_ESTIM numeric(38,2)} : au-delà, la base
     * arrondissait en silence (3ᵉ décimale) ou renvoyait un 409 opaque (dépassement de capacité).</p>
     */
    @PositiveOrZero
    @Digits(integer = 36, fraction = 2)
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
