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
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité JPA mappée sur la table {@code t_marche}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_marche", indexes = {
        @Index(name = "idx_marche_dossier", columnList = "ID_DOSSIER"),
        @Index(name = "idx_marche_ppm", columnList = "ID_PPM")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Marche {

    @Id
    @Column(name = "ID_DETAIL", nullable = false)
    private Integer idDetail;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "ID_PPM", nullable = false)
    private Integer idPpm;

    @Column(name = "DESIGNATION_MARCHE", length = 500)
    private String designationMarche;

    @Column(name = "NUM_COMPTE", length = 20)
    private String numCompte;

    @Column(name = "MONT_ESTIM")
    private BigDecimal montEstim;

    @Column(name = "ANCIEN_MONT_ESTIM")
    private BigDecimal ancienMontEstim;

    @Column(name = "NOUV_MONT_ESTIM")
    private BigDecimal nouvMontEstim;

    @Column(name = "FINANCEMENT", length = 20)
    private String financement;

    @Column(name = "STATUT", length = 20)
    private String statut;

    @Column(name = "ID_NATURE")
    private Integer idNature;

    @Column(name = "ID_MODE")
    private Integer idMode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DOSSIER", insertable = false, updatable = false)
    @JsonIgnore
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PPM", insertable = false, updatable = false)
    @JsonIgnore
    private Ppm ppm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "NUM_COMPTE", insertable = false, updatable = false)
    @JsonIgnore
    private Compte compte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_MODE", insertable = false, updatable = false)
    @JsonIgnore
    private ModePassation mode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_NATURE", insertable = false, updatable = false)
    @JsonIgnore
    private Nature nature;
}
