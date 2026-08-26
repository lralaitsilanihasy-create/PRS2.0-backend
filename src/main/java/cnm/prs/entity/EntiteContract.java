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
 * Entité JPA mappée sur la table {@code tr_entite_contract}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "tr_entite_contract")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EntiteContract {

    @Id
    @Column(name = "ID_ENTITE_CONTRACT", nullable = false)
    private Integer idEntiteContract;

    @Column(name = "LIBELLE_ENTITE", nullable = false, length = 150)
    private String libelleEntite;

    @Column(name = "ADRESSE", nullable = false, length = 200)
    private String adresse;

    @Column(name = "CATEGORIE_ENTITE", length = 20)
    private String categorieEntite;

    @Column(name = "ID_ORGANIGRAMME", nullable = false)
    private Integer idOrganigramme;

    @Column(name = "ID_ENTITE_PARENT")
    private Integer idEntiteParent;

    @Column(name = "NIVEAU_HIERARCHIQUE")
    private Integer niveauHierarchique;

    /** Localité de l'entité contractante (§1). C'est elle qui fixe la localité d'un dossier la concernant. */
    @Column(name = "ID_LOCALITE", length = 5)
    private String idLocalite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_LOCALITE", insertable = false, updatable = false)
    @JsonIgnore
    private Localite localite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_ORGANIGRAMME", insertable = false, updatable = false)
    @JsonIgnore
    private Organigramme organigramme;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_ENTITE_PARENT", insertable = false, updatable = false)
    @JsonIgnore
    private EntiteContract entiteParent;
}
