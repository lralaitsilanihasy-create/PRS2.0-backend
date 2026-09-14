package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_observation_controle} : une ligne « AU LIEU DE / LIRE »
 * d'observation d'un point de contrôle d'examen ({@link ExamenDetail}, via {@code ID_DETAIL}).
 * Relation 1,N : un point de contrôle a 0..N lignes d'observation. PK auto (IDENTITY).
 */
@Entity
@Table(name = "t_observation_controle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ObservationControle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_OBSERVATION", nullable = false)
    private Integer idObservation;

    /** Point de contrôle concerné (FK vers {@code t_examen_detail.ID_DETAIL_EXAMEN}). */
    @Column(name = "ID_DETAIL", nullable = false)
    private Integer idDetail;

    @Column(name = "AU_LIEU_DE", length = 500)
    private String auLieuDe;

    @Column(name = "LIRE", length = 500)
    private String lire;

    /** Ordre de saisie (ASC). */
    @Column(name = "ORDRE")
    private Integer ordre;

    /**
     * ⚠️ V30 (2026-09-14) — code de la <strong>cellule</strong> du document visée par la ligne
     * ({@link cnm.prs.enums.ChampCible}) ; {@code null} = aucune cellule, comportement antérieur. Validé
     * par {@code ObservationCibleValidateur} ; le CHECK SQL interdit une cible sans champ.
     */
    @Column(name = "CHAMP_CIBLE", length = 40)
    private String champCible;

    /** ⚠️ V30 — ligne de marché visée ({@code t_marche.ID_DETAIL}), sans FK ; {@code null} sans champ. */
    @Column(name = "ID_MARCHE_CIBLE")
    private Integer idMarcheCible;

    /** ⚠️ V30 — bénéficiaire visé ({@code t_service_beneficiaire.ID_BENEF}), colonnes par bénéficiaire seulement. */
    @Column(name = "ID_BENEF_CIBLE")
    private Integer idBenefCible;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DETAIL", insertable = false, updatable = false)
    @JsonIgnore
    private ExamenDetail detail;
}
