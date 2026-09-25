package cnm.prs.entity;

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

    @Column(name = "QUANTITE_MIN")
    private Integer quantiteMin;

    @Column(name = "QUANTITE_MAX")
    private Integer quantiteMax;

    @Column(name = "QUANTITE")
    private Integer quantite;

    @Column(name = "REDIGE_PAR", length = 50)
    private String redigePar;

    @Column(name = "PROFIL_REDACTEUR", length = 30)
    private String profilRedacteur;
}
