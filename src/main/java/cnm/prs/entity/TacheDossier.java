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
 * Entite JPA mappee sur {@code t_tache_dossier} — une OCCURRENCE de tache chronometree.
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

    /** Matricule de l'acteur ; nul si la tache a ete ouverte par tolerance sans acteur identifiable. */
    @Column(name = "IM_ACTEUR", length = 7)
    private String imActeur;

    /** Profil sous lequel l'acteur agit (delegation ou interim compris) au moment de la prise en charge. */
    @Column(name = "PROFIL", length = 30)
    private String profil;

    @Column(name = "DATE_PRISE_EN_CHARGE", nullable = false)
    private LocalDateTime datePriseEnCharge;

    /** Nul tant que la tache est en cours ; posee par le geste metier de cloture. */
    @Column(name = "DATE_FIN")
    private LocalDateTime dateFin;

    @Column(name = "PREVISION_JOURS", nullable = false)
    private Integer previsionJours;

    /** Vrai si la prevision vient du referentiel administrable, faux si elle a ete saisie par le porteur. */
    @Column(name = "PREVISION_STANDARD", nullable = false)
    private Boolean previsionStandard = Boolean.FALSE;

    /** Tache encore ouverte : aucune date de fin. */
    public boolean enCours() {
        return dateFin == null;
    }
}
