package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.service.ChampFicheMarcheService;

/**
 * ⚠️ <strong>Fiche DAO des travaux et réhabilitation</strong> (demande front du 2026-09-24, référentiel converti) — le
 * bloc B11 et les 96 rubriques de V41, l'import des deux fichiers de correspondance (140 + 117 champs, catégorie
 * TRAVAUX), la catégorie ouverte : une fiche de travaux se prépare, s'ouvre par la question des tranches et produit
 * DPAO, CCAP et AE (DPAC et AE en contrat-cadre). Les fiches de fournitures n'en sont pas changées.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), lignes en appel d'offres ouvert : 9901 travaux à quantité
 * fixe, 9902 travaux en contrat-cadre (nature 91 « Travaux »), 9903 fournitures à quantité fixe (nature 92).</p>
 */
class FicheDaoTravauxIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheService champService;

    private ChampFicheMarcheService.BilanImport travaux;
    private ChampFicheMarcheService.BilanImport travauxCc;

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
        natureRepository.save(new Nature(91, "Travaux", null, "TRAVAUX"));
        ligne(9901, 91, FormeMarche.QUANTITE_FIXE);
        ligne(9902, 91, FormeMarche.CONTRAT_CADRE);
        ligne(9903, natureFournitures(), FormeMarche.QUANTITE_FIXE);

        importer("referentiel-champs-fiche-marche-fournitures.csv");
        travaux = importer("referentiel-champs-fiche-dao-travaux.csv");
        travauxCc = importer("referentiel-champs-fiche-dao-travaux-contrat-cadre.csv");
    }

    @Test
    @DisplayName("1 — Import : 146 et 117 champs de travaux, aucun rejet ; B11 « Annexes et formulaires » servi aux travaux "
            + "seulement ; aucune rubrique des travaux dans une fiche de fournitures, ni l'inverse")
    void chargement() throws Exception {
        assertThat(travaux.rejets()).isEmpty();
        assertThat(travaux.crees()).hasSize(157);   // 02/10 (recette du MEN, §B3.2) : + B05-GQ-04, B05-VR-02, B08-RE-04, B08-MR-05/06, B09-PE-03   // lot D4 : + 6 (B02-MW-04, B02-LT-06/07, B04-VL-02, B05-GE-05, B09-BT-01) ;
        // 01/10 (DAO du MEN) : + B03-QT-12/13/14, + B02-AU-07 et B06-EO-07 venus du fichier des fournitures
        assertThat(travauxCc.rejets()).isEmpty();
        assertThat(travauxCc.crees()).hasSize(117);

        String qf = ref("typeMarche=QUANTITE_FIXE&categorie=TRAVAUX");
        assertThat(JsonPath.<List<String>>read(qf, "$.blocs[?(@.code=='B11')].libelle")).containsExactly("Annexes et formulaires");
        assertThat(JsonPath.<List<String>>read(qf, "$.blocs[?(@.code=='B11')].rubriques[*].code"))
                .isEmpty();   // lot D4 : B11-FR-01..06 retirés (annexes du CCAP-T) ; 30/09 : B11-AN-01..05 retirés (§B2.2.6)
        List<String> rubriquesQf = JsonPath.read(qf, "$.blocs[*].rubriques[*].code");
        assertThat(rubriquesQf).contains("B02-LT", "B09-RP", "B01-AC", "B04-VO", "B02-AU", "B06-EO")   // V55 : B04-VO ; V58 : B02-AU, B06-EO
                .doesNotContain("B04-RO", "B02-DK", "B04-DV");
        assertThat(JsonPath.<List<List<String>>>read(qf, "$.champs[?(@.source=='SAISIE')].categories")).allMatch(c -> c.contains("TRAVAUX"));
        // 2026-09-25 (§B3) — la composition du dossier est réemployée par les fournitures, les plans restent aux travaux.
        // + V47 : les champs des formulaires du candidat, trois catégories ; + V50 : les seize saisis de la remise électronique.
        List<String> partages = new java.util.ArrayList<>(List.of("B04-CD-01", "B04-CD-02", "B02-OB-03", "B03-CQ-01", "B03-CQ-09", "B03-CQ-10"));
        java.util.stream.IntStream.rangeClosed(2, 17).forEach(i -> partages.add(String.format("B04-SE-%02d", i)));
        partages.add("B04-VO-01");   // ⚠️ lot D4 (2026-09-30, V55) : le délai de validité des offres, commun aux deux catégories
        assertThat(JsonPath.<List<String>>read(qf, "$.champs[*].code")).doesNotContain("B04-DV-01");
        assertThat(JsonPath.<List<String>>read(ref("typeMarche=CONTRAT_CADRE&categorie=TRAVAUX"), "$.champs[*].code"))
                .contains("B04-VO-01").doesNotContain("B04-VT-01");
        assertThat(JsonPath.<List<java.util.Map<String, Object>>>read(qf, "$.champs[?(@.source=='SAISIE')]").stream()
                .filter(c -> ((List<?>) c.get("categories")).contains("FOURNITURES_SERVICES")).map(c -> c.get("code")))
                .containsExactlyInAnyOrderElementsOf(partages);

        String cc = ref("typeMarche=CONTRAT_CADRE&categorie=TRAVAUX");
        // ⚠️ Lot D4 (§B3) — les rubriques du contrat-cadre des fournitures servent aussi les travaux (V54) ; 30/09 : les
        // champs propres au contrat-cadre de travaux qu'aucun document n'utilise sont retirés, leurs rubriques ne sont plus servies.
        assertThat(JsonPath.<List<String>>read(cc, "$.blocs[?(@.code=='B07')].rubriques[*].code")).contains("B07-PS", "B07-DE", "B07-PE")
                .doesNotContain("B07-AT", "B07-DT");
        assertThat(JsonPath.<List<String>>read(cc, "$.champs[*].code")).contains("B08-FT-03", "B08-AT-03")
                .doesNotContain("B02-OC-01", "B10-RT-01", "B03-TT-01");
        assertThat(JsonPath.<List<Boolean>>read(cc, "$.champs[?(@.code=='B08-FT-03')].obligatoire")).containsExactly(false);
        String fs = ref("typeMarche=QUANTITE_FIXE&categorie=FOURNITURES_SERVICES");
        assertThat(JsonPath.<List<String>>read(fs, "$.blocs[*].code")).doesNotContain("B11");
        assertThat(JsonPath.<List<String>>read(fs, "$.champs[*].code")).hasSize(157)   // 155 des fournitures (V50, lot D2 : + 7, 29/09 : − 23 non imprimés, − B08-PA-08) + B04-CD-01, -02 (2026-09-25)
                .contains("B04-CD-01", "B04-CD-02").doesNotContain("B04-CD-03");
    }

    @Test
    @DisplayName("2 — Travaux à quantité fixe : outillés, la question des tranches ouvre la tranche ferme et les "
            + "conditionnelles, validation → DPAO, CCAP et AE ; les formulaires à remplir ne bloquent pas")
    void travauxQuantiteFixe() throws Exception {
        String eligibles = mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(eligibles, "$[?(@.idDetail==9901)].categorieOutillee")).containsExactly(true);
        long idDmc = creerDmc(9901);
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorie").value("TRAVAUX"))
                .andExpect(jsonPath("$.typeOutille").value(true));

        cadrage(idDmc, "{\"tranches\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"garantieSoumission\":\"NON\"}");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B02").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B02-LT-03\":\"Tranche ferme : gros œuvre\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valeurs.B02-LT-03").doesNotExist());
        cadrage(idDmc, "{\"tranches\":\"OUI\",\"groupement\":\"NON\",\"avance\":\"NON\",\"garantieSoumission\":\"NON\"}");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B02").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B02-LT-03\":\"Tranche ferme : gros œuvre\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valeurs.B02-LT-03").value("Tranche ferme : gros œuvre"));

        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "TRAVAUX", Map.of("B02-LT-03", "Tranche ferme : gros œuvre"));
        List<String> types = JsonPath.read(documents(idDmc), "$[*].type");
        assertThat(types).containsExactly("DPAO", "DPAO", "CCAP", "CCAP", "AE", "AE", "A1", "A1");   // V46 : fiche A1 exigée (B04-CD-01), gabarit provisoire
    }

    @Test
    @DisplayName("3 — Travaux en contrat-cadre : validation → DPAC et AE seulement, jamais DPAO ni CCAP")
    void travauxContratCadre() throws Exception {
        long idDmc = creerDmc(9902);
        cadrage(idDmc, "{\"attributaires\":\"MONO\",\"groupement\":\"NON\",\"avance\":\"NON\"}");
        remplirObligatoiresEtValider(idDmc, "CONTRAT_CADRE", "TRAVAUX", Map.of());
        List<String> types = JsonPath.read(documents(idDmc), "$[*].type");
        assertThat(types).containsExactly("DPAC", "DPAC", "AE", "AE");
    }

    @Test
    @DisplayName("4 — Fiche de fournitures, référentiel des travaux chargé : la question des tranches reste refusée, et "
            + "l'allotissement se valide par son reflet des fournitures (OUI/NON), pas par celui des travaux")
    void fournituresInchangees() throws Exception {
        long idDmc = creerDmc(9903);
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"tranches\":\"OUI\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("tranches"));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"alloti\":\"PEUT-ETRE\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("alloti"));
    }

    @Test
    @DisplayName("2026-10-01 — DAO de travaux du MEN (§B2) : trois champs créés (période 5 par défaut, personnel clé, liquidité "
            + "par lot), seuil, garantie et délai par lot, formes de garantie à choix multiples, libération à 100 % à la réception "
            + "provisoire, B02-AU-07 et B06-EO-07 ouverts aux travaux seulement, pièces administratives proposées une par ligne ; "
            + "le DPAO validé imprime « cinq (5) » et les pièces sur des lignes distinctes")
    void daoDuMen() throws Exception {
        String ref = ref("typeMarche=QUANTITE_FIXE&categorie=TRAVAUX");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[*].code"))
                .contains("B03-QT-12", "B03-QT-13", "B03-QT-14", "B02-AU-07", "B06-EO-07");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.parLot==true)].code"))
                .contains("B05-GQ-03", "B03-QT-08", "B09-DL-01", "B03-QT-14");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B03-QT-12')].valeurDefaut")).containsExactly("5");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B03-CQ-01')].valeurDefaut").get(0).split("\n"))
                .hasSize(6).startsWith("une photocopie certifiée de la Carte Professionnelle de l'année en cours");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B05-GQ-02' || @.code=='B05-GE-03')].type"))
                .containsOnly("LISTE_MULTIPLE");
        assertThat(JsonPath.<List<List<String>>>read(ref, "$.champs[?(@.code=='B05-GE-04')].options").get(0))
                .contains("Libérée à 100 % à la réception provisoire");
        assertThat(JsonPath.<List<String>>read(ref("typeMarche=QUANTITE_FIXE&categorie=FOURNITURES_SERVICES"), "$.champs[*].code"))
                .doesNotContain("B02-AU-07", "B06-EO-07", "B03-QT-12");

        long idDmc = creerDmc(9901);
        cadrage(idDmc, "{\"tranches\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"garantieSoumission\":\"OUI\"}");
        // Les valeurs par défaut sont recopiées au premier enregistrement de la fiche (V47).
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(fiche, "$.valeurs['B03-QT-12']")).isEqualTo("5");
        assertThat(JsonPath.<String>read(fiche, "$.valeurs['B03-CQ-01']")).contains("\nun certificat de non faillite");
        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "TRAVAUX",
                Map.of("B05-GQ-02", "Garantie bancaire,Chèque de banque", "B05-GQ-03", "5000000", "B04-CD-02", "C1"));
        fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(fiche, "$.valeurs['B05-GQ-02']")).isEqualTo("Garantie bancaire,Chèque de banque");

        int idDocx = JsonPath.<List<Integer>>read(documents(idDmc), "$[?(@.type=='DPAO' && @.extension=='docx')].idDocument").get(0);
        byte[] docx = mvc.perform(get("/api/fiches-marche/documents/" + idDocx + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String texte;
        try (org.apache.poi.xwpf.usermodel.XWPFDocument d = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new java.io.ByteArrayInputStream(docx)); org.apache.poi.xwpf.extractor.XWPFWordExtractor ex =
                        new org.apache.poi.xwpf.extractor.XWPFWordExtractor(d)) {
            texte = ex.getText().replace(' ', ' ').replace(' ', ' ');
        }
        assertThat(texte).contains("au cours des cinq (5) dernières années",
                "une photocopie certifiée de la carte statistique", "cinq millions ariary (5 000 000 Ariary)")
                .contains("de l'Extrait du Registre de Commerce\nun certificat de non faillite");   // un vrai saut de ligne
    }

    @Test
    @DisplayName("2026-10-02 — recette du DAO du MEN (§B2, §B3.2) : chiffre d'affaires, antécédents financiers et date de "
            + "réception facultatifs ; procédure contentieuse proposée par défaut ; libellés précisés ; six champs créés")
    void recetteDuMen() throws Exception {
        String ref = ref("typeMarche=QUANTITE_FIXE&categorie=TRAVAUX");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.obligatoire==true)].code"))
                .doesNotContain("B03-QT-07", "B03-CQ-10", "B09-DL-04");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B10-PC-01')].valeurDefaut").get(0))
                .startsWith("Les différends nés de l'exécution du marché").contains("article 50 du Cahier des Clauses Administratives Générales");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B09-MA-03')].libelle").get(0))
                .endsWith(": pourcentage de la masse initiale (ex. « vingt pour cent (20 %) »)");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B03-NT-01')].libelle"))
                .containsExactly("Comptable assignataire des paiements (désignation seule)");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B02-MW-04')].libelle").get(0)).endsWith("(laisser vide s'il n'y en a pas)");
        java.util.Map<String, String> crees = new java.util.LinkedHashMap<>();
        for (String code : List.of("B05-GQ-04", "B05-VR-02", "B08-RE-04", "B08-MR-05", "B08-MR-06", "B09-PE-03")) {
            crees.put(code, JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='" + code + "')].type").get(0));
        }
        assertThat(crees).containsExactly(java.util.Map.entry("B05-GQ-04", "TEXTE"), java.util.Map.entry("B05-VR-02", "TEXTE_LONG"),
                java.util.Map.entry("B08-RE-04", "POURCENTAGE"), java.util.Map.entry("B08-MR-05", "NOMBRE"),
                java.util.Map.entry("B08-MR-06", "TEXTE_LONG"), java.util.Map.entry("B09-PE-03", "POURCENTAGE"));
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.obligatoire==true)].code")).doesNotContainAnyElementsOf(crees.keySet());

        // ⚠️ 2026-10-02 (demande « gabarits ») — la phrase du modèle qui imprime le champ ; rien pour un paragraphe fait du
        // seul jeton ; pas de clé dans la vue d'administration (sans filtre).
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B09-MA-03')].gabarits[*].avant"))
                .containsExactly("La diminution dans la masse des travaux au delà de ");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B09-MA-03')].gabarits[*].document")).containsExactly("CCAP");
        assertThat(JsonPath.<List<Object>>read(ref, "$.champs[?(@.code=='B10-PC-01')].gabarits[*]")).isEmpty();
        assertThat(JsonPath.<List<Object>>read(mvc.perform(get("/api/champs-fiche-marche").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.champs[?(@.gabarits)]")).isEmpty();
    }

    @Test
    @DisplayName("2026-10-02 — DAO routier du MTP (§B1-§B4) : sans maître d'œuvre ni assurance décennale, la fiche se valide "
            + "et le CCAP imprime SANS-MOE ; un bâtiment exige l'assurance (ASSURANCE_DECENNALE bloquant) ; les garanties de "
            + "soumission des travaux s'intitulent et se nomment B1 / B2 ; B08-MO-01 n'est plus servi aux travaux")
    void travauxRoutiers() throws Exception {
        String ref = ref("typeMarche=QUANTITE_FIXE&categorie=TRAVAUX");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.obligatoire==true)].code")).doesNotContain("B02-MW-01", "B09-AC-03");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[*].code")).doesNotContain("B08-MO-01");

        long idDmc = creerDmc(9901);
        cadrage(idDmc, "{\"tranches\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"garantieSoumission\":\"OUI\"}");
        // Un bâtiment sans assurance décennale : bloquant.
        remplirObligatoires(idDmc, "QUANTITE_FIXE", "TRAVAUX", Map.of("B09-BT-01", "OUI", "B05-GQ-02", "Garantie bancaire",
                "B05-GQ-03", "5000000", "B04-CD-02", "C1 et C2"));
        String refus = mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(refus, "$.message")).contains("assurance de responsabilité civile décennale");
        // Une route : ni bâtiment, ni maître d'œuvre, ni assurance — la fiche se valide.
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B09").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":" + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                        valeursDuBloc(idDmc, "B09", Map.of("B09-BT-01", "NON"))) + "}")).andExpect(status().isOk());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Map<String, String>>read(fiche, "$.valeurs")).doesNotContainKeys("B02-MW-01", "B09-AC-03");

        String docs = documents(idDmc);
        int idCcap = JsonPath.<List<Integer>>read(docs, "$[?(@.type=='CCAP' && @.extension=='docx')].idDocument").get(0);
        byte[] ccap = mvc.perform(get("/api/fiches-marche/documents/" + idCcap + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (org.apache.poi.xwpf.usermodel.XWPFDocument d = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new java.io.ByteArrayInputStream(ccap)); org.apache.poi.xwpf.extractor.XWPFWordExtractor ex =
                        new org.apache.poi.xwpf.extractor.XWPFWordExtractor(d)) {
            // §B5.1 : la rédaction SANS-MOE n'a plus de trou.
            assertThat(ex.getText()).contains("Le maître d’œuvre sera désigné par une décision du Maître de l’ouvrage ou de la PRMP")
                    .doesNotContain("<préciser l'autorité désignée par la PRMP>");
        }
        assertThat(JsonPath.<List<String>>read(docs, "$[?(@.type=='C1')].libelle")).containsOnly("Garantie bancaire de soumission (B1)");
        assertThat(JsonPath.<List<String>>read(docs, "$[?(@.type=='C1')].nomFichier")).allMatch(n -> n.startsWith("B1_"));
        // §B5.2 : le modèle B1 du dossier type des travaux, avec ses renvois aux IC des travaux.
        int idB1 = JsonPath.<List<Integer>>read(docs, "$[?(@.type=='C1' && @.extension=='docx')].idDocument").get(0);
        byte[] b1 = mvc.perform(get("/api/fiches-marche/documents/" + idB1 + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (org.apache.poi.xwpf.usermodel.XWPFDocument d = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new java.io.ByteArrayInputStream(b1)); org.apache.poi.xwpf.extractor.XWPFWordExtractor ex =
                        new org.apache.poi.xwpf.extractor.XWPFWordExtractor(d)) {
            assertThat(ex.getText()).contains("(travaux)").doesNotContain("(fournitures)");
            // §B5 (jetons des travaux) : montant du lot, validité des offres + 30 jours.
            int validite = Integer.parseInt(JsonPath.<String>read(fiche, "$.valeurs['B04-VO-01']")) + 30;
            assertThat(ex.getText()).contains("cinq millions ariary (5 000 000 Ariary)", "soit jusqu’au " + validite + " ème jour");
        }
        int idB2 = JsonPath.<List<Integer>>read(docs, "$[?(@.type=='C2' && @.extension=='docx')].idDocument").get(0);
        byte[] b2 = mvc.perform(get("/api/fiches-marche/documents/" + idB2 + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (org.apache.poi.xwpf.usermodel.XWPFDocument d = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new java.io.ByteArrayInputStream(b2)); org.apache.poi.xwpf.extractor.XWPFWordExtractor ex =
                        new org.apache.poi.xwpf.extractor.XWPFWordExtractor(d)) {
            assertThat(ex.getText()).startsWith("B 2").contains("(heure locale)", "5 000 000 Ariary");
        }
    }

    /** Les valeurs actuelles d'un bloc, modifiées : un PUT de bloc remplace le bloc entier. */
    private Map<String, String> valeursDuBloc(long idDmc, String bloc, Map<String, String> modifs) throws Exception {
        Map<String, String> toutes = JsonPath.read(mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.valeurs");
        Map<String, String> out = new java.util.TreeMap<>();
        toutes.forEach((k, v) -> { if (k.startsWith(bloc + "-")) { out.put(k, v); } });
        out.putAll(modifs);
        return out;
    }

    // ------------------------------------------------------------------ outils

    private ChampFicheMarcheService.BilanImport importer(String fichier) throws Exception {
        return champService.importerCsv(new ClassPathResource("fiche-marche/" + fichier).getFile().toPath());
    }

    private String ref(String requete) throws Exception {
        return mvc.perform(get("/api/champs-fiche-marche?" + requete).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }

    private void cadrage(long idDmc, String cadrage) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":" + cadrage + "}")).andExpect(status().isOk());
    }

    private String documents(long idDmc) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void ligne(int idDetail, int idNature, FormeMarche forme) {
        Marche l = marche(idDetail, 9900, 9900);
        l.setIdMode(92);
        l.setFormeMarche(forme);
        l.setIdNature(idNature);
        l.setDesignationMarche("Réhabilitation " + idDetail);
        marcheRepository.save(l);
    }
}
