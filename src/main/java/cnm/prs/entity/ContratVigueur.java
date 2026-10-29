package cnm.prs.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V124 (2026-10-16, demande front « marchés subséquents », lot MS-1) — un contrat-cadre ou un marché à commandes en vigueur : un par lot
 * notifié. Ce qui le régit est figé depuis la dernière version validée de la fiche à la notification ; ses titulaires sont ceux de
 * l'attribution, liste fermée (décret n° 2019-1064, art. 18). {@code dateFin} est la fin de la période en cours (reconductions comprises).
 */
@Entity
@Table(name = "t_contrat_vigueur")
@Getter
@Setter
@NoArgsConstructor
public class ContratVigueur {

    public static final String CONTRAT_CADRE = "CONTRAT_CADRE";
    public static final String A_COMMANDE = "A_COMMANDE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "LOT", nullable = false)
    private Integer lot;

    @Column(name = "TYPE_MARCHE", nullable = false, length = 20)
    private String typeMarche;

    @Column(name = "DATE_EFFET", nullable = false)
    private LocalDate dateEffet;

    @Column(name = "DATE_FIN", nullable = false)
    private LocalDate dateFin;

    @Column(name = "PERIODE_MOIS")
    private Integer periodeMois;

    @Column(name = "DUREE_MAX_MOIS")
    private Integer dureeMaxMois;

    @Column(name = "RECONDUCTIBLE", nullable = false)
    private boolean reconductible;

    @Column(name = "RECONDUCTIONS_MAX")
    private Integer reconductionsMax;

    @Column(name = "RECONDUCTIONS", nullable = false)
    private int reconductions;

    @Column(name = "PREAVIS_MOIS")
    private Integer preavisMois;

    @Column(name = "MONTANT_ESTIME", precision = 18, scale = 2)
    private BigDecimal montantEstime;

    @Column(name = "MONTANT_MINIMUM", precision = 18, scale = 2)
    private BigDecimal montantMinimum;

    @Column(name = "MONTANT_MAXIMUM", precision = 18, scale = 2)
    private BigDecimal montantMaximum;

    @Column(name = "FORME_SUBSEQUENTS", length = 200)
    private String formeSubsequents;

    @Column(name = "RYTHME", length = 200)
    private String rythme;

    @Column(name = "CALENDRIER")
    private String calendrier;

    @Column(name = "CREE_LE", nullable = false)
    private LocalDateTime creeLe;

    /** ⚠️ V147 (« contrat-cadre-4a », §B3) — le calendrier structuré des remises : périodicité en mois ({@code B02-PC-04}). */
    @Column(name = "PERIODICITE_MOIS")
    private Integer periodiciteMois;

    /** ⚠️ V147 — la date de la première remise en concurrence ({@code B02-PC-05}). */
    @Column(name = "PREMIERE_REMISE")
    private LocalDate premiereRemise;

    /** ⚠️ V147 (§B2) — la fin de période pour laquelle {@code RECONDUCTION_A_DECIDER} est notifiée. */
    @Column(name = "RAPPEL_RECONDUCTION")
    private LocalDate rappelReconduction;

    /** ⚠️ V147 (§B3) — l'échéance du calendrier pour laquelle {@code REMISE_A_LANCER} est notifiée. */
    @Column(name = "RAPPEL_REMISE")
    private LocalDate rappelRemise;

    /** ⚠️ V147 (§B2) — {@code CONTRAT_ECHU} notifié. */
    @Column(name = "ECHU_NOTIFIE_LE")
    private LocalDateTime echuNotifieLe;
}
