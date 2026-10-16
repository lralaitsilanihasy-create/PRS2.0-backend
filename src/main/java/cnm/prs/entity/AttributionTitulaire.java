package cnm.prs.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 * ⚠️ V122 (2026-10-15, tranche MT-b1, décret n° 2019-1064, art. 13-15) — un titulaire retenu d'un lot de contrat-cadre
 * multi-attributaire : l'offre, son rang au classement et ses valeurs figées au choix de la PRMP. Le cycle de vie du lot (information,
 * signature, notification) reste porté par {@link Attribution}.
 */
@Entity
@Table(name = "t_attribution_titulaire")
@IdClass(AttributionTitulaire.Cle.class)
@Getter
@Setter
@NoArgsConstructor
public class AttributionTitulaire {

    @Id
    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Id
    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Id
    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "RANG")
    private Integer rang;

    @Column(name = "MONTANT", precision = 18, scale = 2)
    private BigDecimal montant;

    @Column(name = "MONTANT_TTC", precision = 18, scale = 2)
    private BigDecimal montantTtc;

    @Column(name = "DELAI", length = 50)
    private String delai;

    @Column(name = "RETENU_LE", nullable = false)
    private LocalDateTime retenuLe;

    @Column(name = "RETENU_PAR", length = 100)
    private String retenuPar;

    /** ⚠️ V123 (tranche MT-b2, réponse 2 du pilote) — le titulaire retiré seul, faute de pièces : le contrat-cadre continue avec les autres. */
    @Column(name = "RETIRE_LE")
    private LocalDateTime retireLe;

    @Column(name = "RETIRE_PAR", length = 100)
    private String retirePar;

    @Column(name = "MOTIF_RETRAIT")
    private String motifRetrait;

    /** ⚠️ V123 — l'accusé de réception de la notification par ce titulaire (la première consultation de son résultat ou du contrat signé). */
    @Column(name = "NOTIFICATION_RECUE_LE")
    private LocalDateTime notificationRecueLe;

    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Cle implements Serializable {
        private Long idDmc;
        private Integer lot;
        private String idOffre;
    }
}
