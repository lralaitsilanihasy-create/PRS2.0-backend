package cnm.prs.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * ⚠️ V47 (formulaires du candidat, R12 (c)) — les six modèles officiels, lus une fois au démarrage depuis
 * {@code classpath:modeles/candidat/<sigle>.txt} (copies telles quelles des fichiers de commande du dépôt front,
 * {@code scripts/modeles-candidat/modeles/}). Un fichier absent ou illisible empêche le démarrage : un modèle
 * réglementaire ne se devine pas.
 */
@Component
public class ModelesCandidat {

    private final Map<String, List<DocumentLibre.Element>> modeles = new LinkedHashMap<>();

    public ModelesCandidat() {
        for (String sigle : List.of("A1", "A2", "A3", "A4", "C1", "C2")) {
            String chemin = "/modeles/candidat/" + sigle + ".txt";
            try (InputStream in = getClass().getResourceAsStream(chemin)) {
                if (in == null) {
                    throw new IllegalStateException("Modèle du candidat absent des ressources : " + chemin);
                }
                modeles.put(sigle, List.copyOf(FichierCommande.lire(new String(in.readAllBytes(), StandardCharsets.UTF_8))));
            } catch (IOException | IllegalArgumentException e) {
                throw new IllegalStateException("Modèle du candidat illisible : " + chemin + " — " + e.getMessage(), e);
            }
        }
    }

    /** Les éléments de chaque modèle, par sigle, dans l'ordre du document. */
    public Map<String, List<DocumentLibre.Element>> modeles() {
        return modeles;
    }
}
