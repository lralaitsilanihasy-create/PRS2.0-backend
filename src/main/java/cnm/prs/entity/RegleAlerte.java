package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_regle_alerte}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_regle_alerte")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegleAlerte {

    @Id
    @Column(name = "ID_REGLE_ALERTE", nullable = false)
    private Integer idRegleAlerte;

    @Column(name = "TYPE_JALON", nullable = false, length = 30)
    private String typeJalon;

    @Column(name = "JOURS_AVANT", nullable = false)
    private Integer joursAvant;

    @Column(name = "DESTINATAIRE_PROFIL")
    private Integer destinataireProfil;

    @Column(name = "ACTIF")
    private Boolean actif;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DESTINATAIRE_PROFIL", insertable = false, updatable = false)
    @JsonIgnore
    private Profile profile;
}
