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
 * Entité JPA mappée sur la table {@code tr_cat_compte}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "tr_cat_compte")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CatCompte {

    @Id
    @Column(name = "ID_CAT_COMPTE", nullable = false, length = 10)
    private String idCatCompte;

    @Column(name = "CAT_COMPTE", length = 50)
    private String catCompte;
}
