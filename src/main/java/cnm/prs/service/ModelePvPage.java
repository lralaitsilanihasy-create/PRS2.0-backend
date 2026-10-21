package cnm.prs.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * ⚠️ 2026-10-21 (demande front « modele-pv-page », lots P1-P2 ; décision du pilote : option C') — le <strong>modèle de PV construit
 * dans l'application</strong> : un document JSON, celui de l'éditeur (Tiptap / ProseMirror), aux types de nœuds <strong>fermés</strong>.
 * Le serveur le valide (§B1), en relève les variables, champs, blocs et lignes répétées pour le contrôle (§B4), et le rend en PDF
 * (§B5) par un XHTML qu'il écrit lui-même : aucun HTML venu du navigateur n'est jamais interprété. Pur : aucune base.
 *
 * <p><strong>L'enveloppe</strong> : {@code { page?, entete?, enteteSurPremiereSeulement?, corps, piedDePage? }} ; {@code entete},
 * {@code corps} et {@code piedDePage} sont chacun un {@code doc} Tiptap ({@code { type: "doc", content: [...] }}).
 * {@code page} : {@code format} (A4), {@code orientation} (portrait, paysage), {@code marges} en mm ({@code haut}, {@code bas},
 * {@code gauche}, {@code droite}), {@code police} et {@code taille} par défaut. Les nœuds et leurs attributs sont ceux de
 * {@link #schema()} ; un attribut inconnu de valeur nulle est toléré (Tiptap écrit les valeurs par défaut). Une {@code variable} et
 * un {@code champ} portent des marques comme un texte (gras, police…).</p>
 *
 * <p><strong>Le rendu</strong> (OpenHTMLtoPDF) : {@code @page} aux marges données, en-tête et pied de page courants (ou en-tête sur
 * la première page seulement), numéros de page par compteurs CSS. « Times New Roman » et « Arial » sont rendus par Liberation Serif
 * et Liberation Sans (métriquement compatibles, SIL OFL), embarquées dans le PDF (Q2).</p>
 */
public final class ModelePvPage {

    public static final long TAILLE_MAX = 2L * 1024 * 1024;
    static final int PROFONDEUR_MAX = 40;
    static final int NOEUDS_MAX = 50_000;
    static final int TEXTE_MAX = 20_000;

    public static final List<String> POLICES = List.of("Times New Roman", "Arial");
    /** Couleurs du texte : code → libellé. */
    public static final Map<String, String> COULEURS = ordonnee("#000000", "noir", "#404040", "gris foncé", "#808080", "gris",
            "#C00000", "rouge", "#1F3864", "bleu nuit", "#2E74B5", "bleu", "#375623", "vert");
    /** Fonds de cellule : code → libellé. */
    public static final Map<String, String> FONDS = ordonnee("#F2F2F2", "gris très clair", "#D9D9D9", "gris clair", "#DEEAF6",
            "bleu clair", "#FFF2CC", "jaune clair", "#E2EFDA", "vert clair");
    public static final List<String> ALIGNEMENTS = List.of("left", "center", "right", "justify");
    public static final List<String> ORIENTATIONS = List.of("portrait", "paysage");
    static final double TAILLE_MIN = 6;
    static final double TAILLE_MAX_PT = 72;

    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*");
    private static final Pattern NOM_CONDITION = Pattern.compile("[A-Za-z0-9_-]{1,60}");
    private static final Pattern TAILLE_PT = Pattern.compile("(\\d{1,2}(?:\\.\\d+)?)\\s*pt");
    private static final Pattern MARQUEUR = Pattern.compile("\\{\\{(?:(SI|FINSI|SAISI):([A-Za-z0-9_-]+)|([A-Za-z][A-Za-z0-9_.]*)(?:\\|([a-z]+))?)}}");

    private static final Set<String> BLOCS = Set.of("paragraph", "heading", "bulletList", "orderedList", "table", "image", "pageBreak",
            "bloc");
    private static final Set<String> EN_LIGNE = Set.of("text", "hardBreak", "variable", "champ", "numeroPage", "nombrePages", "image",
            "bloc");
    private static final Map<String, String> LIBELLES = Map.ofEntries(Map.entry("paragraph", "paragraphe"),
            Map.entry("heading", "titre"), Map.entry("bulletList", "liste à puces"), Map.entry("orderedList", "liste numérotée"),
            Map.entry("listItem", "élément"), Map.entry("table", "tableau"), Map.entry("tableRow", "ligne"),
            Map.entry("tableCell", "cellule"), Map.entry("tableHeader", "cellule"), Map.entry("image", "image"),
            Map.entry("pageBreak", "saut de page"), Map.entry("bloc", "bloc"), Map.entry("text", "texte"),
            Map.entry("hardBreak", "retour à la ligne"), Map.entry("variable", "variable"), Map.entry("champ", "champ"),
            Map.entry("numeroPage", "numéro de page"), Map.entry("nombrePages", "nombre de pages"));
    private static final Map<String, String> ZONES = ordonnee("entete", "en-tête", "corps", "corps", "piedDePage", "pied de page");
    private static final Set<String> ENVELOPPE = Set.of("page", "entete", "enteteSurPremiereSeulement", "corps", "piedDePage");
    private static final Set<String> MARQUES = Set.of("bold", "italic", "underline", "textStyle");

    private ModelePvPage() {
    }

    /** Une image servie au rendu : type contrôlé, contenu, dimensions en pixels. */
    public record Image(String type, byte[] contenu, int largeur, int hauteur) {
    }

    /** Le document ne respecte pas le schéma : {@code details} chemin technique du nœud → raison (lieu lisible compris). */
    public static final class Invalide extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final transient Map<String, Object> details;

        Invalide(Map<String, Object> details) {
            super("Document invalide : " + details.size() + " défaut(s)");
            this.details = details;
        }

        public Map<String, Object> details() {
            return details;
        }
    }

    /**
     * Les marqueurs relevés (au sens du contrôle de {@link ModelePvWord} : {@code VARIABLE}, {@code SAISI}, {@code SI},
     * {@code POUR}), avec le chemin technique de chacun ({@code corps/3/0/1}) ; {@code ou} est le lieu lisible
     * (« corps › tableau 2 › ligne 1 › cellule 1 »).
     */
    public record Releve(List<ModelePvWord.Marqueur> marqueurs, Map<ModelePvWord.Marqueur, String> chemins) {
    }

    // ───────────────────────────────────────────── le lieu d'un nœud ─────────────────────────────────────────────

    /** Lieu lisible et chemin technique ; les frères se comptent par libellé (« paragraphe 12 », « tableau 2 »). */
    private record Lieu(String ou, String chemin) {

        Lieu enfant(String libelle, int rang, int index) {
            return new Lieu(ou + " › " + libelle + " " + rang, chemin + "/" + index);
        }
    }

    private static Lieu racine(String zone) {
        return new Lieu(ZONES.get(zone), zone);
    }

    /** Les enfants d'un nœud, chacun avec son lieu. */
    private static List<Map.Entry<JsonNode, Lieu>> enfants(JsonNode n, Lieu lieu) {
        List<Map.Entry<JsonNode, Lieu>> out = new ArrayList<>();
        JsonNode content = n.path("content");
        if (!content.isArray()) {
            return out;
        }
        Map<String, Integer> rangs = new HashMap<>();
        for (int i = 0; i < content.size(); i++) {
            JsonNode e = content.get(i);
            String libelle = LIBELLES.getOrDefault(type(e), "nœud");
            int rang = rangs.merge(libelle, 1, Integer::sum);
            out.add(Map.entry(e, lieu.enfant(libelle, rang, i)));
        }
        return out;
    }

    private static String type(JsonNode n) {
        return n != null && n.path("type").isString() ? n.path("type").asString() : "";
    }

    // ───────────────────────────────────────────── validation (§B1) ─────────────────────────────────────────────

    /**
     * Valide le document : enveloppe, types de nœuds et leur place, attributs et leurs valeurs (listes fermées), images connues,
     * profondeur et nombre de nœuds. Les codes de variable, de champ, de condition et de liste ne sont vérifiés que dans leur forme :
     * les connaître relève du contrôle (§B4).
     *
     * @throws Invalide au premier défaut de chaque nœud, cinquante au plus
     */
    public static void valider(JsonNode doc, LongPredicate imageConnue) {
        Validation v = new Validation(imageConnue);
        if (doc == null || !doc.isObject()) {
            v.erreur(new Lieu("document", ""), "le document est un objet { page, entete, corps, piedDePage }");
            throw new Invalide(v.erreurs);
        }
        Lieu tout = new Lieu("document", "");
        for (String cle : doc.propertyNames()) {
            if (!ENVELOPPE.contains(cle)) {
                v.erreur(tout, "clé inconnue « " + cle + " » " + ENVELOPPE);
            }
        }
        JsonNode page = doc.path("page");
        if (!page.isMissingNode() && !page.isNull()) {
            v.page(page, new Lieu("page", "page"));
        }
        JsonNode premiere = doc.path("enteteSurPremiereSeulement");
        if (!premiere.isMissingNode() && !premiere.isNull() && !premiere.isBoolean()) {
            v.erreur(new Lieu("document", "enteteSurPremiereSeulement"), "enteteSurPremiereSeulement est vrai ou faux");
        }
        if (doc.path("corps").isMissingNode() || doc.path("corps").isNull()) {
            v.erreur(new Lieu("corps", "corps"), "le corps est obligatoire");
        }
        for (String zone : ZONES.keySet()) {
            JsonNode z = doc.path(zone);
            if (z.isMissingNode() || z.isNull()) {
                continue;
            }
            Lieu lieu = racine(zone);
            if (!z.isObject() || !"doc".equals(type(z)) || !z.path("content").isArray()) {
                v.erreur(lieu, "une zone est un nœud { type: \"doc\", content: [...] }");
                continue;
            }
            v.autorises(z, lieu, Set.of("type", "content"));
            v.contenu(z, lieu, BLOCS, 1, false);
        }
        if (!v.erreurs.isEmpty()) {
            throw new Invalide(v.erreurs);
        }
    }

    private static final class Validation {

        private final LongPredicate imageConnue;
        private final Map<String, Object> erreurs = new LinkedHashMap<>();
        private int noeuds;

        Validation(LongPredicate imageConnue) {
            this.imageConnue = imageConnue;
        }

        void erreur(Lieu lieu, String raison) {
            if (erreurs.size() < 50) {
                erreurs.putIfAbsent(lieu.chemin().isEmpty() ? "document" : lieu.chemin(), lieu.ou() + " : " + raison);
            }
        }

        void page(JsonNode page, Lieu lieu) {
            if (!page.isObject()) {
                erreur(lieu, "les réglages de page sont un objet");
                return;
            }
            autorises(page, lieu, Set.of("format", "orientation", "marges", "police", "taille"));
            texte(page, "format", lieu, List.of("A4"));
            texte(page, "orientation", lieu, ORIENTATIONS);
            texte(page, "police", lieu, POLICES);
            nombre(page, "taille", lieu, TAILLE_MIN, TAILLE_MAX_PT);
            JsonNode marges = page.path("marges");
            if (!marges.isMissingNode() && !marges.isNull()) {
                if (!marges.isObject()) {
                    erreur(lieu, "les marges sont un objet { haut, bas, gauche, droite } en mm");
                    return;
                }
                autorises(marges, lieu, Set.of("haut", "bas", "gauche", "droite"));
                for (String m : List.of("haut", "bas", "gauche", "droite")) {
                    nombre(marges, m, lieu, 0, 60);
                }
            }
        }

        /** Les enfants d'un nœud : chacun d'un type permis à cette place. */
        void contenu(JsonNode n, Lieu lieu, Set<String> permis, int profondeur, boolean dansLigneRepetee) {
            JsonNode content = n.path("content");
            if (content.isMissingNode() || content.isNull()) {
                return;
            }
            if (!content.isArray()) {
                erreur(lieu, "content est une liste de nœuds");
                return;
            }
            for (Map.Entry<JsonNode, Lieu> e : enfants(n, lieu)) {
                noeud(e.getKey(), e.getValue(), permis, profondeur + 1, dansLigneRepetee);
            }
        }

        void noeud(JsonNode n, Lieu lieu, Set<String> permis, int profondeur, boolean dansLigneRepetee) {
            if (++noeuds > NOEUDS_MAX) {
                erreur(lieu, "plus de " + NOEUDS_MAX + " nœuds");
                return;
            }
            if (profondeur > PROFONDEUR_MAX) {
                erreur(lieu, "imbrication de plus de " + PROFONDEUR_MAX + " niveaux");
                return;
            }
            if (!n.isObject()) {
                erreur(lieu, "un nœud est un objet { type, attrs?, content?, marks?, text? }");
                return;
            }
            String t = type(n);
            if (!permis.contains(t)) {
                erreur(lieu, t.isEmpty() ? "nœud sans type" : "« " + t + " » n'est pas permis ici " + permis.stream().sorted().toList());
                return;
            }
            boolean enLigne = permis == EN_LIGNE;
            switch (t) {
                case "paragraph" -> {
                    cles(n, lieu, "content");
                    attrs(n, lieu, Set.of("textAlign", "retrait", "retraitDroite", "retraitPremiereLigne", "espaceAvant", "espaceApres",
                            "interligne"));
                    paragraphe(n.path("attrs"), lieu);
                    contenu(n, lieu, EN_LIGNE, profondeur, dansLigneRepetee);
                }
                case "heading" -> {
                    cles(n, lieu, "content");
                    attrs(n, lieu, Set.of("level", "textAlign", "retrait", "retraitDroite", "retraitPremiereLigne", "espaceAvant",
                            "espaceApres", "interligne"));
                    paragraphe(n.path("attrs"), lieu);
                    JsonNode level = n.path("attrs").path("level");
                    if (!level.isIntegralNumber() || level.intValue() < 1 || level.intValue() > 3) {
                        erreur(lieu, "level vaut 1, 2 ou 3");
                    }
                    contenu(n, lieu, EN_LIGNE, profondeur, dansLigneRepetee);
                }
                case "bulletList", "orderedList" -> {
                    cles(n, lieu, "content");
                    attrs(n, lieu, "orderedList".equals(t) ? Set.of("start") : Set.of());
                    nombre(n.path("attrs"), "start", lieu, 1, 9999);
                    nonVide(n, lieu);
                    contenu(n, lieu, Set.of("listItem"), profondeur, dansLigneRepetee);
                }
                case "listItem" -> {
                    cles(n, lieu, "content");
                    attrs(n, lieu, Set.of());
                    nonVide(n, lieu);
                    contenu(n, lieu, BLOCS, profondeur, dansLigneRepetee);
                }
                case "table" -> {
                    cles(n, lieu, "content");
                    attrs(n, lieu, Set.of("bordures", "retrait", "largeur"));
                    booleen(n.path("attrs"), "bordures", lieu);
                    nombre(n.path("attrs"), "retrait", lieu, -50, 150);
                    nombre(n.path("attrs"), "largeur", lieu, 10, 100);
                    nonVide(n, lieu);
                    contenu(n, lieu, Set.of("tableRow"), profondeur, dansLigneRepetee);
                }
                case "tableRow" -> {
                    cles(n, lieu, "content");
                    attrs(n, lieu, Set.of("liste"));
                    JsonNode liste = n.path("attrs").path("liste");
                    if (!liste.isMissingNode() && !liste.isNull() && (!liste.isString() || !CODE.matcher(liste.asString()).matches())) {
                        erreur(lieu, "liste est un nom de liste (OBSERVATIONS, TITULAIRES)");
                    }
                    nonVide(n, lieu);
                    contenu(n, lieu, Set.of("tableCell", "tableHeader"), profondeur, dansLigneRepetee || liste.isString());
                }
                case "tableCell", "tableHeader" -> {
                    cles(n, lieu, "content");
                    attrs(n, lieu, Set.of("colspan", "rowspan", "colwidth", "fond", "alignementVertical", "bordures"));
                    JsonNode a = n.path("attrs");
                    nombre(a, "colspan", lieu, 1, 50);
                    nombre(a, "rowspan", lieu, 1, 200);
                    texte(a, "fond", lieu, List.copyOf(FONDS.keySet()));
                    texte(a, "alignementVertical", lieu, List.of("haut", "milieu", "bas"));
                    booleen(a, "bordures", lieu);
                    JsonNode cw = a.path("colwidth");
                    if (!cw.isMissingNode() && !cw.isNull()) {
                        boolean ok = cw.isArray() && cw.size() <= 50;
                        for (JsonNode w : cw) {
                            ok &= w.isNull() || w.isNumber() && w.doubleValue() > 0 && w.doubleValue() <= 3000;
                        }
                        if (!ok) {
                            erreur(lieu, "colwidth est une liste de largeurs en pixels");
                        }
                    }
                    nonVide(n, lieu);
                    contenu(n, lieu, BLOCS, profondeur, dansLigneRepetee);
                }
                case "image" -> {
                    cles(n, lieu);
                    attrs(n, lieu, Set.of("idImage", "largeur", "alignement"));
                    JsonNode id = n.path("attrs").path("idImage");
                    if (!id.isIntegralNumber() || !imageConnue.test(id.longValue())) {
                        erreur(lieu, "idImage ne désigne aucune image déposée (POST /api/modeles-pv/images)");
                    }
                    nombre(n.path("attrs"), "largeur", lieu, 1, 400);
                    texte(n.path("attrs"), "alignement", lieu, List.of("left", "center", "right"));
                }
                case "pageBreak", "hardBreak", "numeroPage", "nombrePages" -> {
                    cles(n, lieu);
                    attrs(n, lieu, Set.of());
                }
                case "bloc" -> {
                    cles(n, lieu, "content");
                    attrs(n, lieu, Set.of("condition", "enLigne"));
                    JsonNode c = n.path("attrs").path("condition");
                    if (!c.isString() || !NOM_CONDITION.matcher(c.asString()).matches()) {
                        erreur(lieu, "condition est le nom d'une condition déclarée (lettres, chiffres, _ ou -)");
                    }
                    contenu(n, lieu, enLigne ? EN_LIGNE : BLOCS, profondeur, dansLigneRepetee);
                }
                case "variable" -> {
                    cles(n, lieu, "marks");
                    marques(n.path("marks"), lieu);
                    attrs(n, lieu, Set.of("code", "format"));
                    JsonNode c = n.path("attrs").path("code");
                    if (!c.isString() || !CODE.matcher(c.asString()).matches()) {
                        erreur(lieu, "code est un code du catalogue (PV.REFERENCE)");
                    }
                    JsonNode f = n.path("attrs").path("format");
                    if (!f.isMissingNode() && !f.isNull() && !f.isString()) {
                        erreur(lieu, "format est un texte");
                    }
                }
                case "champ" -> {
                    cles(n, lieu, "marks");
                    marques(n.path("marks"), lieu);
                    attrs(n, lieu, Set.of("code"));
                    JsonNode c = n.path("attrs").path("code");
                    if (!c.isString() || !c.asString().matches("[A-Za-z0-9_]{1,60}")) {
                        erreur(lieu, "code est le code d'un champ saisi (lettres, chiffres ou _)");
                    }
                }
                case "text" -> {
                    for (String cle : n.propertyNames()) {
                        if (!Set.of("type", "text", "marks").contains(cle)) {
                            erreur(lieu, "clé inconnue « " + cle + " »");
                        }
                    }
                    JsonNode texte = n.path("text");
                    if (!texte.isString() || texte.asString().isEmpty() || texte.asString().length() > TEXTE_MAX) {
                        erreur(lieu, "text est un texte non vide de " + TEXTE_MAX + " caractères au plus");
                    }
                    marques(n.path("marks"), lieu);
                }
                default -> erreur(lieu, "type inconnu « " + t + " »");
            }
        }

        void paragraphe(JsonNode a, Lieu lieu) {
            texte(a, "textAlign", lieu, ALIGNEMENTS);
            nombre(a, "retrait", lieu, -50, 150);
            nombre(a, "retraitDroite", lieu, -50, 150);
            nombre(a, "retraitPremiereLigne", lieu, -50, 150);
            nombre(a, "espaceAvant", lieu, 0, 200);
            nombre(a, "espaceApres", lieu, 0, 200);
            nombre(a, "interligne", lieu, 0.5, 4);
        }

        void marques(JsonNode marks, Lieu lieu) {
            if (marks.isMissingNode() || marks.isNull()) {
                return;
            }
            if (!marks.isArray()) {
                erreur(lieu, "marks est une liste");
                return;
            }
            for (JsonNode m : marks) {
                String t = type(m);
                if (!MARQUES.contains(t)) {
                    erreur(lieu, "marque inconnue « " + t + " » " + MARQUES.stream().sorted().toList());
                    continue;
                }
                for (String cle : m.propertyNames()) {
                    if (!Set.of("type", "attrs").contains(cle)) {
                        erreur(lieu, "clé inconnue « " + cle + " » dans la marque " + t);
                    }
                }
                attrs(m, lieu, "textStyle".equals(t) ? Set.of("fontFamily", "fontSize", "color") : Set.of());
                JsonNode a = m.path("attrs");
                texte(a, "fontFamily", lieu, POLICES);
                texte(a, "color", lieu, List.copyOf(COULEURS.keySet()));
                JsonNode s = a.path("fontSize");
                if (!s.isMissingNode() && !s.isNull() && taille(s) == null) {
                    erreur(lieu, "fontSize est une taille en points, de " + (int) TAILLE_MIN + " à " + (int) TAILLE_MAX_PT + " (12 ou \"12pt\")");
                }
            }
        }

        /** Les clés d'un objet de réglage : aucune autre que {@code permises}. */
        void autorises(JsonNode objet, Lieu lieu, Set<String> permises) {
            for (String cle : objet.propertyNames()) {
                if (!permises.contains(cle)) {
                    erreur(lieu, "clé inconnue « " + cle + " » " + permises.stream().sorted().toList());
                }
            }
        }

        void cles(JsonNode n, Lieu lieu, String... autres) {
            for (String cle : n.propertyNames()) {
                if (!"type".equals(cle) && !"attrs".equals(cle) && !List.of(autres).contains(cle)) {
                    erreur(lieu, "clé inconnue « " + cle + " »");
                }
            }
        }

        void attrs(JsonNode n, Lieu lieu, Set<String> permis) {
            JsonNode a = n.path("attrs");
            if (a.isMissingNode() || a.isNull()) {
                return;
            }
            if (!a.isObject()) {
                erreur(lieu, "attrs est un objet");
                return;
            }
            for (Map.Entry<String, JsonNode> e : a.properties()) {
                if (!permis.contains(e.getKey()) && !e.getValue().isNull()) {
                    erreur(lieu, "attribut inconnu « " + e.getKey() + " »" + (permis.isEmpty() ? "" : " " + permis.stream().sorted().toList()));
                }
            }
        }

        void nonVide(JsonNode n, Lieu lieu) {
            if (!n.path("content").isArray() || n.path("content").isEmpty()) {
                erreur(lieu, "ce nœud a au moins un enfant");
            }
        }

        void texte(JsonNode a, String cle, Lieu lieu, List<String> valeurs) {
            JsonNode v = a.path(cle);
            if (!v.isMissingNode() && !v.isNull() && (!v.isString() || !valeurs.contains(v.asString()))) {
                erreur(lieu, cle + " vaut " + valeurs);
            }
        }

        void nombre(JsonNode a, String cle, Lieu lieu, double min, double max) {
            JsonNode v = a.path(cle);
            if (!v.isMissingNode() && !v.isNull() && (!v.isNumber() || v.doubleValue() < min || v.doubleValue() > max)) {
                erreur(lieu, cle + " est un nombre de " + fmt(min) + " à " + fmt(max));
            }
        }

        void booleen(JsonNode a, String cle, Lieu lieu) {
            JsonNode v = a.path(cle);
            if (!v.isMissingNode() && !v.isNull() && !v.isBoolean()) {
                erreur(lieu, cle + " est vrai ou faux");
            }
        }
    }

    /** Une taille de police en points ({@code 12} ou {@code "12pt"}), ou {@code null} hors de la plage. */
    static Double taille(JsonNode s) {
        Double v = null;
        if (s.isNumber()) {
            v = s.doubleValue();
        } else if (s.isString()) {
            Matcher m = TAILLE_PT.matcher(s.asString().trim());
            v = m.matches() ? Double.valueOf(m.group(1)) : null;
        }
        return v == null || v < TAILLE_MIN || v > TAILLE_MAX_PT ? null : v;
    }

    // ───────────────────────────────────────────── relevé (§B4) ─────────────────────────────────────────────

    /** Les variables, champs, blocs et lignes répétées du document, dans l'ordre des zones (en-tête, corps, pied de page). */
    public static Releve relever(JsonNode doc) {
        List<ModelePvWord.Marqueur> out = new ArrayList<>();
        Map<ModelePvWord.Marqueur, String> chemins = new IdentityHashMap<>();
        for (String zone : ZONES.keySet()) {
            JsonNode z = doc.path(zone);
            if (z.isObject()) {
                relever(z, racine(zone), null, out, chemins);
            }
        }
        return new Releve(out, chemins);
    }

    private static void relever(JsonNode n, Lieu lieu, String liste, List<ModelePvWord.Marqueur> out,
            Map<ModelePvWord.Marqueur, String> chemins) {
        for (Map.Entry<JsonNode, Lieu> e : enfants(n, lieu)) {
            JsonNode c = e.getKey();
            Lieu l = e.getValue();
            JsonNode a = c.path("attrs");
            ModelePvWord.Marqueur mk = null;
            String sous = liste;
            switch (type(c)) {
                case "variable" -> {
                    String f = a.path("format").isString() ? a.path("format").asString() : null;
                    mk = new ModelePvWord.Marqueur("{{" + a.path("code").asString() + (f == null ? "" : "|" + f) + "}}", "VARIABLE",
                            a.path("code").asString(), f, l.ou(), liste, false);
                }
                case "champ" -> mk = new ModelePvWord.Marqueur("{{SAISI:" + a.path("code").asString() + "}}", "SAISI",
                        a.path("code").asString(), null, l.ou(), liste, false);
                case "bloc" -> mk = new ModelePvWord.Marqueur("{{SI:" + a.path("condition").asString() + "}}", "SI",
                        a.path("condition").asString(), null, l.ou(), liste, false);
                case "tableRow" -> {
                    if (a.path("liste").isString()) {
                        String nom = a.path("liste").asString();
                        // Une ligne répétée dans une autre : la liste relevée ne correspond pas (POUR_MAL_PLACE).
                        mk = new ModelePvWord.Marqueur("{{POUR:" + nom + "}}", "POUR", nom, null, l.ou(), liste == null ? nom : "", true);
                        sous = nom;
                    }
                }
                default -> {
                    // pas de marqueur
                }
            }
            if (mk != null) {
                out.add(mk);
                chemins.put(mk, l.chemin());
            }
            relever(c, l, sous, out, chemins);
        }
    }

    /** Les images que le document emploie. */
    public static Set<Long> images(JsonNode doc) {
        Set<Long> out = new java.util.LinkedHashSet<>();
        Deque<JsonNode> pile = new ArrayDeque<>();
        pile.push(doc);
        while (!pile.isEmpty()) {
            JsonNode n = pile.pop();
            if ("image".equals(type(n)) && n.path("attrs").path("idImage").isIntegralNumber()) {
                out.add(n.path("attrs").path("idImage").longValue());
            }
            if (n.isObject() || n.isArray()) {
                for (JsonNode e : n.isObject() ? n.values() : n) {
                    pile.push(e);
                }
            }
        }
        return out;
    }

    // ───────────────────────────────────────────── rendu (§B5) ─────────────────────────────────────────────

    /**
     * Le XHTML de page du document rempli : blocs selon leurs conditions (déclarées, évaluées comme pour le Word), lignes répétées par
     * élément de leur liste, variables formatées. Une variable inconnue du catalogue s'écrit telle quelle ({@code {{CODE}}}) : le
     * contrôle l'a signalée. Un paragraphe qui ne portait que des blocs, tous faux, disparaît (pas de ligne blanche à sa place).
     */
    public static String xhtml(JsonNode doc, ModelePvWord.Contexte ctx, Map<String, String> conditions, LongFunction<Image> images) {
        Rendu r = new Rendu(ctx, nom -> {
            String expression = conditions.get(nom);
            return expression != null && ConditionsModele.vraie(expression, cle -> cle.startsWith("saisi.")
                    ? ctx.saisis().get(cle.substring("saisi.".length())) : ctx.cles().get(cle));
        }, images);
        JsonNode page = doc.path("page");
        JsonNode marges = page.path("marges");
        String police = page.path("police").isString() ? page.path("police").asString() : "Times New Roman";
        double taille = page.path("taille").isNumber() ? page.path("taille").doubleValue() : 12;
        boolean paysage = "paysage".equals(page.path("orientation").asString(""));
        boolean premiere = doc.path("enteteSurPremiereSeulement").asBoolean(false);
        boolean entete = doc.path("entete").isObject();
        boolean pied = doc.path("piedDePage").isObject();
        StringBuilder h = new StringBuilder(8192);
        h.append("<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/><style>\n")
                .append("@page { size: A4 ").append(paysage ? "landscape" : "portrait").append("; margin: ")
                .append(mm(marges, "haut", 25)).append(' ').append(mm(marges, "droite", 20)).append(' ')
                .append(mm(marges, "bas", 20)).append(' ').append(mm(marges, "gauche", 25)).append(';')
                .append(entete && !premiere ? " @top-center { content: element(entete); vertical-align: bottom; }" : "")
                .append(pied ? " @bottom-center { content: element(pied); vertical-align: top; }" : "").append(" }\n")
                .append("body { margin: 0; font-family: '").append(police).append("'; font-size: ").append(fmt(taille))
                .append("pt; line-height: 1.15; color: #000000; }\n")
                .append("#entete { position: running(entete); width: 100%; } #pied { position: running(pied); width: 100%; }\n")
                .append("p, h1, h2, h3 { margin: 0; } h1 { font-size: 1.5em; } h2 { font-size: 1.25em; } h3 { font-size: 1.1em; }\n")
                .append("ul, ol { margin: 0; padding-left: 8mm; } table { border-collapse: collapse; }\n")
                .append("td, th { padding: 0.5mm 1.5mm; vertical-align: top; text-align: left; } th { font-weight: bold; }\n")
                .append(".np::after { content: counter(page); } .nps::after { content: counter(pages); }\n")
                .append("</style></head><body>");
        if (pied) {
            h.append("<div id=\"pied\">");
            r.blocs(doc.path("piedDePage"), h, null);
            h.append("</div>");
        }
        if (entete) {
            h.append(premiere ? "<div>" : "<div id=\"entete\">");
            r.blocs(doc.path("entete"), h, null);
            h.append("</div>");
        }
        r.blocs(doc.path("corps"), h, null);
        return h.append("</body></html>").toString();
    }

    private static final class Rendu {

        private final ModelePvWord.Contexte ctx;
        private final Predicate<String> vraie;
        private final LongFunction<Image> images;

        Rendu(ModelePvWord.Contexte ctx, Predicate<String> vraie, LongFunction<Image> images) {
            this.ctx = ctx;
            this.vraie = vraie;
            this.images = images;
        }

        void blocs(JsonNode parent, StringBuilder h, Map<String, ModelePvWord.Valeur> item) {
            for (JsonNode n : parent.path("content")) {
                bloc(n, h, item);
            }
        }

        void bloc(JsonNode n, StringBuilder h, Map<String, ModelePvWord.Valeur> item) {
            JsonNode a = n.path("attrs");
            switch (type(n)) {
                case "paragraph", "heading" -> {
                    String balise = "heading".equals(type(n)) ? "h" + Math.max(1, Math.min(3, a.path("level").asInt(1))) : "p";
                    StringBuilder dedans = new StringBuilder();
                    enLigne(n, dedans, item);
                    boolean queDesBlocs = n.path("content").isArray() && !n.path("content").isEmpty();
                    for (JsonNode c : n.path("content")) {
                        queDesBlocs &= "bloc".equals(type(c));
                    }
                    if (queDesBlocs && dedans.isEmpty()) {
                        return;
                    }
                    h.append('<').append(balise).append(styleParagraphe(a)).append('>')
                            .append(dedans.isEmpty() ? "&#160;" : dedans).append("</").append(balise).append('>');
                }
                case "bulletList", "orderedList" -> {
                    String balise = "bulletList".equals(type(n)) ? "ul" : "ol";
                    h.append('<').append(balise);
                    if (a.path("start").isIntegralNumber()) {
                        h.append(" start=\"").append(a.path("start").intValue()).append('"');
                    }
                    h.append('>');
                    for (JsonNode li : n.path("content")) {
                        h.append("<li>");
                        blocs(li, h, item);
                        h.append("</li>");
                    }
                    h.append("</").append(balise).append('>');
                }
                case "table" -> table(n, h, item);
                case "image" -> {
                    String align = a.path("alignement").asString("center");
                    h.append("<div style=\"text-align: ").append(align).append(";\">");
                    image(a, h);
                    h.append("</div>");
                }
                case "pageBreak" -> h.append("<div style=\"page-break-before: always;\"></div>");
                case "bloc" -> {
                    if (vraie.test(a.path("condition").asString(""))) {
                        blocs(n, h, item);
                    }
                }
                default -> {
                    // validé : rien d'autre à cette place
                }
            }
        }

        void table(JsonNode t, StringBuilder h, Map<String, ModelePvWord.Valeur> item) {
            JsonNode a = t.path("attrs");
            boolean bordures = a.path("bordures").asBoolean(true);
            StringBuilder lignes = new StringBuilder();
            for (JsonNode ligne : t.path("content")) {
                JsonNode liste = ligne.path("attrs").path("liste");
                if (liste.isString()) {
                    String prefixe = CatalogueModelesPv.liste(liste.asString()).map(CatalogueModelesPv.Liste::prefixe).orElse(null);
                    for (Map<String, ModelePvWord.Valeur> element : ctx.listes().getOrDefault(liste.asString(), List.of())) {
                        ligne(ligne, lignes, bordures, prefixe == null ? item : element);
                    }
                } else {
                    ligne(ligne, lignes, bordures, item);
                }
            }
            if (lignes.isEmpty()) {
                return;
            }
            h.append("<table style=\"width: ").append(fmt(a.path("largeur").asDouble(100))).append("%;");
            if (a.path("retrait").isNumber()) {
                h.append(" margin-left: ").append(fmt(a.path("retrait").doubleValue())).append("mm;");
            }
            h.append(" table-layout: fixed;\">");
            List<Double> colonnes = colonnes(t);
            if (!colonnes.isEmpty()) {
                double total = colonnes.stream().mapToDouble(Double::doubleValue).sum();
                h.append("<colgroup>");
                for (double c : colonnes) {
                    h.append("<col style=\"width: ").append(fmt(100 * c / total)).append("%;\"/>");
                }
                h.append("</colgroup>");
            }
            h.append("<tbody>").append(lignes).append("</tbody></table>");
        }

        void ligne(JsonNode ligne, StringBuilder h, boolean bordures, Map<String, ModelePvWord.Valeur> item) {
            h.append("<tr>");
            for (JsonNode c : ligne.path("content")) {
                JsonNode a = c.path("attrs");
                String balise = "tableHeader".equals(type(c)) ? "th" : "td";
                h.append('<').append(balise);
                if (a.path("colspan").asInt(1) > 1) {
                    h.append(" colspan=\"").append(a.path("colspan").asInt()).append('"');
                }
                if (a.path("rowspan").asInt(1) > 1) {
                    h.append(" rowspan=\"").append(a.path("rowspan").asInt()).append('"');
                }
                h.append(" style=\"");
                if (a.path("bordures").asBoolean(bordures)) {
                    h.append("border: 0.5pt solid #000000; ");
                }
                if (a.path("fond").isString()) {
                    h.append("background-color: ").append(a.path("fond").asString()).append("; ");
                }
                String va = a.path("alignementVertical").asString("haut");
                h.append("vertical-align: ").append("milieu".equals(va) ? "middle" : "bas".equals(va) ? "bottom" : "top").append(";\">");
                blocs(c, h, item);
                h.append("</").append(balise).append('>');
            }
            h.append("</tr>");
        }

        void enLigne(JsonNode parent, StringBuilder h, Map<String, ModelePvWord.Valeur> item) {
            for (JsonNode n : parent.path("content")) {
                JsonNode a = n.path("attrs");
                switch (type(n)) {
                    case "text" -> marque(n.path("marks"), echapper(n.path("text").asString("")), h);
                    case "hardBreak" -> h.append("<br/>");
                    case "variable" -> {
                        String code = a.path("code").asString("");
                        String format = a.path("format").isString() ? a.path("format").asString() : null;
                        CatalogueModelesPv.Variable v = CatalogueModelesPv.variable(code).orElse(null);
                        String valeur = v == null ? "{{" + code + "}}"
                                : ModelePvWord.formater(v.liste() != null ? (item == null ? null : item.get(v.code())) : ctx.variables().get(v.code()),
                                        format);
                        marque(n.path("marks"), lignes(valeur), h);
                    }
                    case "champ" -> marque(n.path("marks"), lignes(ctx.saisis() == null ? null : ctx.saisis().get(a.path("code").asString(""))), h);
                    case "numeroPage" -> h.append("<span class=\"np\"></span>");
                    case "nombrePages" -> h.append("<span class=\"nps\"></span>");
                    case "image" -> image(a, h);
                    case "bloc" -> {
                        if (vraie.test(a.path("condition").asString(""))) {
                            enLigne(n, h, item);
                        }
                    }
                    default -> {
                        // validé : rien d'autre à cette place
                    }
                }
            }
        }

        void image(JsonNode a, StringBuilder h) {
            Image img = images.apply(a.path("idImage").asLong(-1));
            if (img == null) {
                return;
            }
            double largeur = a.path("largeur").isNumber() ? a.path("largeur").doubleValue() : Math.min(160, img.largeur() * 25.4 / 96);
            double hauteur = largeur * img.hauteur() / Math.max(1, img.largeur());
            h.append("<img src=\"data:").append(img.type()).append(";base64,").append(Base64.getEncoder().encodeToString(img.contenu()))
                    .append("\" style=\"width: ").append(fmt(largeur)).append("mm; height: ").append(fmt(hauteur)).append("mm;\"/>");
        }
    }

    private static void marque(JsonNode marks, String texte, StringBuilder h) {
        StringBuilder style = new StringBuilder();
        boolean gras = false;
        boolean italique = false;
        boolean souligne = false;
        for (JsonNode m : marks) {
            switch (type(m)) {
                case "bold" -> gras = true;
                case "italic" -> italique = true;
                case "underline" -> souligne = true;
                case "textStyle" -> {
                    JsonNode a = m.path("attrs");
                    if (a.path("fontFamily").isString()) {
                        style.append("font-family: '").append(a.path("fontFamily").asString()).append("'; ");
                    }
                    Double t = a.path("fontSize").isMissingNode() || a.path("fontSize").isNull() ? null : taille(a.path("fontSize"));
                    if (t != null) {
                        style.append("font-size: ").append(fmt(t)).append("pt; ");
                    }
                    if (a.path("color").isString()) {
                        style.append("color: ").append(a.path("color").asString()).append("; ");
                    }
                }
                default -> {
                    // validé
                }
            }
        }
        if (gras) {
            style.append("font-weight: bold; ");
        }
        if (italique) {
            style.append("font-style: italic; ");
        }
        if (souligne) {
            style.append("text-decoration: underline; ");
        }
        if (style.isEmpty() || texte.isEmpty()) {
            h.append(texte);
        } else {
            h.append("<span style=\"").append(style.toString().trim()).append("\">").append(texte).append("</span>");
        }
    }

    private static String styleParagraphe(JsonNode a) {
        StringBuilder s = new StringBuilder();
        if (a.path("textAlign").isString()) {
            s.append("text-align: ").append(a.path("textAlign").asString()).append("; ");
        }
        ajouter(s, a, "retrait", "margin-left", "mm");
        ajouter(s, a, "retraitDroite", "margin-right", "mm");
        ajouter(s, a, "retraitPremiereLigne", "text-indent", "mm");
        ajouter(s, a, "espaceAvant", "margin-top", "pt");
        ajouter(s, a, "espaceApres", "margin-bottom", "pt");
        ajouter(s, a, "interligne", "line-height", "");
        return s.isEmpty() ? "" : " style=\"" + s.toString().trim() + "\"";
    }

    private static void ajouter(StringBuilder s, JsonNode a, String cle, String propriete, String unite) {
        if (a.path(cle).isNumber()) {
            s.append(propriete).append(": ").append(fmt(a.path(cle).doubleValue())).append(unite).append("; ");
        }
    }

    /** Les largeurs de colonnes (pixels de l'éditeur) lues sur la première ligne, si chaque colonne en a une. */
    private static List<Double> colonnes(JsonNode table) {
        List<Double> out = new ArrayList<>();
        JsonNode premiere = table.path("content").path(0);
        for (JsonNode c : premiere.path("content")) {
            int colspan = Math.max(1, c.path("attrs").path("colspan").asInt(1));
            JsonNode cw = c.path("attrs").path("colwidth");
            for (int i = 0; i < colspan; i++) {
                if (!cw.isArray() || !cw.path(i).isNumber()) {
                    return List.of();
                }
                out.add(cw.path(i).doubleValue());
            }
        }
        return out;
    }

    private static String mm(JsonNode marges, String cle, double defaut) {
        return fmt(marges.path(cle).isNumber() ? marges.path(cle).doubleValue() : defaut) + "mm";
    }

    private static String lignes(String valeur) {
        return valeur == null ? "" : echapper(valeur).replace("\r\n", "\n").replace("\n", "<br/>");
    }

    static String echapper(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    if (c >= 0x20 || c == '\n' || c == '\t') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    // ───────────────────────────────────────────── PDF ─────────────────────────────────────────────

    private static final Map<String, byte[]> POLICES_EMBARQUEES = new LinkedHashMap<>();

    static {
        // Le moteur journalise chaque rendu au niveau INFO : seuls ses avertissements comptent ici.
        for (String journal : com.openhtmltopdf.util.XRLog.listRegisteredLoggers()) {
            com.openhtmltopdf.util.XRLog.setLevel(journal, java.util.logging.Level.WARNING);
        }
    }

    /** Le PDF du XHTML de page, polices Liberation embarquées sous les noms de l'éditeur. */
    public static byte[] pdf(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();
            for (String[] p : new String[][] { { "Times New Roman", "Serif" }, { "Arial", "Sans" } }) {
                police(b, p[0], "Liberation" + p[1] + "-Regular.ttf", 400, FontStyle.NORMAL);
                police(b, p[0], "Liberation" + p[1] + "-Bold.ttf", 700, FontStyle.NORMAL);
                police(b, p[0], "Liberation" + p[1] + "-Italic.ttf", 400, FontStyle.ITALIC);
                police(b, p[0], "Liberation" + p[1] + "-BoldItalic.ttf", 700, FontStyle.ITALIC);
            }
            b.withHtmlContent(xhtml, null);
            b.toStream(out);
            b.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void police(PdfRendererBuilder b, String famille, String fichier, int graisse, FontStyle style) {
        byte[] octets;
        synchronized (POLICES_EMBARQUEES) {
            octets = POLICES_EMBARQUEES.computeIfAbsent(fichier, f -> {
                try (InputStream in = ModelePvPage.class.getResourceAsStream("/fonts/liberation/" + f)) {
                    if (in == null) {
                        throw new IllegalStateException("Police absente du JAR : " + f);
                    }
                    return in.readAllBytes();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        b.useFont((FSSupplier<InputStream>) () -> new java.io.ByteArrayInputStream(octets), famille, graisse, style, true);
    }

    // ───────────────────────────────────────────── construction ─────────────────────────────────────────────

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    /**
     * Les nœuds en ligne d'un texte écrit avec les marqueurs du Word ({@code {{CODE|format}}}, {@code {{SAISI:code}}},
     * {@code {{SI:NOM}}…{{FINSI:NOM}}}) : textes, variables, champs et blocs en ligne. {@code marques} s'applique aux textes.
     */
    public static ArrayNode enLigne(String texte, JsonNode marques) {
        Deque<ArrayNode> pile = new ArrayDeque<>();
        ArrayNode racine = F.arrayNode();
        pile.push(racine);
        Matcher m = MARQUEUR.matcher(texte);
        int pos = 0;
        while (m.find()) {
            ajouterTexte(pile.peek(), texte.substring(pos, m.start()), marques);
            pos = m.end();
            if ("SI".equals(m.group(1))) {
                ObjectNode bloc = noeud("bloc");
                bloc.putObject("attrs").put("condition", m.group(2));
                pile.peek().add(bloc);
                pile.push(bloc.putArray("content"));
            } else if ("FINSI".equals(m.group(1))) {
                if (pile.size() > 1) {
                    pile.pop();
                }
            } else if ("SAISI".equals(m.group(1))) {
                ObjectNode c = noeud("champ");
                c.putObject("attrs").put("code", m.group(2));
                marquer(c, marques);
                pile.peek().add(c);
            } else {
                ObjectNode v = noeud("variable");
                ObjectNode a = v.putObject("attrs").put("code", m.group(3));
                if (m.group(4) != null) {
                    a.put("format", m.group(4));
                }
                marquer(v, marques);
                pile.peek().add(v);
            }
        }
        ajouterTexte(pile.peek(), texte.substring(pos), marques);
        return racine;
    }

    private static void ajouterTexte(ArrayNode cible, String texte, JsonNode marques) {
        if (!texte.isEmpty()) {
            cible.add(marquer(noeud("text").put("text", texte), marques));
        }
    }

    private static ObjectNode marquer(ObjectNode n, JsonNode marques) {
        if (marques != null && !marques.isEmpty()) {
            n.set("marks", marques.deepCopy());
        }
        return n;
    }

    static ObjectNode noeud(String type) {
        return F.objectNode().put("type", type);
    }

    /** Le schéma servi au front : nœuds, attributs et listes fermées (§B1). */
    public static Map<String, Object> schema() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("enveloppe", List.of("page", "entete", "enteteSurPremiereSeulement", "corps", "piedDePage"));
        s.put("page", Map.of("format", List.of("A4"), "orientation", ORIENTATIONS, "marges", "haut, bas, gauche, droite en mm (0 à 60)",
                "police", POLICES, "taille", "points, " + (int) TAILLE_MIN + " à " + (int) TAILLE_MAX_PT));
        Map<String, Object> noeuds = new LinkedHashMap<>();
        noeuds.put("paragraph", List.of("textAlign", "retrait", "retraitDroite", "retraitPremiereLigne", "espaceAvant", "espaceApres",
                "interligne"));
        noeuds.put("heading", List.of("level", "textAlign", "retrait", "retraitDroite", "retraitPremiereLigne", "espaceAvant",
                "espaceApres", "interligne"));
        noeuds.put("bulletList", List.of());
        noeuds.put("orderedList", List.of("start"));
        noeuds.put("listItem", List.of());
        noeuds.put("table", List.of("bordures", "retrait", "largeur"));
        noeuds.put("tableRow", List.of("liste"));
        noeuds.put("tableCell", List.of("colspan", "rowspan", "colwidth", "fond", "alignementVertical", "bordures"));
        noeuds.put("tableHeader", List.of("colspan", "rowspan", "colwidth", "fond", "alignementVertical", "bordures"));
        noeuds.put("image", List.of("idImage", "largeur", "alignement"));
        noeuds.put("pageBreak", List.of());
        noeuds.put("bloc", List.of("condition", "enLigne"));
        noeuds.put("text", List.of("text", "marks"));
        noeuds.put("hardBreak", List.of());
        noeuds.put("variable", List.of("code", "format", "marks"));
        noeuds.put("champ", List.of("code", "marks"));
        noeuds.put("numeroPage", List.of());
        noeuds.put("nombrePages", List.of());
        s.put("noeuds", noeuds);
        s.put("blocs", BLOCS.stream().sorted().toList());
        s.put("enLigne", EN_LIGNE.stream().sorted().toList());
        s.put("marques", Map.of("bold", List.of(), "italic", List.of(), "underline", List.of(), "textStyle",
                List.of("fontFamily", "fontSize", "color")));
        s.put("alignements", ALIGNEMENTS);
        s.put("polices", POLICES);
        s.put("couleurs", COULEURS);
        s.put("fonds", FONDS);
        s.put("formats", CatalogueModelesPv.FORMATS);
        s.put("limites", Map.of("tailleOctets", TAILLE_MAX, "profondeur", PROFONDEUR_MAX, "noeuds", NOEUDS_MAX, "texte", TEXTE_MAX));
        return s;
    }

    private static Map<String, String> ordonnee(String... cv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < cv.length; i += 2) {
            m.put(cv[i], cv[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }
}
