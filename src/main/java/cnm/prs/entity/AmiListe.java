package cnm.prs.entity;

import java.io.Serializable;
import java.math.BigDecimal;

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

/** ⚠️ V83 (AMI en ligne, tranche AMI-b, §B3) — un candidat de la liste restreinte arrêtée : son rang, son compte, sa note. */
@Entity
@Table(name = "t_ami_liste")
@IdClass(AmiListe.Cle.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AmiListe {

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Id
    @Column(name = "RANG", nullable = false)
    private Integer rang;

    @Column(name = "ID_EXPRESSION", nullable = false, length = 36)
    private String idExpression;

    @Column(name = "ID_CANDIDAT", nullable = false, length = 10)
    private String idCandidat;

    @Column(name = "NIF", length = 50)
    private String nif;

    @Column(name = "RAISON_SOCIALE", length = 255)
    private String raisonSociale;

    @Column(name = "NOTE", precision = 6, scale = 2)
    private BigDecimal note;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Cle implements Serializable {
        private Long idDmc;
        private Integer rang;
    }
}
