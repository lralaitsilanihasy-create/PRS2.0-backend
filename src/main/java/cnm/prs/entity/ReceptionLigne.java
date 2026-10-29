package cnm.prs.entity;

import java.io.Serializable;
import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V148 (2026-10-29, demande front « bons-reception », §B3) — les quantités d'une réception provisoire pour une ligne du bon
 * ({@code rang}) : livrées à la déclaration de la livraison, reçues au PV. Une réception sans ligne (avant V148) vaut pour tout le bon.
 */
@Entity
@Table(name = "t_reception_ligne")
@IdClass(ReceptionLigne.Cle.class)
@Getter
@Setter
@NoArgsConstructor
public class ReceptionLigne {

    @Id
    @Column(name = "ID_RECEPTION", nullable = false)
    private Long idReception;

    @Id
    @Column(name = "RANG", nullable = false)
    private Integer rang;

    @Column(name = "QUANTITE_LIVREE", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantiteLivree;

    @Column(name = "QUANTITE_RECUE", precision = 18, scale = 3)
    private BigDecimal quantiteRecue;

    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Cle implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long idReception;
        private Integer rang;
    }
}
