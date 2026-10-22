package cnm.prs.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cnm.prs.dto.ModelePvDto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * ⚠️ 2026-10-22 (demande front « modeles-dao », D0-D3) — la <strong>page</strong> comme vue modifiable d'un fichier de commande du
 * DAO ({@link FichierCommande}) : la conversion dans les deux sens, sans perte. La production garde le moteur du DAO
 * ({@link FormulairesCandidat#rendreModele}, {@link GenerateurDocumentsFiche}) : un modèle en vigueur est reconverti en fichier de
 * commande, et un départ non modifié rend exactement le document intégré. Pure : aucune base.
 *
 * <pre>
 *   TITRE        titre de niveau 1, centré         SOUS_TITRE   titre de niveau 2
 *   PARA         paragraphe justifié               CENTRE       paragraphe centré
 *   DROITE       paragraphe aligné à droite        VIDE         paragraphe vide, sans réglage
 *   PARA {{SI:X}} … PARA {{FINSI:X}}               passage (bloc) de la règle X
 *   TABLE / LIGNE / FIN_TABLE                      tableau ; une cellule = ses paragraphes (sans réglage)
 *   {{CODE}}, {{CODE.lettres}}, {{LOT}}, {{DERIVE.…}}, {{AVIS.…}}…   variables (le code porte son suffixe)
 *   CONDITION NOM US expression                    règles déclarées, même expression
 * </pre>
 *
 * <p>Restent du texte : {@code {{IMAGE:…}}}, et les marqueurs d'une plage de lignes de tableau (un paragraphe de cellule qui n'est
 * que {@code {{SI:X}}} ou {@code {{FINSI:X}}}). Ce que le moteur du DAO ne sait pas rendre (marques, listes, images de la
 * bibliothèque, fonds, sauts de page, répétitions, titres numérotés, renvois, passages en ligne, en-tête et pied) est relevé par
 * {@link #nonRendus} : la reconversion l'aplatit ou l'omet.</p>
 */
public final class ConversionDao {

    private static final char US = '\u001F';
    private static final char RS = '\u001E';
    /** Un passage du fichier : une ligne PARA qui n'est que le marqueur. */
    private static final Pattern MARQUEUR_SEUL = Pattern.compile("^\\{\\{(SI|FINSI):([^}]+)}}$");
    /** Un jeton du DAO : une forme de code (tirets et points compris) ; les autres ({@code IMAGE:…}) restent du texte. */
    private static final Pattern JETON = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_.#-]*)}}");
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private ConversionDao() {
    }

    /** Le fichier de commande en page : document et règles déclarées (dans l'ordre du fichier). */
    public record Page(ObjectNode document, List<ModelePvDto.Condition> conditions) {
    }

    // ───────────────────────────────────────────── commande → page ─────────────────────────────────────────────

    public static Page enPage(FichierCommande.Modele modele) {
        Deque<ArrayNode> pile = new ArrayDeque<>();
        ArrayNode corps = F.arrayNode();
        pile.push(corps);
        for (DocumentLibre.Element e : modele.elements()) {
            if (e instanceof DocumentLibre.Paragraphe p) {
                Matcher m = MARQUEUR_SEUL.matcher(p.texte());
                if (p.style() == DocumentLibre.Style.PARA && m.matches()) {
                    if ("SI".equals(m.group(1))) {
                        ObjectNode bloc = ModelePvPage.noeud("bloc");
                        bloc.putObject("attrs").put("condition", m.group(2));
                        pile.peek().add(bloc);
                        pile.push(bloc.putArray("content"));
                    } else if (pile.size() > 1) {
                        pile.pop();
                    }
                    continue;
                }
                pile.peek().add(paragraphe(p));
            } else if (e instanceof DocumentLibre.Tableau t) {
                pile.peek().add(tableau(t));
            } else {
                throw new IllegalArgumentException("Élément inattendu dans un fichier de commande : " + e.getClass().getSimpleName());
            }
        }
        ObjectNode doc = F.objectNode();
        doc.putObject("corps").put("type", "doc").set("content", corps);
        List<ModelePvDto.Condition> conditions = new ArrayList<>();
        modele.conditions().forEach((nom, expr) -> conditions.add(new ModelePvDto.Condition(nom, expr, null)));
        return new Page(doc, conditions);
    }

    private static ObjectNode paragraphe(DocumentLibre.Paragraphe p) {
        ObjectNode n;
        switch (p.style()) {
            case TITRE -> {
                n = ModelePvPage.noeud("heading");
                n.putObject("attrs").put("level", 1).put("textAlign", "center");
            }
            case SOUS_TITRE -> {
                n = ModelePvPage.noeud("heading");
                n.putObject("attrs").put("level", 2);
            }
            case PARA -> {
                n = ModelePvPage.noeud("paragraph");
                n.putObject("attrs").put("textAlign", "justify");
            }
            case CENTRE -> {
                n = ModelePvPage.noeud("paragraph");
                n.putObject("attrs").put("textAlign", "center");
            }
            case DROITE -> {
                n = ModelePvPage.noeud("paragraph");
                n.putObject("attrs").put("textAlign", "right");
            }
            default -> n = ModelePvPage.noeud("paragraph");
        }
        if (!p.texte().isEmpty()) {
            n.set("content", enLigne(p.texte()));
        }
        return n;
    }

    private static ObjectNode tableau(DocumentLibre.Tableau t) {
        ObjectNode table = ModelePvPage.noeud("table");
        table.putObject("attrs").put("bordures", true);
        ArrayNode lignes = table.putArray("content");
        for (List<List<String>> ligne : t.lignes()) {
            ObjectNode row = ModelePvPage.noeud("tableRow");
            ArrayNode cellules = row.putArray("content");
            for (List<String> cellule : ligne) {
                ObjectNode c = ModelePvPage.noeud("tableCell");
                ArrayNode ps = c.putArray("content");
                for (String texte : cellule) {
                    ObjectNode p = ModelePvPage.noeud("paragraph");
                    if (!texte.isEmpty()) {
                        p.set("content", enLigne(texte));
                    }
                    ps.add(p);
                }
                cellules.add(c);
            }
            lignes.add(row);
        }
        return table;
    }

    /** Un texte : ses jetons en variables (code entier, suffixe compris), le reste en texte tel quel. */
    static ArrayNode enLigne(String texte) {
        ArrayNode out = F.arrayNode();
        Matcher m = JETON.matcher(texte);
        int pos = 0;
        while (m.find()) {
            if (m.start() > pos) {
                out.add(ModelePvPage.noeud("text").put("text", texte.substring(pos, m.start())));
            }
            ObjectNode v = ModelePvPage.noeud("variable");
            v.putObject("attrs").put("code", m.group(1));
            out.add(v);
            pos = m.end();
        }
        if (pos < texte.length()) {
            out.add(ModelePvPage.noeud("text").put("text", texte.substring(pos)));
        }
        return out;
    }

    // ───────────────────────────────────────────── page → commande ─────────────────────────────────────────────

    /** La page reconvertie en fichier de commande (son texte, relu par {@link FichierCommande#lireModele}). */
    public static String enCommande(JsonNode document, Map<String, String> conditions) {
        List<String> lignes = new ArrayList<>();
        conditions.forEach((nom, expr) -> lignes.add("CONDITION\t" + nom + US + expr));
        blocs(document.path("corps").path("content"), lignes);
        return String.join("\n", lignes) + "\n";
    }

    /** Le modèle du moteur du DAO, depuis une page. */
    public static FichierCommande.Modele enModele(JsonNode document, Map<String, String> conditions) {
        return FichierCommande.lireModele(enCommande(document, conditions));
    }

    private static void blocs(JsonNode content, List<String> lignes) {
        for (JsonNode n : content) {
            JsonNode a = n.path("attrs");
            switch (type(n)) {
                case "bloc" -> {
                    String c = a.path("condition").asString("");
                    lignes.add("PARA\t{{SI:" + c + "}}");
                    blocs(n.path("content"), lignes);
                    lignes.add("PARA\t{{FINSI:" + c + "}}");
                }
                case "heading" -> lignes.add((a.path("level").asInt(1) <= 1 ? "TITRE" : "SOUS_TITRE") + "\t" + texte(n));
                case "paragraph" -> {
                    String t = texte(n);
                    String al = a.path("textAlign").asString("");
                    String style = switch (al) {
                        case "center" -> "CENTRE";
                        case "right" -> "DROITE";
                        case "justify", "left" -> "PARA";
                        default -> t.isEmpty() ? "VIDE" : "PARA";
                    };
                    lignes.add("VIDE".equals(style) ? "VIDE" : style + "\t" + t);
                }
                case "table" -> {
                    int colonnes = 0;
                    List<String> rangs = new ArrayList<>();
                    for (JsonNode ligne : n.path("content")) {
                        List<String> cellules = new ArrayList<>();
                        for (JsonNode c : ligne.path("content")) {
                            List<String> ps = new ArrayList<>();
                            for (JsonNode p : c.path("content")) {
                                ps.add(texte(p));
                            }
                            cellules.add(String.join(String.valueOf(RS), ps.isEmpty() ? List.of("") : ps));
                        }
                        colonnes = Math.max(colonnes, cellules.size());
                        rangs.add("LIGNE\t" + String.join(String.valueOf(US), cellules));
                    }
                    lignes.add("TABLE\t" + colonnes);
                    lignes.addAll(rangs);
                    lignes.add("FIN_TABLE");
                }
                case "bulletList", "orderedList" -> {
                    for (JsonNode li : n.path("content")) {
                        blocs(li.path("content"), lignes);
                    }
                }
                case "repetition" -> blocs(n.path("content"), lignes);
                default -> {
                    // image, saut de page : le moteur du DAO ne les rend pas (relevés par nonRendus)
                }
            }
        }
    }

    /** Le texte d'un paragraphe : textes tels quels, variables en jetons, retours à la ligne en paragraphes du moteur. */
    private static String texte(JsonNode parent) {
        StringBuilder s = new StringBuilder();
        for (JsonNode n : parent.path("content")) {
            switch (type(n)) {
                case "text" -> s.append(n.path("text").asString(""));
                case "variable" -> s.append("{{").append(n.path("attrs").path("code").asString("")).append("}}");
                case "hardBreak" -> s.append(FormulairesCandidat.SEPARATEUR_LIGNES);
                case "bloc" -> s.append(texte(n));
                case "renvoi" -> s.append(ProjetMarcheService.A_COMPLETER);
                default -> {
                    // champ, numéro de page, image : non rendus (relevés par nonRendus)
                }
            }
        }
        return s.toString();
    }

    // ───────────────────────────────────────────── ce que le moteur ne rend pas ─────────────────────────────────────────────

    /**
     * Ce que la page porte et que le moteur du DAO ne rend pas : lieu lisible → ce qui est ignoré ou aplati. Le contrôle en fait des
     * avertissements ({@code MISE_EN_FORME_NON_RENDUE}), sauf un passage en ligne, bloquant (son contenu serait toujours imprimé).
     */
    public static Map<String, String> nonRendus(JsonNode document) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String zone : List.of("entete", "piedDePage")) {
            if (document.path(zone).isObject() && !document.path(zone).path("content").isEmpty()) {
                out.put(zone, "l'en-tête et le pied de page d'un document du DAO sont ceux de l'application (pied de version)");
            }
        }
        if (document.path("page").isObject()) {
            out.put("page", "les réglages de page (marges, police, taille) ne s'appliquent pas : le document garde la mise en page du DAO");
        }
        parcourir(document.path("corps"), "corps", false, out);
        return out;
    }

    /** Les passages en ligne : leur contenu serait toujours imprimé. */
    public static List<String> passagesEnLigne(JsonNode document) {
        List<String> out = new ArrayList<>();
        enLigneBlocs(document.path("corps"), "corps", out);
        return out;
    }

    private static void enLigneBlocs(JsonNode n, String ou, List<String> out) {
        int i = 0;
        for (JsonNode c : n.path("content")) {
            String lieu = ou + "/" + i++;
            if (("paragraph".equals(type(c)) || "heading".equals(type(c))) && contientBloc(c)) {
                out.add(lieu);
            }
            enLigneBlocs(c, lieu, out);
        }
    }

    private static boolean contientBloc(JsonNode p) {
        for (JsonNode c : p.path("content")) {
            if ("bloc".equals(type(c))) {
                return true;
            }
        }
        return false;
    }

    private static void parcourir(JsonNode n, String ou, boolean dansCellule, Map<String, String> out) {
        int i = 0;
        for (JsonNode c : n.path("content")) {
            String lieu = ou + "/" + i++;
            JsonNode a = c.path("attrs");
            switch (type(c)) {
                case "bulletList", "orderedList" -> out.put(lieu, "liste : ses éléments deviennent des paragraphes, sans puce ni numéro");
                case "image" -> out.put(lieu, "image de la bibliothèque : omise (l'emblème du DAO s'écrit {{IMAGE:embleme}})");
                case "pageBreak" -> out.put(lieu, "saut de page : omis");
                case "repetition" -> out.put(lieu, "répétition : son contenu est imprimé une fois");
                case "heading" -> {
                    if (a.path("numerotation").isString() || a.path("level").asInt(1) > 2) {
                        out.put(lieu, "titre numéroté ou de niveau 3 : écrit comme un sous-titre, sans numéro calculé");
                    }
                }
                case "paragraph" -> {
                    for (String r : List.of("retrait", "retraitDroite", "retraitPremiereLigne", "espaceAvant", "espaceApres", "interligne")) {
                        if (a.path(r).isNumber()) {
                            out.put(lieu, "réglage de paragraphe (" + r + ") : ignoré, le style du DAO s'applique");
                        }
                    }
                    if (dansCellule && a.path("textAlign").isString()) {
                        out.put(lieu, "alignement dans une cellule : ignoré");
                    }
                }
                case "table" -> {
                    if (a.path("largeur").isNumber() || a.path("retrait").isNumber() || a.path("bordures").isBoolean() && !a.path("bordures")
                            .asBoolean()) {
                        out.put(lieu, "réglages de tableau (largeur, retrait, bordures) : ignorés");
                    }
                }
                case "tableCell", "tableHeader" -> {
                    if (a.path("colspan").asInt(1) > 1 || a.path("rowspan").asInt(1) > 1 || a.path("fond").isString()
                            || a.path("colwidth").isArray() || "tableHeader".equals(type(c))) {
                        out.put(lieu, "réglages de cellule (fusion, fond, largeur, en-tête) : ignorés");
                    }
                }
                case "text", "variable" -> {
                    if (c.path("marks").isArray() && !c.path("marks").isEmpty()) {
                        out.put(lieu, "mise en forme du texte (gras, italique, police, couleur) : ignorée");
                    }
                    if ("variable".equals(type(c)) && a.path("format").isString()) {
                        out.put(lieu, "format de variable : ignoré (le format d'un jeton du DAO s'écrit dans son code, « .lettres »)");
                    }
                }
                case "champ" -> out.put(lieu, "champ saisi : omis (un document du DAO n'a pas de champ saisi)");
                case "renvoi" -> out.put(lieu, "renvoi : écrit « …… », sans numéro calculé");
                case "numeroPage", "nombrePages" -> out.put(lieu, "numéro de page : omis");
                default -> {
                    // rendu
                }
            }
            parcourir(c, lieu, dansCellule || "tableCell".equals(type(c)) || "tableHeader".equals(type(c)), out);
        }
    }

    private static String type(JsonNode n) {
        return n != null && n.path("type").isString() ? n.path("type").asString() : "";
    }
}
