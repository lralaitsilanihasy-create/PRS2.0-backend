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
 * ⚠️ V88 (lot 3 PI, tranche PI-d1, §B3) — la seconde séance d'ouverture d'une consultation de prestations intellectuelles : les
 * enveloppes financières à ouvrir ({@code A_OUVRIR}, identifiants séparés par des virgules, fixés à l'ouverture), son état, ses
 * présents, son PV. ⚠️ V89 (PI-d2a) : une ligne par ronde — 1 = la seconde séance, 2… = les séances complémentaires (ouverture de la
 * financière du suivant après l'échec d'une négociation), avec leur motif.
 */
@Entity
@Table(name = "t_seance_financiere")
@IdClass(SeanceFinanciere.Cle.class)
@Getter
@Setter
@NoArgsConstructor
public class SeanceFinanciere {

    public static final String OUVERTE = "OUVERTE";
    public static final String DECHIFFREE = "DECHIFFREE";
    public static final String CLOSE = "CLOSE";

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Id
    @Column(name = "RONDE", nullable = false)
    private Integer ronde;

    @Column(name = "MOTIF")
    private String motif;

    @Column(name = "ETAT", nullable = false, length = 12)
    private String etat;

    @Column(name = "METHODE", length = 200)
    private String methode;

    @Column(name = "A_OUVRIR", nullable = false)
    private String aOuvrir;

    @Column(name = "OUVERTE_LE", nullable = false)
    private LocalDateTime ouverteLe;

    @Column(name = "OUVERTE_PAR", length = 100)
    private String ouvertePar;

    @Column(name = "DECHIFFREE_LE")
    private LocalDateTime dechiffreeLe;

    @Column(name = "PRESENTS", length = 1000)
    private String presents;

    @Column(name = "AUTRES")
    private String autres;

    @Column(name = "SECOURS_EMPLOYE", nullable = false)
    private Boolean secoursEmploye = false;

    @Column(name = "SECOURS_MOTIF")
    private String secoursMotif;

    @Column(name = "OBSERVATIONS")
    private String observations;

    @Column(name = "CLOSE_LE")
    private LocalDateTime closeLe;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PV")
    private byte[] pv;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "PV_DOCX")
    private byte[] pvDocx;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Cle implements Serializable {
        private Long idDmc;
        private Integer ronde;
    }
}
