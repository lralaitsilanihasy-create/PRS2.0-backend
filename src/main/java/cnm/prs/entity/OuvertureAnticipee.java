package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ⚠️ V160 (demande front « appel-offres-restreint », §B7) — la demande d'ouverture anticipée des plis d'une procédure. */
@Entity
@Table(name = "t_ouverture_anticipee")
@Getter
@Setter
@NoArgsConstructor
public class OuvertureAnticipee {

    public static final String DEMANDEE = "DEMANDEE";
    public static final String ACCORDEE = "ACCORDEE";
    public static final String FIXEE = "FIXEE";
    public static final String REFUSEE = "REFUSEE";

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ETAT", nullable = false, length = 10)
    private String etat;

    @Column(name = "OUVERTURE_INITIALE")
    private LocalDateTime ouvertureInitiale;

    @Column(name = "NOUVELLE_OUVERTURE")
    private LocalDateTime nouvelleOuverture;

    @Column(name = "DEMANDEE_LE", nullable = false)
    private LocalDateTime demandeeLe;

    @Column(name = "DEMANDEE_PAR", length = 100)
    private String demandeePar;

    @Column(name = "FIXEE_LE")
    private LocalDateTime fixeeLe;

    @Column(name = "FIXEE_PAR", length = 100)
    private String fixeePar;

    /** Une demande qui fige les offres : en cours ou aboutie (pas refusée). */
    public boolean enCours() {
        return !REFUSEE.equals(etat);
    }
}
