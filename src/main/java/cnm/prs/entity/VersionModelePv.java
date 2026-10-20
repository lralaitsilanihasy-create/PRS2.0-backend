package cnm.prs.entity;

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
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », lot G1 ; V138) — une version d'un modèle de PV : le fichier Word déposé
 * (en base, Q2), son empreinte, son état ({@code BROUILLON}, {@code EN_VIGUEUR}, {@code REMPLACEE}) et son rapport de contrôle
 * (JSON). Une version en vigueur ne se modifie plus : on en dépose une nouvelle.
 */
@Entity
@Table(name = "t_version_modele_pv")
@Getter
@Setter
@NoArgsConstructor
public class VersionModelePv {

    public static final String BROUILLON = "BROUILLON";
    public static final String EN_VIGUEUR = "EN_VIGUEUR";
    public static final String REMPLACEE = "REMPLACEE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_VERSION", nullable = false)
    private Long idVersion;

    @Column(name = "ID_MODELE", nullable = false)
    private Long idModele;

    @Column(name = "NUMERO", nullable = false)
    private Integer numero;

    @Column(name = "NOM_FICHIER", nullable = false)
    private String nomFichier;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "CONTENU", nullable = false, columnDefinition = "bytea")
    private byte[] contenu;

    @Column(name = "EMPREINTE", nullable = false, length = 64)
    private String empreinte;

    @Column(name = "TAILLE", nullable = false)
    private Long taille;

    @Column(name = "ETAT", nullable = false, length = 20)
    private String etat;

    @Column(name = "RAPPORT", columnDefinition = "text")
    private String rapport;

    @Column(name = "BLOQUANT", nullable = false)
    private Boolean bloquant;

    @Column(name = "MOTIF")
    private String motif;

    @Column(name = "DEPOSEE_LE", nullable = false)
    private LocalDateTime deposeeLe;

    @Column(name = "DEPOSEE_PAR")
    private String deposeePar;

    @Column(name = "EN_VIGUEUR_LE")
    private LocalDateTime enVigueurLe;

    @Column(name = "EN_VIGUEUR_PAR")
    private String enVigueurPar;

    @Column(name = "REMPLACEE_LE")
    private LocalDateTime remplaceeLe;
}
