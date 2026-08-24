package cnm.prs.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Garde-fous de la clé de signature des jetons — {@link SecurityConfig#jwtSecretKey}.
 *
 * <p>Un secret HMAC faible ou connu permet de <strong>forger un jeton pour n'importe quel
 * profil</strong> : c'est un contournement complet du RBAC, pas une simple faiblesse de
 * configuration. Ces tests fixent les deux barrières qui l'empêchent, parce que la valeur de repli
 * d'{@code application.properties} est versionnée, donc publiquement lisible dans le dépôt.</p>
 */
class JwtSecretKeyTest {

    /** La valeur de repli versionnée dans {@code application.properties}. */
    private static final String SECRET_DEV = "dev-secret-please-change-0123456789-abcdefghij";

    /** Un secret d'exploitation plausible : 32 octets minimum, hors du dépôt. */
    private static final String SECRET_PROPRE = "9f3c7a1e5b8d2046af91c3e7d5b0a284";

    private final SecurityConfig config = new SecurityConfig();

    private static MockEnvironment profils(String... actifs) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(actifs);
        return env;
    }

    @Test
    @DisplayName("Un secret plus court que 256 bits interdit le démarrage, en nommant la longueur")
    void secretTropCourtRefuse() {
        String court = "trop-court";            // 10 octets, HS256 en exige 32
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> config.jwtSecretKey(court, profils()));
        assertTrue(e.getMessage().contains("10"), "le motif doit nommer la longueur reçue");
        assertTrue(e.getMessage().contains("APP_JWT_SECRET"), "le motif doit nommer le correctif");
    }

    @Test
    @DisplayName("Un secret vide est refusé, sans NullPointerException")
    void secretVideRefuse() {
        assertThrows(IllegalStateException.class, () -> config.jwtSecretKey("", profils()));
        assertThrows(IllegalStateException.class, () -> config.jwtSecretKey(null, profils()));
    }

    @Test
    @DisplayName("Sous le profil prod, le secret de développement interdit le démarrage")
    void secretDeDeveloppementRefuseEnProd() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> config.jwtSecretKey(SECRET_DEV, profils("prod")));
        assertTrue(e.getMessage().contains("APP_JWT_SECRET"), "le motif doit nommer le correctif");
    }

    @Test
    @DisplayName("Hors profil prod, le secret de développement reste toléré (poste local)")
    void secretDeDeveloppementTolereEnLocal() {
        assertDoesNotThrow(() -> config.jwtSecretKey(SECRET_DEV, profils()));
        assertDoesNotThrow(() -> config.jwtSecretKey(SECRET_DEV, profils("dev")));
    }

    @Test
    @DisplayName("Un secret propre est accepté en prod et porte les octets fournis")
    void secretPropreAccepteEnProd() {
        SecretKey cle = config.jwtSecretKey(SECRET_PROPRE, profils("prod"));
        assertEquals("HmacSHA256", cle.getAlgorithm());
        assertArrayEquals(SECRET_PROPRE.getBytes(StandardCharsets.UTF_8), cle.getEncoded());
    }
}
