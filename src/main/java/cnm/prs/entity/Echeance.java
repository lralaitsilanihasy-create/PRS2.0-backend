package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_echeance}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_echeance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Echeance {

    @Id
    @Column(name = "ID_ECHEANCE", nullable = false)
    private Integer idEcheance;

    @Column(name = "ID_DETAIL", nullable = false)
    private Integer idDetail;

    @Column(name = "TYPE_JALON", nullable = false, length = 30)
    private String typeJalon;

    @Column(name = "DATE_PREVUE", nullable = false)
    private LocalDate datePrevue;

    @Column(name = "DATE_REELLE")
    private LocalDate dateReelle;

    @Column(name = "STATUT_JALON", length = 20)
    private String statutJalon;

    @Column(name = "ECART_JOURS")
    private Integer ecartJours;

    @Column(name = "ALERTE_ENVOYEE")
    private Boolean alerteEnvoyee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DETAIL", insertable = false, updatable = false)
    @JsonIgnore
    private Marche detail;
}
