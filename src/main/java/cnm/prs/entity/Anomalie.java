package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_anomalie}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_anomalie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Anomalie {

    @Id
    @Column(name = "ID_ANOMALIE", nullable = false)
    private Integer idAnomalie;

    @Column(name = "ID_DETAIL")
    private Integer idDetail;

    @Column(name = "ID_PPM")
    private Integer idPpm;

    @Column(name = "ID_REGLE_ANOMALIE", nullable = false)
    private Integer idRegleAnomalie;

    @Column(name = "TYPE_ANOMALIE", length = 50)
    private String typeAnomalie;

    @Column(name = "GRAVITE", length = 10)
    private String gravite;

    @Column(name = "DESCRIPTION", columnDefinition = "text")
    private String description;

    @Column(name = "DATE_DETECTION")
    private LocalDateTime dateDetection;

    @Column(name = "SOURCE", length = 20)
    private String source;

    @Column(name = "STATUT", length = 20)
    private String statut;

    @Column(name = "IM_TRAITEMENT", length = 7)
    private String imTraitement;

    @Column(name = "DATE_TRAITEMENT")
    private LocalDateTime dateTraitement;

    @Column(name = "COMMENTAIRE_TRAITEMENT", columnDefinition = "text")
    private String commentaireTraitement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DETAIL", insertable = false, updatable = false)
    @JsonIgnore
    private Marche detail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PPM", insertable = false, updatable = false)
    @JsonIgnore
    private Ppm ppm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_REGLE_ANOMALIE", insertable = false, updatable = false)
    @JsonIgnore
    private RegleAnomalie regleAnomalie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_TRAITEMENT", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur traitement;
}
