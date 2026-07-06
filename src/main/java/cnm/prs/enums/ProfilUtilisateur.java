package cnm.prs.enums;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Les 8 profils fonctionnels définis dans {@code docs/regles-gestion.md} §3.
 *
 * <p>Le nom de la constante sert d'autorité de sécurité : le rôle Spring Security est
 * {@code ROLE_<nom>} (ex. {@code ROLE_PRESIDENT}). Le rapprochement avec la base se fait
 * sur le <strong>libellé</strong> {@code tr_profile.PROFILE} (et non sur {@code ID_PROFILE},
 * dont la correspondance numérique n'est pas spécifiée par les règles), via
 * {@link #resolve(String)} qui normalise accents et casse.</p>
 */
public enum ProfilUtilisateur {

    PRMP,
    PRESIDENT,
    CHEF_COMMISSION,
    SECRETAIRE,
    MEMBRE,
    VERIFICATEUR,
    ASSISTANT_CONTROLEUR,
    CHARGE_PUBLICATION,
    ADMINISTRATEUR,
    /** Unité de Gestion de la Passation des Marchés — rattachée à une PRMP de tutelle (crée/édite, ne soumet pas). */
    UGPM;

    /** Autorité Spring Security correspondante (préfixe {@code ROLE_}). */
    public String authority() {
        return "ROLE_" + name();
    }

    /**
     * Déduit le profil à partir du libellé {@code tr_profile.PROFILE}.
     *
     * @param libelle libellé en base (ex. « Contrôleur vérificateur »)
     * @return le profil reconnu, ou {@code null} si aucun ne correspond
     */
    public static ProfilUtilisateur resolve(String libelle) {
        if (libelle == null || libelle.isBlank()) {
            return null;
        }
        String n = normalize(libelle);
        // Ordre important : tester les libellés les plus spécifiques d'abord.
        if (n.contains("president")) {
            return PRESIDENT;
        }
        if (n.contains("chef")) {
            return CHEF_COMMISSION;
        }
        if (n.contains("secretaire")) {
            return SECRETAIRE;
        }
        if (n.contains("assistant")) {
            return ASSISTANT_CONTROLEUR;
        }
        if (n.contains("verificateur")) {
            return VERIFICATEUR;
        }
        if (n.contains("publication")) {
            return CHARGE_PUBLICATION;
        }
        if (n.contains("admin")) {
            return ADMINISTRATEUR;
        }
        if (n.contains("ugpm")) {
            return UGPM;
        }
        if (n.contains("prmp")) {
            return PRMP;
        }
        if (n.contains("membre")) {
            return MEMBRE;
        }
        return null;
    }

    /** Minuscule, sans accents, espaces compactés. */
    private static String normalize(String s) {
        String lower = s.toLowerCase(Locale.ROOT).trim();
        String stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return stripped.replaceAll("\\s+", " ");
    }
}
