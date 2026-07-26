package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ⚠️ Référentiel ajouté (2026-07-26) — <strong>catégorie d'entité contractante</strong>
 * (table {@code tr_categorie_entite}). Source unique du <strong>niveau hiérarchique</strong> : une
 * {@link EntiteContract} porte une {@code categorieEntite} (texte, validé au référentiel) dont le
 * {@code niveauHierarchique} est <strong>dérivé</strong> à l'écriture ({@code POST/PUT /api/entite-contracts}) —
 * l'entité et sa catégorie ne peuvent plus diverger.
 *
 * <p>PK = {@code libelle} (ex. « MINISTERE »), aligné sur {@code tr_entite_contract.CATEGORIE_ENTITE}
 * (longueur 20) pour une éventuelle FK ultérieure.</p>
 */
@Entity
@Table(name = "tr_categorie_entite")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategorieEntite {

    @Id
    @Column(name = "LIBELLE", nullable = false, length = 20)
    private String libelle;

    @Column(name = "NIVEAU_HIERARCHIQUE", nullable = false)
    private Integer niveauHierarchique;
}
