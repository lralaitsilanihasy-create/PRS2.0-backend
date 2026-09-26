package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.ChampFicheMarcheRepository;

/**
 * ⚠️ <strong>Documents générés depuis la fiche marché, lot 2a</strong> (demande front du 2026-09-23) — cas 1 à 7 de la
 * recette (le cas 8, l'échec de génération, est dans {@code FicheMarcheDocumentsEchecIntegrationTest}) : trois documents
 * et six fichiers à la validation, liste vide avant, champ fermé absent, champ vide omis et jamais « null », nouvelle
 * version sans effacer l'ancienne, jointure au dossier produit (pièces non supprimables), lecture par la Commission.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, entité 1, CLOTURE, PV signé FAV), ligne 9901 à quantité fixe (mode 92 → DAO), deux
 * lots dont un sans montant ; champs de recette B02-AU-01 (DPAO), B04-LR-02 et B04-OP-02 (dates, DPAO), B05-GS-02
 * (forme de la garantie de soumission, liste à choix multiples depuis le 2026-09-26, DPAO repris AE et CCAP) et B05-GS-03
 * (montant), tous deux sous garantie de soumission = OUI, B08-PA-50 (CCAP, repris AE). Type de pièce « Dossier d'appel
 * d'offres complet » de code DAO_COMPLET.</p>
 */
class FicheMarcheDocumentsIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheRepository champRepository;

    private int typeDao;
    private Long idDmc;

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
        Marche l = marcheDao(9901, 9900, 9900);
        l.setIdMode(92);
        l.setDesignationMarche("Fourniture de mobilier de bureau");
        l.setMontEstim(new BigDecimal("8400000"));
        marcheRepository.save(l);

        champ("B02-AU-01", "Autorité contractante (précisions)", "TEXTE", "DPAO", null, null);
        champ("B04-LR-02", "Date limite de remise des offres", "DATE", "DPAO", null, null);
        champ("B04-OP-02", "Date d'ouverture des plis", "DATE", "DPAO", null, null);
        // ⚠️ 2026-09-26 — la forme de la garantie est une liste à choix multiples (les quatre formes du CMP), comme au
        // référentiel des fournitures ; le montant est B05-GS-03.
        champ("B05-GS-02", "Forme de la garantie de soumission", "LISTE_MULTIPLE", "DPAO", "AE,CCAP", "garantieSoumission = OUI",
                "Dépôt en numéraire au Trésor,Caution personnelle et solidaire d'un organisme agréé par le MEF,"
                        + "Garantie bancaire,Chèque de banque", true);
        champ("B05-GS-03", "Montant de la garantie de soumission (Ariary)", "MONTANT", "DPAO", null, "garantieSoumission = OUI");
        champ("B08-PA-50", "Délai de paiement (jours)", "NOMBRE", "CCAP", "AE", null);

        typeDao = seedTypePiece("Dossier d'appel d'offres complet", true, "DMC", 1);
        TypePieceJointe t = typePieceJointeRepository.findById(typeDao).orElseThrow();
        t.setCode("DAO_COMPLET");
        typePieceJointeRepository.save(t);

        idDmc = creerDmc();
    }

    // ------------------------------------------------------------------ 1. trois documents, six fichiers

    @Test
    @DisplayName("1 — Valider une fiche quantité fixe → DPAO, CCAP et AE, en docx et pdf, liés à la version ; listés, "
            + "téléchargeables sous leur nom (docx = Word, pdf = PDF)")
    void troisDocumentsSixFichiers() throws Exception {
        remplirEtValider();
        String corps = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(corps, "$[*].type")).containsExactly("DPAO", "DPAO", "CCAP", "CCAP", "AE", "AE", "LF", "LF", "BP", "TC");   // V45 : liste des fournitures, bordereau, conformité
        assertThat(JsonPath.<List<String>>read(corps, "$[*].extension")).containsExactly("docx", "pdf", "docx", "pdf", "docx", "pdf", "docx", "pdf", "xlsx", "xlsx");
        assertThat(JsonPath.<List<Integer>>read(corps, "$[*].version")).containsOnly(1);
        assertThat(JsonPath.<List<String>>read(corps, "$[?(@.type=='AE')].libelle")).containsOnly("Acte d'engagement");
        assertThat(JsonPath.<List<String>>read(corps, "$[*].nomFichier")).contains("DPAO_DOS-9900_9901_v1.docx",
                "CCAP_DOS-9900_9901_v1.pdf", "AE_DOS-9900_9901_v1.docx");
        assertThat(JsonPath.<List<Integer>>read(corps, "$[*].tailleOctets")).allMatch(n -> n > 500);

        int idDocx = JsonPath.<List<Integer>>read(corps, "$[?(@.type=='DPAO' && @.extension=='docx')].idDocument").get(0);
        byte[] docx = mvc.perform(get("/api/fiches-marche/documents/" + idDocx + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("DPAO_DOS-9900_9901_v1.docx")))
                .andExpect(header().string("Content-Type",
                        containsString("application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                .andReturn().getResponse().getContentAsByteArray();
        String texte = texteDuDocx(docx);
        assertThat(texte).contains("Données particulières de l'appel d'offres", "Fourniture de mobilier de bureau",
                "Autorité contractante (précisions) : Direction des achats", "Date limite de remise des offres : 10/04/2026",
                "Montant estimatif (Ariary) : 8 400 000 Ariary (huit millions quatre cent mille ariary)",
                "Plan DOS-9900 · ligne 9901 · fiche marché version 1 validée le");

        int idPdf = JsonPath.<List<Integer>>read(corps, "$[?(@.type=='CCAP' && @.extension=='pdf')].idDocument").get(0);
        byte[] pdf = mvc.perform(get("/api/fiches-marche/documents/" + idPdf + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(texteDuPdf(pdf)).contains("Cahier des clauses administratives particulières", "Délai de paiement (jours) : 60");
        mvc.perform(get("/api/fiches-marche/documents/999999/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 2. rien avant la validation

    @Test
    @DisplayName("2 — Fiche virtuelle ou brouillon → 200 et liste vide ; version inconnue → 404")
    void listeVideAvantValidation() throws Exception {
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        cadrage("{\"garantieSoumission\":\"NON\"}");
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").param("version", "1").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").param("version", "7").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 3. champ fermé

    @Test
    @DisplayName("3 — Garantie de soumission saisie puis cadrage passé à NON : le montant de la garantie n'apparaît pas "
            + "dans le DPAO")
    void champFermeAbsent() throws Exception {
        cadrage("{\"garantieSoumission\":\"OUI\"}");
        bloc("B05", "{\"B05-GS-03\":1250000}");
        cadrage("{\"garantieSoumission\":\"NON\"}");
        bloc("B04", "{\"B04-LR-02\":\"2026-04-10\"}");
        valider();
        String dpao = texteDuDocx(contenu("DPAO", "docx"));
        assertThat(dpao).doesNotContain("Montant de la garantie de soumission").doesNotContain("1 250 000");
        assertThat(texteDuPdf(contenu("DPAO", "pdf"))).doesNotContain("Montant de la garantie de soumission");
    }

    // ------------------------------------------------------------------ 4. champ vide omis, jamais « null »

    @Test
    @DisplayName("4 — Champ ouvert non saisi : ligne omise ; le mot « null » n'apparaît dans aucun document ; montant par "
            + "lot sans montant : mention explicite (ligne en deux lots : un acte d'engagement par lot, 2026-09-25)")
    void champVideOmis() throws Exception {
        // ⚠️ 2026-09-25 — les lots ne sont posés qu'ici : une ligne allotie produit un acte d'engagement par lot, les autres
        // cas gardent une ligne non allotie.
        lotRepository.save(lot(99011, "Lot A", null));
        lotRepository.save(lot(99012, "Lot B", new BigDecimal("5000000")));
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valeursPpm.B02-LV-03").value("Lot A : montant non renseigné ; Lot B : 5 000 000"));
        remplirEtValider();
        String dpao = texteDuDocx(contenu("DPAO", "docx"));
        assertThat(dpao).doesNotContain("Date d'ouverture des plis").contains("Lot A : montant non renseigné");
        for (String type : List.of("DPAO", "CCAP", "AE")) {
            assertThat(texteDuDocx(contenu(type, "docx")).toLowerCase()).as(type + " docx").doesNotContain("null");
            assertThat(texteDuPdf(contenu(type, "pdf")).toLowerCase()).as(type + " pdf").doesNotContain("null");
        }
    }

    // ------------------------------------------------------------------ 5. nouvelle version

    @Test
    @DisplayName("5 — reviser puis valider → documents de la version 2 ; ?version=1 sert toujours ceux de la version 1")
    void nouvelleVersion() throws Exception {
        remplirEtValider();
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        bloc("B02", "{\"B02-AU-01\":\"Service des marchés\"}");
        valider();
        String v2 = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(v2, "$[*].version")).hasSize(10).containsOnly(2);
        assertThat(JsonPath.<List<String>>read(v2, "$[*].nomFichier")).contains("DPAO_DOS-9900_9901_v2.docx");
        String v1 = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").param("version", "1")
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(v1, "$[*].version")).hasSize(10).containsOnly(1);
        int idV1 = JsonPath.<List<Integer>>read(v1, "$[?(@.type=='DPAO' && @.extension=='docx')].idDocument").get(0);
        byte[] ancien = mvc.perform(get("/api/fiches-marche/documents/" + idV1 + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(texteDuDocx(ancien)).contains("Direction des achats").doesNotContain("Service des marchés");
        assertThat(texteDuDocx(contenu("DPAO", "docx"))).contains("Service des marchés");
    }

    // ------------------------------------------------------------------ 6. jointure au dossier

    @Test
    @DisplayName("6 — Dossier produit par la fiche : les trois PDF en pièces « Dossier d'appel d'offres complet », non "
            + "supprimables (409) ni remplaçables à la main ; une nouvelle version les remplace ; détacher les retire")
    void jointureAuDossier() throws Exception {
        remplirEtValider();
        String dossier = mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDossier = JsonPath.read(dossier, "$.idDossier");
        String pieces = pieces(idDossier);
        assertThat(JsonPath.<List<Integer>>read(pieces, "$[*].idTypePiece")).hasSize(4).containsOnly(typeDao);
        assertThat(JsonPath.<List<String>>read(pieces, "$[*].format")).containsOnly("PDF");
        assertThat(JsonPath.<List<String>>read(pieces, "$[*].nomFichier"))
                .containsExactlyInAnyOrder("DPAO_DOS-9900_9901_v1.pdf", "CCAP_DOS-9900_9901_v1.pdf", "AE_DOS-9900_9901_v1.pdf",
                        "LF_DOS-9900_9901_v1.pdf");
        assertThat(JsonPath.<List<Object>>read(pieces, "$[*].idDocumentFiche")).doesNotContainNull();

        int idPiece = JsonPath.<List<Integer>>read(pieces, "$[*].idPiece").get(0);
        mvc.perform(delete("/api/piece-jointe-dossiers/" + idPiece).header("Authorization", tokenPrmp))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PIECE_PRODUITE_PAR_FICHE"))
                .andExpect(jsonPath("$.message", containsString("fiche marché")));
        mvc.perform(delete("/api/piece-jointe-dossiers/" + idPiece).header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
        MockMultipartFile fichier = new MockMultipartFile("fichier", "dao.pdf", "application/pdf", pdfMinimal());
        MockMultipartFile data = new MockMultipartFile("data", "", JSON,
                ("{\"idDossier\":" + idDossier + ",\"idTypePiece\":" + typeDao + "}").getBytes());
        mvc.perform(multipart("/api/piece-jointe-dossiers").file(data).file(fichier).header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PIECE_PRODUITE_PAR_FICHE"));

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        valider();
        assertThat(JsonPath.<List<String>>read(pieces(idDossier), "$[*].nomFichier"))
                .containsExactlyInAnyOrder("DPAO_DOS-9900_9901_v2.pdf", "CCAP_DOS-9900_9901_v2.pdf", "AE_DOS-9900_9901_v2.pdf",
                        "LF_DOS-9900_9901_v2.pdf");

        mvc.perform(delete("/api/dossiers/" + idDossier + "/fiche-marche").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        assertThat(JsonPath.<List<Object>>read(pieces(idDossier), "$")).isEmpty();
        mvc.perform(put("/api/dossiers/" + idDossier + "/fiche-marche").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"idDmc\":" + idDmc + "}"))
                .andExpect(status().isOk());
        assertThat(JsonPath.<List<Object>>read(pieces(idDossier), "$")).hasSize(4);
    }

    // ------------------------------------------------------------------ 7. la Commission

    @Test
    @DisplayName("7 — Dossier soumis : le Chef de commission de la localité lit la liste et le contenu ; celui d'une autre "
            + "localité reçoit 403 sur les deux")
    void lectureParLaCommission() throws Exception {
        remplirEtValider();
        String dossier = mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Dossier d = dossierRepository.findById(JsonPath.<Integer>read(dossier, "$.idDossier")).orElseThrow();
        d.setStatut("SOUMIS");
        dossierRepository.saveAndFlush(d);

        String corps = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int idDoc = JsonPath.<List<Integer>>read(corps, "$[*].idDocument").get(0);
        mvc.perform(get("/api/fiches-marche/documents/" + idDoc + "/contenu").header("Authorization", tokenMembre))
                .andExpect(status().isOk());

        String ailleurs = bearer("CTRMEM9", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM9", "TMS");
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", ailleurs))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/fiches-marche/documents/" + idDoc + "/contenu").header("Authorization", ailleurs))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 8. plusieurs formes de garantie admises

    @Test
    @DisplayName("8 — Forme de la garantie de soumission à choix multiples (2026-09-26) : trois formes → enregistrées dans "
            + "l'ordre du référentiel, imprimées « l'une des formes suivantes : – soit … » (une par ligne) dans le DPAO et "
            + "l'acte d'engagement, docx et pdf ; une seule forme → ligne ordinaire ; aucune → bloquant OBLIGATOIRE")
    void formesDeGarantieAdmises() throws Exception {
        cadrage("{\"garantieSoumission\":\"OUI\"}");
        bloc("B02", "{\"B02-AU-01\":\"Direction des achats\"}");
        bloc("B04", "{\"B04-LR-02\":\"2026-04-10\"}");
        bloc("B08", "{\"B08-PA-50\":60}");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B05").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B05-GS-02\":[\"Chèque de banque\",\"garantie bancaire\","
                        + "\"Caution personnelle et solidaire d'un organisme agréé par le MEF\"],\"B05-GS-03\":1250000}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valeurs.B05-GS-02").value(
                        "Caution personnelle et solidaire d'un organisme agréé par le MEF,Garantie bancaire,Chèque de banque"))
                .andExpect(jsonPath("$.bilanControles.bloquants[?(@.champs[0]=='B05-GS-02')]", hasSize(0)));
        valider();
        String tournure = "Forme de la garantie de soumission : Une garantie de soumission doit être fournie dans l'une des "
                + "formes suivantes :";
        String caution = "– soit une caution personnelle et solidaire d'un organisme agréé par le MEF";
        String bancaire = "– soit une garantie bancaire";
        String cheque = "– soit un chèque de banque";
        for (String type : List.of("DPAO", "AE")) {
            String docx = texteDuDocx(contenu(type, "docx"));
            assertThat(docx).as(type + " docx").contains(tournure, caution, bancaire, cheque);
            assertThat(docx.indexOf(caution)).as(type + " : ordre du référentiel").isGreaterThan(docx.indexOf(tournure))
                    .isLessThan(docx.indexOf(bancaire));
            assertThat(docx.indexOf(bancaire)).isLessThan(docx.indexOf(cheque));
            assertThat(docx.lines()).as(type + " : une forme par ligne").contains(caution, bancaire, cheque);
            String pdf = texteDuPdf(contenu(type, "pdf"));
            assertThat(pdf).as(type + " pdf").contains("l'une des formes suivantes :", caution, bancaire, cheque);
            assertThat(pdf.indexOf(caution)).as(type + " pdf : ordre du référentiel").isLessThan(pdf.indexOf(bancaire));
            assertThat(pdf.indexOf(bancaire)).isLessThan(pdf.indexOf(cheque));
        }

        // une seule forme retenue : la ligne ordinaire, comme avant
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        bloc("B05", "{\"B05-GS-02\":\"Garantie bancaire\",\"B05-GS-03\":1250000}");
        valider();
        String dpao = texteDuDocx(contenu("DPAO", "docx"));
        assertThat(dpao).contains("Forme de la garantie de soumission : Garantie bancaire")
                .doesNotContain("– soit").doesNotContain("formes suivantes");

        // aucune forme quand la garantie est exigée : le bloquant OBLIGATOIRE, comme pour une valeur vide
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B05").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B05-GS-02\":[],\"B05-GS-03\":1250000}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valeurs.B05-GS-02").doesNotExist())
                .andExpect(jsonPath("$.bilanControles.bloquants[?(@.regle=='OBLIGATOIRE' && @.champs[0]=='B05-GS-02')]", hasSize(1)));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B05").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B05-GS-02\":[\"Garantie bancaire\",\"Lettre de crédit\"],\"B05-GS-03\":1250000}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='B05-GS-02')].message").value(hasItem(containsString("Lettre de crédit"))));
    }

    // ------------------------------------------------------------------ outils

    private void remplirEtValider() throws Exception {
        cadrage("{\"garantieSoumission\":\"NON\"}");
        bloc("B02", "{\"B02-AU-01\":\"Direction des achats\"}");
        bloc("B04", "{\"B04-LR-02\":\"2026-04-10\"}");
        bloc("B08", "{\"B08-PA-50\":60}");
        valider();
    }

    private void valider() throws Exception {
        besoinDeTest(idDmc);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
    }

    private byte[] contenu(String type, String extension) throws Exception {
        String corps = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.<List<Integer>>read(corps,
                "$[?(@.type=='" + type + "' && @.extension=='" + extension + "')].idDocument").get(0);
        return mvc.perform(get("/api/fiches-marche/documents/" + id + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private String pieces(int idDossier) throws Exception {
        return mvc.perform(get("/api/piece-jointe-dossiers").param("dossier", String.valueOf(idDossier))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String texteDuDocx(byte[] docx) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }

    private Long creerDmc() throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }

    private void cadrage(String cadrage) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":" + cadrage + "}")).andExpect(status().isOk());
    }

    private void bloc(String bloc, String valeurs) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/" + bloc).header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":" + valeurs + "}")).andExpect(status().isOk());
    }

    private Lot lot(int id, String designation, BigDecimal montant) {
        Lot lot = new Lot();
        lot.setIdLot(id);
        lot.setIdDossier(9900);
        lot.setIdDetail(9901);
        lot.setDesignationLot(designation);
        lot.setMontLot(montant);
        return lot;
    }

    private void champ(String code, String libelle, String type, String document, String reprises, String condition) {
        champ(code, libelle, type, document, reprises, condition, null, false);
    }

    private void champ(String code, String libelle, String type, String document, String reprises, String condition,
            String options, boolean obligatoire) {
        ChampFicheMarche c = new ChampFicheMarche();
        c.setCode(code);
        c.setCodeRubrique(code.substring(0, code.lastIndexOf('-')));
        c.setRang(Integer.parseInt(code.substring(code.lastIndexOf('-') + 1)));
        c.setLibelle(libelle);
        c.setType(type);
        c.setSource("SAISIE");
        c.setDocumentMaitre(document);
        c.setReprises(reprises);
        c.setTypesMarche("QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE");
        c.setObligatoire(obligatoire);
        c.setCondition(condition);
        c.setOptions(options);
        c.setActif(true);
        champRepository.save(c);
    }
}
