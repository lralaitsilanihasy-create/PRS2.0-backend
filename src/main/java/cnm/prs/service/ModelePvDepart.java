package cnm.prs.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;

import cnm.prs.dto.ModelePvDto;

/**
 * ⚠️ 2026-10-21 (demande front « modele-pv-depart ») — le <strong>modèle de départ</strong> d'un PV, assemblé par l'application à
 * partir du PV officiel du plan avec réserves ({@code PV_AFSR_PPM_*}) : l'application <strong>assemble</strong>, elle ne rédige pas.
 * Les phrases de chaque famille sont écrites ici une fois (Q1), à faire valider par la CNM ; le texte juridique de la base (formule
 * « L'an … », signatures, annexe) est repris tel quel.
 *
 * <p>Ce que fait l'assemblage (§B2) : titre et intitulé de la famille, « L'an » en lettres, avis en trois blocs (gras gardé),
 * observation générale saisie, annexe sous {@code AVEC_RESERVES} avec sa ligne {@code {{POUR:OBSERVATIONS}}}, anciens chevrons
 * remplacés par leurs variables ; pour {@code les-deux}, les écarts entre les PV centrale et régionale en blocs {@code CENTRALE} /
 * {@code REGIONALE} ; pour le dossier de marché, le tableau des titulaires proposés et l'écart à l'estimation. Pur : aucune base.</p>
 */
public final class ModelePvDepart {

    public static final String CENTRALE = "centrale";
    public static final String REGIONALE = "regionale";
    public static final String LES_DEUX = "les-deux";
    public static final List<String> PORTEES = List.of(CENTRALE, REGIONALE, LES_DEUX);

    static final String MOTIF = "Modèle de départ généré par l'application — texte à faire valider par la CNM";
    private static final String GABARITS = "/templates/";
    private static final String BASE_CENTRALE = "PV_AFSR_PPM_CENTRALE.docx";
    private static final String BASE_REGIONALE = "PV_AFSR_PPM_REGIONALE.docx";

    /** Les phrases d'une famille : complément du titre, intitulé du dossier, ce sur quoi porte l'avis, titulaires proposés. */
    public record Famille(String code, String titre, String intitule, String cible, boolean titulaires) {
    }

    /** Le Word assemblé, les fichiers officiels qui ont servi, et ce qui se déclare d'office avec lui (§B4). */
    public record Depart(byte[] docx, List<String> bases, List<ModelePvDto.Condition> conditions, List<ModelePvDto.Champ> champs) {
    }

    /** §B3 — propositions du front, articles ajoutés au titre (« DE L'ACTE », « DE LA DÉCLARATION »). Le plan (DDP) n'en a pas. */
    public static final Map<String, Famille> FAMILLES = Map.of(
            "DMC", new Famille("DMC", "DU DOSSIER DE MISE EN CONCURRENCE",
                    "{{DOSSIER.SOUS_TYPE}} n° {{DOSSIER.REFERENCE}} — {{PROCEDURE.OBJET}} (procédure {{PROCEDURE.NUMERO}}, "
                            + "{{PROCEDURE.MODE}}, lot {{PROCEDURE.LOT}})",
                    "au dossier de mise en concurrence", false),
            "DDM", new Famille("DDM", "DU DOSSIER DE MARCHÉ",
                    "{{DOSSIER.SOUS_TYPE}} n° {{DOSSIER.REFERENCE}} — {{PROCEDURE.OBJET}} (procédure {{PROCEDURE.NUMERO}}, "
                            + "lot {{PROCEDURE.LOT}}), issu du dossier de mise en concurrence {{MARCHE.DOSSIER_MEC}}",
                    "à l’attribution du marché", true),
            "DGC", new Famille("DGC", "DE L’ACTE DE GESTION CONTRACTUELLE", "{{DOSSIER.SOUS_TYPE}} n° {{DOSSIER.REFERENCE}}",
                    "à l’acte de gestion", false),
            "DSS", new Famille("DSS", "DE LA DÉCLARATION SANS SUITE", "{{DOSSIER.SOUS_TYPE}} n° {{DOSSIER.REFERENCE}}",
                    "à la déclaration sans suite", false));

    private ModelePvDepart() {
    }

    /** Assemble le modèle de départ d'une famille ({@link #FAMILLES}) pour une portée ({@link #PORTEES}). */
    public static Depart assembler(Famille f, String portee) throws IOException {
        boolean deux = LES_DEUX.equals(portee);
        String base = REGIONALE.equals(portee) ? BASE_REGIONALE : BASE_CENTRALE;
        List<String> bases = deux ? List.of(BASE_CENTRALE, BASE_REGIONALE) : List.of(base);
        String commission = deux ? "{{SI:CENTRALE}}Centrale{{FINSI:CENTRALE}}{{SI:REGIONALE}}Régionale{{FINSI:REGIONALE}}"
                : REGIONALE.equals(portee) ? "Régionale" : "Centrale";
        byte[] docx;
        try (InputStream in = ModelePvDepart.class.getResourceAsStream(GABARITS + base);
                XWPFDocument doc = new XWPFDocument(in); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<XWPFParagraph> corps = new ArrayList<>(doc.getParagraphs());
            for (XWPFParagraph p : tous(doc)) {
                ModelePvWord.recoller(p);
            }
            // Avant toute insertion : les paragraphes neufs se créent en fin de corps avant d'être déplacés.
            finAnnexe(doc);
            List<Runnable> deplacements = new ArrayList<>();
            XWPFParagraph titre = null;
            for (int i = 0; i < corps.size(); i++) {
                XWPFParagraph p = corps.get(i);
                String t = p.getText().trim();
                if (deux && t.startsWith("COMMISSION CENTRALE")) {
                    // Les deux premières lignes de l'en-tête : la commission, puis la localité (régionale seule).
                    ecrire(p, List.of(new Morceau("COMMISSION {{SI:CENTRALE}}CENTRALE DES MARCHES….{{FINSI:CENTRALE}}"
                            + "{{SI:REGIONALE}}REGIONALE DES MARCHES{{FINSI:REGIONALE}}", rpr(p, 0))));
                } else if (t.startsWith("PROCES-VERBAL")) {
                    titre = p;
                    ecrire(p, List.of(new Morceau("PROCÈS-VERBAL D’EXAMEN " + f.titre(), rpr(p, 0))));
                } else if (t.startsWith("NATURE ET INTITULE")) {
                    ecrire(p, List.of(new Morceau("NATURE ET INTITULE DU DOSSIER : ", rpr(p, 0)),
                            new Morceau(f.intitule(), rpr(p, p.getRuns().size() - 1))));
                } else if (t.contains("instituée")) {
                    for (XWPFRun r : p.getRuns()) {
                        String x = r.getText(0);
                        if (x != null) {
                            r.setText(x.replace("<DATE EXAMEN>", "{{PV.DATE_EXAMEN|lettres}}")
                                    .replaceAll("Commission (Centrale|Régionale) des Marchés", "Commission " + commission + " des Marchés"), 0);
                        }
                    }
                } else if (t.startsWith("La Commission") && t.contains("émet un")) {
                    avis(doc, p, f, commission, corps.get(i + 1), deplacements);
                } else if ("ANNEXE".equals(t)) {
                    XWPFRun r = p.getRuns().get(0);
                    r.setText("{{SI:AVEC_RESERVES}}" + r.getText(0), 0);
                    // Le saut de page part avec l'annexe : la base la séparait par un saut de section, qui reste en place
                    // quand l'annexe disparaît et laissait une page blanche (les deux sections ne diffèrent que par un pied de
                    // page équivalent, le numéro de page).
                    p.setPageBreak(true);
                }
                if (p.getCTP().getPPr() != null && p.getCTP().getPPr().isSetSectPr()) {
                    p.getCTP().getPPr().unsetSectPr();
                }
            }
            if (deux && titre != null) {
                // Sous la ligne de la commission régionale : sa localité, puis les pointillés (comme le PV régional).
                XWPFParagraph pointilles = corps.get(corps.indexOf(titre) - 1);
                for (String texte : List.of("{{SI:REGIONALE}}{{COMMISSION.LOCALITE}}", pointilles.getText() + "{{FINSI:REGIONALE}}")) {
                    XWPFParagraph n = copie(doc, pointilles, texte, rpr(pointilles, 0));
                    XWPFParagraph avant = titre;
                    deplacements.add(() -> deplacer(n.getCTP(), avant.getCTP()));
                }
            }
            // Les anciens chevrons restants deviennent la variable de leur alias (§B2.8) ; le point de contrôle ouvre la ligne
            // répétée de l'annexe (§B2.6).
            for (XWPFParagraph p : tous(doc)) {
                ModelePvWord.remplacer(p, brut -> {
                    String code = CatalogueModelesPv.ALIAS.get(brut);
                    return code == null ? null : ("OBS.POINT".equals(code) ? "{{POUR:OBSERVATIONS}}" : "") + "{{" + code + "}}";
                });
            }
            deplacements.forEach(Runnable::run);
            doc.write(out);
            docx = out.toByteArray();
        }
        List<ModelePvDto.Condition> conditions = new ArrayList<>(List.of(
                new ModelePvDto.Condition("FAVORABLE", "avis = FAV", "Avis favorable"),
                new ModelePvDto.Condition("AVEC_RESERVES", "avis = FAVR", "Avis favorable avec réserves"),
                new ModelePvDto.Condition("DEFAVORABLE", "avis = DEF", "Avis défavorable"),
                new ModelePvDto.Condition("OBS_GENERALE", "saisi.observation_generale renseigne", "Une observation générale est saisie")));
        if (deux) {
            conditions.add(new ModelePvDto.Condition("CENTRALE", "portee = centrale", "Commission centrale"));
            conditions.add(new ModelePvDto.Condition("REGIONALE", "portee = regionale", "Commission régionale"));
        }
        List<ModelePvDto.Champ> champs = List.of(
                new ModelePvDto.Champ("motif_defavorable", "Motif de l'avis défavorable", "TEXTE_LONG", List.of(), true,
                        "Obligatoire quand l'avis est défavorable.", "DEFAVORABLE"),
                new ModelePvDto.Champ("observation_generale", "Observation générale de la Commission", "TEXTE_LONG", List.of(), false,
                        "Facultative : un constat d'ensemble sur le dossier.", null));
        return new Depart(docx, bases, conditions, champs);
    }

    /**
     * L'avis en trois blocs, « AVIS FAVORABLE » gardant le gras de la base ; puis l'observation générale et, pour le dossier de
     * marché, les titulaires proposés et l'écart à l'estimation, insérés avant {@code suivant}.
     */
    private static void avis(XWPFDocument doc, XWPFParagraph p, Famille f, String commission, XWPFParagraph suivant,
            List<Runnable> deplacements) {
        int iGras = 0;
        while (iGras < p.getRuns().size() && !String.valueOf(p.getRuns().get(iGras).getText(0)).contains("AVIS")) {
            iGras++;
        }
        CTRPr simple = rpr(p, 0);
        CTRPr gras = rpr(p, Math.min(iGras, p.getRuns().size() - 1));
        CTRPr suite = rpr(p, Math.min(iGras + 1, p.getRuns().size() - 1));
        String cible = f.cible();
        ecrire(p, List.of(new Morceau("La Commission " + commission + " des Marchés émet un ", simple),
                new Morceau("{{SI:FAVORABLE}}AVIS FAVORABLE", gras), new Morceau(" " + cible + ".{{FINSI:FAVORABLE}}", suite),
                new Morceau("{{SI:AVEC_RESERVES}}AVIS FAVORABLE", gras),
                new Morceau(" " + cible + ", sous réserve qu’il soit tenu compte des observations portées en annexe.{{FINSI:AVEC_RESERVES}}",
                        suite),
                new Morceau("{{SI:DEFAVORABLE}}AVIS NON FAVORABLE", gras),
                new Morceau(" " + cible + ", pour le motif suivant : {{SAISI:motif_defavorable}}.{{FINSI:DEFAVORABLE}}", suite)));
        List<XmlObject> inseres = new ArrayList<>();
        inseres.add(copie(doc, p, "{{SI:OBS_GENERALE}}Observations générales de la Commission : {{SAISI:observation_generale}}"
                + "{{FINSI:OBS_GENERALE}}", simple).getCTP());
        if (f.titulaires()) {
            inseres.add(copie(doc, p, "Titulaire(s) proposé(s) :", simple).getCTP());
            inseres.add(titulaires(doc, p, simple, gras).getCTTbl());
            inseres.add(copie(doc, p, "Écart à l’estimation : {{MARCHE.ECART}}", simple).getCTP());
        }
        for (XmlObject o : inseres) {
            deplacements.add(() -> deplacer(o, suivant.getCTP()));
        }
    }

    /**
     * Le tableau des titulaires proposés : un en-tête (en gras), une ligne répétée ; aligné sur le paragraphe d'avis (même retrait,
     * même largeur, même police).
     */
    private static XWPFTable titulaires(XWPFDocument doc, XWPFParagraph avis, CTRPr ligne, CTRPr entete) {
        XWPFTable t = doc.createTable(2, 4);
        int gauche = Math.max(0, avis.getIndentationLeft());
        int droite = Math.max(0, avis.getIndentationRight());
        CTSectPr sect = doc.getDocument().getBody().getSectPr();
        int page = 11910;
        int marges = 425;
        if (sect != null && sect.getPgSz() != null && sect.getPgSz().getW() != null && sect.getPgMar() != null) {
            page = ((java.math.BigInteger) sect.getPgSz().getW()).intValue();
            marges = ((java.math.BigInteger) sect.getPgMar().getLeft()).intValue() + ((java.math.BigInteger) sect.getPgMar().getRight()).intValue();
        }
        t.setWidth(String.valueOf(Math.max(4000, page - marges - gauche - droite)));
        CTTblPr tblPr = t.getCTTbl().getTblPr() == null ? t.getCTTbl().addNewTblPr() : t.getCTTbl().getTblPr();
        CTTblWidth ind = tblPr.isSetTblInd() ? tblPr.getTblInd() : tblPr.addNewTblInd();
        ind.setW(java.math.BigInteger.valueOf(gauche));
        ind.setType(STTblWidth.DXA);
        XWPFBorderType b = XWPFBorderType.SINGLE;
        t.setTopBorder(b, 4, 0, "000000");
        t.setBottomBorder(b, 4, 0, "000000");
        t.setLeftBorder(b, 4, 0, "000000");
        t.setRightBorder(b, 4, 0, "000000");
        t.setInsideHBorder(b, 4, 0, "000000");
        t.setInsideVBorder(b, 4, 0, "000000");
        String[] titres = { "RANG", "CANDIDAT PROPOSÉ", "MONTANT HT (Ar)", "DÉLAI D’EXÉCUTION" };
        String[] valeurs = { "{{POUR:TITULAIRES}}{{TIT.RANG}}", "{{TIT.CANDIDAT}}", "{{TIT.MONTANT_HT}}", "{{TIT.DELAI}}" };
        for (int c = 0; c < 4; c++) {
            cellule(t.getRow(0).getCell(c), titres[c], entete);
            cellule(t.getRow(1).getCell(c), valeurs[c], ligne);
        }
        return t;
    }

    private static void cellule(XWPFTableCell c, String texte, CTRPr rpr) {
        XWPFParagraph p = c.getParagraphs().get(0);
        ecrire(p, List.of(new Morceau(texte, rpr)));
    }

    /**
     * {@code {{FINSI:AVEC_RESERVES}}} dans le dernier paragraphe du corps : l'annexe et les paragraphes vides qui la suivent
     * disparaissent ensemble quand l'avis n'est pas « avec réserves » (pas de page blanche à leur place).
     */
    private static void finAnnexe(XWPFDocument doc) {
        List<IBodyElement> elements = doc.getBodyElements();
        XWPFParagraph dernier = elements.get(elements.size() - 1) instanceof XWPFParagraph p ? p : doc.createParagraph();
        dernier.insertNewRun(0).setText("{{FINSI:AVEC_RESERVES}}");
    }

    // ───────────────────────────────────────────── outils ─────────────────────────────────────────────

    private record Morceau(String texte, CTRPr rpr) {
    }

    /** Réécrit un paragraphe : ses morceaux retirés, puis un morceau par texte, chacun avec la mise en forme donnée. */
    private static void ecrire(XWPFParagraph p, List<Morceau> morceaux) {
        for (int i = p.getRuns().size() - 1; i >= 0; i--) {
            p.removeRun(i);
        }
        for (Morceau m : morceaux) {
            XWPFRun r = p.createRun();
            if (m.rpr() != null) {
                r.getCTR().setRPr(m.rpr());
            }
            r.setText(m.texte());
        }
    }

    /** Une copie détachée de la mise en forme du morceau {@code i} (nulle s'il n'en a pas). */
    private static CTRPr rpr(XWPFParagraph p, int i) {
        if (i < 0 || i >= p.getRuns().size() || p.getRuns().get(i).getCTR().getRPr() == null) {
            return null;
        }
        return (CTRPr) p.getRuns().get(i).getCTR().getRPr().copy();
    }

    /** Un paragraphe neuf, en fin de corps, avec la mise en forme de {@code modele} ; {@link #deplacer} le met en place. */
    private static XWPFParagraph copie(XWPFDocument doc, XWPFParagraph modele, String texte, CTRPr rpr) {
        XWPFParagraph n = doc.createParagraph();
        if (modele.getCTP().getPPr() != null) {
            CTPPr ppr = (CTPPr) modele.getCTP().getPPr().copy();
            if (ppr.isSetSectPr()) {
                ppr.unsetSectPr();
            }
            n.getCTP().setPPr(ppr);
        }
        ecrire(n, List.of(new Morceau(texte, rpr)));
        return n;
    }

    /** Déplace un élément du corps juste avant {@code avant} (en dernier : les objets POI ne suivent pas le déplacement). */
    private static void deplacer(XmlObject element, XmlObject avant) {
        try (XmlCursor source = element.newCursor(); XmlCursor cible = avant.newCursor()) {
            source.moveXml(cible);
        }
    }

    /** Les paragraphes du corps et des cellules (un niveau de tableau suffit aux bases). */
    private static List<XWPFParagraph> tous(XWPFDocument doc) {
        List<XWPFParagraph> out = new ArrayList<>(doc.getParagraphs());
        for (XWPFTable t : doc.getTables()) {
            t.getRows().forEach(l -> l.getTableCells().forEach(c -> out.addAll(c.getParagraphs())));
        }
        return out;
    }
}
