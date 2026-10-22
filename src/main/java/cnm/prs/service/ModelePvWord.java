package cnm.prs.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », §B2 et §B5, lot G1) — le <strong>moteur des modèles de PV</strong> :
 * il lit et remplit un {@code .docx} rédigé dans Word par l'Administrateur, en gardant sa mise en page (arbitrage du pilote :
 * « fidèle au Word »). Pur : des octets et un contexte en entrée, des octets en sortie.
 *
 * <p>Syntaxe : {@code {{CODE}}}, {@code {{CODE|format}}}, {@code {{SAISI:code}}}, {@code {{SI:NOM}} … {{FINSI:NOM}}} (dans un même
 * paragraphe, ou sur plusieurs paragraphes et tableaux d'une même zone), {@code {{POUR:LISTE}}} dans la première cellule d'une ligne
 * de tableau (la ligne est répétée par élément), et les chevrons des modèles du plan ({@link CatalogueModelesPv#ALIAS}).</p>
 *
 * <p><strong>Le piège de Word</strong> : un {@code {{PV.REFERENCE}}} tapé d'un trait est souvent rangé dans plusieurs morceaux
 * (correcteur, révisions, changement de police). Avant toute lecture, chaque marqueur est <strong>recollé</strong> dans le morceau
 * où il commence, les autres gardent leur mise en forme. Un marqueur coupé par autre chose que du texte (tabulation, saut, image)
 * ne se recolle pas : le contrôle le signale, bloquant.</p>
 *
 * <p>Le moteur des DAO ({@link ModelesDao}) ne remplit pas de Word : il rend des fichiers de commande {@code .txt}. Seules sa
 * grammaire de conditions ({@link ConditionsModele}) et {@link NombreEnLettres} sont reprises ici.</p>
 */
public final class ModelePvWord {

    /** Une valeur du contexte : un texte, ou une date (formatée selon le {@code |format}). */
    public record Valeur(String texte, LocalDate date) {

        public static Valeur de(String texte) {
            return new Valeur(texte, null);
        }

        public static Valeur date(LocalDate date) {
            return new Valeur(null, date);
        }
    }

    /** Ce que le modèle lit : variables, listes répétées, clés de condition, champs saisis. */
    public record Contexte(Map<String, Valeur> variables, Map<String, List<Map<String, Valeur>>> listes, Map<String, String> cles,
            Map<String, String> saisis) {
    }

    /**
     * Un marqueur relevé dans le fichier : {@code type} parmi {@code VARIABLE}, {@code ALIAS}, {@code SAISI}, {@code SI},
     * {@code FINSI}, {@code POUR}, {@code IMAGE} ; {@code liste} est la liste de la ligne répétée qui le contient, ou nul.
     */
    public record Marqueur(String brut, String type, String code, String format, String ou, String liste, boolean premiereCellule) {
    }

    /** Un défaut de structure relevé à la lecture (marqueur coupé, bloc mal fermé). */
    public record Defaut(String code, String message, String ou) {
    }

    public record Releve(List<Marqueur> marqueurs, List<Defaut> defauts) {
    }

    private static final Pattern BLOC = Pattern.compile("\\{\\{(SI|FINSI):([A-Za-z0-9_-]+)}}");
    private static final Pattern POUR = Pattern.compile("\\{\\{POUR:([A-Za-z0-9_]+)}}");
    private static final Pattern TOUT = Pattern.compile("\\{\\{[^{}]{1,200}}}|" + CatalogueModelesPv.ALIAS.keySet().stream()
            .map(Pattern::quote).collect(Collectors.joining("|")));
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter COURT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ModelePvWord() {
    }

    /** Un paragraphe du document, avec son emplacement lisible et la zone (corps, cellule, en-tête) qui le porte. */
    private record Para(XWPFParagraph p, String ou, IBody zone, String liste, boolean premiereCellule, List<String> coupes) {
    }

    // ───────────────────────────────────────────── lecture ─────────────────────────────────────────────

    /** Relève les marqueurs du fichier, dans l'ordre, et ses défauts de structure. Lève {@link IOException} s'il n'est pas lisible. */
    public static Releve relever(byte[] docx) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<Para> paras = paragraphes(doc);
            List<Marqueur> marqueurs = new ArrayList<>();
            List<Defaut> defauts = new ArrayList<>();
            Map<IBody, Deque<String>> piles = new IdentityHashMap<>();
            Map<IBody, String> lieux = new IdentityHashMap<>();
            for (Para pa : paras) {
                for (String c : pa.coupes()) {
                    defauts.add(new Defaut("MARQUEUR_COUPE", "Le marqueur « " + c + " » est coupé par autre chose que du texte "
                            + "(tabulation, saut, image) : retapez-le d'un seul tenant.", pa.ou()));
                }
                Deque<String> pile = piles.computeIfAbsent(pa.zone(), z -> new ArrayDeque<>());
                lieux.putIfAbsent(pa.zone(), pa.ou());
                Matcher m = TOUT.matcher(pa.p().getText());
                while (m.find()) {
                    Marqueur mk = classer(m.group(), pa.ou(), pa.liste(), pa.premiereCellule());
                    marqueurs.add(mk);
                    if ("SI".equals(mk.type())) {
                        pile.push(mk.code());
                    } else if ("FINSI".equals(mk.type())) {
                        if (mk.code().equals(pile.peek())) {
                            pile.pop();
                        } else {
                            defauts.add(new Defaut("BLOC_MAL_FERME", "{{FINSI:" + mk.code() + "}} ne ferme pas le dernier bloc ouvert ("
                                    + (pile.isEmpty() ? "aucun" : "{{SI:" + pile.peek() + "}}") + ").", pa.ou()));
                        }
                    }
                }
            }
            piles.forEach((zone, pile) -> {
                if (!pile.isEmpty()) {
                    defauts.add(new Defaut("BLOC_NON_FERME", "Bloc(s) ouvert(s) sans {{FINSI}} dans la même zone : "
                            + pile.stream().map(n -> "{{SI:" + n + "}}").collect(Collectors.joining(", "))
                            + ". Un bloc se ferme dans la zone où il s'ouvre (corps, même cellule, même en-tête).", lieux.get(zone)));
                }
            });
            return new Releve(marqueurs, defauts);
        }
    }

    static Marqueur classer(String brut, String ou, String liste, boolean premiereCellule) {
        if (!brut.startsWith("{{")) {
            return new Marqueur(brut, "ALIAS", CatalogueModelesPv.ALIAS.get(brut), null, ou, liste, premiereCellule);
        }
        String interieur = brut.substring(2, brut.length() - 2).trim();
        for (String type : List.of("SI", "FINSI", "POUR", "SAISI", "IMAGE")) {
            if (interieur.startsWith(type + ":")) {
                return new Marqueur(brut, type, interieur.substring(type.length() + 1).trim(), null, ou, liste, premiereCellule);
            }
        }
        int barre = interieur.indexOf('|');
        return barre < 0 ? new Marqueur(brut, "VARIABLE", interieur, null, ou, liste, premiereCellule)
                : new Marqueur(brut, "VARIABLE", interieur.substring(0, barre).trim(), interieur.substring(barre + 1).trim(), ou, liste,
                        premiereCellule);
    }

    // ───────────────────────────────────────────── remplissage ─────────────────────────────────────────────

    /**
     * Remplit le modèle : lignes répétées, puis blocs conditionnels (conditions déclarées, évaluées sur {@code ctx.cles()} et
     * {@code saisi.<code>}), puis variables. Une variable inconnue reste telle quelle (le contrôle l'a signalée) ; une variable
     * connue sans valeur s'efface. Le document est relu entre deux étapes, pour que chacune parte d'un état cohérent.
     */
    public static byte[] remplir(byte[] docx, Contexte ctx, Map<String, String> conditions) throws IOException {
        byte[] b = etape(docx, doc -> {
            paragraphes(doc);
            for (IBody zone : zones(doc)) {
                repeter(zone.getTables(), ctx);
            }
        });
        Predicate<String> vraie = nom -> {
            String expression = conditions.get(nom);
            return expression != null && ConditionsModele.vraie(expression, cle -> lire(ctx, cle));
        };
        b = etape(b, doc -> zones(doc).forEach(z -> blocs(z, vraie)));
        return etape(b, doc -> {
            for (Para pa : paragraphes(doc)) {
                remplacer(pa.p(), brut -> resoudre(brut, ctx, null));
            }
        });
    }

    /**
     * Ce qu'une condition lit : un champ saisi ({@code saisi.<code>}), une clé de condition, ⚠️ 2026-10-22 (« modele-pv-plan-depart »)
     * ou une variable du catalogue ({@code PV.PRESIDENT renseigne} : la ligne du Président sous un nom seulement, comme le générateur
     * intégré). Partagé par le Word et la page.
     */
    static String lire(Contexte ctx, String cle) {
        if (cle.startsWith("saisi.")) {
            return ctx.saisis().get(cle.substring("saisi.".length()));
        }
        if (ctx.cles().containsKey(cle)) {
            return ctx.cles().get(cle);
        }
        Valeur v = ctx.variables().get(cle);
        return v == null ? null : v.texte() != null ? v.texte() : v.date() == null ? null : v.date().toString();
    }

    private interface Etape {
        void appliquer(XWPFDocument doc) throws IOException;
    }

    private static byte[] etape(byte[] docx, Etape etape) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            etape.appliquer(doc);
            doc.write(out);
            return out.toByteArray();
        }
    }

    /** La valeur d'un marqueur, ou {@code null} pour le laisser tel quel. {@code item} : l'élément de la ligne répétée. */
    private static String resoudre(String brut, Contexte ctx, Map<String, Valeur> item) {
        Marqueur m = classer(brut, null, null, false);
        switch (m.type()) {
            case "SAISI":
                return nz(ctx.saisis().get(m.code()));
            case "IMAGE", "SI", "FINSI", "POUR":
                return "";
            default:
                break;
        }
        CatalogueModelesPv.Variable v = CatalogueModelesPv.variable(m.code()).orElse(null);
        if (v == null) {
            return null;
        }
        if (v.liste() != null) {
            return item == null ? null : formater(item.get(v.code()), m.format());
        }
        return formater(ctx.variables().get(v.code()), m.format());
    }

    static String formater(Valeur v, String format) {
        if (v == null) {
            return "";
        }
        String s;
        if (v.date() != null) {
            s = "court".equals(format) ? v.date().format(COURT)
                    : "lettres".equals(format) ? NombreEnLettres.dateExamenPourLAn(v.date()) : v.date().format(LONG);
        } else {
            s = nz(v.texte());
            if ("lettres".equals(format) && s.matches("\\d{1,12}")) {
                s = NombreEnLettres.cardinal(Long.parseLong(s));
            }
        }
        if ("majuscules".equals(format)) {
            return s.toUpperCase(Locale.FRENCH);
        }
        return "minuscules".equals(format) ? s.toLowerCase(Locale.FRENCH) : s;
    }

    /** Répète chaque ligne {@code {{POUR:LISTE}}} par élément de la liste (aucun élément : la ligne disparaît). */
    private static void repeter(List<XWPFTable> tables, Contexte ctx) throws IOException {
        for (XWPFTable t : tables) {
            for (int i = t.getRows().size() - 1; i >= 0; i--) {
                XWPFTableRow ligne = t.getRow(i);
                for (XWPFTableCell c : ligne.getTableCells()) {
                    repeter(c.getTables(), ctx);
                }
                if (ligne.getTableCells().isEmpty()) {
                    continue;
                }
                Matcher m = POUR.matcher(ligne.getTableCells().get(0).getText());
                if (!m.find()) {
                    continue;
                }
                String liste = m.group(1);
                List<Map<String, Valeur>> items = ctx.listes().getOrDefault(liste, List.of());
                for (int k = 0; k < items.size(); k++) {
                    CTRow copie;
                    try {
                        copie = CTRow.Factory.parse(ligne.getCtRow().newInputStream());
                    } catch (org.apache.xmlbeans.XmlException e) {
                        throw new IOException(e);
                    }
                    XWPFTableRow nouvelle = new XWPFTableRow(copie, t);
                    Map<String, Valeur> item = items.get(k);
                    for (XWPFTableCell c : nouvelle.getTableCells()) {
                        for (XWPFParagraph p : c.getParagraphs()) {
                            remplacer(p, brut -> {
                                Marqueur mk = classer(brut, null, null, false);
                                if ("POUR".equals(mk.type())) {
                                    return "";
                                }
                                String code = mk.code();
                                return code != null && code.startsWith(prefixe(liste) + ".") ? resoudre(brut, ctx, item) : null;
                            });
                        }
                    }
                    // addRow copie le XML de la ligne : elle est remplie avant d'être insérée.
                    t.addRow(nouvelle, i + 1 + k);
                }
                t.removeRow(i);
            }
        }
    }

    private static String prefixe(String liste) {
        return CatalogueModelesPv.liste(liste).map(CatalogueModelesPv.Liste::prefixe).orElse(liste);
    }

    /**
     * Les blocs d'une zone : un morceau de texte, un paragraphe ou un tableau compris dans un bloc faux disparaît ; un paragraphe
     * qui ne portait que des marqueurs disparaît aussi (pas de ligne blanche à leur place). Les cellules sont des zones à part.
     */
    private static void blocs(IBody zone, Predicate<String> vraie) {
        Deque<Boolean> pile = new ArrayDeque<>();
        for (IBodyElement el : new ArrayList<>(zone.getBodyElements())) {
            boolean visibleAvant = visible(pile);
            if (el instanceof XWPFParagraph p) {
                boolean marqueur = false;
                boolean garde = false;
                List<XWPFRun> runs = p.getRuns();
                List<Integer> aRetirer = new ArrayList<>();
                for (int i = 0; i < runs.size(); i++) {
                    XWPFRun r = runs.get(i);
                    String t = simple(r) ? texte(r) : null;
                    if (t != null && BLOC.matcher(t).find()) {
                        marqueur = true;
                        StringBuilder reste = new StringBuilder();
                        Matcher m = BLOC.matcher(t);
                        int pos = 0;
                        while (m.find()) {
                            if (visible(pile)) {
                                reste.append(t, pos, m.start());
                            }
                            if ("SI".equals(m.group(1))) {
                                pile.push(vraie.test(m.group(2)));
                            } else if (!pile.isEmpty()) {
                                pile.pop();
                            }
                            pos = m.end();
                        }
                        if (visible(pile)) {
                            reste.append(t.substring(pos));
                        }
                        if (reste.length() == 0) {
                            aRetirer.add(i);
                        } else {
                            r.setText(reste.toString(), 0);
                            garde |= !reste.toString().isBlank();
                        }
                    } else if (!visible(pile)) {
                        aRetirer.add(i);
                    } else if (!r.text().isBlank() || !r.getEmbeddedPictures().isEmpty()) {
                        garde = true;
                    }
                }
                for (int k = aRetirer.size() - 1; k >= 0; k--) {
                    p.removeRun(aRetirer.get(k));
                }
                if (!garde && (marqueur || !visibleAvant)) {
                    retirer(zone, p);
                }
            } else if (el instanceof XWPFTable t) {
                if (!visible(pile)) {
                    retirer(t.getCTTbl().getDomNode());
                } else {
                    for (XWPFTableRow ligne : t.getRows()) {
                        for (XWPFTableCell c : ligne.getTableCells()) {
                            blocs(c, vraie);
                        }
                    }
                }
            }
        }
    }

    private static boolean visible(Deque<Boolean> pile) {
        return !pile.contains(Boolean.FALSE);
    }

    /**
     * Retire un paragraphe. Une cellule garde au moins un paragraphe (Word l'exige), et un paragraphe qui porte un saut de section
     * reste en place, vidé : le retirer changerait la mise en page des pages suivantes.
     */
    private static void retirer(IBody zone, XWPFParagraph p) {
        boolean saut = p.getCTP().getPPr() != null && p.getCTP().getPPr().getSectPr() != null;
        boolean dernierDeCellule = zone instanceof XWPFTableCell c && paragraphesRestants(c) <= 1;
        if (saut || dernierDeCellule) {
            for (int i = p.getRuns().size() - 1; i >= 0; i--) {
                p.removeRun(i);
            }
            return;
        }
        retirer(p.getCTP().getDomNode());
    }

    private static int paragraphesRestants(XWPFTableCell c) {
        int n = 0;
        NodeList enfants = c.getCTTc().getDomNode().getChildNodes();
        for (int i = 0; i < enfants.getLength(); i++) {
            if ("p".equals(enfants.item(i).getLocalName())) {
                n++;
            }
        }
        return n;
    }

    private static void retirer(Node n) {
        if (n.getParentNode() != null) {
            n.getParentNode().removeChild(n);
        }
    }

    /** Remplace les marqueurs d'un paragraphe ; une valeur sur plusieurs lignes devient des sauts de ligne. */
    static void remplacer(XWPFParagraph p, Function<String, String> resoudre) {
        for (XWPFRun r : p.getRuns()) {
            boolean simple = simple(r);
            int nt = r.getCTR().sizeOfTArray();
            for (int k = 0; k < nt; k++) {
                String t = r.getText(k);
                if (t == null || (!t.contains("{{") && !t.contains("<"))) {
                    continue;
                }
                Matcher m = TOUT.matcher(t);
                StringBuilder sb = new StringBuilder();
                boolean change = false;
                while (m.find()) {
                    String v = resoudre.apply(m.group());
                    change |= v != null;
                    m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? m.group() : v));
                }
                if (!change) {
                    continue;
                }
                m.appendTail(sb);
                String res = sb.toString();
                if (simple && res.contains("\n")) {
                    String[] lignes = res.split("\n", -1);
                    r.setText(lignes[0], 0);
                    for (int i = 1; i < lignes.length; i++) {
                        r.addBreak();
                        r.setText(lignes[i]);
                    }
                    break;
                }
                r.setText(res.replace('\n', ' '), k);
            }
        }
    }

    // ───────────────────────────────────────────── parcours ─────────────────────────────────────────────

    /** Le corps, puis les en-têtes, puis les pieds de page. */
    private static List<IBody> zones(XWPFDocument doc) {
        List<IBody> zones = new ArrayList<>();
        zones.add(doc);
        zones.addAll(doc.getHeaderList());
        zones.addAll(doc.getFooterList());
        return zones;
    }

    /** Tous les paragraphes du document, dans l'ordre, chacun recollé ; les cellules de tableau sont parcourues. */
    private static List<Para> paragraphes(XWPFDocument doc) {
        List<Para> out = new ArrayList<>();
        lister(doc, "", null, false, out);
        int n = 0;
        for (XWPFHeaderFooter h : doc.getHeaderList()) {
            lister(h, "en-tête " + (++n), null, false, out);
        }
        n = 0;
        for (XWPFHeaderFooter f : doc.getFooterList()) {
            lister(f, "pied de page " + (++n), null, false, out);
        }
        return out;
    }

    private static void lister(IBody zone, String lieu, String liste, boolean premiereCellule, List<Para> out) {
        int np = 0;
        int nt = 0;
        for (IBodyElement el : new ArrayList<>(zone.getBodyElements())) {
            if (el instanceof XWPFParagraph p) {
                out.add(new Para(p, lieu(lieu, "paragraphe " + (++np)), zone, liste, premiereCellule, recoller(p)));
            } else if (el instanceof XWPFTable t) {
                String base = lieu(lieu, "tableau " + (++nt));
                int l = 0;
                for (XWPFTableRow ligne : t.getRows()) {
                    l++;
                    List<XWPFTableCell> cellules = ligne.getTableCells();
                    String listeLigne = liste;
                    if (!cellules.isEmpty()) {
                        cellules.get(0).getParagraphs().forEach(ModelePvWord::recoller);
                        Matcher m = POUR.matcher(cellules.get(0).getText());
                        if (m.find()) {
                            listeLigne = m.group(1);
                        }
                    }
                    for (int c = 0; c < cellules.size(); c++) {
                        lister(cellules.get(c), base + ", ligne " + l + ", cellule " + (c + 1), listeLigne, c == 0, out);
                    }
                }
            }
        }
    }

    private static String lieu(String parent, String ici) {
        return parent.isEmpty() ? ici : parent + ", " + ici;
    }

    /**
     * Recolle dans un seul morceau chaque marqueur que Word a réparti sur plusieurs : le morceau où il commence reçoit le marqueur
     * entier, celui où il finit garde sa suite, ceux du milieu se vident. Renvoie les marqueurs restés coupés par un morceau qui
     * n'est pas du texte seul.
     */
    static List<String> recoller(XWPFParagraph p) {
        List<XWPFRun> runs = p.getRuns();
        List<String> coupes = new ArrayList<>();
        int n = runs.size();
        if (n < 2) {
            return coupes;
        }
        String[] textes = new String[n];
        int[] debut = new int[n];
        boolean[] simples = new boolean[n];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            simples[i] = simple(runs.get(i));
            textes[i] = simples[i] ? texte(runs.get(i)) : runs.get(i).text();
            debut[i] = sb.length();
            sb.append(textes[i]);
        }
        List<int[]> portees = new ArrayList<>();
        Matcher m = TOUT.matcher(sb);
        while (m.find()) {
            portees.add(new int[] { m.start(), m.end() });
        }
        for (int x = portees.size() - 1; x >= 0; x--) {
            int s = portees.get(x)[0];
            int e = portees.get(x)[1];
            int i = morceau(debut, textes, s);
            int j = morceau(debut, textes, e - 1);
            if (i == j || i < 0 || j < 0) {
                continue;
            }
            boolean texteSeul = true;
            for (int k = i; k <= j; k++) {
                texteSeul &= simples[k];
            }
            if (!texteSeul) {
                coupes.add(sb.substring(s, e));
                continue;
            }
            String avant = textes[i].substring(0, s - debut[i]);
            String apres = textes[j].substring(e - debut[j]);
            textes[i] = avant + sb.substring(s, e);
            runs.get(i).setText(textes[i], 0);
            for (int k = i + 1; k < j; k++) {
                textes[k] = "";
                runs.get(k).setText("", 0);
            }
            textes[j] = apres;
            runs.get(j).setText(apres, 0);
        }
        return coupes;
    }

    private static int morceau(int[] debut, String[] textes, int pos) {
        for (int i = 0; i < debut.length; i++) {
            if (pos >= debut[i] && pos < debut[i] + textes[i].length()) {
                return i;
            }
        }
        return -1;
    }

    /** Un morceau « texte seul » : mise en forme, au plus un texte, et rien d'autre (ni tabulation, ni saut, ni image, ni champ). */
    private static boolean simple(XWPFRun r) {
        NodeList enfants = r.getCTR().getDomNode().getChildNodes();
        int textes = 0;
        for (int i = 0; i < enfants.getLength(); i++) {
            Node e = enfants.item(i);
            if (e.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String nom = e.getLocalName();
            if ("t".equals(nom)) {
                textes++;
            } else if (!"rPr".equals(nom) && !"lastRenderedPageBreak".equals(nom)) {
                return false;
            }
        }
        return textes <= 1;
    }

    private static String texte(XWPFRun r) {
        String t = r.getText(0);
        return t == null ? "" : t;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Pour les tests et le rapport : le texte du document rempli, paragraphe par paragraphe (corps et cellules). */
    static String texte(byte[] docx) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            Map<String, String> lignes = new LinkedHashMap<>();
            List<Para> paras = new ArrayList<>();
            lister(doc, "", null, false, paras);
            for (Para pa : paras) {
                lignes.put(pa.ou(), pa.p().getText());
            }
            return String.join("\n", lignes.values());
        }
    }
}
