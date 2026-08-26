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
 * Entité JPA mappée sur la table {@code t_prmp_entite}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_prmp_entite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PrmpEntite {

    @Id
    @Column(name = "ID_PRMP_ENTITE", nullable = false)
    private Integer idPrmpEntite;

    @Column(name = "ID_PRMP", nullable = false, length = 10)
    private String idPrmp;

    @Column(name = "ID_ENTITE_CONTRACT", nullable = false)
    private Integer idEntiteContract;

    @Column(name = "DATE_AFFECTATION")
    private LocalDate dateAffectation;

    @Column(name = "ACTIF", nullable = false)
    private Boolean actif;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_ENTITE_CONTRACT", insertable = false, updatable = false)
    @JsonIgnore
    private EntiteContract entiteContract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PRMP", insertable = false, updatable = false)
    @JsonIgnore
    private Prmp prmp;
}
