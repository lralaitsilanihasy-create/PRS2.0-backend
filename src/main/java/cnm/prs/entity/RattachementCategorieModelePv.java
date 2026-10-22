package cnm.prs.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ 2026-10-22 (demande front « modeles-documents-marche », lot M1 ; V140) — le rattachement d'une <strong>catégorie</strong> de
 * procédure (fournitures et services, travaux, prestations intellectuelles) à un modèle de projet de marché : un modèle au plus par
 * catégorie (arbitrage Q1 du pilote). Comme pour les sous-types, le rattachement courant fait foi ; son historique est au journal.
 */
@Entity
@Table(name = "t_rattachement_categorie_modele_pv")
@IdClass(RattachementCategorieModelePv.Cle.class)
@Getter
@Setter
@NoArgsConstructor
public class RattachementCategorieModelePv {

    /** Clé : (catégorie, nature). */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Cle implements Serializable {
        private static final long serialVersionUID = 1L;
        private String categorie;
        private String nature;
    }

    @Id
    @Column(name = "CATEGORIE", nullable = false, length = 40)
    private String categorie;

    @Id
    @Column(name = "NATURE", nullable = false, length = 20)
    private String nature;

    @Column(name = "ID_MODELE", nullable = false)
    private Long idModele;

    @Column(name = "RATTACHE_LE", nullable = false)
    private LocalDateTime rattacheLe;

    @Column(name = "RATTACHE_PAR")
    private String rattachePar;
}
