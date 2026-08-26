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
 * Entité JPA mappée sur la table {@code t_copie_dossier}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_copie_dossier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CopieDossier {

    @Id
    @Column(name = "ID_COPIE", nullable = false)
    private Integer idCopie;

    @Column(name = "ID_DISPATCH", nullable = false)
    private Integer idDispatch;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "IM_DESTINATAIRE", nullable = false, length = 7)
    private String imDestinataire;

    @Column(name = "TYPE_COPIE", nullable = false, length = 30)
    private String typeCopie;

    @Column(name = "DATE_TRANSMISSION", nullable = false)
    private LocalDateTime dateTransmission;

    @Column(name = "ACCUSE_RECEPTION", nullable = false)
    private Boolean accuseReception;

    @Column(name = "DATE_ACCUSE")
    private LocalDateTime dateAccuse;

    @Column(name = "OBSERVATION", length = 300)
    private String observation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DISPATCH", insertable = false, updatable = false)
    @JsonIgnore
    private Dispatch dispatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DOSSIER", insertable = false, updatable = false)
    @JsonIgnore
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_DESTINATAIRE", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur destinataire;
}
