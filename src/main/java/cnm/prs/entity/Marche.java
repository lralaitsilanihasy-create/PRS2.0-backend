package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cnm.prs.enums.FormeMarche;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité JPA mappée sur la table {@code t_marche}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 */
@Entity
@Table(name = "t_marche", indexes = {
        @Index(name = "idx_marche_dossier", columnList = "ID_DOSSIER"),
        @Index(name = "idx_marche_ppm", columnList = "ID_PPM")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Marche {

    @Id
    @Column(name = "ID_DETAIL", nullable = false)
    private Integer idDetail;

    /** Verrou optimiste (⚠️ LOT 4, 2026-08-26, migration V6) : une écriture concurrente perdante lève un 409 au lieu d'écraser. */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    /**
     * ⚠️ Règle ajoutée (2026-08-05, mise à jour des PPM) — <strong>identité de la ligne à travers les
     * versions</strong>. {@link #idDetail} n'identifie la ligne que DANS un dossier : une copie en v2
     * reçoit une nouvelle PK. {@code idLigneOrigine} vaut l'{@code idDetail} de la PREMIÈRE apparition de
     * la ligne et est hérité tel quel à chaque nouvelle version.
     *
     * <p>C'est la clé de rapprochement du diff entre deux versions. Elle ne dépend jamais de la position :
     * des lignes <strong>réordonnées sans changement de contenu</strong> ressortent donc « inchangées ».
     * Repli pour une ligne sans ancêtre (réimport PDF) : libellé normalisé + service bénéficiaire.</p>
     */
    @Column(name = "ID_LIGNE_ORIGINE")
    private Integer idLigneOrigine;

    /**
     * ⚠️ Règle ajoutée (2026-08-05) — <strong>suppression logique</strong> d'une ligne dans une version.
     * La ligne est bien recopiée dans la nouvelle version, marquée supprimée : elle reste visible,
     * <strong>restaurable</strong>, et n'est jamais effacée. Les documents officiels (PPM, annexe du PV)
     * doivent l'exclure.
     */
    @Column(name = "SUPPRIMEE")
    private Boolean supprimee = Boolean.FALSE;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "ID_PPM", nullable = false)
    private Integer idPpm;

    @Column(name = "DESIGNATION_MARCHE", length = 500)
    private String designationMarche;

    @Column(name = "NUM_COMPTE", length = 20)
    private String numCompte;

    @Column(name = "MONT_ESTIM")
    private BigDecimal montEstim;

    @Column(name = "ANCIEN_MONT_ESTIM")
    private BigDecimal ancienMontEstim;

    @Column(name = "NOUV_MONT_ESTIM")
    private BigDecimal nouvMontEstim;

    @Column(name = "FINANCEMENT", length = 20)
    private String financement;

    @Column(name = "STATUT", length = 20)
    private String statut;

    @Column(name = "ID_NATURE")
    private Integer idNature;

    @Column(name = "ID_MODE")
    private Integer idMode;

    /**
     * ⚠️ Règle ajoutée (2026-07-18) — forme du marché (à commande / contrat cadre / à quantité fixe).
     * Colonne nullable en base (ajout `ddl-auto=update` sur table existante) mais jamais {@code null}
     * côté Java : défaut {@link FormeMarche#QUANTITE_FIXE} à la création, getter coalescent, et reprise
     * des lignes historiques par {@code FormeMarcheMigration} (dérivée de la désignation).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "FORME_MARCHE", length = 20)
    private FormeMarche formeMarche = FormeMarche.QUANTITE_FIXE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_DOSSIER", insertable = false, updatable = false)
    @JsonIgnore
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PPM", insertable = false, updatable = false)
    @JsonIgnore
    private Ppm ppm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "NUM_COMPTE", insertable = false, updatable = false)
    @JsonIgnore
    private Compte compte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_MODE", insertable = false, updatable = false)
    @JsonIgnore
    private ModePassation mode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_NATURE", insertable = false, updatable = false)
    @JsonIgnore
    private Nature nature;

    /** Jamais {@code null} : les lignes historiques pas encore reprises lisent le défaut QUANTITE_FIXE. */
    public FormeMarche getFormeMarche() {
        return formeMarche == null ? FormeMarche.QUANTITE_FIXE : formeMarche;
    }

    /**
     * Jamais {@code null} : une ligne jamais versionnée est <strong>sa propre origine</strong>. Ce repli
     * dispense de reprendre les lignes historiques — colonne ajoutée par {@code ddl-auto=update} donc
     * {@code null} sur l'existant — tout en gardant la clé stable dès la première mise à jour.
     */
    public Integer getIdLigneOrigine() {
        return idLigneOrigine == null ? idDetail : idLigneOrigine;
    }

    /** Jamais {@code null} : colonne ajoutée sur table existante, {@code null} y vaut « non supprimée ». */
    public Boolean getSupprimee() {
        return supprimee != null && supprimee;
    }
}
