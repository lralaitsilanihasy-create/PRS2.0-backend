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

    /** Type MIME d'un document Word produit par le serveur. */
    static final MediaType DOCX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    /** Type MIME d'un classeur Excel produit par le serveur. */
    static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    /** Liste blanche de sortie — accepte les libellés courts stockés (PDF/JPEG/PNG) et les types MIME. */
    static MediaType typeAutorise(String format) {
        if (format == null || format.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return switch (format.trim().toUpperCase(Locale.ROOT)) {
            case "PDF", "APPLICATION/PDF" -> MediaType.APPLICATION_PDF;
            case "JPEG", "JPG", "IMAGE/JPEG", "IMAGE/JPG" -> MediaType.IMAGE_JPEG;
            case "PNG", "IMAGE/PNG" -> MediaType.IMAGE_PNG;
            // ⚠️ Lot 2a (2026-09-23) — documents générés par la fiche marché (jamais un fichier téléversé).
            case "DOCX" -> DOCX;
            // ⚠️ V45 (2026-09-25) — classeurs du candidat produits par la fiche (bordereau, conformité).
            case "XLSX" -> XLSX;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /** Valeur {@code Content-Disposition} « attachment » avec nom assaini ({@code document} si vide). */
    static String disposition(String nom) {
        String sain = (nom == null || nom.isBlank() ? "document" : nom).replaceAll("[\\r\\n\"\\\\]", "_");
        return "attachment; filename=\"" + sain + "\"";
    }

    /** ⚠️ 2026-10-07 (attribution, tranche 2b) — un fichier téléversé (réponse, pièce), typé par la liste blanche. */
    static org.springframework.http.ResponseEntity<byte[]> fichier(String nom, String format, byte[] contenu) {
        return org.springframework.http.ResponseEntity.ok().header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition(nom))
                .contentType(typeAutorise(format)).body(contenu);
    }

    /** ⚠️ 2026-10-07 (attribution, tranche 2b) — une lettre produite par le serveur, en PDF ou en Word. */
    static org.springframework.http.ResponseEntity<byte[]> lettre(cnm.prs.entity.AttributionLettre x, boolean docx) {
        String nom = (cnm.prs.entity.AttributionLettre.ATTRIBUTION.equals(x.getType()) ? "lettre-attribution-" : "lettre-resultat-") + x.getIdDmc()
                + "-lot" + x.getLot() + (docx ? ".docx" : ".pdf");
        return org.springframework.http.ResponseEntity.ok().header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition(nom))
                .contentType(docx ? DOCX : MediaType.APPLICATION_PDF).body(docx ? x.getDocx() : x.getPdf());
    }
}
