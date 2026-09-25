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
    @DisplayName("1-2 — Référentiel : quantité fixe + fournitures et services = ses 144 champs (139 avant le 2026-09-25 ; avec ou sans le "
            + "filtre) ; travaux : aucun champ saisi ni rubrique des fournitures, seules les 23 informations du plan ; "
            + "catégorie inconnue → 400")
    void referentielSurDeuxAxes() throws Exception {
        assertThat(champs("typeMarche=QUANTITE_FIXE&categorie=FOURNITURES_SERVICES")).hasSize(144);
        assertThat(champs("typeMarche=QUANTITE_FIXE")).hasSize(144);

        String travaux = ref("typeMarche=QUANTITE_FIXE&categorie=TRAVAUX");
        assertThat(JsonPath.<List<String>>read(travaux, "$.champs[*].source")).hasSize(23).containsOnly("PPM");
        // Rubriques : celles du plan, plus celles des travaux (V41) encore sans champ dans ce jeu — servies « à compléter » ;
        // aucune des fournitures.
        assertThat(JsonPath.<List<String>>read(travaux, "$.blocs[*].rubriques[*].code"))
                .contains("B01-AC", "B02-OB", "B02-LV", "B02-LT").doesNotContain("B02-AU", "B04-RO", "B05-GS");
        assertThat(JsonPath.<List<String>>read(ref("categorie=PRESTATIONS_INTELLECTUELLES"), "$.champs[*].source"))
                .containsOnly("PPM");

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
}
