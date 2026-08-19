package cnm.prs.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Actualité affichée à l'ouverture de session (table {@code t_actualite}) — spec du 2026-08-18.
 *
 * <p>Mini-page façon lettre d'information : contenu <strong>markdown brut</strong> (aucun HTML
 * accepté — la surface XSS soldée par l'audit du 16-17/08 ne se rouvre pas), images en table
 * dédiée ({@code t_actualite_image}), ciblage par profils ({@code t_actualite_profil}).
 * Suppression = <strong>archivage logique</strong> (statut {@code ARCHIVE}), jamais physique.</p>
 */
@Entity
@Table(name = "t_actualite")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Actualite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ACTUALITE", nullable = false)
    private Integer idActualite;

    @Column(name = "TITRE", nullable = false, length = 200)
    private String titre;

    /** Markdown brut, stocké tel quel (jamais de HTML). */
    @Column(name = "CONTENU_MD", nullable = false, columnDefinition = "text")
    private String contenuMd;

    /** Cf. {@link cnm.prs.enums.StatutActualite} : ACTIF / INACTIF / ARCHIVE. */
    @Column(name = "STATUT", nullable = false, length = 20)
    private String statut;

    /** Nullable = visible dès activation. */
    @Column(name = "DATE_PUBLICATION")
    private LocalDate datePublication;

    /** Nullable = sans terme. Atteinte (jour J compris) → archivage automatique. */
    @Column(name = "DATE_EXPIRATION")
    private LocalDate dateExpiration;

    @Column(name = "DATE_CREATION", nullable = false)
    private LocalDateTime dateCreation;

    /** Auteur (Administrateur), identité JWT. */
    @Column(name = "IM_AUTEUR", length = 10)
    private String imAuteur;

    @Column(name = "DATE_ARCHIVAGE")
    private LocalDateTime dateArchivage;

    /** Archiveur (Administrateur) — {@code null} si archivage automatique à l'expiration. */
    @Column(name = "IM_ARCHIVEUR", length = 10)
    private String imArchiveur;
}
