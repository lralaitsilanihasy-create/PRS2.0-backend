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
 * Entité JPA mappée sur la table référentielle {@code tr_sous_type_dossier} (⚠️ règle ajoutée —
 * hiérarchie famille → sous-type). Chaque sous-type (ex. {@code PPM}, {@code PPM-AGPM}, {@code DAO},
 * {@code DAOR}, {@code MAOO}, {@code MAOR}…) est rattaché à une <strong>famille</strong> de
 * {@code tr_type_dossier} ({@code DDP} / {@code DMC} / {@code DDM}). Liste ouverte : référentiel
 * administrable (écritures ADMINISTRATEUR). PK assignée (code stable, comme les autres référentiels).
 */
@Entity
@Table(name = "tr_sous_type_dossier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SousTypeDossier {

    @Id
    @Column(name = "ID_SOUS_TYPE", nullable = false, length = 20)
    private String idSousType;

    @Column(name = "LIBELLE_SOUS_TYPE", length = 150)
    private String libelleSousType;

    /** Famille de rattachement (FK {@code tr_type_dossier.ID_TYPE_DOSSIER} : DDP / DMC / DDM). */
    @Column(name = "ID_TYPE_DOSSIER", nullable = false, length = 10)
    private String idTypeDossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_TYPE_DOSSIER", insertable = false, updatable = false)
    @JsonIgnore
    private TypeDossier typeDossier;
    /** ⚠️ V96 (manuel de contrôle, M3) — le sous-type dont la grille s'ajoute à celle-ci (DAOR = DAOO + ses points). */
    @Column(name = "GRILLE_BASE", length = 20)
    private String grilleBase;

    /** ⚠️ V96 (M3) — vrai : les points communs de la famille ne s'appliquent pas (MPI, MGG, DC, AVN…). */
    @Column(name = "GRILLE_PROPRE", nullable = false)
    private Boolean grillePropre = false;

    public SousTypeDossier(String idSousType, String libelleSousType, String idTypeDossier) {
        this.idSousType = idSousType;
        this.libelleSousType = libelleSousType;
        this.idTypeDossier = idTypeDossier;
    }
}
