package cnm.prs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.SessionUtilisateur;
import cnm.prs.security.SessionCookies;

/**
 * ⚠️ Lot 6 (2026-09-17, demande front §B4) — <strong>journal des connexions</strong> : recette de
 * l'écriture de {@code t_session_utilisateur} au login et au logout.
 *
 * <p>Avant ce chantier, aucune connexion n'était tracée durablement nulle part : {@code AuditConfig}
 * exclut {@code /api/auth/**} du journal d'audit (et l'intercepteur ignore de toute façon les réponses
 * ≥ 400, donc les échecs), la table n'était écrite par aucun code applicatif, et les échecs ne vivaient
 * que dans les tables en mémoire de {@code LoginRateLimiter}, effacées à chaque redémarrage.</p>
 */
class JournalConnexionIntegrationTest extends CnmIntegrationTestSupport {

    private static final String AGENT = "Mozilla/5.0 (Windows NT 10.0) PosteCNM/1.0";

    private ResultActions connexion(String login, String motDePasse) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .header(HttpHeaders.USER_AGENT, AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"motDePasse\":\"" + motDePasse + "\"}"));
    }

    /** Les sessions du test, de la plus ancienne à la plus récente. */
    private List<SessionUtilisateur> journal() {
        return sessionUtilisateurRepository.findAll().stream()
                .sorted(Comparator.comparing(SessionUtilisateur::getDateConnexion))
                .toList();
    }

    /** Valeur du cookie de session posé par la réponse de login. */
    private static String cookieDeSession(MockHttpServletResponse reponse) {
        return reponse.getCookie(SessionCookies.NOM).getValue();
    }

    // ------------------------------------------------------------------
    // Écriture au login
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Login réussi : une ligne SUCCES = true, avec l'acteur, l'adresse et le poste")
    void login_reussi_ecritUneSession() throws Exception {
        connexion("CTRADM", "pw").andExpect(status().isOk());

        List<SessionUtilisateur> sessions = journal();
        assertEquals(1, sessions.size(), "une connexion réussie doit laisser exactement une ligne");
        SessionUtilisateur s = sessions.get(0);
        assertEquals(Boolean.TRUE, s.getSucces());
        assertEquals("CTRADM", s.getLogin());
        assertEquals("CTRADM", s.getImControleur());
        assertNotNull(s.getDateConnexion());
        assertNull(s.getDateDeconnexion(), "la session vient de s'ouvrir");
        assertNotNull(s.getIpAdresse());
        assertEquals(AGENT, s.getUserAgent());
        // ID_SESSION = empreinte SHA-256 du jeton (64 hexadécimaux), jamais le jeton lui-même.
        assertEquals(64, s.getIdSession().length());
    }

    @Test
    @DisplayName("Login échoué sur un compte connu : ligne SUCCES = false, avec l'identifiant tenté ET l'acteur")
    void login_echoue_compteConnu() throws Exception {
        connexion("CTRADM", "faux").andExpect(status().isUnauthorized());

        List<SessionUtilisateur> sessions = journal();
        assertEquals(1, sessions.size());
        SessionUtilisateur s = sessions.get(0);
        assertEquals(Boolean.FALSE, s.getSucces());
        assertEquals("CTRADM", s.getLogin());
        assertEquals("CTRADM", s.getImControleur(), "le login existe : on sait sur quel compte on s'acharne");
        assertNull(s.getDateDeconnexion());
    }

    @Test
    @DisplayName("Login échoué sur un identifiant INCONNU : la ligne existe quand même, sans acteur")
    void login_echoue_loginInconnu() throws Exception {
        connexion("intrus.inconnu", "faux").andExpect(status().isUnauthorized());

        List<SessionUtilisateur> sessions = journal();
        assertEquals(1, sessions.size());
        SessionUtilisateur s = sessions.get(0);
        assertEquals(Boolean.FALSE, s.getSucces());
        assertEquals("intrus.inconnu", s.getLogin(), "c'est la ligne qui manquait le plus");
        assertNull(s.getImControleur(), "aucun acteur à désigner derrière un login inconnu");
    }

    @Test
    @DisplayName("Login échoué puis réussi puis logout : trois moments, une seule session ouverte puis fermée")
    void echec_puis_succes_puis_logout() throws Exception {
        connexion("CTRADM", "faux").andExpect(status().isUnauthorized());
        MockHttpServletResponse ok = connexion("CTRADM", "pw").andExpect(status().isOk())
                .andReturn().getResponse();

        mvc.perform(post("/api/auth/logout").cookie(new jakarta.servlet.http.Cookie(
                SessionCookies.NOM, cookieDeSession(ok))))
                .andExpect(status().isNoContent());

        List<SessionUtilisateur> sessions = journal();
        assertEquals(2, sessions.size());
        assertEquals(Boolean.FALSE, sessions.get(0).getSucces());
        assertNull(sessions.get(0).getDateDeconnexion(), "un échec n'ouvre aucune session à fermer");
        SessionUtilisateur reussie = sessions.get(1);
        assertEquals(Boolean.TRUE, reussie.getSucces());
        assertNotNull(reussie.getDateDeconnexion(), "le logout doit fermer la session du cookie");
    }

    @Test
    @DisplayName("La durée de la session se mesure : déconnexion postérieure à la connexion, et logout idempotent")
    void logout_poseLaDuree_etNeRajeunitPas() throws Exception {
        MockHttpServletResponse ok = connexion("CTRADM", "pw").andExpect(status().isOk())
                .andReturn().getResponse();
        String cookie = cookieDeSession(ok);

        mvc.perform(post("/api/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie(SessionCookies.NOM, cookie)))
                .andExpect(status().isNoContent());
        LocalDateTime premiereFermeture = journal().get(0).getDateDeconnexion();
        assertNotNull(premiereFermeture);
        SessionUtilisateur s = journal().get(0);
        assertFalse(premiereFermeture.isBefore(s.getDateConnexion()), "la durée ne peut pas être négative");
        assertTrue(Duration.between(s.getDateConnexion(), premiereFermeture).toMinutes() < 5);

        // Second logout avec le même jeton : la fermeture déjà enregistrée ne bouge pas.
        mvc.perform(post("/api/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie(SessionCookies.NOM, cookie)))
                .andExpect(status().isNoContent());
        assertEquals(premiereFermeture, journal().get(0).getDateDeconnexion());
    }

    @Test
    @DisplayName("Logout sans jeton : 204 comme avant, et aucune session inventée")
    void logout_sansJeton_neCasseRien() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
        assertTrue(journal().isEmpty());
    }

    // ------------------------------------------------------------------
    // Ce que la migration V31 débloque
    // ------------------------------------------------------------------

    @Test
    @DisplayName("V31 : une PRMP dont l'identifiant fait 10 caractères s'écrit VRAIMENT au journal")
    void login_prmp_reference10Caracteres() throws Exception {
        // Avant V31, IM_CONTROLEUR était en varchar(7) : cette insertion partait en 22001 au flush et
        // AUCUNE connexion de PRMP ni d'UGPM n'était traçable. C'est le défaut C3 de l'audit du 14/09.
        prmpRepository.save(prmp("PRMP123456", "ANT"));
        compteAuthRepository.save(new CompteAuth("prmp.longue", passwordEncoder.encode("pw"),
                "PRMP", "PRMP123456", true));

        connexion("prmp.longue", "pw").andExpect(status().isOk());

        SessionUtilisateur s = journal().get(0);
        assertEquals("PRMP123456", s.getImControleur());
        assertEquals(10, s.getImControleur().length());
        assertEquals("prmp.longue", s.getLogin());
    }

    @Test
    @DisplayName("V31 : une UGPM est journalisée sous SA référence, pas sous celle de sa PRMP de tutelle")
    void login_ugpm_saPropreReference() throws Exception {
        // Le jeton d'une UGPM porte l'ID_PRMP de TUTELLE dans « ref » : c'est un périmètre de
        // visibilité, pas une identité. Le journal doit désigner la personne qui s'est connectée.
        ugpmRepository.save(ugpm("UGPM001", "PRMP001", "Randria", "Hanta"));
        compteAuthRepository.save(new CompteAuth("ugpm.hanta", passwordEncoder.encode("pw"),
                "UGPM", "UGPM001", true));

        connexion("ugpm.hanta", "pw").andExpect(status().isOk());

        assertEquals("UGPM001", journal().get(0).getImControleur());
    }

    // ------------------------------------------------------------------
    // Volume et robustesse
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Volume : un refus de quota (429) n'est PAS journalisé — sinon le verrou ne plafonnerait plus rien")
    void quota_429_nonJournalise() throws Exception {
        for (int i = 0; i < 5; i++) {
            connexion("CTRADM", "faux").andExpect(status().isUnauthorized());
        }
        // 6e essai : le quota refuse avant même d'examiner les identifiants.
        connexion("CTRADM", "faux").andExpect(status().isTooManyRequests());
        connexion("CTRADM", "faux").andExpect(status().isTooManyRequests());

        assertEquals(5, journal().size(), "seules les tentatives réellement examinées sont journalisées");
    }

    @Test
    @DisplayName("Un identifiant démesuré est tronqué à la longueur de la colonne, pas refusé ni perdu")
    void loginDemesure_tronque() throws Exception {
        // LoginRequest ne porte qu'un @NotBlank : rien n'empêche un client d'envoyer 500 caractères.
        // Sans troncature ici, l'insertion partirait en 22001 à CHAQUE tentative de ce genre.
        String demesure = "x".repeat(500);
        connexion(demesure, "faux").andExpect(status().isUnauthorized());

        SessionUtilisateur s = journal().get(0);
        assertEquals(100, s.getLogin().length(), "tronqué à la longueur de t_session_utilisateur.LOGIN");
        assertEquals(Boolean.FALSE, s.getSucces());
    }
}
