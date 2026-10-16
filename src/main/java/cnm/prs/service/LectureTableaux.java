package cnm.prs.service;

import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/**
 * ⚠️ 2026-10-16 (demande front « import-specifications », §B3-B4) — la lecture des <strong>articles</strong> d'un DAO par ses
 * <strong>tableaux</strong> : bordereau des prix (ou détail quantitatif), spécifications techniques, définition des prix (travaux).
 * Portage tel quel du prototype du front ({@code scripts/import-dao/PdfGrille.java} et {@code lire-specs.mjs}), contrôlé contre son
 * jeu de référence ({@code attendus-articles/*.json}) comme {@link LecturePdf} l'est pour la lecture par modèle (ADR-0012).
 *
 * <ul>
 *   <li><strong>PDF</strong> : les mots positionnés et les filets tracés de chaque page reconstituent la grille, puis les cellules
 *       (règles 1-2) ; un tableau coupé par un saut de page se recolle (règle 5).</li>
 *   <li><strong>Word</strong> (§B4) : les tableaux natifs portent leurs cellules ; seules les règles 3, 4 et 6 à 11 s'appliquent.
 *       Une cellule d'un seul paragraphe long vaut un texte « d'un seul tenant ».</li>
 * </ul>
 * Les expressions du prototype sont reprises avec la sémantique JavaScript ({@code \s} Unicode, {@code \b} ASCII).
 */
public final class LectureTableaux {

    private LectureTableaux() {
    }

    // ------------------------------------------------------------------ expressions au sens JavaScript

    /** {@code \s} de JavaScript : les espaces Unicode, dont l'espace insécable. */
    private static final String S = "[\\t\\n\\x0B\\f\\r \\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]";
    private static final String NS = "[^\\t\\n\\x0B\\f\\r \\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]";
    /** {@code \b} de JavaScript (sans drapeau u) : une frontière de mot ASCII. */
    private static final String B = "(?:(?<=[A-Za-z0-9_])(?![A-Za-z0-9_])|(?<![A-Za-z0-9_])(?=[A-Za-z0-9_]))";

    private static Pattern js(String re, int drapeaux) {
        return Pattern.compile(re.replace("\\s", S).replace("\\S", NS).replace("\\b", B), drapeaux);
    }

    private static Pattern js(String re) {
        return js(re, 0);
    }

    private static final int I = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern ESPACES = js("\\s+");
    private static final Pattern NOMBRES = Pattern.compile("\\d+(?:[,.]\\d+)?");
    private static final Pattern NON_MOT = Pattern.compile("[^A-Z0-9 ]");
    private static final Pattern MOT_VIDE = Pattern.compile("^(DES|LES|POUR|AVEC|PAR|SUR|UNE)$");
    private static final Pattern INSERER = js("\\s*\\(?\\s*ins[ée]rer le prix.*$", I);
    private static final Pattern SUFFIXE_PRIX = js("\\s*,?\\s*(?:l['’]\\s*|le\\s+|la\\s+)\\p{L}+\\s+à(?!\\p{L}).*$", I);
    private static final Pattern LOT = js("\\bLOT\\s*(?:N\\s*°?\\s*)?(\\d+)\\s*:", I);
    private static final Pattern NUMERO = Pattern.compile("^\\d{1,3}$");
    private static final Pattern PRIX_DEF = js("^Prix\\s*N\\s*[°o]?\\s*(\\d{1,3})$", I);
    private static final Pattern PRIX_SEUL = js("^Prix\\s*N\\s*[°o]?$", I);
    private static final Pattern COL_DESIG = Pattern.compile("DESIGNATION|DESCRIPTION");
    private static final Pattern COL_SPEC = Pattern.compile("SPECIFICATION|DESCRIPTION.*(?:DEMANDE|MINIMUM|REQUIS)");
    private static final Pattern SAUF_SPEC = Pattern.compile("PROPOS|CANDIDAT|DESIGNATION");
    private static final Pattern COL_UNITE = js("\\bUNITE\\b");
    private static final Pattern SAUF_UNITE = Pattern.compile("PRIX|DESIGNATION");
    private static final Pattern COL_QTE = js("QUANTITE|\\bQTE\\b");
    private static final Pattern COL_MIN = js("\\bMIN\\b");
    private static final Pattern COL_MAX = js("\\bMAX\\b");
    private static final Pattern SAUF_MONTANT = Pattern.compile("MONTANT");
    private static final Pattern NOMBRE = Pattern.compile("^\\d+(?:[,.]\\d+)?$");
    private static final Pattern PROSE_COUPE = js("\\s*(?:;|\\s\\+\\s?|\\+\\s|,\\s)\\s*");
    private static final Pattern MOT_A_CHIFFRE = js("^\\S*[\\d/]\\S*$");
    private static final Pattern FIN_LIANTE = js("[,\\-:/+]\\s*$");
    private static final Pattern MOT_OUTIL = js("\\b(?:de|du|des|à|au|aux|et|ou|avec|pour|en|par|sur)\\s*$", I);
    private static final Pattern DEBUT_LIGNE = Pattern.compile("^[\\p{Lu}\\d(«\"]");
    private static final Pattern POINT_VIRGULE = js("\\s*;(?!\\s*\\d)\\s*");
    private static final Pattern PUCES = js("\\s*[•✓]\\s*");

    // ------------------------------------------------------------------ le modèle

    /** Un mot positionné (y = ligne de base, origine en haut). */
    record Mot(double x0, double y, double x1, String t) {
    }

    record Filet(double a, double b0, double b1) {
    }

    /** Une cellule : ses colonnes de grille [{@code c0}, {@code c1}), ses lignes, « d'un seul tenant », son texte. */
    record Cellule(int c0, int c1, List<String> lignes, boolean prose, String texte) {
    }

    record Ligne(List<Cellule> cellules) {
    }

    /** Un tableau : sa page, ses lignes, le texte qui le précède (le lot), l'abscisse de ses colonnes (PDF, pour la suite). */
    record Tableau(int page, List<Ligne> lignes, String dessus, double[] colonnes, int nColonnes, boolean pdf) {
    }

    /** Un article lu (mutable, comme dans le prototype). */
    public static final class Article {
        public Integer lot;
        public int n;
        public String code;
        public String designationBrute;
        public String designation;
        public String designationSpec;
        public String unite;
        public BigDecimal quantite;
        public BigDecimal min;
        public BigDecimal max;
        public String serie;
        public String serieLibelle;
        public List<String> exigences;
        public boolean prose;
        public boolean definition;
        public Integer pageBordereau;
        public Integer pageSpec;
        public boolean bordereau;
        public List<Caracteristique> caracteristiques = List.of();
    }

    public record Caracteristique(String libelle, String exigence) {
    }

    // ------------------------------------------------------------------ entrée

    /** Les articles d'un {@code .pdf} ou d'un {@code .docx}, triés par lot puis numéro. */
    public static List<Article> lire(String nomFichier, byte[] contenu) throws IOException {
        String nom = nomFichier == null ? "" : nomFichier.toLowerCase(Locale.ROOT);
        return articles(nom.endsWith(".pdf") ? tableauxPdf(contenu) : tableauxDocx(contenu));
    }

    // ------------------------------------------------------------------ PDF : mots et filets (PdfGrille)

    static List<Tableau> tableauxPdf(byte[] contenu) throws IOException {
        Map<Integer, List<Mot>> mots = new LinkedHashMap<>();
        Map<Integer, List<Filet>> hs = new LinkedHashMap<>();
        Map<Integer, List<Filet>> vs = new LinkedHashMap<>();
        int nbPages;
        try (PDDocument doc = Loader.loadPDF(contenu)) {
            PDFTextStripper s = new PDFTextStripper() {
                @Override
                protected void writeString(String texte, List<TextPosition> positions) {
                    float l = getCurrentPage().getMediaBox().getWidth();
                    float h = getCurrentPage().getMediaBox().getHeight();
                    List<TextPosition> mot = new ArrayList<>();
                    TextPosition prec = null;
                    List<Mot> page = mots.computeIfAbsent(getCurrentPageNo(), k -> new ArrayList<>());
                    for (TextPosition p : positions) {
                        float x = p.getXDirAdj();
                        float y = p.getYDirAdj();
                        boolean incline = Math.abs(p.getTextMatrix().getShearY()) > 0.01f || Math.abs(p.getTextMatrix().getShearX()) > 0.01f;
                        if (p.getDir() != 0f || incline || x < 0 || x > l || y < 0 || y > h) {
                            continue;
                        }
                        boolean coupe = p.getUnicode().isBlank() || prec != null
                                && p.getXDirAdj() - (prec.getXDirAdj() + prec.getWidthDirAdj()) > Math.max(prec.getWidthOfSpace(), 1f) * 0.3f;
                        if (coupe) {
                            sortir(mot, page);
                            mot.clear();
                        }
                        if (!p.getUnicode().isBlank()) {
                            mot.add(p);
                        }
                        prec = p;
                    }
                    sortir(mot, page);
                }
            };
            s.setSortByPosition(true);
            s.getText(doc);
            int n = 0;
            for (PDPage page : doc.getPages()) {
                n++;
                new Filets(page, hs.computeIfAbsent(n, k -> new ArrayList<>()), vs.computeIfAbsent(n, k -> new ArrayList<>())).processPage(page);
            }
            nbPages = n;
        }
        List<Tableau> out = new ArrayList<>();
        for (int n = 1; n <= nbPages; n++) {
            out.addAll(tableaux(n, mots.getOrDefault(n, List.of()), hs.getOrDefault(n, List.of()), vs.getOrDefault(n, List.of())));
        }
        return out;
    }

    /** Une valeur arrondie au dixième, comme le TSV du prototype ({@code %.1f}). */
    private static double r1(double v) {
        return Double.parseDouble(String.format(Locale.ROOT, "%.1f", v));
    }

    private static void sortir(List<TextPosition> mot, List<Mot> page) {
        if (mot.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (TextPosition p : mot) {
            sb.append(p.getUnicode());
        }
        TextPosition d = mot.get(0);
        TextPosition f = mot.get(mot.size() - 1);
        page.add(new Mot(r1(d.getXDirAdj()), r1(d.getYDirAdj()), r1(f.getXDirAdj() + f.getWidthDirAdj()), sb.toString().replace('\t', ' ')));
    }

    /** Les traits et rectangles minces d'une page, en coordonnées « origine en haut » comme le texte. */
    private static final class Filets extends PDFGraphicsStreamEngine {
        private final List<Filet> h;
        private final List<Filet> v;
        private final float hauteur;
        private final List<float[]> chemin = new ArrayList<>();
        private Point2D courant;

        Filets(PDPage p, List<Filet> h, List<Filet> v) {
            super(p);
            this.h = h;
            this.v = v;
            this.hauteur = p.getMediaBox().getHeight();
        }

        private void segment(Point2D a, Point2D b) {
            chemin.add(new float[] { (float) a.getX(), hauteur - (float) a.getY(), (float) b.getX(), hauteur - (float) b.getY() });
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            float x0 = (float) Math.min(p0.getX(), p2.getX());
            float x1 = (float) Math.max(p0.getX(), p2.getX());
            float y0 = hauteur - (float) Math.max(p0.getY(), p2.getY());
            float y1 = hauteur - (float) Math.min(p0.getY(), p2.getY());
            // Un rectangle mince est un filet ; un grand rectangle donne ses quatre côtés.
            if (y1 - y0 < 2.5f) {
                chemin.add(new float[] { x0, (y0 + y1) / 2, x1, (y0 + y1) / 2 });
            } else if (x1 - x0 < 2.5f) {
                chemin.add(new float[] { (x0 + x1) / 2, y0, (x0 + x1) / 2, y1 });
            } else {
                chemin.add(new float[] { x0, y0, x1, y0, 1 });
                chemin.add(new float[] { x0, y1, x1, y1, 1 });
                chemin.add(new float[] { x0, y0, x0, y1, 1 });
                chemin.add(new float[] { x1, y0, x1, y1, 1 });
            }
        }

        @Override
        public void moveTo(float x, float y) {
            courant = new Point2D.Float(x, y);
        }

        @Override
        public void lineTo(float x, float y) {
            Point2D p = new Point2D.Float(x, y);
            if (courant != null) {
                segment(courant, p);
            }
            courant = p;
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            courant = new Point2D.Float(x3, y3);
        }

        @Override
        public Point2D getCurrentPoint() {
            return courant;
        }

        @Override
        public void closePath() {
        }

        @Override
        public void endPath() {
            chemin.clear();
        }

        @Override
        public void strokePath() {
            vider(true);
        }

        @Override
        public void fillPath(int windingRule) {
            vider(false);
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
            vider(true);
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void drawImage(PDImage pdImage) {
        }

        @Override
        public void shadingFill(COSName shadingName) {
        }

        private void vider(boolean trace) {
            for (float[] s : chemin) {
                if (!trace && s.length > 4) {
                    continue;   // un fond rempli (ombrage d'en-tête) n'est pas un filet
                }
                if (Math.abs(s[1] - s[3]) < 1.5f && Math.abs(s[0] - s[2]) > 3f) {
                    h.add(new Filet(r1((s[1] + s[3]) / 2), r1(Math.min(s[0], s[2])), r1(Math.max(s[0], s[2]))));
                } else if (Math.abs(s[0] - s[2]) < 1.5f && Math.abs(s[1] - s[3]) > 3f) {
                    v.add(new Filet(r1((s[0] + s[2]) / 2), r1(Math.min(s[1], s[3])), r1(Math.max(s[1], s[3]))));
                }
            }
            chemin.clear();
        }
    }

    // ------------------------------------------------------------------ PDF : la grille (règles 1-2)

    /** Valeurs proches regroupées (tolérance de 2 points). */
    private static double[] grappes(List<Double> vals) {
        List<Double> s = new ArrayList<>(vals);
        s.sort(Comparator.naturalOrder());
        List<double[]> out = new ArrayList<>();
        for (double v : s) {
            if (!out.isEmpty() && v - out.get(out.size() - 1)[1] <= 2) {
                out.get(out.size() - 1)[1] = v;
            } else {
                out.add(new double[] { v, v });
            }
        }
        return out.stream().mapToDouble(g -> (g[0] + g[1]) / 2).toArray();
    }

    private static final class V {
        final double x;
        double y0;
        double y1;

        V(double x, double y0, double y1) {
            this.x = x;
            this.y0 = y0;
            this.y1 = y1;
        }
    }

    private static final class Bloc {
        double y0;
        double y1;
        final List<V> v = new ArrayList<>();
    }

    /**
     * Les tableaux d'une page : un bloc de filets verticaux qui se recouvrent en hauteur (recollés quand Word les trace case par
     * case), puis la grille lignes × colonnes ; une ligne n'est coupée que là où un filet vertical la traverse.
     */
    static List<Tableau> tableaux(int page, List<Mot> mots, List<Filet> h, List<Filet> v) {
        if (v.size() < 3 || h.size() < 2) {
            return List.of();
        }
        List<Filet> tries = new ArrayList<>(v);
        tries.sort(Comparator.comparingDouble(Filet::a).thenComparingDouble(Filet::b0));
        List<V> vs = new ArrayList<>();
        for (Filet f : tries) {
            V d = vs.isEmpty() ? null : vs.get(vs.size() - 1);
            if (d != null && Math.abs(d.x - f.a()) < 1.5 && f.b0() <= d.y1 + 2) {
                d.y1 = Math.max(d.y1, f.b1());
            } else {
                vs.add(new V(f.a(), f.b0(), f.b1()));
            }
        }
        vs.sort(Comparator.comparingDouble(x -> x.y0));
        List<Bloc> blocs = new ArrayList<>();
        for (V x : vs) {
            Bloc b = blocs.stream().filter(k -> x.y0 <= k.y1 + 2 && x.y1 >= k.y0 - 2).findFirst().orElse(null);
            if (b != null) {
                b.y0 = Math.min(b.y0, x.y0);
                b.y1 = Math.max(b.y1, x.y1);
                b.v.add(x);
            } else {
                Bloc n = new Bloc();
                n.y0 = x.y0;
                n.y1 = x.y1;
                n.v.add(x);
                blocs.add(n);
            }
        }
        List<Tableau> out = new ArrayList<>();
        for (Bloc b : blocs) {
            if (b.v.size() < 3) {
                continue;
            }
            double[] xs = grappes(b.v.stream().map(x -> x.x).toList());
            double premier = xs[0];
            double dernier = xs[xs.length - 1];
            double[] ys = grappes(h.stream().filter(f -> f.a() >= b.y0 - 2 && f.a() <= b.y1 + 2 && f.b1() > premier && f.b0() < dernier)
                    .map(Filet::a).toList());
            List<Ligne> lignes = new ArrayList<>();
            for (int i = 0; i + 1 < ys.length; i++) {
                double y0 = ys[i];
                double y1 = ys[i + 1];
                List<Integer> coupes = new ArrayList<>();
                for (int j = 0; j < xs.length; j++) {
                    double x = xs[j];
                    // ⚠️ 2026-10-16 (recette de la charte, R3) — une marge intérieure haute laisse, sur une cellule grisée, le filet vertical
                    // commencer sous le filet du haut : la tolérance suit la hauteur de la ligne (3 à 6 points ; 3 dans le prototype).
                    double tol = Math.max(3, Math.min(6, 0.2 * (y1 - y0)));
                    if (b.v.stream().anyMatch(k -> Math.abs(k.x - x) < 2 && k.y0 <= y0 + tol && k.y1 >= y1 - tol)) {
                        coupes.add(j);
                    }
                }
                List<Cellule> cellules = new ArrayList<>();
                for (int j = 0; j + 1 < coupes.size(); j++) {
                    double x0 = xs[coupes.get(j)];
                    double x1 = xs[coupes.get(j + 1)];
                    List<Mot> dedans = new ArrayList<>(mots.stream().filter(m -> {
                        double cx = (m.x0() + m.x1()) / 2;
                        double cy = m.y() - 2;
                        return cx > x0 && cx < x1 && cy > y0 && cy < y1;
                    }).toList());
                    dedans.sort(Comparator.comparingDouble(Mot::y).thenComparingDouble(Mot::x0));
                    LinkedHashMap<Double, List<Mot>> parY = new LinkedHashMap<>();
                    for (Mot m : dedans) {
                        Double k = parY.keySet().stream().filter(y -> Math.abs(y - m.y()) < 2.5).findFirst().orElse(m.y());
                        parY.computeIfAbsent(k, z -> new ArrayList<>()).add(m);
                    }
                    List<String> texte = new ArrayList<>();
                    List<Double> fins = new ArrayList<>();
                    for (List<Mot> ms : parY.values()) {
                        ms.sort(Comparator.comparingDouble(Mot::x0));
                        StringBuilder t = new StringBuilder();
                        for (int k = 0; k < ms.size(); k++) {
                            t.append(k > 0 && ms.get(k).x0() - ms.get(k - 1).x1() > 1.0 ? " " : "").append(ms.get(k).t());
                        }
                        texte.add(t.toString());
                        fins.add(ms.get(ms.size() - 1).x1());
                    }
                    int pleines = 0;
                    for (int k = 0; k < fins.size() - 1; k++) {
                        if (x1 - fins.get(k) < 22) {
                            pleines++;
                        }
                    }
                    boolean prose = texte.size() > 1 && pleines >= (texte.size() - 1) * 0.6;
                    cellules.add(new Cellule(coupes.get(j), coupes.get(j + 1), texte, prose, espaces(String.join(" ", texte))));
                }
                lignes.add(new Ligne(cellules));
            }
            String dessus = "";
            if (ys.length > 0) {
                double haut = ys[0];
                List<Mot> au = new ArrayList<>(mots.stream().filter(m -> m.y() < haut && m.y() > haut - 70).toList());
                au.sort(Comparator.comparingDouble(Mot::y).thenComparingDouble(Mot::x0));
                StringBuilder t = new StringBuilder();
                for (int k = 0; k < au.size(); k++) {
                    Mot m = au.get(k);
                    Mot p = k > 0 ? au.get(k - 1) : null;
                    t.append(p != null && (Math.abs(m.y() - p.y()) > 2 || m.x0() - p.x1() > 1.0) ? " " : "").append(m.t());
                }
                dessus = t.toString();
            }
            out.add(new Tableau(page, lignes, dessus, xs, xs.length - 1, true));
        }
        return out;
    }

    // ------------------------------------------------------------------ Word : les tableaux natifs (§B4)

    static List<Tableau> tableauxDocx(byte[] contenu) throws IOException {
        List<Tableau> out = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(contenu))) {
            List<String> avant = new ArrayList<>();
            int rang = 0;
            for (IBodyElement e : doc.getBodyElements()) {
                if (e instanceof XWPFParagraph p) {
                    String t = espaces(p.getText());
                    if (!t.isEmpty()) {
                        avant.add(t);
                    }
                } else if (e instanceof XWPFTable tab) {
                    rang += 2;   // jamais « la page suivante » : un tableau Word ne se coupe pas
                    out.add(tableauDocx(tab, rang, String.join(" ", avant.subList(Math.max(0, avant.size() - 3), avant.size()))));
                    avant.clear();
                }
            }
        }
        return out;
    }

    private static Tableau tableauDocx(XWPFTable tab, int rang, String dessus) {
        List<Ligne> lignes = new ArrayList<>();
        int largeur = 0;
        for (XWPFTableRow r : tab.getRows()) {
            List<Cellule> cellules = new ArrayList<>();
            int col = 0;
            for (XWPFTableCell c : r.getTableCells()) {
                int span = 1;
                if (c.getCTTc().getTcPr() != null && c.getCTTc().getTcPr().getGridSpan() != null) {
                    span = Math.max(1, c.getCTTc().getTcPr().getGridSpan().getVal().intValue());
                }
                boolean suiteFusion = c.getCTTc().getTcPr() != null && c.getCTTc().getTcPr().getVMerge() != null
                        && (c.getCTTc().getTcPr().getVMerge().getVal() == null
                                || "continue".equals(c.getCTTc().getTcPr().getVMerge().getVal().toString()));
                List<String> ls = new ArrayList<>();
                if (!suiteFusion) {
                    for (XWPFParagraph p : c.getParagraphs()) {
                        for (String l : p.getText().split("\\R")) {
                            String t = espaces(l);
                            if (!t.isEmpty()) {
                                ls.add(t);
                            }
                        }
                    }
                }
                String texte = espaces(String.join(" ", ls));
                // Un paragraphe unique et long se replierait sur plusieurs lignes pleines : « d'un seul tenant ».
                boolean prose = ls.size() == 1 && texte.length() > 80;
                cellules.add(new Cellule(col, col + span, ls, prose, texte));
                col += span;
            }
            largeur = Math.max(largeur, col);
            lignes.add(new Ligne(cellules));
        }
        return new Tableau(rang, lignes, dessus, null, largeur, false);
    }

    // ------------------------------------------------------------------ la reconnaissance (règles 3-9, lire-specs)

    private static final class Precedent {
        int page;
        Tableau tableau;
        Integer lot;
        int[] indices;
        Article dernier;
    }

    private static Cellule cellule(Ligne l, int j) {
        if (j < 0) {
            return null;
        }
        return l.cellules().stream().filter(c -> c.c0() <= j && c.c1() > j).findFirst().orElse(null);
    }

    private static String texte(Cellule c) {
        return c == null ? null : c.texte();
    }

    private static String premiere(Ligne l) {
        return l.cellules().isEmpty() ? "" : l.cellules().get(0).texte();
    }

    static Integer lotDe(String texte) {
        Matcher m = LOT.matcher(texte == null ? "" : texte);
        if (!m.find()) {
            return null;
        }
        int n = Integer.parseInt(m.group(1));
        return n == 0 ? null : n;
    }

    private static int col(String[] colonnes, Pattern re, Pattern sauf) {
        for (int j = 0; j < colonnes.length; j++) {
            if (re.matcher(colonnes[j]).find() && !(sauf != null && sauf.matcher(colonnes[j]).find())) {
                return j;
            }
        }
        return -1;
    }

    private static BigDecimal nombre(Cellule c) {
        String v = c == null || c.texte() == null ? null : c.texte().replaceAll(S, "");
        return v != null && !v.isEmpty() && NOMBRE.matcher(v).matches() ? new BigDecimal(v.replace(',', '.')) : null;
    }

    private static boolean memesColonnes(Tableau a, Tableau b) {
        if (!a.pdf() || !b.pdf() || a.colonnes().length != b.colonnes().length) {
            return false;
        }
        for (int j = 0; j < a.colonnes().length; j++) {
            if (Math.abs(a.colonnes()[j] - b.colonnes()[j]) >= 3) {
                return false;
            }
        }
        return true;
    }

    static List<Article> articles(List<Tableau> tableaux) {
        LinkedHashMap<String, Article> articles = new LinkedHashMap<>();
        Integer lotCourant = null;
        Precedent precedent = null;
        Article defCourant = null;
        String attente = null;
        String[] serieCourante = null;
        for (Tableau t : tableaux) {
            int n = t.page();
            Integer lotTitre = lotDe(t.dessus());
            if (lotTitre != null) {
                lotCourant = lotTitre;
            }
            if (t.lignes().stream().anyMatch(l -> PRIX_DEF.matcher(premiere(l)).matches())) {
                for (Ligne l : t.lignes()) {
                    String c0 = premiere(l);
                    Cellule c1 = l.cellules().isEmpty() ? null : l.cellules().get(l.cellules().size() - 1);
                    if (PRIX_SEUL.matcher(c0).matches()) {
                        attente = texte(c1);
                        continue;
                    }
                    if (attente != null && !attente.isEmpty() && NUMERO.matcher(c0).matches()) {
                        Article a = art(articles, lotCourant, Integer.parseInt(c0));
                        if (a.code == null) {
                            a.code = c0;
                        }
                        a.designationSpec = attente;
                        a.exigences = new ArrayList<>(c1 == null ? List.of() : c1.lignes());
                        a.definition = true;
                        a.pageSpec = n;
                        defCourant = a;
                        attente = null;
                        continue;
                    }
                    Matcher m = PRIX_DEF.matcher(c0);
                    if (m.matches()) {
                        Article a = art(articles, lotCourant, Integer.parseInt(m.group(1)));
                        if (a.code == null) {
                            a.code = m.group(1);
                        }
                        a.designationSpec = c1 == null || c1.lignes().isEmpty() ? null : c1.lignes().get(0);
                        a.exigences = new ArrayList<>(c1 == null || c1.lignes().isEmpty() ? List.of() : c1.lignes().subList(1, c1.lignes().size()));
                        a.definition = true;
                        a.pageSpec = n;
                        defCourant = a;
                    } else if (c0.isEmpty() && defCourant != null && c1 != null && !c1.texte().isEmpty()) {
                        defCourant.exigences.addAll(c1.lignes());
                    }
                }
                precedent = null;
                continue;
            }
            int iPremier = -1;
            for (int i = 0; i < t.lignes().size(); i++) {
                if (NUMERO.matcher(premiere(t.lignes().get(i))).matches()) {
                    iPremier = i;
                    break;
                }
            }
            final Precedent prec = precedent;
            boolean suite = (iPremier == 0 || iPremier == 1 && premiere(t.lignes().get(0)).isEmpty()) && prec != null && prec.page == n - 1
                    && memesColonnes(prec.tableau, t);
            int nEntete = suite ? 0 : iPremier > 0 ? iPremier : 1;
            String[] colonnes = new String[t.nColonnes()];
            for (int j = 0; j < colonnes.length; j++) {
                List<String> parts = new ArrayList<>();
                for (Ligne l : t.lignes().subList(0, Math.min(nEntete, t.lignes().size()))) {
                    String x = texte(cellule(l, j));
                    parts.add(x == null ? "" : x);
                }
                colonnes[j] = norm(String.join(" ", parts));
            }
            int[] ix = suite ? prec.indices : new int[] { col(colonnes, COL_DESIG, null), col(colonnes, COL_SPEC, SAUF_SPEC),
                    col(colonnes, COL_UNITE, SAUF_UNITE), col(colonnes, COL_QTE, null), col(colonnes, COL_MIN, SAUF_MONTANT),
                    col(colonnes, COL_MAX, SAUF_MONTANT) };
            int iDesig = ix[0];
            int iSpec = ix[1];
            int iUnite = ix[2];
            int iQte = ix[3];
            int iMin = ix[4];
            int iMax = ix[5];
            boolean spec = iDesig >= 0 && iSpec >= 0;
            boolean bord = !spec && iDesig >= 0 && iUnite >= 0 && (iQte >= 0 || iMin >= 0);
            if (!spec && !bord) {
                precedent = null;
                continue;
            }
            Integer lot = suite ? prec.lot : lotTitre != null ? lotTitre : lotCourant;
            Precedent p = new Precedent();
            p.page = n;
            p.tableau = t;
            p.lot = lot;
            p.indices = ix;
            p.dernier = suite ? prec.dernier : null;
            precedent = p;
            if (!suite) {
                serieCourante = null;
            }
            for (int il = Math.min(nEntete, t.lignes().size()); il < t.lignes().size(); il++) {
                Ligne l = t.lignes().get(il);
                String c0 = premiere(l);
                Integer num = NUMERO.matcher(c0).matches() ? Integer.valueOf(c0) : null;
                if (num == null && suite && il < iPremier && p.dernier != null) {
                    Article d = p.dernier;
                    if (spec) {
                        List<String> ex = new ArrayList<>(d.exigences == null ? List.of() : d.exigences);
                        Cellule c = cellule(l, iSpec);
                        ex.addAll(c == null ? List.of() : c.lignes());
                        d.exigences = ex;
                    } else {
                        String x = texte(cellule(l, iDesig));
                        d.designationBrute = (d.designationBrute == null ? "" : d.designationBrute) + " " + (x == null ? "" : x);
                        d.designation = nettoie(d.designationBrute);
                    }
                    continue;
                }
                String desig = texte(cellule(l, iDesig));
                if (num == null || desig == null || desig.isEmpty()) {
                    continue;
                }
                String unite = texte(cellule(l, iUnite));
                if (bord && (unite == null || unite.isEmpty())) {
                    serieCourante = new String[] { c0, desig };
                    continue;
                }
                Article a = art(articles, lot, num);
                if (a.code == null) {
                    a.code = c0;
                }
                p.dernier = a;
                if (spec) {
                    Cellule c = cellule(l, iSpec);
                    a.designationSpec = desig;
                    a.exigences = new ArrayList<>(c == null ? List.of() : c.lignes());
                    a.prose = c != null && c.prose();
                    a.pageSpec = n;
                } else {
                    a.bordereau = true;
                    a.designationBrute = desig;
                    a.designation = nettoie(desig);
                    if (serieCourante != null) {
                        a.serie = serieCourante[0];
                        a.serieLibelle = serieCourante[1];
                    }
                    a.unite = unite;
                    if (iMin >= 0) {
                        a.min = nombre(cellule(l, iMin));
                        a.max = nombre(cellule(l, iMax));
                    } else {
                        a.quantite = nombre(cellule(l, iQte));
                    }
                    a.pageBordereau = n;
                }
            }
        }
        List<Article> liste = new ArrayList<>(articles.values());
        liste.sort(Comparator.comparingInt((Article a) -> a.lot == null ? 0 : a.lot).thenComparingInt(a -> a.n));
        for (Article a : liste) {
            a.caracteristiques = a.definition ? puces(a.exigences) : caracteristiques(a.exigences == null ? List.of() : a.exigences, a.prose);
        }
        return liste;
    }

    private static Article art(Map<String, Article> articles, Integer lot, int n) {
        return articles.computeIfAbsent(lot + "#" + n, k -> {
            Article a = new Article();
            a.lot = lot;
            a.n = n;
            return a;
        });
    }

    // ------------------------------------------------------------------ les caractéristiques (règle 10)

    private static List<Caracteristique> puces(List<String> exigences) {
        List<Caracteristique> out = new ArrayList<>();
        for (String m : split(PUCES, String.join(" ", exigences == null ? List.of() : exigences))) {
            String t = m.trim();
            if (t.length() > 1) {
                out.add(new Caracteristique(t, ""));
            }
        }
        return out;
    }

    /**
     * Les caractéristiques d'une cellule : un texte d'un seul tenant se découpe sur « ; », « + » et les virgules ; sinon une
     * caractéristique par ligne, une ligne prolongeant la précédente (fin liante, mot-outil, mot à chiffre) ; « Libellé : valeur ».
     */
    static List<Caracteristique> caracteristiques(List<String> lignes, boolean prose) {
        List<String> morceaux = new ArrayList<>();
        if (prose) {
            morceaux.addAll(split(PROSE_COUPE, String.join(" ", lignes)));
        } else {
            List<String> acc = new ArrayList<>();
            for (String l : lignes) {
                boolean suite = !acc.isEmpty() && (MOT_A_CHIFFRE.matcher(trimJs(l)).find() || FIN_LIANTE.matcher(acc.get(acc.size() - 1)).find()
                        || MOT_OUTIL.matcher(acc.get(acc.size() - 1)).find());
                if (acc.isEmpty() || !suite && DEBUT_LIGNE.matcher(l).find()) {
                    acc.add(l);
                } else {
                    acc.set(acc.size() - 1, acc.get(acc.size() - 1) + " " + l);
                }
            }
            for (String m : acc) {
                morceaux.addAll(split(POINT_VIRGULE, m));
            }
        }
        List<Caracteristique> out = new ArrayList<>();
        for (String brut : morceaux) {
            String m = trimJs(ESPACES.matcher(brut).replaceAll(" "));
            if (m.length() <= 1) {
                continue;
            }
            int i = m.indexOf(':');
            out.add(i > 2 && i < 40 ? new Caracteristique(trimJs(m.substring(0, i)), trimJs(m.substring(i + 1))) : new Caracteristique(m, ""));
        }
        return out;
    }

    /** {@code String.split} de JavaScript : les morceaux vides sont gardés (puis filtrés par l'appelant). */
    private static List<String> split(Pattern p, String s) {
        return Arrays.asList(p.split(s, -1));
    }

    // ------------------------------------------------------------------ désignations (règles 7, 11)

    /** Désignation de bordereau sans son suffixe de prix : « , l'unité à… », « Le forfait à… », « (insérer le prix…) ». */
    static String nettoie(String x) {
        String t = INSERER.matcher(x).replaceFirst("");
        t = SUFFIXE_PRIX.matcher(t).replaceFirst("");
        return trimJs(t);
    }

    /**
     * Deux désignations divergent si leurs nombres diffèrent, ou si plus de la moitié des mots manquent dans les deux sens.
     * ⚠️ Écart au prototype (règle 11) : les nombres ne se comparent que si les <strong>deux</strong> désignations en portent — une
     * précision d'un seul côté (« Ordinateurs portables » / « Ordinateur portable I3 », DAO 297) n'est pas une contradiction ; sans
     * quoi la recette (« les trois écarts réels et aucun autre ») ne tient pas.
     */
    public static boolean ecart(String a, String b) {
        String na = nombresDe(a);
        String nb = nombresDe(b);
        return !na.isEmpty() && !nb.isEmpty() && !na.equals(nb) || manque(a, b) && manque(b, a);
    }

    private static String nombresDe(String x) {
        Matcher m = NOMBRES.matcher(norm(x));
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add(m.group());
        }
        return String.join("|", out);
    }

    private static List<String> motsDe(String x) {
        List<String> out = new ArrayList<>();
        for (String w : NON_MOT.matcher(norm(x)).replaceAll(" ").split(" ")) {
            if (w.length() > 2 && !MOT_VIDE.matcher(w).matches()) {
                out.add(w.endsWith("S") ? w.substring(0, w.length() - 1) : w);
            }
        }
        return out;
    }

    private static boolean manque(String p, String q) {
        Set<String> mq = new HashSet<>(motsDe(q));
        List<String> mp = motsDe(p);
        return !mp.isEmpty() && (double) mp.stream().filter(w -> !mq.contains(w)).count() / mp.size() > 0.5;
    }

    /** NFKD, sans diacritiques, en capitales, espaces réduites (le {@code norm} du prototype). */
    static String norm(String s) {
        String t = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKD).replaceAll("[\\u0300-\\u036f]", "").toUpperCase(Locale.ROOT);
        return trimJs(ESPACES.matcher(t).replaceAll(" "));
    }

    private static String espaces(String s) {
        return trimJs(ESPACES.matcher(s == null ? "" : s).replaceAll(" "));
    }

    /** {@code trim} de JavaScript (espaces Unicode compris). */
    private static String trimJs(String s) {
        return s.replaceAll("^" + S + "+|" + S + "+$", "");
    }
}
