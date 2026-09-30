package cnm.prs.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * ⚠️ <strong>Lot D — le DAO complet sur les documents types officiels</strong> (demande front du 2026-09-28 ; ADR-0011) —
 * les fichiers de commande des documents du DAO, lus une fois au démarrage depuis {@code classpath:modeles/dao/<sigle>.txt}
 * (copies telles quelles de {@code frontendprs2/scripts/modeles-dao/modeles/}). Premier périmètre : le contrat-cadre,
 * fournitures et services — {@code DPAC-CC} (données particulières d'appel à concurrence) et {@code AE-CC} (contrat-cadre
 * valant acte d'engagement et CCAP).
 *
 * <p><strong>Refus au démarrage</strong>, fichier nommé : fichier absent ou illisible ; condition déclarée illisible ;
 * section {@code {{SI:…}}} utilisée sans être déclarée ou mal emboîtée ({@link ConditionsModele#defauts}) ; jeton
 * {@code {{INT-…}}} (paramètre interne de la procédure, V50). Jamais « vrai » en silence.</p>
 */
@Component
public class ModelesDao {

    /** Un document produit depuis un modèle : type de document, forme de marché et catégorie qu'il couvre. */
    public record Couverture(String sigle, String typeDocument, String typeMarche, String categorie) {
    }

    /** Les modèles décrits, dans l'ordre de production. Les autres formes restent au lot 2a (liste « libellé : valeur »). */
    public static final List<Couverture> COUVERTURES = List.of(
            new Couverture("DPAC-CC", "DPAC", "CONTRAT_CADRE", "FOURNITURES_SERVICES"),
            new Couverture("AE-CC", "AE", "CONTRAT_CADRE", "FOURNITURES_SERVICES"),
            // ⚠️ Lot D2 (2026-09-29, §B3) — fournitures et services, quantité fixe et à commande : un modèle par document,
            // les passages propres à une forme sous condition typeMarche.
            new Couverture("DPAO-F", "DPAO", "QUANTITE_FIXE", "FOURNITURES_SERVICES"),
            new Couverture("CCAP-F", "CCAP", "QUANTITE_FIXE", "FOURNITURES_SERVICES"),
            new Couverture("AE-F", "AE", "QUANTITE_FIXE", "FOURNITURES_SERVICES"),
            new Couverture("DPAO-F", "DPAO", "A_COMMANDE", "FOURNITURES_SERVICES"),
            new Couverture("CCAP-F", "CCAP", "A_COMMANDE", "FOURNITURES_SERVICES"),
            new Couverture("AE-F", "AE", "A_COMMANDE", "FOURNITURES_SERVICES"),
            // ⚠️ Lot D3 (2026-09-29, §B1) — prestations intellectuelles, quantité fixe et à commande : le DPIC (le tableau
            // 1.3 seul, à la place du DPAO — remappage V42), l'AE, et le CPS qui tient le rôle du CCAP.
            new Couverture("DPIC-PI", "DPIC", "QUANTITE_FIXE", "PRESTATIONS_INTELLECTUELLES"),
            new Couverture("CPS-PI", "CCAP", "QUANTITE_FIXE", "PRESTATIONS_INTELLECTUELLES"),
            new Couverture("AE-PI", "AE", "QUANTITE_FIXE", "PRESTATIONS_INTELLECTUELLES"),
            new Couverture("DPIC-PI", "DPIC", "A_COMMANDE", "PRESTATIONS_INTELLECTUELLES"),
            new Couverture("CPS-PI", "CCAP", "A_COMMANDE", "PRESTATIONS_INTELLECTUELLES"),
            new Couverture("AE-PI", "AE", "A_COMMANDE", "PRESTATIONS_INTELLECTUELLES"),
            // ⚠️ Lot D4 (2026-09-29, §B1) — travaux, quantité fixe et à commande (le document type n'a pas de variante « à
            // commande ») : DPAO, CCAP et ses six annexes, AE.
            new Couverture("DPAO-T", "DPAO", "QUANTITE_FIXE", "TRAVAUX"),
            new Couverture("CCAP-T", "CCAP", "QUANTITE_FIXE", "TRAVAUX"),
            new Couverture("AE-T", "AE", "QUANTITE_FIXE", "TRAVAUX"),
            new Couverture("DPAO-T", "DPAO", "A_COMMANDE", "TRAVAUX"),
            new Couverture("CCAP-T", "CCAP", "A_COMMANDE", "TRAVAUX"),
            new Couverture("AE-T", "AE", "A_COMMANDE", "TRAVAUX"),
            // ⚠️ Lot D4 (§B3) — le contrat-cadre de travaux sur le même document type que celui des fournitures : ses choix
            // « CCAG Fournitures / CCAG Travaux » se font par la catégorie, et ses codes sont ceux du contrat-cadre.
            new Couverture("DPAC-CC", "DPAC", "CONTRAT_CADRE", "TRAVAUX"),
            new Couverture("AE-CC", "AE", "CONTRAT_CADRE", "TRAVAUX"));

    private final Map<String, FichierCommande.Modele> modeles = new LinkedHashMap<>();

    public ModelesDao() {
        for (Couverture c : COUVERTURES) {
            if (modeles.containsKey(c.sigle())) {
                continue;   // un modèle sert plusieurs formes (lot D2)
            }
            String chemin = "/modeles/dao/" + c.sigle() + ".txt";
            modeles.put(c.sigle(), charger(chemin, lireRessource(chemin)));
        }
    }

    private String lireRessource(String chemin) {
        try (InputStream in = getClass().getResourceAsStream(chemin)) {
            if (in == null) {
                throw new IllegalStateException("Modèle du DAO absent des ressources : " + chemin);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Modèle du DAO illisible : " + chemin + " — " + e.getMessage(), e);
        }
    }

    /** Lit et contrôle un fichier de commande du DAO ; {@link IllegalStateException} nominative au premier défaut. */
    static FichierCommande.Modele charger(String chemin, String texte) {
        ModelesCandidat.verifierJetonsInternes(chemin, texte);
        FichierCommande.Modele m;
        try {
            m = FichierCommande.lireModele(texte);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Modèle du DAO illisible : " + chemin + " — " + e.getMessage(), e);
        }
        List<String> defauts = ConditionsModele.defauts(m.elements(), m.conditions(), Set.of());
        if (!defauts.isEmpty()) {
            throw new IllegalStateException("Modèle du DAO refusé : " + chemin + " — " + String.join(" ; ", defauts));
        }
        return m;
    }

    /** Le modèle d'un sigle ({@code DPAC-CC}…), ou {@code null}. */
    public FichierCommande.Modele modele(String sigle) {
        return modeles.get(sigle);
    }

    /** Tous les modèles, par sigle. */
    public Map<String, FichierCommande.Modele> modeles() {
        return modeles;
    }

    /** Les documents décrits pour une forme et une catégorie (à défaut de catégorie : fournitures et services). */
    public static List<Couverture> couvertures(String typeMarche, String categorie) {
        String cat = categorie == null ? "FOURNITURES_SERVICES" : categorie;
        return COUVERTURES.stream().filter(c -> c.typeMarche().equals(typeMarche) && c.categorie().equals(cat)).toList();
    }
}
