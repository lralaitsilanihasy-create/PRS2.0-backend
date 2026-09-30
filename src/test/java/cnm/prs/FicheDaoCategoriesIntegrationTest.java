package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
import cnm.prs.repository.ChampFicheMarcheRepository;
import cnm.prs.service.ChampFicheMarcheService;

/**
 * ⚠️ <strong>Les trois catégories de fiche DAO, lot 5</strong> (demande front du 2026-09-24) — le second axe du
 * référentiel : filtre {@code categorie}, catégorie dérivée de la nature de la ligne, correspondance administrable
 * {@code tr_nature.CATEGORIE_DAO}, refus des catégories non outillées, question des tranches réservée aux travaux.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), lignes en appel d'offres ouvert à quantité fixe : 9901
 * Fournitures (nature 92), 9902 Prestations intellectuelles (nature 91), 9903 nature sans catégorie (93), 9904 sans nature.</p>
 */
class FicheDaoCategoriesIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheService champService;
    @Autowired private ChampFicheMarcheRepository champRepository;

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

        natureRepository.save(new Nature(91, "Prestations intellectuelles", null, "PRESTATIONS_INTELLECTUELLES"));
        natureRepository.save(new Nature(93, "Nature à classer", null, null));
        ligne(9901, natureFournitures());
        ligne(9902, 91);
        ligne(9903, 93);
        ligne(9904, null);
        champService.importerCsv(new ClassPathResource("fiche-marche/referentiel-champs-fiche-marche-fournitures.csv")
                .getFile().toPath());
    }

    // ------------------------------------------------------------------ 1-2. le référentiel sur deux axes

    @Test
    @DisplayName("1-2 — Référentiel : quantité fixe + fournitures et services = ses 155 champs (156 avant le retrait de B08-PA-08 le 2026-09-29 soir, 179 avant le retrait des champs non imprimés, 172 avant le lot D2 le 2026-09-29, 146 avant V50 le 2026-09-27, 139 avant le 25 ; avec ou sans le "
            + "filtre) ; travaux : aucun champ saisi ni rubrique des fournitures, seules les 23 informations du plan ; "
            + "catégorie inconnue → 400")
    void referentielSurDeuxAxes() throws Exception {
        assertThat(champs("typeMarche=QUANTITE_FIXE&categorie=FOURNITURES_SERVICES")).hasSize(155);
        assertThat(champs("typeMarche=QUANTITE_FIXE")).hasSize(155);

        String travaux = ref("typeMarche=QUANTITE_FIXE&categorie=TRAVAUX");
        assertThat(JsonPath.<List<String>>read(travaux, "$.champs[?(@.source=='PPM')].code")).hasSize(23);
        // V47 (2026-09-26) — les quatre champs des formulaires du candidat valent pour les trois catégories ;
        // V50 (2026-09-27) — les seize champs saisis de la remise électronique (B04-SE-02 à -17) aussi.
        List<String> troisCategories = new java.util.ArrayList<>(List.of("B02-OB-03", "B03-CQ-01", "B03-CQ-09", "B03-CQ-10"));
        java.util.stream.IntStream.rangeClosed(2, 17).forEach(i -> troisCategories.add(String.format("B04-SE-%02d", i)));
        assertThat(JsonPath.<List<String>>read(travaux, "$.champs[?(@.source=='SAISIE')].code"))
                .containsExactlyInAnyOrderElementsOf(troisCategories);
        // Rubriques : celles du plan, plus celles des travaux (V41) encore sans champ dans ce jeu — servies « à compléter » ;
        // aucune des fournitures.
        assertThat(JsonPath.<List<String>>read(travaux, "$.blocs[*].rubriques[*].code"))
                .contains("B01-AC", "B02-OB", "B02-LV", "B02-LT").doesNotContain("B02-AU", "B04-RO", "B05-GS");
        assertThat(JsonPath.<List<String>>read(ref("categorie=PRESTATIONS_INTELLECTUELLES"), "$.champs[?(@.source=='PPM')].code")).hasSize(23);
        assertThat(JsonPath.<List<String>>read(ref("categorie=PRESTATIONS_INTELLECTUELLES"), "$.champs[?(@.source=='SAISIE')].code"))
                .containsExactlyInAnyOrderElementsOf(troisCategories);

        mvc.perform(get("/api/champs-fiche-marche?categorie=MOBILIER").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("categorie"));
        // Sans paramètre : tout, inactifs compris, et les catégories de chaque champ.
        String tout = ref("");
        assertThat(JsonPath.<List<Object>>read(tout, "$.champs[?(@.code=='B01-AC-01')].categories[*]"))
                .containsExactlyInAnyOrder("FOURNITURES_SERVICES", "TRAVAUX", "PRESTATIONS_INTELLECTUELLES");
        assertThat(JsonPath.<List<Object>>read(tout, "$.champs[?(@.code=='B04-RO-01')].categories[*]"))
                .containsExactly("FOURNITURES_SERVICES");
    }

    // ------------------------------------------------------------------ 3-6. la catégorie de la ligne

    @Test
    @DisplayName("3-6 — Éligibles : catégorie et catégorie outillée par ligne ; fournitures et prestations intellectuelles "
            + "préparables ; nature sans catégorie et ligne sans nature → 409 FORME_NON_OUTILLEE nommant ce qui manque ; "
            + "fiche d'une ligne sans catégorie : categorie nulle, typeOutille faux, écritures refusées")
    void categorieDeLaLigne() throws Exception {
        String eligibles = mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(eligibles, "$[?(@.idDetail==9901)].categorie")).containsExactly("FOURNITURES_SERVICES");
        assertThat(JsonPath.<List<Boolean>>read(eligibles, "$[?(@.idDetail==9901)].categorieOutillee")).containsExactly(true);
        assertThat(JsonPath.<List<String>>read(eligibles, "$[?(@.idDetail==9902)].categorie")).containsExactly("PRESTATIONS_INTELLECTUELLES");
        assertThat(JsonPath.<List<Boolean>>read(eligibles, "$[?(@.idDetail==9902)].categorieOutillee")).containsExactly(true);
        assertThat(JsonPath.<List<Object>>read(eligibles, "$[?(@.idDetail==9903)].categorie")).containsExactly((Object) null);
        assertThat(JsonPath.<List<Boolean>>read(eligibles, "$[?(@.idDetail==9903)].categorieOutillee")).containsExactly(false);
        assertThat(JsonPath.<List<Boolean>>read(eligibles, "$[?(@.idDetail==9904)].categorieOutillee")).containsExactly(false);

        String dmc = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long idFs = ((Number) JsonPath.read(dmc, "$.idDmc")).longValue();
        mvc.perform(get("/api/fiches-marche/" + idFs).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorie").value("FOURNITURES_SERVICES"))
                .andExpect(jsonPath("$.typeOutille").value(true));
        String dmcPi = mvc.perform(post("/api/dmcs/par-marche/9902").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/fiches-marche/" + ((Number) JsonPath.read(dmcPi, "$.idDmc")).longValue())
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorie").value("PRESTATIONS_INTELLECTUELLES"))
                .andExpect(jsonPath("$.typeOutille").value(true));

        mvc.perform(post("/api/dmcs/par-marche/9903").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FORME_NON_OUTILLEE"))
                .andExpect(jsonPath("$.message", containsString("à compléter par l'Administrateur")));
        mvc.perform(post("/api/dmcs/par-marche/9904").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("« Nature »")));

        // Un DMC sur la ligne sans catégorie, créé par l'Administrateur (geste sans garde H4) : lu, pas écrit.
        String dmcSans = mvc.perform(post("/api/dmcs/par-marche/9903").header("Authorization", tokenAdmin))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long idSans = ((Number) JsonPath.read(dmcSans, "$.idDmc")).longValue();
        mvc.perform(get("/api/fiches-marche/" + idSans).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorie").doesNotExist())
                .andExpect(jsonPath("$.typeOutille").value(false));
        mvc.perform(put("/api/fiches-marche/" + idSans + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"NON\"}}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FORME_NON_OUTILLEE"));
    }

    // ------------------------------------------------------------------ B5. les tranches

    @Test
    @DisplayName("B5 — « tranches » : question des travaux seulement — une fiche de fournitures la refuse en 400")
    void tranchesReserveesAuxTravaux() throws Exception {
        String dmc = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long idDmc = ((Number) JsonPath.read(dmc, "$.idDmc")).longValue();
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"tranches\":\"OUI\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("tranches"))
                .andExpect(jsonPath("$.erreurs[0].message", containsString("travaux")));
    }

    // ------------------------------------------------------------------ B1/B3. administration

    @Test
    @DisplayName("B1/B3 — Catégories d'un champ : colonne du CSV, PUT sans catégories = inchangées ; nature : catégorie "
            + "administrable, inconnue → 400, absente du PUT = inchangée")
    void administration() throws Exception {
        Path csv = Files.createTempFile("travaux", ".csv");
        Files.writeString(csv, "code;libelle;type;source;documentMaitre;typesMarche;categories\n"
                + "B02-AU-60;Lieu d'exécution des travaux;TEXTE;SAISIE;DPAO;QUANTITE_FIXE;TRAVAUX\n"
                + "B02-AU-61;Sans catégorie dans le fichier;TEXTE;SAISIE;DPAO;QUANTITE_FIXE;\n");
        ChampFicheMarcheService.BilanImport bilan = champService.importerCsv(csv);
        assertThat(bilan.rejets()).isEmpty();
        assertThat(champRepository.findById("B02-AU-60").orElseThrow().getCategories()).isEqualTo("TRAVAUX");
        assertThat(champRepository.findById("B02-AU-61").orElseThrow().getCategories()).isEqualTo("FOURNITURES_SERVICES");
        assertThat(champs("typeMarche=QUANTITE_FIXE&categorie=TRAVAUX")).contains("B02-AU-60").doesNotContain("B02-AU-61");

        String sansCategories = "{\"code\":\"B02-AU-60\",\"libelle\":\"Lieu d'exécution des travaux\",\"type\":\"TEXTE\","
                + "\"source\":\"SAISIE\",\"documentMaitre\":\"DPAO\"}";
        mvc.perform(put("/api/champs-fiche-marche/B02-AU-60").header("Authorization", tokenAdmin).contentType(JSON)
                .content(sansCategories))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categories[0]").value("TRAVAUX"));
        mvc.perform(put("/api/champs-fiche-marche/B02-AU-60").header("Authorization", tokenAdmin).contentType(JSON)
                .content(sansCategories.replace("}", ",\"categories\":[\"CHANTIER\"]}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("categories"));

        mvc.perform(put("/api/natures/93").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"idNature\":93,\"libelle\":\"Nature à classer\",\"categorieDao\":\"CHANTIER\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/natures/93").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"idNature\":93,\"libelle\":\"Nature à classer\",\"categorieDao\":\"FOURNITURES_SERVICES\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categorieDao").value("FOURNITURES_SERVICES"));
        mvc.perform(put("/api/natures/93").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"idNature\":93,\"libelle\":\"Nature classée\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categorieDao").value("FOURNITURES_SERVICES"));
        mvc.perform(post("/api/dmcs/par-marche/9903").header("Authorization", tokenPrmp)).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ FAR : les pièces ne se saisissent pas

    @Test
    @DisplayName("FAR — Un champ PIECE, même obligatoire, n'est ni attendu ni bloquant au bilan : il se joint au dossier")
    void piecesHorsBilan() throws Exception {
        String dmc = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long idDmc = ((Number) JsonPath.read(dmc, "$.idDmc")).longValue();
        String avant = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int attendusAvant = JsonPath.read(avant, "$.bilanControles.nbAttendus");

        Path csv = Files.createTempFile("far", ".csv");
        Files.writeString(csv, "code;libelle;type;source;documentMaitre;typesMarche;obligatoire\n"
                + "B02-AU-62;Modèle de garantie de soumission;PIECE;SAISIE;CCAP;QUANTITE_FIXE;oui\n");
        assertThat(champService.importerCsv(csv).rejets()).isEmpty();

        String apres = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(apres, "$.bilanControles.nbAttendus")).isEqualTo(attendusAvant);
        assertThat(JsonPath.<List<String>>read(apres, "$.bilanControles.bloquants[*].champs[*]")).doesNotContain("B02-AU-62");
    }

    // ------------------------------------------------------------------ outils

    private String ref(String requete) throws Exception {
        return mvc.perform(get("/api/champs-fiche-marche" + (requete.isEmpty() ? "" : "?" + requete))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private List<String> champs(String requete) throws Exception {
        return JsonPath.read(ref(requete), "$.champs[*].code");
    }

    private void ligne(int idDetail, Integer idNature) {
        Marche l = marche(idDetail, 9900, 9900);
        l.setIdMode(92);
        l.setFormeMarche(FormeMarche.QUANTITE_FIXE);
        l.setIdNature(idNature);
        marcheRepository.save(l);
    }

    // ------------------------------------------------------------------ 29/09 — la nature dans les lignes éligibles

    @Test
    @DisplayName("29/09 — GET /api/dmcs/eligibles sert idNature et libelleNature : une ligne « Services » rend « Services » "
            + "(catégorie fournitures et services, comme « Fournitures ») ; une ligne sans nature rend null (la clé étrangère "
            + "t_marche → t_nature exclut une nature inconnue)")
    void natureDesLignesEligibles() throws Exception {
        natureRepository.save(new Nature(94, "Services", null, "FOURNITURES_SERVICES"));
        ligne(9905, 94);
        String eligibles = mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(eligibles, "$[?(@.idDetail==9905)].libelleNature")).containsExactly("Services");
        assertThat(JsonPath.<List<Integer>>read(eligibles, "$[?(@.idDetail==9905)].idNature")).containsExactly(94);
        assertThat(JsonPath.<List<String>>read(eligibles, "$[?(@.idDetail==9905)].categorie")).containsExactly("FOURNITURES_SERVICES");
        assertThat(JsonPath.<List<String>>read(eligibles, "$[?(@.idDetail==9901)].libelleNature")).containsExactly("Fournitures");
        assertThat(JsonPath.<List<Object>>read(eligibles, "$[?(@.idDetail==9904)].libelleNature")).containsExactly((Object) null);
        assertThat(JsonPath.<List<Object>>read(eligibles, "$[?(@.idDetail==9904)].idNature")).containsExactly((Object) null);
    }

    // ------------------------------------------------------------------ 29/09 — champs non imprimés retirés

    @Autowired private cnm.prs.repository.FicheMarcheValeurRepository valeurRepository;

    /** Les 25 codes retirés de la fiche des fournitures (demande front du 2026-09-29, §B1). */
    private static final List<String> RETIRES = List.of("B02-AU-05", "B05-TP-02", "B08-PA-01", "B08-PA-02", "B03-NA-01",
            "B03-NA-02", "B03-ST-02", "B04-RO-03", "B06-EO-04", "B06-EO-05", "B06-EO-06", "B08-AC-01", "B08-AC-02", "B08-AV-03",
            "B08-AV-05", "B08-AV-06", "B08-PA-04", "B10-IR-02", "B02-AU-07", "B05-CP-03", "B06-EO-03", "B06-EO-07", "B06-EO-08",
            "B06-AN-02", "B09-DG-02");

    @Test
    @DisplayName("29/09 — Champs non imprimés : les 25 retirés ne sont plus servis aux fournitures (quantité fixe, à commande), "
            + "les 4 lus par une règle restent ; aucun ne sert ailleurs ; une valeur saisie avant le retrait survit à "
            + "l'enregistrement de son bloc et ne compte ni dans l'avancement ni au bilan")
    void champsNonImprimesRetires() throws Exception {
        assertThat(RETIRES).hasSize(25);
        for (String type : List.of("QUANTITE_FIXE", "A_COMMANDE")) {
            assertThat(champs("typeMarche=" + type + "&categorie=FOURNITURES_SERVICES")).as(type)
                    .doesNotContainAnyElementsOf(RETIRES).contains("B04-OP-02", "B04-OP-03")
                    .doesNotContain("B08-PA-08");   // ⚠️ §B6 (arbitrage du pilote, 29/09) : retiré à son tour
        }
        // ⚠️ §B6 — B05-TP-03 reste servi au marché à commande, mais facultatif, libellé « estimé » ;
        // B08-FP-03 (même avertissement DELAI_PAIEMENT_75) reste servi au contrat-cadre : FicheMarcheCommandeEtContratCadre.
        String commande = ref("typeMarche=A_COMMANDE&categorie=FOURNITURES_SERVICES");
        assertThat(JsonPath.<List<Boolean>>read(commande, "$.champs[?(@.code=='B05-TP-03')].obligatoire")).containsExactly(false);
        assertThat(JsonPath.<List<String>>read(commande, "$.champs[?(@.code=='B05-TP-03')].libelle"))
                .containsExactly("Montant maximum annuel estimé du marché (Ariary)");
        assertThat(champRepository.findById("B08-PA-08").orElseThrow().getActif()).isFalse();
        // Aucun des 25 n'appartient à une autre catégorie : « ailleurs » est inchangé (les champs y restent ce qu'ils étaient).
        for (String code : RETIRES) {
            cnm.prs.entity.ChampFicheMarche c = champRepository.findById(code).orElseThrow();
            assertThat(c.getActif()).as(code).isFalse();
            assertThat(c.getCategories()).as(code).isEqualTo("FOURNITURES_SERVICES");
        }

        // Une valeur saisie avant le retrait (B08-PA-04, obligatoire ; B08-PA-08 depuis le §B6) est conservée, hors
        // avancement et hors bilan.
        String dmc = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long idDmc = ((Number) JsonPath.read(dmc, "$.idDmc")).longValue();
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"cadrage\":{\"garantieSoumission\":\"NON\"}}"))
                .andExpect(status().isOk());
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int idFiche = JsonPath.read(fiche, "$.idFiche");
        valeurRepository.save(new cnm.prs.entity.FicheMarcheValeur(null, idFiche, "B08-PA-04", "30 jours fin de mois"));
        valeurRepository.save(new cnm.prs.entity.FicheMarcheValeur(null, idFiche, "B08-PA-08", "45"));
        String apres = mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B08").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"valeurs\":{}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<java.util.Map<String, String>>read(apres, "$.valeurs"))
                .containsEntry("B08-PA-04", "30 jours fin de mois").containsEntry("B08-PA-08", "45");
        assertThat(JsonPath.<List<String>>read(apres, "$.bilanControles.bloquants[*].champs[*]"))
                .doesNotContainAnyElementsOf(RETIRES);
        assertThat(JsonPath.<List<String>>read(apres, "$.bilanControles.ok[*].champs[*]")).doesNotContain("B08-PA-04", "B08-PA-08");
        // un champ retiré ne se saisit plus
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B08").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"valeurs\":{\"B08-PA-04\":\"60 jours\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("B08-PA-04"));
    }
}
