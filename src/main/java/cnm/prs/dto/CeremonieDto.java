package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 2, §B2 ; ADR-0013) — la <strong>cérémonie des clés</strong>
 * d'une procédure, servie au responsable de la procédure et aux membres désignés
 * ({@code GET /api/fiches-marche/{idDmc}/ceremonie}).
 *
 * @param etat                {@code A_VENIR} (des clés manquent, jamais close), {@code CLOSE}, {@code A_REFAIRE} (rouverte)
 * @param dateCeremoniePrevue {@code dateCeremonie} des paramètres internes (INT-SE-04)
 * @param dateCloture         la date <strong>effective</strong> de la cérémonie
 * @param n                   {@code membresCommission.length + 1} (la part de secours)
 * @param premierDepot        posé par le lot 3 à la première offre scellée ; toujours faux au lot 2
 * @param avertissements      {@code SE_MARGE_EPUISEE} (cérémonie close, parts disponibles ≤ quorum)
 */
public record CeremonieDto(Long idDmc, String etat, LocalDateTime dateCeremoniePrevue, LocalDateTime dateCloture,
        Integer quorum, int n, boolean premierDepot, List<Detenteur> detenteurs, List<Avertissement> avertissements) {

    /**
     * Un détenteur de part : {@code empreinte} = SHA-256 de la forme SPKI de la clé publique (hexadécimal minuscule),
     * {@code clePublique} = SPKI en base64 ; {@code etatPart} : {@code ABSENTE}, {@code PUBLIEE}, {@code VERIFIEE},
     * {@code PERDUE}.
     */
    public record Detenteur(String role, String im, String nom, String empreinte, String clePublique,
            LocalDateTime datePublication, String etatPart, LocalDateTime derniereVerification, int remplacements) {
    }

    public record Avertissement(String regle, String message) {
    }

    /**
     * La clé privée <strong>enveloppée</strong> par la phrase secrète du détenteur ({@code wrapKey('pkcs8')}) : {@code chiffre},
     * {@code iv} (12 octets) et {@code sel} (16 octets) en base64 ; {@code kdf} = {@code PBKDF2-SHA-256},
     * {@code algorithme} = {@code AES-256-GCM}. Le serveur la garde sans pouvoir la lire, et ne la rend qu'à son propriétaire.
     */
    public record Enveloppe(String chiffre, String iv, String sel, Integer iterations, String kdf, String algorithme) {
    }

    /** Le corps de la publication d'une clé (§B2.2, §B2.3) et de son remplacement (§B5.1). */
    public record CleCorps(String clePublique, String empreinte, Enveloppe enveloppe) {
    }

    /**
     * Les clés publiées aux candidats ({@code GET /api/procedures-en-ligne/{idDmc}/cles}, public) : sans matricule ni nom.
     * C'est l'entrée du scellement (lot 3).
     */
    public record ClesPubliques(Long idDmc, Integer quorum, int n, List<String> algorithmes, LocalDateTime dateCloture,
            List<ClePubliee> detenteurs) {
    }

    public record ClePubliee(String role, String empreinte, String clePublique) {
    }

    /** Un défi (S2) : 32 octets chiffrés avec la clé publique du détenteur, en base64 ; expiré après {@code expire}. */
    public record Defi(Long idDefi, String chiffre, LocalDateTime expire) {
    }

    /** La réponse au défi : le clair, en base64. */
    public record ReponseDefi(String clair) {
    }

    /** Le dépositaire de la part de secours (§B1) : une désignation nominative, pas un compte. */
    public record Depositaire(String nom, String organisme, String fonction, String contact) {
    }

    /**
     * La part de secours vue des paramètres internes : {@code etat} ∈ {@code A_DESIGNER}, {@code DESIGNE}, {@code PUBLIEE},
     * {@code VERIFIEE}, {@code PERDUE}.
     */
    public record PartDeSecours(Depositaire depositaire, String etat) {
    }
}
