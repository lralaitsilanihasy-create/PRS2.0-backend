package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.service.ModelePvWordTest;

/**
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », lot G1 ; V138) — les routes de l'Administrateur : création, dépôt et
 * rapport de contrôle, conditions et champs (rapport recalculé), aperçu sur un dossier réel sans rien enregistrer, mise en vigueur,
 * rattachements, duplication, archivage, journal. L'essai jusqu'au PDF est désactivé en test ({@code app.modeles-pv.essai-pdf}).
 */
class ModelePvIntegrationTest extends CnmIntegrationTestSupport {

    private static final String BASE = "/api/modeles-pv";

    /** Un PV minimal : identification, avis par blocs, observations répétées, signatures. */
    private static byte[] modelePv(String referenceEcrite) {
        return ModelePvWordTest.docx(doc -> {
            ModelePvWordTest.paragraphe(doc, "{{COMMISSION.NOM|majuscules}}");
            ModelePvWordTest.paragraphe(doc, "PV n° ", referenceEcrite, " — dossier {{DOSSIER.REFERENCE}}");
            ModelePvWordTest.paragraphe(doc, "La Commission émet un avis {{PV.AVIS}}{{SI:AVEC_RESERVES}}, sous les réserves de l'annexe"
                    + "{{FINSI:AVEC_RESERVES}}.");
            XWPFTable t = doc.createTable(2, 3);
            t.getRow(0).getCell(0).setText("Point");
            t.getRow(1).getCell(0).setText("{{POUR:OBSERVATIONS}}{{OBS.POINT}}");
            t.getRow(1).getCell(1).setText("{{OBS.AU_LIEU_DE}}");
            t.getRow(1).getCell(2).setText("{{OBS.LIRE}}");
            ModelePvWordTest.paragraphe(doc, "Observation : {{SAISI:observation}}");
            ModelePvWordTest.paragraphe(doc, "Le membre : {{PV.MEMBRE}}");
        });
    }

    private long creer(String code) throws Exception {
        String r = mvc.perform(post(BASE).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"libelle\":\"PV d'examen du dossier de marché\",\"nature\":\"PV\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    private String deposer(long id, byte[] contenu) throws Exception {
        return mvc.perform(multipart(BASE + "/" + id + "/versions").file(new MockMultipartFile("fichier", "PV_MARCHE_v1.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", contenu)).header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void reserveALAdministrateur() throws Exception {
        mvc.perform(get(BASE).header("Authorization", tokenMembre)).andExpect(status().isForbidden());
        mvc.perform(get(BASE).header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
        mvc.perform(get(BASE).header("Authorization", tokenAdmin)).andExpect(status().isOk());
    }

    @Test
    void duDepotALaMiseEnVigueur() throws Exception {
        long id = creer("PV-MARCHE");
        mvc.perform(post(BASE).header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"PV-MARCHE\",\"libelle\":\"x\",\"nature\":\"PV\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CODE_DEJA_PRIS"));

        // Dépôt : une variable mal orthographiée, un bloc et un champ non déclarés → bloquant, avec la suggestion.
        byte[] fautif = modelePv("{{PV.REFERENCES}}");
        String v1 = deposer(id, fautif);
        assertThat(JsonPath.<Integer>read(v1, "$.numero")).isEqualTo(1);
        assertThat(JsonPath.<Boolean>read(v1, "$.bloquant")).isTrue();
        assertThat(JsonPath.<java.util.List<String>>read(v1, "$.rapport.points[?(@.niveau=='BLOQUANT')].code"))
                .contains("VARIABLE_INCONNUE", "CONDITION_NON_DECLAREE", "CHAMP_NON_DECLARE", "SECTION_ABSENTE");
        assertThat(JsonPath.<java.util.List<String>>read(v1, "$.rapport.points[?(@.code=='VARIABLE_INCONNUE')].suggestion"))
                .containsExactly("{{PV.REFERENCE}}");
        assertThat(JsonPath.<java.util.List<String>>read(v1, "$.rapport.points[?(@.code=='VARIABLE_INCONNUE')].ou"))
                .containsExactly("paragraphe 2");
        mvc.perform(post(BASE + "/" + id + "/versions/1/mettre-en-vigueur").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"premier modèle\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RAPPORT_BLOQUANT"));

        // Version 2, corrigée ; conditions et champ déclarés : le rapport tombe à zéro bloquant.
        deposer(id, modelePv("{{PV.REFERENCE}}"));
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
        assertThat(JsonPath.<java.util.List<String>>read(v2, "$.rapport.points[*].code"))
                .contains("FICHIER_LISIBLE", "SECTIONS", "VARIABLES", "BLOCS", "ESSAI_PDF_NON_VERIFIE");

        // Aperçu sur le dossier 1 (examen 1, PV signé) : rien n'est enregistré, l'avis forcé choisit le bloc des réserves.
        seedPvSigne(9701, 1);
        byte[] apercu = mvc.perform(post(BASE + "/" + id + "/versions/2/apercu").param("dossier", "1").param("avis", "FAVR")
                .param("format", "docx").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"saisis\":{\"observation\":\"RAS\"}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(apercu))) {
            String texte = doc.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b);
            assertThat(texte).contains("COMMISSION NATIONALE DES MARCHÉS", "dossier DOS-1",
                    "avis favorable avec réserves, sous les réserves de l'annexe.", "Observation : RAS")
                    .doesNotContain("{{");
        }
        mvc.perform(post(BASE + "/" + id + "/versions/2/apercu").param("dossier", "999999").param("format", "docx")
                .header("Authorization", tokenAdmin)).andExpect(status().isNotFound());

        // Mise en vigueur : motif exigé ; la v2 en vigueur ne se modifie plus.
        mvc.perform(post(BASE + "/" + id + "/versions/2/mettre-en-vigueur").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_REQUIS"));
        mvc.perform(post(BASE + "/" + id + "/versions/2/mettre-en-vigueur").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"modèle validé par le pilote\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versions[0].etat").value("EN_VIGUEUR"))
                .andExpect(jsonPath("$.versions[1].etat").value("BROUILLON"));
        mvc.perform(put(BASE + "/" + id + "/versions/2/champs").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("[]")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_FIGEE"));

        // Une v3 mise en vigueur remplace la v2.
        deposer(id, modelePv("{{PV.REFERENCE}}"));
        mvc.perform(post(BASE + "/" + id + "/dupliquer").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PV-MARCHE-COPIE")).andExpect(jsonPath("$.versions[0].numero").value(1))
                .andExpect(jsonPath("$.versions[0].etat").value("BROUILLON"))
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
                .andExpect(jsonPath("$.historique[*].action").value(org.hamcrest.Matchers.hasItems("CREE", "DEPOSEE", "CONDITIONS", "CHAMPS",
                        "MISE_EN_VIGUEUR", "RATTACHEMENTS", "DUPLIQUE")));
        mvc.perform(put(BASE + "/" + autre + "/rattachements").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sousTypes\":[\"DAOO-CC\"]}")).andExpect(status().isOk());
        mvc.perform(multipart(BASE + "/" + id + "/versions").file(new MockMultipartFile("fichier", "x.docx", "application/octet-stream",
                modelePv("{{PV.REFERENCE}}"))).header("Authorization", tokenAdmin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODELE_ARCHIVE"));

        // Le fichier d'une version se télécharge tel que déposé.
        byte[] fichier = mvc.perform(get(BASE + "/" + id + "/versions/1/fichier").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(fichier).isEqualTo(fautif);
        mvc.perform(get(BASE + "/" + id + "/versions/1").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.empreinte").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")));
    }

    @Test
    void depotRefuseEtCatalogue() throws Exception {
        long id = creer("PV-PLAN");
        mvc.perform(multipart(BASE + "/" + id + "/versions").file(new MockMultipartFile("fichier", "modele.doc", "application/msword",
                new byte[] { 1, 2, 3 })).header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FICHIER_INVALIDE"));
        String illisible = deposer(id, "pas un word".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(JsonPath.<java.util.List<String>>read(illisible, "$.rapport.points[*].code")).containsExactly("FICHIER_ILLISIBLE");

        // Un ancien modèle du plan : les chevrons sont lus (avertissement), l'annexe exige sa ligne répétée.
        String plan = deposer(id, ModelePvWordTest.docx(doc -> {
            ModelePvWordTest.paragraphe(doc, "<REFERENCE PV > du <DATE EXAMEN> à <CHEF LIEU >, avis {{PV.AVIS}}");
            ModelePvWordTest.paragraphe(doc, "<NOM ET PRENOMS DU MEMBRE> — <POINT DE CONTROLE>");
        }));
        assertThat(JsonPath.<java.util.List<String>>read(plan, "$.rapport.points[?(@.code=='ANCIEN_MARQUEUR')].suggestion"))
                .contains("{{PV.REFERENCE}}", "{{PV.DATE_EXAMEN}}", "{{COMMISSION.CHEF_LIEU}}", "{{PV.MEMBRE}}");
        assertThat(JsonPath.<java.util.List<String>>read(plan, "$.rapport.points[?(@.niveau=='BLOQUANT')].code"))
                .containsExactly("VARIABLE_HORS_LISTE");

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
}
