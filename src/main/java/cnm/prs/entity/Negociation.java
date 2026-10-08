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
 * ⚠️ V89 (lot 3 PI, tranche PI-d2a, §B6 ; art. 42-IV) — une négociation avec un candidat classé : conduite par la PRMP (ou son UGPM),
 * une seule en cours et une seule réussie par lot ; son procès-verbal (texte, date, lieu, pièce jointe, PDF et Word) ; l'échec, motivé,
 * ouvre la voie au suivant (Q7, en attente du juriste).
 */
@Entity
@Table(name = "t_negociation")
@Getter
@Setter
@NoArgsConstructor
public class Negociation {

    public static final String EN_COURS = "EN_COURS";
    public static final String REUSSIE = "REUSSIE";
    public static final String ECHOUEE = "ECHOUEE";
    /** ⚠️ V92 (2d-2) — la négociation réussie dont le marché a été retiré faute de pièces : le suivant est invité. */
    public static final String RETIREE = "RETIREE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "LOT", nullable = false)
    private Integer lot;

    /** La proposition (son enveloppe technique). */
    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "ID_FINANCIERE", length = 36)
    private String idFinanciere;

    @Column(name = "NUMERO")
    private Integer numero;

    @Column(name = "RAISON_SOCIALE", length = 300)
    private String raisonSociale;

    @Column(name = "RANG")
    private Integer rang;

    @Column(name = "ETAT", nullable = false, length = 10)
    private String etat;

    @Column(name = "OUVERTE_LE", nullable = false)
    private LocalDateTime ouverteLe;

    @Column(name = "OUVERTE_PAR", length = 100)
    private String ouvertePar;

    @Column(name = "PREVUE_LE")
    private LocalDateTime prevueLe;

    @Column(name = "LIEU")
    private String lieu;

    @Column(name = "CONCLUE_LE")
    private LocalDateTime conclueLe;

    @Column(name = "CONCLUE_PAR", length = 100)
    private String concluePar;

    @Column(name = "DATE_NEGOCIATION")
    private LocalDate dateNegociation;

    @Column(name = "TEXTE")
    private String texte;

    @Column(name = "MOTIF_ECHEC")
    private String motifEchec;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PIECE")
    private byte[] piece;

    @Column(name = "PIECE_NOM", length = 255)
    private String pieceNom;

    @Column(name = "PIECE_TYPE", length = 100)
    private String pieceType;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PV")
    private byte[] pv;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PV_DOCX")
    private byte[] pvDocx;
}
