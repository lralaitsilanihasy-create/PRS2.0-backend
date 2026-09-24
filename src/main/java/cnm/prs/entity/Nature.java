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
 * Entité JPA mappée sur la table {@code tr_nature}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "tr_nature")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Nature {

    @Id
    @Column(name = "ID_NATURE", nullable = false)
    private Integer idNature;

    @Column(name = "LIBELLE", length = 100)
    private String libelle;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /**
     * ⚠️ Fiche DAO, lot 5 (2026-09-24, V40) — la catégorie de fiche DAO des lignes de cette nature
     * ({@link cnm.prs.enums.CategorieDao}) ; {@code null} : ses lignes ne se préparent pas. Administrable.
     */
    @Column(name = "CATEGORIE_DAO", length = 30)
    private String categorieDao;

    /** Constructeur historique (identifiant, libellé, description), sans catégorie. */
    public Nature(Integer idNature, String libelle, String description) {
        this(idNature, libelle, description, null);
    }
}
