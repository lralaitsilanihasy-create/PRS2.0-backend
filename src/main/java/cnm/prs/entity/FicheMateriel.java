package cnm.prs.entity;

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
 * ⚠️ V60 (demande front du 2026-10-03, matériel et personnel des travaux, §B1.2) — un <strong>engin exigé</strong> d'une
 * version de fiche DAO de travaux ({@code t_fiche_materiel}) : désignation, caractéristique (« ≥ 350 l »), nombre, minimum
 * en propre ({@code null} : propriété ou location indifférente), et {@code parLot} (le nombre vaut pour chaque lot).
 */
@Entity
@Table(name = "t_fiche_materiel")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FicheMateriel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_MATERIEL", nullable = false)
    private Integer idMateriel;

    @Column(name = "ID_FICHE", nullable = false)
    private Integer idFiche;

    @Column(name = "ORDRE", nullable = false)
    private Integer ordre;

    @Column(name = "DESIGNATION", nullable = false, length = 200)
    private String designation;

    @Column(name = "CARACTERISTIQUE", length = 200)
    private String caracteristique;

    @Column(name = "NOMBRE", nullable = false)
    private Integer nombre;

    @Column(name = "MINIMUM_EN_PROPRE")
    private Integer minimumEnPropre;

    @Column(name = "PAR_LOT", nullable = false)
    private boolean parLot;
}
