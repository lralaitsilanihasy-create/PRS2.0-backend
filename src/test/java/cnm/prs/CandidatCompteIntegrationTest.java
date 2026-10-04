package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.CompteCandidat;
import cnm.prs.repository.CodeCandidatRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.service.CandidatService;
import cnm.prs.service.EmailService;
import cnm.prs.service.PasserelleSms;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1a, §B2 et §B7) — le compte candidat : inscription publique,
 * codes de confirmation (courriel, téléphone sur paramètre), connexion par l'adresse électronique, aucune route interne
 * pour le profil CANDIDAT, paramètres de l'Administrateur, ménage (suppression des comptes non confirmés, archivage et
 * réactivation des comptes inactifs). Le courriel et le SMS sont captés par des simulacres.
 */
class CandidatCompteIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    @MockitoBean private EmailService email;
    @MockitoBean private PasserelleSms sms;
    @Autowired private CompteCandidatRepository candidats;
    @Autowired private CodeCandidatRepository codes;
    @Autowired private CandidatService service;

    private ResultActions inscrire(String adresse) throws Exception {
        return mvc.perform(post("/api/candidats/inscription").contentType(JSON).content("{\"email\":\"" + adresse + "\","
                + "\"telephone\":\"+261 34 12 345 67\",\"motDePasse\":\"Soumission2026\",\"nom\":\"Rakoto\",\"prenom\":\"Jean\"}"));
    }

    /** Le dernier code envoyé par courriel à l'adresse. */
    private String dernierCodeCourriel(String adresse) {
        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        verify(email, atLeastOnce()).envoyer(eq(adresse), anyString(), corps.capture());
        List<String> tous = corps.getAllValues();
        Matcher m = CODE.matcher(tous.get(tous.size() - 1));
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private ResultActions confirmer(String adresse, String codeEmail, String codeTelephone) throws Exception {
        return mvc.perform(post("/api/candidats/confirmation").contentType(JSON).content("{\"email\":\"" + adresse
                + "\",\"codeEmail\":\"" + codeEmail + "\"" + (codeTelephone == null ? "" : ",\"codeTelephone\":\"" + codeTelephone + "\"")
                + "}"));
    }

    private ResultActions connecter(String login) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(JSON)
                .content("{\"login\":\"" + login + "\",\"motDePasse\":\"Soumission2026\"}"));
    }

    @Test
    @DisplayName("Inscription → codes par courriel → confirmation → connexion par l'adresse électronique (casse ignorée) ; "
            + "doublon 409 EMAIL_EXISTANT ; mot de passe trop simple 400 ; connexion avant confirmation 409 COMPTE_A_CONFIRMER")
    void inscriptionEtConnexion() throws Exception {
        String id = JsonPath.read(inscrire("Jean.Rakoto@Entreprise.mg").andExpect(status().isCreated())
                .andExpect(jsonPath("$.etat").value("A_CONFIRMER")).andReturn().getResponse().getContentAsString(), "$.idCompte");
        assertThat(id).matches("C\\d{9}");
        assertThat(compteAuthRepository.findByLogin("jean.rakoto@entreprise.mg")).hasValueSatisfying(a -> {
            assertThat(a.getTypeActeur()).isEqualTo("CANDIDAT");
            assertThat(a.getRefActeur()).isEqualTo(id);
            assertThat(a.getActif()).isFalse();
        });
        inscrire("jean.rakoto@entreprise.mg").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_EXISTANT"));
        mvc.perform(post("/api/candidats/inscription").contentType(JSON).content("{\"email\":\"x@y.mg\",\"telephone\":\"0341234567\","
                + "\"motDePasse\":\"court\",\"nom\":\"A\",\"prenom\":\"B\"}")).andExpect(status().isBadRequest());
        connecter("jean.rakoto@entreprise.mg").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPTE_A_CONFIRMER"));

        String code = dernierCodeCourriel("jean.rakoto@entreprise.mg");
        String faux = code.equals("000000") ? "111111" : "000000";
        confirmer("jean.rakoto@entreprise.mg", faux, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_INVALIDE"));
        confirmer("JEAN.RAKOTO@entreprise.mg", code, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("CONFIRME"));
        confirmer("jean.rakoto@entreprise.mg", code, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("CONFIRME"));

        String r = connecter("Jean.Rakoto@Entreprise.MG").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CANDIDAT"))
                .andExpect(jsonPath("$.typeActeur").value("CANDIDAT")).andExpect(jsonPath("$.ref").value(id))
                .andExpect(jsonPath("$.nomAffichage").value("RAKOTO Jean")).andReturn().getResponse().getContentAsString();
        assertThat(candidats.findById(id)).hasValueSatisfying(c -> assertThat(c.getDerniereConnexion()).isNotNull());
        mvc.perform(post("/api/auth/login").contentType(JSON).content("{\"login\":\"jean.rakoto@entreprise.mg\","
                + "\"motDePasse\":\"Faux2026\"}")).andExpect(status().isUnauthorized());
        assertThat(r).contains("CANDIDAT");
    }

    @Test
    @DisplayName("Codes : 5 essais manqués → 429 ; expiré → 400 CODE_EXPIRE ; le renvoi invalide l'ancien code ; renvoi toujours "
            + "204, même pour une adresse inconnue")
    void codes() throws Exception {
        inscrire("essais@entreprise.mg").andExpect(status().isCreated());
        String code = dernierCodeCourriel("essais@entreprise.mg");
        String faux = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            confirmer("essais@entreprise.mg", faux, null).andExpect(status().isBadRequest());
        }
        confirmer("essais@entreprise.mg", faux, null).andExpect(status().isTooManyRequests());
        confirmer("essais@entreprise.mg", code, null).andExpect(status().isTooManyRequests());   // épuisé, même le bon

        mvc.perform(post("/api/candidats/codes").contentType(JSON).content("{\"email\":\"essais@entreprise.mg\"}"))
                .andExpect(status().isNoContent());
        String nouveau = dernierCodeCourriel("essais@entreprise.mg");
        if (!nouveau.equals(code)) {
            confirmer("essais@entreprise.mg", code, null).andExpect(status().isBadRequest());   // l'ancien ne vaut plus
        }
        String id = candidats.findByEmail("essais@entreprise.mg").orElseThrow().getIdCandidat();
        codes.findByIdCandidatAndUtiliseFalse(id).forEach(c -> {
            c.setExpiration(LocalDateTime.now().minusMinutes(1));
            codes.save(c);
        });
        confirmer("essais@entreprise.mg", nouveau, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRE"));
        mvc.perform(post("/api/candidats/codes").contentType(JSON).content("{\"email\":\"inconnu@nulle-part.mg\"}"))
                .andExpect(status().isNoContent());
        confirmer("inconnu@nulle-part.mg", "123456", null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un jeton CANDIDAT n'atteint aucune route interne, même sans garde de profil au contrôleur ; les routes publiques "
            + "du candidat restent ouvertes sans session")
    void aucuneRouteInterne() throws Exception {
        String candidat = bearer("garde@entreprise.mg", cnm.prs.enums.ProfilUtilisateur.CANDIDAT, cnm.prs.enums.TypeActeur.CANDIDAT,
                "C000009999", null);
        for (String route : List.of("/api/localites", "/api/natures", "/api/messages", "/api/parametres/fiche-garantie-taux",
                "/api/parametres/candidats", "/api/fiches-marche/1", "/api/dossiers")) {
            mvc.perform(get(route).header("Authorization", candidat)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/localites").header("Authorization", tokenPrmp)).andExpect(status().isOk());   // l'interne inchangé
    }

    @Test
    @DisplayName("Paramètres des candidats : Administrateur seul ; le quota d'inscriptions par jour s'applique (429, Retry-After) ; "
            + "confirmation par téléphone sur paramètre (code SMS exigé)")
    void parametres() throws Exception {
        mvc.perform(get("/api/parametres/candidats").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationNif").value("SUR_PIECES")).andExpect(jsonPath("$.confirmationTelephone").value(false))
                .andExpect(jsonPath("$.inscriptionsParJour").value(5));
        mvc.perform(get("/api/parametres/candidats").header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
        mvc.perform(put("/api/parametres/candidats").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"verificationNif\":\"MANUELLE\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/parametres/candidats").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"inscriptionsParJour\":2,\"confirmationTelephone\":true}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.inscriptionsParJour").value(2)).andExpect(jsonPath("$.confirmationTelephone").value(true));

        inscrire("tel1@entreprise.mg").andExpect(status().isCreated());
        inscrire("tel2@entreprise.mg").andExpect(status().isCreated());
        inscrire("tel3@entreprise.mg").andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));

        String codeEmail = dernierCodeCourriel("tel1@entreprise.mg");
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(sms, atLeastOnce()).envoyer(eq("+261 34 12 345 67"), message.capture());
        confirmer("tel1@entreprise.mg", codeEmail, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_INVALIDE"));   // le code SMS manque
        String id = candidats.findByEmail("tel1@entreprise.mg").orElseThrow().getIdCandidat();
        // Le dernier SMS est celui de tel2 : on reprend le code SMS de tel1 dans l'ordre d'envoi (premier message).
        Matcher m = CODE.matcher(message.getAllValues().get(0));
        assertThat(m.find()).isTrue();
        confirmer("tel1@entreprise.mg", codeEmail, m.group(1)).andExpect(status().isOk());
        assertThat(candidats.findById(id)).hasValueSatisfying(c -> assertThat(c.isTelephoneConfirme()).isTrue());
    }

    @Test
    @DisplayName("Ménage : un compte jamais confirmé est supprimé après le délai ; un compte inactif est archivé, refuse la "
            + "connexion (409 COMPTE_ARCHIVE, nouveau code par courriel) et se réactive par ce code")
    void menage() throws Exception {
        inscrire("oubli@entreprise.mg").andExpect(status().isCreated());
        inscrire("ancien@entreprise.mg").andExpect(status().isCreated());
        confirmer("ancien@entreprise.mg", dernierCodeCourriel("ancien@entreprise.mg"), null).andExpect(status().isOk());
        CompteCandidat oubli = candidats.findByEmail("oubli@entreprise.mg").orElseThrow();
        oubli.setDateInscription(LocalDateTime.now().minusDays(8));
        candidats.save(oubli);
        CompteCandidat ancien = candidats.findByEmail("ancien@entreprise.mg").orElseThrow();
        ancien.setDerniereConnexion(LocalDateTime.now().minusMonths(25));
        candidats.save(ancien);

        assertThat(service.menage()).containsExactly(1, 1);
        assertThat(candidats.findByEmail("oubli@entreprise.mg")).isEmpty();
        assertThat(compteAuthRepository.findByLogin("oubli@entreprise.mg")).isEmpty();
        assertThat(candidats.findByEmail("ancien@entreprise.mg")).hasValueSatisfying(c -> assertThat(c.getEtat()).isEqualTo("ARCHIVE"));

        clearInvocations(email);
        connecter("ancien@entreprise.mg").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMPTE_ARCHIVE"));
        confirmer("ancien@entreprise.mg", dernierCodeCourriel("ancien@entreprise.mg"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("CONFIRME"));
        connecter("ancien@entreprise.mg").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CANDIDAT"));
    }
}
