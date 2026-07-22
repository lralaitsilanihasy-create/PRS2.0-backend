package cnm.prs.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.SaisiePpmImportResult;
import cnm.prs.dto.SaisiePpmImportResult.BeneficiaireImport;
import cnm.prs.dto.SaisiePpmImportResult.LotImport;
import cnm.prs.dto.SaisiePpmImportResult.MarcheImport;
import cnm.prs.dto.SaisiePpmImportResult.PrevisionImport;
import cnm.prs.exception.BadRequestException;

/**
 * ⚠️ Règle ajoutée (2026-07-22) — import PPM depuis un <strong>tableur</strong> (Excel {@code .xlsx}) à colonnes
 * explicites, endpoint {@code POST /api/saisies/ppm/import-xlsx}. Contrairement au PDF, la transcription est
 * <strong>exacte par construction</strong> (chaque champ dans sa cellule) — le PDF ne reste qu'un justificatif.
 * Read-only : pré-remplit le formulaire ({@link SaisiePpmImportResult}, mêmes anomalies structurées), la création
 * reste {@code POST /api/saisies/ppm}.
 *
 * <p>L'<strong>assemblage</strong> (résolution des référentiels par libellé, forme, anomalies) est délégué à
 * {@link SaisiePpmImportService#assemblerMarche} — <strong>partagé</strong> avec l'import PDF ; seule l'extraction
 * amont diffère (cellules vs géométrie). Une ligne dont l'<em>objet</em> est vide continue le marché précédent
 * (bénéficiaire supplémentaire).</p>
 */
@Service
public class SaisiePpmXlsxImportService {

    private final SaisiePpmImportService assembleur;

    public SaisiePpmXlsxImportService(SaisiePpmImportService assembleur) {
        this.assembleur = assembleur;
    }

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Colonnes du gabarit : nom canonique → alias tolérés (normalisés). */
    private static final Map<String, String[]> COLONNES = new java.util.LinkedHashMap<>();
    static {
        COLONNES.put("objet", new String[] { "objet", "designation", "designationmarche" });
        COLONNES.put("forme", new String[] { "forme", "formemarche" });
        COLONNES.put("nature", new String[] { "nature", "naturelibelle" });
        COLONNES.put("montant", new String[] { "montantestimatif", "montant", "montestim" });
        COLONNES.put("nouvMontant", new String[] { "nouveaumontant", "nouveaumontantestimatif", "nouvmontestim" });
        COLONNES.put("mode", new String[] { "mode", "modepassation", "modelibelle" });
        COLONNES.put("financement", new String[] { "financement" });
        COLONNES.put("soa", new String[] { "soa", "soacode", "servicebeneficiaire" });
        COLONNES.put("compte", new String[] { "compte", "numcompte" });
        COLONNES.put("montantBenef", new String[] { "montantbeneficiaire", "montantparbeneficiaire", "ancmontbenef" });
        COLONNES.put("nouvMontantBenef", new String[] { "nouveaumontantbeneficiaire", "nouvmontbenef" });
        COLONNES.put("dateLancement", new String[] { "datelancement", "lancement" });
        COLONNES.put("dateOuverture", new String[] { "dateouverture", "ouverture" });
        COLONNES.put("dateAttribution", new String[] { "dateattribution", "attribution" });
        COLONNES.put("lots", new String[] { "lots", "lot" });
        COLONNES.put("exercice", new String[] { "exercice", "annee" });
        COLONNES.put("dateSignature", new String[] { "datesignature", "signature" });
    }

    public SaisiePpmImportResult importer(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Fichier tableur manquant ou vide.");
        }
        List<String> avert = new ArrayList<>();
        Integer exercice = null;
        String dateSignature = null;
        List<MarcheImport> marches = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(fichier.getBytes()))) {
            Sheet sheet = wb.getNumberOfSheets() == 0 ? null : wb.getSheetAt(0);
            if (sheet == null) {
                throw new BadRequestException("Classeur vide.");
            }
            Map<String, Integer> col = repererColonnes(sheet);
            if (!col.containsKey("objet") || !col.containsKey("montant")) {
                throw new BadRequestException(
                        "Colonnes obligatoires « objet » et « montant estimatif » introuvables — utilisez le gabarit.");
            }

            // Accumulateur du marché courant (multi-bénéficiaires : lignes à objet vide = continuation).
            String objet = null;
            BigDecimal montEstim = null;
            BigDecimal nouvMontEstim = null;
            String nature = null;
            String mode = null;
            String forme = null;
            String financement = null;
            List<BeneficiaireImport> benef = new ArrayList<>();
            List<String> dates = new ArrayList<>();
            List<LotImport> lots = new ArrayList<>();

            int premiere = sheet.getFirstRowNum() + 1;   // ligne 0 = en-têtes
            for (int r = premiere; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || estLigneVide(row, col)) {
                    continue;
                }
                String objetLigne = texte(row, col.get("objet"));
                boolean nouveauMarche = objetLigne != null && !objetLigne.isBlank();

                if (nouveauMarche) {
                    // Clôturer le marché précédent.
                    if (objet != null) {
                        marches.add(assembler(objet, montEstim, nouvMontEstim, nature, mode, forme, financement,
                                benef, dates, lots, avert));
                    }
                    objet = objetLigne.trim();
                    montEstim = montant(row, col.get("montant"));
                    nouvMontEstim = montant(row, col.get("nouvMontant"));
                    nature = texte(row, col.get("nature"));
                    mode = texte(row, col.get("mode"));
                    forme = texte(row, col.get("forme"));
                    financement = texte(row, col.get("financement"));
                    benef = new ArrayList<>();
                    dates = List.of(date(row, col.get("dateLancement")), date(row, col.get("dateOuverture")),
                            date(row, col.get("dateAttribution")));
                    lots = parserLots(texte(row, col.get("lots")));
                    if (exercice == null) {
                        exercice = entier(row, col.get("exercice"));
                    }
                    if (dateSignature == null) {
                        dateSignature = date(row, col.get("dateSignature"));
                    }
                } else if (objet == null) {
                    avert.add("Ligne " + (r + 1) + " ignorée : bénéficiaire sans marché de rattachement (objet vide en tête).");
                    continue;
                }
                // Bénéficiaire de la ligne (marché ou continuation).
                BeneficiaireImport b = beneficiaire(row, col);
                if (b != null) {
                    benef.add(b);
                }
            }
            if (objet != null) {
                marches.add(assembler(objet, montEstim, nouvMontEstim, nature, mode, forme, financement,
                        benef, dates, lots, avert));
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Tableur illisible ou format invalide (attendu .xlsx) : " + e.getMessage());
        }

        if (marches.isEmpty()) {
            avert.add("Aucune ligne de marché exploitable dans le tableur — vérifiez le gabarit.");
        }
        int nbAVerifier = (int) marches.stream()
                .filter(m -> m.anomalies() != null && !m.anomalies().isEmpty()).count();
        return new SaisiePpmImportResult(exercice, dateSignature, null, null, marches, avert, nbAVerifier);
    }

    private MarcheImport assembler(String objet, BigDecimal montEstim, BigDecimal nouvMontEstim, String nature,
            String mode, String forme, String financement, List<BeneficiaireImport> benef, List<String> dates,
            List<LotImport> lots, List<String> avert) {
        // Bénéficiaire unique sans montant explicite → défaut = montant estimatif (invariant satisfait).
        if (benef.size() == 1 && benef.get(0).ancMontBenef() == null && montEstim != null) {
            BeneficiaireImport b = benef.get(0);
            benef = List.of(new BeneficiaireImport(b.soaCode(), b.numCompte(), montEstim, b.nouvMontBenef()));
        }
        List<PrevisionImport> prev = List.of(
                new PrevisionImport("LANCEMENT", dates.size() > 0 ? dates.get(0) : null),
                new PrevisionImport("OUVERTURE", dates.size() > 1 ? dates.get(1) : null),
                new PrevisionImport("ATTRIBUTION", dates.size() > 2 ? dates.get(2) : null));
        return assembleur.assemblerMarche(nature, objet, montEstim, nouvMontEstim, financement, mode,
                List.copyOf(benef), prev, lots, forme, false, avert);
    }

    /** Bénéficiaire d'une ligne (null si ni SOA, ni compte, ni montant bénéficiaire). */
    private BeneficiaireImport beneficiaire(Row row, Map<String, Integer> col) {
        String soa = texte(row, col.get("soa"));
        String compte = texte(row, col.get("compte"));
        BigDecimal anc = montant(row, col.get("montantBenef"));
        BigDecimal nouv = montant(row, col.get("nouvMontantBenef"));
        if ((soa == null || soa.isBlank()) && (compte == null || compte.isBlank()) && anc == null && nouv == null) {
            return null;
        }
        return new BeneficiaireImport(vide(soa) ? null : soa.trim(), vide(compte) ? null : compte.trim(), anc, nouv);
    }

    private static List<LotImport> parserLots(String cellule) {
        if (cellule == null || cellule.isBlank()) {
            return List.of();
        }
        List<LotImport> lots = new ArrayList<>();
        for (String part : cellule.split("[|;\\n]")) {
            String d = part.trim();
            if (!d.isEmpty()) {
                lots.add(new LotImport(d, null, null, null));
            }
        }
        return lots;
    }

    /** En-têtes (ligne 0) → index de colonne par nom canonique (normalisation tolérante). */
    private Map<String, Integer> repererColonnes(Sheet sheet) {
        Map<String, Integer> res = new HashMap<>();
        Row entete = sheet.getRow(sheet.getFirstRowNum());
        if (entete == null) {
            return res;
        }
        for (int c = entete.getFirstCellNum(); c < entete.getLastCellNum(); c++) {
            String h = normaliser(texte(entete, c));
            if (h.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, String[]> e : COLONNES.entrySet()) {
                for (String alias : e.getValue()) {
                    if (h.equals(alias)) {
                        res.putIfAbsent(e.getKey(), c);
                    }
                }
            }
        }
        return res;
    }

    private static boolean estLigneVide(Row row, Map<String, Integer> col) {
        for (Integer c : col.values()) {
            String t = texte(row, c);
            if (t != null && !t.isBlank()) {
                return false;
            }
        }
        return true;
    }

    // --- Lecture de cellules (tolérante aux types Excel) ---

    private static String texte(Row row, Integer c) {
        if (c == null || row == null) {
            return null;
        }
        Cell cell = row.getCell(c);
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().format(ISO)
                    : nombreEnTexte(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> texteFormule(cell);
            default -> null;
        };
    }

    private static String texteFormule(Cell cell) {
        try {
            return cell.getStringCellValue().trim();
        } catch (IllegalStateException e) {
            return nombreEnTexte(cell.getNumericCellValue());
        }
    }

    /** Nombre Excel → texte sans notation scientifique ni « .0 » superflu. */
    private static String nombreEnTexte(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return BigDecimal.valueOf(d).toBigInteger().toString();
        }
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal montant(Row row, Integer c) {
        if (c == null || row == null) {
            return null;
        }
        Cell cell = row.getCell(c);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
        }
        String t = texte(row, c);
        if (t == null || t.isBlank()) {
            return null;
        }
        String net = t.replaceAll("[\\s\\u00a0]", "").replace(',', '.');
        try {
            return new BigDecimal(net);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer entier(Row row, Integer c) {
        BigDecimal m = montant(row, c);
        return m == null ? null : m.intValue();
    }

    /** Date d'une cellule → {@code yyyy-MM-dd} ; accepte date Excel, {@code dd/MM/yyyy} ou {@code yyyy-MM-dd}. */
    private static String date(Row row, Integer c) {
        if (c == null || row == null) {
            return null;
        }
        Cell cell = row.getCell(c);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().format(ISO);
        }
        String t = texte(row, c);
        if (t == null || t.isBlank()) {
            return null;
        }
        t = t.trim();
        try {
            if (t.matches("\\d{2}/\\d{2}/\\d{4}")) {
                String[] p = t.split("/");
                return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0])).format(ISO);
            }
            if (t.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(t).format(ISO);
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }

    private static String normaliser(String s) {
        if (s == null) {
            return "";
        }
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.FRENCH).replaceAll("[^a-z0-9]", "");
    }

    /** Génère le gabarit {@code .xlsx} (en-têtes + exemples + notice) à remplir par la PRMP. */
    public byte[] genererGabarit() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String[] entetes = { "objet", "forme", "nature", "montant estimatif", "nouveau montant", "mode",
                    "financement", "soa", "compte", "montant beneficiaire", "nouveau montant beneficiaire",
                    "date lancement", "date ouverture", "date attribution", "lots", "exercice", "date signature" };
            Sheet sheet = wb.createSheet("Marchés");
            Row h = sheet.createRow(0);
            for (int i = 0; i < entetes.length; i++) {
                h.createCell(i).setCellValue(entetes[i]);
            }
            // Exemple 1 : marché simple, 1 bénéficiaire.
            Object[] ex1 = { "Travaux de réhabilitation de la RN 13", "QUANTITE_FIXE", "Travaux", 500000000L, "",
                    "Consultation de prix ouverte", "RPI", "00-61-0-D10-00000", "2441", 500000000L, "",
                    "06/03/2026", "16/03/2026", "27/03/2026", "", 2026L, "14/04/2026" };
            remplir(sheet.createRow(1), ex1);
            // Exemple 2 : marché à 2 bénéficiaires (2e ligne = objet vide = continuation).
            Object[] ex2 = { "Fourniture de matériel", "QUANTITE_FIXE", "Fournitures", 3000000L, "", "Achat Direct",
                    "RPI", "00-21-0-J00-00000", "6211", 1000000L, "", "06/03/2026", "16/03/2026", "27/03/2026",
                    "", "", "" };
            remplir(sheet.createRow(2), ex2);
            Object[] ex2b = { "", "", "", "", "", "", "", "00-22-0-J00-00000", "6211", 2000000L, "", "", "", "", "", "", "" };
            remplir(sheet.createRow(3), ex2b);
            for (int i = 0; i < entetes.length; i++) {
                sheet.autoSizeColumn(i);
            }

            Sheet notice = wb.createSheet("Notice");
            String[] lignes = {
                    "GABARIT D'IMPORT PPM (.xlsx) — une ligne par marché ; onglet « Marchés ».",
                    "",
                    "Colonnes obligatoires : objet, montant estimatif.",
                    "forme : A_COMMANDE | CONTRAT_CADRE | QUANTITE_FIXE (défaut QUANTITE_FIXE si vide).",
                    "nature / mode : libellés (résolus au référentiel ; sinon signalés « à confirmer »).",
                    "soa / compte : identifiants du service bénéficiaire et du compte.",
                    "montant beneficiaire : montant par bénéficiaire ; pour 1 seul bénéficiaire, laisser vide = montant estimatif.",
                    "Plusieurs bénéficiaires : ajouter une ligne SOUS le marché avec l'objet VIDE (seuls soa/compte/montant bénéficiaire).",
                    "dates : jj/mm/aaaa ou date Excel (lancement / ouverture / attribution).",
                    "lots : désignations séparées par « | » (ex. Lot 1 | Lot 2).",
                    "exercice / date signature : à renseigner sur la 1re ligne (facultatif).",
                    "",
                    "L'import est READ-ONLY : il pré-remplit le formulaire. La création reste POST /api/saisies/ppm.",
                    "Chaque marché est renvoyé avec ses anomalies[] (montant incohérent, référentiel inconnu, champ manquant…)." };
            for (int i = 0; i < lignes.length; i++) {
                notice.createRow(i).createCell(0).setCellValue(lignes[i]);
            }
            notice.setColumnWidth(0, 120 * 256);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BadRequestException("Génération du gabarit impossible : " + e.getMessage());
        }
    }

    private static void remplir(Row row, Object[] valeurs) {
        for (int i = 0; i < valeurs.length; i++) {
            Cell c = row.createCell(i);
            Object v = valeurs[i];
            if (v instanceof Number n) {
                c.setCellValue(n.doubleValue());
            } else {
                c.setCellValue(String.valueOf(v));
            }
        }
    }
}
