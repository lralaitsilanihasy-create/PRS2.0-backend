package cnm.prs.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1b) — les formes normalisées qui servent à l'unicité (NIF,
 * STAT, RCS) et aux rapprochements (téléphone, adresse, signataire).
 */
public final class NormalisationCandidat {

    private NormalisationCandidat() {
    }

    /** Un identifiant (NIF, STAT, RCS) : sans aucun blanc, en majuscules ; {@code null} s'il est vide. */
    public static String identifiant(String v) {
        if (v == null) {
            return null;
        }
        String s = v.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return s.isEmpty() ? null : s;
    }

    /** Un texte comparé « après normalisation » : casse, blancs et accents ignorés. */
    public static String texte(String v) {
        if (v == null) {
            return "";
        }
        String sansAccents = Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sansAccents.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** Un signataire : nom et prénom normalisés. */
    public static String personne(String nom, String prenom) {
        return texte(nom) + " " + texte(prenom);
    }

    /**
     * Un numéro de téléphone : ses chiffres seuls, l'indicatif de Madagascar ramené au zéro national
     * ({@code +261 34 12 345 67} et {@code 034 12 345 67} se rapprochent).
     */
    public static String telephone(String v) {
        if (v == null) {
            return "";
        }
        String chiffres = v.replaceAll("\\D", "");
        if (chiffres.startsWith("261") && chiffres.length() > 9) {
            chiffres = "0" + chiffres.substring(3);
        }
        return chiffres;
    }
}
