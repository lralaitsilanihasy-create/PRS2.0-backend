package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.service.ChampFicheMarcheService;

/**
 * ⚠️ <strong>Le besoin par lot et les formulaires du candidat</strong> (demande front du 2026-09-25, V45) — §B1 : le
 * besoin en ressource (articles et caractéristiques), remplacé en bloc par lot, et ses gardes ; §B2 : la liste à choix
 * multiples ; §B4 : les contrôles (besoin incomplet, quantités, garantie manquante, taux de garantie administrable) ;
 * §B3 : la liste des fournitures, et par lot le bordereau des prix et le tableau de conformité en classeurs protégés.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV) ; 9902 à commande en trois lots (fournitures), 9901 à commande
 * non allotie (fournitures), 9903 à quantité fixe en prestations intellectuelles.</p>
 */
class FicheBesoinIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheService champService;

    @BeforeEach
    void jeu() throws Exception {
        TypeDmc dao = typeDmcRepository.findByCode("DAO").orElseThrow();
        ModePassation m92 = new ModePassation(92, "Appel d'offres ouvert", null, null, null, null);
        m92.setIdTypeDmc(dao.getIdTypeDmc());
        modePassationRepository.save(m92);
        dossierRepository.save(dossierLoc(9900, "CLOTURE", "ANT", "PRMP001"));
        Dossier plan = dossierRepository.findById(9900).orElseThrow();
        plan.setIdEntiteContract(1);
        dossierRepository.save(plan);
        ppmRepository.save(ppm(9900, 9900, "PRMP001"));
        receptionRepository.save(reception(9900, 9900, "CTRCC1", true));
        dispatchRepository.save(dispatch(9900, 9900, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9900, 9900, "CTRMEM"));
        seedPvSigne(9900, 9900);
        ligne(9901, FormeMarche.A_COMMANDE, natureFournitures());
        ligne(9902, FormeMarche.A_COMMANDE, natureFournitures());
        natureRepository.save(new Nature(94, "Prestations intellectuelles", null, "PRESTATIONS_INTELLECTUELLES"));
        ligne(9903, FormeMarche.QUANTITE_FIXE, 94);
        for (int n = 1; n <= 3; n++) {
            Lot lot = new Lot();
            lot.setIdLot(9910 + n);
            lot.setIdDossier(9900);
            lot.setIdDetail(9902);
            lot.setDesignationLot("Lot " + n);
            lotRepository.save(lot);
        }
        for (String f : List.of("referentiel-champs-fiche-marche-fournitures.csv", "referentiel-champs-fiche-dao-travaux.csv")) {
            assertThat(champService.importerCsv(new ClassPathResource("fiche-marche/" + f).getFile().toPath()).rejets()).isEmpty();
        }
    }

    @Test
    @DisplayName("1 — Besoin d'un lot remplacé en bloc : ordre par position, rédacteur posé, trié par lot ; lot hors plan, lot sur "
            + "une ligne non allotie, quantités d'un marché à commande manquantes → 400 ; prestations intellectuelles → 409 "
            + "BESOIN_HORS_PERIMETRE ; DELETE → 204 puis 404")
    void besoinEtGardes() throws Exception {
        Long idDmc = creerDmc(9902);
        String lot2 = articles(idDmc, 2, article(null, "Kit Core i3", 8, 16, "Mémoire vive", "8 Go au minimum")
                + "," + article(null, "Kit Core i5", 13, 26, "Moniteur", "22 pouces au minimum"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(lot2, "$[*].lot")).containsExactly(2, 2);
        assertThat(JsonPath.<List<Integer>>read(lot2, "$[*].ordre")).containsExactly(1, 2);
        assertThat(JsonPath.<List<String>>read(lot2, "$[*].redigePar")).containsOnly("PRMP001");
        articles(idDmc, 1, article(null, "Ordinateur de bureau", 15, 30, "Processeur", "Core i5")).andExpect(status().isOk());
        String tout = mvc.perform(get("/api/fiches-marche/" + idDmc + "/articles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(tout, "$[*].designation"))
                .containsExactly("Ordinateur de bureau", "Kit Core i3", "Kit Core i5");
        assertThat(JsonPath.<List<String>>read(tout, "$[1].caracteristiques[*].exigence")).containsExactly("8 Go au minimum");

        articles(idDmc, 4, article(null, "X", 1, 2, "a", "b")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("lot"));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/articles").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"articles\":[{\"designation\":\"X\",\"unite\":\"U\",\"quantiteMin\":1}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("articles[0].lot"));
        articles(idDmc, 3, "{\"designation\":\"X\",\"unite\":\"U\",\"quantiteMax\":2}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("articles[0].quantiteMin"));

        Long nonAllotie = creerDmc(9901);
        articles(nonAllotie, 1, article(null, "X", 1, 2, "a", "b")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("lot"));
        Long pi = creerDmc(9903);
        articles(pi, null, article(null, "X", 1, 2, "a", "b")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BESOIN_HORS_PERIMETRE"));

        int idArticle = JsonPath.read(tout, "$[0].idArticle");
        mvc.perform(delete("/api/fiches-marche/" + idDmc + "/articles/" + idArticle).header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/fiches-marche/" + idDmc + "/articles/" + idArticle).header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("2 — Bilan : BESOIN_INCOMPLET par lot vide et par article sans caractéristique, QUANTITES_ORDRE, "
            + "GARANTIE_MANQUANTE sans modèle de garantie ; GARANTIE_TAUX constaté, en avertissement hors des bornes "
            + "administrables ; liste à choix multiples normalisée, option inconnue → 400")
    void controles() throws Exception {
        mvc.perform(put("/api/parametres/fiche-garantie-taux").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"reference\":2,\"borneBasse\":1,\"borneHaute\":3}")).andExpect(status().isOk());
        Long idDmc = creerDmc(9902);
        cadrage(idDmc, "OUI");
        articles(idDmc, 1, article(null, "Ordinateur", 30, 15, "Processeur", "Core i5")).andExpect(status().isOk());
        articles(idDmc, 2, "{\"designation\":\"Kit\",\"unite\":\"U\",\"quantiteMin\":1,\"quantiteMax\":2}").andExpect(status().isOk());
        String fiche = bloc(idDmc, "B05", "{\"B05-GS-03#1\":1600000,\"B05-TP-03#1\":80000000,"
                + "\"B05-GS-03#2\":5000000,\"B05-TP-03#2\":80000000}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[?(@.regle=='BESOIN_INCOMPLET')].message"))
                .containsExactlyInAnyOrder("Le lot 3 n'a aucun article.",
                        "L'article 1 du lot 2 (« Kit ») n'a aucune caractéristique exigée.");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[?(@.regle=='QUANTITES_ORDRE')].message"))
                .containsExactly("L'article 1 du lot 1 (« Ordinateur ») : la quantité minimum (30) dépasse la quantité maximum (15).");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[?(@.regle=='GARANTIE_MANQUANTE')].champs[0]"))
                .containsExactly("B04-CD-02");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.ok[?(@.regle=='GARANTIE_TAUX')].message"))
                .containsExactly("Garantie de soumission du lot 1 : 2 % du montant maximum (référence 2 %).");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.avertissements[?(@.regle=='GARANTIE_TAUX')].message"))
                .containsExactly("Garantie de soumission du lot 2 : 6.25 % du montant maximum (référence 2 %), hors des bornes 1 % à 3 %.");

        String b04 = bloc(idDmc, "B04", "{\"B04-CD-01\":[\"a3\",\"A1\"],\"B04-CD-02\":\"C1 et C2\"}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(b04, "$.valeurs['B04-CD-01']")).isEqualTo("A1,A3");
        assertThat(JsonPath.<List<Object>>read(b04, "$.bilanControles.bloquants[?(@.regle=='GARANTIE_MANQUANTE')]")).isEmpty();
        bloc(idDmc, "B04", "{\"B04-CD-01\":\"A1,A9\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("B04-CD-01"));
    }

    @Test
    @DisplayName("3 — Validation : liste des fournitures (un tableau par lot, lieu et délai du lot), bordereau des prix et "
            + "tableau de conformité par lot en xlsx protégés (prix unitaire seul déverrouillé, montants en formules, TVA) ; "
            + "la révision copie le besoin ; fiche validée → 409")
    void generation() throws Exception {
        Long idDmc = creerDmc(9902);
        cadrage(idDmc, "NON");
        for (int n = 1; n <= 3; n++) {
            articles(idDmc, n, article(null, "Ordinateur lot " + n, 10 * n, 20 * n, "Mémoire vive", "8 Go au minimum") + ","
                    + article(null, "Onduleur lot " + n, 5, 10, "Puissance", "1200 VA")).andExpect(status().isOk());
        }
        Map<String, String> donnees = new LinkedHashMap<>();
        for (int n = 1; n <= 3; n++) {
            donnees.put("B09-LL-01#" + n, "Site " + n);
            donnees.put("B06-EO-12#" + n, "30");
        }
        remplirObligatoiresEtValider(idDmc, "A_COMMANDE", "FOURNITURES_SERVICES", donnees);

        String documents = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(documents, "$[?(@.extension=='xlsx')].type"))
                .containsExactly("BP", "BP", "BP", "TC", "TC", "TC");
        assertThat(JsonPath.<List<String>>read(documents, "$[?(@.type=='BP')].nomFichier"))
                .allMatch(n -> n.matches("BP_.*_9902_lot[123]_v1\\.xlsx"));
        assertThat(JsonPath.<List<String>>read(documents, "$[?(@.type=='TC')].libelle"))
                .contains("Spécifications techniques — tableau de conformité — lot 2");

        String lf = texteDocx(contenu(documents, "LF", "docx", null));
        assertThat(lf).contains("Liste des fournitures et calendrier de livraison", "Lot 1", "Lot 3", "Ordinateur lot 2",
                "Lieu de livraison : Site 2", "Délai maximum de livraison de chaque commande : 30 jours");

        try (XSSFWorkbook bp = new XSSFWorkbook(new ByteArrayInputStream(contenu(documents, "BP", "xlsx", 2)))) {
            XSSFSheet f = bp.getSheetAt(0);
            assertThat(f.getProtect()).isTrue();
            assertThat(f.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Bordereau des prix — lot 2");
            assertThat(f.getRow(5).getCell(1).getStringCellValue()).isEqualTo("Ordinateur lot 2");
            assertThat(f.getRow(5).getCell(3).getNumericCellValue()).isEqualTo(20);
            assertThat(f.getRow(5).getCell(5).getCellStyle().getLocked()).isFalse();   // prix unitaire HT
            assertThat(f.getRow(5).getCell(3).getCellStyle().getLocked()).isTrue();
            assertThat(f.getRow(5).getCell(7).getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(f.getRow(5).getCell(7).getCellFormula()).isEqualTo("E6*F6");
            assertThat(f.getRow(7).getCell(5).getStringCellValue()).isEqualTo("Total HT");
            assertThat(f.getRow(8).getCell(5).getStringCellValue()).isEqualTo("TVA (20 %)");
            assertThat(f.getRow(9).getCell(7).getCellFormula()).isEqualTo("H8+H9");
        }
        try (XSSFWorkbook tc = new XSSFWorkbook(new ByteArrayInputStream(contenu(documents, "TC", "xlsx", 1)))) {
            XSSFSheet f = tc.getSheetAt(0);
            assertThat(f.getProtect()).isTrue();
            assertThat(f.getRow(5).getCell(3).getStringCellValue()).isEqualTo("8 Go au minimum");
            assertThat(f.getRow(5).getCell(4).getCellStyle().getLocked()).isFalse();
            assertThat(f.getDataValidations()).hasSize(1);
        }

        articles(idDmc, 1, article(null, "X", 1, 2, "a", "b")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FICHE_VALIDEE"));
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        String copie = mvc.perform(get("/api/fiches-marche/" + idDmc + "/articles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(copie, "$")).hasSize(6);
        assertThat(JsonPath.<List<Object>>read(copie, "$[*].caracteristiques[*]")).hasSize(6);
    }

    @Test
    @DisplayName("4 — V46 : le bloc déclare son rendu (B12 → BESOIN, les autres null) ; le taux de TVA est exposé en lecture "
            + "et en administration (403 hors Administrateur, 400 hors 0–100)")
    void renduEtTva() throws Exception {
        String ref = mvc.perform(get("/api/champs-fiche-marche").param("typeMarche", "A_COMMANDE")
                .param("categorie", "FOURNITURES_SERVICES").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(ref, "$.blocs[?(@.code=='B12')].rendu")).containsExactly("BESOIN");
        assertThat(JsonPath.<List<Object>>read(ref, "$.blocs[?(@.code=='B05')].rendu")).containsExactly((Object) null);

        mvc.perform(get("/api/parametres/fiche-taux-tva").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taux").value(20));
        mvc.perform(put("/api/parametres/fiche-taux-tva").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"taux\":18}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/parametres/fiche-taux-tva").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"taux\":120}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/parametres/fiche-taux-tva").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"taux\":18}")).andExpect(status().isOk()).andExpect(jsonPath("$.taux").value(18));
    }

    @Test
    @DisplayName("5 — V46 : fiches A1, A2 par lot et garanties C1, C2 par lot sur gabarit provisoire filigrané ; C1 porte le "
            + "montant du lot en chiffres et en lettres et la validité en ordinal ; C2 la fin de validité des offres calculée")
    void formulairesProvisoires() throws Exception {
        Long idDmc = creerDmc(9902);
        cadrage(idDmc, "OUI");
        for (int n = 1; n <= 3; n++) {
            articles(idDmc, n, article(null, "Ordinateur lot " + n, 10, 20, "Mémoire vive", "8 Go")).andExpect(status().isOk());
        }
        Map<String, String> donnees = new LinkedHashMap<>();
        donnees.put("B02-OB-03", "AOO 2461/MT/2026");
        donnees.put("B04-CD-01", "A1,A2");
        donnees.put("B04-CD-02", "C1 et C2");
        donnees.put("B05-GS-04", "105");
        donnees.put("B04-VO-01", "75");
        for (int n = 1; n <= 3; n++) {
            donnees.put("B05-GS-03#" + n, String.valueOf(1600000 * n));
            donnees.put("B05-TP-03#" + n, String.valueOf(80000000 * n));
        }
        remplirObligatoiresEtValider(idDmc, "A_COMMANDE", "FOURNITURES_SERVICES", donnees);

        String documents = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String type : List.of("A1", "A2", "C1", "C2")) {
            assertThat(JsonPath.<List<Integer>>read(documents, "$[?(@.type=='" + type + "' && @.extension=='pdf')].lot"))
                    .as(type).containsExactly(1, 2, 3);
        }
        assertThat(JsonPath.<List<String>>read(documents, "$[?(@.type=='C1')].libelle"))
                .contains("Garantie bancaire de soumission (C1) — lot 2");

        byte[] c1 = contenu(documents, "C1", "docx", 2);
        String texte = texteDocx(c1);
        assertThat(texte).contains("Lot 2 : Lot 2",
                "3 200 000 Ariary (trois millions deux cent mille ariary)",
                "jusqu'au cent cinquième (105ème) jour");
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(c1))) {
            assertThat(doc.getHeaderList()).anyMatch(h -> h._getHdrFtr().xmlText().contains("MODÈLE PROVISOIRE – NON OFFICIEL"));
        }
        String pdf = texteDuPdf(contenu(documents, "C2", "pdf", 1));
        assertThat(pdf.replace(" ", "")).contains("MODÈLEPROVISOIRE–NONOFFICIEL");   // texte en diagonale : PDFBox le découpe
        assertThat(pdf).contains("Page 1 de", "Validité de l'offre expirant le", "appel d'offres AOO 2461/MT/2026");
        assertThat(texteDocx(contenu(documents, "A1", "docx", 3)))
                .contains("A1-b", "Non applicable", "Lot visé : Lot 3");
    }

    // ------------------------------------------------------------------ outils

    private static String article(Integer lot, String designation, int min, int max, String libelle, String exigence) {
        return "{" + (lot == null ? "" : "\"lot\":" + lot + ",") + "\"designation\":\"" + designation + "\",\"unite\":\"U\","
                + "\"quantiteMin\":" + min + ",\"quantiteMax\":" + max + ",\"caracteristiques\":[{\"libelle\":\"" + libelle
                + "\",\"exigence\":\"" + exigence + "\"}]}";
    }

    private ResultActions articles(Long idDmc, Integer lot, String articles) throws Exception {
        return mvc.perform(put("/api/fiches-marche/" + idDmc + "/articles" + (lot == null ? "" : "?lot=" + lot))
                .header("Authorization", tokenPrmp).contentType(JSON).content("{\"articles\":[" + articles + "]}"));
    }

    private ResultActions bloc(Long idDmc, String bloc, String valeurs) throws Exception {
        return mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/" + bloc).header("Authorization", tokenPrmp)
                .contentType(JSON).content("{\"valeurs\":" + valeurs + "}"));
    }

    private void cadrage(Long idDmc, String garantie) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"alloti\":\"OUI\",\"variantes\":\"NON\",\"groupement\":\"NON\","
                        + "\"provenance\":\"NATIONAL\",\"typePrix\":\"UNITAIRES\",\"prixRevisable\":\"NON\","
                        + "\"garantieSoumission\":\"" + garantie + "\",\"avance\":\"NON\",\"penalites\":\"CCAG\"}}"))
                .andExpect(status().isOk());
    }

    private byte[] contenu(String documents, String type, String extension, Integer lot) throws Exception {
        String filtre = "$[?(@.type=='" + type + "' && @.extension=='" + extension + "'"
                + (lot == null ? "" : " && @.lot==" + lot) + ")].idDocument";
        int id = JsonPath.<List<Integer>>read(documents, filtre).get(0);
        return mvc.perform(get("/api/fiches-marche/documents/" + id + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private static String texteDocx(byte[] docx) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }

    private Long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }

    private void ligne(int idDetail, FormeMarche forme, int nature) {
        Marche l = marche(idDetail, 9900, 9900);
        l.setIdMode(92);
        l.setIdNature(nature);
        l.setFormeMarche(forme);
        l.setDesignationMarche("Acquisition de matériels informatiques " + idDetail);
        marcheRepository.save(l);
    }
}
