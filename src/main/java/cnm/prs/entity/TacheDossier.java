package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entite JPA mappee sur {@code t_tache_dossier} — la <strong>FIN</strong> d'un passage par une etape.
 *
 * <p>⚠️ <strong>Demande pilote du 2026-09-12 — la prise en charge a disparu.</strong> Une ligne n'est
 * plus ouverte par un geste de porteur puis close : elle est ecrite, deja close, par le geste metier qui
 * <em>termine</em> l'etape. Il n'y a donc plus ni {@code DATE_PRISE_EN_CHARGE} ni prevision saisie ;
 * l'<strong>entree</strong> dans l'etape est DERIVEE a la lecture (fin du passage precedent, depot du
 * dossier, ou sortie d'attente PRMP — cf. {@code ChronometrageService}), et la duree s'en deduit.</p>
 *
 * <p>Append-only : une etape rejouee (reexamen, nouvelle navette de visa, passage supplementaire dans
 * la boucle FAVR) cree une ligne de plus, jamais une mise a jour de la precedente. C'est ce qui rend
 * visible le nombre d'aller-retours, information que le chronometrage existe precisement pour donner.</p>
 */
@Entity
@Table(name = "t_tache_dossier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TacheDossier {

    /**
     * ⚠️ Audit 2026-09-14 (C3) — longueurs des colonnes texte, declarees UNE fois : l'annotation
     * {@code @Column} et la validation prealable de {@code ChronometrageService} lisent la meme
     * constante, pour que la garde ne puisse pas diverger du schema. {@code IM_ACTEUR} passe a 10 (V29) :
     * l'acteur d'un passage peut etre une PRMP ({@code ID_PRMP varchar(10)}), pas seulement un controleur.
     */
    public static final int LONGUEUR_ETAPE = 30;
    public static final int LONGUEUR_IM_ACTEUR = 10;
    public static final int LONGUEUR_PROFIL = 30;

    @Id
    @Column(name = "ID_TACHE", nullable = false)
    private Integer idTache;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    /** Valeur de {@code cnm.prs.enums.EtapeCircuit}, stockee en texte. */
    @Column(name = "ETAPE", nullable = false, length = LONGUEUR_ETAPE)
    private String etape;

    /** Rang de l'occurrence pour ce dossier et cette etape (1 = premier passage). */
    @Column(name = "OCCURRENCE", nullable = false)
    private Integer occurrence;

    /**
     * Matricule du controleur, ou identifiant de la PRMP (resoumission, rectification), a qui l'etape
     * revenait ; nul si aucun acteur n'est identifiable.
     */
    @Column(name = "IM_ACTEUR", length = LONGUEUR_IM_ACTEUR)
    private String imActeur;

    /** Profil sous lequel l'etape a ete tenue (delegation ou interim compris). */
    @Column(name = "PROFIL", length = LONGUEUR_PROFIL)
    private String profil;

    /**
     * Instant ou l'etape s'est TERMINEE — le geste metier de cloture. Jamais nul : une ligne n'existe
     * que parce qu'un passage s'est acheve (2026-09-12). L'entree, elle, n'est pas stockee.
     */
    @Column(name = "DATE_FIN", nullable = false)
    private LocalDateTime dateFin;

    /**
     * ⚠️ Interim designe (V34, 2026-09-21) — passage tenu PAR INTERIM de ce titulaire : {@code imActeur} reste
     * l'interimaire (celui qui a agi), {@code profil} le profil du titulaire (celui sous lequel l'etape a ete
     * tenue). Meme longueur que {@code IM_ACTEUR}, relue par la garde prealable.
     */
    @Column(name = "INTERIM_DE", length = LONGUEUR_IM_ACTEUR)
    private String interimDe;

    @Column(name = "ID_INTERIM")
    private Integer idInterim;
}
