package cnm.prs.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import com.documents4j.api.DocumentType;
import com.documents4j.api.IConverter;
import com.documents4j.job.LocalConverter;

import cnm.prs.exception.BusinessRuleException;
import jakarta.annotation.PreDestroy;

/**
 * Génère le PDF d'une lettre de renvoi <strong>à partir du modèle Word fourni</strong> :
 * copie du {@code .docx} ({@code resources/templates/LR_CENTRALE.docx} ou {@code LR_REGIONALE.docx}),
 * remplacement des placeholders <strong>au niveau du paragraphe</strong> (gère les placeholders
 * scindés sur plusieurs runs), puis conversion en PDF <strong>via Microsoft Word</strong>
 * (documents4j local) pour un rendu fidèle au modèle. La mise en forme et l'emblème sont conservés.
 *
 * <p>Pré-requis machine : Microsoft Word installé (automation COM). Le convertisseur est
 * initialisé à la première utilisation et fermé à l'arrêt de l'application.</p>
 */
@Component
public class LettreRenvoiDocumentGenerator {

    private static final String MODELE_CENTRALE = "/templates/LR_CENTRALE.docx";
    private static final String MODELE_REGIONALE = "/templates/LR_REGIONALE.docx";

    /** Convertisseur Word, initialisé paresseusement (démarre MS Word) et réutilisé. */
    private volatile IConverter convertisseur;

    /**
     * @param centrale      {@code true} → modèle central (ANT) ; {@code false} → modèle régional
     * @param remplacements placeholders littéraux (ex. {@code <DATE_LETTRE>}) → valeurs
     * @return le PDF de la lettre (copie du modèle, placeholders remplis)
     */
    public byte[] genererPdf(boolean centrale, Map<String, String> remplacements) {
        String modele = centrale ? MODELE_CENTRALE : MODELE_REGIONALE;
        try (InputStream in = getClass().getResourceAsStream(modele)) {
            if (in == null) {
                throw new BusinessRuleException("Modèle de lettre introuvable : " + modele);
            }
            XWPFDocument doc = new XWPFDocument(in);
            remplacerPartout(doc, remplacements);
            ByteArrayOutputStream docxOut = new ByteArrayOutputStream();
            doc.write(docxOut);
            doc.close();

            return convertirEnPdf(docxOut.toByteArray());
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleException("Génération du document de la lettre impossible : " + e.getMessage());
        }
    }

    /**
     * ⚠️ Robustesse (2026-08-04, même correctif que le générateur de PV) — Word peut s'arrêter entre
     * deux conversions : sans cela le convertisseur en cache reste « shut down » et toutes les
     * générations suivantes échouent jusqu'au redémarrage. On retente UNE fois avec un convertisseur neuf.
     */
    private byte[] convertirEnPdf(byte[] docx) {
        try {
            return convertir(docx);
        } catch (RuntimeException premiereTentative) {
            invaliderConvertisseur();
            return convertir(docx);
        }
    }

    private byte[] convertir(byte[] docx) {
        ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
        convertisseur().convert(new ByteArrayInputStream(docx))
                .as(DocumentType.DOCX)
                .to(pdfOut)
                .as(DocumentType.PDF)
                .execute();
        return pdfOut.toByteArray();
    }

    /** Convertisseur Word partagé, (re)créé s'il est absent ou arrêté (initialisation paresseuse, thread-safe). */
    private IConverter convertisseur() {
        IConverter c = convertisseur;
        if (c == null || !c.isOperational()) {
            synchronized (this) {
                c = convertisseur;
                if (c == null || !c.isOperational()) {
                    c = LocalConverter.builder().build();
                    convertisseur = c;
                }
            }
        }
        return c;
    }

    /** Oublie (et tente de fermer) le convertisseur courant : le suivant sera recréé. */
    private void invaliderConvertisseur() {
        synchronized (this) {
            IConverter c = convertisseur;
            convertisseur = null;
            if (c != null) {
                try {
                    c.shutDown();
                } catch (RuntimeException dejaArrete) {
                    // convertisseur déjà mort : rien à fermer
                }
            }
        }
    }

    @PreDestroy
    void fermerConvertisseur() {
        IConverter c = convertisseur;
        if (c != null) {
            c.shutDown();
        }
    }

    /** Remplace les placeholders dans tout le document : corps, tableaux, en-têtes et pieds de page. */
    private void remplacerPartout(XWPFDocument doc, Map<String, String> rempl) {
        doc.getParagraphs().forEach(p -> remplacerDansParagraphe(p, rempl));
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    cell.getParagraphs().forEach(p -> remplacerDansParagraphe(p, rempl));
                }
            }
        }
        for (XWPFHeader header : doc.getHeaderList()) {
            header.getParagraphs().forEach(p -> remplacerDansParagraphe(p, rempl));
        }
        for (XWPFFooter footer : doc.getFooterList()) {
            footer.getParagraphs().forEach(p -> remplacerDansParagraphe(p, rempl));
        }
    }

    /**
     * Remplace les placeholders dans un paragraphe en raisonnant sur le <strong>texte concaténé</strong>
     * de tous ses runs (un placeholder Word est souvent fragmenté). Le texte remplacé est placé dans le
     * premier run (formatage conservé via son {@code rPr}) et les runs suivants sont vidés.
     */
    private void remplacerDansParagraphe(XWPFParagraph paragraphe, Map<String, String> rempl) {
        List<XWPFRun> runs = paragraphe.getRuns();
        if (runs == null || runs.isEmpty()) {
            return;
        }
        StringBuilder concat = new StringBuilder();
        for (XWPFRun run : runs) {
            String t = run.getText(0);
            if (t != null) {
                concat.append(t);
            }
        }
        String texte = concat.toString();
        if (texte.isEmpty() || !contientUnPlaceholder(texte, rempl)) {
            return;
        }
        String remplace = texte;
        for (Map.Entry<String, String> e : rempl.entrySet()) {
            remplace = remplace.replace(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        runs.get(0).setText(remplace, 0);
        for (int i = runs.size() - 1; i >= 1; i--) {
            paragraphe.removeRun(i);
        }
    }

    private boolean contientUnPlaceholder(String texte, Map<String, String> rempl) {
        for (String cle : rempl.keySet()) {
            if (texte.contains(cle)) {
                return true;
            }
        }
        return false;
    }
}
