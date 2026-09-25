package cnm.prs.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import cnm.prs.dto.ArticleBesoinDto;

/**
 * ⚠️ V45 (demande front du 2026-09-25, formulaires du candidat, §B3) — les deux <strong>classeurs du candidat</strong>
 * d'un lot, produits à la validation de la fiche depuis le besoin :
 *
 * <ul>
 *   <li><strong>Bordereau des prix</strong> : n° d'article, désignation, unité et quantités (minimum et maximum pour un
 *       marché à commande) pré-remplis ; <strong>seule la colonne « prix unitaire HT » est déverrouillée</strong> ;
 *       montants par ligne, totaux, TVA (taux administrable, {@code FICHE_TAUX_TVA}) et TTC en <strong>formules</strong>
 *       — calculés, jamais saisis. Feuille protégée (sans mot de passe : c'est une aide contre l'erreur de calcul, pas
 *       une dématérialisation de l'offre — la remise électronique n'est pas admise, le papier signé fait foi).</li>
 *   <li><strong>Tableau de conformité</strong> : une ligne par caractéristique exigée ; les colonnes du candidat
 *       (caractéristique proposée, marque, modèle, conforme OUI/NON) sont déverrouillées, le reste protégé.</li>
 * </ul>
 */
@Component
public class GenerateurClasseursFiche {

    /** Le bordereau des prix d'un lot ({@code lot} nul : ligne non allotie). */
    public byte[] bordereau(String reference, String objet, Integer lot, List<BesoinFiche.Article> articles,
            boolean aCommande, BigDecimal tauxTva) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles s = new Styles(wb);
            XSSFSheet f = wb.createSheet(lot == null ? "Bordereau des prix" : "Bordereau lot " + lot);
            int r = entete(f, s, "Bordereau des prix" + (lot == null ? "" : " — lot " + lot), reference, objet);
            List<String> colonnes = aCommande
                    ? List.of("N°", "Désignation", "Unité", "Quantité minimum", "Quantité maximum", "Prix unitaire HT",
                            "Montant minimum HT", "Montant maximum HT")
                    : List.of("N°", "Désignation", "Unité", "Quantité", "Prix unitaire HT", "Montant HT");
            ligneEntetes(f, s, r++, colonnes);
            int premiere = r;
            for (BesoinFiche.Article a : articles) {
                Row row = f.createRow(r);
                texte(row, 0, String.valueOf(a.ordre()), s.verrouille);
                texte(row, 1, a.designation(), s.verrouille);
                texte(row, 2, a.unite(), s.verrouille);
                int ligne = r + 1;   // numéro de ligne Excel (base 1)
                if (aCommande) {
                    nombre(row, 3, a.quantiteMin(), s.verrouille);
                    nombre(row, 4, a.quantiteMax(), s.verrouille);
                    row.createCell(5).setCellStyle(s.saisie);
                    formule(row, 6, "D" + ligne + "*F" + ligne, s.montant);
                    formule(row, 7, "E" + ligne + "*F" + ligne, s.montant);
                } else {
                    nombre(row, 3, a.quantite(), s.verrouille);
                    row.createCell(4).setCellStyle(s.saisie);
                    formule(row, 5, "D" + ligne + "*E" + ligne, s.montant);
                }
                r++;
            }
            int derniere = r;   // dernière ligne d'article (base 1)
            List<String> colonnesMontant = aCommande ? List.of("G", "H") : List.of("F");
            int colLibelle = aCommande ? 5 : 4;
            r = total(f, s, r, colLibelle, "Total HT", colonnesMontant, c -> "SUM(" + c + (premiere + 1) + ":" + c + derniere + ")");
            int ligneHt = r;
            if (tauxTva != null) {
                String taux = tauxTva.stripTrailingZeros().toPlainString();
                r = total(f, s, r, colLibelle, "TVA (" + taux + " %)", colonnesMontant, c -> c + ligneHt + "*" + taux + "/100");
                int ligneTva = r;
                r = total(f, s, r, colLibelle, "Total TTC", colonnesMontant, c -> c + ligneHt + "+" + c + ligneTva);
            }
            largeurs(f, aCommande ? new int[] { 6, 50, 10, 14, 14, 18, 20, 20 } : new int[] { 6, 50, 10, 12, 18, 20 });
            f.protectSheet("");
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Génération du bordereau des prix impossible : " + e.getMessage(), e);
        }
    }

    /** Le tableau de conformité d'un lot. */
    public byte[] conformite(String reference, String objet, Integer lot, List<BesoinFiche.Article> articles) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles s = new Styles(wb);
            XSSFSheet f = wb.createSheet(lot == null ? "Conformité" : "Conformité lot " + lot);
            int r = entete(f, s, "Spécifications techniques — tableau de conformité" + (lot == null ? "" : " — lot " + lot),
                    reference, objet);
            ligneEntetes(f, s, r++, List.of("N°", "Article", "Caractéristique", "Caractéristique exigée",
                    "Caractéristique proposée", "Marque", "Modèle", "Conforme (OUI/NON)"));
            int premiere = r;
            for (BesoinFiche.Article a : articles) {
                List<ArticleBesoinDto.Caracteristique> caracs = a.caracteristiques() == null ? List.of() : a.caracteristiques();
                for (ArticleBesoinDto.Caracteristique c : caracs) {
                    Row row = f.createRow(r++);
                    texte(row, 0, String.valueOf(a.ordre()), s.verrouille);
                    texte(row, 1, a.designation(), s.verrouille);
                    texte(row, 2, c.getLibelle(), s.verrouille);
                    texte(row, 3, c.getExigence(), s.verrouille);
                    for (int col = 4; col <= 7; col++) {
                        row.createCell(col).setCellStyle(s.saisie);
                    }
                }
            }
            if (r > premiere) {
                DataValidationHelper aide = f.getDataValidationHelper();
                DataValidation v = aide.createValidation(aide.createExplicitListConstraint(new String[] { "OUI", "NON" }),
                        new CellRangeAddressList(premiere, r - 1, 7, 7));
                v.setShowErrorBox(true);
                f.addValidationData(v);
            }
            largeurs(f, new int[] { 6, 36, 28, 40, 40, 16, 16, 14 });
            f.protectSheet("");
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Génération du tableau de conformité impossible : " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ mise en forme

    private static final class Styles {
        final CellStyle titre;
        final CellStyle entete;
        final CellStyle verrouille;
        final CellStyle saisie;
        final CellStyle montant;
        final CellStyle totalLibelle;

        Styles(XSSFWorkbook wb) {
            Font gras = wb.createFont();
            gras.setBold(true);
            Font grand = wb.createFont();
            grand.setBold(true);
            grand.setFontHeightInPoints((short) 13);
            titre = wb.createCellStyle();
            titre.setFont(grand);
            entete = bordure(wb.createCellStyle());
            entete.setFont(gras);
            entete.setWrapText(true);
            verrouille = bordure(wb.createCellStyle());
            verrouille.setWrapText(true);
            saisie = bordure(wb.createCellStyle());
            saisie.setLocked(false);
            saisie.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            montant = bordure(wb.createCellStyle());
            montant.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            totalLibelle = bordure(wb.createCellStyle());
            totalLibelle.setFont(gras);
        }

        private static CellStyle bordure(CellStyle s) {
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            return s;
        }
    }

    /** Titre, référence du dossier, objet ; renvoie la ligne suivante (base 0), après une ligne vide. */
    private static int entete(XSSFSheet f, Styles s, String titre, String reference, String objet) {
        texte(f.createRow(0), 0, titre, s.titre);
        texte(f.createRow(1), 0, "Dossier d'appel d'offres : " + (reference == null ? "—" : reference), null);
        texte(f.createRow(2), 0, "Objet : " + (objet == null ? "—" : objet), null);
        return 4;
    }

    private static void ligneEntetes(XSSFSheet f, Styles s, int r, List<String> colonnes) {
        Row row = f.createRow(r);
        for (int c = 0; c < colonnes.size(); c++) {
            texte(row, c, colonnes.get(c), s.entete);
        }
    }

    private static int total(XSSFSheet f, Styles s, int r, int colLibelle, String libelle, List<String> colonnes,
            java.util.function.Function<String, String> formule) {
        Row row = f.createRow(r);
        texte(row, colLibelle, libelle, s.totalLibelle);
        for (String c : colonnes) {
            formule(row, c.charAt(0) - 'A', formule.apply(c), s.montant);
        }
        return r + 1;
    }

    private static void texte(Row row, int col, String v, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(v == null ? "" : v);
        if (style != null) {
            c.setCellStyle(style);
        }
    }

    private static void nombre(Row row, int col, Integer v, CellStyle style) {
        Cell c = row.createCell(col);
        if (v != null) {
            c.setCellValue(v);
        }
        c.setCellStyle(style);
    }

    private static void formule(Row row, int col, String f, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellFormula(f);
        c.setCellStyle(style);
    }

    private static void largeurs(XSSFSheet f, int[] caracteres) {
        for (int i = 0; i < caracteres.length; i++) {
            f.setColumnWidth(i, caracteres[i] * 256);
        }
    }
}
