package cnm.prs.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import org.springframework.stereotype.Component;

/**
 * ⚠️ V47 (formulaires du candidat, R12 (c)) — les six modèles officiels, lus une fois au démarrage depuis
 * {@code classpath:modeles/candidat/<sigle>.txt} (copies telles quelles des fichiers de commande du dépôt front,
 * {@code scripts/modeles-candidat/modeles-armp/}). Un fichier absent ou illisible empêche le démarrage : un modèle
 * réglementaire ne se devine pas.
 *
 * <p>⚠️ V50 (2026-09-27, remise électronique, §B2.2) — un fichier de commande qui porte un jeton {@code {{INT-…}}} (un
 * paramètre interne de la procédure : membres détenteurs de parts de clé, quorum, cérémonie) est <strong>refusé au
 * démarrage</strong>, fichier et ligne nommés : ces paramètres n'entrent dans aucun document.</p>
 */
@Component
public class ModelesCandidat {

    private final Map<String, List<DocumentLibre.Element>> modeles = new LinkedHashMap<>();

    public ModelesCandidat() {
        // ⚠️ 2026-10-02 (demande « référentiel des travaux routiers », §B5.2) — B1 / B2 : les garanties de soumission du
        // dossier type des travaux, rendues à la place de C1 / C2 pour une fiche de travaux.
        for (String sigle : List.of("A1", "A2", "A3", "A4", "C1", "C2", "B1", "B2")) {
            String chemin = "/modeles/candidat/" + sigle + ".txt";
            try (InputStream in = getClass().getResourceAsStream(chemin)) {
                if (in == null) {
                    throw new IllegalStateException("Modèle du candidat absent des ressources : " + chemin);
                }
                String texte = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                verifierJetonsInternes(chemin, texte);
                FichierCommande.Modele m = FichierCommande.lireModele(texte);
                // ⚠️ Lot D (2026-09-28) — même garde que les modèles du DAO ; les trois noms historiques restent admis.
                List<String> defauts = ConditionsModele.defauts(m.elements(), m.conditions(), FormulairesCandidat.SECTIONS_HISTORIQUES);
                if (!defauts.isEmpty()) {
                    throw new IllegalStateException("Modèle du candidat refusé : " + chemin + " — " + String.join(" ; ", defauts));
                }
                modeles.put(sigle, List.copyOf(m.elements()));
            } catch (IOException | IllegalArgumentException e) {
                throw new IllegalStateException("Modèle du candidat illisible : " + chemin + " — " + e.getMessage(), e);
            }
        }
    }

    /**
     * ⚠️ V50 (§B2.2) — refuse un fichier de commande qui porte un jeton visant un paramètre interne ({@code {{INT-…}}}) :
     * {@link IllegalStateException} nominative (fichier, ligne, jeton).
     */
    static void verifierJetonsInternes(String chemin, String texte) {
        String[] lignes = texte.split("\r?\n", -1);
        for (int i = 0; i < lignes.length; i++) {
            Matcher m = RemiseElectronique.JETON_INTERNE.matcher(lignes[i]);
            if (m.find()) {
                throw new IllegalStateException("Modèle du candidat refusé : " + chemin + ", ligne " + (i + 1) + " — le jeton {{"
                        + m.group(1).trim() + "}} vise un paramètre interne de la procédure, qui n'entre dans aucun document.");
            }
        }
    }

    /** Les éléments de chaque modèle, par sigle, dans l'ordre du document. */
    public Map<String, List<DocumentLibre.Element>> modeles() {
        return modeles;
    }
}
