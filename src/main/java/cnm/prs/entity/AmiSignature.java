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

/** ⚠️ V83 (AMI en ligne, tranche AMI-b) — la signature du rapport de présélection par un membre, ou son empêchement constaté. */
@Entity
@Table(name = "t_ami_signature")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AmiSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "IM", nullable = false, length = 20)
    private String im;

    @Column(name = "DATE", nullable = false)
    private LocalDateTime date;

    @Column(name = "EMPECHEMENT", nullable = false)
    private Boolean empechement;

    @Column(name = "MOTIF")
    private String motif;

    @Column(name = "CONSTATE_PAR", length = 100)
    private String constatePar;

    @Column(name = "OBSERVATION")
    private String observation;
}
