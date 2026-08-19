package cnm.prs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Actualite} — spec « Actualités » du 2026-08-18.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActualiteDto {

    private Integer idActualite;    // ignoré en entrée (IDENTITY)

    @NotBlank
    @Size(max = 200)
    private String titre;

    /** Markdown brut. Aucun HTML n'est accepté (400) ni renvoyé. */
    @NotBlank
    private String contenuMd;

    /** Noms d'enum {@link cnm.prs.enums.ProfilUtilisateur} — au moins un (400 si vide ou inconnu). */
    @NotEmpty
    private List<String> profilsCibles;

    /** Forcé INACTIF à la création ; ACTIF/INACTIF par le PUT ; ARCHIVE par le DELETE seul. */
    @Size(max = 20)
    private String statut;

    /** Nullable = visible dès activation. */
    private LocalDate datePublication;

    /** Nullable = sans terme ; antérieure à datePublication → 400. */
    private LocalDate dateExpiration;

    /** Lecture seule (peuplé serveur), triées par ordre. */
    private List<ActualiteImageDto> images;

    private LocalDateTime dateCreation;   // serveur
    private String imAuteur;              // serveur (JWT)
    private LocalDateTime dateArchivage;  // serveur
    private String imArchiveur;           // serveur (null = archivage automatique à l'expiration)
}
