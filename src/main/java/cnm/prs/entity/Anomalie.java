package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_anomalie}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 *
 * <p>⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, migration V32) — cette table porte
 * désormais les <strong>signalements</strong> du pré-contrôle : ce que la PRMP doit regarder avant de
 * soumettre son plan, et le contrôleur avant de l'examiner. Le MLD avait prévu l'essentiel
 * ({@code STATUT} + {@code COMMENTAIRE_TRAITEMENT} = « écarter en motivant ») ; V32 y a ajouté de quoi
 * enregistrer une PRMP comme auteur d'un écartement, rattacher un signalement à la grille de contrôle et
 * le retrouver d'une exécution à l'autre.</p>
 *
 * <p><strong>Les colonnes de vocabulaire restent des chaînes</strong>, comme le MLD les a générées, et
 * non des énumérations : la ressource {@code /api/anomalies} est une CRUD administrateur dont le contrat
 * est inchangé. Les valeurs admises sont celles de {@link cnm.prs.enums.GraviteSignalement},
 * {@link cnm.prs.enums.SourceSignalement} et {@link cnm.prs.enums.StatutSignalement} — et, depuis V32,
 * des contraintes {@code CHECK} en base les ferment pour les écritures nouvelles.</p>
 */
@Entity
@Table(name = "t_anomalie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Anomalie {

    @Id
    @Column(name = "ID_ANOMALIE", nullable = false)
    private Integer idAnomalie;

    @Column(name = "ID_DETAIL")
    private Integer idDetail;

    @Column(name = "ID_PPM")
    private Integer idPpm;

    @Column(name = "ID_REGLE_ANOMALIE", nullable = false)
    private Integer idRegleAnomalie;

    @Column(name = "TYPE_ANOMALIE", length = 50)
    private String typeAnomalie;

    /** ⚠️ Élargie de 10 à 20 caractères par V32 : « PRIORITAIRE » en fait 11. */
    @Column(name = "GRAVITE", length = 20)
    private String gravite;

    @Column(name = "DESCRIPTION", columnDefinition = "text")
    private String description;

    @Column(name = "DATE_DETECTION")
    private LocalDateTime dateDetection;

    @Column(name = "SOURCE", length = 20)
    private String source;

    @Column(name = "STATUT", length = 20)
    private String statut;

    /**
     * ⚠️ Élargie de 7 à 10 caractères par V32, et sa FK vers {@code tr_controleur} retirée : un
     * {@code ID_PRMP} fait 10 caractères, et aucune PRMP ne figure dans {@code tr_controleur} — sans quoi
     * elle n'aurait jamais pu être enregistrée comme ayant écarté un signalement. Porte donc la référence
     * de <strong>n'importe quel</strong> acteur, et {@link #typeActeurTraitement} dit laquelle.
     */
    @Column(name = "IM_TRAITEMENT", length = 10)
    private String imTraitement;

    /**
     * ⚠️ V32 — {@code CONTROLEUR} ou {@code PRMP} ({@link cnm.prs.enums.TypeActeur}), comme la claim
     * {@code acteurType} du jeton. Un agent d'UGPM est enregistré comme sa <strong>PRMP de
     * tutelle</strong> : c'est son {@code ID_PRMP} que porte le jeton (claim {@code ref}), et le périmètre
     * d'une UGPM est celui de sa tutelle.
     */
    @Column(name = "TYPE_ACTEUR_TRAITEMENT", length = 20)
    private String typeActeurTraitement;

    @Column(name = "DATE_TRAITEMENT")
    private LocalDateTime dateTraitement;

    /** Motif de l'écartement — <strong>obligatoire</strong> quand un signalement est écarté (3.f). */
    @Column(name = "COMMENTAIRE_TRAITEMENT", columnDefinition = "text")
    private String commentaireTraitement;

    /**
     * ⚠️ V32 — point de la grille de contrôle du PPM ({@code tr_points_ctrl}) que le signalement éclaire
     * (« Mode de passation conforme », « Conformité de la désignation »…). Le contrôleur le trouve ainsi
     * là où il travaille déjà. {@code null} tant qu'aucun point ne correspond.
     */
    @Column(name = "ID_POINT_CTRL")
    private Integer idPointCtrl;

    /**
     * ⚠️ V32 — <strong>identité stable</strong> du signalement dans son PPM : code de règle + objet visé,
     * normalisé. Une nouvelle exécution du pré-contrôle <strong>retrouve</strong> le signalement au lieu
     * d'en créer un double — donc son écartement et son motif —, et repère celui qui a disparu après
     * modification du plan (unique par {@code ID_PPM}).
     */
    @Column(name = "CLE_SIGNALEMENT", length = 200)
    private String cleSignalement;

    /**
     * ⚠️ V32 — correction proposée, rédigée comme l'annexe d'un PV (« Au lieu de : … Lire : … ») pour que
     * le contrôleur puisse la reprendre, la modifier ou l'ignorer. C'est lui qui écrit le PV.
     */
    @Column(name = "SUGGESTION", columnDefinition = "text")
    private String suggestion;

    /** ⚠️ V32 — date à laquelle le signalement a cessé de ressortir, le plan ayant changé. */
    @Column(name = "DATE_LEVEE")
    private LocalDateTime dateLevee;

    /**
     * ⚠️ V32 — ce qui a changé dans le plan et a fait disparaître le signalement. C'est ce que le
     * contrôleur lit quand une PRMP a fait taire l'alarme en modifiant ses lignes : rien ne s'efface.
     */
    @Column(name = "DETAIL_LEVEE", columnDefinition = "text")
    private String detailLevee;

    /**
     * ⚠️ V32 — vrai dès la soumission du plan : un écartement figé ne se défait plus (3.f, condition 3).
     * Jamais {@code null} en base (défaut {@code false}).
     */
    @Column(name = "FIGE", nullable = false)
    private Boolean fige = Boolean.FALSE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DETAIL", insertable = false, updatable = false)
    @JsonIgnore
    private Marche detail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PPM", insertable = false, updatable = false)
    @JsonIgnore
    private Ppm ppm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_REGLE_ANOMALIE", insertable = false, updatable = false)
    @JsonIgnore
    private RegleAnomalie regleAnomalie;

    /**
     * ⚠️ V32 — l'association vers {@code tr_controleur} a été <strong>retirée</strong> avec la FK :
     * {@link #imTraitement} peut désormais porter un {@code ID_PRMP}, qui n'a aucune ligne dans
     * {@code tr_controleur}. Le nom de l'acteur se résout par son type
     * ({@link #typeActeurTraitement}), jamais par une jointure. Elle n'était lue nulle part.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_POINT_CTRL", insertable = false, updatable = false)
    @JsonIgnore
    private PointsCtrl pointCtrl;

    /** Jamais {@code null} : colonne ajoutée avec un défaut {@code false} (V32). */
    public Boolean getFige() {
        return fige != null && fige;
    }
}
