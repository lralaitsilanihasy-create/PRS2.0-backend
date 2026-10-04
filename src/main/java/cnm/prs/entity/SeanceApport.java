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

/** ⚠️ V69 (lot 4, §B2) — un apport de parts en séance : qui ({@code K…} ou {@code SECOURS}), combien, quand. Jamais les parts. */
@Entity
@Table(name = "t_seance_apport")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SeanceApport {

    public static final String SECOURS = "SECOURS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "DETENTEUR", nullable = false, length = 10)
    private String detenteur;

    @Column(name = "NOMBRE", nullable = false)
    private Integer nombre;

    @Column(name = "DATE", nullable = false)
    private LocalDateTime date;
}
