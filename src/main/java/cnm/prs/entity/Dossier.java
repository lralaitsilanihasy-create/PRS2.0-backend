package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité JPA mappée sur la table {@code t_dossier}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_dossier", indexes = {
        @Index(name = "idx_dossier_localite", columnList = "ID_LOCALITE"),
        @Index(name = "idx_dossier_prmp", columnList = "ID_PRMP"),
        @Index(name = "idx_dossier_statut", columnList = "STATUT")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Dossier {

    @Id
    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "ID_TYPE_DOSSIER", length = 10)
    private String idTypeDossier;

    @Column(name = "ID_DOSSIER_PARENT")
    private Integer idDossierParent;

    @Column(name = "REFE_DOSSIER", length = 100)
    private String refeDossier;

    @Column(name = "DATE_REF")
    private LocalDate dateRef;

    /** Date et heure de soumission du dossier (TIMESTAMP). Posée à la saisie (POST /api/saisies/ppm). */
    @Column(name = "DATE_SOUMISSION")
    private LocalDateTime dateSoumission;

    @Column(name = "STATUT", length = 30)
    private String statut;

    /** Localité du dossier (§1). Renseignée à la soumission ; rend le dossier visible/réceptionnable
     *  par les contrôleurs de cette localité même sans PPM ni réception. */
    @Column(name = "ID_LOCALITE", length = 5)
    private String idLocalite;

    /** PRMP propriétaire du dossier (§3.1). Posée à la saisie ; le périmètre de propriété. */
    @Column(name = "ID_PRMP", length = 10)
    private String idPrmp;

    /** Traçabilité (règle ajoutée) — login de l'acteur ayant <strong>créé</strong> le dossier (PRMP ou UGPM). */
    @Column(name = "CREE_PAR", length = 100)
    private String creePar;

    /** Traçabilité (règle ajoutée) — login de l'acteur ayant <strong>soumis</strong> le dossier (PRMP uniquement). */
    @Column(name = "SOUMIS_PAR", length = 100)
    private String soumisPar;

    /** Entité contractante concernée par le dossier (§1) — choisie à la saisie parmi les entités de la
     *  PRMP ; c'est elle qui détermine la localité ({@code idLocalite} en est dérivé). */
    @Column(name = "ID_ENTITE_CONTRACT")
    private Integer idEntiteContract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_TYPE_DOSSIER", insertable = false, updatable = false)
    @JsonIgnore
    private TypeDossier typeDossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_LOCALITE", insertable = false, updatable = false)
    @JsonIgnore
    private Localite localite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DOSSIER_PARENT", insertable = false, updatable = false)
    @JsonIgnore
    private Dossier dossierParent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PRMP", insertable = false, updatable = false)
    @JsonIgnore
    private Prmp prmp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_ENTITE_CONTRACT", insertable = false, updatable = false)
    @JsonIgnore
    private EntiteContract entiteContract;
}
