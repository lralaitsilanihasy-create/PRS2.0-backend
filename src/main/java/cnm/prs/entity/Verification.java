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
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_verification}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_verification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Verification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_VERIFICATION", nullable = false)
    private Integer idVerification;

    @Column(name = "ID_RECEPTION", nullable = false)
    private Integer idReception;

    @Column(name = "ID_PV", nullable = false)
    private Integer idPv;

    @Column(name = "IM_CTRL_VERIF", length = 7)
    private String imCtrlVerif;

    @Column(name = "DATE_VERIF")
    private LocalDate dateVerif;

    @Column(name = "OBSERVATION", length = 500)
    private String observation;

    @Column(name = "OBS_LEVEES")
    private Boolean obsLevees;

    /** ⚠️ Règle ajoutée — motif de rectification saisi par la PRMP à la resoumission (visible côté vérificateur). */
    @Column(name = "MOTIF_RECTIF", length = 255)
    private String motifRectif;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PV", insertable = false, updatable = false)
    @JsonIgnore
    private PvExamen pv;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_RECEPTION", insertable = false, updatable = false)
    @JsonIgnore
    private Reception reception;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_CTRL_VERIF", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur ctrlVerif;
}
