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
 * fixe, 9902 travaux en contrat-cadre (nature 91 « Travaux »), 9903 fournitures à quantité fixe (nature 92), ⚠️ V59 9904
 * travaux à quantité fixe en deux lots.</p>
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
        // ⚠️ V59 (02/10, DQE des travaux) — travaux à quantité fixe en deux lots (le MEN).
        ligne(9904, 91, FormeMarche.QUANTITE_FIXE);
        for (int n = 1; n <= 2; n++) {
            cnm.prs.entity.Lot lot = new cnm.prs.entity.Lot();
            lot.setIdLot(9940 + n);
            lot.setIdDossier(9900);
            lot.setIdDetail(9904);
            lot.setDesignationLot("Lot " + n);
            lotRepository.save(lot);
        }

        importer("referentiel-champs-fiche-marche-fournitures.csv");
        travaux = importer("referentiel-champs-fiche-dao-travaux.csv");
        travauxCc = importer("referentiel-champs-fiche-dao-travaux-contrat-cadre.csv");
    }

    @Test
    @DisplayName("1 — Import : 146 et 117 champs de travaux, aucun rejet ; B11 « Annexes et formulaires » servi aux travaux "
            + "seulement ; aucune rubrique des travaux dans une fiche de fournitures, ni l'inverse")
    void chargement() throws Exception {
        assertThat(travaux.rejets()).isEmpty();
        assertThat(travaux.crees()).hasSize(163);   // V59 (02/10, seuils calculés) : + B03-QT-15 à 20 ;   02/10 (recette du MEN, §B3.2) : + B05-GQ-04, B05-VR-02, B08-RE-04, B08-MR-05/06, B09-PE-03   // lot D4 : + 6 (B02-MW-04, B02-LT-06/07, B04-VL-02, B05-GE-05, B09-BT-01) ;
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
        assertThat(types).containsExactly("DPAO", "DPAO", "CCAP", "CCAP", "AE", "AE", "A1", "A1", "BP");   // V59 : + le bordereau des prix et DQE (xlsx)   // V46 : fiche A1 exigée (B04-CD-01), gabarit provisoire
    }

    @Test
    @DisplayName("3 — Travaux en contrat-cadre : validation → DPAC et AE seulement, jamais DPAO ni CCAP")
    void travauxContratCadre() throws Exception {
        long idDmc = creerDmc(9902);
        cadrage(idDmc, "{\"attributaires\":\"MONO\",\"groupement\":\"NON\",\"avance\":\"NON\"}");
        remplirObligatoiresEtValider(idDmc, "CONTRAT_CADRE", "TRAVAUX", Map.of());
        List<String> types = JsonPath.read(documents(idDmc), "$[*].type");
        assertThat(types).containsExactly("DPAC", "DPAC", "AE", "AE", "BP");   // V59 : + le bordereau des prix et DQE
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
        for (String code : List.of("B05-GQ-04", "B05-VR-02", "B08-RE-04", "B08-MR-05", "B09-PE-03")) {
            crees.put(code, JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='" + code + "')].type").get(0));
        }
        assertThat(crees).containsExactly(java.util.Map.entry("B05-GQ-04", "TEXTE"), java.util.Map.entry("B05-VR-02", "TEXTE_LONG"),
                java.util.Map.entry("B08-RE-04", "POURCENTAGE"), java.util.Map.entry("B08-MR-05", "NOMBRE"),
                java.util.Map.entry("B09-PE-03", "POURCENTAGE"));
        // ⚠️ V59 (02/10, DQE des travaux, §B1.5) — B08-MR-06 désactivé : le découpage du forfait se dérive des séries du DQE.
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[*].code")).doesNotContain("B08-MR-06");
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

    @Test
    @DisplayName("V59 (02/10, DQE des travaux, §B1) — prix unitaires (MTP) : le besoin s'ouvre aux travaux ; numéro de prix, "
            + "série, quantités à deux décimales, 400 nominatifs ; classeur BP : séries, sous-totaux, récapitulation, prix en "
            + "lettres et HT seuls ouverts, plafond de 001 en formule, liste des sous-détails ; ni LF ni TC ; fournitures inchangées")
    void dqePrixUnitaires() throws Exception {
        String ref = ref("typeMarche=QUANTITE_FIXE&categorie=TRAVAUX");
        assertThat(JsonPath.<List<String>>read(ref, "$.blocs[?(@.code=='B12')].rendu")).containsExactly("BESOIN");
        assertThat(JsonPath.<List<String>>read(ref, "$.blocs[?(@.code=='B12')].rubriques[*].code")).containsExactly("B12-DQ");
        assertThat(JsonPath.<List<String>>read(ref, "$.blocs[?(@.code=='B12')].rubriques[*].libelle"))
                .containsExactly("Détail quantitatif et estimatif, par lot");
        assertThat(JsonPath.<List<String>>read(ref("typeMarche=QUANTITE_FIXE&categorie=FOURNITURES_SERVICES"),
                "$.blocs[?(@.code=='B12')].rubriques[*].code")).containsExactly("B12-BE");

        long idDmc = creerDmc(9901);
        cadrage(idDmc, "{\"tranches\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"garantieSoumission\":\"NON\","
                + "\"typePrix\":\"UNITAIRES\"}");
        // 400 nominatifs : numéro de prix manquant, en double, série à deux intitulés, trois décimales, plafond hors 0-100.
        dqe(idDmc, null, "[" + art("", "000", null, "Installation", "fft", "1", null, false, null) + "]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("articles[0].numeroPrix"));
        dqe(idDmc, null, "[" + art("529", "500", "Ouvrages", "Déblais", "m³", "10", null, false, null) + ","
                + art("529", "500", null, "Remblais", "m³", "10", null, false, null) + "]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("articles[1].numeroPrix"));
        dqe(idDmc, null, "[" + art("529", "500", "Ouvrages", "Déblais", "m³", "10", null, false, null) + ","
                + art("530", "500", "Ouvrages d'art", "Remblais", "m³", "10", null, false, null) + "]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("articles[1].serieLibelle"));
        dqe(idDmc, null, "[" + art("529", "500", null, "Déblais", "m³", "10.125", null, false, null) + "]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("articles[0].quantite"));
        dqe(idDmc, null, "[" + art("001", "000", null, "Installation", "fft", "1", null, false, "120") + "]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("articles[0].plafond"));

        // Le DQE du MTP, en raccourci : trois séries, 001 soumis à sous-détail et plafonné à 10 %.
        String lu = dqe(idDmc, null, "[" + art("001", "000", "Installation", "Installation de chantier", "fft", "1", null, true, "10") + ","
                + art("529", "500", "Ouvrages", "Déblais", "m³", "2054.50", "Le mètre cube", false, null) + ","
                + art("530", "500", null, "Remblais", "m³", "100", "Le mètre cube", false, null) + ","
                + art("601", "600", "Chaussées", "Couche de base", "m²", "3000", "Le mètre carré", false, null) + "]")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(lu, "$[*].numeroPrix")).containsExactly("001", "529", "530", "601");
        assertThat(JsonPath.<List<String>>read(lu, "$[*].serieLibelle")).containsExactly("Installation", "Ouvrages", "Ouvrages", "Chaussées");
        assertThat(JsonPath.<List<Number>>read(lu, "$[*].quantite")).extracting(Number::doubleValue).containsExactly(1.0, 2054.5, 100.0, 3000.0);
        assertThat(JsonPath.<List<Boolean>>read(lu, "$[*].sousDetail")).containsExactly(true, false, false, false);
        assertThat(JsonPath.<List<Object>>read(lu, "$[0].caracteristiques")).isEmpty();

        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "TRAVAUX", Map.of());
        String docs = documents(idDmc);
        assertThat(JsonPath.<List<String>>read(docs, "$[*].type")).contains("BP").doesNotContain("LF", "TC");
        assertThat(JsonPath.<List<String>>read(docs, "$[?(@.type=='BP')].libelle"))
                .containsExactly("Bordereau des prix et détail quantitatif et estimatif");
        int idBp = JsonPath.<List<Integer>>read(docs, "$[?(@.type=='BP')].idDocument").get(0);
        byte[] xlsx = mvc.perform(get("/api/fiches-marche/documents/" + idBp + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(xlsx))) {
            org.apache.poi.xssf.usermodel.XSSFSheet f = wb.getSheetAt(0);
            assertThat(f.getProtect()).isTrue();
            // ⚠️ 03/10 (contre-recette du front) — le numéro du DAO, puis la référence du plan de passation.
            String valide = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String numeroDao = JsonPath.read(valide, "$.valeurs['B02-OB-03']");
            assertThat(numeroDao).isNotBlank();
            assertThat(f.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Dossier d'appel d'offres : " + numeroDao
                    + " (plan de passation : " + JsonPath.read(valide, "$.refeDossier") + ")");
            assertThat(cnm.prs.service.GenerateurClasseursFiche.ligneDossier(null, "00004/PPM-AGPM/CNM/2026"))
                    .isEqualTo("Plan de passation : 00004/PPM-AGPM/CNM/2026");
            List<String> entetes = new java.util.ArrayList<>();
            f.getRow(4).forEach(c -> entetes.add(c.getStringCellValue()));
            assertThat(entetes).containsExactly("N° de prix", "Désignation", "Unité", "Quantité", "Libellé du bordereau",
                    "Prix unitaire en toutes lettres", "Prix unitaire HT", "Montant HT");
            Map<String, Integer> lignes = new java.util.LinkedHashMap<>();
            for (org.apache.poi.ss.usermodel.Row r : f) {
                org.apache.poi.ss.usermodel.Cell c0 = r.getCell(0);
                org.apache.poi.ss.usermodel.Cell c1 = r.getCell(1);
                String cle = (c0 == null ? "" : c0.toString()) + "|" + (c1 == null || c1.getCellType()
                        == org.apache.poi.ss.usermodel.CellType.FORMULA ? "" : c1.getStringCellValue());
                lignes.putIfAbsent(cle, r.getRowNum());
            }
            org.apache.poi.ss.usermodel.Row deblais = f.getRow(lignes.get("529|Déblais"));
            assertThat(deblais.getCell(3).getNumericCellValue()).isEqualTo(2054.5);
            assertThat(deblais.getCell(4).getStringCellValue()).isEqualTo("Le mètre cube à :");
            for (int c = 0; c <= 7; c++) {   // seules les colonnes du candidat sont ouvertes
                assertThat(deblais.getCell(c).getCellStyle().getLocked()).as("colonne " + c).isEqualTo(c != 5 && c != 6);
            }
            assertThat(lignes).containsKeys("500|Ouvrages", "|Sous-total série 500 — Ouvrages", "|Récapitulation", "|Total HT");
            // Des prix posés comme le ferait le candidat : sous-totaux, récapitulation et plafond se calculent.
            Map<String, Double> prix = Map.of("001", 9_000_000.0, "529", 10_000.0, "530", 8_000.0, "601", 15_000.0);
            for (org.apache.poi.ss.usermodel.Row r : f) {
                org.apache.poi.ss.usermodel.Cell c0 = r.getCell(0);
                if (c0 != null && prix.containsKey(c0.toString()) && r.getCell(3) != null) {
                    r.getCell(6).setCellValue(prix.get(c0.toString()));
                }
            }
            org.apache.poi.ss.usermodel.FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            ev.evaluateAll();
            double ouvrages = 2054.5 * 10_000 + 100 * 8_000;
            assertThat(f.getRow(lignes.get("|Sous-total série 500 — Ouvrages")).getCell(7).getNumericCellValue()).isEqualTo(ouvrages);
            double ht = 9_000_000 + ouvrages + 3000 * 15_000;
            assertThat(f.getRow(lignes.get("|Total HT")).getCell(7).getNumericCellValue()).isEqualTo(ht);
            String plafond = null;
            for (org.apache.poi.ss.usermodel.Row r : f) {
                org.apache.poi.ss.usermodel.Cell c1 = r.getCell(1);
                if (c1 != null && c1.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
                    plafond = c1.getStringCellValue();
                }
            }
            assertThat(plafond).isEqualTo("001 : 10 % au plus du montant des travaux — dépassé");   // 9 M > 10 % de 75,3 M
            f.getRow(lignes.get("001|Installation de chantier")).getCell(6).setCellValue(5_000_000);
            ev.clearAllCachedResultValues();
            ev.evaluateAll();
            for (org.apache.poi.ss.usermodel.Row r : f) {
                org.apache.poi.ss.usermodel.Cell c1 = r.getCell(1);
                if (c1 != null && c1.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
                    plafond = c1.getStringCellValue();
                }
            }
            assertThat(plafond).endsWith("— respecté");   // 5 M ≤ 10 % de 71,3 M
            org.apache.poi.xssf.usermodel.XSSFSheet sd = wb.getSheet("Prix soumis à sous-détail");
            assertThat(sd).isNotNull();
            assertThat(sd.getRow(5).getCell(0).getStringCellValue()).isEqualTo("001");
            assertThat(sd.getLastRowNum()).isEqualTo(5);
        }

        // Fournitures : les propriétés des travaux sont ignorées, la caractéristique reste exigée.
        long fournitures = creerDmc(9903);
        String f = dqe(fournitures, null, "[" + art("001", "000", "Installation", "Ordinateur", "U", "2", null, true, "10") + "]")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(f, "$[0].numeroPrix")).isNull();
        assertThat(JsonPath.<Boolean>read(f, "$[0].sousDetail")).isFalse();
    }

    @Test
    @DisplayName("V59 (02/10, DQE des travaux, §B1.4) — prix forfaitaire (MEN), deux lots : BESOIN_INCOMPLET par lot vide et "
            + "par quantité nulle, sans caractéristique exigée ; un BP par lot, sans colonne des lettres ; la révision copie le DQE")
    void dqeForfaitParLot() throws Exception {
        long idDmc = creerDmc(9904);
        cadrage(idDmc, "{\"tranches\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"garantieSoumission\":\"NON\","
                + "\"typePrix\":\"FORFAITAIRE\"}");
        String lot1 = "[" + art("0.1", "0", "Installation de chantier", "Installation", "fft", "1", null, false, null) + ","
                + art("1.1", "1", "Terrassement", "Fouilles", "m³", "332.18", null, false, null) + ","
                + art("1.2", "1", null, "Remblais", "m³", "0", null, false, null) + "]";
        dqe(idDmc, 1, lot1).andExpect(status().isOk());
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[?(@.regle=='BESOIN_INCOMPLET')].message"))
                .containsExactlyInAnyOrder("L'article n° 1.2 du lot 1 (« Remblais ») n'a pas de quantité positive.",
                        "Le lot 2 n'a aucun article.");
        String complet = lot1.replace("\"quantite\":0", "\"quantite\":12.5");
        dqe(idDmc, 1, complet).andExpect(status().isOk());
        dqe(idDmc, 2, complet).andExpect(status().isOk());   // le MEN répète le même DQE au lot 2 (H2 : numéros uniques par lot)
        fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.ok[?(@.regle=='BESOIN_INCOMPLET')].message").get(0))
                .startsWith("Détail quantitatif et estimatif complet");

        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "TRAVAUX", Map.of());
        String docs = documents(idDmc);
        assertThat(JsonPath.<List<String>>read(docs, "$[?(@.type=='BP')].libelle")).containsExactly(
                "Bordereau des prix et détail quantitatif et estimatif — lot 1", "Bordereau des prix et détail quantitatif et estimatif — lot 2");
        int idBp = JsonPath.<List<Integer>>read(docs, "$[?(@.type=='BP')].idDocument").get(1);
        byte[] xlsx = mvc.perform(get("/api/fiches-marche/documents/" + idBp + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(xlsx))) {
            List<String> entetes = new java.util.ArrayList<>();
            wb.getSheetAt(0).getRow(4).forEach(c -> entetes.add(c.getStringCellValue()));
            assertThat(entetes).containsExactly("N° de prix", "Désignation", "Unité", "Quantité", "Prix unitaire HT", "Montant HT");
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);   // aucun prix soumis à sous-détail
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).endsWith("— lot 2");
        }

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        String copie = mvc.perform(get("/api/fiches-marche/" + idDmc + "/articles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(copie, "$[*].numeroPrix")).containsExactly("0.1", "1.1", "1.2", "0.1", "1.1", "1.2");
        assertThat(JsonPath.<List<String>>read(copie, "$[*].serieLibelle")).containsOnly("Installation de chantier", "Terrassement");
    }

    @Test
    @DisplayName("V59 (02/10, §B2) — seuils calculés : liquidité en montant ET en pourcentage, moyenne du chiffre d'affaires "
            + "incomplète, cumul des références sans montant → bloquants ; complétés, la fiche se valide")
    void seuilsCalcules() throws Exception {
        String ref = ref("typeMarche=QUANTITE_FIXE&categorie=TRAVAUX");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B03-QT-07')].libelle"))
                .containsExactly("Chiffre d'affaires minimum exigé (Ariary)");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=~/B03-QT-(1[5-9]|20)/)].type"))
                .containsExactly("POURCENTAGE", "NOMBRE", "NOMBRE", "TEXTE", "NOMBRE", "MONTANT");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B03-QT-18')].valeurDefaut"))
                .containsExactly("travaux de construction");

        long idDmc = creerDmc(9901);
        cadrage(idDmc, "{\"tranches\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"garantieSoumission\":\"NON\"}");
        remplirObligatoires(idDmc, "QUANTITE_FIXE", "TRAVAUX", Map.of("B03-QT-14", "100000000", "B03-QT-15", "10",
                "B03-QT-16", "3", "B03-QT-19", "3", "B03-QT-18", "travaux routiers"));
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[*].regle"))
                .contains("LIQUIDITE_DOUBLE", "CA_MOYENNE", "REFERENCES_CUMUL");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[?(@.regle=='CA_MOYENNE')].message"))
                .anyMatch(m -> m.contains("vont ensemble")).anyMatch(m -> m.contains("Chiffre d'affaires minimum exigé"));

        Map<String, String> b03 = valeursDuBloc(idDmc, "B03", Map.of("B03-QT-17", "5", "B03-QT-07", "5000000000",
                "B03-QT-20", "2500000000"));
        b03.remove("B03-QT-14");   // la liquidité en pourcentage seule (MTP : 10 % de l'offre)
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B03").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":" + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(b03) + "}"))
                .andExpect(status().isOk());
        fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[*].regle"))
                .doesNotContain("LIQUIDITE_DOUBLE", "CA_MOYENNE", "REFERENCES_CUMUL");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.ok[*].regle"))
                .contains("LIQUIDITE_DOUBLE", "CA_MOYENNE", "REFERENCES_CUMUL");
        besoinDeTest(idDmc);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("V60 (03/10, matériel et personnel exigés) — bloc B13 rendu MOYENS ; listes de la version : 400 nominatifs, "
            + "409 hors travaux ; MATERIEL_EXIGE (liste ou B03-QT-09, devenu facultatif) ; la révision copie les deux listes")
    void materielEtPersonnel() throws Exception {
        String ref = ref("typeMarche=QUANTITE_FIXE&categorie=TRAVAUX");
        assertThat(JsonPath.<List<String>>read(ref, "$.blocs[?(@.code=='B13')].rendu")).containsExactly("MOYENS");
        assertThat(JsonPath.<List<String>>read(ref, "$.blocs[?(@.code=='B13')].rubriques[*].code")).containsExactly("B13-MA", "B13-PE");
        assertThat(JsonPath.<List<Boolean>>read(ref, "$.champs[?(@.code=='B03-QT-09')].obligatoire")).containsExactly(false);
        assertThat(JsonPath.<List<String>>read(ref("typeMarche=QUANTITE_FIXE&categorie=FOURNITURES_SERVICES"), "$.blocs[*].code"))
                .doesNotContain("B13");

        long fournitures = creerDmc(9903);
        mvc.perform(put("/api/fiches-marche/" + fournitures + "/materiel").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"materiel\":[]}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOYENS_HORS_PERIMETRE"));

        // Le MEN : deux lots, cinq engins sans minimum, deux postes par lot.
        long idDmc = creerDmc(9904);
        cadrage(idDmc, "{\"tranches\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"garantieSoumission\":\"NON\","
                + "\"typePrix\":\"FORFAITAIRE\"}");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/materiel").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"materiel\":[{\"designation\":\"Citerne à eau\",\"caracteristique\":\"≥ 5 000 l\",\"nombre\":2,"
                        + "\"minimumEnPropre\":3}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("materiel[0].minimumEnPropre"));
        remplirObligatoires(idDmc, "QUANTITE_FIXE", "TRAVAUX", Map.of());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/materiel").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"materiel\":[]}")).andExpect(status().isOk());
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Map<String, String>>read(fiche, "$.valeurs")).doesNotContainKey("B03-QT-09");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[?(@.regle=='MATERIEL_EXIGE')].message"))
                .containsExactly("Le matériel exigé n'est pas dit : remplissez la liste du matériel, ou « Forme sous laquelle "
                        + "l'entrepreneur disposera du matériel (propriété, location…) ».");
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp)).andExpect(status().isConflict());

        String materiel = mvc.perform(put("/api/fiches-marche/" + idDmc + "/materiel").header("Authorization", tokenPrmp)
                .contentType(JSON).content("{\"materiel\":[{\"designation\":\"Bétonnière\",\"caracteristique\":\"≥ 350 l\",\"nombre\":1},"
                        + "{\"designation\":\"Camion ou camionnette\",\"caracteristique\":\"≥ 2,5 t\",\"nombre\":1},"
                        + "{\"designation\":\"Voiture de liaison 4×4\",\"nombre\":1},{\"designation\":\"Pervibrateur\",\"nombre\":1},"
                        + "{\"designation\":\"Groupe électrogène\",\"caracteristique\":\"≥ 3 kVA\",\"nombre\":1}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(materiel, "$[*].ordre")).containsExactly(1, 2, 3, 4, 5);
        assertThat(JsonPath.<List<Object>>read(materiel, "$[*].minimumEnPropre")).containsOnlyNulls();
        String personnel = mvc.perform(put("/api/fiches-marche/" + idDmc + "/personnel").header("Authorization", tokenPrmp)
                .contentType(JSON).content("{\"personnel\":[{\"poste\":\"Conducteur de travaux\",\"diplome\":\"Ingénieur BTP ou "
                        + "équivalent\",\"experienceAnnees\":3,\"justificatifs\":\"CV avec photo, diplôme certifié\",\"parLot\":true},"
                        + "{\"poste\":\"Chef de chantier\",\"diplome\":\"Technicien supérieur BTP\",\"experienceAnnees\":3,\"parLot\":true}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(personnel, "$[*].nombre")).containsExactly(1, 1);   // défaut 1
        assertThat(JsonPath.<List<Boolean>>read(personnel, "$[*].parLot")).containsExactly(true, true);
        fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.ok[?(@.regle=='MATERIEL_EXIGE')].message"))
                .containsExactly("Matériel exigé : 5 ligne(s).");

        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "TRAVAUX", Map.of());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/personnel").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"personnel\":[]}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_VALIDEE"));
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        assertThat(JsonPath.<List<String>>read(mvc.perform(get("/api/fiches-marche/" + idDmc + "/materiel")
                .header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$[*].designation")).containsExactly("Bétonnière", "Camion ou camionnette", "Voiture de liaison 4×4",
                        "Pervibrateur", "Groupe électrogène");
        assertThat(JsonPath.<List<String>>read(mvc.perform(get("/api/fiches-marche/" + idDmc + "/personnel")
                .header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$[*].poste")).containsExactly("Conducteur de travaux", "Chef de chantier");
    }

    @Test
    @DisplayName("V61 (03/10, pièces de l'offre) — bloc B14 rendu PIECES ; liste typée de la version : 400 nominatifs, 409 hors "
            + "travaux ; PIECES_OFFRE_EXIGEES (liste OFFRE ou B04-PI-01, devenu facultatif) ; PIECES_EN_DOUBLE tant que B03-CQ-01 "
            + "garde son défaut ; la révision copie la liste")
    void piecesDeLOffre() throws Exception {
        String ref = ref("typeMarche=QUANTITE_FIXE&categorie=TRAVAUX");
        assertThat(JsonPath.<List<String>>read(ref, "$.blocs[?(@.code=='B14')].rendu")).containsExactly("PIECES");
        assertThat(JsonPath.<List<String>>read(ref, "$.blocs[?(@.code=='B14')].rubriques[*].code")).containsExactly("B14-AD", "B14-OF");
        assertThat(JsonPath.<List<Boolean>>read(ref, "$.champs[?(@.code=='B04-PI-01')].obligatoire")).containsExactly(false);

        // ⚠️ V62 — hors périmètre : le contrat-cadre (aucun DPAC n'a de place pour ces pièces), travaux compris.
        assertThat(JsonPath.<List<String>>read(ref("typeMarche=CONTRAT_CADRE&categorie=TRAVAUX"), "$.blocs[*].code")).doesNotContain("B14");
        long contratCadre = creerDmc(9902);
        mvc.perform(put("/api/fiches-marche/" + contratCadre + "/pieces").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"pieces\":[]}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PIECES_HORS_PERIMETRE"));

        long idDmc = creerDmc(9904);
        cadrage(idDmc, "{\"tranches\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"garantieSoumission\":\"NON\","
                + "\"typePrix\":\"FORFAITAIRE\"}");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/pieces").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"pieces\":[{\"rubrique\":\"AUTRE\",\"libelle\":\"X\"}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("pieces[0].rubrique"));
        remplirObligatoires(idDmc, "QUANTITE_FIXE", "TRAVAUX", Map.of());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/pieces").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"pieces\":[]}")).andExpect(status().isOk());
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[?(@.regle=='PIECES_OFFRE_EXIGEES')].message"))
                .containsExactly("Les pièces de l'offre ne sont pas dites : remplissez la liste des pièces de l'offre, ou "
                        + "« Documents et pièces constitutifs de l'offre ».");

        // Le MEN : quatre pièces administratives sans numéro, à 3 mois ; les autres pièces de l'offre.
        String lues = mvc.perform(put("/api/fiches-marche/" + idDmc + "/pieces").header("Authorization", tokenPrmp)
                .contentType(JSON).content("{\"pieces\":["
                        + "{\"rubrique\":\"ADMINISTRATIVE\",\"libelle\":\"Carte d'immatriculation fiscale\",\"forme\":\"copie certifiée\",\"ancienneteMaxMois\":3},"
                        + "{\"rubrique\":\"ADMINISTRATIVE\",\"libelle\":\"Carte statistique\",\"forme\":\"copie certifiée\",\"ancienneteMaxMois\":3},"
                        + "{\"rubrique\":\"ADMINISTRATIVE\",\"libelle\":\"Certificat de non-faillite\",\"forme\":\"original\",\"ancienneteMaxMois\":3},"
                        + "{\"rubrique\":\"ADMINISTRATIVE\",\"libelle\":\"Extrait RCS\",\"forme\":\"original\",\"ancienneteMaxMois\":3},"
                        + "{\"rubrique\":\"offre\",\"libelle\":\"Garantie de soumission\",\"parLot\":true},"
                        + "{\"rubrique\":\"OFFRE\",\"libelle\":\"Planning d'exécution\"}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(lues, "$[*].rubrique")).containsExactly("ADMINISTRATIVE", "ADMINISTRATIVE",
                "ADMINISTRATIVE", "ADMINISTRATIVE", "OFFRE", "OFFRE");
        assertThat(JsonPath.<List<Integer>>read(lues, "$[*].ordre")).containsExactly(1, 2, 3, 4, 5, 6);
        fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.ok[?(@.regle=='PIECES_OFFRE_EXIGEES')].message"))
                .containsExactly("Pièces de l'offre : 2 pièce(s).");
        // B03-CQ-01 garde la liste du document type (défaut recopié à la création) : avertissement, jamais bloquant.
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.avertissements[?(@.regle=='PIECES_EN_DOUBLE')].champs[0]"))
                .containsExactly("B03-CQ-01");
        Map<String, String> b03 = valeursDuBloc(idDmc, "B03", Map.of());
        b03.remove("B03-CQ-01");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B03").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":" + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(b03) + "}"))
                .andExpect(status().isOk());
        fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(fiche, "$.bilanControles.avertissements[?(@.regle=='PIECES_EN_DOUBLE')]")).isEmpty();

        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "TRAVAUX", Map.of());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        assertThat(JsonPath.<List<String>>read(mvc.perform(get("/api/fiches-marche/" + idDmc + "/pieces")
                .header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$[*].libelle")).containsExactly("Carte d'immatriculation fiscale", "Carte statistique", "Certificat de non-faillite",
                        "Extrait RCS", "Garantie de soumission", "Planning d'exécution");
    }

    @Test
    @DisplayName("V62 (03/10, pièces ouvertes aux fournitures, choix A) — B14 servi aux fournitures (quantité fixe) ; "
            + "PIECES_OFFRE_EXIGEES sur B04-CO-01 devenu facultatif ; PIECES_EN_DOUBLE ; le DPAO-F imprime les deux listes à la "
            + "clause 6.2, sous « Pièces administratives à joindre à l'offre : »")
    void piecesDesFournitures() throws Exception {
        String ref = ref("typeMarche=QUANTITE_FIXE&categorie=FOURNITURES_SERVICES");
        assertThat(JsonPath.<List<String>>read(ref, "$.blocs[?(@.code=='B14')].rubriques[*].code")).containsExactly("B14-AD", "B14-OF");
        assertThat(JsonPath.<List<Boolean>>read(ref, "$.champs[?(@.code=='B04-CO-01')].obligatoire")).containsExactly(false);
        assertThat(JsonPath.<List<String>>read(ref("typeMarche=CONTRAT_CADRE&categorie=FOURNITURES_SERVICES"), "$.blocs[*].code"))
                .doesNotContain("B14");

        long idDmc = creerDmc(9903);
        remplirObligatoires(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", Map.of());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/pieces").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"pieces\":[]}")).andExpect(status().isOk());
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[?(@.regle=='PIECES_OFFRE_EXIGEES')].message"))
                .containsExactly("Les pièces de l'offre ne sont pas dites : remplissez la liste des pièces de l'offre, ou "
                        + "« Documents et pièces constituant l'offre ».");

        // Les pièces administratives du 2463, datées ; deux pièces de l'offre.
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/pieces").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"pieces\":["
                        + "{\"rubrique\":\"ADMINISTRATIVE\",\"libelle\":\"Carte d'Immatriculation Fiscale 2026 ou 2025 validée\",\"ancienneteMaxMois\":3},"
                        + "{\"rubrique\":\"ADMINISTRATIVE\",\"libelle\":\"Certificat de non-faillite\",\"forme\":\"original\",\"ancienneteMaxMois\":3},"
                        + "{\"rubrique\":\"OFFRE\",\"libelle\":\"Bordereau des prix\",\"parLot\":true},"
                        + "{\"rubrique\":\"OFFRE\",\"libelle\":\"Prospectus des fournitures\"}]}"))
                .andExpect(status().isOk());
        fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(fiche, "$.bilanControles.bloquants[?(@.regle=='PIECES_OFFRE_EXIGEES')]")).isEmpty();
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.avertissements[?(@.regle=='PIECES_EN_DOUBLE')].champs[0]"))
                .containsExactly("B03-CQ-01");   // le défaut du document type, recopié à la création

        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", Map.of());
        int idDpao = JsonPath.<List<Integer>>read(documents(idDmc), "$[?(@.type=='DPAO' && @.extension=='docx')].idDocument").get(0);
        byte[] docx = mvc.perform(get("/api/fiches-marche/documents/" + idDpao + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (org.apache.poi.xwpf.usermodel.XWPFDocument d = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new java.io.ByteArrayInputStream(docx)); org.apache.poi.xwpf.extractor.XWPFWordExtractor ex =
                        new org.apache.poi.xwpf.extractor.XWPFWordExtractor(d)) {
            assertThat(ex.getText()).containsSubsequence("Documents ou pièces à remettre en sus",
                    "- Bordereau des prix, une par lot", "- Prospectus des fournitures",
                    "Pièces administratives à joindre à l’offre :",
                    "- Carte d'Immatriculation Fiscale 2026 ou 2025 validée, datée de moins de 3 mois",
                    "- Certificat de non-faillite, original, datée de moins de 3 mois")
                    // ⚠️ 2026-10-06 (recette du DAO complet, C4) — la liste saisie remplace le texte B03-CQ-01 (son défaut) : plus de doublon.
                    .doesNotContain("une photocopie certifiée de la carte statistique");
        }
    }

    /** ⚠️ V59 — un article de travaux en JSON ({@code null} : propriété absente). */
    private static String art(String numero, String serie, String serieLibelle, String designation, String unite,
            String quantite, String libelleBordereau, boolean sousDetail, String plafond) {
        return "{\"numeroPrix\":\"" + numero + "\",\"serie\":\"" + serie + "\""
                + (serieLibelle == null ? "" : ",\"serieLibelle\":\"" + serieLibelle + "\"")
                + ",\"designation\":\"" + designation + "\",\"unite\":\"" + unite + "\",\"quantite\":" + quantite
                + (libelleBordereau == null ? "" : ",\"libelleBordereau\":\"" + libelleBordereau + "\"")
                + ",\"sousDetail\":" + sousDetail + (plafond == null ? "" : ",\"plafond\":" + plafond)
                + ",\"caracteristiques\":[]}";
    }

    private org.springframework.test.web.servlet.ResultActions dqe(long idDmc, Integer lot, String articles) throws Exception {
        return mvc.perform(put("/api/fiches-marche/" + idDmc + "/articles" + (lot == null ? "" : "?lot=" + lot))
                .header("Authorization", tokenPrmp).contentType(JSON).content("{\"articles\":" + articles + "}"));
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
