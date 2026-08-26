package cnm.prs.entity;

import java.math.BigDecimal;
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
 * ⚠️ Règle ajoutée (2026-08-15, visibilité des rectifications) — instantané <strong>pré-correction</strong>
 * d'une ligne de marché, figé au <strong>premier</strong> {@code PUT /api/saisies/ppm/{id}} d'un cycle de
 * rectification (dossier {@code EN_ATTENTE_DECISION_PRMP}). La rectification modifie la version courante
 * <em>en place</em> (structure figée, mise à jour par {@code idDetail}) : sans cet instantané, il n'y a
 * rien à comparer pour montrer au vérificateur ce que la PRMP a changé.
 *
 * <p>Un <strong>cycle</strong> = de la transmission des observations à la resoumission ; {@code CYCLE} =
 * nombre de resoumissions du dossier + 1 au moment du gel. Une seule série d'instantanés est conservée
 * par dossier (celle du dernier cycle — le vérificateur juge toujours le dernier). Les empreintes des
 * collections (bénéficiaires, lots, processus) sont figées ici car le diff les compare à l'état courant
 * — même sémantique que {@code MiseAJourPpmService}.</p>
 */
@Entity
@Table(name = "t_snapshot_rectif_ligne")
@Getter
@Setter
@NoArgsConstructor
public class SnapshotRectifLigne {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_SNAPSHOT", nullable = false)
    private Integer idSnapshot;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    /** Cycle de rectification (resoumissions du dossier + 1 au moment du gel). */
    @Column(name = "CYCLE", nullable = false)
    private Integer cycle;

    /** PK de la ligne de marché ({@code t_marche.ID_DETAIL}) — stable en rectification (structure figée). */
    @Column(name = "ID_DETAIL", nullable = false)
    private Integer idDetail;

    @Column(name = "ID_LIGNE_ORIGINE")
    private Integer idLigneOrigine;

    @Column(name = "DESIGNATION_MARCHE", length = 500)
    private String designationMarche;

    @Column(name = "MONT_ESTIM")
    private BigDecimal montEstim;

    @Column(name = "NOUV_MONT_ESTIM")
    private BigDecimal nouvMontEstim;

    @Column(name = "NUM_COMPTE", length = 20)
    private String numCompte;

    @Column(name = "FINANCEMENT", length = 100)
    private String financement;

    @Column(name = "STATUT", length = 20)
    private String statut;

    @Column(name = "ID_NATURE")
    private Integer idNature;

    @Column(name = "ID_MODE")
    private Integer idMode;

    /** Nom de l'énumération {@code FormeMarche} au moment du gel ({@code null} si non renseignée). */
    @Column(name = "FORME_MARCHE", length = 20)
    private String formeMarche;

    @Column(name = "SUPPRIMEE")
    private Boolean supprimee;

    /** Empreintes normalisées des collections au moment du gel (mêmes formats que le diff des versions). */
    @Column(name = "EMP_BENEFICIAIRES", length = 2000)
    private String empBeneficiaires;

    @Column(name = "EMP_LOTS", length = 2000)
    private String empLots;

    @Column(name = "EMP_PROCESSUS", length = 2000)
    private String empProcessus;

    @Column(name = "DATE_SNAPSHOT", nullable = false)
    private LocalDateTime dateSnapshot;
}
