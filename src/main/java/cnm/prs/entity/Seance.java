package cnm.prs.entity;

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
 * ⚠️ V69 (demande front du 2026-10-04, soumission en ligne, lot 4 ; ADR-0013) — la <strong>séance d'ouverture des plis</strong> d'une
 * procédure : {@code OUVERTE} (les parts arrivent), {@code DECHIFFREE} (toutes les offres sont ouvertes), {@code ILLISIBLE} (S5),
 * {@code CLOSE} (PV produit) ; {@code A_VENIR} tant qu'elle n'a pas de ligne.
 */
@Entity
@Table(name = "t_seance")
@Getter
@Setter
@NoArgsConstructor
public class Seance {

    public static final String A_VENIR = "A_VENIR";
    public static final String OUVERTE = "OUVERTE";
    public static final String DECHIFFREE = "DECHIFFREE";
    /** ⚠️ V70 (§B2) — le PV est produit, les membres présents le signent. */
    public static final String PV_A_SIGNER = "PV_A_SIGNER";
    public static final String ILLISIBLE = "ILLISIBLE";
    public static final String CLOSE = "CLOSE";

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ETAT", nullable = false, length = 12)
    private String etat;

    @Column(name = "OUVERTE_LE", nullable = false)
    private LocalDateTime ouverteLe;

    @Column(name = "DECHIFFREE_LE")
    private LocalDateTime dechiffreeLe;

    @Column(name = "CLOSE_LE")
    private LocalDateTime closeLe;

    /** Les membres présents (identifiants courts {@code K…}), séparés par des virgules. */
    @Column(name = "PRESENTS", length = 1000)
    private String presents;

    /** Les autres présents, en JSON : {@code [{ nom, qualite }]}. */
    @Column(name = "AUTRES")
    private String autres;

    @Column(name = "SECOURS_EMPLOYE", nullable = false)
    private Boolean secoursEmploye = Boolean.FALSE;

    @Column(name = "SECOURS_MOTIF")
    private String secoursMotif;

    @Column(name = "MOTIF_ILLISIBLE")
    private String motifIllisible;

    @Column(name = "OBSERVATIONS")
    private String observations;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PV")
    private byte[] pv;

    /** Le PV tel qu'il se publie ({@code B04-OP-13 = OUI}) : sans les alertes de rapprochement ni la vérification des NIF. */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PV_PUBLIC")
    private byte[] pvPublic;

    @Column(name = "PV_PUBLIE", nullable = false)
    private Boolean pvPublie = Boolean.FALSE;

    /** ⚠️ V70 (§B2) — les membres présents appelés à signer le PV ({@code K…}), figés à sa production. */
    @Column(name = "SIGNATAIRES", length = 1000)
    private String signataires;

    /** ⚠️ V70 (§B2) — le PV entièrement signé (ou les empêchements constatés). */
    @Column(name = "PV_SIGNE_LE")
    private LocalDateTime pvSigneLe;
}
