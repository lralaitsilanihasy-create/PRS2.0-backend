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
 * ⚠️ V91 (lot 2, tranche 2d-1, Q3) — une reprise de l'évaluation après l'avis défavorable de la Commission sur le dossier de marché :
 * le dossier refusé et son avis, le motif de la PRMP, et le rapport d'évaluation archivé (il cède la place au nouveau rapport).
 */
@Entity
@Table(name = "t_attribution_reprise")
@Getter
@Setter
@NoArgsConstructor
public class AttributionReprise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Column(name = "ID_DOSSIER")
    private Integer idDossier;

    @Column(name = "ID_OFFRE_PROPOSEE", length = 36)
    private String idOffreProposee;

    @Column(name = "AVIS", length = 10)
    private String avis;

    @Column(name = "MOTIF", nullable = false)
    private String motif;

    @Column(name = "LE", nullable = false)
    private LocalDateTime le;

    @Column(name = "PAR", length = 100)
    private String par;

    @Column(name = "RAPPORT_PRODUIT_LE")
    private LocalDateTime rapportProduitLe;

    @Column(name = "RAPPORT_SIGNE_LE")
    private LocalDateTime rapportSigneLe;

    @Column(name = "RAPPORT_SIGNATURES")
    private String rapportSignatures;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "RAPPORT_PDF")
    private byte[] rapportPdf;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "RAPPORT_DOCX")
    private byte[] rapportDocx;
}
