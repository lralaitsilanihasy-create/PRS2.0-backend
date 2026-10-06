package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.service.ChampFicheMarcheService;

/**
 * ⚠️ 2026-10-06 (demande front « le DAO complet », §B1-B3 ; V73) — sans Word (tests : {@code app.dao-complet.actif=false}) : les
 * spécifications techniques (dépôt .docx, gardes, avertissement, recopie à la révision) ; la liste des documents qui préfère le DAO
 * complet quand il existe (les classeurs restent). L'assemblage par Word a son test marqué {@code word} ({@code DaoCompletWordTest}).
 */
class DaoCompletIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheService champService;
    @Autowired private DocumentFicheMarcheRepository documentRepository;

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
        Marche l = marche(9901, 9900, 9900);
        l.setIdMode(92);
        l.setIdNature(natureFournitures());
        l.setFormeMarche(FormeMarche.QUANTITE_FIXE);
        l.setDesignationMarche("Acquisition de matériels informatiques");
        marcheRepository.save(l);
        for (String f : List.of("referentiel-champs-fiche-marche-fournitures.csv", "referentiel-champs-fiche-dao-travaux.csv")) {
            assertThat(champService.importerCsv(new ClassPathResource("fiche-marche/" + f).getFile().toPath()).rejets()).isEmpty();
        }
    }

    @Test
    @DisplayName("Spécifications techniques : .docx seul (type lu sur le contenu), avertissement tant qu'il manque, écrit sur le "
            + "brouillon (409 une fois validée), recopié à la révision ; sans Word, les documents séparés restent servis")
    void specifications() throws Exception {
        Long idDmc = creerDmc();
        String url = "/api/fiches-marche/" + idDmc + "/specifications";
        mvc.perform(get(url).header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        assertThat(JsonPath.<List<String>>read(fiche(idDmc), "$.bilanControles.avertissements[*].regle")).contains("SPECIFICATIONS_ABSENTES");
        deposer(url, "notes.docx", "bonjour".getBytes()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FORMAT_INVALIDE"));
        deposer(url, "specs.docx", docx("Devis descriptif")).andExpect(status().isOk())
                .andExpect(jsonPath("$.nomFichier").value("specs.docx")).andExpect(jsonPath("$.deposePar").isNotEmpty());
        mvc.perform(get(url).header("Authorization", tokenPrmp)).andExpect(status().isOk()).andExpect(jsonPath("$.taille").isNumber());
        byte[] f = mvc.perform(get(url + "/fichier").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(f, 0, 2)).isEqualTo("PK");
        assertThat(JsonPath.<List<String>>read(fiche(idDmc), "$.bilanControles.avertissements[*].regle")).doesNotContain("SPECIFICATIONS_ABSENTES");

        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", new LinkedHashMap<>());
        deposer(url, "specs.docx", docx("Autre")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_VALIDEE"));
        mvc.perform(delete(url).header("Authorization", tokenPrmp)).andExpect(status().isConflict());
        String documents = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(documents, "$[*].type")).doesNotContain("DAO_COMPLET").contains("DPAO", "AE");

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(get(url).header("Authorization", tokenPrmp)).andExpect(status().isOk()).andExpect(jsonPath("$.nomFichier").value("specs.docx"));
        mvc.perform(delete(url).header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        mvc.perform(get(url).header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Le DAO complet, quand il existe, remplace les documents séparés dans la liste (titre « Dossier d'appel d'offres "
            + "complet »), les classeurs restent")
    void listePrefereLeDaoComplet() throws Exception {
        Long idDmc = creerDmc();
        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", new LinkedHashMap<>());
        int idFiche = JsonPath.read(fiche(idDmc), "$.idFiche");
        for (String ext : List.of("docx", "pdf")) {
            documentRepository.save(new DocumentFicheMarche(null, idFiche, "DAO_COMPLET", ext, "DAO_COMPLET_test_v1." + ext, 4L, "0".repeat(64),
                    LocalDateTime.now(), "%PDF".getBytes(), null, null));
        }
        String documents = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(documents, "$[?(@.extension!='xlsx')].type")).containsOnly("DAO_COMPLET").hasSize(2);
        assertThat(JsonPath.<List<String>>read(documents, "$[?(@.type=='DAO_COMPLET')].libelle")).containsOnly("Dossier d'appel d'offres complet");
    }

    private ResultActions deposer(String url, String nom, byte[] contenu) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, url).file(new MockMultipartFile("fichier", nom,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", contenu)).header("Authorization", tokenPrmp));
    }

    static byte[] docx(String texte) throws Exception {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            d.createParagraph().createRun().setText(texte);
            d.write(o);
            return o.toByteArray();
        }
    }

    private String fiche(Long idDmc) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Long creerDmc() throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long idDmc = ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"alloti\":\"NON\",\"variantes\":\"NON\",\"groupement\":\"NON\",\"provenance\":\"NATIONAL\","
                        + "\"typePrix\":\"UNITAIRES\",\"prixRevisable\":\"NON\",\"garantieSoumission\":\"NON\",\"avance\":\"NON\","
                        + "\"penalites\":\"CCAG\"}}"))
                .andExpect(status().isOk());
        return idDmc;
    }
}
