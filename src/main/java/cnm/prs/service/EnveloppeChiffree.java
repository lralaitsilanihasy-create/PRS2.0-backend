package cnm.prs.service;

import cnm.prs.dto.CeremonieDto;
import cnm.prs.exception.BadRequestException;

/**
 * ⚠️ Une <strong>enveloppe chiffrée par une phrase secrète</strong>, que le serveur garde sans pouvoir la lire : base64 pour le
 * chiffré, l'IV et le sel ; PBKDF2-SHA-256 d'au moins {@link #ITERATIONS_MIN} itérations ; AES-256-GCM. Le même contrôle pour la
 * clé privée d'un détenteur de part (V66, {@code …/ceremonie/cles}) et pour la copie de son offre par le candidat (V136,
 * « copie-offre-candidat ») : 400 {@code ENVELOPPE_INVALIDE}, message nommé. Pur.
 */
public final class EnveloppeChiffree {

    public static final String CODE_INVALIDE = "ENVELOPPE_INVALIDE";
    public static final int ITERATIONS_MIN = 600_000;
    public static final String KDF = "PBKDF2-SHA-256";
    public static final String ALGORITHME = "AES-256-GCM";

    private EnveloppeChiffree() {
    }

    /** Contrôle l'enveloppe ; rend le chiffré décodé (sa taille sert aux appelants qui la bornent). */
    public static byte[] controler(CeremonieDto.Enveloppe e) {
        if (e == null) {
            throw new BadRequestException("L'enveloppe (chiffre, iv, sel, iterations, kdf, algorithme) est attendue.", CODE_INVALIDE);
        }
        byte[] chiffre = ClesRsa.decoder(e.chiffre());
        if (chiffre == null || ClesRsa.decoder(e.iv()) == null || ClesRsa.decoder(e.sel()) == null) {
            throw new BadRequestException("L'enveloppe attend chiffre, iv et sel en base64.", CODE_INVALIDE);
        }
        if (e.iterations() == null || e.iterations() < ITERATIONS_MIN) {
            throw new BadRequestException("La dérivation de la clé d'enveloppe attend au moins " + ITERATIONS_MIN + " itérations.",
                    CODE_INVALIDE);
        }
        if (!KDF.equalsIgnoreCase(e.kdf()) || !ALGORITHME.equalsIgnoreCase(e.algorithme())) {
            throw new BadRequestException("L'enveloppe attend kdf = " + KDF + " et algorithme = " + ALGORITHME + ".", CODE_INVALIDE);
        }
        return chiffre;
    }
}
