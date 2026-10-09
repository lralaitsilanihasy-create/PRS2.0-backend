package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M4, §B4 ; V97) — un motif-type de la conclusion : motif de <strong>renvoi</strong>
 * (lettre de demande de compléments) ou d'<strong>avis défavorable</strong>, commun à une famille ({@code idSousType} nul) ou propre à un
 * sous-type. Le front insère {@link #texte} dans le projet de PV ou de lettre, que le Membre modifie ensuite.
 */
@Entity
@Table(name = "tr_motif_type")
@Getter
@Setter
@NoArgsConstructor
public class MotifType {

    public static final String RENVOI = "RENVOI";
    public static final String AVIS_DEFAVORABLE = "AVIS_DEFAVORABLE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_MOTIF", nullable = false)
    private Integer idMotif;

    @Column(name = "ID_TYPE_DOSSIER", nullable = false, length = 10)
    private String idTypeDossier;

    @Column(name = "ID_SOUS_TYPE", length = 20)
    private String idSousType;

    /** {@link #RENVOI} ou {@link #AVIS_DEFAVORABLE}. */
    @Column(name = "NATURE", nullable = false, length = 20)
    private String nature;

    @Column(name = "LIBELLE", nullable = false, length = 200)
    private String libelle;

    @Column(name = "TEXTE", nullable = false, columnDefinition = "text")
    private String texte;

    @Column(name = "ORDRE")
    private Integer ordre;

    /** Condition : catégorie de la fiche (nul : toutes). */
    @Column(name = "CATEGORIE", length = 40)
    private String categorie;

    /** Condition : {@code CONTRAT_CADRE}, {@code AUTRE} (nul : toutes formes). */
    @Column(name = "FORME", length = 20)
    private String forme;

    @Column(name = "ACTIF", nullable = false)
    private Boolean actif = Boolean.TRUE;
}
