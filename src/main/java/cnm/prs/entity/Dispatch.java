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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_dispatch}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_dispatch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Dispatch {

    @Id
    @Column(name = "ID_DISPATCH", nullable = false)
    private Integer idDispatch;

    @Column(name = "ID_RECEPTION", nullable = false)
    private Integer idReception;

    @Column(name = "IM_CTRL_DISPATCH", length = 7)
    private String imCtrlDispatch;

    @Column(name = "IM_CTRL_CC", length = 7)
    private String imCtrlCc;

    @Column(name = "IM_CTRL_MEMBRE", length = 7)
    private String imCtrlMembre;

    /** Date <strong>et heure</strong> du dispatch (TIMESTAMP). */
    @Column(name = "DATE_DISPATCH")
    private LocalDateTime dateDispatch;

    @Column(name = "DATE_CTRL_ASSIGNE")
    private LocalDate dateCtrlAssigne;

    @Column(name = "INSTRUCTIONS", length = 500)
    private String instructions;

    @Column(name = "INTERIM_DISPATCH", nullable = false)
    private Boolean interimDispatch;

    /**
     * ⚠️ Intérim désigné (V34, 2026-09-21) — non nul quand le dispatch (ou la réattribution) a été posé par
     * un intérimaire désigné ({@code t_interim}). {@code imCtrlDispatch} porte alors le <strong>titulaire</strong>,
     * pas l'intérimaire (ADR-0008) : c'est le dispatcheur que l'aval reconnaît — visa, retrait, discriminant
     * de la navette à deux niveaux. Distinct d'{@code interimDispatch}, le repli ponctuel sans désignation.
     */
    @Column(name = "ID_INTERIM")
    private Integer idInterim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_RECEPTION", insertable = false, updatable = false)
    @JsonIgnore
    private Reception reception;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_CTRL_CC", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur ctrlCc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_CTRL_DISPATCH", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur ctrlDispatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_CTRL_MEMBRE", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur ctrlMembre;
}
