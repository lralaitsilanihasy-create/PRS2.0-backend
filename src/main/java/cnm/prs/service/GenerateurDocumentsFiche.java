package cnm.prs.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

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
                        v.setText(ligne.valeur());
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

    // ------------------------------------------------------------------ pdf

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
            PdfWriter.getInstance(document, out);
            HeaderFooter footer = new HeaderFooter(new Phrase(m.piedDePage(), pied), false);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setBorder(0);
            document.setFooter(footer);
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
                        pl.add(new Phrase(l.valeur(), valeur));
                        pl.setSpacingAfter(2);
                        document.add(pl);
                    }
                }
            }
            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Génération du " + m.type() + " (pdf) impossible : " + e.getMessage(), e);
        }
    }
}
