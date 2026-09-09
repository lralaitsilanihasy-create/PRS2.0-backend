package cnm.prs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠️ <strong>Contenu des 12 modèles PV</strong> — test de ressource, sans Spring ni Word.
 *
 * <p>Il garde ce que la <strong>dérivation</strong> a produit. Les modèles sont des binaires : une
 * dérivation ratée ne casse aucune compilation et ne se voit qu'à l'ouverture du document final, chez
 * l'utilisateur. Or les 24 tests qui pilotent réellement Word sont <strong>exclus de la CI</strong>
 * (tag {@code word}, pas de Word sur les runners) : sans ce test-ci, aucune vérification automatique
 * ne couvrirait le contenu des modèles en intégration continue.</p>
 *
 * <p>Il vérifie la dérivation du <strong>2026-09-02</strong> (retrait de la ligne « Secrétaire de
 * séance ») <em>et</em> la non-régression de celle du <strong>2026-09-01</strong> (ligne du VISEUR),
 * ainsi que la survie des trois autres noms du bloc « Étaient présents » — retirer une ligne dans un
 * binaire est précisément le geste qui peut en emporter une voisine.</p>
 */
class ModelesPvTest {

    /** Les 12 modèles PV : trois axes (avis, type de plan, ressort). */
    private static final List<String> MODELES = List.of(
            "PV_AFSR_PPMAGPM_CENTRALE", "PV_AFSR_PPMAGPM_REGIONALE",
            "PV_AFSR_PPM_CENTRALE", "PV_AFSR_PPM_REGIONALE",
            "PV_AF_PPMAGPM_CENTRALE", "PV_AF_PPMAGPM_REGIONALE",
            "PV_AF_PPM_CENTRALE", "PV_AF_PPM_REGIONALE",
            "PV_ANF_PPMAGPM_CENTRALE", "PV_ANF_PPMAGPM_REGIONALE",
            "PV_ANF_PPM_CENTRALE", "PV_ANF_PPM_REGIONALE");

    private static final String SECRETAIRE_RETIRE = "<NOM ET PRENOMS DU VERIFICATEUR>";

    @Test
    @DisplayName("Les 12 modèles ne portent plus la ligne « Secrétaire de séance » (dérivation 2026-09-02)")
    void secretaireDeSeanceRetireDesModeles() throws Exception {
        for (String modele : MODELES) {
            String corps = corps(modele);
            assertEquals(0, occurrences(corps, SECRETAIRE_RETIRE),
                    modele + " : le marqueur du Secrétaire de séance devrait avoir disparu");
            assertTrue(!corps.contains("Secrétaire de séance"),
                    modele + " : l'intitulé « Secrétaire de séance » subsiste sans son marqueur — "
                            + "une ligne orpheline s'imprimerait sur un document officiel");
        }
    }

    @Test
    @DisplayName("Les trois autres noms de « Étaient présents » ont survécu à la dérivation")
    void autresPresentsIntacts() throws Exception {
        for (String modele : MODELES) {
            String corps = corps(modele);
            for (String marqueur : List.of("<NOM ET PRENOMS DU PRESIDENT>",
                    "<NOM ET PRENOMS DU CHEF DE COMMISSION>", "<NOM ET PRENOMS DU MEMBRE>")) {
                assertEquals(1, occurrences(corps, marqueur),
                        modele + " : " + marqueur + " doit figurer exactement une fois");
            }
        }
    }

    @Test
    @DisplayName("La ligne du VISEUR (dérivation 2026-09-01) est toujours dans la table VISA")
    void ligneViseurIntacte() throws Exception {
        for (String modele : MODELES) {
            try (XWPFDocument doc = ouvrir(modele)) {
                assertTrue(porteLeViseur(doc),
                        modele + " : la ligne du VISEUR a disparu de la table VISA");
            }
        }
    }

    /**
     * ⚠️ <strong>Bloc VISA — chaque nom sous sa propre légende</strong> (correction du 2026-09-09).
     *
     * <p>La dérivation du 2026-09-01 avait ajouté le VISEUR dans la cellule de <em>droite</em>, celle
     * dont la légende annonce « le membre en charge du dossier » : le PV imprimait donc « Visé par …
     * Président » sous le nom attendu du Membre, et la colonne de gauche restait vide. Viseur et membre
     * sont deux personnes distinctes ; leurs noms doivent tomber sous la bonne légende.</p>
     *
     * <p>Le test lit les <strong>colonnes</strong>, pas le texte global : une table dont les deux noms
     * seraient présents mais intervertis passerait n'importe quelle vérification par « contient ».</p>
     */
    @Test
    @DisplayName("⚠️ Bloc VISA — le VISEUR est dans la colonne du supérieur hiérarchique (gauche) et le "
            + "MEMBRE dans celle du membre en charge du dossier (droite), sur les 12 modèles")
    void blocVisa_viseurAGauche_membreADroite() throws Exception {
        for (String modele : MODELES) {
            try (XWPFDocument doc = ouvrir(modele)) {
                XWPFTable visa = tableVisa(doc);
                assertTrue(visa != null, modele + " : table VISA introuvable");

                // La ligne des légendes situe les colonnes : gauche = supérieur, droite = membre.
                XWPFTableRow legendes = ligneContenant(visa, "SUPERIEUR");
                assertTrue(legendes != null, modele + " : ligne des légendes du bloc VISA introuvable");
                assertEquals(2, legendes.getTableCells().size(), modele + " : le bloc VISA a deux colonnes");
                assertTrue(legendes.getCell(0).getText().contains("SUPERIEUR"),
                        modele + " : la légende du supérieur hiérarchique doit être à GAUCHE");
                assertTrue(legendes.getCell(1).getText().contains("membre en charge du dossier"),
                        modele + " : la légende du membre en charge doit être à DROITE");

                // Et les noms tombent chacun sous la sienne.
                XWPFTableRow noms = ligneContenant(visa, "<VISEUR>");
                assertTrue(noms != null, modele + " : ligne des noms du bloc VISA introuvable");
                assertEquals(2, noms.getTableCells().size(), modele + " : la ligne des noms a deux colonnes");
                assertTrue(noms.getCell(0).getText().contains("<VISEUR>"),
                        modele + " : le VISEUR doit être à GAUCHE, sous « visa du supérieur hiérarchique »");
                assertTrue(noms.getCell(1).getText().contains("<NOM ET PRENOMS DU MEMBRE>"),
                        modele + " : le MEMBRE doit être à DROITE, sous « membre en charge du dossier »");
                assertTrue(!noms.getCell(1).getText().contains("<VISEUR>"),
                        modele + " : le VISEUR ne doit plus figurer dans la colonne du membre");
            }
        }
    }

    // ------------------------------------------------------------------ utilitaires

    private static XWPFDocument ouvrir(String modele) throws Exception {
        InputStream flux = ModelesPvTest.class.getResourceAsStream("/templates/" + modele + ".docx");
        if (flux == null) {
            throw new IllegalStateException("Modèle introuvable au classpath : " + modele);
        }
        return new XWPFDocument(flux);
    }

    /** Texte des paragraphes de CORPS, concaténé run par run (un marqueur peut être scindé entre runs). */
    private static String corps(String modele) throws Exception {
        StringBuilder b = new StringBuilder();
        try (XWPFDocument doc = ouvrir(modele)) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                for (XWPFRun r : p.getRuns()) {
                    if (r.text() != null) {
                        b.append(r.text());
                    }
                }
                b.append('\n');
            }
        }
        return b.toString();
    }

    /**
     * La table VISA se cherche par son <strong>contenu</strong>, jamais par son index : les 4 modèles
     * AFSR portent deux tables (VISA + ANNEXE), les 8 autres une seule.
     */
    private static boolean porteLeViseur(XWPFDocument doc) {
        for (XWPFTable t : doc.getTables()) {
            for (XWPFTableRow r : t.getRows()) {
                for (XWPFTableCell c : r.getTableCells()) {
                    if (c.getText().contains("<VISEUR>")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** La table VISA, cherchée par sa légende — jamais par son index (les AFSR en portent deux). */
    private static XWPFTable tableVisa(XWPFDocument doc) {
        for (XWPFTable t : doc.getTables()) {
            if (t.getText() != null && t.getText().contains("SUPERIEUR")) {
                return t;
            }
        }
        return null;
    }

    /** Première ligne de la table dont une cellule porte le motif. */
    private static XWPFTableRow ligneContenant(XWPFTable table, String motif) {
        for (XWPFTableRow r : table.getRows()) {
            for (XWPFTableCell c : r.getTableCells()) {
                if (c.getText() != null && c.getText().contains(motif)) {
                    return r;
                }
            }
        }
        return null;
    }

    private static int occurrences(String texte, String motif) {
        int compte = 0;
        int index = texte.indexOf(motif);
        while (index >= 0) {
            compte++;
            index = texte.indexOf(motif, index + motif.length());
        }
        return compte;
    }
}
