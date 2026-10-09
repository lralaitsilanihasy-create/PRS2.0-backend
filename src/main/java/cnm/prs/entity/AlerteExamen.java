package cnm.prs.entity;

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
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M5b, §B6 ; V99) — l'alerte de dépassement d'un passage en examen : une seule par
 * passage, repéré par le dossier et l'entrée dans l'étape.
 */
@Entity
@Table(name = "t_alerte_examen")
@Getter
@Setter
@NoArgsConstructor
public class AlerteExamen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ALERTE", nullable = false)
    private Integer idAlerte;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "ENTREE", nullable = false)
    private LocalDateTime entree;

    @Column(name = "ECOULE_HEURES", nullable = false)
    private Integer ecouleHeures;

    @Column(name = "ALERTE_LE", nullable = false)
    private LocalDateTime alerteLe;
}
