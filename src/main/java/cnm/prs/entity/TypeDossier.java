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
 * Entité JPA mappée sur la table {@code tr_type_dossier}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "tr_type_dossier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TypeDossier {

    @Id
    @Column(name = "ID_TYPE_DOSSIER", nullable = false, length = 10)
    private String idTypeDossier;

    @Column(name = "LIBELLE_TYPE", length = 100)
    private String libelleType;
}
