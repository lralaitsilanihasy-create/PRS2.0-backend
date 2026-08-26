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
 * Référentiel <strong>administrable</strong> des types de dossier de mise en concurrence (DMC) :
 * {@code DAO} (Dossier d'Appel d'Offres), {@code DC} (Dossier de Consultation), {@code BC} (Bon de
 * Commande)… Liste ouverte, complétable en administration. Table {@code t_type_dmc}.
 */
@Entity
@Table(name = "t_type_dmc")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TypeDmc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_TYPE_DMC", nullable = false)
    private Long idTypeDmc;

    @Column(name = "CODE", nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "LIBELLE", nullable = false, length = 120)
    private String libelle;

    @Column(name = "ACTIF", nullable = false)
    private boolean actif = true;
}
