package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.TypeDmc;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.repository.FicheMarcheValeurRepository;

/**
 * ⚠️ <strong>Avis spécifique d'appel d'offres</strong> (demande front du 2026-09-30, §B3, §B4, §B6) — la garde (raisons de
 * {@code AVIS_INDISPONIBLE}, dont un PV FAVR avant puis après la levée des réserves), l'impression sur la dernière version
 * validée, deux impressions successives, une révision ouverte, les informations de publication hors de la fiche, l'avis
 * jamais joint au dossier comme pièce du DAO.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), ligne 9901 de fournitures à quantité fixe (mode 92 → DAO),
 * ligne 9902 de prestations intellectuelles (nature 93). Le dossier DAO de la ligne 9901 est produit par la fiche ; sa
 * chaîne réception → dispatch → examen → PV (9950) est posée à la main, avec l'avis et le statut voulus.</p>
 */
class AvisSpecifiqueIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String CORPS = "{\"datePublication\":\"2026-10-05\",\"jmpNumero\":\"123\",\"jmpDate\":\"2026-01-15\","
            + "\"supports\":\"le quotidien Midi Madagasikara du 06/10/2026\"}";

    @Autowired private FicheMarcheValeurRepository valeurRepository;

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
        marcheRepository.save(l);
        natureRepository.save(new cnm.prs.entity.Nature(93, "Prestations intellectuelles", null, "PRESTATIONS_INTELLECTUELLES"));
        Marche pi = marcheDao(9902, 9900, 9900);
        pi.setIdMode(92);
        pi.setIdNature(93);
        pi.setDesignationMarche("Étude de faisabilité");
        marcheRepository.save(pi);

        TypePieceJointe t = typePieceJointeRepository.findById(seedTypePiece("Dossier d'appel d'offres complet", true, "DMC", 1))
                .orElseThrow();
        t.setCode("DAO_COMPLET");
        typePieceJointeRepository.save(t);

        idDmc = creerDmc(9901);
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"NON\",\"alloti\":\"NON\",\"typePrix\":\"UNITAIRES\"}}")).andExpect(status().isOk());   // alloti : imposé par le plan à l'écran
        valider();
    }

    @Test
    @DisplayName("Garde : sans dossier, PV non signé, PV FAVR avant la levée (409 AVIS_INDISPONIBLE, raison dans details) "
            + "puis après (OBSERVATIONS_LEVEES, DECISION_TRANSMISE_SIGMP, CLOTURE), DEF, prestations intellectuelles")
    void garde() throws Exception {
        disponibilite(idDmc).andExpect(jsonPath("$.disponible").value(false)).andExpect(jsonPath("$.raison").value("SANS_DOSSIER"));
        imprimer(idDmc).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AVIS_INDISPONIBLE"))
                .andExpect(jsonPath("$.details.raison").value("SANS_DOSSIER"));

        int idDossier = creerDossier();
        disponibilite(idDmc).andExpect(jsonPath("$.raison").value("PV_NON_SIGNE"))
                .andExpect(jsonPath("$.idDossierSoumis").value(idDossier));

        pvSigne(idDossier, "FAVR", "EN_VERIFICATION");
        disponibilite(idDmc).andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.raison").value("RESERVES_NON_LEVEES")).andExpect(jsonPath("$.idAvis").value("FAVR"))
                .andExpect(jsonPath("$.statutPv").value("SIGNE")).andExpect(jsonPath("$.statutDossier").value("EN_VERIFICATION"));
        imprimer(idDmc).andExpect(status().isConflict()).andExpect(jsonPath("$.details.raison").value("RESERVES_NON_LEVEES"));
        for (String levee : List.of("OBSERVATIONS_LEVEES", "DECISION_TRANSMISE_SIGMP", "CLOTURE")) {
            statut(idDossier, levee);
            disponibilite(idDmc).andExpect(jsonPath("$.disponible").value(true)).andExpect(jsonPath("$.raison").isEmpty());
        }
        statut(idDossier, "EN_ATTENTE_DECISION_PRMP");
        disponibilite(idDmc).andExpect(jsonPath("$.raison").value("RESERVES_NON_LEVEES"));

        avis(idDossier, "FAV");
        disponibilite(idDmc).andExpect(jsonPath("$.disponible").value(true));   // FAV : dès le PV signé
        avis(idDossier, "DEF");
        disponibilite(idDmc).andExpect(jsonPath("$.raison").value("AVIS_NON_FAVORABLE"));

        Long pi = creerDmc(9902);
        disponibilite(pi).andExpect(jsonPath("$.raison").value("CATEGORIE_SANS_AVIS"));

        mvc.perform(get("/api/fiches-marche/" + idDmc + "/avis-specifique/disponibilite").header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/avis-specifique").header("Authorization", tokenMembre)
                .contentType(JSON).content(CORPS)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Impression : 400 nominatif si une information manque ou si une date est illisible ; 201, paire .docx / .pdf "
            + "de type AVIS avec les informations de publication rendues JJ/MM/AAAA et gardées en trace, absentes de la fiche ; "
            + "une seconde impression ajoute une paire ; l'avis n'est pas joint au dossier")
    void impression() throws Exception {
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAV", "PV_SIGNE");

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/avis-specifique").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"datePublication\":\"05/10/2026\",\"jmpNumero\":\"123\",\"jmpDate\":\"2026-01-15\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='datePublication')]").isNotEmpty())
                // ⚠️ 2026-10-01 (§B7.5) — le numéro du JMP et les supports sont facultatifs : seules les deux dates sont exigées.
                .andExpect(jsonPath("$.erreurs[?(@.champ=='supports')]").isEmpty())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='jmpNumero')]").isEmpty());

        String premier = imprimer(idDmc).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(premier, "$[*].type")).containsExactly("AVIS", "AVIS");
        assertThat(JsonPath.<List<String>>read(premier, "$[*].extension")).containsExactly("docx", "pdf");
        assertThat(JsonPath.<List<String>>read(premier, "$[*].libelle")).containsOnly("Avis spécifique d'appel d'offres");
        assertThat(JsonPath.<List<Integer>>read(premier, "$[*].version")).containsOnly(1);
        assertThat(JsonPath.<List<String>>read(premier, "$[*].publication.datePublication")).containsOnly("2026-10-05");
        assertThat(JsonPath.<List<String>>read(premier, "$[*].nomFichier")).allMatch(n -> n.startsWith("AVIS_") && n.contains("_v1_"));
        int idDocx = JsonPath.<List<Integer>>read(premier, "$[?(@.extension=='docx')].idDocument").get(0);
        byte[] docx = mvc.perform(get("/api/fiches-marche/documents/" + idDocx + "/contenu")
                .header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(doc.getAllPictures()).as("l'emblème en tête (§B7.8)").hasSize(1);
        }
        String texte = texteDuDocx(docx);
        assertThat(texte).contains("05/10/2026", "Journal des Marchés Publics n°123 en date du 15/01/2026",
                "le quotidien Midi Madagasikara du 06/10/2026", "Fourniture de mobilier de bureau")
                .doesNotContain("{{", "2026-10-05");
        // Les informations de publication n'entrent pas dans la fiche.
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int idFiche = JsonPath.read(fiche, "$.idFiche");
        assertThat(valeurRepository.findByIdFiche(idFiche)).noneMatch(v -> v.getCodeChamp().startsWith("AVIS")
                || String.valueOf(v.getValeur()).contains("Midi Madagasikara"));

        imprimer(idDmc).andExpect(status().isCreated());
        String liste = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> avis = JsonPath.read(liste, "$[?(@.type=='AVIS')].idDocument");
        assertThat(avis).hasSize(4).isSortedAccordingTo(java.util.Comparator.reverseOrder());   // du plus récent au plus ancien
        assertThat(JsonPath.<List<String>>read(liste, "$[?(@.type!='AVIS')].type")).contains("DPAO", "CCAP", "AE");

        // Rattachement de secours du dossier à la fiche : les PDF du DAO sont joints, jamais l'avis.
        statut(idDossier, "BROUILLON");
        mvc.perform(delete("/api/dossiers/" + idDossier + "/fiche-marche").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        mvc.perform(put("/api/dossiers/" + idDossier + "/fiche-marche").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"idDmc\":" + idDmc + "}")).andExpect(status().isOk());
        String pieces = mvc.perform(get("/api/piece-jointe-dossiers").param("dossier", String.valueOf(idDossier))
                .header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(pieces, "$[*].nomFichier")).isNotEmpty().noneMatch(n -> n.startsWith("AVIS_"));
    }

    @Test
    @DisplayName("Fiche révisée pendant la rectification (EN_ATTENTE_DECISION_PRMP) puis revalidée : après la levée des "
            + "réserves, l'avis se lit sur la dernière version VALIDÉE (v2) ; une révision n'est possible que dossier rendu "
            + "à la PRMP, jamais une fois les réserves levées")
    void ficheRevisee() throws Exception {
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAVR", "EN_ATTENTE_DECISION_PRMP");
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B02").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{}}")).andExpect(status().isOk());
        valider();
        statut(idDossier, "OBSERVATIONS_LEVEES");
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOSSIER_EN_EXAMEN"));

        String produit = imprimer(idDmc).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(produit, "$[*].version")).containsOnly(2);
        assertThat(JsonPath.<List<String>>read(produit, "$[*].nomFichier")).allMatch(n -> n.contains("_v2_"));
        String liste = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Map<String, String>>>read(liste, "$[?(@.type=='AVIS')].publication"))
                .hasSize(2).allMatch(p -> "123".equals(p.get("jmpNumero")));
        // La version 1 n'a pas d'avis.
        String v1 = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").param("version", "1")
                .header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(v1, "$[*].type")).doesNotContain("AVIS");
    }

    @Test
    @DisplayName("Statut « Lancé » (décision du 30/09, §B2) — la fiche laisse la ligne PREVU ; la première impression la passe "
            + "LANCE (journal LIGNE_LANCEE, avisImprimeLe) ; une réimpression ne change rien et n'écrit rien")
    void premiereImpressionLanceLaLigne() throws Exception {
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAV", "PV_SIGNE");
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PREVU")).andExpect(jsonPath("$.avisImprimeLe").isEmpty());

        imprimer(idDmc).andExpect(status().isCreated());
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("LANCE"))
                .andExpect(jsonPath("$.avisImprimeLe").value(java.time.LocalDate.now().toString()));
        assertThat(lancements()).containsExactly(
                "Ligne 9901 : avis spécifique imprimé (publication du 05/10/2026), statut PREVU → LANCE");

        imprimer(idDmc).andExpect(status().isCreated());
        assertThat(lancements()).hasSize(1);
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(jsonPath("$.statut").value("LANCE"));
    }

    @Test
    @DisplayName("Statut « Lancé » (§B2) — un statut manuel (CHDP) n'est pas écrasé ; l'impression reste possible, le journal "
            + "le dit")
    void statutManuelConserve() throws Exception {
        Marche l = marcheRepository.findById(9901).orElseThrow();
        l.setStatut("CHDP");
        marcheRepository.saveAndFlush(l);
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAV", "PV_SIGNE");
        imprimer(idDmc).andExpect(status().isCreated());
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(jsonPath("$.statut").value("CHDP"));
        assertThat(lancements()).containsExactly(
                "Ligne 9901 : avis spécifique imprimé (publication du 05/10/2026), statut CHDP conservé (statut manuel)");
    }

    @Test
    @DisplayName("§B7.5 / §B8 (2026-10-01) — impression sans numéro de JMP ni supports : 201, pas de « et dans », pointillés ; "
            + "le compte bancaire de l'ARMP réglé par l'Administrateur est imprimé, des pointillés tant qu'il ne l'est pas")
    void publicationMinimaleEtCompte() throws Exception {
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAV", "PV_SIGNE");
        String minimal = "{\"datePublication\":\"2026-10-05\",\"jmpDate\":\"2026-01-15\"}";
        String r = mvc.perform(post("/api/fiches-marche/" + idDmc + "/avis-specifique").header("Authorization", tokenPrmp)
                .contentType(JSON).content(minimal)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String sansCompte = docxTexte(r);
        assertThat(sansCompte).contains("n°………", "compte bancaire de l’ARMP : ………").doesNotContain("et dans", "{{");

        mvc.perform(put("/api/parametres/compte-dao").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"banque\":\"BNI Madagascar\",\"titulaire\":\"ARMP\",\"numeroCompte\":\"00005 00001 12345678901 23\"}"))
                .andExpect(status().isOk());
        r = mvc.perform(post("/api/fiches-marche/" + idDmc + "/avis-specifique").header("Authorization", tokenPrmp)
                .contentType(JSON).content(minimal)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(docxTexte(r)).contains("BNI Madagascar, compte n° 00005 00001 12345678901 23 au nom de ARMP");
    }

    private String docxTexte(String produits) throws Exception {
        int id = JsonPath.<List<Integer>>read(produits, "$[?(@.extension=='docx')].idDocument").get(0);
        return texteDuDocx(mvc.perform(get("/api/fiches-marche/documents/" + id + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).replace(' ', ' ').replace(' ', ' ');
    }

    // ------------------------------------------------------------------ outils

    /** Les détails du journal LIGNE_LANCEE du plan 9900. */
    private List<String> lancements() throws Exception {
        String journal = mvc.perform(get("/api/dossiers/9900/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(journal, "$[?(@.typeAction=='LIGNE_LANCEE')].detail");
    }

    private org.springframework.test.web.servlet.ResultActions disponibilite(Long dmc) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + dmc + "/avis-specifique/disponibilite").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions imprimer(Long dmc) throws Exception {
        return mvc.perform(post("/api/fiches-marche/" + dmc + "/avis-specifique").header("Authorization", tokenPrmp)
                .contentType(JSON).content(CORPS));
    }

    private void valider() throws Exception {
        besoinDeTest(idDmc);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp)).andExpect(status().isOk());
    }

    private Long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }

    private int creerDossier() throws Exception {
        String corps = mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corps, "$.idDossier");
    }

    /** La chaîne réception → dispatch → examen → PV signé du dossier DAO, avec l'avis et le statut du dossier voulus. */
    private void pvSigne(int idDossier, String avis, String statutDossier) {
        receptionRepository.save(reception(9950, idDossier, "CTRCC1", true));
        dispatchRepository.save(dispatch(9950, 9950, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9950, 9950, "CTRMEM"));
        seedPvSigne(9950, 9950);
        avis(idDossier, avis);
        statut(idDossier, statutDossier);
    }

    private void avis(int idDossier, String avis) {
        PvExamen pv = pvExamenRepository.findSignesParDossierRows(idDossier).get(0);
        pv.setIdAvis(avis);
        pvExamenRepository.save(pv);
    }

    private void statut(int idDossier, String statut) {
        Dossier d = dossierRepository.findById(idDossier).orElseThrow();
        d.setStatut(statut);
        dossierRepository.saveAndFlush(d);
    }

    private static String texteDuDocx(byte[] docx) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }
}
