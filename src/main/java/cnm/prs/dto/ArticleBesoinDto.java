package cnm.prs.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ⚠️ V45 (demande front du 2026-09-25, formulaires du candidat, §B1) — un article du besoin d'une fiche DAO de
 * fournitures, avec ses caractéristiques exigées ({@code GET/PUT /api/fiches-marche/{idDmc}/articles}).
 *
 * <p>{@code lot} : rang du lot au plan ({@code null} sur une ligne non allotie). {@code ordre} : servi, numéro porté au
 * bordereau ; à l'écriture, c'est la <strong>position dans la liste</strong> qui fait foi (1, 2, 3… par lot). Quantités :
 * {@code quantiteMin} et {@code quantiteMax} pour un marché à commande, {@code quantite} pour la quantité fixe et le
 * contrat-cadre. {@code redigePar} / {@code profilRedacteur} : lecture seule, posés par le serveur.</p>
 *
 * <p>⚠️ V59 (2026-10-02, DQE des travaux, §B1.2) — quantités à deux décimales ; pour un article de <strong>travaux</strong> :
 * {@code numeroPrix} (unique dans le lot), {@code serie} et {@code serieLibelle} (le même pour toute la série du lot),
 * {@code libelleBordereau} (prix unitaires : « Le mètre cube »), {@code sousDetail}, {@code plafond} (au plus n % du
 * montant des travaux). Hors travaux, ces propriétés sont ignorées à l'écriture et servies vides.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleBesoinDto {

    private Integer idArticle;
    private Integer lot;
    private Integer ordre;
    private String designation;
    private String unite;
    private BigDecimal quantiteMin;
    private BigDecimal quantiteMax;
    private BigDecimal quantite;
    private String redigePar;
    private String profilRedacteur;
    private List<Caracteristique> caracteristiques;
    private String numeroPrix;
    private String serie;
    private String serieLibelle;
    private String libelleBordereau;
    private Boolean sousDetail;
    private BigDecimal plafond;

    /** Une exigence technique de l'article (« Mémoire vive » : « 8 Go au minimum »). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Caracteristique {
        private Integer idCaracteristique;
        private Integer ordre;
        private String libelle;
        private String exigence;
    }

    /** Corps de {@code PUT /api/fiches-marche/{idDmc}/articles}. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Remplacement {
        private List<ArticleBesoinDto> articles;
    }
}
