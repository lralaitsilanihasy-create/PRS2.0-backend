package cnm.prs.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * ⚠️ V59 (demande front du 2026-10-02, DQE des travaux, §B1.3) — le <strong>bordereau des prix et détail quantitatif
     * et estimatif</strong> d'un lot de travaux. Les articles sont groupés par série (ordre de première apparition), chaque
     * série ouverte par son intertitre et close par son sous-total ; une récapitulation par série en pied, puis total HT,
     * TVA ({@code FICHE_TAUX_TVA}) et TTC — tout en formules. Seules les colonnes du candidat sont ouvertes : le prix
     * unitaire HT et, à prix unitaires ou mixtes ({@code typePrix}), le prix unitaire en toutes lettres (les lettres font
     * foi), précédé du libellé du bordereau (« Le mètre cube à : »). Un article plafonné reçoit une cellule de contrôle
     * en formule (« respecté / dépassé ») : elle informe le candidat sans rien bloquer. Les prix soumis à sous-détail sont
     * listés sur une seconde feuille.
     */
    public byte[] bordereauTravaux(String reference, String objet, Integer lot, List<BesoinFiche.Article> articles,
            boolean aCommande, String typePrix, BigDecimal tauxTva) {
        boolean lettres = "UNITAIRES".equalsIgnoreCase(typePrix) || "MIXTE".equalsIgnoreCase(typePrix);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles s = new Styles(wb);
            XSSFSheet f = wb.createSheet(lot == null ? "Bordereau des prix - DQE" : "Bordereau DQE lot " + lot);
            int r = entete(f, s, "Bordereau des prix et détail quantitatif et estimatif" + (lot == null ? "" : " — lot " + lot),
                    reference, objet);
            List<String> colonnes = new ArrayList<>(List.of("N° de prix", "Désignation", "Unité"));
            colonnes.addAll(aCommande ? List.of("Quantité minimum", "Quantité maximum") : List.of("Quantité"));
            if (lettres) {
                colonnes.addAll(List.of("Libellé du bordereau", "Prix unitaire en toutes lettres"));
            }
            colonnes.add("Prix unitaire HT");
            colonnes.addAll(aCommande ? List.of("Montant minimum HT", "Montant maximum HT") : List.of("Montant HT"));
            ligneEntetes(f, s, r++, colonnes);
            int colQte = 3;
            int colPrix = colonnes.indexOf("Prix unitaire HT");
            List<Integer> colsMontant = aCommande ? List.of(colPrix + 1, colPrix + 2) : List.of(colPrix + 1);
            String prix = lettre(colPrix);
            Map<String, List<BesoinFiche.Article>> series = new LinkedHashMap<>();
            for (BesoinFiche.Article a : articles) {
                series.computeIfAbsent(a.serie() == null ? "" : a.serie(), k -> new ArrayList<>()).add(a);
            }
            Map<String, Integer> sousTotaux = new LinkedHashMap<>();   // série → ligne Excel (base 1) de son sous-total
            Map<String, String> libelles = new LinkedHashMap<>();
            List<Map.Entry<BesoinFiche.Article, Integer>> lignes = new ArrayList<>();
            for (Map.Entry<String, List<BesoinFiche.Article>> e : series.entrySet()) {
                String libelle = e.getValue().stream().map(BesoinFiche.Article::serieLibelle)
                        .filter(x -> x != null && !x.isBlank()).findFirst().orElse("");
                libelles.put(e.getKey(), libelle);
                Row titre = f.createRow(r++);
                texte(titre, 0, e.getKey(), s.totalLibelle);
                texte(titre, 1, libelle, s.totalLibelle);
                int premiere = r + 1;
                for (BesoinFiche.Article a : e.getValue()) {
                    Row row = f.createRow(r);
                    int ligne = r + 1;
                    lignes.add(Map.entry(a, ligne));
                    texte(row, 0, a.numeroPrix(), s.verrouille);
                    texte(row, 1, a.designation(), s.verrouille);
                    texte(row, 2, a.unite(), s.verrouille);
                    if (aCommande) {
                        decimal(row, colQte, a.quantiteMin(), s.quantite);
                        decimal(row, colQte + 1, a.quantiteMax(), s.quantite);
                    } else {
                        decimal(row, colQte, a.quantite(), s.quantite);
                    }
                    if (lettres) {
                        texte(row, colPrix - 2, a.libelleBordereau() == null ? "" : a.libelleBordereau() + " à :", s.verrouille);
                        row.createCell(colPrix - 1).setCellStyle(s.saisieTexte);
                    }
                    row.createCell(colPrix).setCellStyle(s.saisie);
                    for (int k = 0; k < colsMontant.size(); k++) {
                        formule(row, colsMontant.get(k), lettre(colQte + k) + ligne + "*" + prix + ligne, s.montant);
                    }
                    r++;
                }
                int derniere = r;
                Row st = f.createRow(r++);
                texte(st, 1, "Sous-total série " + e.getKey() + (libelle.isEmpty() ? "" : " — " + libelle), s.totalLibelle);
                for (int c : colsMontant) {
                    formule(st, c, "SUM(" + lettre(c) + premiere + ":" + lettre(c) + derniere + ")", s.montant);
                }
                sousTotaux.put(e.getKey(), r);
            }
            r++;
            texte(f.createRow(r++), 1, "Récapitulation", s.titre);
            int premiereRecap = r + 1;
            for (Map.Entry<String, Integer> e : sousTotaux.entrySet()) {
                Row row = f.createRow(r++);
                texte(row, 0, e.getKey(), s.verrouille);
                texte(row, 1, libelles.get(e.getKey()), s.verrouille);
                for (int c : colsMontant) {
                    formule(row, c, lettre(c) + e.getValue(), s.montant);
                }
            }
            int derniereRecap = r;
            List<String> lettresMontant = colsMontant.stream().map(GenerateurClasseursFiche::lettre).toList();
            r = total(f, s, r, 1, "Total HT", lettresMontant, c -> "SUM(" + c + premiereRecap + ":" + c + derniereRecap + ")");
            int ligneHt = r;
            if (tauxTva != null) {
                String taux = tauxTva.stripTrailingZeros().toPlainString();
                r = total(f, s, r, 1, "TVA (" + taux + " %)", lettresMontant, c -> c + ligneHt + "*" + taux + "/100");
                int ligneTva = r;
                r = total(f, s, r, 1, "Total TTC", lettresMontant, c -> c + ligneHt + "+" + c + ligneTva);
            }
            // À commande : le plafond se juge sur les montants maximum.
            String montantRetenu = lettresMontant.get(lettresMontant.size() - 1);
            boolean plafonds = false;
            for (Map.Entry<BesoinFiche.Article, Integer> e : lignes) {
                BesoinFiche.Article a = e.getKey();
                if (a.plafond() == null) {
                    continue;
                }
                if (!plafonds) {
                    r++;
                    texte(f.createRow(r++), 1, "Contrôle des prix plafonnés (information du candidat)", s.totalLibelle);
                    plafonds = true;
                }
                String p = a.plafond().stripTrailingZeros().toPlainString();
                Row row = f.createRow(r++);
                formule(row, 1, "\"" + a.numeroPrix() + " : " + p.replace('.', ',') + " % au plus du montant des travaux — \"&IF("
                        + montantRetenu + e.getValue() + "<=" + p + "/100*" + montantRetenu + ligneHt
                        + ",\"respecté\",\"dépassé\")", s.verrouille);
            }
            int[] largeurs = new int[colonnes.size()];
            for (int c = 0; c < colonnes.size(); c++) {
                String nom = colonnes.get(c);
                largeurs[c] = c == 1 ? 50 : nom.startsWith("Prix unitaire en") ? 36 : nom.startsWith("Libellé") ? 22
                        : nom.startsWith("N°") ? 10 : "Unité".equals(nom) ? 8 : 18;
            }
            largeurs(f, largeurs);
            f.protectSheet("");
            List<BesoinFiche.Article> sousDetail = articles.stream().filter(BesoinFiche.Article::sousDetail).toList();
            if (!sousDetail.isEmpty()) {
                XSSFSheet g = wb.createSheet("Prix soumis à sous-détail");
                int q = entete(g, s, "Liste des prix soumis à sous-détail" + (lot == null ? "" : " — lot " + lot), reference, objet);
                ligneEntetes(g, s, q++, List.of("N° de prix", "Désignation", "Unité"));
                for (BesoinFiche.Article a : sousDetail) {
                    Row row = g.createRow(q++);
                    texte(row, 0, a.numeroPrix(), s.verrouille);
                    texte(row, 1, a.designation(), s.verrouille);
                    texte(row, 2, a.unite(), s.verrouille);
                }
                largeurs(g, new int[] { 10, 60, 8 });
                g.protectSheet("");
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Génération du bordereau des prix et DQE impossible : " + e.getMessage(), e);
        }
    }

    /**
     * ⚠️ 2026-10-03 — la ligne « dossier » d'un classeur : « Dossier d'appel d'offres : 001-DAOO/… (plan de passation :
     * 00004/PPM-…) », ou « Plan de passation : 00004/PPM-… » tant que le numéro du DAO ({@code B02-OB-03}) n'est pas saisi.
     * La référence du plan n'est pas celle du DAO.
     */
    public static String ligneDossier(String numeroDao, String referencePlan) {
        String plan = referencePlan == null || referencePlan.isBlank() ? "—" : referencePlan;
        return numeroDao == null || numeroDao.isBlank() ? "Plan de passation : " + plan
                : "Dossier d'appel d'offres : " + numeroDao.trim() + " (plan de passation : " + plan + ")";
    }

    /** La lettre d'une colonne (0 → A). */
    static String lettre(int colonne) {
        return org.apache.poi.ss.util.CellReference.convertNumToColString(colonne);
    }

    private static void decimal(Row row, int col, BigDecimal v, CellStyle style) {
        Cell c = row.createCell(col);
        if (v != null) {
            c.setCellValue(v.doubleValue());
        }
        c.setCellStyle(style);
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
        final CellStyle saisieTexte;
        final CellStyle quantite;

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
            saisieTexte = bordure(wb.createCellStyle());   // ⚠️ V59 — le prix en toutes lettres
            saisieTexte.setLocked(false);
            saisieTexte.setWrapText(true);
            quantite = bordure(wb.createCellStyle());   // ⚠️ V59 — quantités à deux décimales
            quantite.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        }

        private static CellStyle bordure(CellStyle s) {
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            return s;
        }
    }

    /**
     * Titre, dossier, objet ; renvoie la ligne suivante (base 0), après une ligne vide. ⚠️ 2026-10-03 (contre-recette du
     * front, fiche 32) — {@code reference} est la ligne du dossier déjà rédigée par l'appelant
     * ({@link #ligneDossier}) : le numéro du DAO, et la référence du plan de passation dont il relève.
     */
    private static int entete(XSSFSheet f, Styles s, String titre, String reference, String objet) {
        texte(f.createRow(0), 0, titre, s.titre);
        texte(f.createRow(1), 0, reference == null ? "Dossier d'appel d'offres : —" : reference, null);
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

    private static void nombre(Row row, int col, BigDecimal v, CellStyle style) {
        Cell c = row.createCell(col);
        if (v != null) {
            c.setCellValue(v.doubleValue());   // ⚠️ V59 — quantités décimales
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
