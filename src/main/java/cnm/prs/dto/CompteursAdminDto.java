package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * Compteurs de contenu par section du menu Administrateur — comptes globaux (rôle transversal).
 *
 * <p>⚠️ Lot 6 (2026-09-17, demande front « espace d'administration » §B1) — l'accueil de
 * l'Administrateur ne demande pas seulement des volumes mais l'<strong>ancienneté de la plus vieille
 * demande</strong> de chaque file : c'est elle qui dit s'il y a urgence, pas le compte brut. La
 * <strong>forme</strong> des trois champs d'origine ne bouge pas (le front lit
 * {@code inscriptionsEnAttente} pour la pastille du menu, et {@code BadgesDto} est partagé avec les
 * neuf autres profils) ; seul le <strong>périmètre</strong> d'{@code inscriptionsEnAttente} est
 * corrigé — voir son paramètre.</p>
 *
 * <p>⚠️ <strong>2026-09-17, §B4</strong> — {@code sessionsOuvertes} et {@code echecsConnexion24h} sont
 * désormais servis : {@code t_session_utilisateur} est alimentée au login et au logout
 * ({@code JournalConnexionService}, migration {@code V31}). Ce sont les deux tuiles que l'accueil
 * refusait d'afficher (plan L6 §6) faute de source — « une mesure fausse sur un tableau de bord de
 * sécurité est pire qu'une mesure absente ».</p>
 *
 * @param inscriptionsEnAttente inscriptions en attente de validation
 *                              ({@code t_compte_auth.STATUT = EN_ATTENTE}) — ⚠️ 2026-09-17 : types
 *                              <strong>PRMP et UGPM</strong>, et non plus PRMP seul, pour compter ce
 *                              que l'écran des inscriptions liste
 * @param comptes               nombre total de comptes d'authentification
 * @param journalAudit          nombre total d'entrées du journal d'audit
 * @param rattachementsEnAttente déclarations de rattachement PRMP⇄entité non décidées
 *                              ({@code t_prmp_entite_demande.STATUT_DEMANDE = EN_ATTENTE})
 * @param inscriptionDoyenneLe  dépôt de la plus ancienne inscription encore en attente (même file que
 *                              {@code inscriptionsEnAttente}) ; {@code null} si la file est vide
 *                              (dérivation expliquée dans {@code KpiService})
 * @param rattachementDoyenLe   première déclaration de rattachement encore en attente (à minuit :
 *                              {@code DATE_DECLARATION} est une date) ; {@code null} si la file est vide
 * @param comptesActifs         comptes connectables ({@code t_compte_auth.ACTIF = true})
 * @param comptesSuspendus      comptes validés puis <strong>fermés</strong> par l'Administrateur
 *                              ({@code STATUT = ACTIF} avec {@code ACTIF = false}). ⚠️ 2026-09-17 :
 *                              les inscriptions <strong>refusées</strong> ({@code STATUT = REFUSE})
 *                              n'y sont PAS comptées — ce n'est pas le même état, et la tuile est une
 *                              mesure de sécurité. L'annuaire les expose sous {@code statut=REFUSE}
 * @param mandatsExpirantSous30j mandats PRMP non abrogés dont la fin tombe dans les 30 jours
 * @param sessionsOuvertes      ⚠️ §B4 — connexions <strong>réussies</strong> jamais fermées et datant
 *                              de moins de 12 heures. La borne n'est pas un détail : une session n'est
 *                              fermée que par un {@code logout} explicite, or la plupart des
 *                              utilisateurs ferment simplement leur onglet — sans elle, la tuile ne
 *                              redescendrait jamais (cf. {@code KpiService.DUREE_SESSION_OUVERTE_HEURES})
 * @param echecsConnexion24h    ⚠️ §B4 — tentatives de connexion refusées des 24 dernières heures,
 *                              identifiants inconnus compris. Les refus du quota (429) n'y figurent
 *                              pas : ils n'examinent aucun identifiant et ne sont pas journalisés
 */
public record CompteursAdminDto(
        long inscriptionsEnAttente,
        long comptes,
        long journalAudit,
        long rattachementsEnAttente,
        LocalDateTime inscriptionDoyenneLe,
        LocalDateTime rattachementDoyenLe,
        long comptesActifs,
        long comptesSuspendus,
        long mandatsExpirantSous30j,
        long sessionsOuvertes,
        long echecsConnexion24h) {
}
