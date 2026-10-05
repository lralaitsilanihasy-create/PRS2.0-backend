package cnm.prs.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * ⚠️ 2026-10-05 (demande front « soumission en ligne, lot 5 : l'offre saisie dans des formulaires », §B1) — le <strong>besoin
 * servi au candidat</strong> ({@code GET /api/procedures-en-ligne/{idDmc}/besoin}), lu sur la version validée en vigueur de la
 * fiche : de quoi pré-remplir le bordereau, le DQE, la conformité, le calendrier et (lot 5b) les capacités. {@code formulaires} est
 * faux quand la fiche n'a pas de besoin (prestations intellectuelles, fiche antérieure à V45) : dépôt par pièces seules.
 *
 * @param categorie   {@code FOURNITURES_SERVICES} · {@code TRAVAUX} · {@code PRESTATIONS_INTELLECTUELLES} (le nom servi par
 *                    {@code ProcedureEnLigneDto.categorie})
 * @param typeMarche  {@code QUANTITE_FIXE} · {@code A_COMMANDE} · {@code CONTRAT_CADRE}
 * @param tauxTva     {@code FICHE_TAUX_TVA}, en %, {@code null} s'il n'est pas fixé
 * @param materiel    travaux : le matériel exigé (V60), une liste par fiche ({@code parLot} : vaut pour chaque lot) ; vide ailleurs
 * @param personnel   travaux : le personnel clé exigé (V60)
 */
public record BesoinEnLigneDto(Long idDmc, String categorie, String typeMarche, boolean formulaires, BigDecimal tauxTva, String monnaie,
        List<Lot> lots, List<MaterielExigeDto> materiel, List<PersonnelExigeDto> personnel) {

    /**
     * Un lot (une seule entrée, {@code numero = null}, pour un marché non alloti), ses articles et les valeurs de fiche utiles au
     * formulaire, résolues pour <em>ce</em> lot ({@code CODE#n}, à défaut {@code CODE}). {@code qualification} : travaux seulement.
     */
    public record Lot(Integer numero, String intitule, List<Article> articles, String lieuLivraison, Delai delaiExecution,
            BigDecimal garantieSoumission, Qualification qualification) {
    }

    /** Un article du besoin : {@code quantite} (quantité fixe, contrat-cadre) ou {@code quantiteMin} / {@code quantiteMax} (à commande). */
    public record Article(Integer idArticle, Integer ordre, String designation, String unite, BigDecimal quantite, BigDecimal quantiteMin,
            BigDecimal quantiteMax, List<Caracteristique> caracteristiques, String numeroPrix, String serie, String serieLibelle,
            String libelleBordereau, boolean sousDetail, BigDecimal plafond) {
    }

    public record Caracteristique(Integer idCaracteristique, Integer ordre, String libelle, String exigence) {
    }

    /**
     * Le délai : {@code valeur} et {@code unite} ({@code JOURS}) quand la fiche le donne en nombre (fournitures : délai de livraison
     * {@code B06-EO-11} / {@code B06-EO-12}, à défaut {@code B09-DX-01}) ; {@code texte} quand elle le donne en clair (travaux :
     * {@code B09-DL-01}), {@code valeur} alors lue s'il commence par un nombre.
     */
    public record Delai(Integer valeur, String unite, String texte) {
    }

    /**
     * Les seuils de qualification des travaux (clauses {@code B03-QT-*}), typés : liquidité ({@code QT-14} montant, {@code QT-15}
     * pourcentage du montant de l'offre), chiffre d'affaires ({@code QT-07} montant, {@code QT-17} années, {@code QT-16} meilleures
     * années, {@code QT-18} domaine), références ({@code QT-20} montant cumulé, {@code QT-19} nombre de marchés cumulables,
     * {@code QT-12} période en années).
     */
    public record Qualification(BigDecimal liquiditeMontant, BigDecimal liquiditePourcentage, ChiffreAffaires chiffreAffaires,
            References references) {
    }

    public record ChiffreAffaires(BigDecimal montant, Integer annees, Integer meilleures, String domaine) {
    }

    /** {@code cumul} : vrai quand plusieurs marchés peuvent être cumulés ({@code nombre} > 1). */
    public record References(BigDecimal montant, Integer nombre, Integer annees, boolean cumul) {
    }
}
