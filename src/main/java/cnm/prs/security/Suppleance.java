package cnm.prs.security;

import cnm.prs.enums.ProfilUtilisateur;

/**
 * ⚠️ Intérim désigné (lot 1, 2026-09-21) — un intérim <strong>ACTIF à la date du jour</strong>, réduit à ce
 * que les gardes ont besoin d'en savoir : qui supplée qui, avec quel profil et sur quel périmètre.
 *
 * <p>C'est la forme sous laquelle un intérim circule dans les services : posé une fois par requête dans
 * {@link InterimContexte} pour le connecté (ses suppléances), ou résolu à la demande pour un titulaire
 * (son suppléant, pour la copie des notifications). Le contenu binaire de la pièce n'y est jamais.</p>
 *
 * @param idInterim          identifiant de l'intérim ({@code t_interim})
 * @param imTitulaire        le titulaire suppléé (l'absent)
 * @param nomTitulaire       son nom figé à la désignation (« NOM Prénoms »)
 * @param profilTitulaire    le profil que l'intérimaire exerce à sa place (Président ou CC)
 * @param localiteTitulaire  le périmètre : localité du titulaire, {@code null} pour le Président (toutes)
 * @param imInterimaire      celui qui agit
 * @param nomInterimaire     son nom figé à la désignation
 * @param emailInterimaire   son courriel au moment de la résolution (copie des notifications) ; peut être nul
 */
public record Suppleance(Integer idInterim, String imTitulaire, String nomTitulaire,
        ProfilUtilisateur profilTitulaire, String localiteTitulaire, String imInterimaire,
        String nomInterimaire, String emailInterimaire) {

    /** L'intérimaire agit-il au nom du Président (périmètre : toutes les localités) ? */
    public boolean duPresident() {
        return profilTitulaire == ProfilUtilisateur.PRESIDENT;
    }
}
