package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;

/**
 * ⚠️ <strong>Lettres d'invitation des prestations intellectuelles</strong> (lot AV-4.1, demande front du 2026-10-01,
 * §B3-§B6) — la garde (celle de l'avis, {@code CATEGORIE_SANS_LETTRE} pour les fournitures), les 400 nominatifs, une
 * paire par candidat avec son destinataire et la même liste, la trace, le journal, le statut « Lancé » à la première
 * impression, jamais jointes au dossier.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), ligne 9901 de prestations intellectuelles à quantité fixe
 * (nature 94, mode 92 → DAO), ligne 9902 de fournitures. La chaîne réception → examen → PV (9950) du dossier DAO est
 * posée à la main, avec l'avis et le statut voulus.</p>
 */
class LettreInvitationIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String CORPS = "{\"dateEnvoi\":\"2026-10-05\",\"lieu\":\"Antananarivo\",\"candidats\":["
            + "{\"nom\":\"Cabinet A\",\"adresse\":\"Lot II A 12\\nAntananarivo\"},"
            + "{\"nom\":\"Bureau B\",\"adresse\":\"Rue Rainandriamampandry, Toamasina\"}]}";

    @org.springframework.beans.factory.annotation.Autowired private cnm.prs.service.ChampFicheMarcheService champService;

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
        natureRepository.save(new Nature(94, "Prestations intellectuelles", null, "PRESTATIONS_INTELLECTUELLES"));
        Marche l = marche(9901, 9900, 9900);
        l.setIdMode(92);
        l.setFormeMarche(FormeMarche.QUANTITE_FIXE);
        l.setIdNature(94);
        l.setDesignationMarche("Étude de faisabilité du schéma directeur");
        marcheRepository.save(l);
        Marche f = marcheDao(9902, 9900, 9900);
        f.setIdMode(92);
        f.setDesignationMarche("Fourniture de mobilier de bureau");
        marcheRepository.save(f);
        cnm.prs.entity.TypePieceJointe t = typePieceJointeRepository.findById(
                seedTypePiece("Dossier d'appel d'offres complet", true, "DMC", 1)).orElseThrow();
        t.setCode("DAO_COMPLET");
        typePieceJointeRepository.save(t);
        champService.importerCsv(new ClassPathResource(
                "fiche-marche/referentiel-champs-fiche-dao-prestations-intellectuelles.csv").getFile().toPath());

        idDmc = creerDmc(9901);
        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "PRESTATIONS_INTELLECTUELLES",
                Map.of("B02-MS-01", "Budget prédéterminé dont le candidat propose la meilleure utilisation"));
    }

    @Test
    @DisplayName("Garde (celle de l'avis) : sans dossier, PV non signé, FAVR avant puis après la levée, DEF (409 "
            + "LETTRE_INDISPONIBLE, raison dans details) ; fournitures → CATEGORIE_SANS_LETTRE ; Administrateur et Membre : 403")
    void garde() throws Exception {
        disponibilite(idDmc).andExpect(jsonPath("$.disponible").value(false)).andExpect(jsonPath("$.raison").value("SANS_DOSSIER"));
        imprimer(idDmc, CORPS).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LETTRE_INDISPONIBLE"))
                .andExpect(jsonPath("$.details.raison").value("SANS_DOSSIER"));

        int idDossier = creerDossier();
        disponibilite(idDmc).andExpect(jsonPath("$.raison").value("PV_NON_SIGNE"))
                .andExpect(jsonPath("$.idDossierSoumis").value(idDossier));
        pvSigne(idDossier, "FAVR", "EN_VERIFICATION");
        disponibilite(idDmc).andExpect(jsonPath("$.raison").value("RESERVES_NON_LEVEES")).andExpect(jsonPath("$.idAvis").value("FAVR"))
                .andExpect(jsonPath("$.statutPv").value("SIGNE")).andExpect(jsonPath("$.statutDossier").value("EN_VERIFICATION"));
        imprimer(idDmc, CORPS).andExpect(status().isConflict()).andExpect(jsonPath("$.details.raison").value("RESERVES_NON_LEVEES"));
        statut(idDossier, "OBSERVATIONS_LEVEES");
        disponibilite(idDmc).andExpect(jsonPath("$.disponible").value(true)).andExpect(jsonPath("$.raison").isEmpty());
        avis(idDossier, "DEF");
        disponibilite(idDmc).andExpect(jsonPath("$.raison").value("AVIS_NON_FAVORABLE"));

        Long fournitures = creerDmc(9902);
        disponibilite(fournitures).andExpect(jsonPath("$.raison").value("CATEGORIE_SANS_LETTRE"));
        // Le pendant : la fiche PI n'a pas d'avis spécifique.
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/avis-specifique/disponibilite").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.raison").value("CATEGORIE_SANS_AVIS"));

        mvc.perform(get("/api/fiches-marche/" + idDmc + "/lettres-invitation/disponibilite").header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/lettres-invitation").header("Authorization", tokenMembre)
                .contentType(JSON).content(CORPS)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("400 nominatif : corps vide (dateEnvoi, lieu, candidats), date illisible, liste vide, nom et adresse d'un "
            + "candidat manquants (candidats[i].nom, candidats[i].adresse)")
    void saisieInvalide() throws Exception {
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAV", "PV_SIGNE");
        imprimer(idDmc, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='dateEnvoi')]").isNotEmpty())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='lieu')]").isNotEmpty())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='candidats')]").isNotEmpty());
        imprimer(idDmc, "{\"dateEnvoi\":\"05/10/2026\",\"lieu\":\"Antananarivo\",\"candidats\":[]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='dateEnvoi')]").isNotEmpty())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='candidats')]").isNotEmpty())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='lieu')]").isEmpty());
        imprimer(idDmc, "{\"dateEnvoi\":\"2026-10-05\",\"lieu\":\"Antananarivo\",\"candidats\":[{\"nom\":\"Cabinet A\","
                + "\"adresse\":\"Lot II A 12\"},{\"nom\":\" \"}]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='candidats[1].nom')]").isNotEmpty())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='candidats[1].adresse')]").isNotEmpty())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='candidats[0].nom')]").isEmpty());
        assertThat(JsonPath.<List<String>>read(documents(), "$[*].type")).doesNotContain("LETTRE_INVITATION");
    }

    @Test
    @DisplayName("Deux candidats : 201, deux paires LETTRE_INVITATION (rang 01 et 02 dans le nom), chacune son destinataire "
            + "(nom puis adresse ligne à ligne) et la même liste ; la trace (dateEnvoi, lieu, candidats, rang) ; journal "
            + "LETTRES_INVITATION_IMPRIMEES ; listées dans /documents, jamais jointes au dossier")
    void deuxCandidats() throws Exception {
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAV", "PV_SIGNE");
        String r = imprimer(idDmc, CORPS).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(r, "$[*].type")).hasSize(4).containsOnly("LETTRE_INVITATION");
        assertThat(JsonPath.<List<String>>read(r, "$[*].libelle")).containsOnly("Lettre d'invitation");
        List<String> noms = JsonPath.read(r, "$[*].nomFichier");
        assertThat(noms).allMatch(n -> n.startsWith("LETTRE_") && n.contains("_9901_v1_"))
                .anyMatch(n -> n.endsWith("_01.docx")).anyMatch(n -> n.endsWith("_01.pdf"))
                .anyMatch(n -> n.endsWith("_02.docx")).anyMatch(n -> n.endsWith("_02.pdf"));
        assertThat(JsonPath.<List<Integer>>read(r, "$[*].publication.rang")).containsExactlyInAnyOrder(1, 1, 2, 2);
        assertThat(JsonPath.<List<String>>read(r, "$[*].publication.dateEnvoi")).containsOnly("2026-10-05");
        assertThat(JsonPath.<List<String>>read(r, "$[0].publication.candidats[*].nom")).containsExactly("Cabinet A", "Bureau B");

        String premiere = texte(r, "_01.docx");
        String seconde = texte(r, "_02.docx");
        assertThat(premiere).contains("Antananarivo, 05/10/2026", "Cabinet A\nLot II A 12\nAntananarivo\n", "- Cabinet A\n- Bureau B",
                "d’un budget prédéterminé").doesNotContain("Toamasina", "{{", "exclusivement de la qualité technique");
        assertThat(seconde).contains("Bureau B\nRue Rainandriamampandry, Toamasina\n", "- Cabinet A\n- Bureau B")
                .doesNotContain("Lot II A 12");

        String journal = mvc.perform(get("/api/dossiers/" + idDossier + "/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='LETTRES_INVITATION_IMPRIMEES')].detail"))
                .singleElement().asString().startsWith("2 lettre(s) d'invitation imprimée(s)");
        assertThat(JsonPath.<List<String>>read(documents(), "$[?(@.type=='LETTRE_INVITATION')].nomFichier")).hasSize(4);
        String pieces = mvc.perform(get("/api/piece-jointe-dossiers").param("dossier", String.valueOf(idDossier))
                .header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(pieces, "$[*].nomFichier")).noneMatch(n -> n.startsWith("LETTRE_"));
    }

    @Test
    @DisplayName("Statut « Lancé » (Q5) — la première impression des lettres passe la ligne PREVU → LANCE (journal "
            + "LIGNE_LANCEE, avisImprimeLe) ; une réimpression ne change rien et n'écrit rien")
    void premiereImpressionLanceLaLigne() throws Exception {
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAV", "PV_SIGNE");
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PREVU")).andExpect(jsonPath("$.avisImprimeLe").isEmpty());
        imprimer(idDmc, CORPS).andExpect(status().isCreated());
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("LANCE"))
                .andExpect(jsonPath("$.avisImprimeLe").value(java.time.LocalDate.now().toString()));
        assertThat(lancements()).containsExactly(
                "Ligne 9901 : 2 lettre(s) d'invitation imprimée(s) (envoi du 05/10/2026), statut PREVU → LANCE");
        imprimer(idDmc, CORPS).andExpect(status().isCreated());
        assertThat(lancements()).hasSize(1);
    }

    // ------------------------------------------------------------------ outils

    private String documents() throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** Le texte du .docx produit dont le nom finit par {@code fin}. */
    private String texte(String produits, String fin) throws Exception {
        int id = JsonPath.<List<Integer>>read(produits, "$[?(@.nomFichier =~ /.*" + fin.replace(".", "\\.") + "/)].idDocument").get(0);
        byte[] docx = mvc.perform(get("/api/fiches-marche/documents/" + id + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx)); XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText().replace(' ', ' ').replace(' ', ' ');
        }
    }

    private List<String> lancements() throws Exception {
        String journal = mvc.perform(get("/api/dossiers/9900/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(journal, "$[?(@.typeAction=='LIGNE_LANCEE')].detail");
    }

    private org.springframework.test.web.servlet.ResultActions disponibilite(Long dmc) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + dmc + "/lettres-invitation/disponibilite").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions imprimer(Long dmc, String corps) throws Exception {
        return mvc.perform(post("/api/fiches-marche/" + dmc + "/lettres-invitation").header("Authorization", tokenPrmp)
                .contentType(JSON).content(corps));
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
}
