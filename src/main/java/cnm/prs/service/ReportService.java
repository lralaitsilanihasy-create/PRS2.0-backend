package cnm.prs.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import cnm.prs.config.HorlogeRecette;
import cnm.prs.entity.Dossier;
import cnm.prs.repository.DossierRepository;

/**
 * Génération des rapports périodiques au format PDF (§3.2 / §3.8 — Module 10).
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final DossierRepository dossierRepository;

    public ReportService(DossierRepository dossierRepository) {
        this.dossierRepository = dossierRepository;
    }

    /**
     * Rapport des dossiers traités, optionnellement borné par une période (sur DATE_REF) et
     * filtré sur une localité ({@code localite != null}, rapport par commission du CC, §3.3).
     *
     * @return le PDF sous forme de tableau d'octets
     */
    public byte[] rapportDossiers(LocalDate debut, LocalDate fin, String localite) {
        List<Dossier> dossiers = charger(debut, fin, localite);

        Font titreFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font enteteFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            document.add(new Paragraph("CNM — Rapport des dossiers traités", titreFont));
            String periode = (debut != null && fin != null)
                    ? "Période : du " + debut + " au " + fin
                    : "Période : tous les dossiers";
            document.add(new Paragraph(periode));
            document.add(new Paragraph("Localité : " + (localite != null ? localite : "toutes")));
            document.add(new Paragraph("Édité le : " + LocalDateTime.now(HorlogeRecette.HORLOGE)));
            document.add(new Paragraph("Nombre de dossiers : " + dossiers.size()));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(new float[] { 3, 2, 2, 2, 2 });
            table.setWidthPercentage(100);
            for (String entete : new String[] { "Référence", "Date", "Type", "Statut", "Localité" }) {
                PdfPCell cell = new PdfPCell(new Phrase(entete, enteteFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            for (Dossier d : dossiers) {
                table.addCell(safe(d.getRefeDossier()));
                table.addCell(d.getDateRef() != null ? d.getDateRef().toString() : "");
                table.addCell(safe(d.getIdTypeDossier()));
                table.addCell(safe(d.getStatut()));
                table.addCell(safe(d.getIdLocalite()));
            }
            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (RuntimeException e) {
            if (document.isOpen()) {
                document.close();
            }
            throw new IllegalStateException("Échec de génération du rapport PDF : " + e.getMessage(), e);
        }
    }

    /**
     * Même rapport des dossiers traités, au format Excel (.xlsx), filtrable par période et localité.
     *
     * @return le classeur sous forme de tableau d'octets
     */
    public byte[] rapportDossiersExcel(LocalDate debut, LocalDate fin, String localite) {
        List<Dossier> dossiers = charger(debut, fin, localite);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dossiers traités");

            CellStyle gras = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font font = workbook.createFont();
            font.setBold(true);
            gras.setFont(font);

            String[] entetes = { "Référence", "Date", "Type", "Statut", "Localité" };
            Row ligneEntete = sheet.createRow(0);
            for (int i = 0; i < entetes.length; i++) {
                Cell cell = ligneEntete.createCell(i);
                cell.setCellValue(entetes[i]);
                cell.setCellStyle(gras);
                sheet.setColumnWidth(i, 256 * 22);
            }

            int numLigne = 1;
            for (Dossier d : dossiers) {
                Row row = sheet.createRow(numLigne++);
                row.createCell(0).setCellValue(safe(d.getRefeDossier()));
                row.createCell(1).setCellValue(d.getDateRef() != null ? d.getDateRef().toString() : "");
                row.createCell(2).setCellValue(safe(d.getIdTypeDossier()));
                row.createCell(3).setCellValue(safe(d.getStatut()));
                row.createCell(4).setCellValue(safe(d.getIdLocalite()));
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Échec de génération du rapport Excel : " + e.getMessage(), e);
        }
    }

    /** Sélection des dossiers selon les 4 combinaisons période × localité. */
    private List<Dossier> charger(LocalDate debut, LocalDate fin, String localite) {
        boolean periode = debut != null && fin != null;
        if (localite != null) {
            return periode
                    ? dossierRepository.findByDateRefBetweenAndIdLocalite(debut, fin, localite)
                    : dossierRepository.findByIdLocalite(localite);
        }
        return periode
                ? dossierRepository.findByDateRefBetween(debut, fin)
                : dossierRepository.findAll();
    }

    private String safe(String s) {
        return s != null ? s : "";
    }
}
