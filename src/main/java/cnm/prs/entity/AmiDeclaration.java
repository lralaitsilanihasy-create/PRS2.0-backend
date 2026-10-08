package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ⚠️ V83 (AMI en ligne, tranche AMI-b) — la déclaration préalable d'un membre de la commission sur l'AMI (conflit d'intérêts, confidentialité). */
@Entity
@Table(name = "t_ami_declaration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AmiDeclaration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "IM", nullable = false, length = 20)
    private String im;

    @Column(name = "SIGNEE_LE", nullable = false)
    private LocalDateTime signeeLe;

    @Column(name = "CONFLIT", nullable = false)
    private Boolean conflit;

    @Column(name = "PRECISION")
    private String precision;
}
