package cnm.prs.entity;

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
 * ⚠️ V66 (demande front du 2026-10-04, soumission en ligne, lot 2, §B2.2, §B2.3 ; ADR-0013 §1, §3) — la clé d'un
 * <strong>détenteur de part</strong> : un membre désigné ({@code role = MEMBRE}, {@code im}) ou la part de secours
 * ({@code role = SECOURS}, sans matricule).
 * <ul>
 *   <li>{@code clePublique} (SPKI en base64) et {@code empreinte} (SHA-256 de la SPKI, hexadécimal minuscule) sont
 *       publiques : servies aux candidats pour sceller.</li>
 *   <li>La clé privée n'existe qu'<strong>enveloppée</strong> par la phrase secrète du détenteur ({@code env*}, PKCS#8
 *       sous AES-256-GCM, clé dérivée par PBKDF2-SHA-256) : le serveur en garde la copie pour la rendre à son seul
 *       propriétaire, il ne peut pas la lire.</li>
 *   <li>{@code dateArchivage} : une clé remplacée après le premier dépôt est archivée, jamais supprimée (S4) — des offres
 *       scellées pour son empreinte en dépendent.</li>
 * </ul>
 */
@Entity
@Table(name = "t_cle_detenteur")
@Getter
@Setter
@NoArgsConstructor
public class CleDetenteur {

    public static final String MEMBRE = "MEMBRE";
    public static final String SECOURS = "SECOURS";
    /** ⚠️ V71 — qui a généré la part de secours. */
    public static final String PAR_RESPONSABLE = "RESPONSABLE";
    public static final String PAR_DEPOSITAIRE = "DEPOSITAIRE";

    public static final String PUBLIEE = "PUBLIEE";
    public static final String VERIFIEE = "VERIFIEE";
    public static final String PERDUE = "PERDUE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_CLE", nullable = false)
    private Long idCle;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ROLE", nullable = false, length = 10)
    private String role;

    @Column(name = "IM", length = 10)
    private String im;

    @Column(name = "CLE_PUBLIQUE", nullable = false)
    private String clePublique;

    @Column(name = "EMPREINTE", nullable = false, length = 64)
    private String empreinte;

    @Column(name = "ENV_CHIFFRE", nullable = false)
    private String envChiffre;

    @Column(name = "ENV_IV", nullable = false, length = 64)
    private String envIv;

    @Column(name = "ENV_SEL", nullable = false, length = 64)
    private String envSel;

    @Column(name = "ENV_ITERATIONS", nullable = false)
    private Integer envIterations;

    @Column(name = "ENV_KDF", nullable = false, length = 30)
    private String envKdf;

    @Column(name = "ENV_ALGORITHME", nullable = false, length = 30)
    private String envAlgorithme;

    @Column(name = "ETAT_PART", nullable = false, length = 10)
    private String etatPart;

    @Column(name = "DATE_PUBLICATION", nullable = false)
    private LocalDateTime datePublication;

    @Column(name = "DERNIERE_VERIFICATION")
    private LocalDateTime derniereVerification;

    @Column(name = "REMPLACEMENTS", nullable = false)
    private Integer remplacements = 0;

    /** ⚠️ V71 (§B2, §B4) — part de secours : {@code RESPONSABLE} (ancien geste) ou {@code DEPOSITAIRE} ; nul pour un membre. */
    @Column(name = "GENERE_PAR", length = 12)
    private String generePar;

    /** ⚠️ V71 — le compte du dépositaire qui a publié la part de secours ({@code D…}, nouveau geste). */
    @Column(name = "ID_DEPOSITAIRE", length = 10)
    private String idDepositaire;

    @Column(name = "DATE_ARCHIVAGE")
    private LocalDateTime dateArchivage;
}
