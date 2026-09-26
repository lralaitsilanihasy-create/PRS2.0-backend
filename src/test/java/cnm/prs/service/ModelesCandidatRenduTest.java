package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠️ V47 (formulaires du candidat, R12 (c)) — les six modèles officiels, rendus <strong>bruts</strong> (jetons non
 * substitués) par le moteur : le texte du docx, relu comme le fait le comparateur du front ({@code LireDocx} : corps,
 * paragraphes et tableaux dans l'ordre, cellules jointes), est celui du fichier de commande, ligne à ligne. Les docx sont
 * aussi écrits dans {@code target/modeles-candidat/} pour {@code node scripts/modeles-candidat/verifier.mjs <sigle>
 * --docx=…}, qui les juge contre les pages du dossier 2463.
 */
class ModelesCandidatRenduTest {

    @Test
    @DisplayName("Les six fichiers de commande se lisent, se rendent en docx et pdf, et le docx relu dit exactement le texte du modèle")
    void renduBrutFidele() throws Exception {
        ModelesCandidat modeles = new ModelesCandidat();
        GenerateurDocumentsFiche generateur = new GenerateurDocumentsFiche();
        Path dossier = Path.of("target", "modeles-candidat");
        Files.createDirectories(dossier);
        assertThat(modeles.modeles()).containsKeys("A1", "A2", "A3", "A4", "C1", "C2");
        for (Map.Entry<String, List<DocumentLibre.Element>> m : modeles.modeles().entrySet()) {
            DocumentLibre brut = FormulairesCandidat.brut(m.getKey(), m.getValue());
            List<GenerateurDocumentsFiche.Fichier> fichiers = generateur.generer(brut);
            assertThat(fichiers).extracting(GenerateurDocumentsFiche.Fichier::extension).containsExactly("docx", "pdf");
            for (GenerateurDocumentsFiche.Fichier f : fichiers) {
                Files.write(dossier.resolve(m.getKey() + "." + f.extension()), f.contenu());
            }
            assertThat(lireCommeLeComparateur(fichiers.get(0).contenu())).as(m.getKey()).isEqualTo(brut.texte());
        }
    }

    @Test
    @DisplayName("Jetons et marqueurs : groupement OUI garde A1-b et retire la mention ; NON l'omet et écrit « (non applicable) » ; "
            + "A3-b porte les natures de la catégorie ; un jeton vide s'imprime en pointillés ; le lot commande le montant")
    void jetonsEtMarqueurs() {
        Map<String, List<DocumentLibre.Element>> modeles = new ModelesCandidat().modeles();
        Map<String, cnm.prs.entity.ChampFicheMarche> champs = new java.util.HashMap<>();
        champs.put("B05-GS-03", champ("B05-GS-03", "MONTANT", true));
        champs.put("B03-CQ-09", champ("B03-CQ-09", "NOMBRE", false));
        champs.put("B04-LR-03", champ("B04-LR-03", "DATE", false));

        cnm.prs.dto.FicheMarcheDto fiche = new cnm.prs.dto.FicheMarcheDto();
        fiche.setIdDetail(1);
        fiche.setVersion(1);
        fiche.setDesignationMarche("Marché de test");
        fiche.setCategorie("TRAVAUX");
        fiche.setNbLots(2);
        fiche.setSaisieParLot(true);
        fiche.setCadrage(new java.util.HashMap<>(Map.of("groupement", "OUI")));
        fiche.setValeurs(new java.util.HashMap<>(Map.of("B04-CD-01", "A1,A3", "B04-CD-02", "C1",
                "B05-GS-03#1", "1600000", "B05-GS-03#2", "2170000", "B03-CQ-09", "5", "B04-LR-03", "2026-03-02")));
        fiche.setValeursPpm(Map.of("B02-OB-01", "Réhabilitation d'une route"));

        List<DocumentLibre> docs = FormulairesCandidat.generer(fiche, champs, modeles, null);
        assertThat(docs).extracting(DocumentLibre::type).containsExactly("A1", "A3", "C1", "C1");
        assertThat(docs).extracting(DocumentLibre::lot).containsExactly(null, null, 1, 2);
        String a1 = docs.get(0).texte();
        assertThat(a1).contains("Nature du groupement", "au cours des cinq dernières années", "pendant la période de 5 ans",
                "N° D'appel d'offre et titre: ……… — Réhabilitation d'une route")   // B02-OB-03 absent → pointillés (R2)
                .doesNotContain("(non applicable)", "{{");
        String a3 = docs.get(1).texte();
        assertThat(a3).contains("Travaux\t\t\t").doesNotContain("{{");
        assertThat(a3.lines().filter(l -> l.startsWith("Fournitures")).count()).isEqualTo(1);   // le premier tableau seul
        assertThat(docs.get(3).texte()).contains("2 170 000 Ariary (deux millions cent soixante-dix mille ariary)")
                .contains("jusqu’au ……… jour suivant")   // B05-GS-04 et B04-VO-01 absents → pointillés
                .doesNotContain("1 600 000");

        fiche.getCadrage().put("groupement", "NON");
        String sans = FormulairesCandidat.generer(fiche, champs, modeles, null).get(0).texte();
        assertThat(sans).contains("(non applicable)").doesNotContain("Nature du groupement", "{{SI", "{{FINSI");
    }

    private static cnm.prs.entity.ChampFicheMarche champ(String code, String type, boolean parLot) {
        cnm.prs.entity.ChampFicheMarche c = new cnm.prs.entity.ChampFicheMarche();
        c.setCode(code);
        c.setType(type);
        c.setParLot(parLot);
        c.setActif(true);
        return c;
    }

    /** La lecture de {@code LireDocx.java} (dépôt front) : corps dans l'ordre, cellules jointes par une tabulation. */
    private static String lireCommeLeComparateur(byte[] docx) throws Exception {
        List<String> lignes = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            for (IBodyElement e : doc.getBodyElements()) {
                if (e instanceof XWPFParagraph p) {
                    lignes.add(p.getText());
                } else if (e instanceof XWPFTable t) {
                    for (XWPFTableRow r : t.getRows()) {
                        List<String> cellules = new ArrayList<>();
                        for (XWPFTableCell c : r.getTableCells()) {
                            cellules.add(String.join(" ", c.getParagraphs().stream().map(XWPFParagraph::getText).toList()));
                        }
                        lignes.add(String.join("\t", cellules));
                    }
                }
            }
        }
        return String.join("\n", lignes);
    }
}
