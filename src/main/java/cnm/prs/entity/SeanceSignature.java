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

/**
 * ⚠️ V70 (arbitrages du pilote après le lot 4, §B2) — la <strong>signature électronique simple</strong> du PV d'ouverture par un membre
 * présent de la CAO (l'acte authentifié du membre connecté, horodaté), ou son <strong>empêchement</strong> constaté par le président,
 * avec un motif porté au PV.
 */
@Entity
@Table(name = "t_seance_signature")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SeanceSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "IM", nullable = false, length = 10)
    private String im;

    @Column(name = "DATE", nullable = false)
    private LocalDateTime date;

    @Column(name = "EMPECHEMENT", nullable = false)
    private Boolean empechement = Boolean.FALSE;

    @Column(name = "MOTIF")
    private String motif;

    @Column(name = "CONSTATE_PAR", length = 100)
    private String constatePar;
}
