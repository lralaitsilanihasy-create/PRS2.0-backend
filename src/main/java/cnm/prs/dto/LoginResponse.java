package cnm.prs.dto;

/**
 * Réponse de connexion : jeton JWT et informations de session.
 *
 * @param token        jeton JWT à placer dans l'en-tête {@code Authorization: Bearer ...}
 * @param login        login authentifié
 * @param role         profil métier reconnu (ou {@code null})
 * @param typeActeur   CONTROLEUR, PRMP ou UGPM
 * @param ref          <strong>périmètre</strong> de l'acteur : matricule contrôleur, identifiant PRMP —
 *                     et, pour une UGPM, l'identifiant de sa <strong>PRMP de tutelle</strong> (pas son
 *                     propre matricule) : c'est ce qui fait fonctionner son scoping
 * @param nomAffichage « Nom Prénoms » de la personne connectée, résolu serveur quel que soit le type
 *                     d'acteur ; repli sur le login si la fiche ne porte aucun nom. Évite au front un
 *                     appel de référentiel à chaque ouverture de session — et le rend possible pour une
 *                     UGPM, dont {@code ref} ne désigne pas la fiche
 * @param localite     localité de rattachement ({@code null} = toutes, cas Président)
 * @param expiresIn    durée de validité du jeton en secondes
 */
public record LoginResponse(
        String token,
        String login,
        String role,
        String typeActeur,
        String ref,
        String nomAffichage,
        String localite,
        long expiresIn) {
}
