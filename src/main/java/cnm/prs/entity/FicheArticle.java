package cnm.prs.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ V45 (demande front du 2026-09-25, formulaires du candidat, §B1) — un <strong>article du besoin</strong> d'une
 * version de fiche DAO ({@code t_fiche_article}), dans un lot du plan (rang ; {@code null} pour une ligne non allotie).
 * Quantités minimum et maximum pour un marché à commande, quantité pour la quantité fixe et le contrat-cadre.
 * {@code redigePar} / {@code profilRedacteur} réservent la place du rédacteur (service bénéficiaire, à confirmer) :
 * renseignés, jamais exigés.
 */
@Entity
@Table(name = "t_fiche_article")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FicheArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ARTICLE", nullable = false)
    private Integer idArticle;

    @Column(name = "ID_FICHE", nullable = false)
    private Integer idFiche;

    @Column(name = "LOT")
    private Integer lot;

    @Column(name = "ORDRE", nullable = false)
    private Integer ordre;

    @Column(name = "DESIGNATION", nullable = false, length = 500)
    private String designation;

    @Column(name = "UNITE", nullable = false, length = 20)
    private String unite;

    // ⚠️ V59 (2026-10-02, DQE des travaux) — quantités à deux décimales.
    @Column(name = "QUANTITE_MIN", precision = 15, scale = 2)
    private BigDecimal quantiteMin;

    @Column(name = "QUANTITE_MAX", precision = 15, scale = 2)
    private BigDecimal quantiteMax;

    @Column(name = "QUANTITE", precision = 15, scale = 2)
    private BigDecimal quantite;

    @Column(name = "REDIGE_PAR", length = 50)
    private String redigePar;

    @Column(name = "PROFIL_REDACTEUR", length = 30)
    private String profilRedacteur;

    // ⚠️ V59 (2026-10-02, DQE des travaux) — ce qu'un article de travaux porte de plus ; hors travaux : null / false.
    @Column(name = "NUMERO_PRIX", length = 10)
    private String numeroPrix;

    @Column(name = "SERIE", length = 10)
    private String serie;

    @Column(name = "SERIE_LIBELLE", length = 200)
    private String serieLibelle;

    @Column(name = "LIBELLE_BORDEREAU", length = 200)
    private String libelleBordereau;

    @Column(name = "SOUS_DETAIL", nullable = false)
    private boolean sousDetail;

    @Column(name = "PLAFOND", precision = 5, scale = 2)
    private BigDecimal plafond;
}
