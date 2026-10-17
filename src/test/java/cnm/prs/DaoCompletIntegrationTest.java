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
    @Autowired private cnm.prs.service.PiecesExemptees piecesExemptees;
    @Autowired private cnm.prs.repository.PieceJointeDossierRepository pieceJointeDossierRepository;
    @Autowired private cnm.prs.repository.SpecificationsFicheRepository specificationsRepository;

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
    @DisplayName("⚠️ 17/10 (« specifications-donnees-et-plans-pdf », A2-A3) — fournitures : plus de fichier de spécifications (409 "
            + "SPECIFICATIONS_HORS_PERIMETRE) ; sans article, avertissement qui demande le besoin ; avec, une information sans Word ; les "
            + "prescriptions générales (B12-BE-01) se saisissent au B12")
    void specifications() throws Exception {
        Long idDmc = creerDmc();
        String url = "/api/fiches-marche/" + idDmc + "/specifications";
        mvc.perform(get(url).header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        assertThat(JsonPath.<List<String>>read(fiche(idDmc), "$.bilanControles.avertissements[?(@.regle=='SPECIFICATIONS_ABSENTES')].message"))
                .singleElement().asString().contains("Saisissez le besoin").doesNotContain("Word");
        deposer(url, "specs.docx", docx("Devis descriptif")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPECIFICATIONS_HORS_PERIMETRE"));
        besoinDeTest(idDmc);
        assertThat(JsonPath.<List<String>>read(fiche(idDmc), "$.bilanControles.avertissements[*].regle")).doesNotContain("SPECIFICATIONS_ABSENTES");
        assertThat(JsonPath.<List<String>>read(fiche(idDmc), "$.bilanControles.ok[?(@.regle=='SPECIFICATIONS_DU_BESOIN')].message"))
                .containsExactly("Le DAO complet portera le tableau des spécifications tiré du besoin.");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B12").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B12-BE-01\":\"Emballage d'origine scellé.\\nNotice en français.\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valeurs.B12-BE-01").value("Emballage d'origine scellé.\nNotice en français."));

        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", new LinkedHashMap<>());
        String documents = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(documents, "$[*].type")).doesNotContain("DAO_COMPLET", "DESCRIPTIF_PLANS").contains("DPAO", "AE");
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
        assertThat(JsonPath.<List<String>>read(documents, "$[?(@.extension!='xlsx' && @.type!='BORDEREAU_ESTIMATIF')].type")).containsOnly("DAO_COMPLET").hasSize(2);   // ⚠️ V117 : l'estimatif est listé à part du DAO
        assertThat(JsonPath.<List<String>>read(documents, "$[?(@.type=='DAO_COMPLET')].libelle")).containsOnly("Dossier d'appel d'offres complet");
        // ⚠️ C6 (recette du 06/10) — le DAO complet en tête, avant les classeurs.
        assertThat(JsonPath.<List<String>>read(documents, "$[*].type")).startsWith("DAO_COMPLET", "DAO_COMPLET");
    }

    @Test
    @DisplayName("⚠️ E2 (07/10) : un dossier qui porte le DAO complet n'a plus à joindre le CCAG, ni le CCTP quand la fiche a ses "
            + "spécifications techniques (⚠️ 17/10 : en fournitures, un besoin à caractéristiques) ; sans DAO complet, rien n'est exempté")
    void ccagEtCctpDansLeDaoComplet() throws Exception {
        Long idDmc = creerDmc();
        besoinDeTest(idDmc);   // ⚠️ 17/10 — en fournitures, les spécifications sont le besoin (articles à caractéristiques), plus un Word
        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", new LinkedHashMap<>());
        int idFiche = JsonPath.read(fiche(idDmc), "$.idFiche");
        int ccag = seedTypePiece("Cahier des clauses administratives générales", true, "DMC", 7);
        int cctp = seedTypePiece("Cahier des clauses techniques particulières", true, "DMC", 8);
        for (int[] t : new int[][] { { ccag, 0 }, { cctp, 1 } }) {
            cnm.prs.entity.TypePieceJointe tp = typePieceJointeRepository.findById(t[0]).orElseThrow();
            tp.setCode(t[1] == 0 ? "CCAG" : "CCTP");
            typePieceJointeRepository.save(tp);
        }
        dossierRepository.save(dossierLoc(9910, "BROUILLON", "ANT", "PRMP001"));
        assertThat(piecesExemptees.de(9910)).isEmpty();
        DocumentFicheMarche dao = documentRepository.save(new DocumentFicheMarche(null, idFiche, "DAO_COMPLET", "pdf", "DAO_COMPLET_test_v1.pdf", 4L,
                "0".repeat(64), LocalDateTime.now(), "%PDF".getBytes(), null, null));
        cnm.prs.entity.PieceJointeDossier p = new cnm.prs.entity.PieceJointeDossier();
        p.setIdDossier(9910);
        p.setIdTypePiece(seedTypePiece("Dossier d'appel d'offres complet", true, "DMC", 6));
        p.setNomFichier(dao.getNomFichier());
        p.setFormat("PDF");
        p.setApresLettreRenvoi(false);
        p.setIdDocumentFiche(dao.getIdDocument());
        pieceJointeDossierRepository.save(p);
        assertThat(piecesExemptees.de(9910)).containsExactlyInAnyOrder(ccag, cctp);
        entityManager.flush();
        jdbcTemplate.update("DELETE FROM t_fiche_caracteristique WHERE \"ID_ARTICLE\" IN (SELECT \"ID_ARTICLE\" FROM t_fiche_article WHERE \"ID_FICHE\" = ?)", idFiche);
        entityManager.clear();
        assertThat(piecesExemptees.de(9910)).containsExactly(ccag);
    }

    @Test
    @DisplayName("⚠️ 17/10 (« specifications-donnees-et-plans-pdf », B1) — la limite de 50 Mo ne vaut que pour la route des spécifications : "
            + "ailleurs, un fichier de plus de 20 Mo reste refusé (400 « Fichier trop volumineux »)")
    void limiteDeTailleAilleurs() throws Exception {
        Long idDmc = creerDmc();
        byte[] gros = new byte[20 * 1024 * 1024 + 1];
        mvc.perform(multipart("/api/fiches-marche/" + idDmc + "/import").file(new MockMultipartFile("fichier", "DAO.pdf", "application/pdf", gros))
                .header("Authorization", tokenPrmp)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Fichier trop volumineux : la taille maximale autorisée est dépassée."));
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
