package cnm.prs.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V124 (lot MS-1) — la décision écrite de l'autorité contractante de reconduire ou non un contrat-cadre (AE-CC, art. 7.3 : « le
 * titulaire ne peut refuser la reconduction ») : sa référence et sa date, la fin d'avant et, reconduit, la nouvelle fin ; prise moins de
 * {@code preavisMois} avant la fin, elle est marquée {@code horsPreavis}.
 */
@Entity
@Table(name = "t_contrat_reconduction")
@Getter
@Setter
@NoArgsConstructor
public class ContratReconduction {

    public static final String RECONDUIRE = "RECONDUIRE";
    public static final String NE_PAS_RECONDUIRE = "NE_PAS_RECONDUIRE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_CONTRAT", nullable = false)
    private Long idContrat;

    @Column(name = "DECISION", nullable = false, length = 20)
    private String decision;

    @Column(name = "REFERENCE", nullable = false, length = 100)
    private String reference;

    @Column(name = "DATE_DECISION", nullable = false)
    private LocalDate dateDecision;

    @Column(name = "FIN_AVANT", nullable = false)
    private LocalDate finAvant;

    @Column(name = "NOUVELLE_FIN")
    private LocalDate nouvelleFin;

    @Column(name = "HORS_PREAVIS", nullable = false)
    private boolean horsPreavis;

    @Column(name = "LE", nullable = false)
    private LocalDateTime le;

    @Column(name = "PAR", length = 100)
    private String par;
}
