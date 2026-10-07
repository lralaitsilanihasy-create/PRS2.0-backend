package cnm.prs.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V79 (évaluation des offres, lot 2, §B1-§B2) — l'attribution d'un lot d'une procédure, après le rapport d'évaluation signé : son
 * état, l'offre proposée, le dossier de marché (famille {@code DDM}) soumis au contrôle de la Commission et le projet de marché
 * produit par le serveur (arbitrage Q1 du pilote).
 */
@Entity
@Table(name = "t_attribution")
@IdClass(Attribution.Cle.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Attribution {

    public static final String AU_CONTROLE = "AU_CONTROLE";
    public static final String ATTRIBUE = "ATTRIBUE";
    public static final String INFORME = "INFORME";
    public static final String SIGNE = "SIGNE";
    public static final String NOTIFIE = "NOTIFIE";
    public static final String PUBLIE = "PUBLIE";
    public static final String RETIRE = "RETIRE";

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Id
    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Column(name = "ETAT", nullable = false, length = 20)
    private String etat;

    @Column(name = "ID_OFFRE_PROPOSEE", length = 36)
    private String idOffreProposee;

    @Column(name = "ID_DOSSIER")
    private Integer idDossier;

    @Column(name = "DOSSIER_CREE_LE")
    private LocalDateTime dossierCreeLe;

    @Column(name = "DOSSIER_CREE_PAR", length = 100)
    private String dossierCreePar;

    // ⚠️ V80 (tranche 2b, §B3, §B4.1) — le choix de l'attributaire et l'information des candidats.

    @Column(name = "ID_OFFRE_ATTRIBUEE", length = 36)
    private String idOffreAttribuee;

    @Column(name = "ATTRIBUE_LE")
    private LocalDateTime attribueLe;

    @Column(name = "ATTRIBUE_PAR", length = 100)
    private String attribuePar;

    @Column(name = "MOTIF_ATTRIBUTION")
    private String motifAttribution;

    /** Le montant hors taxes du marché (prix corrigé − rabais), le TTC lu et le délai de l'offre attribuée, figés au choix. */
    @Column(name = "MONTANT", precision = 18, scale = 2)
    private java.math.BigDecimal montant;

    @Column(name = "MONTANT_TTC", precision = 18, scale = 2)
    private java.math.BigDecimal montantTtc;

    @Column(name = "DELAI", length = 50)
    private String delai;

    @Column(name = "INFORME_LE")
    private LocalDateTime informeLe;

    @Column(name = "INFORME_PAR", length = 100)
    private String informePar;

    /** Le nom de la PRMP qui a signé les lettres (signature électronique simple, Q9). */
    @Column(name = "SIGNATAIRE", length = 200)
    private String signataire;

    @Column(name = "DATE_AFFICHAGE")
    private java.time.LocalDate dateAffichage;

    // ⚠️ V81 (tranche 2c, §B4.3, §B4.4, §B5) — mise au point, signature, enregistrement, notification, avis d'attribution, retrait.

    @Column(name = "RAPPORT_MISE_AU_POINT")
    private String rapportMiseAuPoint;

    @Column(name = "MISE_AU_POINT_LE")
    private LocalDateTime miseAuPointLe;

    @Column(name = "MISE_AU_POINT_PAR", length = 100)
    private String miseAuPointPar;

    @Column(name = "DATE_SIGNATURE")
    private java.time.LocalDate dateSignature;

    @Column(name = "SIGNE_LE")
    private LocalDateTime signeLe;

    @Column(name = "SIGNE_PAR", length = 100)
    private String signePar;

    @Column(name = "DATE_ENREGISTREMENT")
    private java.time.LocalDate dateEnregistrement;

    @Column(name = "REFERENCE_ENREGISTREMENT", length = 100)
    private String referenceEnregistrement;

    @Column(name = "ENREGISTRE_LE")
    private LocalDateTime enregistreLe;

    @Column(name = "DATE_NOTIFICATION")
    private java.time.LocalDate dateNotification;

    @Column(name = "NOTIFIE_LE")
    private LocalDateTime notifieLe;

    @Column(name = "NOTIFIE_PAR", length = 100)
    private String notifiePar;

    /** La réception de la notification par l'attributaire : la date d'effet du marché (art. 54). */
    @Column(name = "NOTIFICATION_RECUE_LE")
    private LocalDateTime notificationRecueLe;

    /** Vrai : réception déclarée par la PRMP ; faux : accusé de lecture de la plateforme. */
    @Column(name = "RECEPTION_DECLAREE")
    private Boolean receptionDeclaree;

    @Column(name = "DATE_PUBLICATION_AVIS")
    private java.time.LocalDate datePublicationAvis;

    @Column(name = "AVIS_PUBLIE_LE")
    private LocalDateTime avisPublieLe;

    @Column(name = "AVIS_PUBLIE_PAR", length = 100)
    private String avisPubliePar;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "AVIS_PDF")
    private byte[] avisPdf;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "AVIS_DOCX")
    private byte[] avisDocx;

    @Column(name = "RETIRE_LE")
    private LocalDateTime retireLe;

    @Column(name = "RETIRE_PAR", length = 100)
    private String retirePar;

    @Column(name = "MOTIF_RETRAIT")
    private String motifRetrait;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PROJET_PDF")
    private byte[] projetPdf;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PROJET_DOCX")
    private byte[] projetDocx;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Cle implements Serializable {
        private Long idDmc;
        private Integer lot;
    }
}
