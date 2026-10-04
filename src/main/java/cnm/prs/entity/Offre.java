package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V68 (demande front du 2026-10-04, soumission en ligne, lot 3 ; ADR-0013 §1, §4, §7) — une <strong>offre déposée en
 * ligne</strong> : un conteneur scellé dans le navigateur du candidat, que le serveur ne peut pas lire. La ligne porte ce que
 * le serveur sait — compte, entreprise au moment du dépôt, lot, horodatage, taille, empreinte, en-tête (donc les empreintes
 * des clés pour lesquelles l'offre est scellée), chemin du fichier — et rien d'autre : ni le contenu, ni un prix.
 * {@code idOffre} est l'UUID tiré par le navigateur, qui figure dans l'en-tête et dans les données authentifiées des morceaux.
 */
@Entity
@Table(name = "t_offre")
@Getter
@Setter
@NoArgsConstructor
public class Offre {

    public static final String EN_COURS = "EN_COURS";
    public static final String DEPOSEE = "DEPOSEE";
    public static final String REMPLACEE = "REMPLACEE";
    public static final String RETIREE = "RETIREE";
    public static final String ECARTEE = "ECARTEE";

    @Id
    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ID_CANDIDAT", nullable = false, length = 10)
    private String idCandidat;

    @Column(name = "ID_ENTREPRISE", nullable = false)
    private Integer idEntreprise;

    @Column(name = "NIF", length = 50)
    private String nif;

    @Column(name = "RAISON_SOCIALE", length = 255)
    private String raisonSociale;

    @Column(name = "LOT")
    private Integer lot;

    @Column(name = "ETAT", nullable = false, length = 10)
    private String etat;

    @Column(name = "DATE_CREATION", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "DATE_DEPOT")
    private LocalDateTime dateDepot;

    @Column(name = "DATE_RETRAIT")
    private LocalDateTime dateRetrait;

    @Column(name = "NUMERO")
    private Integer numero;

    /** L'en-tête JSON, tel que le navigateur l'a sérialisé et haché. */
    @Column(name = "EN_TETE", nullable = false)
    private String enTete;

    @Column(name = "NOMBRE_MORCEAUX", nullable = false)
    private Integer nombreMorceaux;

    @Column(name = "TAILLE_MORCEAU", nullable = false)
    private Integer tailleMorceau;

    @Column(name = "TAILLE_CONTENU")
    private Long tailleContenu;

    /** Les octets reçus (morceaux). */
    @Column(name = "TAILLE", nullable = false)
    private Long taille = 0L;

    /** SHA-256 de l'en-tête puis des morceaux, posée au scellement. */
    @Column(name = "EMPREINTE", length = 64)
    private String empreinte;

    @Column(name = "QUORUM", nullable = false)
    private Integer quorum;

    @Column(name = "N", nullable = false)
    private Integer n;

    /** Les empreintes des clés publiques pour lesquelles l'offre est scellée, dans l'ordre, séparées par des virgules. */
    @Column(name = "EMPREINTES_DETENTEURS", nullable = false)
    private String empreintesDetenteurs;

    @Column(name = "CHEMIN", length = 500)
    private String chemin;

    @Column(name = "REMPLACE", length = 36)
    private String remplace;

    @Column(name = "REMPLACEE_PAR", length = 36)
    private String remplaceePar;

    /** Les NIF normalisés des membres d'un groupement, pour le seul contrôle des exclusions (séparés par des virgules). */
    @Column(name = "GROUPEMENT_NIFS", length = 1000)
    private String groupementNifs;

    @Column(name = "DERNIER_MORCEAU")
    private LocalDateTime dernierMorceau;
}
