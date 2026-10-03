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
 * ⚠️ V60 (demande front du 2026-10-03, matériel et personnel des travaux, §B1.3) — un <strong>poste clé exigé</strong>
 * d'une version de fiche DAO de travaux ({@code t_fiche_personnel}) : poste, nombre, diplôme, expérience (années et
 * domaine), justificatifs, et {@code parLot} (chaque lot mobilise son équipe).
 */
@Entity
@Table(name = "t_fiche_personnel")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FichePersonnel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PERSONNEL", nullable = false)
    private Integer idPersonnel;

    @Column(name = "ID_FICHE", nullable = false)
    private Integer idFiche;

    @Column(name = "ORDRE", nullable = false)
    private Integer ordre;

    @Column(name = "POSTE", nullable = false, length = 200)
    private String poste;

    @Column(name = "NOMBRE", nullable = false)
    private Integer nombre;

    @Column(name = "DIPLOME", length = 500)
    private String diplome;

    @Column(name = "EXPERIENCE_ANNEES")
    private Integer experienceAnnees;

    @Column(name = "DOMAINE_EXPERIENCE", length = 200)
    private String domaineExperience;

    @Column(name = "JUSTIFICATIFS", length = 500)
    private String justificatifs;

    @Column(name = "PAR_LOT", nullable = false)
    private boolean parLot;
}
