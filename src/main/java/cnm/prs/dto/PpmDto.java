package cnm.prs.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Ppm}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PpmDto {

    private Integer idPpm;

    @NotNull
    private Integer idDossier;

    /**
     * ⚠️ Audit 2026-08-27, lot B — exercice budgétaire, jusque-là sans borne : « 12 » ou « 20260 »
     * étaient acceptés et se retrouvaient dans la référence officielle du dossier
     * ({@code .../<année>}) comme dans les filtres par exercice. Fenêtre volontairement large
     * (2000-2100) : le but est d'écarter la faute de frappe, pas d'arbitrer un calendrier.
     */
    @NotNull
    @Min(value = 2000, message = "L'exercice budgétaire doit être compris entre 2000 et 2100.",
            groups = { Default.class, GroupeRectification.class })
    @Max(value = 2100, message = "L'exercice budgétaire doit être compris entre 2000 et 2100.",
            groups = { Default.class, GroupeRectification.class })
    private Integer exercice;

    @NotBlank
    @Size(max = 210, groups = { Default.class, GroupeRectification.class })
    private String signataire;

    @NotNull
    private LocalDate dateSignature;

    private LocalDate datePpmInit;

    private Integer numMajPrec;

    private LocalDate dateMajPrec;

    private Integer numMaj;

    private LocalDate dateMaj;

    @NotBlank
    @Size(max = 100, groups = { Default.class, GroupeRectification.class })
    private String reference;

    @Size(max = 200, groups = { Default.class, GroupeRectification.class })
    private String libelle;

    private LocalDate dateReceptionCnm;

    @Size(max = 5, groups = { Default.class, GroupeRectification.class })
    private String idLocalite;

    @Size(max = 100, groups = { Default.class, GroupeRectification.class })
    private String vu;

    @Size(max = 10, groups = { Default.class, GroupeRectification.class })
    private String idPrmp;

    @Size(max = 500, groups = { Default.class, GroupeRectification.class })
    private String motifMaj;

    /**
     * <strong>Dérivé serveur (lecture seule)</strong> : {@code true} ssi ≥1 marché de ce PPM est en
     * « appel d'offres ouvert » ({@code ModePassation.declencheAgpm}). Indique au front qu'un AGPM
     * (Avis Général de Passation de Marché) doit accompagner le PPM. Le front l'<em>affiche</em>, ne le
     * recalcule pas ; toute valeur envoyée en écriture est ignorée.
     */
    private Boolean agpmRequis;

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
