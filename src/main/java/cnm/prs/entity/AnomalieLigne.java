package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, migration V32) — une <strong>ligne visée par un
 * signalement inter-lignes</strong> ({@code t_anomalie_ligne}).
 *
 * <p>Le fractionnement illicite est le cœur du lot, et il ne concerne jamais une ligne seule : « ces trois
 * lignes, compte X, cumul Y ≥ seuil Z ». {@code t_anomalie.ID_DETAIL} ne désigne qu'une ligne et reste
 * réservé aux signalements qui n'en visent qu'une ; les signalements de portée dossier listent les leurs
 * ici.</p>
 *
 * <p>{@link #montant} est le montant en vigueur de la ligne <strong>au moment de la détection</strong>. Il
 * est recopié, et non relu, pour que l'explication reste lisible après modification du plan — c'est ce que
 * le contrôleur a besoin de voir quand une PRMP a fait taire l'alarme en réimputant ses lignes.</p>
 */
@Entity
@Table(name = "t_anomalie_ligne")
@IdClass(AnomalieLigne.Cle.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnomalieLigne {

    @Id
    @Column(name = "ID_ANOMALIE", nullable = false)
    private Integer idAnomalie;

    @Id
    @Column(name = "ID_DETAIL", nullable = false)
    private Integer idDetail;

    /** Montant en vigueur de la ligne au moment de la détection ({@code NOUV_MONT_ESTIM}, sinon {@code MONT_ESTIM}). */
    @Column(name = "MONTANT")
    private BigDecimal montant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DETAIL", insertable = false, updatable = false)
    @JsonIgnore
    private Marche detail;

    /**
     * Clé composite de {@code @IdClass} — JPA <strong>exige</strong> ici {@code equals}/{@code hashCode}
     * par valeur, d'où {@code @Data} (même motif que {@link SequenceReference.Cle}).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cle implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer idAnomalie;
        private Integer idDetail;
    }
}
