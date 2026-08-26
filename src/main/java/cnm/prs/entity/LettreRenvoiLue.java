package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur {@code t_lettre_renvoi_lue} : trace de lecture d'une lettre de renvoi par une
 * PRMP (une seule entrée par couple lettre/PRMP). Posée à la consultation du détail d'une lettre SIGNE
 * par la PRMP propriétaire du dossier ; sert à ne compter que les lettres non encore lues.
 */
@Entity
@Table(name = "t_lettre_renvoi_lue",
        uniqueConstraints = @UniqueConstraint(name = "uk_lettre_lue", columnNames = {"ID_LETTRE", "ID_PRMP"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LettreRenvoiLue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_LECTURE", nullable = false)
    private Integer idLecture;

    @Column(name = "ID_LETTRE", nullable = false)
    private Integer idLettre;

    @Column(name = "ID_PRMP", nullable = false, length = 10)
    private String idPrmp;

    @Column(name = "DATE_LECTURE", nullable = false)
    private LocalDateTime dateLecture;
}
