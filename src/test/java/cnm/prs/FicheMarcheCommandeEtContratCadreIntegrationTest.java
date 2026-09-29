package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.service.ChampFicheMarcheService;

/**
 * ⚠️ <strong>Marché à commande (lot 3) et contrat-cadre (lot 4)</strong> — demandes front du 2026-09-23. Le référentiel
 * est celui des fichiers de correspondance du front, chargés par l'import ({@code src/test/resources/fiche-marche},
 * copies de {@code frontendprs2/docs}) : 123 champs des fournitures (116 avant le 2026-09-25, 121 avant le 26), 114 du contrat-cadre.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), lignes en appel d'offres ouvert : 9901 à quantité fixe, 9902
 * à commande, 9903 contrat-cadre.</p>
 */
class FicheMarcheCommandeEtContratCadreIntegrationTest extends CnmIntegrationTestSupport {

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
        ligne(9901, FormeMarche.QUANTITE_FIXE);
        ligne(9902, FormeMarche.A_COMMANDE);
        ligne(9903, FormeMarche.CONTRAT_CADRE);
    }

    // ------------------------------------------------------------------ 1. chargement des référentiels

    @Test
    @DisplayName("1 — Import : 156 champs des fournitures (149 avant le lot D2, 123 avant V50) et 132 du contrat-cadre (114 avant le 28/09), aucun rejet ; champs actifs servis : 179 en "
            + "quantité fixe, 184 à commande (lot D2 : + 7), 175 en contrat-cadre (V50 : + 26 ; modèle officiel du 28/09 : − 20 + 11 ; lot D : + 9 ; Q2 : − 1) — 2026-09-25 : cinq créations, "
            + "B06-EO-11 réservé à la quantité fixe")
    void chargementDesReferentiels() throws Exception {
        ChampFicheMarcheService.BilanImport f = importer("referentiel-champs-fiche-marche-fournitures.csv");
        assertThat(f.rejets()).isEmpty();
        assertThat(f.crees()).hasSize(156);   // V50 : + 26 champs de la remise électronique ; lot D2 (2026-09-29) : + 7
        ChampFicheMarcheService.BilanImport cc = importer("referentiel-champs-fiche-marche-contrat-cadre.csv");
        assertThat(cc.rejets()).isEmpty();
        assertThat(cc.crees()).hasSize(132);   // 2026-09-28 : + 9 champs du modèle officiel, + 9 du lot D

        assertThat(champs("QUANTITE_FIXE")).hasSize(179);
        assertThat(champs("A_COMMANDE")).hasSize(184);
        assertThat(champs("CONTRAT_CADRE")).hasSize(175);   // 2026-09-28 : 176 − 20 + 11 (modèle officiel) + 9 (lot D) − 1 (B07-DU-06, Q2)
    }

    // ------------------------------------------------------------------ 2. rubriques servies par type (B4)

    @Test
    @DisplayName("2 — Contrat-cadre : B07 et ses 8 rubriques, aucune rubrique partagée des fournitures ; quantité fixe et à "
            + "commande : aucune des 37 rubriques du contrat-cadre, pas de B07")
    void rubriquesParType() throws Exception {
        importer("referentiel-champs-fiche-marche-fournitures.csv");
        importer("referentiel-champs-fiche-marche-contrat-cadre.csv");
        String cc = referentiel("CONTRAT_CADRE");
        assertThat(JsonPath.<List<String>>read(cc, "$.blocs[*].code")).contains("B07");
        assertThat(JsonPath.<List<String>>read(cc, "$.blocs[?(@.code=='B07')].rubriques[*].code"))
                .containsExactly("B07-PS", "B07-FS", "B07-MA", "B07-TN", "B07-PI", "B07-DU", "B07-DE", "B07-PE");
        List<String> rubriquesCc = JsonPath.read(cc, "$.blocs[*].rubriques[*].code");
        assertThat(rubriquesCc).contains("B04-RQ", "B09-AU", "B01-AC").doesNotContain("B02-AU", "B04-RO", "B09-AS");

        for (String type : List.of("QUANTITE_FIXE", "A_COMMANDE")) {
            String ref = referentiel(type);
            assertThat(JsonPath.<List<String>>read(ref, "$.blocs[*].code")).as(type).doesNotContain("B07");
            List<String> rubriques = JsonPath.read(ref, "$.blocs[*].rubriques[*].code");
            assertThat(rubriques).as(type).contains("B02-AU", "B04-RO").doesNotContain("B04-RQ", "B09-AU", "B02-OE");
        }
    }

    // ------------------------------------------------------------------ 3. marché à commande (lot 3)

    @Test
    @DisplayName("3 — À commande : éligible et outillé, DMC créé, cadrage des neuf questions sans attributaires, typeOutille ; "
            + "validation → DPAO, CCAP et AE rendus depuis les documents types des fournitures (lot D2, 2026-09-29) : rangée « 1.2 "
            + "Marché à commandes », délai fixé dans le bon de commande")
    void marcheACommande() throws Exception {
        importer("referentiel-champs-fiche-marche-fournitures.csv");
        String eligibles = mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(eligibles, "$[?(@.idDetail==9902)].formeOutillee")).containsExactly(true);
        Long idDmc = creerDmc(9902);
        cadrage(idDmc, "{\"alloti\":\"NON\",\"variantes\":\"NON\",\"groupement\":\"NON\",\"provenance\":\"NATIONAL\","
                + "\"typePrix\":\"UNITAIRES\",\"prixRevisable\":\"NON\",\"garantieSoumission\":\"NON\",\"avance\":\"NON\","
                + "\"penalites\":\"CCAG\"}");
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeMarche").value("A_COMMANDE"))
                .andExpect(jsonPath("$.typeOutille").value(true));

        Map<String, String> propres = new LinkedHashMap<>();
        propres.put("B05-TP-02", "10000000");
        propres.put("B05-TP-03", "50000000");
        remplirObligatoiresEtValider(idDmc, "A_COMMANDE", null, propres);

        List<String> types = JsonPath.read(documents(idDmc), "$[*].type");
        assertThat(types).containsExactly("DPAO", "DPAO", "CCAP", "CCAP", "AE", "AE", "LF", "LF", "BP", "TC");
        assertThat(texte(idDmc, "DPAO")).contains("1.2. - DONNEES PARTICULIERES DE L’APPEL D’OFFRES", "1.2 Marché à commandes");
        assertThat(texte(idDmc, "CCAP")).contains("CAHIER DES PRESCRIPTIONS SPECIALES", "fixé dans le bon de commande");
        assertThat(texte(idDmc, "AE")).contains("ACTE D'ENGAGEMENT (A.E)", "Le délai d'exécution est fixé dans le bon de commande");
        assertThat(JsonPath.<List<String>>read(documents(idDmc), "$[?(@.type=='CCAP')].libelle"))
                .containsOnly("Cahier des prescriptions spéciales");
    }

    // ------------------------------------------------------------------ 4. contrat-cadre (lot 4)

    @Test
    @DisplayName("4 — Contrat-cadre : outillé, 23 informations reprises du plan, attributaires MONO ferme la remise en "
            + "concurrence et MULTI l'ouvre ; validation → DPAC et AE seulement, jamais DPAO ni CCAP")
    void contratCadre() throws Exception {
        importer("referentiel-champs-fiche-marche-contrat-cadre.csv");
        Long idDmc = creerDmc(9903);
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeMarche").value("CONTRAT_CADRE"))
                .andExpect(jsonPath("$.typeOutille").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Map<String, Object>>read(fiche, "$.valeursPpm")).hasSize(23);

        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"attributaires\":2}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("attributaires"));
        cadrage(idDmc, "{\"alloti\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"typePrix\":\"UNITAIRES\","
                + "\"attributaires\":\"MONO\"}");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B07").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B07-MA-01\":10,\"B07-MA-03\":\"Après remise en concurrence\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valeurs.B07-MA-01").value("10"))
                .andExpect(jsonPath("$.valeurs.B07-MA-03").doesNotExist());
        cadrage(idDmc, "{\"alloti\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"typePrix\":\"UNITAIRES\","
                + "\"attributaires\":\"MULTI\"}");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B07").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B07-MA-01\":10,\"B07-MA-03\":\"Après remise en concurrence\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valeurs.B07-MA-03").value("Après remise en concurrence"))
                .andExpect(jsonPath("$.valeurs.B07-MA-01").doesNotExist());

        remplirObligatoiresEtValider(idDmc, "CONTRAT_CADRE", null, Map.of("B05-MT-01", "250000000"));
        List<String> types = JsonPath.read(documents(idDmc), "$[*].type");
        assertThat(types).containsExactly("DPAC", "DPAC", "AE", "AE", "LF", "LF", "BP", "TC");
        // ⚠️ Lot D (2026-09-28) — le DPAC et l'AE du contrat-cadre sont le document type officiel rempli, plus des listes.
        assertThat(texte(idDmc, "DPAC")).contains("DONNEES PARTICULIERES D’APPEL A CONCURRENCE",
                "Calendrier prévisionnel de la consultation").doesNotContain("{{");
        assertThat(texte(idDmc, "AE")).contains("ACTE D’ENGAGEMENT ET CAHIER DES CLAUSES ADMINISTRATIVES PARTICULIERES",
                "ARTICLE 4 – Modalités d’attribution des marchés subséquents",
                "est estimé à : 250 000 000 Ariary H.T.").doesNotContain("{{");
        assertThat(JsonPath.<List<String>>read(documents(idDmc), "$[?(@.type=='AE')].libelle"))
                .containsOnly("Contrat-cadre valant acte d'engagement et CCAP");
        assertThat(JsonPath.<List<String>>read(documents(idDmc), "$[?(@.type=='DPAC')].libelle"))
                .containsOnly("Données particulières d'appel à concurrence");
    }

    // ------------------------------------------------------------------ 5. quantité fixe inchangée

    @Test
    @DisplayName("5 — Quantité fixe : typeOutille vrai, B07 absent des blocs, et rien du contrat-cadre dans ses champs")
    void quantiteFixeInchangee() throws Exception {
        importer("referentiel-champs-fiche-marche-contrat-cadre.csv");
        Long idDmc = creerDmc(9901);
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeOutille").value(true))
                .andExpect(jsonPath("$.bilanControles.nbAttendus").value(0));
        assertThat(champs("QUANTITE_FIXE")).noneMatch(c -> c.startsWith("B07-") || c.startsWith("B02-OE"));
    }

    // ------------------------------------------------------------------ 6. modèle officiel du contrat-cadre (2026-09-28)

    @Autowired private cnm.prs.repository.MandatRepository mandatRepository;

    @Test
    @DisplayName("6 — Modèle officiel ARMP (2026-09-28, B1-B10) : référentiel du contrat-cadre aligné (date-heure, offres optimisées, "
            + "signataire, acte de nomination depuis le mandat, informations du candidat et doublons retirés) ; quantité fixe "
            + "et à commande inchangées ; DATES_ORDRE ordonne les offres optimisées ; validation sans information du candidat")
    void modeleOfficielDuContratCadre() throws Exception {
        importer("referentiel-champs-fiche-marche-fournitures.csv");
        importer("referentiel-champs-fiche-marche-contrat-cadre.csv");
        String ref = referentiel("CONTRAT_CADRE");
        List<String> cc = JsonPath.read(ref, "$.champs[*].code");
        assertThat(cc).contains("B04-CP-06", "B04-CP-07", "B04-CP-08", "B02-SG-03", "B02-SG-04", "B07-MA-06", "B05-PM-04",
                "B05-PM-05", "B07-DU-07", "B04-VO-01", "B06-AN-03")
                .doesNotContain("B04-RQ-04", "B04-RQ-05", "B07-PS-01", "B07-MA-05", "B03-TI-01", "B03-TI-05", "B03-GC-02",
                        "B03-GC-06", "B08-FP-06", "B08-FP-09", "B08-FI-03", "B09-PR-01", "B06-AN-02");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B04-CP-02')].type")).containsExactly("DATE_HEURE");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B07-DU-03')].libelle"))
                .containsExactly("Durée des marchés subséquents (jours)");
        assertThat(JsonPath.<List<List<String>>>read(ref, "$.champs[?(@.code=='B07-MA-04')].options").get(0))
                .containsExactly("Titulaires des lots correspondant à l'objet du marché", "Titulaires de tous les lots");
        for (String type : List.of("QUANTITE_FIXE", "A_COMMANDE")) {
            assertThat(champs(type)).as(type).contains("B04-VO-01", "B09-PR-01", "B06-AN-02").doesNotContain("B06-AN-03");
        }

        // B7 — l'acte de nomination se recopie depuis le mandat en vigueur de la PRMP, à la création de la fiche.
        cnm.prs.entity.Mandat m = new cnm.prs.entity.Mandat();
        m.setIdPrmp("PRMP001");
        m.setTitulaire("Titulaire test");
        m.setDateDebut(LocalDate.of(2025, 1, 15));
        m.setDateFin(LocalDate.now().plusYears(2));
        m.setRefArrete("Arrêté n° 1234/2025");
        m.setStatut("ACTIF");
        m.setNumeroMandat(1);
        mandatRepository.save(m);
        Long idDmc = creerDmc(9903);
        cadrage(idDmc, "{\"alloti\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"typePrix\":\"UNITAIRES\","
                + "\"attributaires\":\"MULTI\"}");
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(fiche, "$.valeurs['B02-SG-03']")).isEqualTo("Arrêté n° 1234/2025 du 15/01/2025");

        // B1 — la date limite porte son heure (une date seule → 400) ; B2 — DATES_ORDRE refuse la réception des offres
        // optimisées avant leur demande, et compare la date-heure sur sa date.
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B04").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B04-CP-02\":\"2026-04-10\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("B04-CP-02"));
        String b04 = mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B04").header("Authorization", tokenPrmp)
                .contentType(JSON).content("{\"valeurs\":{\"B04-CP-02\":\"2026-04-10T10:00\",\"B04-CP-06\":\"2026-04-20\","
                        + "\"B04-CP-07\":\"2026-04-15\"}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(b04, "$.bilanControles.bloquants[?(@.regle=='DATES_ORDRE')].message"))
                .singleElement().asString().contains("réception des offres optimisées (2026-04-15) précède demandes d'offres "
                        + "optimisées (2026-04-20)");

        // B5 — une fiche de contrat-cadre se valide sans aucune information du candidat.
        Map<String, String> donnees = new LinkedHashMap<>();
        donnees.put("B05-MT-01", "250000000");
        donnees.put("B04-CP-01", "2026-03-02");
        donnees.put("B04-CP-02", "2026-04-10T10:00");
        donnees.put("B04-CP-03", "2026-04-13");
        donnees.put("B04-CP-06", "2026-04-20");
        donnees.put("B04-CP-07", "2026-04-27");
        donnees.put("B04-CP-04", "2026-05-04");
        donnees.put("B04-CP-08", "2026-05-06");
        donnees.put("B04-CP-05", "2026-05-11");
        remplirObligatoiresEtValider(idDmc, "CONTRAT_CADRE", "FOURNITURES_SERVICES", donnees);
        fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VALIDEE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Map<String, String>>read(fiche, "$.valeurs").keySet())
                .noneMatch(k -> k.startsWith("B03-TI") || k.startsWith("B03-GC") || k.matches("B08-FP-0[6-9]"));
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.ok[?(@.regle=='DATES_ORDRE')].message"))
                .singleElement().asString().contains("remise des offres < ouverture des plis < demandes d'offres optimisées < "
                        + "réception des offres optimisées < attribution < courriers de rejet < notification");
        // Lot D : le document type rempli (date-heure imprimée, calendrier en tableau, acte de nomination après « nommée par »).
        assertThat(texte(idDmc, "DPAC")).contains("DATE ET HEURE LIMITES DE REMISE DES OFFRES : 10/04/2026 10:00",
                "20/04/2026\tEnvoi des demandes d’offres optimisées");
        assertThat(texte(idDmc, "AE")).contains("nommée par Arrêté n° 1234/2025 du 15/01/2025.")
                .doesNotContain("Régime des pénalités de retard");
    }

    // ------------------------------------------------------------------ 7. lot D : un AE par lot, les autres formes au lot 2a

    @Autowired private cnm.prs.service.DocumentsFicheMarcheService documentsService;

    @Test
    @DisplayName("7 — Lot D : contrat-cadre alloti en 2 lots → un DPAC et deux AE « LOT n° 1 / 2 » rendus du document type ; "
            + "à commande → DPAO, CCAP et AE rendus des documents types des fournitures (lot D2, 2026-09-29)")
    void lotDProductionParForme() throws Exception {
        importer("referentiel-champs-fiche-marche-fournitures.csv");
        importer("referentiel-champs-fiche-marche-contrat-cadre.csv");
        cnm.prs.dto.FicheMarcheDto cc = new cnm.prs.dto.FicheMarcheDto();
        cc.setIdDetail(9903);
        cc.setVersion(1);
        cc.setRefeDossier("DOS-9900");
        cc.setTypeMarche("CONTRAT_CADRE");
        cc.setCategorie("FOURNITURES_SERVICES");
        cc.setNbLots(2);
        cc.setSaisieParLot(true);
        cc.setCadrage(new java.util.LinkedHashMap<>(Map.of("attributaires", "MULTI", "alloti", "OUI")));
        cc.setValeurs(new java.util.HashMap<>());
        List<cnm.prs.service.DocumentsFicheMarcheService.Produit> produits = documentsService.produire(cc, java.time.LocalDateTime.now());
        assertThat(produits.stream().filter(p -> "docx".equals(p.extension())).map(p -> p.type() + ":" + p.lot()))
                .containsExactly("DPAC:null", "AE:1", "AE:2");
        for (cnm.prs.service.DocumentsFicheMarcheService.Produit p : produits) {
            if ("AE".equals(p.type()) && "docx".equals(p.extension())) {
                assertThat(texteDocx(p.contenu())).contains("CCAP LOT n°" + p.lot(), "passé pour le lot n° " + p.lot() + ".");
            }
        }

        cnm.prs.dto.FicheMarcheDto ac = new cnm.prs.dto.FicheMarcheDto();
        ac.setIdDetail(9902);
        ac.setVersion(1);
        ac.setTypeMarche("A_COMMANDE");
        ac.setCategorie("FOURNITURES_SERVICES");
        ac.setCadrage(new java.util.LinkedHashMap<>(Map.of("alloti", "NON")));
        ac.setValeurs(new java.util.HashMap<>(Map.of("B02-OB-03", "AOO 1/2026", "B04-VO-01", "90")));
        List<cnm.prs.service.DocumentsFicheMarcheService.Produit> lot2a = documentsService.produire(ac, java.time.LocalDateTime.now());
        assertThat(lot2a.stream().filter(p -> "docx".equals(p.extension())).map(cnm.prs.service.DocumentsFicheMarcheService.Produit::type))
                .containsSubsequence("DPAO", "CCAP", "AE").doesNotContain("DPAC");
        byte[] dpao = lot2a.stream().filter(p -> "DPAO".equals(p.type()) && "docx".equals(p.extension())).findFirst().orElseThrow().contenu();
        assertThat(texteDocx(dpao)).contains("DONNEES PARTICULIERES DE L’APPEL D’OFFRES", "Le délai de validité de l’offre sera de 90 jours.")
                .doesNotContain("Délai de validité des offres (jours) : 90");   // plus la liste « libellé : valeur » du lot 2a
    }

    // ------------------------------------------------------------------ 8. Q2 : les informations sans trou

    @Test
    @DisplayName("8 — Q2 (2026-09-28) : neuf informations sans trou servies facultatives, B07-DU-06 absent ; une fiche de "
            + "contrat-cadre se valide sans aucune des dix ; un délai de paiement saisi au-delà de 75 jours reste signalé")
    void informationsSansTrou() throws Exception {
        importer("referentiel-champs-fiche-marche-fournitures.csv");
        importer("referentiel-champs-fiche-marche-contrat-cadre.csv");
        String ref = referentiel("CONTRAT_CADRE");
        List<String> facultatifs = List.of("B04-DS-02", "B04-DS-03", "B06-SC-01", "B06-SO-04", "B07-DU-01", "B07-MA-03",
                "B07-PI-01", "B08-FP-03", "B10-RS-01");
        for (String code : facultatifs) {
            assertThat(JsonPath.<List<Boolean>>read(ref, "$.champs[?(@.code=='" + code + "')].obligatoire")).as(code)
                    .containsExactly(false);
        }
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[*].code")).doesNotContain("B07-DU-06").contains("B02-DC-01");

        Long idDmc = creerDmc(9903);
        cadrage(idDmc, "{\"alloti\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\",\"typePrix\":\"UNITAIRES\","
                + "\"attributaires\":\"MULTI\"}");
        String b08 = mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B08").header("Authorization", tokenPrmp)
                .contentType(JSON).content("{\"valeurs\":{\"B08-FP-03\":90}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(b08, "$.bilanControles.avertissements[*].regle")).contains("DELAI_PAIEMENT_75");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B08").header("Authorization", tokenPrmp)
                .contentType(JSON).content("{\"valeurs\":{}}")).andExpect(status().isOk());
        remplirObligatoiresEtValider(idDmc, "CONTRAT_CADRE", "FOURNITURES_SERVICES", Map.of("B05-MT-01", "250000000"));
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VALIDEE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Map<String, String>>read(fiche, "$.valeurs").keySet())
                .doesNotContainAnyElementsOf(facultatifs).doesNotContain("B07-DU-06");
    }

    private static String texteDocx(byte[] docx) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }

    // ------------------------------------------------------------------ outils

    private ChampFicheMarcheService.BilanImport importer(String fichier) throws Exception {
        Path chemin = new ClassPathResource("fiche-marche/" + fichier).getFile().toPath();
        return champService.importerCsv(chemin);
    }

    private String referentiel(String type) throws Exception {
        return mvc.perform(get("/api/champs-fiche-marche").param("typeMarche", type)
                .param("categorie", "FOURNITURES_SERVICES").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private List<String> champs(String type) throws Exception {
        return JsonPath.read(referentiel(type), "$.champs[*].code");
    }

    private String documents(Long idDmc) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String texte(Long idDmc, String type) throws Exception {
        int id = JsonPath.<List<Integer>>read(documents(idDmc),
                "$[?(@.type=='" + type + "' && @.extension=='docx')].idDocument").get(0);
        byte[] docx = mvc.perform(get("/api/fiches-marche/documents/" + id + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
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

    private void cadrage(Long idDmc, String cadrage) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":" + cadrage + "}")).andExpect(status().isOk());
    }

    private void ligne(int idDetail, FormeMarche forme) {
        Marche l = marcheDao(idDetail, 9900, 9900);
        l.setIdMode(92);
        l.setFormeMarche(forme);
        l.setDesignationMarche("Marché " + idDetail);
        marcheRepository.save(l);
    }
}
