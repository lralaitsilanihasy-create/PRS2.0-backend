package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ⚠️ V76 (évaluation des offres, lot 1, §B1) — l'évaluation d'une procédure, ouverte une fois le PV d'ouverture signé. */
@Entity
@Table(name = "t_evaluation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Evaluation {

    public static final String EN_COURS = "EN_COURS";
    public static final String RAPPORT_A_SIGNER = "RAPPORT_A_SIGNER";
    public static final String CLOSE = "CLOSE";

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ETAT", nullable = false, length = 20)
    private String etat;

    @Column(name = "OUVERTE_LE", nullable = false)
    private LocalDateTime ouverteLe;

    @Column(name = "OUVERTE_PAR", length = 100)
    private String ouvertePar;
}
