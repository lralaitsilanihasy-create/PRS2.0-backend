package cnm.prs.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPBdr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;

/**
 * ⚠️ 2026-10-16 (demande front « charte-dao », §B1, §B4) — la <strong>charte</strong> des DAO générés, appliquée à chaque partie
 * avant l'assemblage par Word. Le <strong>texte</strong> ne change pas, seulement sa présentation :
 *
 * <ul>
 *   <li>Arial partout, noir seul ;</li>
 *   <li>une partie produite par l'application ({@link DocumentLibre}, mise en forme directe) : titre du document 13 pt gras ;
 *       article (« ARTICLE n… ») 11 pt gras sur filet bas de 0,5 pt ; sous-article 10,5 pt gras souligné ; corps 10,5 pt, interligne
 *       1,3, 1,8 mm après ; tableaux 9,5 pt ;</li>
 *   <li>un texte fixe de l'ARMP (IC, CCAG : ses styles) : Arial dans ses styles et ses runs, corps ramené à 10,5 pt ;</li>
 *   <li>la relecture (§B4) : aucun paragraphe coupé entre deux pages ni césure ; un trait d'union entre deux lettres devient
 *       insécable (U+2011, que la lecture ramène à « - ») ; une ligne de tableau ne se coupe pas (l'en-tête répété et grisé est
 *       posé par {@link TableauxBesoin}, seul à savoir que sa première ligne est un en-tête).</li>
 * </ul>
 * Le Word de spécifications joint par la PRMP n'y passe pas : il garde son contenu (seuls ses styles homonymes sont ramenés à la
 * charte, à l'insertion).
 */
public final class CharteDao {

    private CharteDao() {
    }

    public static final String POLICE = "Arial";
    /** 1,8 mm après un paragraphe, en vingtièmes de point. */
    static final int APRES = 102;
    /** Interligne 1,3 (240 = simple). */
    static final int INTERLIGNE = 312;
    private static final Pattern TRAIT_UNION = Pattern.compile("(?<=\\p{L})-(?=\\p{L})");
    private static final Pattern ARTICLE = Pattern.compile("^(ARTICLE|Article)\\b.*");
    private static final Pattern TITRE_STYLE = Pattern.compile("(?i).*(heading|titre|title|toc|tm\\s?\\d).*");

    /** Une partie produite par l'application. */
    public static byte[] partie(byte[] docx) {
        return appliquer(docx, false);
    }

    /** Un texte fixe de l'ARMP. */
    public static byte[] fixe(byte[] docx) {
        return appliquer(docx, true);
    }

    static byte[] appliquer(byte[] docx, boolean fixe) {
        if (docx == null) {
            return null;
        }
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (fixe) {
                styles(doc);
            }
            normalJustifie(doc);
            numerotation(doc);
            corps(doc.getBodyElements(), fixe, false);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            // Une partie que POI ne lit pas passe telle quelle : la charte ne bloque jamais l'assemblage.
            return docx;
        }
    }

    /**
     * ⚠️ 2026-10-16 (recette de la charte, R3) — le Normal de la partie est déclaré justifié, comme celui du DAO. À l'insertion, Word
     * écarte une mise en forme directe égale au style de la source : un alignement « à gauche » posé sur un Normal à gauche était
     * perdu, et le paragraphe prenait le Normal justifié du DAO (en-têtes de tableau étirés, sous-titres justifiés).
     */
    public static void normalJustifie(XWPFDocument doc) {
        org.apache.poi.xwpf.usermodel.XWPFStyles styles = doc.getStyles() != null ? doc.getStyles() : doc.createStyles();
        org.apache.poi.xwpf.usermodel.XWPFStyle normal = styles.getStyle("Normal");
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle ct;
        if (normal == null) {
            // ⚠️ R5 (contre-recette du 17/10) — addStyle recopie le style : l'alignement se pose AVANT, sinon il est perdu.
            ct = org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle.Factory.newInstance();
            ct.setStyleId("Normal");
            ct.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType.PARAGRAPH);
            ct.setDefault(true);
            ct.addNewName().setVal("Normal");
            ct.addNewPPr().addNewJc().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc.BOTH);
            styles.addStyle(new org.apache.poi.xwpf.usermodel.XWPFStyle(ct, styles));
            return;
        }
        ct = normal.getCTStyle();
        var ppr = ct.isSetPPr() ? ct.getPPr() : ct.addNewPPr();
        (ppr.isSetJc() ? ppr.getJc() : ppr.addNewJc()).setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc.BOTH);
    }

    // ------------------------------------------------------------------ styles (texte fixe)

    private static void styles(XWPFDocument doc) {
        if (doc.getStyles() == null) {
            return;
        }
        try {
            var ct = doc.getStyle();
            if (ct.isSetDocDefaults() && ct.getDocDefaults().isSetRPrDefault() && ct.getDocDefaults().getRPrDefault().isSetRPr()) {
                polices(ct.getDocDefaults().getRPrDefault().getRPr());
            }
            for (var s : ct.getStyleArray()) {
                if (s.isSetRPr()) {
                    polices(s.getRPr());
                }
                if ("Normal".equalsIgnoreCase(s.getStyleId())) {
                    CTRPr r = s.isSetRPr() ? s.getRPr() : s.addNewRPr();
                    polices(r);
                    var sz = r.sizeOfSzArray() > 0 ? r.getSzArray(0) : r.addNewSz();
                    sz.setVal(BigInteger.valueOf(21));
                }
            }
        } catch (IOException | org.apache.xmlbeans.XmlException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Les polices de symboles (cases à cocher, puces) gardent la leur : en Arial, leurs caractères deviendraient des carrés. */
    private static final Pattern SYMBOLE = Pattern.compile("(?i)symbol|wingdings.*|webdings|courier new|ms gothic|segoe ui symbol|marlett");

    private static void polices(CTRPr r) {
        CTFonts f = r.sizeOfRFontsArray() > 0 ? r.getRFontsArray(0) : r.addNewRFonts();
        if (f.getAscii() != null && SYMBOLE.matcher(f.getAscii()).matches() || f.getHAnsi() != null && SYMBOLE.matcher(f.getHAnsi()).matches()) {
            return;
        }
        f.setAscii(POLICE);
        f.setHAnsi(POLICE);
        f.setCs(POLICE);
        f.setEastAsia(POLICE);
        if (f.isSetAsciiTheme()) {
            f.unsetAsciiTheme();
        }
        if (f.isSetHAnsiTheme()) {
            f.unsetHAnsiTheme();
        }
        if (f.isSetCstheme()) {
            f.unsetCstheme();
        }
        if (f.isSetEastAsiaTheme()) {
            f.unsetEastAsiaTheme();
        }
    }

    /** Les puces et numéros des listes (numbering.xml) : Arial, sauf les puces en police de symboles. */
    private static void numerotation(XWPFDocument doc) {
        if (doc.getNumbering() == null) {
            return;
        }
        for (var a : doc.getNumbering().getAbstractNums()) {
            for (var lvl : a.getCTAbstractNum().getLvlArray()) {
                if (lvl.isSetRPr()) {
                    polices(lvl.getRPr());
                }
            }
        }
    }

    // ------------------------------------------------------------------ le corps

    private static void corps(List<IBodyElement> elements, boolean fixe, boolean tableau) {
        for (IBodyElement e : elements) {
            if (e instanceof XWPFParagraph p) {
                paragraphe(p, fixe, tableau);
            } else if (e instanceof XWPFTable t) {
                table(t, fixe);
            }
        }
    }

    private static void table(XWPFTable t, boolean fixe) {
        List<XWPFTableRow> lignes = t.getRows();
        if (!fixe) {
            // Filets noirs de 0,5 pt ; l'en-tête grisé est réservé aux tableaux du besoin (TableauxBesoin), dont la première ligne
            // est un en-tête : un tableau de modèle peut commencer par une ligne « clé / valeur ».
            filets(t);
            // ⚠️ R5 (contre-recette du 17/10) — les cellules des modèles (formulaires A1…) : marges intérieures de la charte et
            // alignement à gauche explicite (sans lui, elles prennent le Normal justifié du DAO) ; un centrage du modèle est gardé.
            t.setCellMargins(57, 85, 57, 85);
            for (XWPFTableRow l : lignes) {
                for (XWPFTableCell c : l.getTableCells()) {
                    for (XWPFParagraph p : c.getParagraphs()) {
                        if (p.getAlignment() == ParagraphAlignment.LEFT || p.getAlignment() == ParagraphAlignment.BOTH
                                || !p.getCTP().isSetPPr() || !p.getCTP().getPPr().isSetJc()) {
                            p.setAlignment(ParagraphAlignment.LEFT);
                        }
                    }
                }
            }
        }
        for (int i = 0; i < lignes.size(); i++) {
            CTTrPr pr = lignes.get(i).getCtRow().isSetTrPr() ? lignes.get(i).getCtRow().getTrPr() : lignes.get(i).getCtRow().addNewTrPr();
            if (pr.sizeOfCantSplitArray() == 0) {
                pr.addNewCantSplit();
            }
            for (XWPFTableCell c : lignes.get(i).getTableCells()) {
                corps(c.getBodyElements(), fixe, true);
            }
        }
    }

    private static void paragraphe(XWPFParagraph p, boolean fixe, boolean tableau) {
        CTPPr pr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        if (!pr.isSetKeepLines()) {
            pr.addNewKeepLines();
        }
        if (!pr.isSetSuppressAutoHyphens()) {
            pr.addNewSuppressAutoHyphens();
        }
        List<XWPFRun> runs = p.getRuns();
        for (XWPFRun r : runs) {
            CTRPr rp = r.getCTR().isSetRPr() ? r.getCTR().getRPr() : r.getCTR().addNewRPr();
            polices(rp);
            if (r.getColor() != null && !"000000".equalsIgnoreCase(r.getColor()) && !"auto".equalsIgnoreCase(r.getColor())) {
                r.setColor("000000");
            }
            for (CTText t : r.getCTR().getTArray()) {
                String v = t.getStringValue();
                if (v != null && v.indexOf('-') >= 0) {
                    t.setStringValue(TRAIT_UNION.matcher(v).replaceAll("‑"));
                }
            }
        }
        if (fixe) {
            String style = p.getStyle();
            boolean titre = style != null && TITRE_STYLE.matcher(style).matches();
            if (!titre && !tableau) {
                for (XWPFRun r : runs) {
                    Double sz = r.getFontSizeAsDouble();
                    if (sz != null && sz >= 9 && sz <= 12) {
                        r.setFontSize(10.5);
                    }
                }
            }
            return;
        }
        if (tableau) {
            for (XWPFRun r : runs) {
                Double sz = r.getFontSizeAsDouble();
                if (sz == null || sz <= 10) {
                    r.setFontSize(9.5);
                }
            }
            return;
        }
        String texte = p.getText() == null ? "" : p.getText().trim();
        XWPFRun premier = runs.isEmpty() ? null : runs.get(0);
        Double taille = premier == null ? null : premier.getFontSizeAsDouble();
        boolean gras = premier != null && premier.isBold();
        if (gras && taille != null && taille >= 12.5) {
            return;   // le titre du document : 13 pt gras, inchangé
        }
        if (gras && taille != null && taille >= 10.5 && p.getAlignment() != ParagraphAlignment.CENTER) {
            if (ARTICLE.matcher(texte).matches()) {
                runs.forEach(r -> r.setFontSize(11));
                CTPBdr bdr = pr.isSetPBdr() ? pr.getPBdr() : pr.addNewPBdr();
                CTBorder bas = bdr.isSetBottom() ? bdr.getBottom() : bdr.addNewBottom();
                bas.setVal(STBorder.SINGLE);
                bas.setSz(BigInteger.valueOf(4));
                bas.setSpace(BigInteger.ONE);
                bas.setColor("000000");
                espacement(pr, 100, 50);
            } else {
                runs.forEach(r -> {
                    r.setFontSize(10.5);
                    r.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
                });
                espacement(pr, 60, 30);
            }
            if (!pr.isSetKeepNext()) {
                pr.addNewKeepNext();
            }
            return;
        }
        for (XWPFRun r : runs) {
            Double sz = r.getFontSizeAsDouble();
            if (sz == null || sz <= 10.5) {
                r.setFontSize(10.5);
            }
        }
        CTSpacing sp = pr.isSetSpacing() ? pr.getSpacing() : pr.addNewSpacing();
        sp.setAfter(BigInteger.valueOf(APRES));
        sp.setLine(BigInteger.valueOf(INTERLIGNE));
        sp.setLineRule(STLineSpacingRule.AUTO);
    }

    /** Le fond de l'en-tête d'un tableau. */
    public static final String GRIS = "E3E3E3";

    /** Filets noirs de 0,5 pt sur tout le tableau. */
    public static void filets(XWPFTable t) {
        t.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        t.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        t.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        t.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        t.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
        t.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
    }

    private static void espacement(CTPPr pr, int avant, int apres) {
        CTSpacing sp = pr.isSetSpacing() ? pr.getSpacing() : pr.addNewSpacing();
        sp.setBefore(BigInteger.valueOf(avant));
        sp.setAfter(BigInteger.valueOf(apres));
    }

}
