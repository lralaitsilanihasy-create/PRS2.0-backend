package cnm.prs.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V82 (AMI en ligne, tranche AMI-a ; art. 32 et 42-II de la loi n° 2016-055) — l'appel à manifestation d'intérêt d'une procédure de
 * prestations intellectuelles : en préparation ({@code BROUILLON}), publié ({@code PUBLIE}, figé), ou dispensé de publicité
 * ({@code DISPENSE} : la liste restreinte se saisit alors comme avant). Critères, pièces et supports de publication en JSON.
 */
@Entity
@Table(name = "t_ami")
@Getter
@Setter
@NoArgsConstructor
public class Ami {

    public static final String BROUILLON = "BROUILLON";
    public static final String PUBLIE = "PUBLIE";
    public static final String DISPENSE = "DISPENSE";

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ETAT", nullable = false, length = 20)
    private String etat;

    @Column(name = "OBJET")
    private String objet;

    @Column(name = "DATE_LIMITE")
    private LocalDateTime dateLimite;

    @Column(name = "CRITERES")
    private String criteres;

    @Column(name = "PIECES")
    private String pieces;

    @Column(name = "NOTE_MINIMALE", precision = 5, scale = 2)
    private BigDecimal noteMinimale;

    @Column(name = "NOMBRE_RETENUS", nullable = false)
    private Integer nombreRetenus = 6;

    @Column(name = "MOTIF_DISPENSE")
    private String motifDispense;

    @Column(name = "PUBLICATIONS")
    private String publications;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "AVIS_PDF")
    private byte[] avisPdf;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "AVIS_DOCX")
    private byte[] avisDocx;

    @Column(name = "AVIS_PRODUIT_LE")
    private LocalDateTime avisProduitLe;

    @Column(name = "PUBLIE_LE")
    private LocalDateTime publieLe;

    @Column(name = "PUBLIE_PAR", length = 100)
    private String publiePar;

    @Column(name = "CREE_LE", nullable = false)
    private LocalDateTime creeLe;

    @Column(name = "CREE_PAR", length = 100)
    private String creePar;

    @Column(name = "MODIFIE_LE")
    private LocalDateTime modifieLe;
}
