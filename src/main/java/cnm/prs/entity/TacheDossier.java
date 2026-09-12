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

    @Id
    @Column(name = "ID_TACHE", nullable = false)
    private Integer idTache;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    /** Valeur de {@code cnm.prs.enums.EtapeCircuit}, stockee en texte. */
    @Column(name = "ETAPE", nullable = false, length = 30)
    private String etape;

    /** Rang de l'occurrence pour ce dossier et cette etape (1 = premier passage). */
    @Column(name = "OCCURRENCE", nullable = false)
    private Integer occurrence;

    /** Matricule de l'acteur a qui l'etape revenait ; nul si aucun acteur n'est identifiable. */
    @Column(name = "IM_ACTEUR", length = 7)
    private String imActeur;

    /** Profil sous lequel l'etape a ete tenue (delegation ou interim compris). */
    @Column(name = "PROFIL", length = 30)
    private String profil;

    /**
     * Instant ou l'etape s'est TERMINEE — le geste metier de cloture. Jamais nul : une ligne n'existe
     * que parce qu'un passage s'est acheve (2026-09-12). L'entree, elle, n'est pas stockee.
     */
    @Column(name = "DATE_FIN", nullable = false)
    private LocalDateTime dateFin;
}
