package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ Lot 6 (2026-09-17, demande front « espace d'administration » §B3) — <strong>fiche</strong> d'une
 * personne de l'annuaire : ce que la maquette C affiche quand on la sélectionne, et rien de plus.
 *
 * <p>Les neuf premiers champs sont <strong>exactement ceux de {@link AnnuairePersonneDto}</strong>,
 * sous les mêmes noms et calculés par le même code : la ligne de liste et la fiche doivent dire la
 * même chose de la même personne, faute de quoi l'écran se contredirait d'un clic à l'autre. La
 * fiche ajoute la date d'activation, la place dans l'organisation et l'activité.</p>
 *
 * <p><strong>Ce qui n'a pas de sens pour une population reste vide</strong> plutôt qu'inventé : ni
 * supérieur, ni chaîne, ni délégation pour une PRMP ou une UGPM (elles ne portent pas de profil de
 * contrôle) ; pas de mandat pour un contrôleur.</p>
 *
 * @param ref                identifiant : {@code IM_CONTROLEUR} (7), {@code ID_PRMP} ou {@code ID_UGPM} (10)
 * @param type               {@code CONTROLEUR} · {@code PRMP} · {@code UGPM} ({@link cnm.prs.enums.TypeActeur})
 * @param nom                nom de famille
 * @param prenoms            prénoms
 * @param profil             profil du contrôleur ; {@code null} pour une PRMP et une UGPM
 * @param localite           code de localité ({@code ID_LOCALITE}) ; {@code null} hors contrôleurs
 * @param entite             entité(s) de rattachement, séparées par « · » ; {@code null} pour un contrôleur
 * @param login              login du compte ; {@code null} si la personne n'en a aucun
 * @param statutCompte       {@link cnm.prs.enums.StatutCompteAnnuaire} : {@code ACTIF}, {@code SUSPENDU},
 *                           {@code REFUSE}, {@code EN_ATTENTE} ou {@code SANS_COMPTE}
 * @param dateActivation     date de la <strong>décision d'ouverture</strong> du compte
 *                           ({@code t_compte_auth.DATE_DECISION}) — « actif depuis le … » de la maquette.
 *                           {@code null} tant que l'inscription n'a pas été validée, et {@code null} pour une
 *                           inscription refusée : {@code DATE_DECISION} porte alors la date du refus, qui
 *                           n'est pas une date d'activation
 * @param derniereConnexion  ⚠️ 2026-09-17, §B4 — <strong>servi</strong> depuis que le journal des
 *                           connexions existe (migration {@code V31}) : la plus récente connexion
 *                           <strong>réussie</strong> de cette personne. {@code null} pour qui ne s'est
 *                           jamais connecté depuis que le journal existe — au début, tout le monde ; le
 *                           front ne l'affiche pas dans ce cas (plan §6 : une mesure fausse sur un écran
 *                           de sécurité est pire qu'une mesure absente)
 * @param echecs30j          ⚠️ 2026-09-17, §B4 — tentatives de connexion <strong>refusées</strong>
 *                           attribuées à cette personne sur 30 jours glissants, même fenêtre que
 *                           {@code actionsJournal30j}. Une tentative sur un login inconnu n'est
 *                           attribuable à personne et n'y figure donc pas : elle se lit dans
 *                           {@code GET /api/sessions}
 * @param superieur          supérieur hiérarchique ({@code tr_controleur.ID_SUPERIEUR}), résolu ; {@code null}
 *                           s'il n'en a pas ou hors contrôleurs
 * @param transversal        contrôleur transversal ({@code TRANSVERSAL}) ; {@code null} hors contrôleurs
 * @param chaineControle     chaîne de rattachement Membre → Vérificateur → Assistant, <strong>la personne en
 *                           tête</strong> puis ses rattachés successifs. Un seul maillon = chaîne incomplète
 *                           (état normal, le repli localité s'applique) ; vide hors contrôleurs
 * @param delegations        délégations de profil <strong>actives</strong> qui concernent son profil
 * @param mandat             mandat en vigueur ce jour, <strong>pour une PRMP seulement</strong> ; {@code null}
 *                           si elle n'en a aucun, et toujours {@code null} pour un contrôleur ou une UGPM
 * @param actionsJournal30j  nombre d'écritures portées à son nom au journal d'audit sur 30 jours glissants
 */
public record AnnuaireFicheDto(
        String ref,
        String type,
        String nom,
        String prenoms,
        String profil,
        String localite,
        String entite,
        String login,
        String statutCompte,
        LocalDateTime dateActivation,
        LocalDateTime derniereConnexion,
        Long echecs30j,
        Personne superieur,
        Boolean transversal,
        List<Maillon> chaineControle,
        List<Delegation> delegations,
        MandatDto mandat,
        long actionsJournal30j) {

    /** Une personne citée par la fiche sans en être le sujet (le supérieur). */
    public record Personne(String ref, String nom, String prenoms, String profil, String localite) {
    }

    /**
     * Un maillon de la chaîne de contrôle. {@code lui} marque la personne de la fiche, toujours le
     * premier maillon — la maquette l'affiche en évidence (« Membre — lui »).
     */
    public record Maillon(String ref, String nom, String prenoms, String profil, boolean lui) {
    }

    /**
     * Une délégation de profil active ({@code t_delegation_profil}).
     *
     * <p><strong>Elle est portée par le profil, pas par la personne</strong> : une paire active vaut
     * pour tous les titulaires du profil. La fiche la montre parce que c'est ce qui explique qu'une
     * personne agisse là où son profil seul ne le permettrait pas.</p>
     *
     * @param sens   {@code EXERCE} — elle exerce les tâches du profil cité ({@code ID_PROFILE_DELEGANT}
     *               est le sien) ; {@code EXERCEE_PAR} — les tâches de son profil peuvent être exercées
     *               par le profil cité ({@code ID_PROFILE_DELEGUE} est le sien)
     * @param profil l'autre profil de la paire ({@link cnm.prs.enums.ProfilUtilisateur})
     */
    public record Delegation(String sens, String profil) {
    }
}
