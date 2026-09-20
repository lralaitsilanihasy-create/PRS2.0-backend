package cnm.prs.entity;

import cnm.prs.enums.CategorieModePassation;
import cnm.prs.enums.ProcedureAttendue;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code tr_mode_passation}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "tr_mode_passation")
@Getter
@Setter
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
    /**
     * ⚠️ Règle précisée (2026-09-07, pilote) — l'AGPM est requis pour les procédures d'<strong>appel
     * d'offres</strong>, <em>toutes variantes</em> : ouvert, restreint, avec préqualification, en deux
     * étapes… et non le seul appel d'offres ouvert. Le drapeau {@code DECLENCHE_AGPM} reste la source de
     * vérité (référentiel administrable) ; cette méthode dit ce qu'il <strong>devrait</strong> valoir pour
     * un libellé donné, et sert à le poser sur un mode créé à la volée par un import (le PDF n'apporte
     * qu'un libellé). Les modes hors appel d'offres — consultation des prix, gré à gré, achat direct —
     * restent hors AGPM, de même que l'« appel à manifestation d'intérêt », qui n'est pas un appel d'offres.
     */
    public static boolean libelleDeclencheAgpm(String libelle) {
        String normalise = normaliserLibelle(libelle);
        return normalise.contains("appel") && (normalise.contains("offre") || normalise.contains("aoo")
                || normalise.contains("aor"));
    }

    /**
     * ⚠️ Arbitrage pilote (2026-09-07, suite) — l'<strong>appel à manifestation d'intérêt</strong> déclenche
     * l'AGPM lui aussi, mais <strong>sous condition de montant</strong> : il sort de l'exclusion posée par la
     * V21, sans pour autant devenir un déclencheur inconditionnel. Le drapeau que cette méthode dérive
     * ({@code AGPM_SI_SEUIL}) marque cette famille ; le <strong>seuil</strong>, lui, est un paramètre
     * administrable ({@code ParametreService.AGPM_SEUIL_MONTANT}) — la valeur vit dans l'administration,
     * jamais dans le code.
     *
     * <p>Sert, comme {@link #libelleDeclencheAgpm}, à poser le drapeau sur un mode créé à la volée par un
     * import PDF, qui n'apporte qu'un libellé.</p>
     */
    public static boolean libelleAgpmSiSeuil(String libelle) {
        String normalise = normaliserLibelle(libelle);
        return normalise.contains("manifestation") && normalise.contains("interet");
    }

    /** Minuscules sans accents — les libellés viennent aussi bien de la saisie que d'un PDF. */
    private static String normaliserLibelle(String libelle) {
        return libelle == null ? "" : java.text.Normalizer.normalize(libelle, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.FRENCH);
    }

    /**
     * ⚠️ Arbitrage pilote (2026-09-07, suite — V22) — déclenchement de l'AGPM <strong>conditionnel au
     * montant</strong> : un marché passé selon ce mode ne rend l'AGPM requis que si son montant estimé
     * atteint le seuil administrable. Porté par l'<strong>appel à manifestation d'intérêt</strong>.
     * Indépendant de {@link #declencheAgpm}, qui reste le déclenchement inconditionnel (appels d'offres).
     */
    @Column(name = "AGPM_SI_SEUIL")
    private Boolean agpmSiSeuil;

    @Column(name = "DECLENCHE_AGPM")
    private Boolean declencheAgpm;

    /**
     * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 2, migration V33) — <strong>palier de
     * l'arrêté n° 13 156/2019-MEF</strong> auquel ce mode appartient (art. 2, 2°). C'est le terme qui
     * manquait pour comparer le mode <strong>saisi</strong> à ce que le montant <strong>appelle</strong>.
     *
     * <p><strong>Administrable</strong>, comme {@link #declencheAgpm} : la valeur initiale est posée au
     * démarrage d'après le libellé ({@link #procedureSeuilDepuisLibelle}), mais c'est la colonne qui fait
     * foi. {@code null} = mode non classé : la règle reste <strong>muette</strong> pour lui, elle ne
     * devine pas.</p>
     *
     * <p>Ne détermine rien : le mode reste purement saisi, et l'écart se signale, il ne se refuse pas.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "PROCEDURE_SEUIL", length = 30)
    private ProcedureAttendue procedureSeuil;

    /**
     * ⚠️ Étape 2 — palier <strong>déduit du libellé</strong>, pour poser la valeur initiale de
     * {@link #procedureSeuil} sur les modes qui n'en portent pas encore (dont ceux créés à la volée par un
     * import PDF, qui n'apporte qu'un libellé). Rend {@code null} sur un libellé non reconnu : mieux vaut
     * un mode non classé, donc muet, qu'un mode mal classé, qui produirait un signalement injuste.
     *
     * <ul>
     *   <li><strong>Appel d'offres</strong>, toutes variantes (ouvert, restreint, avec préqualification,
     *       en deux étapes) → {@code APPEL_OFFRES_OUVERT} : ce sont les procédures du palier haut. Les
     *       variantes restreintes relèvent des exceptions des articles 38 et 39 du code des marchés
     *       publics, dont la justification est un point de la fiche de présentation — ce n'est pas à la
     *       règle des seuils de les relever ;</li>
     *   <li><strong>consultation</strong> (de prix, d'entrepreneurs, de fournisseurs, ouverte ou
     *       restreinte) → {@code CONSULTATION} ; l'<strong>appel à manifestation d'intérêt</strong>, qui
     *       n'est pas un appel d'offres, y est rangé aussi : c'est la procédure des prestations
     *       intellectuelles, pour lesquelles l'arrêté ne fixe pas de seuil de procédure ;</li>
     *   <li><strong>achat direct</strong>, bon de commande, gré à gré, entente directe → {@code ACHAT_DIRECT}.</li>
     * </ul>
     */
    public static ProcedureAttendue procedureSeuilDepuisLibelle(String libelle) {
        String n = normaliserLibelle(libelle);
        if (n.isBlank()) {
            return null;
        }
        if (libelleDeclencheAgpm(libelle)) {
            return ProcedureAttendue.APPEL_OFFRES_OUVERT;
        }
        if (n.contains("consultation") || libelleAgpmSiSeuil(libelle)) {
            return ProcedureAttendue.CONSULTATION;
        }
        if (n.contains("achat direct") || n.contains("bon de commande") || n.contains("gre a gre")
                || n.contains("entente directe")) {
            return ProcedureAttendue.ACHAT_DIRECT;
        }
        return null;
    }

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
