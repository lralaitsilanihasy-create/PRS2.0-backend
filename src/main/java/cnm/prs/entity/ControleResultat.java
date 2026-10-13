package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ R5 (manuel révisé pour l'application, lot 1, §B6 ; V105, 2026-10-13) — un <strong>résultat de contrôle</strong> d'un dossier à une
 * soumission : automatique ({@code auteur = SERVEUR}, règle codée, valeurs constatées) ou posé par un contrôleur (matricule, point de
 * grille ou règle, observation obligatoire si non conforme).
 */
@Entity
@Table(name = "t_controle_resultat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ControleResultat {

    public static final String CONFORME = "CONFORME";
    public static final String NON_CONFORME = "NON_CONFORME";
    public static final String NON_APPLICABLE = "NON_APPLICABLE";
    /** L'auteur d'un contrôle automatique. */
    public static final String SERVEUR = "SERVEUR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_CONTROLE")
    private Long idControle;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "NUMERO_SOUMISSION", nullable = false)
    private Integer numeroSoumission;

    /** La référence du manuel (« 2-III-A P5 »), ou {@code null} pour un point de grille. */
    @Column(name = "REFERENCE", length = 60)
    private String reference;

    /** Le point de grille d'un contrôle B, ou {@code null}. */
    @Column(name = "ID_POINT_CTRL")
    private Integer idPointCtrl;

    /** Le code de la règle ({@code DOCUMENTS_EVALUATION_SIGNES}…), ou {@code POINT_<id>} pour un point de grille. */
    @Column(name = "REGLE", nullable = false, length = 60)
    private String regle;

    @Column(name = "LIBELLE", length = 300)
    private String libelle;

    /** Les valeurs constatées, en JSON. */
    @Column(name = "VALEURS", columnDefinition = "text")
    private String valeurs;

    @Column(name = "RESULTAT", nullable = false, length = 20)
    private String resultat;

    @Column(name = "BLOQUANT", nullable = false)
    private boolean bloquant;

    @Column(name = "AUTEUR", nullable = false, length = 100)
    private String auteur;

    @Column(name = "DATE_CONTROLE", nullable = false)
    private LocalDateTime dateControle;

    @Column(name = "OBSERVATION", columnDefinition = "text")
    private String observation;
}
