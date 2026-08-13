package cnm.prs.entity;

import cnm.prs.enums.CategorieModePassation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité JPA mappée sur la table {@code tr_mode_passation}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "tr_mode_passation")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModePassation {

    @Id
    @Column(name = "ID_MODE", nullable = false)
    private Integer idMode;

    @Column(name = "LIBELLE", length = 100)
    private String libelle;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "PUBLICITE_REQUISE")
    private Boolean publiciteRequise;

    @Column(name = "DELAI_MIN_JOURS")
    private Integer delaiMinJours;

    @Column(name = "BASE_LEGALE", length = 200)
    private String baseLegale;

    /** Mapping (administrable) vers le type de DMC dérivé pour les marchés de ce mode. */
    @Column(name = "ID_TYPE_DMC")
    private Long idTypeDmc;

    /**
     * Marqueur <strong>administrable</strong> « appel d'offres ouvert » : si vrai, tout marché de ce mode
     * déclenche l'exigence d'un AGPM (Avis Général de Passation de Marché) sur le PPM. Détection
     * déterministe et data-driven (l'admin coche le(s) mode(s) concerné(s)), jamais par mot-clé de libellé.
     * {@code null} = false.
     */
    @Column(name = "DECLENCHE_AGPM")
    private Boolean declencheAgpm;

    /**
     * ⚠️ Règle ajoutée (2026-08-13) — <strong>catégorie</strong> du mode : {@code NORMAL} (droit commun)
     * ou {@code DEROGATOIRE}. Purement déclaratif (comme {@code publiciteRequise}), administrable via
     * l'écran référentiel. {@code null} = non classé (les modes créés à l'import naissent non classés).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORIE", length = 20)
    private CategorieModePassation categorie;

    /**
     * ⚠️ Règle ajoutée — <strong>modèle CAPM partagé</strong> : mode dont ce mode réutilise le modèle
     * détaillé de processus CAPM (ex. « Consultation des prix ouverte » et « Appel à manifestation
     * d'intérêt » → modèle « Appel d'offres ouvert »). {@code null} = pas de partage (ses propres
     * processus spécifiques, sinon les communs). Administrable (écran admin des modes).
     */
    @Column(name = "ID_MODE_MODELE_CAPM")
    private Integer idModeModeleCapm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_TYPE_DMC", insertable = false, updatable = false)
    private TypeDmc typeDmc;

    /** Constructeur de compatibilité (champs métier historiques, sans le mapping DMC). */
    public ModePassation(Integer idMode, String libelle, String description, Boolean publiciteRequise,
            Integer delaiMinJours, String baseLegale) {
        this.idMode = idMode;
        this.libelle = libelle;
        this.description = description;
        this.publiciteRequise = publiciteRequise;
        this.delaiMinJours = delaiMinJours;
        this.baseLegale = baseLegale;
    }
}
