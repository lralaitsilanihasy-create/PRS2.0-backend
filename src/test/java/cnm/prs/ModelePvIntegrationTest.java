package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.VersionModelePv;
import cnm.prs.repository.AuditLogRepository;
import cnm.prs.repository.VersionModelePvRepository;
import cnm.prs.service.ModelePvPageTest;
import cnm.prs.service.ModelePvWordTest;
import tools.jackson.databind.node.ObjectNode;

/**
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », lot G1 ; V138) — les routes de l'Administrateur : création, version et
 * rapport de contrôle, conditions et champs (rapport recalculé), aperçu sur un dossier réel sans rien enregistrer, mise en vigueur,
 * rattachements, duplication, archivage, journal.
 *
 * <p>⚠️ 2026-10-21 (« modele-pv-page », lots P1-P2 ; V139) — une version est une page : document JSON validé (400
 * {@code DOCUMENT_INVALIDE}), contrôlé sur ses nœuds (lieu et chemin), modifié en brouillon, aperçu et essai en PDF par le même rendu ;
 * le dépôt d'un Word est retiré, une version Word existante reste en consultation (409 {@code SUPPORT_ABANDONNE}) ; images.</p>
 */
class ModelePvIntegrationTest extends CnmIntegrationTestSupport {

    private static final String BASE = "/api/modeles-pv";

    @Autowired
    private VersionModelePvRepository versionsModele;

    @Autowired
    private AuditLogRepository auditLogs;

    /** Un PV minimal en page : identification, avis par blocs, observations répétées, champ saisi, signatures. */
    private static String modelePv(String codeReference) {
        ObjectNode doc = ModelePvPageTest.document(ModelePvPageTest.paragraphe("{{COMMISSION.NOM|majuscules}}"),
                ModelePvPageTest.paragraphe("PV n° {{" + codeReference + "}} — dossier {{DOSSIER.REFERENCE}}"),
                ModelePvPageTest.paragraphe("La Commission émet un avis {{PV.AVIS}}{{SI:AVEC_RESERVES}}, sous les réserves de l'annexe"
                        + "{{FINSI:AVEC_RESERVES}}."),
                ModelePvPageTest.tableau(List.of("Point", "Au lieu de", "Lire"), "OBSERVATIONS",
                        List.of("{{OBS.POINT}}", "{{OBS.AU_LIEU_DE}}", "{{OBS.LIRE}}")),
                ModelePvPageTest.paragraphe("Observation : {{SAISI:observation}}"),
                ModelePvPageTest.paragraphe("Le membre : {{PV.MEMBRE}}"));
        return "{\"document\":" + doc + "}";
    }

    private long creer(String code) throws Exception {
        String r = mvc.perform(post(BASE).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"libelle\":\"PV d'examen du dossier de marché\",\"nature\":\"PV\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    private String enregistrer(long id, String corps) throws Exception {
        return mvc.perform(post(BASE + "/" + id + "/versions").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(corps)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String texte(byte[] pdf) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            return ModelePvPageTest.trie().getText(doc).replaceAll("\\s+", " ");
        }
    }

    private int pages(byte[] pdf) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    @Test
    void reserveALAdministrateur() throws Exception {
        mvc.perform(get(BASE).header("Authorization", tokenMembre)).andExpect(status().isForbidden());
        mvc.perform(get(BASE).header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
        mvc.perform(get(BASE).header("Authorization", tokenAdmin)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/images/embleme").header("Authorization", tokenMembre)).andExpect(status().isForbidden());
    }

    @Test
    void delaPageALaMiseEnVigueur() throws Exception {
        long id = creer("PV-MARCHE");
        mvc.perform(post(BASE).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"PV-MARCHE\",\"libelle\":\"x\",\"nature\":\"PV\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CODE_DEJA_PRIS"));

        // Version 1 : une variable mal orthographiée, un bloc et un champ non déclarés → bloquant, avec la suggestion, le lieu et le
        // chemin du nœud.
        String v1 = enregistrer(id, modelePv("PV.REFERENCES"));
        assertThat(JsonPath.<Integer>read(v1, "$.numero")).isEqualTo(1);
        assertThat(JsonPath.<String>read(v1, "$.support")).isEqualTo("PAGE");
        assertThat(JsonPath.<String>read(v1, "$.document.corps.type")).isEqualTo("doc");
        assertThat(JsonPath.<Boolean>read(v1, "$.bloquant")).isTrue();
        assertThat(JsonPath.<List<String>>read(v1, "$.rapport.points[?(@.niveau=='BLOQUANT')].code"))
                .contains("VARIABLE_INCONNUE", "CONDITION_NON_DECLAREE", "CHAMP_NON_DECLARE", "SECTION_ABSENTE");
        assertThat(JsonPath.<List<String>>read(v1, "$.rapport.points[?(@.code=='VARIABLE_INCONNUE')].suggestion"))
                .containsExactly("PV.REFERENCE");
        assertThat(JsonPath.<List<String>>read(v1, "$.rapport.points[?(@.code=='VARIABLE_INCONNUE')].ou"))
                .containsExactly("corps › paragraphe 2 › variable 1");
        assertThat(JsonPath.<List<String>>read(v1, "$.rapport.points[?(@.code=='VARIABLE_INCONNUE')].chemin"))
                .containsExactly("corps/1/1");
        assertThat(JsonPath.<List<String>>read(v1, "$.rapport.points[?(@.code=='CONDITION_NON_DECLAREE')].ou"))
                .containsExactly("corps › paragraphe 3 › bloc 1");
        mvc.perform(post(BASE + "/" + id + "/versions/1/mettre-en-vigueur").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"premier modèle\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RAPPORT_BLOQUANT"));

        // Le brouillon se corrige en place (Q4) : même numéro, nouvelle empreinte, rapport recalculé.
        String avant = JsonPath.read(v1, "$.empreinte");
        String corrigee = mvc.perform(put(BASE + "/" + id + "/versions/1/document").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(modelePv("PV.REFERENCE"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(1)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(corrigee, "$.empreinte")).isNotEqualTo(avant);
        assertThat(JsonPath.<List<String>>read(corrigee, "$.rapport.points[*].code")).doesNotContain("VARIABLE_INCONNUE");
        mvc.perform(put(BASE + "/" + id + "/versions/1/document").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"document\":{\"corps\":{\"type\":\"doc\",\"content\":[{\"type\":\"iframe\"}]}}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DOCUMENT_INVALIDE"))
                .andExpect(jsonPath("$.details['corps/0']").value(containsString("« iframe » n'est pas permis ici")));

        // Version 2 ; conditions et champ déclarés : le rapport tombe à zéro bloquant, l'essai va jusqu'au PDF.
        enregistrer(id, modelePv("PV.REFERENCE"));
        mvc.perform(put(BASE + "/" + id + "/versions/2/conditions").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"nom\":\"AVEC_RESERVES\",\"expression\":\"avis = FAVR et inconnue = 1\"}]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONDITION_INVALIDE"));
        mvc.perform(put(BASE + "/" + id + "/versions/2/conditions").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"nom\":\"AVEC_RESERVES\",\"expression\":\"avis = FAVR\",\"libelle\":\"l'avis est favorable avec réserves\"}]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conditions[0].nom").value("AVEC_RESERVES"))
                .andExpect(jsonPath("$.rapport.points[?(@.code=='CONDITION_NON_DECLAREE')]").isEmpty());
        String v2 = mvc.perform(put(BASE + "/" + id + "/versions/2/champs").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"code\":\"observation\",\"libelle\":\"Observation générale\",\"type\":\"TEXTE_LONG\",\"obligatoire\":false}]"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(v2, "$.bloquant")).isFalse();
        assertThat(JsonPath.<List<String>>read(v2, "$.rapport.points[*].code"))
                .containsExactly("DOCUMENT_VALIDE", "SECTIONS", "VARIABLES", "BLOCS", "ESSAI");

        // Aperçu sur le dossier 1 (examen 1, PV signé) : un PDF, rien n'est enregistré, l'avis forcé choisit le bloc des réserves.
        seedPvSigne(9701, 1);
        byte[] apercu = mvc.perform(post(BASE + "/" + id + "/versions/2/apercu").param("dossier", "1").param("avis", "FAVR")
                .header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"saisis\":{\"observation\":\"RAS\"}}"))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(texte(apercu)).contains("COMMISSION NATIONALE DES MARCHÉS", "dossier DOS-1",
                "avis favorable avec réserves, sous les réserves de l'annexe.", "Observation : RAS", "Page 1 / 1").doesNotContain("{{");
        mvc.perform(post(BASE + "/" + id + "/versions/2/apercu").param("dossier", "1").param("format", "docx")
                .header("Authorization", tokenAdmin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FORMAT_INDISPONIBLE"));
        mvc.perform(post(BASE + "/" + id + "/versions/2/apercu").param("dossier", "999999").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());

        // Mise en vigueur : motif exigé ; la v2 en vigueur ne se modifie plus.
        mvc.perform(post(BASE + "/" + id + "/versions/2/mettre-en-vigueur").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_REQUIS"));
        mvc.perform(post(BASE + "/" + id + "/versions/2/mettre-en-vigueur").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"modèle validé par le pilote\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versions[0].etat").value("EN_VIGUEUR"))
                .andExpect(jsonPath("$.versions[0].document").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.versions[1].etat").value("BROUILLON"));
        mvc.perform(put(BASE + "/" + id + "/versions/2/champs").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("[]")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_FIGEE"));
        mvc.perform(put(BASE + "/" + id + "/versions/2/document").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(modelePv("PV.REFERENCE"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_FIGEE"));

        // Une v3 mise en vigueur remplace la v2 ; la copie reprend la page, ses conditions et son document.
        enregistrer(id, modelePv("PV.REFERENCE"));
        mvc.perform(post(BASE + "/" + id + "/dupliquer").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PV-MARCHE-COPIE")).andExpect(jsonPath("$.versions[0].numero").value(1))
                .andExpect(jsonPath("$.versions[0].etat").value("BROUILLON")).andExpect(jsonPath("$.versions[0].support").value("PAGE"))
                .andExpect(jsonPath("$.versions[0].conditions[0].nom").value("AVEC_RESERVES"));
        mvc.perform(put(BASE + "/" + id + "/versions/3/conditions").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"nom\":\"AVEC_RESERVES\",\"expression\":\"avis = FAVR\"}]")).andExpect(status().isOk());
        mvc.perform(put(BASE + "/" + id + "/versions/3/champs").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"code\":\"observation\",\"libelle\":\"Observation\",\"type\":\"TEXTE\"}]")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + id + "/versions/3/mettre-en-vigueur").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"correction\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versions[0].etat").value("EN_VIGUEUR"))
                .andExpect(jsonPath("$.versions[1].etat").value("REMPLACEE"));

        // Rattachements : un sous-type, un modèle ; l'autre modèle ne peut pas le prendre.
        mvc.perform(put(BASE + "/" + id + "/rattachements").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sousTypes\":[\"DAOO\",\"INCONNU\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SOUS_TYPE_INCONNU"));
        mvc.perform(put(BASE + "/" + id + "/rattachements").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sousTypes\":[\"DAOO\",\"DAOO-CC\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sousTypes[*].id").value(org.hamcrest.Matchers.contains("DAOO", "DAOO-CC")));
        long autre = creer("PV-AUTRE");
        mvc.perform(put(BASE + "/" + autre + "/rattachements").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sousTypes\":[\"DAOO-CC\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOUS_TYPE_DEJA_SERVI"))
                .andExpect(jsonPath("$.details['DAOO-CC']").value("PV-MARCHE"));
        mvc.perform(get(BASE).header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='PV-MARCHE')].enVigueur.numero").value(hasItem(3)))
                .andExpect(jsonPath("$[?(@.code=='PV-MARCHE')].sousTypes[0]").value(hasItem("DAOO")));

        // Archivage : refusé sans motif tant que le modèle sert ; avec motif, détaché.
        mvc.perform(post(BASE + "/" + id + "/archiver").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODELE_EN_SERVICE"));
        mvc.perform(post(BASE + "/" + id + "/archiver").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"remplacé par le modèle national\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("ARCHIVE")).andExpect(jsonPath("$.sousTypes").isEmpty())
                .andExpect(jsonPath("$.historique[0].action").value("ARCHIVE"))
                .andExpect(jsonPath("$.historique[*].action").value(org.hamcrest.Matchers.hasItems("CREE", "ENREGISTREE", "DOCUMENT_MODIFIE",
                        "CONDITIONS", "CHAMPS", "MISE_EN_VIGUEUR", "RATTACHEMENTS", "DUPLIQUE")));
        mvc.perform(put(BASE + "/" + autre + "/rattachements").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sousTypes\":[\"DAOO-CC\"]}")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + id + "/versions").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(modelePv("PV.REFERENCE"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODELE_ARCHIVE"));

        // Une page n'a pas de fichier ; sa lecture rend son document et son empreinte.
        mvc.perform(get(BASE + "/" + id + "/versions/1/fichier").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_SANS_FICHIER"));
        mvc.perform(get(BASE + "/" + id + "/versions/1").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.empreinte").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.document.piedDePage.type").value("doc"));
    }

    /**
     * ⚠️ 2026-10-21 (« modele-pv-page », §B2, §B6, Q5) — le dépôt d'un Word est retiré ; une version Word déjà déposée reste en
     * consultation (fichier, aperçu en Word) mais ne se met plus en vigueur, ne se modifie plus en page et ne se copie plus.
     */
    @Test
    void versionWordEnConsultation() throws Exception {
        long id = creer("PV-WORD");
        mvc.perform(multipart(BASE + "/" + id + "/versions").file(new MockMultipartFile("fichier", "PV.docx", "application/octet-stream",
                new byte[] { 1 })).header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPORT_ABANDONNE"));

        byte[] word = ModelePvWordTest.docx(doc -> {
            ModelePvWordTest.paragraphe(doc, "PV {{PV.REFERENCE}} — avis {{PV.AVIS}}");
            ModelePvWordTest.paragraphe(doc, "Le membre : {{PV.MEMBRE}}");
        });
        VersionModelePv v = new VersionModelePv();
        v.setIdModele(id);
        v.setNumero(1);
        v.setNomFichier("PV.docx");
        v.setContenu(word);
        v.setEmpreinte("0".repeat(64));
        v.setTaille((long) word.length);
        v.setEtat(VersionModelePv.BROUILLON);
        v.setBloquant(false);
        v.setDeposeeLe(LocalDateTime.now());
        versionsModele.saveAndFlush(v);

        mvc.perform(get(BASE + "/" + id + "/versions/1").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.support").value("WORD")).andExpect(jsonPath("$.document").value(org.hamcrest.Matchers.nullValue()));
        byte[] fichier = mvc.perform(get(BASE + "/" + id + "/versions/1/fichier").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(fichier).isEqualTo(word);
        seedPvSigne(9704, 1);
        byte[] apercu = mvc.perform(post(BASE + "/" + id + "/versions/1/apercu").param("dossier", "1").param("format", "docx")
                .header("Authorization", tokenAdmin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(apercu))) {
            assertThat(doc.getParagraphs().get(0).getText()).doesNotContain("{{");
        }
        mvc.perform(post(BASE + "/" + id + "/versions/1/mettre-en-vigueur").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPORT_ABANDONNE"));
        mvc.perform(put(BASE + "/" + id + "/versions/1/document").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content(modelePv("PV.REFERENCE"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPORT_ABANDONNE"));
        mvc.perform(post(BASE + "/" + id + "/dupliquer").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.versions").isEmpty())
                .andExpect(jsonPath("$.historique[0].detail").value(containsString("la version Word n'est pas copiée")));
    }

    /** ⚠️ 2026-10-21 (« modele-pv-page », §B1, §B3) — documents refusés, images contrôlées par leur contenu, schéma, catalogue. */
    @Test
    void documentRefuseImagesEtCatalogue() throws Exception {
        long id = creer("PV-PLAN");
        mvc.perform(post(BASE + "/" + id + "/versions").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DOCUMENT_INVALIDE"));
        mvc.perform(post(BASE + "/" + id + "/versions").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"document\":{\"html\":\"<p onclick=x>\",\"corps\":{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\","
                        + "\"content\":[{\"type\":\"text\",\"text\":\"x\",\"marks\":[{\"type\":\"textStyle\",\"attrs\":{\"fontFamily\":\"Comic Sans MS\"}}]}]}]}}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DOCUMENT_INVALIDE"))
                .andExpect(jsonPath("$.details.document").value(containsString("clé inconnue « html »")))
                .andExpect(jsonPath("$.details['corps/0/0']").value(containsString("fontFamily vaut [Times New Roman, Arial]")));

        // Images : un PNG (contenu contrôlé, type déclaré ignoré), servi avec son type ; un texte refusé ; l'emblème fourni une fois.
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", png);
        String image = mvc.perform(multipart(BASE + "/images").file(new MockMultipartFile("fichier", "logo.jpg", "image/jpeg", png.toByteArray()))
                .header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.largeur").value(40)).andExpect(jsonPath("$.hauteur").value(20))
                .andExpect(jsonPath("$.type").value("image/png")).andReturn().getResponse().getContentAsString();
        long idImage = ((Number) JsonPath.read(image, "$.idImage")).longValue();
        mvc.perform(get(BASE + "/images/" + idImage).header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(multipart(BASE + "/images").file(new MockMultipartFile("fichier", "x.png", "image/png", "<svg/>".getBytes()))
                .header("Authorization", tokenAdmin)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_INVALIDE"));
        String embleme = mvc.perform(get(BASE + "/images/embleme").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EMBLEME")).andExpect(jsonPath("$.type").value("image/png"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get(BASE + "/images/embleme").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.idImage").value(((Number) JsonPath.read(embleme, "$.idImage")).intValue()));
        mvc.perform(get(BASE + "/images/999999").header("Authorization", tokenAdmin)).andExpect(status().isNotFound());

        // Une page avec cette image : enregistrée ; une image inconnue : refusée.
        ObjectNode avecImage = ModelePvPageTest.document(ModelePvPageTest.paragraphe("{{PV.REFERENCE}} {{PV.MEMBRE}} {{PV.AVIS}}"));
        ObjectNode n = ((tools.jackson.databind.node.ArrayNode) avecImage.path("corps").path("content")).insertObject(0);
        n.put("type", "image").putObject("attrs").put("idImage", idImage).put("largeur", 30);
        assertThat(JsonPath.<Boolean>read(enregistrer(id, "{\"document\":" + avecImage + "}"), "$.bloquant")).isFalse();
        ((ObjectNode) avecImage.path("corps").path("content").path(0).path("attrs")).put("idImage", 999999);
        mvc.perform(post(BASE + "/" + id + "/versions").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"document\":" + avecImage + "}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details['corps/0']").value(containsString("idImage ne désigne aucune image")));

        // Une ligne répétée dans une autre, une variable de liste hors de sa ligne : bloquants, au lieu du nœud.
        ObjectNode imbriquee = ModelePvPageTest.tableau(List.of("a"), "OBSERVATIONS", List.of("{{OBS.POINT}}"));
        ((tools.jackson.databind.node.ArrayNode) imbriquee.path("content").path(1).path("content").path(0).path("content"))
                .add(ModelePvPageTest.tableau(List.of("b"), "TITULAIRES", List.of("{{TIT.RANG}}")));
        String mal = enregistrer(id, "{\"document\":" + ModelePvPageTest.document(
                ModelePvPageTest.paragraphe("{{PV.REFERENCE}} {{PV.MEMBRE}} {{PV.AVIS}} {{OBS.LIRE}}"), imbriquee) + "}");
        assertThat(JsonPath.<List<String>>read(mal, "$.rapport.points[?(@.niveau=='BLOQUANT')].code"))
                .containsExactlyInAnyOrder("VARIABLE_HORS_LISTE", "POUR_MAL_PLACE");
        assertThat(JsonPath.<List<String>>read(mal, "$.rapport.points[?(@.code=='POUR_MAL_PLACE')].chemin")).containsExactly("corps/1/1/0/1/1");

        mvc.perform(get(BASE + "/schema").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.noeuds.bloc").value(org.hamcrest.Matchers.contains("condition", "enLigne")))
                .andExpect(jsonPath("$.polices").value(org.hamcrest.Matchers.contains("Times New Roman", "Arial")))
                .andExpect(jsonPath("$.limites.tailleOctets").value(2097152));
        mvc.perform(get(BASE + "/variables").param("sousType", "PPM").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.famille").value("DDP"))
                .andExpect(jsonPath("$.groupes[*].nom").value(org.hamcrest.Matchers.not(hasItem("Procédure"))))
                .andExpect(jsonPath("$.alias['<CHEF LIEU>']").value("COMMISSION.CHEF_LIEU"));
        mvc.perform(get(BASE + "/variables").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupes[?(@.nom=='Marché')].variables[*].code").value(hasItem("TIT.CANDIDAT")))
                .andExpect(jsonPath("$.listes[*].nom").value(hasItem("TITULAIRES")))
                .andExpect(jsonPath("$.cles[*].cle").value(hasItem("avis")))
                .andExpect(jsonPath("$.groupes[0].variables[0].exemple").value(containsString("Commission")));
        mvc.perform(get(BASE + "/variables").param("sousType", "XX").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
    }

    /**
     * ⚠️ 2026-10-20 (« modeles-pv-integres-consultation », §B1-B2) — télécharger un fichier intégré (jamais un chemin libre) et l'essayer
     * sur un dossier réel par la règle de la production, le fichier choisi nommé dans {@code X-Fichier-Modele}.
     */
    @Test
    void consultationDesIntegres() throws Exception {
        String integres = BASE + "/integres/";
        mvc.perform(get(integres + "PV-PLAN-INTEGRE/fichiers/PV_AF_PPM_CENTRALE.docx").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        byte[] fichier = mvc.perform(get(integres + "PV-PLAN-INTEGRE/fichiers/PV_AF_PPM_CENTRALE.docx").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(fichier, 0, 2, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("PK");
        mvc.perform(get(integres + "PV-PLAN-INTEGRE/fichiers/LR_CENTRALE.docx").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        mvc.perform(get(integres + "PV-PLAN-INTEGRE/fichiers/application.properties").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        mvc.perform(get(integres + "INCONNU/fichiers/LR_CENTRALE.docx").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        mvc.perform(get(integres + "LETTRE-RENVOI-INTEGREE/fichiers/LR_REGIONALE.docx").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());

        // Dossier 1 (plan sans ligne, examen 1 réceptionné en centrale) : sans PV, puis sans ligne, la règle refuse en clair.
        mvc.perform(post(integres + "PV-PLAN-INTEGRE/apercu").param("dossier", "1").param("format", "docx").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PV_ABSENT"));
        seedPvSigne(9702, 1);
        mvc.perform(post(integres + "PV-PLAN-INTEGRE/apercu").param("dossier", "1").param("format", "docx").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUCUN_DOCUMENT"))
                .andExpect(jsonPath("$.message").value(containsString("plus aucune ligne")));
        marcheRepository.save(marche(9801, 1, 1));
        byte[] af = mvc.perform(post(integres + "PV-PLAN-INTEGRE/apercu").param("dossier", "1").param("format", "docx")
                .header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Fichier-Modele", "PV_AF_PPM_CENTRALE.docx"))
                .andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(af))) {
            String texte = doc.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b);
            assertThat(texte).doesNotContain("<REFERENCE PV", "<ENTITE CONTRACTANTE>");
        }
        // L'avis forcé choisit son fichier, sur une copie : le PV en base garde son avis.
        mvc.perform(post(integres + "PV-PLAN-INTEGRE/apercu").param("dossier", "1").param("avis", "FAVR").param("pv", "9702")
                .param("format", "docx").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Fichier-Modele", "PV_AFSR_PPM_CENTRALE.docx"));
        assertThat(pvExamenRepository.findById(9702).orElseThrow().getIdAvis()).isEqualTo("FAV");
        mvc.perform(post(integres + "PV-PLAN-INTEGRE/apercu").param("dossier", "1").param("avis", "NSP").param("format", "docx")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUCUN_DOCUMENT"))
                .andExpect(jsonPath("$.message").value(containsString("ne se prononce pas")));
        mvc.perform(post(integres + "PV-PLAN-INTEGRE/apercu").param("dossier", "2").param("pv", "9702").param("format", "docx")
                .header("Authorization", tokenAdmin)).andExpect(status().isNotFound());

        // Lettre de renvoi : la dernière du dossier, même non signée.
        mvc.perform(post(integres + "LETTRE-RENVOI-INTEGREE/apercu").param("dossier", "1").param("format", "docx")
                .header("Authorization", tokenAdmin)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LETTRE_ABSENTE"));
        seedLettreSoumise();
        mvc.perform(post(integres + "LETTRE-RENVOI-INTEGREE/apercu").param("dossier", "1").param("format", "docx")
                .header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Fichier-Modele", org.hamcrest.Matchers.startsWith("LR_")));
    }

    /**
     * ⚠️ 2026-10-21 (« modele-pv-depart », puis « modele-pv-page » §B5) — le modèle de départ : la famille lue sur les sous-types, une
     * page en brouillon déjà déclarée et sans blocage, essayée sur un dossier réel ; les refus.
     */
    @Test
    void modeleDeDepart() throws Exception {
        long id = creer("PV-DEPART");
        String depart = BASE + "/" + id + "/versions/depart";
        mvc.perform(post(depart).header("Authorization", tokenMembre)).andExpect(status().isForbidden());
        mvc.perform(post(depart).header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SANS_SOUS_TYPE"));
        mvc.perform(put(BASE + "/" + id + "/rattachements").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sousTypes\":[\"DP\",\"DSS\",\"DC\"]}")).andExpect(status().isOk());
        mvc.perform(post(depart).header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FAMILLES_MULTIPLES"))
                .andExpect(jsonPath("$.details.DMC").value(org.hamcrest.Matchers.contains("DC", "DP")))
                .andExpect(jsonPath("$.details.DSS").value(org.hamcrest.Matchers.contains("DSS")));
        mvc.perform(put(BASE + "/" + id + "/rattachements").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sousTypes\":[\"DP\",\"DC\"]}")).andExpect(status().isOk());
        mvc.perform(post(depart).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON).content("{\"portee\":\"nationale\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PORTEE_INCONNUE"));

        // Sans corps : les deux portées, la version 1 en page, brouillon, déclarée, sans blocage ni avertissement.
        String v1 = mvc.perform(post(depart).header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(1)).andExpect(jsonPath("$.etat").value("BROUILLON"))
                .andExpect(jsonPath("$.bloquant").value(false)).andExpect(jsonPath("$.support").value("PAGE"))
                .andExpect(jsonPath("$.nomFichier").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.document.corps.content[0].type").value("image"))
                .andExpect(jsonPath("$.motif").value(containsString("à faire valider par la CNM")))
                .andExpect(jsonPath("$.conditions[*].nom").value(org.hamcrest.Matchers.contains("FAVORABLE", "AVEC_RESERVES", "DEFAVORABLE",
                        "OBS_GENERALE", "CENTRALE", "REGIONALE")))
                .andExpect(jsonPath("$.champs[0].code").value("motif_defavorable")).andExpect(jsonPath("$.champs[0].obligatoire").value(true))
                .andExpect(jsonPath("$.champs[0].visibleSi").value("DEFAVORABLE"))
                .andExpect(jsonPath("$.champs[1].code").value("observation_generale"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(v1, "$.rapport.points[?(@.niveau!='OK')].code")).isEmpty();
        assertThat(JsonPath.<List<String>>read(v1, "$.rapport.points[*].code")).contains("ESSAI");

        // Essayée sur le dossier 1 : la réserve imprime l'annexe sur une seconde page, le défavorable son motif sur une seule.
        seedPvSigne(9703, 1);
        byte[] favr = mvc.perform(post(BASE + "/" + id + "/versions/1/apercu").param("dossier", "1").param("avis", "FAVR")
                .header("Authorization", tokenAdmin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        byte[] def = mvc.perform(post(BASE + "/" + id + "/versions/1/apercu").param("dossier", "1").param("avis", "DEF")
                .header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"saisis\":{\"motif_defavorable\":\"pièces manquantes\"}}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pages(favr)).isEqualTo(2);
        assertThat(pages(def)).isEqualTo(1);
        assertThat(texte(favr)).contains("PROCÈS-VERBAL D’EXAMEN DU DOSSIER DE MISE EN CONCURRENCE", "ANNEXE",
                "sous réserve qu’il soit tenu compte").doesNotContain("{{", "<");
        assertThat(texte(def)).contains("AVIS NON FAVORABLE au dossier de mise en concurrence, pour le motif suivant : pièces manquantes.")
                .doesNotContain("ANNEXE", "{{");

        // Une deuxième génération ajoute une version ; la portée choisie ne déclare pas CENTRALE / REGIONALE.
        mvc.perform(post(depart).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON).content("{\"portee\":\"centrale\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.numero").value(2)).andExpect(jsonPath("$.conditions.length()").value(4));
        mvc.perform(get(BASE + "/" + id).header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.historique[0].action").value("DEPART_GENERE"))
                .andExpect(jsonPath("$.historique[0].detail").value(containsString(
                        "famille DMC, page reconstruite d'après PV_AFSR_PPM_CENTRALE.docx, portée centrale")))
                .andExpect(jsonPath("$.historique[1].detail").value(containsString("PV_AFSR_PPM_CENTRALE.docx + PV_AFSR_PPM_REGIONALE.docx")));

        // Le plan n'a pas de modèle de départ ; un modèle archivé, rien (la lettre de renvoi en a un : lettreDeRenvoi).
        long plan = creer("PV-PLAN-DEPART");
        mvc.perform(put(BASE + "/" + plan + "/rattachements").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sousTypes\":[\"PPM\"]}")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + plan + "/versions/depart").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FAMILLE_SANS_DEPART"));
        mvc.perform(post(BASE + "/" + plan + "/archiver").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"essai\"}")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + plan + "/versions/depart").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODELE_ARCHIVE"));
    }

    /** ⚠️ 2026-10-20 (« modeles-pv-integres », §B1-B2) — les modèles intégrés au JAR et la couverture par sous-type. */
    @Test
    void integresEtCouverture() throws Exception {
        mvc.perform(get(BASE + "/integres").header("Authorization", tokenMembre)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/couverture").header("Authorization", tokenMembre)).andExpect(status().isForbidden());
        String integres = mvc.perform(get(BASE + "/integres").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("PV-PLAN-INTEGRE"))
                .andExpect(jsonPath("$[0].sousTypes").value(org.hamcrest.Matchers.contains("PPM", "PPM-AGPM")))
                .andExpect(jsonPath("$[0].enService").value(true))
                .andExpect(jsonPath("$[1].code").value("LETTRE-RENVOI-INTEGREE"))
                .andExpect(jsonPath("$[1].nature").value("LETTRE_RENVOI"))
                .andExpect(jsonPath("$[1].sousTypes").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[1].fichiers").value(org.hamcrest.Matchers.contains("LR_CENTRALE.docx", "LR_REGIONALE.docx")))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<java.util.List<String>>read(integres, "$[0].fichiers")).hasSize(12)
                .contains("PV_AFSR_PPMAGPM_CENTRALE.docx", "PV_ANF_PPM_REGIONALE.docx");

        // Un modèle géré rattaché à DAOO-CC ne produit encore rien : la couverture le montre à part.
        long id = creer("PV-DMC");
        mvc.perform(put(BASE + "/" + id + "/rattachements").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sousTypes\":[\"DAOO-CC\"]}")).andExpect(status().isOk());
        String couverture = mvc.perform(get(BASE + "/couverture").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<java.util.List<String>>read(couverture, "$[?(@.sousType=='PPM' && @.nature=='PV')].produitPar"))
                .containsExactly("INTEGRE");
        assertThat(JsonPath.<java.util.List<String>>read(couverture, "$[?(@.sousType=='PPM-AGPM' && @.nature=='PV')].integre"))
                .containsExactly("PV-PLAN-INTEGRE");
        assertThat(JsonPath.<java.util.List<String>>read(couverture, "$[?(@.sousType=='DAOO-CC' && @.nature=='PV')].produitPar"))
                .containsExactly("AUCUN");
        assertThat(JsonPath.<java.util.List<String>>read(couverture, "$[?(@.sousType=='DAOO-CC' && @.nature=='PV')].modeleRattache.code"))
                .containsExactly("PV-DMC");
        assertThat(JsonPath.<java.util.List<Object>>read(couverture,
                "$[?(@.sousType=='DAOO-CC' && @.nature=='PV')].modeleRattache.numeroEnVigueur")).containsExactly((Object) null);
        assertThat(JsonPath.<java.util.List<String>>read(couverture, "$[?(@.sousType=='DAOO' && @.nature=='LETTRE_RENVOI')].produitPar"))
                .containsExactly("INTEGRE");
        assertThat(JsonPath.<java.util.List<String>>read(couverture, "$[?(@.sousType=='DAOO' && @.nature=='PV')].famille"))
                .containsExactly("DMC");
    }

    /**
     * ⚠️ 2026-10-21 (« modele-lettre-renvoi », L1) — les variables de la lettre au catalogue, par nature ; le départ d'une lettre, sans
     * rattachement, contrôlé sans blocage ; une variable hors de sa nature ; l'aperçu sur une lettre réelle du dossier.
     */
    @Test
    void lettreDeRenvoi() throws Exception {
        String lettre = mvc.perform(get(BASE + "/variables").param("nature", "LETTRE_RENVOI").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nature").value("LETTRE_RENVOI"))
                .andExpect(jsonPath("$.listes").isEmpty()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(lettre, "$.groupes[?(@.nom=='Lettre de renvoi')].variables[*].code"))
                .containsExactly("LETTRE.DATE", "LETTRE.DATE_EXAMEN", "LETTRE.CORPS", "LETTRE.SIGNATAIRE", "LETTRE.LOCALITE");
        assertThat(JsonPath.<List<String>>read(lettre, "$.groupes[*].variables[*].code")).contains("DOSSIER.ENTITE")
                .noneMatch(c -> c.startsWith("PV.") || c.startsWith("OBS."));
        String pv = mvc.perform(get(BASE + "/variables").param("nature", "PV").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(pv, "$.groupes[*].variables[*].code")).contains("PV.REFERENCE")
                .noneMatch(c -> c.startsWith("LETTRE."));
        mvc.perform(get(BASE + "/variables").param("nature", "AVIS").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NATURE_INCONNUE"));

        // Le départ : sans sous-type rattaché (une lettre pour tous, Q1), les deux portées en blocs, aucun bloquant.
        String cree = mvc.perform(post(BASE).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"LR-DEPART\",\"libelle\":\"Lettre de renvoi\",\"nature\":\"LETTRE_RENVOI\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(cree, "$.id")).longValue();
        String v1 = mvc.perform(post(BASE + "/" + id + "/versions/depart").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.support").value("PAGE")).andExpect(jsonPath("$.bloquant").value(false))
                .andExpect(jsonPath("$.motif").value(containsString("texte à faire valider")))
                .andExpect(jsonPath("$.conditions[*].nom").value(org.hamcrest.Matchers.contains("CENTRALE", "REGIONALE")))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(v1, "$.rapport.points[*].code")).contains("ESSAI", "SECTIONS")
                .doesNotContain("SECTION_ABSENTE", "VARIABLE_HORS_NATURE");
        mvc.perform(get(BASE + "/" + id).header("Authorization", tokenAdmin)).andExpect(jsonPath("$.historique[0].detail")
                .value(containsString("lettre de renvoi, page reconstruite d'après LR_CENTRALE.docx + LR_REGIONALE.docx, portée les-deux")));

        // Une variable du PV dans une lettre est bloquante ; le corps et le signataire sont attendus.
        String horsNature = enregistrer(id, "{\"document\":" + ModelePvPageTest.document(ModelePvPageTest.paragraphe(
                "{{DOSSIER.REFERENCE}} {{PV.MEMBRE}}")) + "}");
        assertThat(JsonPath.<List<String>>read(horsNature, "$.rapport.points[?(@.niveau=='BLOQUANT')].code"))
                .contains("VARIABLE_HORS_NATURE", "SECTION_ABSENTE");
        assertThat(JsonPath.<List<String>>read(horsNature, "$.rapport.points[?(@.code=='SECTION_ABSENTE')].suggestion"))
                .containsExactlyInAnyOrder("LETTRE.CORPS", "LETTRE.SIGNATAIRE");

        // L'aperçu : sur la dernière lettre du dossier (même non signée), ou sur lettre= ; une lettre d'un autre dossier, 404.
        mvc.perform(post(BASE + "/" + id + "/versions/1/apercu").param("dossier", "1").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LETTRE_ABSENTE"));
        int idLettre = seedLettreSoumise();
        cnm.prs.entity.LettreRenvoi l = lettreRenvoiRepository.findById(idLettre).orElseThrow();
        l.setCorpsLettre("- produire l'attestation fiscale ;\n- corriger le délai de validité.");
        l.setDateLettre(java.time.LocalDate.of(2026, 10, 23));
        l.setDateExamen(java.time.LocalDate.of(2026, 10, 22));
        lettreRenvoiRepository.saveAndFlush(l);
        byte[] apercu = mvc.perform(post(BASE + "/" + id + "/versions/1/apercu").param("dossier", "1").param("lettre", String.valueOf(idLettre))
                .header("Authorization", tokenAdmin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(pages(apercu)).isEqualTo(1);
        assertThat(texte(apercu)).contains("Antananarivo, le 23 octobre 2026", "en date du 22 octobre 2026",
                "- produire l'attestation fiscale ; - corriger le délai de validité.").doesNotContain("{{");
        assertThat(texte(mvc.perform(post(BASE + "/" + id + "/versions/1/apercu").param("dossier", "1").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())).contains("23 octobre 2026");
        mvc.perform(post(BASE + "/" + id + "/versions/1/apercu").param("dossier", "2").param("lettre", String.valueOf(idLettre))
                .header("Authorization", tokenAdmin)).andExpect(status().isNotFound());
    }

    /**
     * ⚠️ 2026-10-21 (« modele-pv-suppression », B1-B2, Q1-Q2) — un brouillon se supprime, son numéro n'est pas repris ; une version
     * qui a été en vigueur, non ; un modèle se supprime s'il n'a jamais été en vigueur et ne sert aucun sous-type, la trace reste au
     * journal général.
     */
    @Test
    void suppressions() throws Exception {
        long id = creer("PV-SUPPR");
        enregistrer(id, modelePv("PV.REFERENCE"));
        enregistrer(id, modelePv("PV.REFERENCE"));
        mvc.perform(delete(BASE + "/" + id + "/versions/2").header("Authorization", tokenMembre)).andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/" + id + "/versions/2").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.versions[*].numero").value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.historique[0].action").value("VERSION_SUPPRIMEE"))
                .andExpect(jsonPath("$.historique[0].detail").value("version 2, page"));
        mvc.perform(delete(BASE + "/" + id + "/versions/2").header("Authorization", tokenAdmin)).andExpect(status().isNotFound());
        // Q1 : le numéro 2 n'est pas repris.
        assertThat(JsonPath.<Integer>read(enregistrer(id, modelePv("PV.REFERENCE")), "$.numero")).isEqualTo(3);

        // Une version qui a été en vigueur ne se supprime pas, son modèle non plus.
        VersionModelePv v1 = versionsModele.findByIdModeleAndNumero(id, 1).orElseThrow();
        v1.setEtat(VersionModelePv.REMPLACEE);
        versionsModele.saveAndFlush(v1);
        mvc.perform(delete(BASE + "/" + id + "/versions/1").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_NON_SUPPRIMABLE"))
                .andExpect(jsonPath("$.message").value(containsString("une version qui a été en vigueur ne se supprime pas")));
        mvc.perform(delete(BASE + "/" + id).header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODELE_NON_SUPPRIMABLE"))
                .andExpect(jsonPath("$.details.raison").value("A_ETE_EN_VIGUEUR"));

        // Un modèle jamais en vigueur : refusé tant qu'il sert un sous-type ; archivé (détaché), il se supprime avec tout ce qu'il a.
        long essai = creer("ESSAI-SUPPR");
        enregistrer(essai, modelePv("PV.REFERENCE"));
        mvc.perform(put(BASE + "/" + essai + "/versions/1/conditions").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("[{\"nom\":\"AVEC_RESERVES\",\"expression\":\"avis = FAVR\"}]"))
                .andExpect(status().isOk());
        mvc.perform(put(BASE + "/" + essai + "/rattachements").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sousTypes\":[\"DAOO\"]}")).andExpect(status().isOk());
        mvc.perform(delete(BASE + "/" + essai).header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODELE_NON_SUPPRIMABLE"))
                .andExpect(jsonPath("$.details.raison").value("SERT_DES_SOUS_TYPES"))
                .andExpect(jsonPath("$.details.sousTypes[0]").value("DAOO"));
        mvc.perform(post(BASE + "/" + essai + "/archiver").header("Authorization", tokenAdmin)).andExpect(status().isOk());
        mvc.perform(delete(BASE + "/" + essai + "/versions/1").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODELE_ARCHIVE"));
        mvc.perform(delete(BASE + "/" + essai).header("Authorization", tokenMembre)).andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/" + essai).header("Authorization", tokenAdmin)).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/" + essai).header("Authorization", tokenAdmin)).andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/" + essai).header("Authorization", tokenAdmin)).andExpect(status().isNotFound());
        assertThat(versionsModele.findByIdModeleOrderByNumeroDesc(essai)).isEmpty();
        // Q2 : la trace reste au journal général, avec le code et le libellé.
        assertThat(auditLogs.findAll()).anySatisfy(a -> {
            assertThat(a.getNomTable()).isEqualTo("modeles-pv");
            assertThat(a.getTypeAction()).isEqualTo("MODELE_SUPPRIME");
            assertThat(a.getIdEnregistrement()).isEqualTo(String.valueOf(essai));
            assertThat(a.getChampModifie()).isEqualTo("ESSAI-SUPPR");
            assertThat(a.getNouvelleValeur()).contains("PV d'examen du dossier de marché", "1 version(s)");
        });
    }
}
