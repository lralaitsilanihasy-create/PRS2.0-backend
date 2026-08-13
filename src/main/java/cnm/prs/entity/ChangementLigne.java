package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import cnm.prs.enums.TypeChangementLigne;

/**
 * ⚠️ Règle ajoutée (2026-08-05, mise à jour des PPM) — <strong>trace figée</strong> du diff entre une
 * version de PPM et son prédécesseur, au niveau de la LIGNE de marché.
 *
 * <p>Le diff est <em>calculé</em> à la demande tant que la nouvelle version est un brouillon (l'aperçu
 * doit refléter la saisie en cours) ; il est <strong>figé ici à la soumission</strong>, moment où la
 * version devient opposable. La table est <strong>append-only</strong> : on n'y modifie ni n'y supprime
 * rien, même si une version ultérieure revient sur le changement — même principe que
 * {@code t_suivi_observation}.</p>
 *
 * <p>Une ligne MODIFIÉE produit <strong>une entrée par champ</strong> changé ({@link #champ},
 * {@link #valeurAvant}, {@link #valeurApres}) ; les autres types produisent une entrée unique sans champ.</p>
 */
@Entity
@Table(name = "t_changement_ligne", indexes = {
        @Index(name = "idx_changement_dossier", columnList = "ID_DOSSIER"),
        @Index(name = "idx_changement_ligne_origine", columnList = "ID_LIGNE_ORIGINE"),
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangementLigne {

    @Id
    @Column(name = "ID_CHANGEMENT", nullable = false)
    private Integer idChangement;

    /** Dossier de la version qui PORTE le changement (le successeur), jamais le prédécesseur. */
    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    /** Identité stable de la ligne à travers les versions (cf. {@code Marche.idLigneOrigine}). */
    @Column(name = "ID_LIGNE_ORIGINE", nullable = false)
    private Integer idLigneOrigine;

    /** Désignation de la ligne au moment du figeage — lisible même si la ligne évolue ensuite. */
    @Column(name = "DESIGNATION", length = 500)
    private String designation;

    @Column(name = "TYPE_CHANGEMENT", length = 20, nullable = false)
    private String typeChangement;

    /** Champ modifié (ex. {@code montEstim}) — {@code null} hors {@link TypeChangementLigne#MODIFIEE}. */
    @Column(name = "CHAMP", length = 50)
    private String champ;

    @Column(name = "VALEUR_AVANT", length = 500)
    private String valeurAvant;

    @Column(name = "VALEUR_APRES", length = 500)
    private String valeurApres;
}
