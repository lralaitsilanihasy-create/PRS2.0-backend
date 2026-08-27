package cnm.prs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.PvExamen}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PvExamenDto {

    private Integer idPv;

    @NotNull
    private Integer idExamen;

    /** ⚠️ Règle ajoutée (2026-08-01) — OPTIONNEL : l'avis est posé à la clôture de navette (accepter, Président/CC). */
    @Size(max = 10)
    private String idAvis;

    @Size(max = 7)
    private String imCtrlPresident;

    @Size(max = 7)
    private String imCtrlCc;

    @NotBlank
    @Size(max = 7)
    private String imCtrlMembre;

    private String syntheseObservations;

    @NotBlank
    @Size(max = 20)
    private String statutPv;

    @NotNull
    private Integer nbNavettes;

    private LocalDate dateSoumissionInitiale;

    private LocalDate dateAcceptation;

    private LocalDate dateSignaturePresident;

    private LocalDate dateSignatureCc;

    private LocalDate dateSignatureMembre;

    private LocalDate datePv;

    @Size(max = 100)
    private String referencePv;

    /** Référence officielle du PV (dérivée du dossier) — lecture seule, générée serveur. */
    @Size(max = 120)
    private String refePv;

    /** Vérificateur désigné Secrétaire de séance (matricule). Posé à la soumission de l'examen. */
    @Size(max = 7)
    private String idSecretaireSeance;

    /** Nom complet du secrétaire de séance (« prénoms nom ») — lecture seule, peuplé serveur. */
    private String nomSecretaireSeance;

    /**
     * Vrai si un PDF officiel est réellement disponible (lecture seule, peuplé serveur) : fichier déjà stocké
     * ({@code CHEMIN_DOCUMENT} non nul) <strong>ou</strong> PV éligible à la génération à la demande (avis FAVR
     * + localité centrale ANT + toutes lignes de marché en appel d'offres ouvert). {@code false} sinon — le
     * front masque « Télécharger le PDF » et évite un 404.
     */
    private Boolean documentDisponible;

    /** ⚠️ Spec navette (2026-08-01) — date d'archivage du PV par l'Assistant contrôleur (lecture seule). */
    private LocalDate dateArchivage;

    /** Assistant contrôleur archiveur (matricule, lecture seule). */
    @Size(max = 7)
    private String imArchiveur;

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
