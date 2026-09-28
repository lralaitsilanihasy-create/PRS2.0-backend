package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.service.ChampFicheMarcheService;
import cnm.prs.service.ImportDaoService;

/**
 * ⚠️ <strong>Import du DAO</strong> (demande front du 2026-09-28, §B1 à §B4 ; ADR-0012) — la lecture « par modèle
 * inversé » d'un DAO Word pour pré-remplir la fiche marché, puis l'écriture de ce que la PRMP a retenu.
 *
 * <p>Jeu : plan 9900 (PRMP001, CLOTURE, PV signé), lignes en appel d'offres ouvert : 9911 à quantité fixe, 9913 et 9914
 * contrat-cadre (fournitures et services). La fiche de 9913 est remplie et validée : ses DPAC et AE sont le DAO lu ; celle de
 * 9914 est vierge et le reçoit.</p>
 */
class ImportDaoIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String CADRAGE_SOURCE = "{\"alloti\":\"NON\",\"groupement\":\"NON\",\"avance\":\"NON\","
            + "\"typePrix\":\"UNITAIRES\",\"attributaires\":\"MULTI\"}";

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
        ligne(9911, FormeMarche.QUANTITE_FIXE);
        ligne(9913, FormeMarche.CONTRAT_CADRE);
        ligne(9914, FormeMarche.CONTRAT_CADRE);
        importer("referentiel-champs-fiche-marche-fournitures.csv");
        importer("referentiel-champs-fiche-marche-contrat-cadre.csv");
    }

    // ------------------------------------------------------------------ B4 — aller-retour

    @Test
    @DisplayName("B4 aller-retour — DPAC et AE d'une fiche de contrat-cadre validée, en un seul fichier, lus pour une fiche vierge : "
            + "les propositions redonnent les valeurs saisies, aucune fausse en haute ni en moyenne ; le cadrage déduit redonne "
            + "celui de la fiche ; B07-DE-02 / B07-DE-03 ambigus ; les reprises du plan jamais proposées (divergence)")
    void allerRetour() throws Exception {
        Source s = sourceValidee();
        Long cible = creerDmc(9914);
        byte[] dao = docx(concatener(s.dpac(), s.ae()));
        String r = importer(cible, "DAO-contrat-cadre.docx", dao).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(r, "$.fichier")).isEqualTo("DAO-contrat-cadre.docx");
        assertThat(JsonPath.<String>read(r, "$.empreinte")).matches("[0-9a-f]{64}");
        assertThat(JsonPath.<List<String>>read(r, "$.modeles[*].sigle")).containsExactly("DPAC-CC", "AE-CC");
        assertThat(JsonPath.<List<Integer>>read(r, "$.modeles[*].reconnues")).allMatch(n -> n > 80);
        assertThat(JsonPath.<List<String>>read(r, "$.avertissements")).isEmpty();

        // Les valeurs : aucune fausse en confiance haute ni moyenne ; les valeurs typées reviennent dans la forme de saisie.
        List<Map<String, Object>> propositions = JsonPath.read(r, "$.propositions");
        int justes = 0;
        for (Map<String, Object> p : propositions) {
            String code = (String) p.get("code");
            String confiance = (String) p.get("confiance");
            assertThat(p.get("actuelle")).as(code).isNull();   // fiche vierge
            String saisie = s.valeurs().get(code);
            if (saisie != null && !"basse".equals(confiance)) {
                assertThat(p.get("valeur")).as(code + " (" + confiance + ")").isEqualTo(saisie);
                justes++;
            }
            assertThat(code).as("jamais une reprise du plan").isNotIn(s.ppm().keySet());
        }
        assertThat(justes).isGreaterThanOrEqualTo(40);
        Map<String, String> lues = new LinkedHashMap<>();
        propositions.forEach(p -> lues.put((String) p.get("code"), (String) p.get("valeur")));
        assertThat(lues).containsEntry("B04-CP-02", "2026-04-10T10:00").containsEntry("B05-MT-01", "250000000")
                .containsEntry("B04-CP-01", "2026-03-02").containsEntry("B04-DS-07", "Monsieur RAKOTO Jean")
                .containsEntry("B07-FS-01", "Marchés uniques non fractionnés");
        assertThat(JsonPath.<List<String>>read(r, "$.propositions[?(@.code=='B04-CP-02')].confiance")).containsExactly("haute");
        assertThat(JsonPath.<List<List<String>>>read(r, "$.propositions[?(@.code=='B04-CP-02')].anomalies").get(0)).isEmpty();

        // Le cadrage : chaque réponse déduite est celle de la fiche source.
        List<Map<String, Object>> cadrage = JsonPath.read(r, "$.cadrage");
        Map<String, Object> source = JsonPath.read(s.fiche(), "$.cadrage");
        assertThat(cadrage).extracting(c -> c.get("cle")).contains("attributaires", "alloti", "typePrix");
        for (Map<String, Object> c : cadrage) {
            // modeRemise absent de la fiche vaut PAPIER (V50) : la rédaction « papier » le dit explicitement
            Object attendu = "modeRemise".equals(c.get("cle")) ? source.getOrDefault("modeRemise", "PAPIER") : source.get(c.get("cle"));
            assertThat(String.valueOf(c.get("valeur"))).as((String) c.get("cle")).isEqualTo(String.valueOf(attendu));
            assertThat(c.get("section")).as((String) c.get("cle")).isNotNull();
        }

        // B07-DE-02 / B07-DE-03 : même intervalle, rien n'imprime lequel — signalé, jamais choisi.
        assertThat(JsonPath.<List<List<String>>>read(r, "$.ambigus[*].candidats")).contains(List.of("B07-DE-02", "B07-DE-03"));
        assertThat(lues).doesNotContainKeys("B07-DE-02", "B07-DE-03");
        // L'objet du plan de la ligne 9913 n'est pas celui de 9914 : divergence, le plan fait foi.
        assertThat(JsonPath.<List<String>>read(r, "$.divergences[?(@.code=='B02-OB-01')].plan")).containsExactly("Marché 9914");

        // Le DPAC tel que produit (tableau du calendrier compris) se lit aussi seul.
        String seul = importer(cible, "DPAC.docx", s.dpac()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(seul, "$.propositions[?(@.code=='B04-CP-05')].valeur")).containsExactly("2026-05-11");
    }

    // ------------------------------------------------------------------ B4 — fusion et ajout

    @Test
    @DisplayName("B4 fusion et ajout — « Nom du Responsable : X Fonction : Y » collés → B04-DS-07 = X en moyenne, B04-DS-08 = Y "
            + "relu ; un paragraphe étranger avant un jeton seul → basse, jamais haute ; aucune valeur fausse en haute")
    void fusionEtAjout() throws Exception {
        Source s = sourceValidee();
        Long cible = creerDmc(9914);
        List<String> unites = concatener(s.dpac(), s.ae());
        List<String> bruites = new ArrayList<>();
        for (int i = 0; i < unites.size(); i++) {
            String u = unites.get(i);
            if (u.startsWith("Nom du Responsable :") && i + 1 < unites.size() && unites.get(i + 1).startsWith("Fonction :")) {
                bruites.add(u + " " + unites.get(++i));   // deux paragraphes collés
            } else if (u.equals(s.valeurs().get("B04-PO-01"))) {
                bruites.add("Paragraphe ajouté par l'autorité contractante, étranger au document type.");
                bruites.add(u);
            } else {
                bruites.add(u);
            }
        }
        assertThat(bruites).hasSize(unites.size());   // une fusion, un ajout
        String r = importer(cible, "DAO-bruite.docx", docx(bruites)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(r, "$.propositions[?(@.code=='B04-DS-07')].valeur")).containsExactly("Monsieur RAKOTO Jean");
        assertThat(JsonPath.<List<String>>read(r, "$.propositions[?(@.code=='B04-DS-07')].confiance")).containsExactly("moyenne");
        assertThat(JsonPath.<List<String>>read(r, "$.propositions[?(@.code=='B04-DS-08')].valeur"))
                .containsExactly("Personne Responsable des Marchés Publics");
        assertThat(JsonPath.<List<String>>read(r, "$.propositions[?(@.code=='B04-PO-01')].confiance")).containsExactly("basse");
        List<Map<String, Object>> hautes = JsonPath.read(r, "$.propositions[?(@.confiance=='haute')]");
        assertThat(hautes).isNotEmpty();
        for (Map<String, Object> p : hautes) {
            String saisie = s.valeurs().get((String) p.get("code"));
            if (saisie != null) {
                assertThat(p.get("valeur")).as((String) p.get("code")).isEqualTo(saisie);
            }
        }
    }

    // ------------------------------------------------------------------ B4 — refus

    @Test
    @DisplayName("B4 refus — .pdf et .docm → 415 FORMAT_NON_SUPPORTE ; fiche validée → 409 FICHE_VALIDEE ; quantité fixe → 422 "
            + "MODELE_ABSENT ; Administrateur → 403 ; document hors gabarit → 200, avertissement et presque rien de proposé")
    void refus() throws Exception {
        Long cible = creerDmc(9914);
        importer(cible, "DAO.pdf", "%PDF-1.4".getBytes()).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FORMAT_NON_SUPPORTE"))
                .andExpect(jsonPath("$.message").value("Seul un fichier Word (.docx) peut être importé pour l'instant."));
        importer(cible, "DAO.docm", docx(List.of("Texte"))).andExpect(status().isUnsupportedMediaType());
        importer(cible, "faux.docx", "pas une archive".getBytes()).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FORMAT_NON_SUPPORTE"));
        mvc.perform(multipart("/api/fiches-marche/" + cible + "/import").file(fichier("DAO.docx", docx(List.of("x"))))
                .header("Authorization", tokenAdmin)).andExpect(status().isForbidden());

        Long qf = creerDmc(9911);
        importer(qf, "DAO.docx", docx(List.of("Texte"))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MODELE_ABSENT"))
                .andExpect(jsonPath("$.message").value("L'import n'est pas encore possible pour ce type de marché : saisissez la fiche."));

        String r = importer(cible, "compte-rendu.docx", docx(List.of("Compte rendu de la réunion du comité de pilotage.",
                "Ordre du jour : budget 2027, recrutements, questions diverses.", "La séance est levée à 17 heures.")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(r, "$.avertissements")).hasSize(2)
                .allMatch(a -> a.contains("ce document ne suit pas le document type"));
        assertThat(JsonPath.<List<Object>>read(r, "$.propositions")).hasSizeLessThanOrEqualTo(2);

        Source s = sourceValidee();
        importer(s.idDmc(), "DAO.docx", s.dpac()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FICHE_VALIDEE"));
        mvc.perform(put("/api/fiches-marche/" + s.idDmc() + "/import/appliquer").header("Authorization", tokenPrmp)
                .contentType(JSON).content(appliquer("{}", "{\"B04-VO-01\":60}")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_VALIDEE"));
    }

    // ------------------------------------------------------------------ B4 — appliquer

    @Test
    @DisplayName("B4 appliquer — fusion (une valeur non envoyée garde la sienne, le cadrage garde ses autres clés) ; un refus → "
            + "400 nominatif et rien d'écrit ; journal FICHE_IMPORTEE ; champ repris du plan ou fermé par le cadrage → 400")
    void appliquer() throws Exception {
        Long cible = creerDmc(9914);
        cadrage(cible, "{\"groupement\":\"OUI\"}");
        mvc.perform(put("/api/fiches-marche/" + cible + "/blocs/B04").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B04-VO-01\":60}}")).andExpect(status().isOk());

        // Un refus : rien n'est écrit, ni la valeur juste ni le cadrage.
        mvc.perform(put("/api/fiches-marche/" + cible + "/import/appliquer").header("Authorization", tokenPrmp)
                .contentType(JSON).content(appliquer("{\"attributaires\":\"MULTI\"}",
                        "{\"B04-CP-02\":\"2026-11-20T10:00\",\"B05-MT-01\":\"beaucoup\",\"B02-OB-01\":\"Autre objet\","
                                + "\"B07-MA-01\":3}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='B05-MT-01')]").exists())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='B02-OB-01')]").exists())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='B07-MA-01')]").exists())   // attributaires = MONO seulement
                .andExpect(jsonPath("$.erreurs[?(@.champ=='B04-CP-02')]").doesNotExist());
        String avant = lire(cible);
        assertThat(JsonPath.<Map<String, Object>>read(avant, "$.valeurs")).doesNotContainKey("B04-CP-02");
        assertThat(JsonPath.<Map<String, Object>>read(avant, "$.cadrage")).doesNotContainKey("attributaires");
        mvc.perform(put("/api/fiches-marche/" + cible + "/import/appliquer").header("Authorization", tokenPrmp)
                .contentType(JSON).content("{\"valeurs\":{\"B04-VO-01\":90},\"fichier\":\"\",\"empreinte\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='fichier')]").exists())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='empreinte')]").exists());

        // Accepté : fusion.
        String apres = mvc.perform(put("/api/fiches-marche/" + cible + "/import/appliquer").header("Authorization", tokenPrmp)
                .contentType(JSON).content(appliquer("{\"attributaires\":\"MULTI\",\"alloti\":\"NON\"}",
                        "{\"B04-CP-02\":\"2026-11-20T10:00\",\"B05-MT-01\":\"250 000 000\",\"B07-MA-06\":\"Critères pondérés\"}")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Map<String, Object>>read(apres, "$.valeurs")).containsEntry("B04-VO-01", "60")
                .containsEntry("B04-CP-02", "2026-11-20T10:00").containsEntry("B05-MT-01", "250000000")
                .containsEntry("B07-MA-06", "Critères pondérés");
        assertThat(JsonPath.<Map<String, Object>>read(apres, "$.cadrage")).containsEntry("groupement", "OUI")
                .containsEntry("attributaires", "MULTI").containsEntry("alloti", "NON");
        assertThat(JsonPath.<String>read(apres, "$.statut")).isEqualTo("BROUILLON");

        String journal = mvc.perform(get("/api/dossiers/9900/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='FICHE_IMPORTEE')].detail"))
                .containsExactly("fiche pré-remplie par import de DAO-contrat-cadre.docx (0123456789ab) : 3 valeurs, "
                        + "2 réponses de cadrage");
    }

    @Test
    @DisplayName("B2 — une fiche jamais enregistrée : l'application la crée (défauts recopiés) et y écrit l'import")
    void appliquerSurFicheVirtuelle() throws Exception {
        Long cible = creerDmc(9914);
        String apres = mvc.perform(put("/api/fiches-marche/" + cible + "/import/appliquer").header("Authorization", tokenPrmp)
                .contentType(JSON).content(appliquer("{\"attributaires\":\"MONO\"}", "{\"B07-MA-01\":3}")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Map<String, Object>>read(apres, "$.valeurs")).containsEntry("B07-MA-01", "3");
        assertThat(JsonPath.<Integer>read(apres, "$.version")).isEqualTo(1);
    }

    // ------------------------------------------------------------------ outils

    private record Source(Long idDmc, String fiche, Map<String, String> valeurs, Map<String, String> ppm, byte[] dpac, byte[] ae) {
    }

    /** La fiche de la ligne 9913 remplie (valeurs choisies + obligatoires) et validée ; ses DPAC et AE .docx. */
    private Source sourceValidee() throws Exception {
        Long idDmc = creerDmc(9913);
        cadrage(idDmc, CADRAGE_SOURCE);
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
        donnees.put("B04-DS-07", "Monsieur RAKOTO Jean");
        donnees.put("B04-DS-08", "Personne Responsable des Marchés Publics");
        donnees.put("B04-PO-01", "Attestations fiscales et sociales, références de livraisons similaires.");
        donnees.put("B07-FS-01", "Marchés uniques non fractionnés");
        donnees.put("B07-DE-01", "Fixés par l'autorité contractante");
        donnees.put("B07-DE-02", "Le délai de livraison est de trente jours à compter de la notification.");
        donnees.put("B07-MA-06", "Prix (60 %) et délai (40 %).");
        remplirObligatoiresEtValider(idDmc, "CONTRAT_CADRE", "FOURNITURES_SERVICES", donnees);
        String fiche = lire(idDmc);
        assertThat(JsonPath.<String>read(fiche, "$.statut")).isEqualTo("VALIDEE");
        String docs = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Source(idDmc, fiche, JsonPath.read(fiche, "$.valeurs"), JsonPath.read(fiche, "$.valeursPpm"),
                contenu(docs, "DPAC"), contenu(docs, "AE"));
    }

    private byte[] contenu(String docs, String type) throws Exception {
        int id = JsonPath.<List<Integer>>read(docs, "$[?(@.type=='" + type + "' && @.extension=='docx')].idDocument").get(0);
        return mvc.perform(get("/api/fiches-marche/documents/" + id + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    /** DPAC et AE « bout à bout » : leurs unités de lecture, dans l'ordre. */
    private static List<String> concatener(byte[] dpac, byte[] ae) {
        List<String> u = new ArrayList<>(ImportDaoService.paragraphes("DPAC.docx", dpac));
        u.addAll(ImportDaoService.paragraphes("AE.docx", ae));
        return u;
    }

    /** Un .docx d'un paragraphe par unité. */
    private static byte[] docx(List<String> paragraphes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String p : paragraphes) {
                doc.createParagraph().createRun().setText(p);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static MockMultipartFile fichier(String nom, byte[] contenu) {
        return new MockMultipartFile("fichier", nom, nom.endsWith(".pdf") ? "application/pdf" : DOCX, contenu);
    }

    private ResultActions importer(Long idDmc, String nom, byte[] contenu) throws Exception {
        return mvc.perform(multipart("/api/fiches-marche/" + idDmc + "/import").file(fichier(nom, contenu))
                .header("Authorization", tokenPrmp));
    }

    private static String appliquer(String cadrage, String valeurs) {
        return "{\"cadrage\":" + cadrage + ",\"valeurs\":" + valeurs + ",\"fichier\":\"DAO-contrat-cadre.docx\","
                + "\"empreinte\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"}";
    }

    private String lire(Long idDmc) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ChampFicheMarcheService.BilanImport importer(String fichier) throws Exception {
        Path chemin = new ClassPathResource("fiche-marche/" + fichier).getFile().toPath();
        return champService.importerCsv(chemin);
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
