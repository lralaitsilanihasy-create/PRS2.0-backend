package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V80 (évaluation des offres, lot 2, tranche 2b, §B4.1, art. 52-I) — une lettre produite à l'information des candidats : la lettre
 * d'attribution, ou celle d'un candidat non retenu et ses motifs ; signée de la PRMP (signature électronique simple, Q9). La date
 * d'envoi du courriel et l'accusé de lecture de la plateforme (première consultation par le candidat) en font la preuve de réception.
 */
@Entity
@Table(name = "t_attribution_lettre")
@Getter
@Setter
@NoArgsConstructor
public class AttributionLettre {

    public static final String ATTRIBUTION = "ATTRIBUTION";
    public static final String NON_RETENU = "NON_RETENU";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "TYPE", nullable = false, length = 20)
    private String type;

    @Column(name = "MOTIF")
    private String motif;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PDF")
    private byte[] pdf;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "DOCX")
    private byte[] docx;

    @Column(name = "PRODUITE_LE", nullable = false)
    private LocalDateTime produiteLe;

    @Column(name = "ENVOYEE_LE")
    private LocalDateTime envoyeeLe;

    @Column(name = "LUE_LE")
    private LocalDateTime lueLe;

    /** ⚠️ V92 (2d-2) — l'archivage du cycle retiré : nul tant que la pièce (le recours, la lettre) vaut pour le lot en cours. */
    @Column(name = "ARCHIVE_LE")
    private java.time.LocalDateTime archiveLe;
}
