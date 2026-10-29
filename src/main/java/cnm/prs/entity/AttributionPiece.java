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
    /** ⚠️ R9a (V111) — la décision d'approbation du marché. */
    public static final String APPROBATION = "APPROBATION";
    /** ⚠️ R10 (V113, §B5, H2) — l'original signé de l'acte d'engagement de l'attributaire ; {@code dateDelivrance} = sa date de remise. */
    public static final String ACTE_ENGAGEMENT_ORIGINAL = "ACTE_ENGAGEMENT_ORIGINAL";
    /** ⚠️ V149 (« chaine-attribution », §B1) — le marché visé par le contrôleur financier. */
    public static final String VISA_CF = "VISA_CF";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Column(name = "NATURE", nullable = false, length = 30)
    private String nature;

    /**
     * ⚠️ V123 (2026-10-16, tranche MT-b2) — l'offre qui produit une pièce de l'attributaire (fiscale, sociale, original de l'acte
     * d'engagement) : chaque titulaire d'un contrat-cadre multi-attributaire a les siennes. Nulle pour les autres natures.
     */
    @Column(name = "ID_OFFRE", length = 36)
    private String idOffre;

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
