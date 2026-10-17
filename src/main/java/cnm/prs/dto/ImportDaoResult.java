package cnm.prs.dto;

import java.util.List;

/**
 * ⚠️ Import du DAO (demande front du 2026-09-28, §B1 ; ADR-0012) — ce que la lecture d'un DAO propose pour une fiche
 * marché, sans rien écrire. La PRMP retient des lignes, que {@code PUT …/import/appliquer} écrit.
 *
 * @param fichier        le nom du fichier lu (il n'est pas conservé)
 * @param empreinte      son SHA-256, en hexadécimal minuscule
 * @param modeles        chaque modèle du lot D cherché dans le fichier : unités du modèle, unités reconnues
 * @param cadrage        les réponses de cadrage déduites des rédactions retenues (ou lues sur un reflet)
 * @param propositions   les valeurs lues, dans la forme de saisie, passées par la validation de la saisie
 * @param ambigus        plusieurs champs possibles pour un même passage : signalés, jamais choisis
 * @param divergences    un champ repris du plan que le document dit autrement (le plan fait foi)
 * @param conflits       un champ lu deux fois différemment
 * @param nonTrouves     les champs saisissables attendus par les modèles et non proposés
 * @param avertissements « hors gabarit » (moins de 30 % d'un modèle reconnu), réponses de cadrage refusées…
 * @param passages       ⚠️ 2026-10-03 (lecture par clause) — les passages de listes (MATERIEL, PERSONNEL, PIECES) repérés
 *                       dans les données particulières d'un DPAO, à proposer dans « Coller une liste » ; jamais appliqués
 * @param articles       ⚠️ 2026-10-16 (« import-specifications », §B1) — les articles lus dans les tableaux du DAO (bordereau, détail
 *                       quantitatif, spécifications, définition des prix) ; lecture seule : le front les écrit par {@code PUT …/articles}
 * @param identite       ⚠️ 2026-10-17 (« identite-dao-importe », §B1) — le verdict d'identité : le DAO est-il celui de la ligne du plan ?
 */
public record ImportDaoResult(String fichier, String empreinte, List<Modele> modeles, List<Cadrage> cadrage,
        List<Proposition> propositions, List<Ambigu> ambigus, List<Divergence> divergences, List<Conflit> conflits,
        List<String> nonTrouves, List<String> avertissements, List<Passage> passages, List<Article> articles,
        Identite identite) {

    /**
     * ⚠️ 2026-10-17 (§B1) — {@code verdict} : {@code CONFORME} (tous les critères retrouvés), {@code NON_PROUVE} (aucun contredit, un
     * introuvable : l'application exige l'attestation de la PRMP), {@code ETRANGER} (un critère contredit : l'application est refusée) ;
     * {@code voisine} : la ligne de la même autorité à laquelle l'objet ressemble nettement plus, ou autant ; {@code motif} : prêt à
     * afficher.
     */
    public record Identite(String verdict, List<Critere> criteres, Voisine voisine, String motif) {
    }

    /**
     * Un critère : {@code OBJET}, {@code AUTORITE}, {@code MODE}, {@code FORME} (contrat-cadre), {@code LOTS} (ligne allotie) ; {@code plan} :
     * la valeur du plan ; {@code etat} : {@code RETROUVE}, {@code CONTREDIT} ou {@code INTROUVABLE} ; {@code extrait} et {@code page} : le
     * passage du document (la page d'un PDF seulement).
     */
    public record Critere(String critere, String plan, String etat, String extrait, Integer page) {
    }

    public record Voisine(Integer idDetail, String objet) {
    }

    /**
     * ⚠️ 2026-10-16 (§B1, §B5) — un article lu. {@code lot} : le rang du lot au plan, nul sur une ligne non allotie ; {@code numero} :
     * tel qu'écrit (le n° de prix en travaux) ; {@code designationSpecification} : seulement quand elle diverge de celle du bordereau ;
     * {@code anomalies} ({@code LOT_HORS_PLAN}, {@code QUANTITES_INCOMPATIBLES}) : l'article ne peut pas être retenu tel quel.
     */
    public record Article(Integer lot, String numero, String designation, String unite, java.math.BigDecimal quantite,
            java.math.BigDecimal quantiteMin, java.math.BigDecimal quantiteMax, String serie, String serieLibelle,
            List<Caracteristique> caracteristiques, Confiance confiance, Pages pages, String designationSpecification,
            List<String> anomalies) {
    }

    public record Caracteristique(String libelle, String exigence) {
    }

    /** {@code haute} ou {@code moyenne} ; {@code caracteristiques} nul sans caractéristique. */
    public record Confiance(String article, String caracteristiques) {
    }

    /** Les pages du PDF où l'article a été lu (nulles pour un Word). */
    public record Pages(Integer bordereau, Integer specification) {
    }


    /** ⚠️ 2026-10-03 — un passage de liste : la liste visée, son texte (lignes séparées par {@code \n}), son paragraphe. */
    public record Passage(String liste, String texte, int paragraphe) {
    }

    public record Modele(String sigle, int unites, int reconnues) {
    }

    /** {@code section} : la section du modèle dont la rédaction dit la réponse ({@code null} : lue sur un reflet). */
    public record Cadrage(String cle, Object valeur, String section, Object actuelle) {
    }

    /**
     * {@code confiance} : {@code haute}, {@code moyenne} ou {@code basse} ; {@code extrait} : le paragraphe du document où
     * la valeur a été lue ; {@code actuelle} : la valeur déjà saisie ; {@code anomalies} : les refus de la validation de la
     * saisie (une proposition qui en porte n'est pas applicable telle quelle). ⚠️ 2026-10-03 — {@code source} :
     * {@code modele} (la lecture par le modèle) ou {@code clause} (trouvée par les mots-clés de sa clause, à vérifier).
     */
    public record Proposition(String code, Integer lot, String valeur, String brut, String confiance, String extrait,
            String actuelle, List<String> anomalies, String source) {
    }

    public record Ambigu(List<String> candidats, String texte) {
    }

    public record Divergence(String code, String document, String plan) {
    }

    public record Conflit(String code, List<String> valeurs) {
    }
}
