package cnm.prs.controller;

import java.util.Locale;

import org.springframework.http.MediaType;

/**
 * ⚠️ Audit front (2026-08-16) — sortie des pièces téléversées, garde SERVEUR :
 * <ul>
 *   <li>{@link #typeAutorise} : le {@code Content-Type} de sortie est forcé sur une <strong>liste
 *       blanche</strong> ({@code application/pdf}, {@code image/jpeg}, {@code image/png}) — tout autre
 *       format stocké (y compris un HTML téléversé) sort en {@code application/octet-stream}, jamais
 *       interprétable par le navigateur. Le format stocké ne doit JAMAIS être renvoyé tel quel.</li>
 *   <li>{@link #disposition} : valeur {@code Content-Disposition: attachment} avec nom de fichier
 *       <strong>assaini</strong> (CR/LF, guillemets et antislash neutralisés — pas d'injection
 *       d'en-tête via un nom de fichier téléversé).</li>
 * </ul>
 * {@code X-Content-Type-Options: nosniff} est posé globalement par la configuration de sécurité.
 */
final class Telechargements {

    private Telechargements() {
    }

    /** Liste blanche de sortie — accepte les libellés courts stockés (PDF/JPEG/PNG) et les types MIME. */
    static MediaType typeAutorise(String format) {
        if (format == null || format.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return switch (format.trim().toUpperCase(Locale.ROOT)) {
            case "PDF", "APPLICATION/PDF" -> MediaType.APPLICATION_PDF;
            case "JPEG", "JPG", "IMAGE/JPEG", "IMAGE/JPG" -> MediaType.IMAGE_JPEG;
            case "PNG", "IMAGE/PNG" -> MediaType.IMAGE_PNG;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /** Valeur {@code Content-Disposition} « attachment » avec nom assaini ({@code document} si vide). */
    static String disposition(String nom) {
        String sain = (nom == null || nom.isBlank() ? "document" : nom).replaceAll("[\\r\\n\"\\\\]", "_");
        return "attachment; filename=\"" + sain + "\"";
    }
}
