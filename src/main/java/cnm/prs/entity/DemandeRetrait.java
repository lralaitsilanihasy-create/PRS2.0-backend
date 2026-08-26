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
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité JPA mappée sur la table {@code t_demande_retrait}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_demande_retrait")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemandeRetrait {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_DEMANDE_RETRAIT", nullable = false)
    private Integer idDemandeRetrait;

    /** Verrou optimiste (⚠️ LOT 4, 2026-08-26, migration V6) : une écriture concurrente perdante lève un 409 au lieu d'écraser. */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "ID_PRMP", nullable = false, length = 10)
    private String idPrmp;

    @Column(name = "MOTIF_RETRAIT", nullable = false, columnDefinition = "text")
    private String motifRetrait;

    @Column(name = "DATE_DEMANDE", nullable = false)
    private LocalDateTime dateDemande;

    @Column(name = "STATUT", nullable = false, length = 20)
    private String statut;

    @Column(name = "IM_CTRL_CC", length = 7)
    private String imCtrlCc;

    @Column(name = "DATE_DECISION")
    private LocalDateTime dateDecision;

    @Column(name = "OBS_DECISION", length = 500)
    private String obsDecision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DOSSIER", insertable = false, updatable = false)
    @JsonIgnore
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PRMP", insertable = false, updatable = false)
    @JsonIgnore
    private Prmp prmp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_CTRL_CC", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur ctrlCc;
}
