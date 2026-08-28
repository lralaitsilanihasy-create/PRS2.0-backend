package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_pv_examen}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_pv_examen",
        uniqueConstraints = @UniqueConstraint(name = "uq_pv_examen_refe_pv", columnNames = "REFE_PV"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PvExamen {

    @Id
    @Column(name = "ID_PV", nullable = false)
    private Integer idPv;

    /** Verrou optimiste (⚠️ LOT 4, 2026-08-26, migration V6) : une écriture concurrente perdante lève un 409 au lieu d'écraser. */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @Column(name = "ID_EXAMEN", nullable = false)
    private Integer idExamen;

    /** ⚠️ Règle ajoutée (2026-08-01) — nullable : l'avis n'est posé qu'à la clôture de navette (accepter). */
    @Column(name = "ID_AVIS", length = 10)
    private String idAvis;

    @Column(name = "IM_CTRL_PRESIDENT", length = 7)
    private String imCtrlPresident;

    @Column(name = "IM_CTRL_CC", length = 7)
    private String imCtrlCc;

    @Column(name = "IM_CTRL_MEMBRE", nullable = false, length = 7)
    private String imCtrlMembre;

    @Column(name = "SYNTHESE_OBSERVATIONS", columnDefinition = "text")
    private String syntheseObservations;

    @Column(name = "STATUT_PV", nullable = false, length = 20)
    private String statutPv;

    @Column(name = "NB_NAVETTES", nullable = false)
    private Integer nbNavettes;

    @Column(name = "DATE_SOUMISSION_INITIALE")
    private LocalDate dateSoumissionInitiale;

    @Column(name = "DATE_ACCEPTATION")
    private LocalDate dateAcceptation;

    @Column(name = "DATE_SIGNATURE_PRESIDENT")
    private LocalDate dateSignaturePresident;

    @Column(name = "DATE_SIGNATURE_CC")
    private LocalDate dateSignatureCc;

    @Column(name = "DATE_SIGNATURE_MEMBRE")
    private LocalDate dateSignatureMembre;

    @Column(name = "DATE_PV")
    private LocalDate datePv;

    @Column(name = "REFERENCE_PV", length = 100)
    private String referencePv;

    /** Référence dérivée du dossier (refeDossier avec /PV avant l'année) — auto-générée à la création, unique. */
    @Column(name = "REFE_PV", length = 120)
    private String refePv;

    /** Vérificateur désigné Secrétaire de séance à la soumission de l'examen (FK {@code tr_controleur}). */
    @Column(name = "ID_SECRETAIRE_SEANCE", length = 7)
    private String idSecretaireSeance;

    /**
     * ⚠️ Co-signature (2026-08-28, arbitrage du pilote) — Membre <strong>désigné</strong> par le
     * Président ou le Chef de commission au moment de signer, seul habilité à poser la part Membre.
     * Distinct de {@code imCtrlMembre}, qui désigne l'attributaire ayant <em>examiné</em> le dossier
     * et qui est imprimé sur le PV officiel. Vide jusqu'à la signature du P/CC : la désignation est
     * préalable (ordre B), la part Membre n'est pas signable avant elle.
     */
    @Column(name = "IM_MEMBRE_COSIGNATAIRE", length = 7)
    private String imMembreCoSignataire;

    /** Chemin du PDF du Projet de PV sur le FSX (renseigné si le PV est éligible à la génération). */
    @Column(name = "CHEMIN_DOCUMENT", length = 500)
    private String cheminDocument;

    /** ⚠️ Spec navette (2026-08-01) — date d'ARCHIVAGE du PV par l'Assistant contrôleur (l'archivage clôt le dossier). */
    @Column(name = "DATE_ARCHIVAGE")
    private LocalDate dateArchivage;

    /** Assistant contrôleur archiveur (matricule, identité JWT). */
    @Column(name = "IM_ARCHIVEUR", length = 7)
    private String imArchiveur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_AVIS", insertable = false, updatable = false)
    @JsonIgnore
    private Avis avis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_EXAMEN", insertable = false, updatable = false)
    @JsonIgnore
    private Examen examen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_CTRL_CC", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur ctrlCc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_CTRL_MEMBRE", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur ctrlMembre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IM_CTRL_PRESIDENT", insertable = false, updatable = false)
    @JsonIgnore
    private Controleur ctrlPresident;
}
