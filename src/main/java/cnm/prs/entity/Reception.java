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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_reception}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_reception", indexes = {
        @Index(name = "idx_reception_dossier", columnList = "ID_DOSSIER")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Reception {

    @Id
    @Column(name = "ID_RECEPTION", nullable = false)
    private Integer idReception;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "NUM_PASSAGE", nullable = false)
    private Integer numPassage;

    @Column(name = "TYPE_PASSAGE", nullable = false, length = 10)
    private String typePassage;

    @Column(name = "IM_CTRL_RECEPT", length = 7)
    private String imCtrlRecept;

    /** Date <strong>et heure</strong> de réception par le secrétariat (TIMESTAMP). */
    @Column(name = "DATE_RECEPTION")
    private LocalDateTime dateReception;

    @Column(name = "OBSERVATION", length = 500)
    private String observation;

    @Column(name = "COMPLET")
    private Boolean complet;

    @Column(name = "ID_RECEPTION_PREC")
    private Integer idReceptionPrec;

    /**
     * (Règle ajoutée) Référence officielle de la réception — <strong>snapshot immuable</strong> de la forme
     * {@code <seq>/<type>/<code_localite>/<annee>} figé au POST. Reste correct même après une mutation
     * ultérieure de {@code dossier.refeDossier} (ex. restauration de la référence PPM après un retrait accepté).
     */
    @Column(name = "REFERENCE", length = 100)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DOSSIER", insertable = false, updatable = false)
    @JsonIgnore
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_CTRL_RECEPT", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur ctrlRecept;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_RECEPTION_PREC", insertable = false, updatable = false)
    @JsonIgnore
    private Reception receptionPrec;
}
