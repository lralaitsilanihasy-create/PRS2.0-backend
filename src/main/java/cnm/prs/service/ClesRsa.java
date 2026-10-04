package cnm.prs.service;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * ⚠️ 2026-10-04 (soumission en ligne, lot 2 ; ADR-0013 §1, §3) — les clés des détenteurs, côté serveur, <strong>par la JCA
 * seule</strong> : lecture d'une clé publique SPKI, empreinte, chiffrement d'un défi. RSA-OAEP, module de 3072 bits,
 * SHA-256 avec MGF1-SHA-256 — l'{@code OAEPParameterSpec} est explicite, sans quoi Java prend SHA-1 pour MGF1 et ne
 * s'accorde plus avec WebCrypto. Aucune clé privée ne passe par ici : le serveur n'en a jamais.
 */
final class ClesRsa {

    static final int MODULE_BITS = 3072;
    static final int DEFI_OCTETS = 32;
    static final String ALGORITHME_RSA = "RSA-OAEP-3072-SHA256";

    private static final SecureRandom ALEA = new SecureRandom();
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);

    private ClesRsa() {
    }

    /** La clé publique lue de sa forme SPKI en base64 : RSA, module de 3072 bits ; {@code null} si elle ne se lit pas ainsi. */
    static RSAPublicKey lire(String spkiBase64) {
        byte[] spki = decoder(spkiBase64);
        if (spki == null) {
            return null;
        }
        try {
            PublicKey k = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(spki));
            if (k instanceof RSAPublicKey rsa && rsa.getModulus().bitLength() == MODULE_BITS) {
                return rsa;
            }
            return null;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    /** L'empreinte d'une clé : SHA-256 de la forme SPKI, en hexadécimal minuscule. */
    static String empreinte(String spkiBase64) {
        byte[] spki = decoder(spkiBase64);
        return spki == null ? null : sha256Hex(spki);
    }

    static String sha256Hex(byte[] octets) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Un défi : {@code DEFI_OCTETS} octets tirés au sort ({@code clair}) et leur chiffré par la clé publique, en base64. */
    record Defi(byte[] clair, String chiffre) {
    }

    static Defi defi(RSAPublicKey cle) {
        byte[] clair = new byte[DEFI_OCTETS];
        ALEA.nextBytes(clair);
        try {
            Cipher c = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            c.init(Cipher.ENCRYPT_MODE, cle, OAEP);
            return new Defi(clair, Base64.getEncoder().encodeToString(c.doFinal(clair)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement du défi impossible.", e);
        }
    }

    /** Le clair renvoyé répond-il au défi ? Comparaison des empreintes en temps constant. */
    static boolean repond(String empreinteClairAttendue, String clairBase64) {
        byte[] clair = decoder(clairBase64);
        if (clair == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256Hex(clair).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                empreinteClairAttendue.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    /** Du base64 (standard ou URL) ; {@code null} si vide ou illisible. */
    static byte[] decoder(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        String s = base64.trim();
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            try {
                return Base64.getUrlDecoder().decode(s);
            } catch (IllegalArgumentException e2) {
                return null;
            }
        }
    }
}
