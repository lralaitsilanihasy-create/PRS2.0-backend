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
        // ⚠️ V155 (« appel-offres-restreint », §B2) — sans motif du recours, la fiche ne se valide pas.
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.modeRestreint").value(true))
                .andExpect(jsonPath("$.bilanControles.bloquants[?(@.regle=='MOTIF_RESTREINT')]").isNotEmpty());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.code").value("CONTROLES_BLOQUANTS"));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/motif-restreint").header("Authorization", tokenPrmp).contentType(JSON)
                .content(MOTIF)).andExpect(status().isOk()).andExpect(jsonPath("$.libelle").value("Urgence avérée"));
        // ⚠️ V156 (§B3) — ni sans sa liste restreinte.
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.bilanControles.bloquants[?(@.regle=='LISTE_RESTREINTE')]").isNotEmpty());
        candidatRepository.save(new cnm.prs.entity.CompteCandidat("C900000077", "groupement.c@entreprise.mg", "034 22 222 77", "Rabe", "Paul",
                cnm.prs.entity.CompteCandidat.CONFIRME, false, java.time.LocalDateTime.now(), java.time.LocalDateTime.now(), null, null));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/liste-restreinte").header("Authorization", tokenPrmp).contentType(JSON)
                .content(LISTE)).andExpect(status().isOk()).andExpect(jsonPath("$.libelleSource").value("Registre de l'ARMP"))
                .andExpect(jsonPath("$.candidats[2].rang").value(3)).andExpect(jsonPath("$.candidats[2].idCandidat").value("C900000077"));
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp)).andExpect(status().isOk());
    }

    private static final String LISTE = "{\"source\":\"registre_armp\",\"candidats\":["
            + "{\"nom\":\"Société A\",\"adresse\":\"Lot II A 12\\nAntananarivo\",\"nif\":\"4000123456\"},"
            + "{\"nom\":\"Entreprise B\",\"adresse\":\"Toamasina\",\"email\":\"entreprise.b@exemple.mg\"},"
            + "{\"nom\":\"Groupement C\",\"adresse\":\"Fianarantsoa\",\"idCandidat\":\"C900000077\"}]}";

    @org.springframework.beans.factory.annotation.Autowired
    private cnm.prs.repository.CompteCandidatRepository candidatRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private cnm.prs.repository.InvitationRepository invitationRepository;

    @Test
    @DisplayName("⚠️ V156 (« appel-offres-restreint », §B3) — la liste restreinte saisie : 400 nominatif (moins de trois, source inconnue "
            + "ou APPEL_CANDIDATURES, doublon, compte inconnu, adresse manquante), servie avec la version, figée (409), copiée à la "
            + "révision, reprise par la fiche de présentation ; hors du mode restreint → 409 LISTE_HORS_RESTREINT")
    void listeRestreinte() throws Exception {
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/liste-restreinte").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("REGISTRE_ARMP")).andExpect(jsonPath("$.candidats.length()").value(3));
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.listeRestreinte.candidats[0].nif").value("4000123456"))
                .andExpect(jsonPath("$.bilanControles.ok[?(@.regle=='LISTE_RESTREINTE')]").isNotEmpty());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/liste-restreinte").header("Authorization", tokenPrmp).contentType(JSON)
                .content(LISTE)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_VALIDEE"));

        int idDossier = creerDossier();
        String presentation = mvc.perform(get("/api/dossiers/" + idDossier + "/fiche-presentation").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // ⚠️ 2026-11-03 (« titre-donnees-particulieres ») — fournitures, quantité fixe : DPAO.
        assertThat(JsonPath.<List<String>>read(presentation, "$.sections[?(@.code=='PROCEDURE')].titre"))
                .containsExactly("Données particulières (DPAO)");
        assertThat(JsonPath.<List<String>>read(presentation, "$.sections[*].lignes[?(@.code=='LISTE_RESTREINTE')].valeur"))
                .containsExactly("3 candidats (Registre de l'ARMP) : Société A ; Entreprise B ; Groupement C");

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.listeRestreinte.candidats.length()").value(3));
        String erreurs = mvc.perform(put("/api/fiches-marche/" + idDmc + "/liste-restreinte").header("Authorization", tokenPrmp)
                .contentType(JSON).content("{\"source\":\"APPEL_CANDIDATURES\",\"candidats\":[{\"nom\":\"Société A\",\"adresse\":\"X\"},"
                        + "{\"nom\":\"société a\",\"adresse\":\" \",\"idCandidat\":\"C999999999\"}]}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(erreurs, "$.erreurs[*].champ")).contains("source", "candidats", "candidats[1].nom",
                "candidats[1].adresse", "candidats[1].idCandidat");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/liste-restreinte").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"source\":\"INCONNUE\",\"candidats\":[]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='source')]").isNotEmpty());

        ModePassation m93 = modePassationRepository.findById(93).orElseThrow();
        m93.setLibelle("Appel d'offres ouvert");
        modePassationRepository.save(m93);
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/liste-restreinte").header("Authorization", tokenPrmp).contentType(JSON)
                .content(LISTE)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LISTE_HORS_RESTREINT"));
    }

    private static final String MOTIF = "{\"motif\":\"urgence_averee\",\"justification\":\"Rupture du stock de mobilier avant la rentrée.\"}";

    @Test
    @DisplayName("⚠️ V155 (« appel-offres-restreint », §B2) — le motif du recours : 400 nominatif (motif inconnu, justification vide), "
            + "servi avec la version et au bilan (ok), figé (409 FICHE_VALIDEE), copié à la révision, repris par la fiche de présentation ; "
            + "une ligne hors du mode restreint → 409 MOTIF_HORS_RESTREINT")
    void motifDuRecours() throws Exception {
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.motifRestreint.motif").value("URGENCE_AVEREE"))
                .andExpect(jsonPath("$.motifRestreint.justification").value("Rupture du stock de mobilier avant la rentrée."))
                .andExpect(jsonPath("$.bilanControles.ok[?(@.regle=='MOTIF_RESTREINT')]").isNotEmpty());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/motif-restreint").header("Authorization", tokenPrmp).contentType(JSON)
                .content(MOTIF)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_VALIDEE"));

        int idDossier = creerDossier();
        String presentation = mvc.perform(get("/api/dossiers/" + idDossier + "/fiche-presentation").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(presentation, "$.sections[*].lignes[?(@.code=='MOTIF_RESTREINT')].valeur"))
                .containsExactly("Urgence avérée");
        assertThat(JsonPath.<List<String>>read(presentation, "$.sections[*].lignes[?(@.code=='JUSTIFICATION_RESTREINT')].valeur"))
                .containsExactly("Rupture du stock de mobilier avant la rentrée.");

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.motifRestreint.motif").value("URGENCE_AVEREE"));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/motif-restreint").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"AUTRE\",\"justification\":\" \"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='motif')]").isNotEmpty())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='justification')]").isNotEmpty());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/motif-restreint").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"PETIT_NOMBRE\",\"justification\":\"Trois fabricants agréés.\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/motif-restreint").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.motif").value("PETIT_NOMBRE"));

        ModePassation m93 = modePassationRepository.findById(93).orElseThrow();
        m93.setLibelle("Appel d'offres ouvert");
        modePassationRepository.save(m93);
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/motif-restreint").header("Authorization", tokenPrmp).contentType(JSON)
                .content(MOTIF)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MOTIF_HORS_RESTREINT"));
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
        // ⚠️ V156 (§B3) — la liste restreinte de la fiche fait foi : trois lettres, les deux candidats du corps sont ignorés.
        assertThat(JsonPath.<List<String>>read(produits, "$[*].type")).hasSize(6).containsOnly("LETTRE_INVITATION");
        assertThat(invitationRepository.findByIdDmcOrderByRangAsc(idDmc)).extracting(cnm.prs.entity.Invitation::getIdCandidat)
                .containsExactly(null, null, "C900000077");
        int idDocx = JsonPath.<List<Integer>>read(produits, "$[?(@.extension=='docx')].idDocument").get(0);
        byte[] docx = mvc.perform(get("/api/fiches-marche/documents/" + idDocx + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String texte = texteDuDocx(docx).replace(' ', ' ').replace(' ', ' ');
        assertThat(texte).contains("LETTRE D’INVITATION À SOUMISSIONNER", "liste restreinte", "article 38", "- Société A", "- Entreprise B",
                "- Groupement C",
                "Fourniture de mobilier de bureau", "au plus tard le", "La garantie de soumission n’est pas requise.", "Antananarivo, 05/10/2026")
                .doesNotContain("{{", "proposition", "Dossier de Consultation");
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(jsonPath("$.statut").value("LANCE"));
        String journal = mvc.perform(get("/api/dossiers/" + idDossier + "/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='LETTRES_INVITATION_IMPRIMEES')].detail"))
                .singleElement().asString().startsWith("3 lettre(s) d'invitation imprimée(s)");

        // ⚠️ 2026-11-02 (§B5) — le projet de décision autorisant le recours, joint d'office au DAOR sous DECISION_AO_RESTR.
        int idTypeDecision = typePieceJointeRepository.findFirstByCode("DECISION_AO_RESTR").orElseThrow().getIdTypePiece();
        String pieces = mvc.perform(get("/api/piece-jointe-dossiers").param("dossier", String.valueOf(idDossier)).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> decisions = JsonPath.read(pieces, "$[?(@.idTypePiece==" + idTypeDecision + ")].idPiece");
        assertThat(decisions).hasSize(1);
        byte[] pdf = mvc.perform(get("/api/piece-jointe-dossiers/" + decisions.get(0) + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String decision;
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            decision = new org.apache.pdfbox.text.PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
        }
        assertThat(decision).contains("DÉCISION N°", "autorisant le recours à l'appel d'offres restreint", "urgence avérée",
                "Rupture du stock de mobilier", "Fourniture de mobilier de bureau", "1. Société A", "3. Groupement C", "registre de l'armp",
                "modèle provisoire");
        // ⚠️ 2026-11-02 (§B5) — la preuve des lettres d'un AOR : les lettres avec accusé, sans journal.
        org.springframework.mock.web.MockMultipartFile scan = new org.springframework.mock.web.MockMultipartFile("fichier", "lettres.pdf",
                "application/pdf", "%PDF-1.4 lettres".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/fiches-marche/" + idDmc + "/publication-avis")
                .file(scan).param("dateParution", "2026-10-05").header("Authorization", tokenPrmp)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.nature").value("LETTRES_INVITATION"));

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/lettres-invitation").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"lieu\":\"Antananarivo\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='dateEnvoi')]").isNotEmpty())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='candidats')]").isEmpty());
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
