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
     * ⚠️ Fiche de présentation (2026-09-01) — la « Justification : » du bas du formulaire officiel,
     * <strong>globale à la fiche</strong> (arbitrage 2). Exigée par la façade de saisie dès qu'au moins
     * une des trois listes de la fiche est non vide — les contrats-cadres n'ayant pas de justification
     * par ligne, c'est elle qui les couvre.
     *
     * <p>Écriture : {@code null} = inchangé, chaîne fournie = écrite après {@code trim}, blanc =
     * effacé (même sémantique que les justifications de ligne sur {@link MarcheDto}).</p>
     */
    @Size(max = 1000, groups = { Default.class, GroupeRectification.class })
    private String justificationFiche;

    /**
     * <strong>Dérivé serveur (lecture seule)</strong> : {@code true} ssi ≥1 marché de ce PPM est en
     * « appel d'offres ouvert » ({@code ModePassation.declencheAgpm}).
     *
     * <p>⚠️ <strong>Le nom a survécu à sa règle</strong> (2026-09-03). Il signifiait « un AGPM doit
     * accompagner le PPM » ; l'obligation de la <em>pièce jointe</em> AGPM a été retirée par le pilote,
     * le <strong>projet d'AGPM dérivé du plan</strong> tenant ce rôle. Ce que le drapeau dit encore, et
     * qui reste utile : <strong>ce plan comporte un appel d'offres ouvert</strong> — d'où le sous-type
     * dérivé {@code PPM-AGPM}, l'onglet du projet d'AGPM et sa grille de contrôle à l'examen.</p>
     *
     * <p>Conservé sous ce nom à dessein : le renommer romprait le contrat que le front lit déjà. Le
     * front l'<em>affiche</em>, ne le recalcule pas ; toute valeur envoyée en écriture est ignorée.</p>
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
