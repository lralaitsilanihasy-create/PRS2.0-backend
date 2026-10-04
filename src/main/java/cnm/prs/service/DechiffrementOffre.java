package cnm.prs.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.threshold.ShamirSecretSplitter;
import org.bouncycastle.crypto.threshold.ShamirSplitSecret;
import org.bouncycastle.crypto.threshold.ShamirSplitSecretShare;

/**
 * ⚠️ 2026-10-04 (soumission en ligne, lot 4 ; ADR-0013 §2, §4) — le <strong>déchiffrement d'une offre</strong> au serveur, en
 * fonctions pures, sans état : la recombinaison de la clé {@code K} à partir de {@code quorum} parts claires (Shamir sur GF(256),
 * polynôme de l'AES, par BouncyCastle — la condition de l'ADR est levée par {@code DechiffrementOffreTest} sur les vecteurs du front),
 * puis le déchiffrement des morceaux AES-256-GCM avec leurs données authentifiées. Aucune cryptographie écrite à la main ; rien n'est
 * gardé : l'appelant oublie {@code K} et les parts.
 */
public final class DechiffrementOffre {

    /** Une part brute : 32 octets de valeurs puis l'abscisse en dernier octet (format de {@code shamir-secret-sharing}). */
    public static final int TAILLE_PART = 33;
    static final int IV = 12;
    static final int ETIQUETTE = 16;

    private DechiffrementOffre() {
    }

    /** Une part est-elle bien formée ? 33 octets, abscisse non nulle. */
    public static boolean partValide(byte[] part) {
        return part != null && part.length == TAILLE_PART && (part[TAILLE_PART - 1] & 0xff) != 0;
    }

    /**
     * Recombine {@code K} à partir de parts brutes (au moins le seuil, abscisses distinctes). {@link IllegalArgumentException} si une
     * part est mal formée ou si deux parts portent la même abscisse.
     */
    public static byte[] recombiner(List<byte[]> parts) {
        Set<Integer> abscisses = new HashSet<>();
        ShamirSplitSecretShare[] s = new ShamirSplitSecretShare[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            byte[] p = parts.get(i);
            if (!partValide(p)) {
                throw new IllegalArgumentException("part mal formée");
            }
            int x = p[TAILLE_PART - 1] & 0xff;
            if (!abscisses.add(x)) {
                throw new IllegalArgumentException("deux parts portent la même abscisse");
            }
            s[i] = new ShamirSplitSecretShare(Arrays.copyOf(p, TAILLE_PART - 1), x);
        }
        try {
            return ShamirSplitSecret.getInstance(ShamirSecretSplitter.Algorithm.AES, s).getSecret();
        } catch (IOException e) {
            throw new IllegalArgumentException("recombinaison impossible", e);
        }
    }

    /** Les données authentifiées d'un morceau : {@code idOffre|1|rang|dernier|sha256(en-tête)}, en UTF-8. */
    public static byte[] donneesAuthentifiees(String idOffre, int rang, boolean dernier, String sha256EnTete) {
        return (idOffre + "|1|" + rang + "|" + (dernier ? 1 : 0) + "|" + sha256EnTete).getBytes(StandardCharsets.UTF_8);
    }

    /** Déchiffre un morceau : iv (12 octets) ‖ chiffré ‖ étiquette (16 octets). {@link GeneralSecurityException} s'il est altéré. */
    public static byte[] dechiffrerMorceau(byte[] k, byte[] morceau, byte[] donneesAuthentifiees) throws GeneralSecurityException {
        if (morceau.length < IV + ETIQUETTE) {
            throw new GeneralSecurityException("morceau tronqué");
        }
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(k, "AES"), new GCMParameterSpec(ETIQUETTE * 8, morceau, 0, IV));
        c.updateAAD(donneesAuthentifiees);
        return c.doFinal(morceau, IV, morceau.length - IV);
    }

    /** Déchiffre et concatène les morceaux d'une offre, dans l'ordre. */
    public static byte[] dechiffrer(byte[] k, String idOffre, String enTete, List<byte[]> morceaux) throws GeneralSecurityException {
        String sha = ClesRsa.sha256Hex(enTete.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int r = 0; r < morceaux.size(); r++) {
            out.writeBytes(dechiffrerMorceau(k, morceaux.get(r), donneesAuthentifiees(idOffre, r, r == morceaux.size() - 1, sha)));
        }
        return out.toByteArray();
    }
}
