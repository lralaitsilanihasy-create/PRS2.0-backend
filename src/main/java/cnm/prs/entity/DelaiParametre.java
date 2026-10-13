package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ R6a (manuel révisé, lot 2, §B1 ; V106, 2026-10-13) — un délai de publicité <strong>administrable</strong> (hors des neuf délais
 * de la loi, qui sont en code : {@code DelaisLegaux}) : signalé, jamais bloquant.
 */
@Entity
@Table(name = "t_delai_parametre")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DelaiParametre {

    public static final String JOURS = "JOURS";
    public static final String JOURS_FRANCS = "JOURS_FRANCS";
    public static final String JOURS_OUVRABLES = "JOURS_OUVRABLES";
    public static final String MOIS = "MOIS";

    @Id
    @Column(name = "CODE", length = 40)
    private String code;

    @Column(name = "VALEUR", nullable = false)
    private Integer valeur;

    @Column(name = "UNITE", nullable = false, length = 20)
    private String unite;

    @Column(name = "TEXTE", length = 300)
    private String texte;

    @Column(name = "ACTIF", nullable = false)
    private boolean actif;

    @Column(name = "DATE_MAJ")
    private LocalDateTime dateMaj;

    @Column(name = "IM_ACTEUR", length = 100)
    private String imActeur;
}
