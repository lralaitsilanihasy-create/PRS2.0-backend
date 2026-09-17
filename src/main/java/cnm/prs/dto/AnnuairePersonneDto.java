package cnm.prs.dto;

/**
 * ⚠️ Lot 6 (2026-09-17, demande front « espace d'administration » §B2) — une ligne de l'annuaire :
 * une <strong>personne</strong>, quelle que soit la population dont elle vient.
 *
 * <p>Contrôleurs ({@code tr_controleur}), PRMP ({@code t_prmp}) et UGPM ({@code t_ugpm}) vivent dans
 * trois tables aux identifiants de longueurs différentes et aux champs qui ne se recouvrent pas. Ce
 * DTO est leur dénominateur commun : ce qu'un administrateur cherche quand il cherche « quelqu'un ».
 * Les champs qui n'ont pas de sens pour une population y sont {@code null} plutôt qu'inventés.</p>
 *
 * @param ref          identifiant de la personne : {@code IM_CONTROLEUR} (7), {@code ID_PRMP} ou
 *                     {@code ID_UGPM} (10)
 * @param type         population d'origine : {@code CONTROLEUR}, {@code PRMP} ou {@code UGPM}
 *                     (valeurs de {@link cnm.prs.enums.TypeActeur})
 * @param nom          nom de famille
 * @param prenoms      prénoms
 * @param profil       profil du contrôleur ({@link cnm.prs.enums.ProfilUtilisateur}) ;
 *                     {@code null} pour une PRMP et une UGPM, dont le type tient lieu de rôle
 * @param localite     localité de rattachement ({@code ID_LOCALITE}, le code — le libellé vient du
 *                     référentiel des localités) ; {@code null} hors contrôleurs : ni la PRMP ni
 *                     l'UGPM n'en portent
 * @param entite       entité(s) contractante(s) de rattachement, séparées par « · » — celles de la
 *                     PRMP, celles de sa tutelle pour une UGPM ; {@code null} pour un contrôleur
 * @param login        login du compte d'authentification ; {@code null} si la personne n'en a aucun
 * @param statutCompte état d'accès ({@link cnm.prs.enums.StatutCompteAnnuaire}) : {@code ACTIF},
 *                     {@code SUSPENDU} (compte fermé par l'Administrateur), {@code REFUSE}
 *                     (inscription rejetée), {@code EN_ATTENTE} ou {@code SANS_COMPTE}
 */
public record AnnuairePersonneDto(
        String ref,
        String type,
        String nom,
        String prenoms,
        String profil,
        String localite,
        String entite,
        String login,
        String statutCompte) {
}
