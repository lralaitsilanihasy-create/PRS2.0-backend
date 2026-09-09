package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ <strong>Référentiel « Statut de marché »</strong> (demande pilote du 2026-09-09) — table
 * {@code tr_statut_marche}.
 *
 * <p>{@code t_marche.STATUT} stockait un <strong>texte libre</strong> : en pratique toujours
 * {@code PREVU}, mais rien ne l'imposait ni ne permettait à l'Administrateur d'en ajouter. Le code
 * devient une valeur de référentiel, sur le moule des natures et des modes de passation.</p>
 *
 * <p>La <strong>clé est le code lui-même</strong> (et non un identifiant technique) : c'est lui que
 * {@code t_marche.STATUT} porte déjà, et le faire migrer vers un entier aurait imposé une reprise de
 * données à toutes les lignes existantes pour ne rien gagner.</p>
 */
@Entity
@Table(name = "tr_statut_marche")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StatutMarche {

    @Id
    @Column(name = "CODE", nullable = false, length = 20)
    private String code;

    @Column(name = "LIBELLE", nullable = false, length = 100)
    private String libelle;

    /** Rang d'affichage dans les listes déroulantes ; {@code null} se range en dernier. */
    @Column(name = "ORDRE")
    private Integer ordre;

    /**
     * Drapeau d'usage : un statut désactivé n'est plus <em>proposé</em>. ⚠️ Il reste <strong>accepté à
     * l'écriture</strong> — refuser un code déjà porté par des marchés empêcherait de ré-enregistrer une
     * ligne parfaitement légitime, ce qui ferait de la désactivation une opération destructrice.
     */
    @Column(name = "ACTIF", nullable = false)
    private Boolean actif;
}
