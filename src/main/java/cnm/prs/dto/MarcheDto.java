package cnm.prs.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
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

    @NotNull
    private Integer idDossier;

    @NotNull
    private Integer idPpm;

    @Size(max = 500, groups = { Default.class, GroupeRectification.class })
    private String designationMarche;

    @Size(max = 20, groups = { Default.class, GroupeRectification.class })
    private String numCompte;

    /**
     * ⚠️ Audit 2026-08-27, lot B — les trois montants n'avaient <strong>aucune borne</strong> : un
     * montant négatif traversait la saisie, la rectification et l'import, et remontait tel quel dans
     * les cumuls des KPI (montant total d'un PPM, seuils de contrôle). Bornes calées sur la colonne
     * réelle {@code numeric(38,2)} : {@code @Digits(integer = 36, fraction = 2)}.
     */
    @PositiveOrZero(message = "Le montant estimé ne peut pas être négatif.",
            groups = { Default.class, GroupeRectification.class })
    @Digits(integer = 36, fraction = 2, message = "Montant hors format (36 chiffres, 2 décimales).",
            groups = { Default.class, GroupeRectification.class })
    private BigDecimal montEstim;

    @PositiveOrZero(message = "L'ancien montant estimé ne peut pas être négatif.",
            groups = { Default.class, GroupeRectification.class })
    @Digits(integer = 36, fraction = 2, message = "Montant hors format (36 chiffres, 2 décimales).",
            groups = { Default.class, GroupeRectification.class })
    private BigDecimal ancienMontEstim;

    @PositiveOrZero(message = "Le nouveau montant estimé ne peut pas être négatif.",
            groups = { Default.class, GroupeRectification.class })
    @Digits(integer = 36, fraction = 2, message = "Montant hors format (36 chiffres, 2 décimales).",
            groups = { Default.class, GroupeRectification.class })
    private BigDecimal nouvMontEstim;

    @Size(max = 20, groups = { Default.class, GroupeRectification.class })
    private String financement;

    @Size(max = 20, groups = { Default.class, GroupeRectification.class })
    private String statut;

    private Integer idNature;

    private Integer idMode;

    /**
     * ⚠️ Règle ajoutée (2026-07-18) — forme du marché : {@code A_COMMANDE} (« Marché à commande »),
     * {@code CONTRAT_CADRE} (« Contrat cadre »), {@code QUANTITE_FIXE} (« À quantité fixe »).
     * Optionnel en entrée (absent/vide → défaut QUANTITE_FIXE, code inconnu → 400 ciblé) ;
     * toujours renseigné en sortie (jamais null).
     */
    @Size(max = 20, groups = { Default.class, GroupeRectification.class })
    private String formeMarche;

    /**
     * ⚠️ Verrou optimiste (cf. {@code docs/plan-conflit-version.md}) — numéro de version de la ligne.
     * <strong>Toujours renseigné en sortie</strong> (GET, POST, PUT), le PUT rendant la version
     * <em>incrémentée</em>. En entrée de PUT : comparé à la version courante, et s'il en diffère
     * l'écriture n'a pas lieu (409 {@code CONFLIT_VERSION}). <strong>Absent/null : toléré</strong> —
     * comportement historique (dernier écrit gagne), par compatibilité ascendante ; d'où l'absence
     * volontaire de {@code @NotNull}. Ignoré en création.
     */
    private Integer version;
}
