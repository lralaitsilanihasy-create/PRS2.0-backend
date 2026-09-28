package cnm.prs.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ⚠️ V47 (formulaires du candidat, R12 (c)) — lecteur du <strong>fichier de commande</strong> écrit par la chaîne de
 * décalque du front ({@code scripts/modeles-candidat/modeles/<sigle>.txt}, format décrit en tête de son
 * {@code Decalque.java}) : un enregistrement par ligne, une tabulation entre le type et le texte.
 *
 * <pre>
 *   FICHIER      nom du fichier (ignoré)
 *   TITRE        centré, gras            SOUS_TITRE   gras, à gauche
 *   PARA         justifié                CENTRE       centré
 *   DROITE       aligné à droite         VIDE         paragraphe vide
 *   CONDITION    NOM US expression (lot D, 2026-09-28) — une condition déclarée, lue par {@link ConditionsModele}
 *   TABLE n      ouvre un tableau à n colonnes ; LIGNE c1 US c2 … (US = 0x1F) ; RS (0x1E) = saut de
 *                paragraphe dans une cellule ; FIN_TABLE le referme
 * </pre>
 *
 * Le texte est posé tel quel : le fichier est copié du dépôt front sans être retapé, et le comparateur de fidélité du
 * front ({@code verifier.mjs}) juge le docx que le serveur en rend.
 */
public final class FichierCommande {

    private static final char US = '\u001F';
    private static final char RS = '\u001E';

    private FichierCommande() {
    }

    /**
     * ⚠️ Lot D (2026-09-28, §B1) — un fichier de commande lu : ses éléments et ses <strong>conditions déclarées</strong>
     * ({@code CONDITION<TAB>NOM<US>expression}, en tête), dans l'ordre de déclaration.
     */
    public record Modele(List<DocumentLibre.Element> elements, Map<String, String> conditions) {
    }

    /** Les seuls éléments (les conditions déclarées sont lues et écartées). */
    public static List<DocumentLibre.Element> lire(String contenu) {
        return lireModele(contenu).elements();
    }

    /** Les éléments et les conditions déclarées ; une condition déclarée deux fois, ou sans nom, est refusée. */
    public static Modele lireModele(String contenu) {
        Map<String, String> conditions = new LinkedHashMap<>();
        List<DocumentLibre.Element> elements = new ArrayList<>();
        List<List<List<String>>> lignesTable = null;
        int colonnes = 0;
        int numero = 0;
        for (String brute : contenu.split("\r?\n")) {
            numero++;
            if (brute.isEmpty()) {
                continue;
            }
            int tab = brute.indexOf('\t');
            String type = tab < 0 ? brute.trim() : brute.substring(0, tab).trim();
            String texte = tab < 0 ? "" : brute.substring(tab + 1);
            switch (type) {
                case "FICHIER" -> {
                    // le nom du fichier n'est pas une information du document
                }
                case "CONDITION" -> {
                    int us = texte.indexOf(US);
                    String nom = us < 0 ? "" : texte.substring(0, us).trim();
                    if (nom.isEmpty()) {
                        throw new IllegalArgumentException("ligne " + numero + " : CONDITION sans nom (attendu NOM US expression)");
                    }
                    if (conditions.put(nom, texte.substring(us + 1).trim()) != null) {
                        throw new IllegalArgumentException("ligne " + numero + " : condition " + nom + " déclarée deux fois");
                    }
                }
                case "TITRE", "SOUS_TITRE", "PARA", "CENTRE", "DROITE", "VIDE" -> {
                    if (lignesTable != null) {
                        throw new IllegalArgumentException("ligne " + numero + " : " + type + " dans un tableau non refermé");
                    }
                    elements.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.valueOf(type), "VIDE".equals(type) ? "" : texte));
                }
                case "TABLE" -> {
                    if (lignesTable != null) {
                        throw new IllegalArgumentException("ligne " + numero + " : tableau ouvert dans un tableau");
                    }
                    colonnes = Integer.parseInt(texte.trim());
                    lignesTable = new ArrayList<>();
                }
                case "LIGNE" -> {
                    if (lignesTable == null) {
                        throw new IllegalArgumentException("ligne " + numero + " : LIGNE hors d'un tableau");
                    }
                    List<List<String>> cellules = new ArrayList<>();
                    for (String cellule : texte.split(String.valueOf(US), -1)) {
                        cellules.add(new ArrayList<>(Arrays.asList(cellule.split(String.valueOf(RS), -1))));
                    }
                    while (cellules.size() < colonnes) {
                        cellules.add(new ArrayList<>(List.of("")));
                    }
                    lignesTable.add(cellules);
                }
                case "FIN_TABLE" -> {
                    if (lignesTable == null) {
                        throw new IllegalArgumentException("ligne " + numero + " : FIN_TABLE sans tableau");
                    }
                    elements.add(new DocumentLibre.Tableau(colonnes, lignesTable));
                    lignesTable = null;
                }
                default -> throw new IllegalArgumentException("ligne " + numero + " : type inconnu « " + type + " »");
            }
        }
        if (lignesTable != null) {
            throw new IllegalArgumentException("tableau non refermé en fin de fichier");
        }
        return new Modele(elements, conditions);
    }
}
