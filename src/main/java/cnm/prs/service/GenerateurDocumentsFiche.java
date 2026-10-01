package cnm.prs.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.HeaderFooter;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfWriter;

/**
 * ⚠️ Fiche marché, lot 2a (demande front du 2026-09-23, §B5) — la <strong>mise en page provisoire</strong> des
 * documents générés : un titre par bloc, un sous-titre par rubrique, une ligne « libellé : valeur » par information,
 * le pied de page de la version. Ce composant ne choisit rien — {@link SelectionDocumentsFiche} a déjà décidé du
 * contenu — : c'est lui, et lui seul, que remplaceront les modèles Word officiels au lot 2b.
 *
 * <p>{@code docx} par Apache POI (document construit, pas un modèle à remplir : aucun marqueur à retrouver dans des
 * {@code <w:r>} découpés) ; {@code pdf} par OpenPDF, <strong>sans Word</strong> — la génération tourne dans la
 * transaction de la validation, y compris sur les serveurs et la CI qui n'ont pas MS Word.</p>
 */
@Component
public class GenerateurDocumentsFiche {

    /** Un fichier produit : extension ({@code docx}, {@code pdf}) et contenu. */
    public record Fichier(String extension, byte[] contenu) {
    }

    /** Les deux fichiers d'un document, docx puis pdf. Une erreur de production fait échouer la validation. */
    public List<Fichier> generer(DocumentFicheModele modele) {
        return List.of(new Fichier("docx", docx(modele)), new Fichier("pdf", pdf(modele)));
    }

    /**
     * ⚠️ V47 (formulaires du candidat, R12 (c)) — les deux fichiers d'un {@link DocumentLibre} : paragraphes alignés
     * (titre centré gras, sous-titre gras, justifié, centré, à droite, vide) et tableaux à cellules multi-paragraphes,
     * dans l'ordre des éléments ; pied de page de la version. Ni filigrane ni numérotation : ce sont les modèles
     * officiels, leur texte est décalqué au caractère près.
     */
    public List<Fichier> generer(DocumentLibre modele) {
        return List.of(new Fichier("docx", docxLibre(modele)), new Fichier("pdf", pdfLibre(modele)));
    }

    // ------------------------------------------------------------------ document libre : docx

    private static byte[] docxLibre(DocumentLibre m) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (DocumentLibre.Element e : m.elements()) {
                if (e instanceof DocumentLibre.Paragraphe p) {
                    paragrapheLibre(doc.createParagraph(), p);
                } else if (e instanceof DocumentLibre.Image im) {
                    // ⚠️ 2026-10-01 (avis spécifique, §B7.8) — une image centrée, à sa largeur, hauteur proportionnelle.
                    XWPFParagraph p = doc.createParagraph();
                    p.setAlignment(ParagraphAlignment.CENTER);
                    double[] taille = taillePoints(im);
                    try {
                        p.createRun().addPicture(new java.io.ByteArrayInputStream(im.contenu()), XWPFDocument.PICTURE_TYPE_PNG,
                                im.nom() + ".png", org.apache.poi.util.Units.toEMU(taille[0]), org.apache.poi.util.Units.toEMU(taille[1]));
                    } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException ex) {
                        throw new IllegalStateException("Image " + im.nom() + " illisible : " + ex.getMessage(), ex);
                    }
                } else if (e instanceof DocumentLibre.Tableau t) {
                    org.apache.poi.xwpf.usermodel.XWPFTable table = doc.createTable(Math.max(1, t.lignes().size()), t.colonnes());
                    table.setWidth("100%");
                    for (int l = 0; l < t.lignes().size(); l++) {
                        List<List<String>> ligne = t.lignes().get(l);
                        for (int c = 0; c < t.colonnes(); c++) {
                            org.apache.poi.xwpf.usermodel.XWPFTableCell cellule = table.getRow(l).getCell(c);
                            List<String> paragraphes = c < ligne.size() ? ligne.get(c) : List.of("");
                            for (int i = 0; i < paragraphes.size(); i++) {
                                XWPFParagraph p = i == 0 ? cellule.getParagraphs().get(0) : cellule.addParagraph();
                                XWPFRun r = p.createRun();
                                r.setFontSize(9);
                                r.setText(paragraphes.get(i));
                            }
                        }
                    }
                }
            }
            XWPFFooter pied = doc.createFooter(HeaderFooterType.DEFAULT);
            XWPFParagraph pp = pied.createParagraph();
            pp.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun r = pp.createRun();
            r.setFontSize(8);
            r.setText(m.piedDePage());
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Génération du " + m.type() + " (docx) impossible : " + e.getMessage(), e);
        }
    }

    /** ⚠️ 2026-10-01 — largeur et hauteur d'une image en points : sa largeur en mm, la hauteur à ses proportions. */
    static double[] taillePoints(DocumentLibre.Image im) {
        double largeur = im.largeurMm() * 72.0 / 25.4;
        try {
            java.awt.image.BufferedImage b = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(im.contenu()));
            double hauteur = b == null || b.getWidth() == 0 ? largeur : largeur * b.getHeight() / b.getWidth();
            return new double[] { largeur, hauteur };
        } catch (IOException ex) {
            throw new IllegalStateException("Image " + im.nom() + " illisible : " + ex.getMessage(), ex);
        }
    }

    private static void paragrapheLibre(XWPFParagraph p, DocumentLibre.Paragraphe modele) {
        p.setSpacingAfter(60);
        XWPFRun r = p.createRun();
        switch (modele.style()) {
            case TITRE -> {
                p.setAlignment(ParagraphAlignment.CENTER);
                r.setBold(true);
                r.setFontSize(13);
            }
            case SOUS_TITRE -> {
                p.setAlignment(ParagraphAlignment.LEFT);
                r.setBold(true);
                r.setFontSize(11);
            }
            case PARA -> {
                p.setAlignment(ParagraphAlignment.BOTH);
                r.setFontSize(10);
            }
            case CENTRE -> {
                p.setAlignment(ParagraphAlignment.CENTER);
                r.setFontSize(10);
            }
            case DROITE -> {
                p.setAlignment(ParagraphAlignment.RIGHT);
                r.setFontSize(10);
            }
            case VIDE -> r.setFontSize(10);
        }
        r.setText(modele.texte());
        if (modele.solidaireDuSuivant()) {   // ⚠️ 2026-10-01 — « paragraphe solidaire du suivant », lignes non séparées
            p.setKeepNext(true);
            p.getCTP().getPPr().addNewKeepLines();
        }
    }

    // ------------------------------------------------------------------ document libre : pdf

    /**
     * ⚠️ Lot D (2026-09-28, recette réelle du front) — les caractères du bloc Unicode <strong>Dingbats</strong> que les
     * documents types emploient, et leur code dans la police standard PDF <strong>ZapfDingbats</strong> (l'une des 14
     * polices de base : présente dans tout lecteur, rien à embarquer, rien à installer sur le serveur). Helvetica en WinAnsi
     * ne les porte pas : ils disparaissaient du PDF (les cases à cocher de l'AE du contrat-cadre). Correspondance vérifiée
     * sur la métrique AFM de la police fournie par OpenPDF ({@code C 111 … N a74}, {@code C 226 … N a173}) et la table de
     * correspondance Unicode de la police : U+274F ❏ = {@code o} (0x6F), U+27A2 ➢ = 0xE2. Le docx garde le caractère Unicode.
     */
    static final Map<Character, Character> DINGBATS = Map.of('❏', (char) 0x6F, '➢', (char) 0xE2);

    /**
     * Un paragraphe PDF : le texte dans sa police, et chaque caractère de {@link #DINGBATS} dans ZapfDingbats à la même
     * taille — substitution au rendu PDF seulement, le fichier de commande reste celui du document type.
     */
    static Paragraph paragraphePdf(String texte, Font police) {
        Paragraph par = new Paragraph();
        par.setFont(police);
        StringBuilder courant = new StringBuilder();
        for (char ch : texte.toCharArray()) {
            Character code = DINGBATS.get(ch);
            if (code == null) {
                courant.append(ch);
                continue;
            }
            if (courant.length() > 0) {
                par.add(new com.lowagie.text.Chunk(courant.toString(), police));
                courant.setLength(0);
            }
            par.add(new com.lowagie.text.Chunk(String.valueOf(code),
                    FontFactory.getFont(FontFactory.ZAPFDINGBATS, police.getSize())));
        }
        if (courant.length() > 0 || par.isEmpty()) {
            par.add(new com.lowagie.text.Chunk(courant.toString(), police));
        }
        return par;
    }

    private static byte[] pdfLibre(DocumentLibre m) {
        Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
        Font sousTitre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font texte = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font cellule = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font pied = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Document document = new Document(PageSize.A4, 50, 50, 50, 60);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            HeaderFooter footer = new HeaderFooter(new Phrase(m.piedDePage(), pied), false);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setBorder(0);
            document.setFooter(footer);
            document.open();
            java.util.function.Function<DocumentLibre.Paragraphe, Paragraph> rendu = p -> {
                Paragraph par = switch (p.style()) {
                    case TITRE -> aligne(paragraphePdf(p.texte(), titre), Element.ALIGN_CENTER);
                    case SOUS_TITRE -> aligne(paragraphePdf(p.texte(), sousTitre), Element.ALIGN_LEFT);
                    case PARA -> aligne(paragraphePdf(p.texte(), texte), Element.ALIGN_JUSTIFIED);
                    case CENTRE -> aligne(paragraphePdf(p.texte(), texte), Element.ALIGN_CENTER);
                    case DROITE -> aligne(paragraphePdf(p.texte(), texte), Element.ALIGN_RIGHT);
                    case VIDE -> new Paragraph(" ", texte);
                };
                par.setSpacingAfter(3);
                return par;
            };
            // ⚠️ 2026-10-01 — des paragraphes solidaires et celui qui les suit : un tableau sans bordure d'une colonne,
            // indivisible (OpenPDF n'a pas de « solidaire du suivant » par paragraphe) ; il passe en entier à la page suivante.
            com.lowagie.text.pdf.PdfPTable bloc = null;
            for (DocumentLibre.Element e : m.elements()) {
                if (e instanceof DocumentLibre.Paragraphe p && (bloc != null || p.solidaireDuSuivant())) {
                    if (bloc == null) {
                        bloc = new com.lowagie.text.pdf.PdfPTable(1);
                        bloc.setWidthPercentage(100);
                        bloc.setKeepTogether(true);
                    }
                    com.lowagie.text.pdf.PdfPCell cell = new com.lowagie.text.pdf.PdfPCell();
                    cell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
                    cell.setPadding(0);
                    cell.addElement(rendu.apply(p));
                    bloc.addCell(cell);
                    if (!p.solidaireDuSuivant()) {
                        document.add(bloc);
                        bloc = null;
                    }
                    continue;
                }
                if (bloc != null) {   // un bloc interrompu par un tableau ou une image : rendu tel quel
                    document.add(bloc);
                    bloc = null;
                }
                if (e instanceof DocumentLibre.Paragraphe p) {
                    document.add(rendu.apply(p));
                } else if (e instanceof DocumentLibre.Image im) {
                    try {
                        com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(im.contenu());
                        double[] taille = taillePoints(im);
                        img.scaleAbsolute((float) taille[0], (float) taille[1]);
                        img.setAlignment(com.lowagie.text.Image.ALIGN_CENTER);
                        document.add(img);
                    } catch (IOException ex) {
                        throw new IllegalStateException("Image " + im.nom() + " illisible : " + ex.getMessage(), ex);
                    }
                } else if (e instanceof DocumentLibre.Tableau t) {
                    com.lowagie.text.pdf.PdfPTable table = new com.lowagie.text.pdf.PdfPTable(t.colonnes());
                    table.setWidthPercentage(100);
                    table.setSpacingBefore(4);
                    table.setSpacingAfter(6);
                    for (List<List<String>> ligne : t.lignes()) {
                        for (int c = 0; c < t.colonnes(); c++) {
                            com.lowagie.text.pdf.PdfPCell cell = new com.lowagie.text.pdf.PdfPCell();
                            for (String paragraphe : c < ligne.size() ? ligne.get(c) : List.of("")) {
                                cell.addElement(paragraphePdf(paragraphe.isEmpty() ? " " : paragraphe, cellule));
                            }
                            table.addCell(cell);
                        }
                    }
                    document.add(table);
                }
            }
            if (bloc != null) {
                document.add(bloc);
            }
            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Génération du " + m.type() + " (pdf) impossible : " + e.getMessage(), e);
        }
    }

    private static Paragraph aligne(Paragraph p, int alignement) {
        p.setAlignment(alignement);
        return p;
    }

    // ------------------------------------------------------------------ docx

    private static byte[] docx(DocumentFicheModele m) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            paragraphe(doc, m.titre(), 16, true, ParagraphAlignment.CENTER, 120);
            if (m.sousTitre() != null && !m.sousTitre().isBlank()) {
                paragraphe(doc, m.sousTitre(), 12, false, ParagraphAlignment.CENTER, 240);
            }
            for (DocumentFicheModele.Bloc bloc : m.blocs()) {
                paragraphe(doc, bloc.titre(), 13, true, ParagraphAlignment.LEFT, 120);
                for (DocumentFicheModele.Rubrique rubrique : bloc.rubriques()) {
                    paragraphe(doc, rubrique.titre(), 11, true, ParagraphAlignment.LEFT, 60);
                    for (DocumentFicheModele.Ligne ligne : rubrique.lignes()) {
                        XWPFParagraph p = doc.createParagraph();
                        p.setSpacingAfter(40);
                        XWPFRun l = p.createRun();
                        l.setBold(true);
                        l.setFontSize(10);
                        l.setText(ligne.libelle() + " : ");
                        XWPFRun v = p.createRun();
                        v.setFontSize(10);
                        texte(v, ligne.valeur());
                    }
                }
            }
            // ⚠️ V45 (2026-09-25) — les tableaux (liste des fournitures : un par lot).
            for (DocumentFicheModele.Tableau t : m.tableaux() == null ? List.<DocumentFicheModele.Tableau>of() : m.tableaux()) {
                paragraphe(doc, t.titre(), 12, true, ParagraphAlignment.LEFT, 80);
                org.apache.poi.xwpf.usermodel.XWPFTable table = doc.createTable(t.lignes().size() + 1, t.entetes().size());
                table.setWidth("100%");
                for (int c = 0; c < t.entetes().size(); c++) {
                    cellule(table.getRow(0).getCell(c), t.entetes().get(c), true);
                }
                for (int l = 0; l < t.lignes().size(); l++) {
                    List<String> ligne = t.lignes().get(l);
                    for (int c = 0; c < t.entetes().size(); c++) {
                        cellule(table.getRow(l + 1).getCell(c), c < ligne.size() ? ligne.get(c) : "", false);
                    }
                }
                for (String mention : t.mentions()) {
                    paragraphe(doc, mention, 10, false, ParagraphAlignment.LEFT, 40);
                }
                paragraphe(doc, "", 6, false, ParagraphAlignment.LEFT, 120);
            }
            XWPFFooter pied = doc.createFooter(HeaderFooterType.DEFAULT);
            XWPFParagraph pp = pied.createParagraph();
            pp.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun r = pp.createRun();
            r.setFontSize(8);
            r.setText(m.piedDePage());
            if (m.filigrane() != null) {
                // ⚠️ V46 — gabarit provisoire : filigrane sur chaque page et « Page n de N » (champs Word).
                new org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy(doc).createWatermark(m.filigrane());
                XWPFParagraph pages = pied.createParagraph();
                pages.setAlignment(ParagraphAlignment.CENTER);
                pages.createRun().setText("Page ");
                pages.getCTP().addNewFldSimple().setInstr("PAGE");
                pages.createRun().setText(" de ");
                pages.getCTP().addNewFldSimple().setInstr("NUMPAGES");
                pages.createRun().setText(" pages");
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Génération du " + m.type() + " (docx) impossible : " + e.getMessage(), e);
        }
    }

    private static void cellule(org.apache.poi.xwpf.usermodel.XWPFTableCell cellule, String texte, boolean gras) {
        XWPFParagraph p = cellule.getParagraphs().get(0);
        XWPFRun r = p.createRun();
        r.setBold(gras);
        r.setFontSize(9);
        r.setText(texte == null ? "" : texte);
    }

    private static void paragraphe(XWPFDocument doc, String texte, int taille, boolean gras, ParagraphAlignment align,
            int espaceApres) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(align);
        p.setSpacingAfter(espaceApres);
        XWPFRun r = p.createRun();
        r.setBold(gras);
        r.setFontSize(taille);
        r.setText(texte);
    }

    /**
     * ⚠️ 2026-09-26 — la valeur d'une ligne, sur plusieurs lignes si elle en porte (formes de garantie admises : une
     * forme par ligne) : un saut de ligne par {@code \n}.
     */
    private static void texte(XWPFRun run, String valeur) {
        String[] lignes = valeur.split("\n", -1);
        for (int i = 0; i < lignes.length; i++) {
            if (i > 0) {
                run.addBreak();
            }
            run.setText(lignes[i]);
        }
    }

    /** ⚠️ 2026-09-26 — même chose en PDF : un {@link Chunk#NEWLINE} par {@code \n}. */
    private static Phrase phrase(String valeur, Font police) {
        Phrase ph = new Phrase();
        String[] lignes = valeur.split("\n", -1);
        for (int i = 0; i < lignes.length; i++) {
            if (i > 0) {
                ph.add(Chunk.NEWLINE);
            }
            ph.add(new Chunk(lignes[i], police));
        }
        return ph;
    }

    // ------------------------------------------------------------------ pdf

    /**
     * ⚠️ V46 (2026-09-25, §B8) — gabarit provisoire : le filigrane en diagonale sur chaque page, et « Page n de N » en
     * haut de page (le total s'écrit à la fermeture, dans un gabarit réservé sur chaque page).
     */
    private static final class FiligraneEtPagination extends com.lowagie.text.pdf.PdfPageEventHelper {
        private final String texte;
        private com.lowagie.text.pdf.PdfTemplate total;
        private com.lowagie.text.pdf.BaseFont police;

        FiligraneEtPagination(String texte) {
            this.texte = texte;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(40, 12);
            try {
                police = com.lowagie.text.pdf.BaseFont.createFont(com.lowagie.text.pdf.BaseFont.HELVETICA,
                        com.lowagie.text.pdf.BaseFont.WINANSI, false);
            } catch (DocumentException | IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            com.lowagie.text.pdf.PdfContentByte fond = writer.getDirectContentUnder();
            fond.saveState();
            com.lowagie.text.pdf.PdfGState g = new com.lowagie.text.pdf.PdfGState();
            g.setFillOpacity(0.18f);
            fond.setGState(g);
            fond.beginText();
            fond.setFontAndSize(police, 42);
            fond.setColorFill(java.awt.Color.GRAY);
            fond.showTextAligned(Element.ALIGN_CENTER, texte, document.getPageSize().getWidth() / 2,
                    document.getPageSize().getHeight() / 2, 45);
            fond.endText();
            fond.restoreState();

            com.lowagie.text.pdf.PdfContentByte dessus = writer.getDirectContent();
            String debut = "Page " + writer.getPageNumber() + " de ";
            float x = document.getPageSize().getWidth() - 50 - police.getWidthPoint(debut, 8) - 24;
            float y = document.getPageSize().getHeight() - 30;
            dessus.beginText();
            dessus.setFontAndSize(police, 8);
            dessus.setTextMatrix(x, y);
            dessus.showText(debut);
            dessus.endText();
            dessus.addTemplate(total, x + police.getWidthPoint(debut, 8), y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(police, 8);
            total.setTextMatrix(0, 0);
            total.showText((writer.getPageNumber() - 1) + " pages");
            total.endText();
        }
    }

    private static byte[] pdf(DocumentFicheModele m) {
        Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font sousTitre = FontFactory.getFont(FontFactory.HELVETICA, 12);
        Font bloc = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
        Font rubrique = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font libelle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font valeur = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font pied = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Document document = new Document(PageSize.A4, 50, 50, 50, 60);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            HeaderFooter footer = new HeaderFooter(new Phrase(m.piedDePage(), pied), false);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setBorder(0);
            document.setFooter(footer);
            if (m.filigrane() != null) {
                writer.setPageEvent(new FiligraneEtPagination(m.filigrane()));
            }
            document.open();
            Paragraph t = new Paragraph(m.titre(), titre);
            t.setAlignment(Element.ALIGN_CENTER);
            t.setSpacingAfter(6);
            document.add(t);
            if (m.sousTitre() != null && !m.sousTitre().isBlank()) {
                Paragraph st = new Paragraph(m.sousTitre(), sousTitre);
                st.setAlignment(Element.ALIGN_CENTER);
                st.setSpacingAfter(14);
                document.add(st);
            }
            for (DocumentFicheModele.Bloc b : m.blocs()) {
                Paragraph pb = new Paragraph(b.titre(), bloc);
                pb.setSpacingBefore(10);
                pb.setSpacingAfter(4);
                document.add(pb);
                for (DocumentFicheModele.Rubrique r : b.rubriques()) {
                    Paragraph pr = new Paragraph(r.titre(), rubrique);
                    pr.setSpacingBefore(4);
                    pr.setSpacingAfter(2);
                    document.add(pr);
                    for (DocumentFicheModele.Ligne l : r.lignes()) {
                        Paragraph pl = new Paragraph();
                        pl.add(new Phrase(l.libelle() + " : ", libelle));
                        pl.add(phrase(l.valeur(), valeur));
                        pl.setKeepTogether(l.valeur().indexOf('\n') >= 0);   // ⚠️ 2026-09-26 — l'énumération reste avec sa tête
                        pl.setSpacingAfter(2);
                        document.add(pl);
                    }
                }
            }
            for (DocumentFicheModele.Tableau tab : m.tableaux() == null ? List.<DocumentFicheModele.Tableau>of() : m.tableaux()) {
                Paragraph pt = new Paragraph(tab.titre(), bloc);
                pt.setSpacingBefore(10);
                pt.setSpacingAfter(4);
                document.add(pt);
                com.lowagie.text.pdf.PdfPTable table = new com.lowagie.text.pdf.PdfPTable(tab.entetes().size());
                table.setWidthPercentage(100);
                table.setHeaderRows(1);
                for (String e : tab.entetes()) {
                    table.addCell(new Phrase(e, libelle));
                }
                for (List<String> ligne : tab.lignes()) {
                    for (int c = 0; c < tab.entetes().size(); c++) {
                        table.addCell(new Phrase(c < ligne.size() && ligne.get(c) != null ? ligne.get(c) : "", valeur));
                    }
                }
                document.add(table);
                for (String mention : tab.mentions()) {
                    Paragraph pm = new Paragraph(mention, valeur);
                    pm.setSpacingBefore(2);
                    document.add(pm);
                }
            }
            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Génération du " + m.type() + " (pdf) impossible : " + e.getMessage(), e);
        }
    }
}
