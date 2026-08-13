package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité JPA mappée sur la table {@code t_lettre_renvoi} : lettre de renvoi d'un examen,
 * action séparée pendant l'examen. Un examen peut produire <strong>N lettres</strong> ({@code ID_EXAMEN}
 * non unique). Cycle : {@code BROUILLON → SOUMIS → SIGNE} (signature CC ou Président). PK auto (IDENTITY).
 */
@Entity
@Table(name = "t_lettre_renvoi")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LettreRenvoi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_LETTRE", nullable = false)
    private Integer idLettre;

    @Column(name = "ID_EXAMEN", nullable = false)
    private Integer idExamen;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    /** Référence officielle, générée serveur : {@code <seq>/<type>/<code_localite>/LR/<année>}. */
    @Column(name = "REF_LETTRE", length = 50)
    private String refLettre;

    @Column(name = "OBJET_LETTRE", length = 500)
    private String objetLettre;

    /** Corps libre de la lettre de renvoi (TEXT, nullable). */
    @Column(name = "CORPS_LETTRE", columnDefinition = "text")
    private String corpsLettre;

    /** Date d'examen du dossier (reprise de l'examen). */
    @Column(name = "DATE_EXAMEN")
    private LocalDate dateExamen;

    /** Date de la lettre, posée serveur (jour de la soumission de l'examen). */
    @Column(name = "DATE_LETTRE")
    private LocalDate dateLettre;

    @Column(name = "STATUT", length = 20)
    private String statut;

    /** IM du signataire (CC ou Président), posé à la signature depuis le JWT. */
    @Column(name = "IM_SIGNATAIRE", length = 7)
    private String imSignataire;

    /** Document PDF de la lettre signée (bytea ; H2 : grand varbinary). Conservé pour compatibilité ;
     *  le stockage de référence est désormais le fichier ({@link #cheminDocument}). */
    @Column(name = "DOCUMENT_PDF", length = 1_000_000)
    @JsonIgnore
    private byte[] documentPdf;

    /** Chemin du PDF stocké sur le système de fichiers (FSX, répertoire LR/), posé à la signature. */
    @Column(name = "CHEMIN_DOCUMENT", length = 500)
    private String cheminDocument;

    /** ⚠️ Spec navette (2026-08-01) — date d'ARCHIVAGE de la lettre par l'Assistant contrôleur (même circuit que les PV). */
    @Column(name = "DATE_ARCHIVAGE")
    private LocalDate dateArchivage;

    /** Assistant contrôleur archiveur (matricule, identité JWT). */
    @Column(name = "IM_ARCHIVEUR", length = 7)
    private String imArchiveur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_EXAMEN", insertable = false, updatable = false)
    @JsonIgnore
    private Examen examen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DOSSIER", insertable = false, updatable = false)
    @JsonIgnore
    private Dossier dossier;
}
