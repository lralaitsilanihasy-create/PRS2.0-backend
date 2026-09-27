package cnm.prs.dto;

/**
 * ⚠️ V50 (2026-09-27, remise électronique, §B4 / §B5) — un compte désignable (membre détenteur d'une part de clé,
 * responsable de la procédure) : matricule, « NOM Prénoms », profil ({@code ProfilUtilisateur}, {@code null} si inconnu).
 */
public record CompteDesignableDto(String im, String nom, String profil) {
}
