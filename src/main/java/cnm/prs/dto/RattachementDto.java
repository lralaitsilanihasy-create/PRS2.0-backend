package cnm.prs.dto;

/**
 * ⚠️ Rattachements Membre → Vérificateur → Assistant (2026-09-01) — une ligne de l'écran
 * d'administration.
 *
 * <p>Porte le contrôleur, son profil, sa localité, et son rattaché <strong>résolu</strong> (matricule
 * et nom). {@code imRattache} nul = <strong>chaîne incomplète</strong> : c'est ce que l'écran signale,
 * et ce n'est pas une erreur — le repli localité s'applique (arbitrage 2).</p>
 *
 * @param imControleur   matricule du porteur
 * @param nomControleur  « prénoms nom » du porteur
 * @param profil         profil du porteur ({@code MEMBRE} ou {@code VERIFICATEUR})
 * @param idLocalite     localité du porteur
 * @param imRattache     matricule du rattaché, ou {@code null} si la chaîne est incomplète
 * @param nomRattache    « prénoms nom » du rattaché, ou {@code null}
 * @param profilAttendu  profil que doit avoir le rattaché ({@code VERIFICATEUR} pour un Membre,
 *                       {@code ASSISTANT_CONTROLEUR} pour un Vérificateur) — évite au front de
 *                       reconstruire la règle pour peupler sa liste de choix
 */
public record RattachementDto(
        String imControleur,
        String nomControleur,
        String profil,
        String idLocalite,
        String imRattache,
        String nomRattache,
        String profilAttendu) {
}
