package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Paramètre système général (table {@code t_parametre}) — clé/valeur éditable sans redéploiement.
 *
 * <p>Introduit pour l'interrupteur global des actualités ({@code ACTUALITES_ACTIVES}, spec du
 * 2026-08-18) ; conçu pour servir au-delà de ce besoin.</p>
 */
@Entity
@Table(name = "t_parametre")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Parametre {

    @Id
    @Column(name = "CLE", nullable = false, length = 50)
    private String cle;

    @Column(name = "VALEUR", length = 200)
    private String valeur;

    @Column(name = "DATE_MAJ")
    private LocalDateTime dateMaj;

    /** Dernier modificateur (identité JWT). */
    @Column(name = "IM_ACTEUR", length = 10)
    private String imActeur;
}
