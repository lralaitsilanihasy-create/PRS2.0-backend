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
 * Entité JPA mappée sur la table {@code tr_ministere}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "tr_ministere")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Ministere {

    @Id
    @Column(name = "ID_MINISTERE", nullable = false)
    private Integer idMinistere;

    @Column(name = "LIBELLE_MINISTERE", nullable = false, length = 150)
    private String libelleMinistere;

    @Column(name = "SIGLE", length = 20)
    private String sigle;
}
