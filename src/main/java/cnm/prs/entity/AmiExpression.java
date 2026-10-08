package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V82 (AMI en ligne, §B2) — une expression d'intérêt déposée par un candidat : en clair, sans scellement, mais illisible de
 * l'administration avant la date limite (arbitrage Q2). {@code CONTENU} = le JSON de l'expression (lettre, références, qualifications,
 * groupement) ; {@code EMPREINTE} = le SHA-256 de ce contenu et des pièces, porté par l'accusé de dépôt. Une seule expression
 * {@code DEPOSEE} par candidat ; un nouveau dépôt remplace la précédente ({@code REMPLACEE}).
 */
@Entity
@Table(name = "t_ami_expression")
@Getter
@Setter
@NoArgsConstructor
public class AmiExpression {

    public static final String DEPOSEE = "DEPOSEE";
    public static final String REMPLACEE = "REMPLACEE";
    public static final String RETIREE = "RETIREE";

    @Id
    @Column(name = "ID", nullable = false, length = 36)
    private String id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ID_CANDIDAT", nullable = false, length = 10)
    private String idCandidat;

    @Column(name = "NIF", length = 50)
    private String nif;

    @Column(name = "RAISON_SOCIALE", length = 255)
    private String raisonSociale;

    @Column(name = "NUMERO")
    private Integer numero;

    @Column(name = "ETAT", nullable = false, length = 20)
    private String etat;

    @Column(name = "CONTENU", nullable = false)
    private String contenu;

    @Column(name = "EMPREINTE", nullable = false, length = 64)
    private String empreinte;

    @Column(name = "DEPOSEE_LE", nullable = false)
    private LocalDateTime deposeeLe;

    @Column(name = "RETIREE_LE")
    private LocalDateTime retireeLe;

    @Column(name = "REMPLACEE_PAR", length = 36)
    private String remplaceePar;

    // ⚠️ V83 (tranche AMI-b) — l'écartement motivé par la commission (irrecevable, hors sujet…).

    @Column(name = "MOTIF_ECARTEMENT")
    private String motifEcartement;

    @Column(name = "ECARTEE_LE")
    private LocalDateTime ecarteeLe;

    @Column(name = "ECARTEE_PAR", length = 100)
    private String ecarteePar;
}
