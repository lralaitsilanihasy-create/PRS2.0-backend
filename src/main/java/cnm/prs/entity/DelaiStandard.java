package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entite JPA mappee sur {@code tr_delai_standard} — referentiel ADMINISTRABLE des delais par etape.
 *
 * <p>Il fournit la prevision des etapes pas encore prises en charge, ce qui permet d'annoncer une date
 * a la PRMP des la soumission, avant que quiconque a la CNM ait touche le dossier. Chaque prise en
 * charge le remplace, pour son etape, par la prevision reellement saisie.</p>
 */
@Entity
@Table(name = "tr_delai_standard")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DelaiStandard {

    /** Valeur de {@code cnm.prs.enums.EtapeCircuit} (PK texte). */
    @Id
    @Column(name = "ETAPE", nullable = false, length = 30)
    private String etape;

    @Column(name = "DELAI_HEURES", nullable = false)
    private Integer delaiHeures;

    @Column(name = "LIBELLE", length = 100)
    private String libelle;
}
