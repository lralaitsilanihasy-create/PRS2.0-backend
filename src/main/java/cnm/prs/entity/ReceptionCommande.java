package cnm.prs.entity;

import java.math.BigDecimal;
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
 * ⚠️ V127 (lot MS-4) — une réception d'un bon de commande par la commission de réception (loi n° 2016-055, art. 13 ; CCAG
 * fournitures, art. 22) : provisoire, après la livraison déclarée, ou définitive, au terme du délai de garantie. {@code A_ETABLIR} →
 * {@code PV_ETABLI} (la décision du président) → {@code SIGNE} (tous les membres ont signé).
 */
@Entity
@Table(name = "t_reception_commande")
@Getter
@Setter
@NoArgsConstructor
public class ReceptionCommande {

    public static final String PROVISOIRE = "PROVISOIRE";
    public static final String DEFINITIVE = "DEFINITIVE";
    public static final String A_ETABLIR = "A_ETABLIR";
    public static final String PV_ETABLI = "PV_ETABLI";
    public static final String SIGNE = "SIGNE";
    public static final String RECEPTION = "RECEPTION";
    public static final String AJOURNEMENT = "AJOURNEMENT";
    public static final String REFACTION = "REFACTION";
    public static final String REJET = "REJET";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_BON", nullable = false)
    private Long idBon;

    @Column(name = "TYPE", nullable = false, length = 20)
    private String type;

    @Column(name = "ETAT", nullable = false, length = 20)
    private String etat;

    @Column(name = "DATE_LIVRAISON")
    private LocalDate dateLivraison;

    @Column(name = "LIVRAISON_OBSERVATIONS")
    private String livraisonObservations;

    @Column(name = "DECLAREE_LE", nullable = false)
    private LocalDateTime declareeLe;

    @Column(name = "DECLAREE_PAR", length = 100)
    private String declareePar;

    @Column(name = "DECISION", length = 20)
    private String decision;

    @Column(name = "DATE_RECEPTION")
    private LocalDate dateReception;

    @Column(name = "MOTIF")
    private String motif;

    @Column(name = "RESERVES")
    private String reserves;

    @Column(name = "MONTANT_REFACTION", precision = 18, scale = 2)
    private BigDecimal montantRefaction;

    @Column(name = "JOURS_RETARD")
    private Integer joursRetard;

    @Column(name = "TAUX_PENALITE", precision = 9, scale = 4)
    private BigDecimal tauxPenalite;

    @Column(name = "PENALITE_INDICATIVE", precision = 18, scale = 2)
    private BigDecimal penaliteIndicative;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PV_PDF")
    private byte[] pvPdf;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PV_DOCX")
    private byte[] pvDocx;

    @Column(name = "ETABLI_LE")
    private LocalDateTime etabliLe;

    @Column(name = "ETABLI_PAR", length = 100)
    private String etabliPar;

    @Column(name = "SIGNE_LE")
    private LocalDateTime signeLe;
}
