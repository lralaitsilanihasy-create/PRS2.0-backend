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
 * ⚠️ V125 (2026-10-16, demande front « marchés subséquents », lot MS-2) — une remise en concurrence des titulaires d'un contrat-cadre
 * en vigueur, ou la complétude de l'offre du titulaire unique (mono-attributaire) : la lettre d'invitation, l'échéance, l'évaluation de
 * la CAO (prix moyen du marché, proposition, rapport simplifié) et la décision de la PRMP (un seul titulaire retenu, ou l'infructuosité).
 */
@Entity
@Table(name = "t_remise_concurrence")
@Getter
@Setter
@NoArgsConstructor
public class RemiseConcurrence {

    public static final String REMISE_EN_CONCURRENCE = "REMISE_EN_CONCURRENCE";
    public static final String COMPLETUDE = "COMPLETUDE";
    public static final String LANCEE = "LANCEE";
    public static final String EVALUEE = "EVALUEE";
    public static final String ATTRIBUEE = "ATTRIBUEE";
    public static final String INFRUCTUEUSE = "INFRUCTUEUSE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_CONTRAT", nullable = false)
    private Long idContrat;

    @Column(name = "NUMERO", nullable = false)
    private Integer numero;

    @Column(name = "MODE", nullable = false, length = 30)
    private String mode;

    @Column(name = "ETAT", nullable = false, length = 20)
    private String etat;

    @Column(name = "OBJET", nullable = false)
    private String objet;

    @Column(name = "SPECIFICATIONS", nullable = false)
    private String specifications;

    @Column(name = "CRITERES")
    private String criteres;

    @Column(name = "LIEU_LIVRAISON", length = 300)
    private String lieuLivraison;

    @Column(name = "DELAI_EXECUTION", nullable = false, length = 100)
    private String delaiExecution;

    @Column(name = "VALIDITE_JOURS")
    private Integer validiteJours;

    @Column(name = "VISITE")
    private String visite;

    @Column(name = "ECHEANCE", nullable = false)
    private LocalDateTime echeance;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "LETTRE_PDF")
    private byte[] lettrePdf;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "LETTRE_DOCX")
    private byte[] lettreDocx;

    @Column(name = "LANCEE_LE", nullable = false)
    private LocalDateTime lanceeLe;

    @Column(name = "LANCEE_PAR", length = 100)
    private String lanceePar;

    @Column(name = "PRIX_MOYEN", precision = 18, scale = 2)
    private BigDecimal prixMoyen;

    @Column(name = "ID_OFFRE_PROPOSEE", length = 36)
    private String idOffreProposee;

    @Column(name = "OBSERVATIONS")
    private String observations;

    @Column(name = "EVALUEE_LE")
    private LocalDateTime evalueeLe;

    @Column(name = "EVALUEE_PAR", length = 100)
    private String evalueePar;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "RAPPORT_PDF")
    private byte[] rapportPdf;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "RAPPORT_DOCX")
    private byte[] rapportDocx;

    @Column(name = "ID_OFFRE_RETENUE", length = 36)
    private String idOffreRetenue;

    @Column(name = "MONTANT_RETENU", precision = 18, scale = 2)
    private BigDecimal montantRetenu;

    @Column(name = "MOTIF_DECISION")
    private String motifDecision;

    @Column(name = "DECIDEE_LE")
    private LocalDateTime decideeLe;

    @Column(name = "DECIDEE_PAR", length = 100)
    private String decideePar;

    /**
     * ⚠️ V151 (2026-10-30, « contrat-cadre-4b », §B3 ; décret n° 2019-1064, art. 31) — la remise commune à tous les lots (rythme selon le
     * calendrier) : le même groupe pour la remise de chaque lot (l'identifiant de la première) ; nul pour une remise d'un seul lot.
     */
    @Column(name = "ID_GROUPE")
    private Long idGroupe;
}
