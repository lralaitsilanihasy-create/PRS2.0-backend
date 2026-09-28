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
            new Couverture("AE-CC", "AE", "CONTRAT_CADRE", "FOURNITURES_SERVICES"));

    private final Map<String, FichierCommande.Modele> modeles = new LinkedHashMap<>();

    public ModelesDao() {
        for (Couverture c : COUVERTURES) {
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
