package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V50 (2026-09-27, remise électronique, §B4) — les <strong>paramètres internes</strong> d'une procédure en remise
 * électronique ({@code t_parametre_interne_procedure}) : les membres de la commission détenteurs d'une part de clé
 * ({@code INT-SE-01}, liste de matricules), le quorum de déchiffrement ({@code INT-SE-03}) et la date de la cérémonie des
 * clés ({@code INT-SE-04}). Le nombre de parts ({@code INT-SE-02}) est la taille de la liste ; le responsable
 * ({@code INT-SE-05}) est le titulaire du rôle ({@link ResponsableProcedure}).
 *
 * <p><strong>Hors référentiel, hors moteur de rendu</strong> : ce ne sont pas des champs de la fiche, aucun jeton ne les
 * atteint ({@code ModelesCandidat} refuse tout {@code {{INT-…}}}), et ils ne sont servis qu'au titulaire du rôle.</p>
 */
@Entity
@Table(name = "t_parametre_interne_procedure")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ParametreInterneProcedure {

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    /** Matricules des membres détenteurs d'une part de clé, séparés par des virgules ({@code INT-SE-01}). */
    @Column(name = "MEMBRES_CLE", length = 1000)
    private String membresCle;

    /** Quorum de déchiffrement ({@code INT-SE-03}). */
    @Column(name = "QUORUM")
    private Integer quorum;

    /** Date et heure de la cérémonie des clés ({@code INT-SE-04}). */
    @Column(name = "DATE_CEREMONIE")
    private LocalDateTime dateCeremonie;

    @Column(name = "DATE_MAJ", nullable = false)
    private LocalDateTime dateMaj;

    /** Dernier modificateur (matricule du titulaire du rôle). */
    @Column(name = "IM_MAJ", length = 10)
    private String imMaj;

    /** ⚠️ V66 (2026-10-04, soumission en ligne, lot 2, §B1) — le dépositaire de la part de secours (ADR-0013 S3) : une désignation nominative. */
    @Column(name = "DEPOSITAIRE_NOM", length = 200)
    private String depositaireNom;

    @Column(name = "DEPOSITAIRE_ORGANISME", length = 200)
    private String depositaireOrganisme;

    @Column(name = "DEPOSITAIRE_FONCTION", length = 200)
    private String depositaireFonction;

    @Column(name = "DEPOSITAIRE_CONTACT", length = 300)
    private String depositaireContact;
}
