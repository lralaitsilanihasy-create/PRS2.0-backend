package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.CompteCandidat;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.EntrepriseJournalRepository;
import cnm.prs.service.RaccordementDgi;
import cnm.prs.service.RapprochementCandidatService;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1b, §B3 à §B6) — l'entreprise d'un candidat et ses pièces,
 * l'unicité des numéros, la vérification du NIF (deux voies, écran de l'Administrateur, journal), le répertoire des
 * exclusions de l'ARMP (signalement), les rapprochements entre comptes. Le raccordement à la DGI est simulé.
 */
class EntrepriseCandidatIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final byte[] PDF = "%PDF-1.4 test".getBytes();

    @MockitoBean private RaccordementDgi dgi;
    @Autowired private CompteCandidatRepository candidats;
    @Autowired private EntrepriseJournalRepository journal;
    @Autowired private RapprochementCandidatService rapprochements;

    private String jetonA;
    private String jetonB;

    @BeforeEach
    void candidats() {
        candidats.save(new CompteCandidat("C900000001", "a@entreprise.mg", "+261 34 11 111 11", "Rabe", "Paul",
                CompteCandidat.CONFIRME, false, LocalDateTime.now(), LocalDateTime.now(), null, null));
        candidats.save(new CompteCandidat("C900000002", "b@entreprise.mg", "034 11 111 11", "Rasoa", "Lova",
                CompteCandidat.CONFIRME, false, LocalDateTime.now(), LocalDateTime.now(), null, null));
        jetonA = bearer("a@entreprise.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000001", null);
        jetonB = bearer("b@entreprise.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000002", null);
    }

    private ResultActions declarer(String jeton, String nif, String stat, String adresse, String repNom) throws Exception {
        return mvc.perform(put("/api/candidat/entreprise").header("Authorization", jeton).contentType(JSON).content(
                "{\"raisonSociale\":\"BTP Sud\",\"nif\":\"" + nif + "\",\"stat\":" + (stat == null ? "null" : "\"" + stat + "\"")
                        + ",\"rcs\":null,\"adresse\":\"" + adresse + "\",\"representant\":{\"nom\":\"" + repNom
                        + "\",\"prenom\":\"Jean\",\"fonction\":\"Gérant\"}}"));
    }

    @Test
    @DisplayName("Déclaration : 404 avant, NIF normalisé, vérification NON_VERIFIE ; NIF ou STAT d'une autre entreprise → 409 sans "
            + "nommer l'autre compte ; un compte = une entreprise (mise à jour en place)")
    void declaration() throws Exception {
        mvc.perform(get("/api/candidat/entreprise").header("Authorization", jetonA)).andExpect(status().isNotFound());
        declarer(jetonA, "1234 567 890", "STAT 01", "Lot II A 12 Antsahavola", "Rakoto").andExpect(status().isOk())
                .andExpect(jsonPath("$.nif").value("1234567890")).andExpect(jsonPath("$.stat").value("STAT01"))
                .andExpect(jsonPath("$.verification.statut").value("NON_VERIFIE")).andExpect(jsonPath("$.exclusion").doesNotExist())
                .andExpect(jsonPath("$.representant.fonction").value("Gérant"));
        String refus = declarer(jetonB, "1234567890", null, "Ailleurs", "Rasoa").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NIF_EXISTANT")).andReturn().getResponse().getContentAsString();
        assertThat(refus).contains("connectez-vous avec le compte qui l'a déclarée").doesNotContain("a@entreprise.mg", "C900000001");
        declarer(jetonB, "999", "stat01", "Ailleurs", "Rasoa").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAT_EXISTANT"));
        declarer(jetonA, "1234567890", "STAT 01", "Lot II A 12 Antsahavola", "Rakoto").andExpect(status().isOk());
        mvc.perform(get("/api/candidat/entreprise").header("Authorization", jetonA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.raisonSociale").value("BTP Sud"));
        mvc.perform(put("/api/candidat/entreprise").header("Authorization", jetonA).contentType(JSON).content("{\"nif\":\"1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Pièces : avant l'entreprise 409 ENTREPRISE_ABSENTE ; PDF accepté (201), texte refusé (400), au-delà du plafond "
            + "413 ; suppression de la sienne seulement")
    void pieces() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("fichier", "statuts.pdf", "application/pdf", PDF);
        mvc.perform(multipart("/api/candidat/entreprise/pieces").file(pdf).param("type", "STATUTS").header("Authorization", jetonA))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENTREPRISE_ABSENTE"));
        declarer(jetonA, "1111111111", null, "Rue A", "Rakoto").andExpect(status().isOk());
        String p = mvc.perform(multipart("/api/candidat/entreprise/pieces").file(pdf).param("type", "STATUTS")
                .header("Authorization", jetonA)).andExpect(status().isCreated()).andExpect(jsonPath("$.format").value("application/pdf"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(p, "$.id");
        mvc.perform(multipart("/api/candidat/entreprise/pieces").file(new MockMultipartFile("fichier", "x.txt", "text/plain",
                "bonjour".getBytes())).param("type", "AUTRE").header("Authorization", jetonA)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/candidat/entreprise/pieces").file(pdf).param("type", "PASSEPORT").header("Authorization", jetonA))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/parametres/candidats").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"tailleMaxPieceMo\":1}")).andExpect(status().isOk());
        byte[] gros = new byte[1024 * 1024 + 10];
        System.arraycopy(PDF, 0, gros, 0, PDF.length);
        mvc.perform(multipart("/api/candidat/entreprise/pieces").file(new MockMultipartFile("fichier", "gros.pdf", "application/pdf",
                gros)).param("type", "AUTRE").header("Authorization", jetonA)).andExpect(status().isPayloadTooLarge());
        mvc.perform(get("/api/candidat/entreprise").header("Authorization", jetonA)).andExpect(jsonPath("$.pieces.length()").value(1));
        mvc.perform(delete("/api/candidat/entreprise/pieces/" + id).header("Authorization", jetonB)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/candidat/entreprise/pieces/" + id).header("Authorization", jetonA)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Vérification sur pièces : liste de l'Administrateur, refus sans motif 400, décision journalisée, fichier d'une "
            + "pièce ; les espaces sont étanches (candidat ↛ admin, admin ↛ espace candidat)")
    void verificationSurPieces() throws Exception {
        declarer(jetonA, "2222222222", null, "Rue A", "Rakoto").andExpect(status().isOk());
        int idPiece = JsonPath.read(mvc.perform(multipart("/api/candidat/entreprise/pieces")
                .file(new MockMultipartFile("fichier", "carte.pdf", "application/pdf", PDF)).param("type", "CARTE_FISCALE")
                .header("Authorization", jetonA)).andReturn().getResponse().getContentAsString(), "$.id");
        String liste = mvc.perform(get("/api/admin/entreprises").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int idEntreprise = JsonPath.<List<Integer>>read(liste, "$[?(@.nif=='2222222222')].id").get(0);
        mvc.perform(post("/api/admin/entreprises/" + idEntreprise + "/verification").header("Authorization", tokenAdmin)
                .contentType(JSON).content("{\"statut\":\"REFUSE_SUR_PIECES\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/entreprises/" + idEntreprise + "/verification").header("Authorization", tokenAdmin)
                .contentType(JSON).content("{\"statut\":\"VERIFIE_SUR_PIECES\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VERIFIE_SUR_PIECES")).andExpect(jsonPath("$.source").value("SUR_PIECES"))
                .andExpect(jsonPath("$.date").exists());
        assertThat(journal.findByIdEntrepriseOrderByDateActionAscIdJournalAsc(idEntreprise)).anySatisfy(j -> {
            assertThat(j.getAncienne()).isEqualTo("NON_VERIFIE");
            assertThat(j.getNouvelle()).isEqualTo("VERIFIE_SUR_PIECES");
        });
        mvc.perform(get("/api/admin/entreprises/" + idEntreprise + "/pieces/" + idPiece + "/fichier").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(content().bytes(PDF));
        mvc.perform(get("/api/admin/entreprises").header("Authorization", jetonA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/candidat/entreprise").header("Authorization", tokenAdmin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/exclusions-armp").header("Authorization", jetonA)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Voie AUTOMATIQUE : la DGI connaît le NIF → VERIFIE_DGI ; injoignable → retombe sur la voie sur pièces "
            + "(NON_VERIFIE) ; NIF inconnu → INCONNU_DGI")
    void voieAutomatique() throws Exception {
        mvc.perform(put("/api/parametres/candidats").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"verificationNif\":\"AUTOMATIQUE\"}")).andExpect(status().isOk());
        when(dgi.verifier(anyString())).thenReturn(RaccordementDgi.Reponse.CONNU);
        declarer(jetonA, "3333333333", null, "Rue A", "Rakoto").andExpect(jsonPath("$.verification.statut").value("VERIFIE_DGI"))
                .andExpect(jsonPath("$.verification.acteur").value("DGI"));
        when(dgi.verifier(anyString())).thenThrow(new IllegalStateException("injoignable"));
        declarer(jetonB, "4444444444", null, "Rue B", "Rasoa").andExpect(jsonPath("$.verification.statut").value("NON_VERIFIE"));
        org.mockito.Mockito.reset(dgi);
        when(dgi.verifier(anyString())).thenReturn(RaccordementDgi.Reponse.INCONNU);
        declarer(jetonB, "5555555555", null, "Rue B", "Rasoa").andExpect(jsonPath("$.verification.statut").value("INCONNU_DGI"));
    }

    @Test
    @DisplayName("Exclusions de l'ARMP : création et modification par l'Administrateur, journal des anciennes et nouvelles valeurs ; "
            + "l'entreprise exclue est signalée tant que l'exclusion court, plus quand sa date de fin est passée")
    void exclusions() throws Exception {
        declarer(jetonA, "6666666666", null, "Rue A", "Rakoto").andExpect(status().isOk());
        String e = mvc.perform(post("/api/exclusions-armp").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"nif\":\"6666 666 666\",\"raisonSociale\":\"BTP Sud\",\"motif\":\"Fraude\",\"referenceDecision\":\"ARMP-2026-12\","
                        + "\"dateDebut\":\"" + LocalDate.now().minusDays(10) + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.enCours").value(true)).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(e, "$.id");
        mvc.perform(get("/api/candidat/entreprise").header("Authorization", jetonA))
                .andExpect(jsonPath("$.exclusion.referenceDecision").value("ARMP-2026-12")).andExpect(jsonPath("$.exclusion.dateFin").doesNotExist());
        mvc.perform(post("/api/exclusions-armp").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"nif\":\"1\",\"raisonSociale\":\"X\",\"motif\":\"M\",\"referenceDecision\":\"R\",\"dateDebut\":\"2026-05-10\","
                        + "\"dateFin\":\"2026-05-01\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/exclusions-armp/" + id).header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"nif\":\"6666666666\",\"raisonSociale\":\"BTP Sud\",\"motif\":\"Fraude\",\"referenceDecision\":\"ARMP-2026-12\","
                        + "\"dateDebut\":\"" + LocalDate.now().minusDays(10) + "\",\"dateFin\":\"" + LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enCours").value(false)).andExpect(jsonPath("$.journal.length()").value(2))
                .andExpect(jsonPath("$.journal[1].anciennes.dateFin").doesNotExist())
                .andExpect(jsonPath("$.journal[1].nouvelles.dateFin").value(LocalDate.now().minusDays(1).toString()));
        mvc.perform(get("/api/candidat/entreprise").header("Authorization", jetonA)).andExpect(jsonPath("$.exclusion").doesNotExist());
        mvc.perform(delete("/api/exclusions-armp/" + id).header("Authorization", tokenAdmin))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Rapprochements : même téléphone (indicatif +261 ou 0), même signataire et même adresse (casse, accents, blancs) ; "
            + "jamais un refus")
    void rapprochementsEntreComptes() throws Exception {
        declarer(jetonA, "7777777777", null, "Lot II A 12  Antsahavola", "Rakoto").andExpect(status().isOk());
        declarer(jetonB, "8888888888", null, "lot ii a 12 antsahavola", "RAKOTO").andExpect(status().isOk());
        assertThat(rapprochements.de("C900000001")).extracting(r -> r.getCritere())
                .containsExactlyInAnyOrder("ADRESSE", "SIGNATAIRE", "TELEPHONE");
        assertThat(rapprochements.de("C900000002")).allSatisfy(r -> {
            assertThat(r.getIdCandidatA()).isEqualTo("C900000001");
            assertThat(r.getIdCandidatB()).isEqualTo("C900000002");
        });
    }
}
