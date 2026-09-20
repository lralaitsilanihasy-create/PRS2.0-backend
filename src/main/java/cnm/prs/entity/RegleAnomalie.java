package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_regle_anomalie}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_regle_anomalie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegleAnomalie {

    @Id
    @Column(name = "ID_REGLE_ANOMALIE", nullable = false)
    private Integer idRegleAnomalie;

    @Column(name = "CODE_REGLE", nullable = false, length = 30)
    private String codeRegle;

    @Column(name = "LIBELLE", length = 200)
    private String libelle;

    @Column(name = "PARAMETRE_NUM")
    private BigDecimal parametreNum;

    @Column(name = "PARAMETRE_TXT", length = 200)
    private String parametreTxt;

    @Column(name = "ACTIF")
    private Boolean actif;

    /**
     * ⚠️ Élargie de 10 à 20 caractères par V32 (pré-contrôle du PPM, lot 3) : la gravité
     * {@code PRIORITAIRE} de {@link cnm.prs.enums.GraviteSignalement} en fait 11.
     */
    @Column(name = "GRAVITE_DEFAUT", length = 20)
    private String graviteDefaut;
}
