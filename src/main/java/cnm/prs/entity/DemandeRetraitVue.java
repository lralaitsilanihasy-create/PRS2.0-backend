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
 * Entité JPA mappée sur {@code t_demande_retrait_vue} : dernière consultation de l'écran « Demandes de
 * retrait » par une PRMP (une seule ligne par PRMP, mise à jour à chaque ouverture). Sert à compter les
 * demandes passées à {@code ACCEPTEE}/{@code REFUSEE} depuis la dernière consultation.
 */
@Entity
@Table(name = "t_demande_retrait_vue",
        uniqueConstraints = @UniqueConstraint(name = "uk_demande_retrait_vue_prmp", columnNames = {"ID_PRMP"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DemandeRetraitVue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_VUE", nullable = false)
    private Integer idVue;

    @Column(name = "ID_PRMP", nullable = false, length = 10)
    private String idPrmp;

    @Column(name = "DATE_DERNIERE_VUE", nullable = false)
    private LocalDateTime dateDerniereVue;
}
