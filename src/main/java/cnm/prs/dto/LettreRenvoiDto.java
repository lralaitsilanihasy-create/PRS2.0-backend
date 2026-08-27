package cnm.prs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.LettreRenvoi}.
 * {@code idDossier}, {@code refLettre}, {@code dateExamen}, {@code dateLettre}, {@code statut} et
 * {@code imSignataire} sont posés/dérivés par le serveur (lecture seule ; ignorés en entrée).
 * L'objet de la lettre est fixe (« lettre de renvoi », déjà inscrit dans les modèles Word) :
 * plus aucun champ {@code objetLettre} n'est saisi ni retourné ; s'il est encore envoyé par
 * un ancien frontend, il est ignoré ({@link JsonIgnoreProperties}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LettreRenvoiDto {

    private Integer idLettre;

    @NotNull(message = "L'examen est obligatoire.")
    private Integer idExamen;

    private Integer idDossier;

    private String refLettre;

    /** Corps libre de la lettre (TEXT, nullable, sans contrainte de taille). */
    private String corpsLettre;

    private LocalDate dateExamen;

    private LocalDate dateLettre;

    private String statut;

    private String imSignataire;

    /** Nom complet du signataire (« prénoms nom »), peuplé serveur en lecture (lecture seule). */
    private String nomSignataire;

    /** Vrai si la lettre a déjà été lue par la PRMP courante (lecture seule). */
    private Boolean lue;

    /** ⚠️ Spec navette (2026-08-01) — date d'archivage par l'Assistant contrôleur (lecture seule). */
    private LocalDate dateArchivage;

    /** Assistant contrôleur archiveur (matricule, lecture seule). */
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
