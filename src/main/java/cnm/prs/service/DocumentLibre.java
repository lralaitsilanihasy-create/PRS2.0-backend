package cnm.prs.service;

import java.util.ArrayList;
import java.util.List;

/**
 * ⚠️ V47 (formulaires du candidat, arbitrage R12 (c) du 2026-09-26) — un document rendu depuis une <strong>liste
 * ordonnée d'éléments</strong> : des paragraphes avec leur style, des tableaux dont chaque cellule est une liste de
 * paragraphes. C'est le format des fichiers de commande du décalque des modèles officiels
 * ({@code modeles/candidat/<sigle>.txt}, lu par {@link FichierCommande}) ; {@link GenerateurDocumentsFiche#generer(DocumentLibre)}
 * le rend en docx (POI) et en pdf (OpenPDF).
 *
 * @param type       type de document ({@code A1}…{@code C2})
 * @param lot        rang du lot d'un document établi par lot ({@code C1}, {@code C2}) ; {@code null} : une fois pour le dossier
 * @param elements   les éléments, dans l'ordre du document
 * @param piedDePage pied de page de la version (hors du texte que le comparateur de fidélité relit)
 */
public record DocumentLibre(String type, Integer lot, List<Element> elements, String piedDePage) {

    /** Style d'un paragraphe, du format de commande. */
    public enum Style {
        /** Centré, gras. */
        TITRE,
        /** Gras, à gauche. */
        SOUS_TITRE,
        /** Justifié. */
        PARA,
        CENTRE,
        DROITE,
        /** Paragraphe vide. */
        VIDE
    }

    /** Un élément du document : un paragraphe ou un tableau. */
    public sealed interface Element permits Paragraphe, Tableau, Image {
    }

    /**
     * ⚠️ 2026-10-01 (avis spécifique, §B7.8) — une image centrée ({@code {{IMAGE:<nom>}}} d'un modèle), PNG, posée à
     * {@code largeurMm} de large, la hauteur suivant les proportions. Elle n'a pas de texte.
     */
    public record Image(String nom, byte[] contenu, int largeurMm) implements Element {
    }

    /**
     * @param solidaireDuSuivant ⚠️ 2026-10-01 (avis spécifique, contre-recette) — le paragraphe reste sur la même page que
     *                           le suivant : {@code keepNext} en Word, bloc indivisible en PDF
     */
    public record Paragraphe(Style style, String texte, boolean solidaireDuSuivant) implements Element {
        public Paragraphe(Style style, String texte) {
            this(style, texte, false);
        }
    }

    /**
     * Un tableau à {@code colonnes} colonnes ; chaque ligne est une liste de cellules, chaque cellule une liste de
     * paragraphes (au moins un, éventuellement vide).
     */
    public record Tableau(int colonnes, List<List<List<String>>> lignes) implements Element {
    }

    /**
     * ⚠️ 2026-10-01 (avis spécifique, contre-recette du front) — le même document, ses {@code n} derniers paragraphes
     * imprimés gardés ensemble (les {@code n - 1} premiers solidaires du suivant) : le bloc de signature de l'avis (« à …,
     * le … », la qualité, le nom) passe en entier sur la page suivante quand il ne tient pas. Les paragraphes vides de fin
     * ne comptent pas.
     */
    public DocumentLibre finGardeeEnsemble(int n) {
        List<Element> els = new ArrayList<>(elements);
        int restants = n;
        boolean dernier = true;
        for (int i = els.size() - 1; i >= 0 && restants > 0; i--) {
            if (!(els.get(i) instanceof Paragraphe p) || p.style() == Style.VIDE) {
                if (restants < n) {
                    break;   // un tableau ou une image au milieu : le bloc s'arrête là
                }
                continue;
            }
            if (!dernier) {
                els.set(i, new Paragraphe(p.style(), p.texte(), true));
            }
            dernier = false;
            restants--;
        }
        return new DocumentLibre(type, lot, List.copyOf(els), piedDePage);
    }

    /** Le texte du document dans l'ordre, une ligne par paragraphe et par ligne de tableau (cellules jointes par une tabulation). */
    public String texte() {
        List<String> lignes = new ArrayList<>();
        for (Element e : elements) {
            if (e instanceof Paragraphe p) {
                lignes.add(p.texte());
            } else if (e instanceof Tableau t) {
                for (List<List<String>> ligne : t.lignes()) {
                    lignes.add(String.join("\t", ligne.stream().map(c -> String.join(" ", c)).toList()));
                }
            }
        }
        return String.join("\n", lignes);
    }
}
