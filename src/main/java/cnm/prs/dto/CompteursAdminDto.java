package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * Compteurs de contenu par section du menu Administrateur — comptes globaux (rôle transversal).
 *
 * <p>⚠️ Lot 6 (2026-09-17, demande front « espace d'administration » §B1) — l'accueil de
 * l'Administrateur ne demande pas seulement des volumes mais l'<strong>ancienneté de la plus vieille
 * demande</strong> de chaque file : c'est elle qui dit s'il y a urgence, pas le compte brut. Les trois
 * champs d'origine sont conservés tels quels (le front lit {@code inscriptionsEnAttente} pour la
 * pastille du menu).</p>
 *
 * <p>{@code sessionsOuvertes} et {@code echecsConnexion24h} ne figurent volontairement
 * <strong>pas</strong> ici : leur source ({@code t_session_utilisateur} alimentée au login) relève du
 * besoin B4, non livré. Une mesure fausse sur un tableau de bord de sécurité est pire qu'une mesure
 * absente.</p>
 *
 * @param inscriptionsEnAttente inscriptions PRMP en attente de validation
 *                              ({@code t_compte_auth.STATUT = EN_ATTENTE}, type PRMP)
 * @param comptes               nombre total de comptes d'authentification
 * @param journalAudit          nombre total d'entrées du journal d'audit
 * @param rattachementsEnAttente déclarations de rattachement PRMP⇄entité non décidées
 *                              ({@code t_prmp_entite_demande.STATUT_DEMANDE = EN_ATTENTE})
 * @param inscriptionDoyenneLe  dépôt de la plus ancienne inscription encore en attente ;
 *                              {@code null} si la file est vide (voir {@code KpiService})
 * @param rattachementDoyenLe   première déclaration de rattachement encore en attente (à minuit :
 *                              {@code DATE_DECLARATION} est une date) ; {@code null} si la file est vide
 * @param comptesActifs         comptes connectables ({@code t_compte_auth.ACTIF = true})
 * @param comptesSuspendus      comptes existants NON connectables, hors inscriptions en attente —
 *                              désactivés par l'Administrateur ou refusés (voir {@code KpiService})
 * @param mandatsExpirantSous30j mandats PRMP non abrogés dont la fin tombe dans les 30 jours
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
        long mandatsExpirantSous30j) {
}
