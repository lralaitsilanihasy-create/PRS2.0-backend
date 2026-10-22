package cnm.prs.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTParaRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSimpleField;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalJc;

import tools.jackson.databind.JsonNode;

/**
 * ⚠️ 2026-10-22 (demande front « modeles-documents-marche », W1) — le <strong>Word</strong> d'une page, écrit depuis le document
 * résolu ({@link ModelePvPage#resoudre}) : le même calcul que le PDF, une autre écriture. Les documents que le candidat ou la PRMP
 * complètent (projet de marché, puis documents du DAO) se remettent en Word ; les PV et la lettre restent en PDF (option C').
 *
 * <p>Ce qui est écrit, d'après le XHTML du PDF : page (A4, orientation, marges), police et taille par défaut ; paragraphes (alignement,
 * retraits, première ligne, espacements, interligne ; 1,15 par défaut comme le PDF) ; titres 1 à 3 (gras, 1,5 / 1,25 / 1,1 fois la
 * taille) ; marques (gras, italique, souligné, police, taille, couleur) ; retours à la ligne ; listes à puces et numérotées (puce ou
 * numéro écrits en tête d'élément, retrait suspendu) ; tableaux (largeur et retrait, colonnes proportionnelles aux largeurs de
 * l'éditeur, fusions horizontales et verticales, fonds, bordures, alignement vertical, cellules d'en-tête en gras) ; images ; sauts de
 * page ; en-tête (courant ou première page seulement) et pied de page, numéro de page et nombre de pages par champs Word. Pur : aucune
 * base.</p>
 */
public final class ModelePvDocx {

    /** mm → vingtièmes de point. */
    private static final double TWIPS_MM = 1440 / 25.4;
    private static final int LARGEUR_A4 = 11906;
    private static final int HAUTEUR_A4 = 16838;

    private ModelePvDocx() {
    }

    /**
     * Le .docx d'un document résolu ; {@code mention} (« EXEMPLE — valeurs fictives ») s'ajoute en bas à droite de chaque page,
     * {@code null} : aucune.
     */
    public static byte[] docx(JsonNode doc, LongFunction<ModelePvPage.Image> images, String mention) {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            JsonNode page = doc.path("page");
            JsonNode marges = page.path("marges");
            boolean paysage = "paysage".equals(page.path("orientation").asString(""));
            String police = page.path("police").isString() ? page.path("police").asString() : "Times New Roman";
            double taille = page.path("taille").isNumber() ? page.path("taille").doubleValue() : 12;
            double gauche = mm(marges, "gauche", 25);
            double droite = mm(marges, "droite", 20);

            CTSectPr sp = d.getDocument().getBody().isSetSectPr() ? d.getDocument().getBody().getSectPr()
                    : d.getDocument().getBody().addNewSectPr();
            CTPageSz sz = sp.isSetPgSz() ? sp.getPgSz() : sp.addNewPgSz();
            sz.setW(BigInteger.valueOf(paysage ? HAUTEUR_A4 : LARGEUR_A4));
            sz.setH(BigInteger.valueOf(paysage ? LARGEUR_A4 : HAUTEUR_A4));
            if (paysage) {
                sz.setOrient(STPageOrientation.LANDSCAPE);
            }
            CTPageMar pm = sp.isSetPgMar() ? sp.getPgMar() : sp.addNewPgMar();
            pm.setTop(twips(mm(marges, "haut", 25)));
            pm.setBottom(twips(mm(marges, "bas", 20)));
            pm.setLeft(twips(gauche));
            pm.setRight(twips(droite));
            pm.setHeader(twips(Math.min(10, mm(marges, "haut", 25) / 2)));
            pm.setFooter(twips(Math.min(10, mm(marges, "bas", 20) / 2)));
            int utile = (paysage ? HAUTEUR_A4 : LARGEUR_A4) - (int) Math.round((gauche + droite) * TWIPS_MM);
            // Comme le PDF : la ligne d'un paragraphe justifié qui finit par un retour à la ligne n'est pas étirée.
            // (CTCompat n'est pas dans poi-ooxml-lite : l'élément s'écrit au curseur ; les réglages de POI sont vides.)
            try (org.apache.xmlbeans.XmlCursor cur = d.getSettings().getCTSettings().newCursor()) {
                String w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
                cur.toEndToken();
                cur.beginElement(new javax.xml.namespace.QName(w, "compat", "w"));
                cur.insertElement(new javax.xml.namespace.QName(w, "doNotExpandShiftReturn", "w"));
            }

            Ecriture e = new Ecriture(images, police, taille, utile);
            if (doc.path("entete").isObject()) {
                boolean premiere = doc.path("enteteSurPremiereSeulement").asBoolean(false);
                if (premiere) {
                    sp.addNewTitlePg();
                    d.createHeader(HeaderFooterType.DEFAULT);
                }
                XWPFHeaderFooter h = d.createHeader(premiere ? HeaderFooterType.FIRST : HeaderFooterType.DEFAULT);
                e.zone(new Cible.Zone(h), doc.path("entete"));
            }
            XWPFHeaderFooter pied = null;
            if (doc.path("piedDePage").isObject() || mention != null) {
                pied = d.createFooter(HeaderFooterType.DEFAULT);
                if (doc.path("piedDePage").isObject()) {
                    e.zone(new Cible.Zone(pied), doc.path("piedDePage"));
                }
                if (mention != null) {
                    XWPFParagraph p = pied.createParagraph();
                    p.setAlignment(ParagraphAlignment.RIGHT);
                    XWPFRun r = p.createRun();
                    r.setText(mention);
                    r.setFontFamily("Arial");
                    r.setFontSize(8);
                    r.setColor("666666");
                }
            }
            e.zone(new Cible.Corps(d), doc.path("corps"));
            d.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Où écrire : le corps, une zone d'en-tête ou de pied, une cellule. */
    private interface Cible {

        XWPFParagraph paragraphe();

        XWPFTable table();

        /** À la fin : une cellule (ou une zone) se termine par un paragraphe. */
        default void terminer() {
        }

        record Corps(XWPFDocument d) implements Cible {

            @Override
            public XWPFParagraph paragraphe() {
                return d.createParagraph();
            }

            @Override
            public XWPFTable table() {
                XWPFTable t = d.createTable();
                return t;
            }
        }

        record Zone(XWPFHeaderFooter z) implements Cible {

            @Override
            public XWPFParagraph paragraphe() {
                return z.createParagraph();
            }

            @Override
            public XWPFTable table() {
                return z.createTable(1, 1);
            }

            @Override
            public void terminer() {
                List<org.apache.poi.xwpf.usermodel.IBodyElement> el = z.getBodyElements();
                if (el.isEmpty() || !(el.get(el.size() - 1) instanceof XWPFParagraph)) {
                    z.createParagraph();
                }
            }
        }

        final class Cellule implements Cible {

            private final XWPFTableCell c;
            private boolean premier = true;

            Cellule(XWPFTableCell c) {
                this.c = c;
            }

            @Override
            public XWPFParagraph paragraphe() {
                if (premier && !c.getParagraphs().isEmpty()) {
                    premier = false;
                    return c.getParagraphs().get(0);
                }
                premier = false;
                return c.addParagraph();
            }

            @Override
            public XWPFTable table() {
                if (premier && c.getParagraphs().size() == 1 && c.getBodyElements().size() == 1) {
                    c.removeParagraph(0);
                }
                premier = false;
                CTTbl ct = c.getCTTc().addNewTbl();
                XWPFTable t = new XWPFTable(ct, c);
                c.insertTable(c.getTables().size(), t);
                if (t.getRows().isEmpty()) {
                    t.createRow();
                }
                return t;
            }

            @Override
            public void terminer() {
                List<org.apache.poi.xwpf.usermodel.IBodyElement> el = c.getBodyElements();
                if (el.isEmpty() || !(el.get(el.size() - 1) instanceof XWPFParagraph)) {
                    c.addParagraph();
                }
            }
        }
    }

    private static final class Ecriture {

        private final LongFunction<ModelePvPage.Image> images;
        private final String police;
        private final double taille;
        private final int utile;

        Ecriture(LongFunction<ModelePvPage.Image> images, String police, double taille, int utile) {
            this.images = images;
            this.police = police;
            this.taille = taille;
            this.utile = utile;
        }

        void zone(Cible cible, JsonNode zone) {
            blocs(cible, zone.path("content"), 0, utile, false);
            cible.terminer();
        }

        void blocs(Cible cible, JsonNode content, int profondeur, int largeur, boolean entete) {
            for (JsonNode n : content) {
                bloc(cible, n, profondeur, largeur, entete);
            }
        }

        void bloc(Cible cible, JsonNode n, int profondeur, int largeur, boolean entete) {
            JsonNode a = n.path("attrs");
            switch (type(n)) {
                case "paragraph" -> {
                    XWPFParagraph p = cible.paragraphe();
                    paragraphe(p, a, taille);
                    enLigne(p, n.path("content"), taille, entete);
                }
                case "heading" -> {
                    int niveau = Math.max(1, Math.min(3, a.path("level").asInt(1)));
                    double t = taille * (niveau == 1 ? 1.5 : niveau == 2 ? 1.25 : 1.1);
                    XWPFParagraph p = cible.paragraphe();
                    paragraphe(p, a, t);
                    enLigne(p, n.path("content"), t, true);
                }
                case "bulletList", "orderedList" -> {
                    boolean numerotee = "orderedList".equals(type(n));
                    int rang = a.path("start").asInt(1);
                    for (JsonNode li : n.path("content")) {
                        String tete = numerotee ? rang++ + ".\t" : "•\t";
                        boolean premier = true;
                        for (JsonNode c : li.path("content")) {
                            if (premier && ("paragraph".equals(type(c)) || "heading".equals(type(c)))) {
                                XWPFParagraph p = cible.paragraphe();
                                paragraphe(p, c.path("attrs"), taille);
                                retraitListe(p, profondeur + 1, true);
                                XWPFRun r = p.createRun();
                                style(r, null, taille, entete);
                                r.setText(tete);
                                enLigne(p, c.path("content"), taille, entete);
                            } else if ("paragraph".equals(type(c))) {
                                XWPFParagraph p = cible.paragraphe();
                                paragraphe(p, c.path("attrs"), taille);
                                retraitListe(p, profondeur + 1, false);
                                enLigne(p, c.path("content"), taille, entete);
                            } else {
                                bloc(cible, c, profondeur + 1, largeur, entete);
                            }
                            premier = false;
                        }
                    }
                }
                case "table" -> table(cible, n, largeur);
                case "image" -> {
                    XWPFParagraph p = cible.paragraphe();
                    paragraphe(p, null, taille);
                    String al = a.path("alignement").asString("center");
                    p.setAlignment("left".equals(al) ? ParagraphAlignment.LEFT : "right".equals(al) ? ParagraphAlignment.RIGHT
                            : ParagraphAlignment.CENTER);
                    image(p, a);
                }
                case "pageBreak" -> {
                    XWPFParagraph p = cible.paragraphe();
                    paragraphe(p, null, taille);
                    p.createRun().addBreak(BreakType.PAGE);
                }
                default -> {
                    // résolu : rien d'autre à cette place
                }
            }
        }

        void enLigne(XWPFParagraph p, JsonNode content, double t, boolean gras) {
            for (JsonNode n : content) {
                switch (type(n)) {
                    case "text" -> {
                        XWPFRun r = p.createRun();
                        style(r, n.path("marks"), t, gras);
                        String texte = n.path("text").asString("");
                        String[] parts = texte.split("\t", -1);
                        for (int i = 0; i < parts.length; i++) {
                            if (i > 0) {
                                r.addTab();
                            }
                            r.setText(parts[i], i);
                        }
                    }
                    case "hardBreak" -> {
                        XWPFRun r = p.createRun();
                        style(r, null, t, gras);
                        r.addBreak();
                    }
                    case "numeroPage", "nombrePages" -> champ(p, "numeroPage".equals(type(n)) ? "PAGE" : "NUMPAGES", t);
                    case "image" -> image(p, n.path("attrs"));
                    default -> {
                        // résolu
                    }
                }
            }
        }

        void champ(XWPFParagraph p, String instruction, double t) {
            CTSimpleField f = p.getCTP().addNewFldSimple();
            f.setInstr(instruction);
            CTR r = f.addNewR();
            CTRPr rpr = r.addNewRPr();
            CTFonts fonts = rpr.addNewRFonts();
            fonts.setAscii(police);
            fonts.setHAnsi(police);
            rpr.addNewSz().setVal(BigInteger.valueOf(Math.round(t * 2)));
            r.addNewT().setStringValue("1");
        }

        void image(XWPFParagraph p, JsonNode a) {
            ModelePvPage.Image img = images.apply(a.path("idImage").asLong(-1));
            if (img == null) {
                return;
            }
            double largeur = a.path("largeur").isNumber() ? a.path("largeur").doubleValue() : Math.min(160, img.largeur() * 25.4 / 96);
            double hauteur = largeur * img.hauteur() / Math.max(1, img.largeur());
            int type = "image/png".equals(img.type()) ? Document.PICTURE_TYPE_PNG : Document.PICTURE_TYPE_JPEG;
            try {
                p.createRun().addPicture(new ByteArrayInputStream(img.contenu()), type, "image", emu(largeur), emu(hauteur));
            } catch (IOException | org.apache.poi.openxml4j.exceptions.InvalidFormatException ex) {
                throw new IllegalStateException("Image illisible pour le Word : " + ex.getMessage(), ex);
            }
        }

        void style(XWPFRun r, JsonNode marks, double t, boolean grasParDefaut) {
            String famille = police;
            double sz = t;
            String couleur = null;
            boolean gras = grasParDefaut;
            boolean italique = false;
            boolean souligne = false;
            if (marks != null) {
                for (JsonNode m : marks) {
                    switch (type(m)) {
                        case "bold" -> gras = true;
                        case "italic" -> italique = true;
                        case "underline" -> souligne = true;
                        case "textStyle" -> {
                            JsonNode a = m.path("attrs");
                            if (a.path("fontFamily").isString()) {
                                famille = a.path("fontFamily").asString();
                            }
                            Double s = a.path("fontSize").isMissingNode() || a.path("fontSize").isNull() ? null : ModelePvPage.taille(a.path("fontSize"));
                            if (s != null) {
                                sz = s;
                            }
                            if (a.path("color").isString()) {
                                couleur = a.path("color").asString().replace("#", "");
                            }
                        }
                        default -> {
                            // validé
                        }
                    }
                }
            }
            r.setFontFamily(famille);
            r.setFontSize(sz);
            if (gras) {
                r.setBold(true);
            }
            if (italique) {
                r.setItalic(true);
            }
            if (souligne) {
                r.setUnderline(UnderlinePatterns.SINGLE);
            }
            if (couleur != null) {
                r.setColor(couleur);
            }
        }

        /** Les réglages de paragraphe ; la marque de paragraphe prend la police et la taille, pour la hauteur d'une ligne vide. */
        void paragraphe(XWPFParagraph p, JsonNode a, double t) {
            CTPPr ppr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
            CTSpacing sp = ppr.isSetSpacing() ? ppr.getSpacing() : ppr.addNewSpacing();
            // L'interligne « simple » de Word vaut déjà environ 1,15 fois la taille (Arial, Times) : 1,15 en CSS = simple en Word.
            double interligne = a != null && a.path("interligne").isNumber() ? a.path("interligne").doubleValue() : 1.15;
            sp.setLine(BigInteger.valueOf(Math.round(240 * interligne / 1.15)));
            sp.setLineRule(STLineSpacingRule.AUTO);
            sp.setBefore(BigInteger.valueOf(a != null && a.path("espaceAvant").isNumber() ? Math.round(a.path("espaceAvant").doubleValue() * 20) : 0));
            sp.setAfter(BigInteger.valueOf(a != null && a.path("espaceApres").isNumber() ? Math.round(a.path("espaceApres").doubleValue() * 20) : 0));
            CTParaRPr rpr = ppr.isSetRPr() ? ppr.getRPr() : ppr.addNewRPr();
            CTFonts f = rpr.addNewRFonts();
            f.setAscii(police);
            f.setHAnsi(police);
            rpr.addNewSz().setVal(BigInteger.valueOf(Math.round(t * 2)));
            if (a == null) {
                return;
            }
            String al = a.path("textAlign").asString("");
            switch (al) {
                case "center" -> p.setAlignment(ParagraphAlignment.CENTER);
                case "right" -> p.setAlignment(ParagraphAlignment.RIGHT);
                case "justify" -> p.setAlignment(ParagraphAlignment.BOTH);
                case "left" -> p.setAlignment(ParagraphAlignment.LEFT);
                default -> {
                    // gauche par défaut
                }
            }
            if (a.path("retrait").isNumber() || a.path("retraitDroite").isNumber() || a.path("retraitPremiereLigne").isNumber()) {
                CTInd ind = ppr.isSetInd() ? ppr.getInd() : ppr.addNewInd();
                if (a.path("retrait").isNumber()) {
                    ind.setLeft(twips(a.path("retrait").doubleValue()));
                }
                if (a.path("retraitDroite").isNumber()) {
                    ind.setRight(twips(a.path("retraitDroite").doubleValue()));
                }
                if (a.path("retraitPremiereLigne").isNumber()) {
                    double v = a.path("retraitPremiereLigne").doubleValue();
                    if (v >= 0) {
                        ind.setFirstLine(twips(v));
                    } else {
                        ind.setHanging(twips(-v));
                    }
                }
            }
        }

        /** Le retrait d'un élément de liste : 8 mm par niveau (comme le PDF), la puce ou le numéro en retrait suspendu. */
        void retraitListe(XWPFParagraph p, int niveau, boolean tete) {
            CTPPr ppr = p.getCTP().getPPr();
            CTInd ind = ppr.isSetInd() ? ppr.getInd() : ppr.addNewInd();
            ind.setLeft(twips(8.0 * niveau));
            if (tete) {
                ind.setHanging(twips(5));
            }
        }

        void table(Cible cible, JsonNode t, int largeurDisponible) {
            JsonNode a = t.path("attrs");
            boolean bordures = a.path("bordures").asBoolean(true);
            int largeur = (int) Math.round(largeurDisponible * a.path("largeur").asDouble(100) / 100);
            JsonNode lignes = t.path("content");
            int colonnes = 0;
            for (JsonNode c : lignes.path(0).path("content")) {
                colonnes += Math.max(1, c.path("attrs").path("colspan").asInt(1));
            }
            if (colonnes == 0) {
                return;
            }
            List<Integer> grille = grille(t, colonnes, largeur);

            XWPFTable tab = cible.table();
            CTTbl ct = tab.getCTTbl();
            CTTblPr tpr = ct.getTblPr() == null ? ct.addNewTblPr() : ct.getTblPr();
            CTTblWidth w = tpr.isSetTblW() ? tpr.getTblW() : tpr.addNewTblW();
            w.setType(STTblWidth.DXA);
            w.setW(BigInteger.valueOf(largeur));
            (tpr.isSetTblLayout() ? tpr.getTblLayout() : tpr.addNewTblLayout()).setType(STTblLayoutType.FIXED);
            if (a.path("retrait").isNumber()) {
                CTTblWidth ind = tpr.isSetTblInd() ? tpr.getTblInd() : tpr.addNewTblInd();
                ind.setType(STTblWidth.DXA);
                ind.setW(twips(a.path("retrait").doubleValue()));
            }
            CTTblBorders tb = tpr.isSetTblBorders() ? tpr.getTblBorders() : tpr.addNewTblBorders();
            for (CTBorder b : new CTBorder[] { tb.isSetTop() ? tb.getTop() : tb.addNewTop(), tb.isSetBottom() ? tb.getBottom() : tb.addNewBottom(),
                    tb.isSetLeft() ? tb.getLeft() : tb.addNewLeft(), tb.isSetRight() ? tb.getRight() : tb.addNewRight(),
                    tb.isSetInsideH() ? tb.getInsideH() : tb.addNewInsideH(), tb.isSetInsideV() ? tb.getInsideV() : tb.addNewInsideV() }) {
                b.setVal(STBorder.NONE);
            }
            var mar = tpr.isSetTblCellMar() ? tpr.getTblCellMar() : tpr.addNewTblCellMar();
            marge(mar.isSetLeft() ? mar.getLeft() : mar.addNewLeft(), 1.5);
            marge(mar.isSetRight() ? mar.getRight() : mar.addNewRight(), 1.5);
            marge(mar.isSetTop() ? mar.getTop() : mar.addNewTop(), 0.5);
            marge(mar.isSetBottom() ? mar.getBottom() : mar.addNewBottom(), 0.5);
            CTTblGrid g = ct.getTblGrid() == null ? ct.addNewTblGrid() : ct.getTblGrid();
            while (g.sizeOfGridColArray() > 0) {
                g.removeGridCol(0);
            }
            for (int gc : grille) {
                g.addNewGridCol().setW(BigInteger.valueOf(gc));
            }

            int[] restant = new int[colonnes];
            int[] envergure = new int[colonnes];
            boolean[] bordureRestante = new boolean[colonnes];
            for (int i = 0; i < lignes.size(); i++) {
                XWPFTableRow row = i == 0 ? tab.getRow(0) : tab.createRow();
                while (!row.getTableCells().isEmpty()) {
                    row.removeCell(0);
                }
                int col = 0;
                java.util.Iterator<JsonNode> cellules = lignes.get(i).path("content").iterator();
                while (col < colonnes) {
                    if (restant[col] > 0) {
                        XWPFTableCell c = row.addNewTableCell();
                        int span = envergure[col];
                        proprietes(c, span, somme(grille, col, span), STMerge.CONTINUE, bordureRestante[col], null, null);
                        restant[col]--;
                        col += span;
                        continue;
                    }
                    if (!cellules.hasNext()) {
                        break;
                    }
                    JsonNode cj = cellules.next();
                    JsonNode ca = cj.path("attrs");
                    int span = Math.min(colonnes - col, Math.max(1, ca.path("colspan").asInt(1)));
                    int rowspan = Math.max(1, ca.path("rowspan").asInt(1));
                    boolean b = ca.path("bordures").asBoolean(bordures);
                    XWPFTableCell c = row.addNewTableCell();
                    proprietes(c, span, somme(grille, col, span), rowspan > 1 ? STMerge.RESTART : null, b,
                            ca.path("fond").isString() ? ca.path("fond").asString().replace("#", "") : null,
                            ca.path("alignementVertical").asString("haut"));
                    if (rowspan > 1) {
                        restant[col] = rowspan - 1;
                        envergure[col] = span;
                        bordureRestante[col] = b;
                    }
                    Cible.Cellule cible2 = new Cible.Cellule(c);
                    blocs(cible2, cj.path("content"), 0, somme(grille, col, span), "tableHeader".equals(type(cj)));
                    cible2.terminer();
                    col += span;
                }
            }
            cible.terminer();
        }

        void proprietes(XWPFTableCell c, int span, int largeur, STMerge.Enum fusion, boolean bordure, String fond, String vertical) {
            CTTcPr pr = c.getCTTc().isSetTcPr() ? c.getCTTc().getTcPr() : c.getCTTc().addNewTcPr();
            CTTblWidth w = pr.isSetTcW() ? pr.getTcW() : pr.addNewTcW();
            w.setType(STTblWidth.DXA);
            w.setW(BigInteger.valueOf(largeur));
            if (span > 1) {
                pr.addNewGridSpan().setVal(BigInteger.valueOf(span));
            }
            if (fusion != null) {
                pr.addNewVMerge().setVal(fusion);
            }
            CTTcBorders tb = pr.addNewTcBorders();
            for (CTBorder b : new CTBorder[] { tb.addNewTop(), tb.addNewBottom(), tb.addNewLeft(), tb.addNewRight() }) {
                if (bordure) {
                    b.setVal(STBorder.SINGLE);
                    b.setSz(BigInteger.valueOf(4));
                    b.setColor("000000");
                } else {
                    b.setVal(STBorder.NIL);
                }
            }
            if (fond != null) {
                CTShd shd = pr.addNewShd();
                shd.setVal(STShd.CLEAR);
                shd.setColor("auto");
                shd.setFill(fond);
            }
            if (vertical != null) {
                pr.addNewVAlign().setVal("milieu".equals(vertical) ? STVerticalJc.CENTER : "bas".equals(vertical) ? STVerticalJc.BOTTOM
                        : STVerticalJc.TOP);
            }
            if (c.getParagraphs().isEmpty()) {
                c.addParagraph();
            }
        }
    }

    /** Les largeurs des colonnes (vingtièmes de point), proportionnelles à celles de l'éditeur, à défaut égales. */
    private static List<Integer> grille(JsonNode t, int colonnes, int largeur) {
        List<Double> px = new ArrayList<>();
        for (JsonNode c : t.path("content").path(0).path("content")) {
            int colspan = Math.max(1, c.path("attrs").path("colspan").asInt(1));
            JsonNode cw = c.path("attrs").path("colwidth");
            for (int i = 0; i < colspan; i++) {
                px.add(cw.isArray() && cw.path(i).isNumber() ? cw.path(i).doubleValue() : null);
            }
        }
        boolean toutes = px.size() == colonnes && px.stream().allMatch(v -> v != null && v > 0);
        double total = toutes ? px.stream().mapToDouble(Double::doubleValue).sum() : colonnes;
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < colonnes; i++) {
            out.add((int) Math.round(largeur * (toutes ? px.get(i) : 1) / total));
        }
        return out;
    }

    private static int somme(List<Integer> grille, int debut, int span) {
        int s = 0;
        for (int i = debut; i < Math.min(grille.size(), debut + span); i++) {
            s += grille.get(i);
        }
        return s;
    }

    private static void marge(CTTblWidth w, double mm) {
        w.setType(STTblWidth.DXA);
        w.setW(twips(mm));
    }

    private static String type(JsonNode n) {
        return n != null && n.path("type").isString() ? n.path("type").asString() : "";
    }

    private static double mm(JsonNode marges, String cle, double defaut) {
        return marges.path(cle).isNumber() ? marges.path(cle).doubleValue() : defaut;
    }

    private static BigInteger twips(double mm) {
        return BigInteger.valueOf(Math.round(mm * TWIPS_MM));
    }

    private static int emu(double mm) {
        return (int) Math.round(mm * 36000);
    }
}
