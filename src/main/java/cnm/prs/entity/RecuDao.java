package cnm.prs.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V72 (demande front du 2026-10-06, « Voie B ») — le <strong>reçu du paiement des frais de dossier</strong>, déposé par un
 * candidat pour son entreprise (la clé est le NIF : deux comptes de la même entreprise le partagent), sur un ou plusieurs lots
 * ({@code lots} nul : tout le dossier). {@code EN_ATTENTE}, puis {@code VALIDE} ou {@code REFUSE} par la PRMP ou l'UGPM de la fiche,
 * sans retour.
 */
@Entity
@Table(name = "t_recu_dao")
@Getter
@Setter
@NoArgsConstructor
public class RecuDao {

    public static final String EN_ATTENTE = "EN_ATTENTE";
    public static final String VALIDE = "VALIDE";
    public static final String REFUSE = "REFUSE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_RECU", nullable = false)
    private Integer idRecu;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ID_ENTREPRISE")
    private Integer idEntreprise;

    @Column(name = "NIF", nullable = false, length = 20)
    private String nif;

    @Column(name = "RAISON_SOCIALE", length = 255)
    private String raisonSociale;

    /** Le compte qui l'a déposé. */
    @Column(name = "ID_CANDIDAT", nullable = false, length = 10)
    private String idCandidat;

    /** Les lots couverts, séparés par des virgules ; {@code null} : tout le dossier. */
    @Column(name = "LOTS", length = 200)
    private String lots;

    @Column(name = "MONTANT", precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(name = "REFERENCE_PAIEMENT", length = 100)
    private String referencePaiement;

    @Column(name = "DATE_PAIEMENT")
    private LocalDate datePaiement;

    @Column(name = "BANQUE", length = 200)
    private String banque;

    @Column(name = "NOM_FICHIER", length = 255)
    private String nomFichier;

    @Column(name = "FORMAT", length = 50)
    private String format;

    @Column(name = "TAILLE_OCTETS")
    private Long tailleOctets;

    @Column(name = "EMPREINTE", length = 64)
    private String empreinte;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "CONTENU")
    private byte[] contenu;

    @Column(name = "DATE_DEPOT", nullable = false)
    private LocalDateTime dateDepot;

    @Column(name = "ETAT", nullable = false, length = 10)
    private String etat;

    @Column(name = "MOTIF_REFUS")
    private String motifRefus;

    @Column(name = "DATE_DECISION")
    private LocalDateTime dateDecision;

    /** La fonction de qui a décidé ({@code PRMP} ou {@code UGPM}), pas son nom (servie au candidat). */
    @Column(name = "DECIDE_PAR", length = 20)
    private String decidePar;

    @Column(name = "LOGIN_DECIDEUR", length = 255)
    private String loginDecideur;

    /** ⚠️ La purge de conservation (V70) : le fichier supprimé, la ligne gardée. */
    @Column(name = "PURGE_LE")
    private LocalDateTime purgeLe;
}
