package cnm.prs.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;

import cnm.prs.dto.ArticleBesoinDto;

/**
 * ⚠️ 2026-10-16 (demande front « charte-dao », §B3) — les <strong>tableaux du besoin</strong> imprimés dans le DAO complet, depuis le
 * bloc B12 de la version validée : un tableau par lot, dans l'ordre des lots du plan, chaque lot sur une nouvelle page sous « Lot k :
 * {intitulé au plan} » (sans titre sur une ligne non allotie). Les intitulés de colonnes sont <strong>fixes, au mot près</strong> :
 * l'import du DAO les reconnaît ({@link LectureTableaux}). Les colonnes de prix, de montant et du candidat restent vides.
 *
 * <ul>
 *   <li>fournitures et services : le cadre du bordereau des prix (annexe 1 de l'acte d'engagement), les spécifications techniques,
 *       la liste des fournitures et le calendrier de livraison ;</li>
 *   <li>travaux : le bordereau des prix unitaires (prix en lettres, sans quantité) puis le détail quantitatif et estimatif, avec les
 *       mêmes lignes de série et le total HT, TVA, TTC.</li>
 * </ul>
 * Chaque tableau suit la charte (§B1) : Arial 9,5 pt, filets de 0,5 pt, en-tête gras sur gris répété sur chaque page, ligne insécable.
 */
public final class TableauxBesoin {

    private TableauxBesoin() {
    }

    /** Un lot du plan : son rang ({@code null} : ligne non allotie) et son intitulé. */
    public record Lot(Integer numero, String intitule) {
    }

    /** Le contexte d'impression : la forme, l'objet, les lots et la mention propre au contrat-cadre. */
    public record Contexte(boolean aCommande, boolean contratCadre, String objet, List<Lot> lots, boolean forfaitaire) {

        public Contexte(boolean aCommande, boolean contratCadre, String objet, List<Lot> lots) {
            this(aCommande, contratCadre, objet, lots, false);
        }
    }

    /** Les colonnes, au mot près (§B3, §B4 règle 4). */
    static final List<String> BORDEREAU = List.of("N°", "DESIGNATION DES ARTICLES ET PRIX UNITAIRE EN TOUTES LETTRES", "UNITE", "QUANTITE",
            "PRIX UNITAIRE", "MONTANT EN ARIARY");
    static final List<String> BORDEREAU_COMMANDE = List.of("N°", "DESIGNATION DES ARTICLES ET PRIX UNITAIRE EN TOUTES LETTRES", "UNITE", "MIN",
            "MAX", "PRIX UNITAIRE", "MONTANT EN ARIARY");
    static final List<String> SPECIFICATIONS = List.of("N°", "DESIGNATIONS", "SPECIFICATIONS MINIMUMS REQUISES", "PROPOSITIONS DU CANDIDAT");
    static final List<String> LISTE = List.of("N°", "Fournitures", "Destination finale", "Délai de livraison au plus tard",
            "Délai offert par le candidat");
    static final List<String> BPU = List.of("N° prix", "DESIGNATION DES OUVRAGES ET PRIX UNITAIRE EN TOUTES LETTRES", "UNITE",
            "PRIX UNITAIRE EN CHIFFRES");
    /** ⚠️ T4 — la récapitulation d'un lot de travaux. */
    static final List<String> RECAPITULATION = List.of("SERIE", "DESIGNATION", "MONTANT EN ARIARY");
    static final List<String> DQE = List.of("N° prix", "DESIGNATION DES OUVRAGES", "UNITE", "QUANTITE", "PRIX UNITAIRE", "MONTANT EN ARIARY");

    /** Les colonnes de quantités, alignées à droite (R3). */
    private static final java.util.Set<String> QUANTITES = java.util.Set.of("QUANTITE", "MIN", "MAX");

    /** Largeur du texte (21 cm − 4,4 cm de marges), en vingtièmes de point. */
    static final int LARGEUR = 9412;
    private static final String POINTILLES = "……………………";

    // ------------------------------------------------------------------ fournitures et services

    /** Le cadre du bordereau des prix ; {@code null} sans article. */
    public static byte[] bordereau(Contexte c, List<ArticleBesoinDto> articles) {
        List<String> entete = c.aCommande() ? BORDEREAU_COMMANDE : BORDEREAU;
        int[] largeurs = c.aCommande() ? new int[] { 550, 3800, 900, 800, 800, 1250, 1312 } : new int[] { 550, 4200, 950, 950, 1350, 1412 };
        return document(c, articles, "ANNEXE 1 — CADRE DU BORDEREAU DES PRIX", null, (doc, lot, as) -> {
            List<List<String>> lignes = new ArrayList<>();
            for (ArticleBesoinDto a : as) {
                List<String> l = new ArrayList<>(List.of(numero(a), designationPrix(a.getDesignation(), a.getUnite(), null), texte(a.getUnite())));
                if (c.aCommande()) {
                    l.add(nombre(a.getQuantiteMin()));
                    l.add(nombre(a.getQuantiteMax()));
                } else {
                    l.add(nombre(a.getQuantite()));
                }
                l.add("");
                l.add("");
                lignes.add(l);
            }
            XWPFTable t = table(doc, entete, largeurs, lignes);
            ligneTotale(t, entete.size(), "Montant de l'offre" + (lot.numero() == null ? "" : " — lot " + lot.numero()), false);
            paragraphe(doc, "Arrêté le montant du marché à la somme de " + POINTILLES + " (en lettres et en chiffres) Ariary.", false, ParagraphAlignment.LEFT);
            if (c.contratCadre()) {
                paragraphe(doc, "Les quantités sont indicatives : le contrat-cadre s'exécute par bons de commande, aux prix unitaires ci-dessus.",
                        false, ParagraphAlignment.LEFT);
            }
            signature(doc);
        });
    }

    /** Les spécifications techniques ; {@code null} sans article. Une caractéristique par ligne, « libellé : exigence ». */
    public static byte[] specifications(Contexte c, List<ArticleBesoinDto> articles) {
        return document(c, articles, "SPECIFICATIONS TECHNIQUES", null, (doc, lot, as) -> {
            List<List<List<String>>> lignes = new ArrayList<>();
            for (ArticleBesoinDto a : as) {
                List<String> caracs = new ArrayList<>();
                for (ArticleBesoinDto.Caracteristique k : a.getCaracteristiques() == null ? List.<ArticleBesoinDto.Caracteristique>of()
                        : a.getCaracteristiques()) {
                    String l = texte(k.getLibelle());
                    String e = texte(k.getExigence());
                    caracs.add(e.isEmpty() ? l : l.isEmpty() ? e : l + " : " + e);
                }
                lignes.add(List.of(List.of(numero(a)), List.of(texte(a.getDesignation())), caracs.isEmpty() ? List.of("") : caracs, List.of("")));
            }
            tableMulti(doc, SPECIFICATIONS, new int[] { 550, 2700, 3800, 2362 }, lignes);
            signature(doc);
        });
    }

    /**
     * La liste des fournitures et le calendrier de livraison : destination {@code B09-LL-01} et délai {@code B09-DX-01} jours du lot ;
     * pour le contrat-cadre à marchés subséquents uniques à délai fixé par l'autorité, « Fixée par chaque marché subséquent » et
     * {@code B07-DE-02}. {@code valeurs} : les valeurs de la fiche ({@code CODE#n} d'abord, puis {@code CODE}).
     */
    public static byte[] liste(Contexte c, List<ArticleBesoinDto> articles, Map<String, String> valeurs) {
        return document(c, articles, "ANNEXE AUX SPECIFICATIONS TECHNIQUES", "LISTE DES FOURNITURES ET CALENDRIER DE LIVRAISON", (doc, lot, as) -> {
            String destination;
            String delai;
            if (c.contratCadre()) {
                destination = "Fixée par chaque marché subséquent";
                delai = texte(valeur(valeurs, "B07-DE-02", lot.numero()));
            } else {
                destination = texte(valeur(valeurs, "B09-LL-01", lot.numero()));
                String jours = texte(valeur(valeurs, "B09-DX-01", lot.numero()));
                delai = jours.isEmpty() ? "" : jours + " jours à compter du lendemain de la notification de l'ordre de service";
            }
            List<List<String>> lignes = new ArrayList<>();
            for (ArticleBesoinDto a : as) {
                lignes.add(List.of(numero(a), texte(a.getDesignation()), destination, delai, ""));
            }
            table(doc, LISTE, new int[] { 550, 2800, 2300, 2100, 1662 }, lignes);
            signature(doc);
        });
    }

    // ------------------------------------------------------------------ travaux

    /**
     * Le bordereau des prix unitaires (travaux) : n° de prix ; la désignation puis, ⚠️ 17/10 (charte travaux, T3), sur la ligne
     * suivante de la même cellule, « Le mètre cube à : …………… Ariary » ; l'unité ; lignes de série.
     */
    public static byte[] bpu(Contexte c, List<ArticleBesoinDto> articles) {
        return document(c, articles, "BORDEREAU DES PRIX UNITAIRES", null, (doc, lot, as) -> {
            List<List<List<String>>> lignes = new ArrayList<>();
            List<Integer> genres = new ArrayList<>();
            travaux(as, lignes, genres, a -> List.of(List.of(texte(a.getNumeroPrix())),
                    List.of(texte(a.getDesignation()), lignePrix(a.getUnite(), a.getLibelleBordereau())), List.of(texte(a.getUnite())), List.of("")),
                    4, false);
            XWPFTable t = tableMulti(doc, BPU, new int[] { 900, 5600, 1100, 1812 }, lignes);
            griser(t, genres);
            signature(doc);
        });
    }

    /**
     * Le détail quantitatif et estimatif (travaux), ou la décomposition du prix global et forfaitaire : mêmes n° de prix et
     * lignes de série ; ⚠️ 17/10 (T4), après chaque série « Sous-total {code} — {libellé} », puis, sous le tableau du lot, sa
     * récapitulation (une ligne par série, total HT, TVA, TTC), « Arrêté le montant… », « Fait à…, le… », « Le candidat ».
     */
    public static byte[] dqe(Contexte c, List<ArticleBesoinDto> articles, BigDecimal tauxTva) {
        return document(c, articles, c.forfaitaire() ? "DECOMPOSITION DU PRIX GLOBAL ET FORFAITAIRE" : "DETAIL QUANTITATIF ET ESTIMATIF", null,
                (doc, lot, as) -> {
                    List<List<List<String>>> lignes = new ArrayList<>();
                    List<Integer> genres = new ArrayList<>();
                    travaux(as, lignes, genres, a -> List.of(List.of(texte(a.getNumeroPrix())), List.of(texte(a.getDesignation())),
                            List.of(texte(a.getUnite())), List.of(nombre(a.getQuantite())), List.of(""), List.of("")), 6, true);
                    XWPFTable t = tableMulti(doc, DQE, new int[] { 900, 4100, 900, 1100, 1200, 1212 }, lignes);
                    griser(t, genres);
                    // La récapitulation du lot.
                    paragraphe(doc, "RECAPITULATION" + (lot.numero() == null ? "" : " — LOT " + lot.numero()), true, ParagraphAlignment.CENTER)
                            .setSpacingBefore(200);
                    List<List<List<String>>> recap = new ArrayList<>();
                    String vue = null;
                    for (ArticleBesoinDto a : as) {
                        String s = texte(a.getSerie());
                        if (!s.isEmpty() && !s.equals(vue)) {
                            recap.add(List.of(List.of(s), List.of(texte(a.getSerieLibelle()).toUpperCase(Locale.FRENCH)), List.of("")));
                            vue = s;
                        }
                    }
                    XWPFTable r = tableMulti(doc, RECAPITULATION, new int[] { 1200, 5600, 2612 }, recap);
                    ligneTotale(r, 3, "Total hors taxes", false);
                    ligneTotale(r, 3, "TVA" + (tauxTva == null ? "" : " " + FormulairesEnLigne.lisible(tauxTva) + " %"), false);
                    ligneTotale(r, 3, "Total toutes taxes comprises", false);
                    paragraphe(doc, "Arrêté le montant " + (lot.numero() == null ? "du marché" : "du lot " + lot.numero()) + " à la somme de "
                            + POINTILLES + " (en lettres et en chiffres) Ariary toutes taxes comprises.", false, ParagraphAlignment.LEFT);
                    signature(doc);
                });
    }

    /** Ligne d'article, de série ou de sous-total : le genre de chaque ligne du tableau. */
    static final int ARTICLE = 0;
    static final int SERIE = 1;
    static final int SOUS_TOTAL = 2;

    /**
     * Les lignes de travaux : une ligne de série (sans unité) devant le premier article de chaque série et, avec {@code sousTotaux},
     * une ligne « Sous-total {code} — {libellé} » après son dernier article (sans n° ni unité : la relecture n'y voit pas d'article).
     */
    private static void travaux(List<ArticleBesoinDto> as, List<List<List<String>>> lignes, List<Integer> genres,
            java.util.function.Function<ArticleBesoinDto, List<List<String>>> ligne, int colonnes, boolean sousTotaux) {
        String serie = null;
        String libelle = null;
        for (ArticleBesoinDto a : as) {
            String s = texte(a.getSerie());
            if (!s.isEmpty() && !s.equals(serie)) {
                if (sousTotaux && serie != null) {
                    lignes.add(ligneVide(colonnes, "Sous-total " + serie + " — " + libelle));
                    genres.add(SOUS_TOTAL);
                }
                List<List<String>> l = new ArrayList<>(List.of(List.of(s), List.of(texte(a.getSerieLibelle()).toUpperCase(Locale.FRENCH))));
                while (l.size() < colonnes) {
                    l.add(List.of(""));
                }
                lignes.add(l);
                genres.add(SERIE);
                serie = s;
                libelle = texte(a.getSerieLibelle()).toUpperCase(Locale.FRENCH);
            }
            lignes.add(ligne.apply(a));
            genres.add(ARTICLE);
        }
        if (sousTotaux && serie != null) {
            lignes.add(ligneVide(colonnes, "Sous-total " + serie + " — " + libelle));
            genres.add(SOUS_TOTAL);
        }
    }

    /** Une ligne sans n° ni unité, son libellé dans la colonne de la désignation. */
    private static List<List<String>> ligneVide(int colonnes, String libelle) {
        List<List<String>> l = new ArrayList<>();
        for (int j = 0; j < colonnes; j++) {
            l.add(List.of(j == 1 ? libelle : ""));
        }
        return l;
    }

    /** « Le mètre cube à : …………… Ariary » : le libellé du bordereau quand il est saisi, sinon l'unité et son article défini. */
    static String lignePrix(String unite, String libelleBordereau) {
        String l = texte(libelleBordereau);
        String u = l.isEmpty() ? uniteAvecArticle(unite) : l;
        return Character.toUpperCase(u.charAt(0)) + u.substring(1) + " à : …………… Ariary";
    }

    // ------------------------------------------------------------------ le document

    private interface Lotisseur {
        void lot(XWPFDocument doc, Lot lot, List<ArticleBesoinDto> articles);
    }

    /** Le document : son titre, puis un tableau par lot (nouvelle page à chaque lot), sous « Lot k : … » sur une ligne allotie. */
    private static byte[] document(Contexte c, List<ArticleBesoinDto> articles, String titre, String sousTitre, Lotisseur lotisseur) {
        if (articles == null || articles.isEmpty()) {
            return null;
        }
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CharteDao.normalJustifie(doc);   // ⚠️ R3 : sans quoi Word perd l'alignement à gauche des cellules à l'insertion
            boolean premier = true;
            List<Lot> lots = c.lots() == null || c.lots().isEmpty() ? List.of(new Lot(null, null)) : c.lots();
            for (Lot lot : lots) {
                List<ArticleBesoinDto> as = articles.stream().filter(a -> Objects.equals(a.getLot(), lot.numero()))
                        .sorted(java.util.Comparator.comparing((ArticleBesoinDto a) -> a.getOrdre() == null ? 0 : a.getOrdre())).toList();
                if (as.isEmpty()) {
                    continue;
                }
                // Le premier lot suit le titre du plan (celui du sommaire) : le titre du tableau ne le répète qu'aux lots suivants, en tête
                // de leur page.
                if (!premier) {
                    paragraphe(doc, titre, true, ParagraphAlignment.CENTER).setPageBreak(true);
                }
                if (sousTitre != null) {
                    paragraphe(doc, sousTitre, true, ParagraphAlignment.CENTER);
                }
                if (lot.numero() == null) {
                    if (c.objet() != null) {
                        paragraphe(doc, c.objet(), true, ParagraphAlignment.CENTER);
                    }
                } else {
                    paragraphe(doc, "Lot " + lot.numero() + " : " + texte(lot.intitule()), true, ParagraphAlignment.LEFT);
                }
                lotisseur.lot(doc, lot, as);
                premier = false;
            }
            if (premier) {
                return null;
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Tableau du besoin impossible : " + e.getMessage(), e);
        }
    }

    private static XWPFTable table(XWPFDocument doc, List<String> entete, int[] largeurs, List<List<String>> lignes) {
        List<List<List<String>>> multi = new ArrayList<>();
        for (List<String> l : lignes) {
            multi.add(l.stream().map(List::of).toList());
        }
        return tableMulti(doc, entete, largeurs, multi);
    }

    /** Un tableau : en-tête gras sur gris répété sur chaque page, lignes insécables, filets de 0,5 pt, 9,5 pt. */
    private static XWPFTable tableMulti(XWPFDocument doc, List<String> entete, int[] largeurs, List<List<List<String>>> lignes) {
        XWPFTable t = doc.createTable(lignes.size() + 1, entete.size());
        CTTblWidth w = t.getCTTbl().getTblPr().isSetTblW() ? t.getCTTbl().getTblPr().getTblW() : t.getCTTbl().getTblPr().addNewTblW();
        w.setType(STTblWidth.DXA);
        w.setW(BigInteger.valueOf(LARGEUR));
        CTTblGrid grille = t.getCTTbl().getTblGrid() == null ? t.getCTTbl().addNewTblGrid() : t.getCTTbl().getTblGrid();
        while (grille.sizeOfGridColArray() > 0) {
            grille.removeGridCol(0);
        }
        for (int l : largeurs) {
            grille.addNewGridCol().setW(BigInteger.valueOf(l));
        }
        CharteDao.filets(t);
        // ⚠️ R3 (recette du 16/10) — marges intérieures : 1 mm en haut et en bas, 1,5 mm à gauche et à droite (vingtièmes de point).
        t.setCellMargins(57, 85, 57, 85);
        for (int i = 0; i <= lignes.size(); i++) {
            XWPFTableRow r = t.getRow(i);
            CTTrPr pr = r.getCtRow().isSetTrPr() ? r.getCtRow().getTrPr() : r.getCtRow().addNewTrPr();
            pr.addNewCantSplit();
            if (i == 0) {
                pr.addNewTblHeader();
            }
            for (int j = 0; j < entete.size(); j++) {
                XWPFTableCell cell = r.getCell(j);
                cell.setWidth(String.valueOf(largeurs[j]));
                List<String> ps = i == 0 ? List.of(entete.get(j)) : j < lignes.get(i - 1).size() ? lignes.get(i - 1).get(j) : List.of("");
                for (int k = 0; k < ps.size(); k++) {
                    XWPFParagraph p = k == 0 ? cell.getParagraphs().get(0) : cell.addParagraph();
                    p.setAlignment(i > 0 && QUANTITES.contains(entete.get(j)) ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
                    p.setSpacingAfter(0);
                    p.setSpacingBefore(0);
                    p.getCTP().getPPr().addNewSuppressAutoHyphens();
                    XWPFRun run = p.createRun();
                    run.setFontFamily(CharteDao.POLICE);
                    run.setFontSize(9.5);
                    run.setBold(i == 0);
                    run.setText(ps.get(k));
                }
                if (i == 0) {
                    cell.setColor(CharteDao.GRIS);
                }
            }
        }
        return t;
    }

    /** Les lignes de série en gras sur gris ; ⚠️ T4 : les sous-totaux en italique gras sur gris. */
    private static void griser(XWPFTable t, List<Integer> genres) {
        for (int i = 0; i < genres.size(); i++) {
            if (genres.get(i) != ARTICLE) {
                boolean italique = genres.get(i) == SOUS_TOTAL;
                for (XWPFTableCell c : t.getRow(i + 1).getTableCells()) {
                    c.setColor("F3F3F3");
                    c.getParagraphs().forEach(p -> p.getRuns().forEach(r -> {
                        r.setBold(true);
                        r.setItalic(italique);
                    }));
                }
            }
        }
    }

    /** Une ligne de total en gras, son libellé dans la colonne de la désignation. */
    private static void ligneTotale(XWPFTable t, int colonnes, String libelle, boolean grise) {
        XWPFTableRow r = t.createRow();
        CTTrPr pr = r.getCtRow().isSetTrPr() ? r.getCtRow().getTrPr() : r.getCtRow().addNewTrPr();
        pr.addNewCantSplit();
        for (int j = 0; j < colonnes; j++) {
            XWPFTableCell c = r.getCell(j) == null ? r.addNewTableCell() : r.getCell(j);
            XWPFParagraph p = c.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.LEFT);
            XWPFRun run = p.createRun();
            run.setFontFamily(CharteDao.POLICE);
            run.setFontSize(9.5);
            run.setBold(true);
            run.setText(j == 1 ? libelle : "");
            if (grise) {
                c.setColor("F3F3F3");
            }
        }
    }

    private static XWPFParagraph paragraphe(XWPFDocument doc, String texte, boolean gras, ParagraphAlignment alignement) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(alignement);
        p.setSpacingAfter(100);
        p.setKeepNext(true);
        XWPFRun r = p.createRun();
        r.setFontFamily(CharteDao.POLICE);
        r.setFontSize(gras ? 11 : 10.5);
        r.setBold(gras);
        r.setText(texte);
        return p;
    }

    private static void signature(XWPFDocument doc) {
        paragraphe(doc, "Fait à " + POINTILLES + ", le " + POINTILLES, false, ParagraphAlignment.LEFT).setSpacingBefore(160);
        paragraphe(doc, "Le candidat", true, ParagraphAlignment.RIGHT);
    }

    // ------------------------------------------------------------------ règles de contenu

    /**
     * « {désignation}, {article défini} {unité} à ….Ariary » : « la rame », « la boîte », « le paquet », « le rouleau », « l'unité »
     * par défaut ; en travaux, le libellé du bordereau quand il est saisi (« Le mètre cube »).
     */
    static String designationPrix(String designation, String unite, String libelleBordereau) {
        String d = texte(designation);
        String l = texte(libelleBordereau);
        String unit = l.isEmpty() ? uniteAvecArticle(unite) : Character.toLowerCase(l.charAt(0)) + l.substring(1);
        return d + ", " + unit + " à ….Ariary";
    }

    private static final Map<String, String> ABREGES = Map.ofEntries(Map.entry("u", "unité"), Map.entry("un", "unité"), Map.entry("unite", "unité"),
            Map.entry("fft", "forfait"), Map.entry("ff", "forfait"), Map.entry("ens", "ensemble"), Map.entry("m3", "mètre cube"),
            Map.entry("m²", "mètre carré"), Map.entry("m2", "mètre carré"), Map.entry("m³", "mètre cube"), Map.entry("ml", "mètre linéaire"),
            Map.entry("m", "mètre"), Map.entry("kg", "kilogramme"), Map.entry("t", "tonne"), Map.entry("l", "litre"), Map.entry("pce", "pièce"),
            Map.entry("pcs", "pièce"), Map.entry("bte", "boîte"), Map.entry("paq", "paquet"), Map.entry("rlx", "rouleau"), Map.entry("km", "kilomètre"));
    private static final java.util.Set<String> FEMININS = java.util.Set.of("rame", "ramette", "boîte", "boite", "pièce", "piece", "paire", "bouteille",
            "tonne", "caisse", "feuille", "cartouche", "palette", "douzaine", "barre", "plaque", "bobine", "trousse", "chemise", "boule", "barquette",
            "bonbonne", "canette", "bande", "planche", "botte", "dose", "séance", "journée", "heure", "semaine", "visite", "mission");

    /** « la rame », « le paquet », « l'unité » ; une unité inconnue ou vide : « l'unité ». */
    static String uniteAvecArticle(String unite) {
        String u = texte(unite).toLowerCase(Locale.FRENCH);
        String mot = ABREGES.getOrDefault(u.replace(".", ""), u);
        if (mot.isEmpty() || mot.length() <= 1) {
            return "l'unité";
        }
        char c = Normalizer.normalize(mot, Normalizer.Form.NFD).charAt(0);
        if ("aeiouyh".indexOf(c) >= 0) {
            return "l'" + mot;
        }
        return (FEMININS.contains(mot) || FEMININS.contains(mot.split(" ")[0]) ? "la " : "le ") + mot;
    }

    static String nombre(BigDecimal n) {
        return n == null ? "" : FormulairesEnLigne.lisible(n);
    }

    private static String numero(ArticleBesoinDto a) {
        return a.getOrdre() == null ? "" : String.valueOf(a.getOrdre());
    }

    private static String texte(String s) {
        return s == null ? "" : s.trim();
    }

    /** La valeur de fiche d'un lot : {@code CODE#n} d'abord, puis {@code CODE}. */
    static String valeur(Map<String, String> valeurs, String code, Integer lot) {
        if (valeurs == null) {
            return null;
        }
        String v = lot == null ? null : valeurs.get(code + LotsFiche.SEPARATEUR + lot);
        return v != null && !v.isBlank() ? v : valeurs.get(code);
    }
}
