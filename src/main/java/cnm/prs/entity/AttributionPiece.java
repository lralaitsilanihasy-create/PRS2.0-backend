package cnm.prs.entity;

import java.time.LocalDate;
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
 * ⚠️ V81 (évaluation des offres, lot 2, tranche 2c) — un fichier de l'attribution d'un lot : rapport de mise au point, marché signé,
 * justificatif d'enregistrement, pièce d'un recours ou de sa décision (déposés par la PRMP), pièce fiscale ou sociale de l'attributaire
 * (art. 20-I : date de délivrance, vérification de la PRMP).
 */
@Entity
@Table(name = "t_attribution_piece")
@Getter
@Setter
@NoArgsConstructor
public class AttributionPiece {

    public static final String MISE_AU_POINT = "MISE_AU_POINT";
    public static final String MARCHE_SIGNE = "MARCHE_SIGNE";
    public static final String ENREGISTREMENT = "ENREGISTREMENT";
    public static final String RECOURS = "RECOURS";
    public static final String DECISION_RECOURS = "DECISION_RECOURS";
    public static final String FISCALE = "FISCALE";
    public static final String SOCIALE = "SOCIALE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Column(name = "NATURE", nullable = false, length = 20)
    private String nature;

    @Column(name = "ID_RECOURS")
    private Long idRecours;

    @Column(name = "NOM", nullable = false, length = 255)
    private String nom;

    @Column(name = "FORMAT", nullable = false, length = 50)
    private String format;

    @Column(name = "TAILLE", nullable = false)
    private Long taille;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "CONTENU", nullable = false)
    private byte[] contenu;

    @Column(name = "DEPOSE_LE", nullable = false)
    private LocalDateTime deposeLe;

    @Column(name = "DEPOSE_PAR", length = 100)
    private String deposePar;

    @Column(name = "DATE_DELIVRANCE")
    private LocalDate dateDelivrance;

    @Column(name = "CONFORME")
    private Boolean conforme;

    @Column(name = "MOTIF")
    private String motif;

    @Column(name = "VERIFIEE_LE")
    private LocalDateTime verifieeLe;

    @Column(name = "VERIFIEE_PAR", length = 100)
    private String verifieePar;

    /** ⚠️ V92 (2d-2) — l'archivage du cycle retiré : nul tant que la pièce (le recours, la lettre) vaut pour le lot en cours. */
    @Column(name = "ARCHIVE_LE")
    private java.time.LocalDateTime archiveLe;
}
