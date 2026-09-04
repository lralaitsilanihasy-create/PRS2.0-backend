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

    /**
     * ⚠️ Visa par intérim (2026-09-01) — vrai si le visa a été posé par un P/CC <strong>autre que le
     * dispatcheur</strong>, justifié par la note ci-dessous. Le signataire et la date ne sont pas
     * redits ici : ils sont déjà portés par {@code imCtrlPresident}/{@code imCtrlCc} et
     * {@code dateAcceptation}. Ce drapeau <em>qualifie</em> le visa, il ne le duplique pas.
     */
    @Column(name = "VISE_PAR_INTERIM", nullable = false)
    private Boolean viseParInterim = Boolean.FALSE;

    /**
     * Note d'intérim (PDF) justifiant l'absence du dispatcheur — stockée en base, comme les pièces
     * jointes et non comme le PDF du PV : le visa étant atomique, un rollback ne doit pas laisser de
     * fichier orphelin sur le FSX.
     */
    @Column(name = "NOTE_INTERIM")
    private byte[] noteInterim;

    @Column(name = "NOTE_INTERIM_NOM", length = 255)
    private String noteInterimNom;

    @Column(name = "NOTE_INTERIM_TAILLE")
    private Long noteInterimTaille;

    /**
     * ⚠️ <strong>Navette à deux niveaux</strong> (spec pilote du 2026-09-04) — étage courant du projet
     * dans la navette Président ↔ CC ↔ Membre : {@code CC}, {@code PRESIDENT}, ou {@code null}.
     *
     * <p>{@code null} n'est pas un état dégradé : c'est une navette SIMPLE (le contrat d'avant, un
     * seul étage), ou un projet qui n'est pas dans la navette P/CC — brouillon, en rectification chez
     * le Membre, ou déjà visé. Le statut du PV ne pouvait pas porter cette information :
     * {@code PROJET_SOUMIS} vaut aussi bien « soumis au CC » que « transmis au Président ».</p>
     */
    @Column(name = "NIVEAU_NAVETTE", length = 20)
    private String niveauNavette;

    /**
     * ⚠️ <strong>Co-signature élargie</strong> (spec pilote du 2026-09-04) — Chef de commission
     * <strong>désigné</strong> par le Président au visa, appelé à poser SA part de signature.
     *
     * <p>Distinct de {@code imCtrlCc}, qui nomme le CC ayant effectivement visé ou signé et que le
     * document imprime. Les confondre rendrait indécidable la bascule en {@code SIGNE} — « ce CC
     * doit-il encore signer, ou a-t-il déjà signé ? » — et ferait porter au PV le nom d'un signataire
     * qui ne l'est pas encore. Même partage que {@code imMembreCoSignataire} face à
     * {@code imCtrlMembre}.</p>
     */
    @Column(name = "IM_CC_COSIGNATAIRE", length = 7)
    private String imCcCoSignataire;

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
