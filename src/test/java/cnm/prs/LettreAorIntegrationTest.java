package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.TypeDmc;
import cnm.prs.entity.TypePieceJointe;

/**
 * ⚠️ R3c (manuel révisé pour l'application, lot 1, §B3 ; 2026-10-13) — la <strong>lettre d'invitation à soumissionner de l'appel
 * d'offres restreint</strong> (LETTRE-AOR, 2-II-D pièce 5) : une fiche de fournitures dont le mode du plan est restreint n'a pas
 * d'avis ({@code MODE_SANS_AVIS}), son projet dès la fiche est la lettre, son dossier est un {@code DAOR}, ses lettres s'impriment
 * avec des candidats saisis et lancent la ligne.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), ligne 9901 de fournitures, mode 93 « Appel d'offres restreint ».</p>
 */
class LettreAorIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String CORPS = "{\"dateEnvoi\":\"2026-10-05\",\"lieu\":\"Antananarivo\",\"candidats\":["
            + "{\"nom\":\"Société A\",\"adresse\":\"Lot II A 12\\nAntananarivo\"},{\"nom\":\"Entreprise B\",\"adresse\":\"Toamasina\"}]}";

    private Long idDmc;

    @BeforeEach
    void jeu() throws Exception {
        TypeDmc dao = typeDmcRepository.findByCode("DAO").orElseThrow();
        ModePassation m93 = new ModePassation(93, "Appel d'offres restreint", null, null, null, null);
        m93.setIdTypeDmc(dao.getIdTypeDmc());
        modePassationRepository.save(m93);
        dossierRepository.save(dossierLoc(9900, "CLOTURE", "ANT", "PRMP001"));
        Dossier plan = dossierRepository.findById(9900).orElseThrow();
        plan.setIdEntiteContract(1);
        dossierRepository.save(plan);
        ppmRepository.save(ppm(9900, 9900, "PRMP001"));
        receptionRepository.save(reception(9900, 9900, "CTRCC1", true));
        dispatchRepository.save(dispatch(9900, 9900, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9900, 9900, "CTRMEM"));
        seedPvSigne(9900, 9900);
        publicationAgpmDeTest(9900);   // ⚠️ V118 (H1) : l'avis lit le journal de l'avis général sur le plan
        Marche l = marcheDao(9901, 9900, 9900);
        l.setIdMode(93);
        l.setDesignationMarche("Fourniture de mobilier de bureau");
        marcheRepository.save(l);
        TypePieceJointe t = typePieceJointeRepository.findById(seedTypePiece("Dossier d'appel d'offres complet", true, "DMC", 1))
                .orElseThrow();
        t.setCode("DAO_COMPLET");
        typePieceJointeRepository.save(t);

        idDmc = creerDmc(9901);
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"NON\",\"alloti\":\"NON\",\"typePrix\":\"UNITAIRES\"}}")).andExpect(status().isOk());
        besoinDeTest(idDmc);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Appel d'offres restreint : pas d'avis (MODE_SANS_AVIS, disponibilité, impression, projet) ; la lettre est disponible "
            + "(SANS_DOSSIER), son projet LETTRE_PROJET ; le dossier est un DAOR ; les mentions de LETTRE-AOR sont au gabarit ; "
            + "l'impression avec candidats saisis rend une lettre « à soumissionner » par candidat et lance la ligne ; sans candidats → 400")
    void lettreAor() throws Exception {
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/avis-specifique/disponibilite").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.raison").value("MODE_SANS_AVIS"));
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/avis-specifique").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"datePublication\":\"2026-10-05\",\"jmpDate\":\"2026-01-15\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AVIS_INDISPONIBLE")).andExpect(jsonPath("$.details.raison").value("MODE_SANS_AVIS"));
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/avis-specifique/projet").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.details.raison").value("MODE_SANS_AVIS"));
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/avis-specifique/mentions").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.details.raison").value("MODE_SANS_AVIS"));

        mvc.perform(get("/api/fiches-marche/" + idDmc + "/lettres-invitation/disponibilite").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.raison").value("SANS_DOSSIER"));
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/lettres-invitation/projet").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("LETTRE_PROJET_")));
        String mentions = mvc.perform(get("/api/fiches-marche/" + idDmc + "/lettres-invitation/mentions").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.gabarit").value("LETTRE-AOR"))
                .andExpect(jsonPath("$.gabaritComplet").value(true)).andExpect(jsonPath("$.documentComplet").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(mentions, "$.mentions[?(@.renseignee==false)].code")).contains("LISTE_CANDIDATS", "COMPTE_PAIEMENT");

        int idDossier = creerDossier();
        mvc.perform(get("/api/dossiers/" + idDossier).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.idSousType").value("DAOR"));
        pvSigne(idDossier, "FAV", "PV_SIGNE");

        String produits = mvc.perform(post("/api/fiches-marche/" + idDmc + "/lettres-invitation").header("Authorization", tokenPrmp)
                .contentType(JSON).content(CORPS)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(produits, "$[*].type")).hasSize(4).containsOnly("LETTRE_INVITATION");
        int idDocx = JsonPath.<List<Integer>>read(produits, "$[?(@.extension=='docx')].idDocument").get(0);
        byte[] docx = mvc.perform(get("/api/fiches-marche/documents/" + idDocx + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String texte = texteDuDocx(docx).replace(' ', ' ').replace(' ', ' ');
        assertThat(texte).contains("LETTRE D’INVITATION À SOUMISSIONNER", "liste restreinte", "article 38", "- Société A", "- Entreprise B",
                "Fourniture de mobilier de bureau", "au plus tard le", "La garantie de soumission n’est pas requise.", "Antananarivo, 05/10/2026")
                .doesNotContain("{{", "proposition", "Dossier de Consultation");
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(jsonPath("$.statut").value("LANCE"));
        String journal = mvc.perform(get("/api/dossiers/" + idDossier + "/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='LETTRES_INVITATION_IMPRIMEES')].detail"))
                .singleElement().asString().startsWith("2 lettre(s) d'invitation imprimée(s)");

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/lettres-invitation").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateEnvoi\":\"2026-10-05\",\"lieu\":\"Antananarivo\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='candidats')]").isNotEmpty());
    }

    // ------------------------------------------------------------------ outils

    private static String texteDuDocx(byte[] docx) throws Exception {
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(docx));
                org.apache.poi.xwpf.extractor.XWPFWordExtractor ex = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc)) {
            return ex.getText();
        }
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

    private void pvSigne(int idDossier, String avis, String statutDossier) {
        receptionRepository.save(reception(9950, idDossier, "CTRCC1", true));
        dispatchRepository.save(dispatch(9950, 9950, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9950, 9950, "CTRMEM"));
        seedPvSigne(9950, 9950);
        PvExamen pv = pvExamenRepository.findSignesParDossierRows(idDossier).get(0);
        pv.setIdAvis(avis);
        pvExamenRepository.save(pv);
        Dossier d = dossierRepository.findById(idDossier).orElseThrow();
        d.setStatut(statutDossier);
        dossierRepository.saveAndFlush(d);
    }
}
