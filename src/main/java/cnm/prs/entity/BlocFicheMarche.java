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
 * Bloc de la fiche marché d'un appel d'offres ({@code tr_bloc_fiche_marche}, V35) — B01 identification et données
 * du PPM … B10 modifications, résiliation, litiges ; B07 réservé au contrat-cadre. Figé par migration.
 */
@Entity
@Table(name = "tr_bloc_fiche_marche")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BlocFicheMarche {

    @Id
    @Column(name = "CODE", nullable = false, length = 3)
    private String code;

    @Column(name = "LIBELLE", nullable = false, length = 150)
    private String libelle;

    @Column(name = "RANG", nullable = false)
    private Integer rang;

    /** Types de marché où le bloc existe, séparés par des virgules ({@link cnm.prs.enums.TypeMarcheDao}). */
    @Column(name = "TYPES_MARCHE", nullable = false, length = 60)
    private String typesMarche;

    /** ⚠️ Lot 5 (2026-09-24, V40) — catégories de fiche DAO où le bloc existe ({@link cnm.prs.enums.CategorieDao}). */
    @Column(name = "CATEGORIES", nullable = false, length = 80)
    private String categories = cnm.prs.enums.CategorieDao.toutes();
}
