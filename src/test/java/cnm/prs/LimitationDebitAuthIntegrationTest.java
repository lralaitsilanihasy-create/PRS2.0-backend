package cnm.prs;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/**
 * ⚠️ Audit 2026-08-27 (lot E, constat « aucun rate limiting au login ») — recette de
 * {@link cnm.prs.security.LoginRateLimiter} : {@code POST /api/auth/login} était public et sans
 * aucune limitation, un bruteforce en ligne n'avait pour seul frein que le coût de BCrypt.
 *
 * <p>Contexte Spring distinct de {@code CnmIntegrationTestSupport} (bean {@code Clock} remplacé par
 * une {@link HorlogeMutable}) : c'est le seul moyen de vérifier qu'un verrou <em>expire</em> sans
 * faire attendre la suite quinze minutes. Les compteurs, eux, vivent hors transaction — ils sont
 * remis à zéro avant chaque test par {@code CnmIntegrationTestSupport#seed()}.</p>
 */
class LimitationDebitAuthIntegrationTest extends CnmIntegrationTestSupport {

    @TestConfiguration
    static class HorlogeLimiteurConfig {
        // Nom de bean different de `clock` (ClockConfig) : Spring Boot refuse par defaut le
        // remplacement d'une definition existante ; @Primary departage les deux pour l'injection.
        @Bean
        @Primary
        Clock horlogeDuLimiteur() {
            return new HorlogeMutable(Instant.now(), ZoneId.systemDefault());
        }
    }

    @Autowired private Clock clock;
    private HorlogeMutable horloge;

    @BeforeEach
    void horlogeDeTest() {
        horloge = (HorlogeMutable) clock;
    }

    /** Le mot de passe des comptes seedés par le socle de test. */
    private static final String BON = "pw";

    private ResultActions tenter(String login, String motDePasse) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"motDePasse\":\"" + motDePasse + "\"}"));
    }

    private void echouer(String login, int fois) throws Exception {
        for (int i = 0; i < fois; i++) {
            tenter(login, "faux").andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------
    // Verrou de compte : couple (IP, login)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("5 échecs sur un identifiant → les tentatives suivantes sont refusées 429, "
            + "le BON mot de passe compris")
    void cinqEchecs_puis429_memeAvecLeBonMotDePasse() throws Exception {
        // Les 5 premiers echecs restent des 401 : ce sont bien des identifiants faux.
        echouer("CTRCC1", 5);

        // Le 6e essai n'est meme plus examine : 429, message francais, delai annonce.
        tenter("CTRCC1", "faux")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message", containsString("Trop de tentatives de connexion")))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(header().exists("Retry-After"));

        // ⚠️ Le cœur du verrou : pendant le blocage, le BON mot de passe est refusé lui aussi.
        // Sinon le changement de réponse (429 → 200) dirait à l'attaquant qu'il vient de le trouver.
        tenter("CTRCC1", BON).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Le verrou expire avec la fenêtre glissante : bloqué à 14 min, libéré à 16 min")
    void verrou_expireApresLaFenetre() throws Exception {
        echouer("CTRCC1", 5);
        tenter("CTRCC1", BON).andExpect(status().isTooManyRequests());

        // Toujours verrouille juste avant la fin de la fenetre de 15 minutes.
        horloge.avancerDe(Duration.ofMinutes(14));
        tenter("CTRCC1", BON).andExpect(status().isTooManyRequests());

        // Fenetre ecoulee : les 5 echecs en sont sortis, la connexion repasse.
        horloge.avancerDe(Duration.ofMinutes(2));
        tenter("CTRCC1", BON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CHEF_COMMISSION"));
    }

    @Test
    @DisplayName("Une connexion réussie remet le compteur du compte à zéro")
    void succes_remetLeCompteurAZero() throws Exception {
        echouer("CTRCC1", 4);                                   // 1 echec avant le seuil
        tenter("CTRCC1", BON).andExpect(status().isOk());       // succes -> compteur efface

        // Sans la remise a zero, ce 5e echec cumule aurait verrouille le compte.
        echouer("CTRCC1", 4);
        tenter("CTRCC1", BON).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Le verrou de compte ne déborde pas sur une AUTRE identité de la même adresse")
    void verrouDeCompte_nAffectePasUnAutreLogin() throws Exception {
        echouer("CTRCC1", 5);
        tenter("CTRCC1", BON).andExpect(status().isTooManyRequests());

        // Meme adresse, autre identite : le verrou porte sur le couple (IP, login), pas sur l'IP.
        tenter("CTRMEM", BON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBRE"));
    }

    // ------------------------------------------------------------------
    // Verrou d'adresse : ce que le verrou de compte ne voit pas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Limite par IP : 20 échecs répartis sur 4 identités (password spraying) verrouillent "
            + "l'adresse, y compris pour une identité jamais essayée")
    void limiteParIp_attrapeLePasswordSpraying() throws Exception {
        // 4 identites x 5 echecs = 20 echecs pour l'adresse. Aucun compte n'est bruteforce
        // au-dela de son propre seuil : c'est precisement le motif que le verrou de compte
        // laisse passer, et que seule la limite d'adresse peut voir.
        for (String cible : List.of("cible.a", "cible.b", "cible.c", "cible.d")) {
            echouer(cible, 5);
        }

        // Une identite VALIDE et jamais essayee depuis cette adresse est refusee : c'est
        // l'ADRESSE qui est verrouillee, et le message le dit.
        tenter("CTRMEM", BON)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("depuis cette adresse")));
    }

    // ------------------------------------------------------------------
    // Inscriptions publiques : quota par adresse
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Inscriptions : 10 demandes par adresse et par heure, la 11e est refusée sans rien créer ; "
            + "les routes JSON et multipart partagent le quota")
    void inscriptions_quotaParAdresse() throws Exception {
        // Les 10 premieres demandes aboutissent normalement.
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/register/prmp").contentType(MediaType.APPLICATION_JSON)
                    .content(inscription(i)))
                    .andExpect(status().isCreated());
        }

        // La 11e est refusee AVANT le service : aucune fiche, aucun compte, aucun fichier.
        mvc.perform(post("/api/auth/register/prmp").contentType(MediaType.APPLICATION_JSON)
                .content(inscription(10)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("demandes d'inscription")));
        assertTrue(compteAuthRepository.findByLogin("spam10").isEmpty(),
                "la demande refusee ne doit avoir cree aucun compte");
        assertTrue(prmpRepository.findById("PRMPS10").isEmpty(),
                "la demande refusee ne doit avoir cree aucune fiche PRMP");

        // Le quota est celui de l'ADRESSE, pas celui d'une route : le canal multipart (celui qui
        // stocke les fichiers) est refuse par le meme compteur.
        MockMultipartFile data = new MockMultipartFile("data", "", "application/json",
                inscription(11).getBytes(StandardCharsets.UTF_8));
        MockMultipartFile arrete = new MockMultipartFile("arrete", "arrete.pdf", "application/pdf",
                "%PDF-1.4 arrete".getBytes(StandardCharsets.US_ASCII));
        MockMultipartFile cin = new MockMultipartFile("cin", "cin.png", "image/png",
                new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 });
        mvc.perform(multipart("/api/auth/register/prmp").file(data).file(arrete).file(cin))
                .andExpect(status().isTooManyRequests());

        // Une heure plus tard, la fenetre est vide : l'inscription redevient possible.
        horloge.avancerDe(Duration.ofMinutes(61));
        mvc.perform(post("/api/auth/register/prmp").contentType(MediaType.APPLICATION_JSON)
                .content(inscription(12)))
                .andExpect(status().isCreated());
    }

    /**
     * Corps d'une auto-inscription PRMP (variante JSON). Login, identifiant, CIN et courriel sont
     * dérivés de {@code i} : chaque demande doit être valide en elle-même, sinon c'est un doublon
     * (409) qu'on mesurerait au lieu du quota.
     */
    private static String inscription(int i) {
        return "{\"login\":\"spam" + i + "\",\"motDePasse\":\"Passw0rd!\",\"idPrmp\":\"PRMPS" + i + "\","
                + "\"nomPrmp\":\"Rakoto\",\"prenomsPrmp\":\"Spam\","
                + "\"arreteNomin\":\"ARR-2026-" + i + "\",\"dateNomin\":\"2026-01-01\","
                + "\"cin\":\"" + String.format("%012d", 700000000000L + i) + "\","
                + "\"dateCin\":\"2010-01-01\",\"lieuCin\":\"Antananarivo\","
                + "\"emailPrmp\":\"spam" + i + "@prmp.mg\",\"telPrmp\":\"0340000000\"}";
    }
}
