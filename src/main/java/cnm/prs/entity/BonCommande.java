package cnm.prs.entity;

import java.math.BigDecimal;
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
 * ⚠️ V126 (lot MS-3) — un bon de commande d'un contrat en vigueur : le bon « réglementaire » du marché subséquent d'un contrat-cadre
 * ({@code idRemise}, décret n° 2019-1064, art. 33), ou un bon du marché à commandes (loi n° 2016-055, art. 30). Émis et notifié par la
 * PRMP ; annulé, il ne compte plus dans les cumuls.
 */
@Entity
@Table(name = "t_bon_commande")
@Getter
@Setter
@NoArgsConstructor
public class BonCommande {

    public static final String EMIS = "EMIS";
    public static final String ANNULE = "ANNULE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_CONTRAT", nullable = false)
    private Long idContrat;

    @Column(name = "NUMERO", nullable = false)
    private int numero;

    @Column(name = "ID_REMISE")
    private Long idRemise;

    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "ETAT", nullable = false, length = 20)
    private String etat;

    @Column(name = "OBJET", nullable = false)
    private String objet;

    @Column(name = "LIEU_LIVRAISON", length = 300)
    private String lieuLivraison;

    @Column(name = "DELAI_LIVRAISON", nullable = false, length = 100)
    private String delaiLivraison;

    @Column(name = "OBSERVATIONS")
    private String observations;

    @Column(name = "MONTANT_HT", nullable = false, precision = 18, scale = 2)
    private BigDecimal montantHt;

    @Column(name = "EMIS_LE", nullable = false)
    private LocalDateTime emisLe;

    @Column(name = "EMIS_PAR", length = 100)
    private String emisPar;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PDF")
    private byte[] pdf;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "DOCX")
    private byte[] docx;

    @Column(name = "ACCUSE_LE")
    private LocalDateTime accuseLe;

    @Column(name = "ANNULE_LE")
    private LocalDateTime annuleLe;

    @Column(name = "ANNULE_PAR", length = 100)
    private String annulePar;

    @Column(name = "MOTIF_ANNULATION")
    private String motifAnnulation;

    /** ⚠️ V148 (« bons-reception », §B2) — le taux de TVA en %, figé à l'émission ; nul pour un bon émis avant V148. */
    @Column(name = "TAUX_TVA", precision = 7, scale = 4)
    private BigDecimal tauxTva;

    @Column(name = "MONTANT_TVA", precision = 18, scale = 2)
    private BigDecimal montantTva;

    @Column(name = "MONTANT_TTC", precision = 18, scale = 2)
    private BigDecimal montantTtc;
}
