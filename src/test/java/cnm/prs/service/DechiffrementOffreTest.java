package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ⚠️ 2026-10-04 (soumission en ligne, lot 4, §B0 ; ADR-0013 §2) — <strong>la condition de l'ADR</strong> : des parts produites par
 * {@code shamir-secret-sharing} (le code du front, {@code scripts/vecteurs-scellement.mjs}) se recombinent par BouncyCastle, et le
 * conteneur du front se déchiffre au serveur. Vecteurs : {@code scellement/vecteurs-scellement-2026-10-04.json}, copie de
 * {@code frontend/docs/} (clés de test sans valeur). Si ce test casse, la reconstitution au serveur n'a plus le droit d'exister :
 * repli du §B3.4 (recombinaison dans le navigateur du responsable).
 */
class DechiffrementOffreTest {

    private static JsonNode v;

    @BeforeAll
    static void lire() throws Exception {
        try (InputStream in = DechiffrementOffreTest.class.getResourceAsStream("/scellement/vecteurs-scellement-2026-10-04.json")) {
            v = JsonMapper.builder().build().readTree(in);
        }
    }

    private static byte[] hex(String s) {
        return HexFormat.of().parseHex(s);
    }

    @Test
    @DisplayName("Shamir : toute paire de parts du front redonne le secret (BouncyCastle, GF(256) au polynôme de l'AES)")
    void shamir() {
        byte[] secret = hex(v.path("shamir").path("secret").asString());
        List<byte[]> parts = new ArrayList<>();
        v.path("shamir").path("parts").forEach(p -> parts.add(hex(p.asString())));
        assertThat(parts).hasSize(3).allMatch(DechiffrementOffre::partValide);
        int[][] paires = { { 0, 1 }, { 0, 2 }, { 1, 2 } };
        for (int[] p : paires) {
            assertThat(DechiffrementOffre.recombiner(List.of(parts.get(p[0]), parts.get(p[1])))).isEqualTo(secret);
        }
        assertThat(DechiffrementOffre.recombiner(parts)).isEqualTo(secret);   // les trois : même secret
        assertThat(DechiffrementOffre.recombiner(List.of(parts.get(0)))).isNotEqualTo(secret);   // sous le seuil : rien
        assertThatThrownBy(() -> DechiffrementOffre.recombiner(List.of(parts.get(0), parts.get(0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(DechiffrementOffre.partValide(new byte[32])).isFalse();
    }

    @Test
    @DisplayName("Conteneur : deux parts RSA-OAEP (SHA-256, MGF1-SHA-256) déchiffrées par la JCA, K recombinée, morceau AES-256-GCM "
            + "déchiffré avec ses données authentifiées, empreintes de l'accusé et du contenu retrouvées ; altéré, il est refusé")
    void conteneur() throws Exception {
        JsonNode c = v.path("conteneur");
        String enTete = c.path("enTete").asString();
        JsonNode t = JsonMapper.builder().build().readTree(enTete);
        String idOffre = t.path("idOffre").asString();
        List<byte[]> parts = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {   // les détenteurs 2 et 3
            JsonNode d = c.path("detenteurs").get(i);
            assertThat(t.path("parts").get(i).path("empreinte").asString()).isEqualTo(d.path("empreinte").asString());
            assertThat(ClesRsa.empreinte(d.path("clePublique").asString())).isEqualTo(d.path("empreinte").asString());
            PrivateKey cle = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(
                    Base64.getDecoder().decode(d.path("clePriveePkcs8").asString())));
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            rsa.init(Cipher.DECRYPT_MODE, cle, new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
            parts.add(rsa.doFinal(Base64.getDecoder().decode(t.path("parts").get(i).path("part").asString())));
        }
        byte[] k = DechiffrementOffre.recombiner(parts);
        assertThat(HexFormat.of().formatHex(k)).isEqualTo(c.path("k").asString());

        List<byte[]> morceaux = new ArrayList<>();
        c.path("morceaux").forEach(m -> morceaux.add(Base64.getDecoder().decode(m.path("base64").asString())));
        assertThat(ClesRsa.sha256Hex(morceaux.get(0))).isEqualTo(c.path("morceaux").get(0).path("sha256").asString());
        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
        sha.update(enTete.getBytes(StandardCharsets.UTF_8));
        morceaux.forEach(sha::update);
        assertThat(HexFormat.of().formatHex(sha.digest())).isEqualTo(c.path("empreinte").asString());

        byte[] clair = DechiffrementOffre.dechiffrer(k, idOffre, enTete, morceaux);
        assertThat(clair).hasSize(c.path("tailleContenu").asInt());
        assertThat(ClesRsa.sha256Hex(clair)).isEqualTo(c.path("sha256Contenu").asString());

        byte[] altere = morceaux.get(0).clone();
        altere[40] ^= 1;
        assertThatThrownBy(() -> DechiffrementOffre.dechiffrer(k, idOffre, enTete, List.of(altere)))
                .isInstanceOf(GeneralSecurityException.class);
        assertThatThrownBy(() -> DechiffrementOffre.dechiffrer(k, "autre-offre", enTete, morceaux))
                .isInstanceOf(GeneralSecurityException.class);   // données authentifiées d'une autre offre
    }
}
