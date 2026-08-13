package cnm.prs.enums;

/**
 * Types de notification (colonne {@code t_notification.TYPE_NOTIF}).
 *
 * <p>Valeurs reprises littéralement de {@code docs/regles-gestion.md}
 * (§2, §3.1, §3.3, §3.4).</p>
 */
public enum TypeNotification {

    /** Dossier complet prêt à être dispatché — vers Président / CC (§2.2, §3.4). */
    PRET_DISPATCH,

    /** Copie de dispatch reçue par le CC (§3.3). */
    DISPATCH_CC,

    /** Demande de retrait à valider, soumise par une PRMP — vers le CC de la localité + Président (§3.3). */
    DEMANDE_RETRAIT_A_VALIDER,

    /** Retrait accepté par le décideur (CC localité ou Président) — vers la PRMP (§3.1, §3.3). */
    RETRAIT_ACCEPTE,

    /** Retrait refusé par le décideur (CC localité ou Président) — vers la PRMP (§3.1, §3.3). */
    RETRAIT_REFUSE,

    /** PV passé au statut SIGNE — vers la PRMP (§3.1). */
    PV_SIGNE,

    /** Alerte de fin de mandat de la PRMP, J-90 / J-30 / J-7 (§3.1). */
    FIN_MANDAT,

    /** Alerte de délai / jalon (§3.2, §3.3). */
    ALERTE_DELAI,

    /** Dossier conforme clôturé, éligible à publication — vers le Chargé de publication (§3.7). */
    CLOTURE_ELIGIBLE,

    /** Nouvelle inscription (auto-inscription PRMP) en attente de validation — vers l'Administrateur. */
    NOUVELLE_INSCRIPTION,


    /** Dossier officiellement soumis par la PRMP, en attente de réception — vers le Secrétaire/CC de la localité (§3.1, Module 03). */
    DOSSIER_SOUMIS,

    /** Inscription PRMP validée par l'Administrateur (compte activé) — vers la PRMP (§3.1). */
    INSCRIPTION_VALIDEE,

    /** Inscription PRMP refusée par l'Administrateur (avec motif) — vers la PRMP (§3.1). */
    INSCRIPTION_REFUSEE,

    /** Message reçu dans la messagerie interne — vers le destinataire du message (Module 04). */
    NOUVEAU_MESSAGE,

    /** Dossier dispatché à examiner — vers le Membre assigné (§2.3, transmission dispatch→examen). */
    EXAMEN_A_FAIRE,

    /** Projet de PV soumis, à valider/signer — vers le CC et le Président de la localité (§3.2, §3.5). */
    PV_A_VALIDER,

    /** Projet de PV retourné pour rectification (avec commentaire) — vers le Membre auteur (§3.2). */
    PV_A_RECTIFIER,

    /** Projet de PV accepté — vers le Membre auteur (§3.2). */
    PV_ACCEPTE,

    /** PV signé (favorable avec réserves) à vérifier — vers le Vérificateur de la localité (⚠️ règle ajoutée). */
    PV_A_VERIFIER,

    /** PV signé, dossier auto-clôturé — vers le Vérificateur pour information/lecture seule (⚠️ règle ajoutée). */
    PV_POUR_INFO,

    /** Observations de vérification non levées à traiter — vers la PRMP du dossier (⚠️ règle ajoutée). */
    OBSERVATION_VERIFICATION,

    /** Dossier rectifié par la PRMP et resoumis — vers le vérificateur du dossier (⚠️ règle ajoutée). */
    RECTIFICATION_PRMP,

    /** Lettre de renvoi signée reçue — vers la PRMP du dossier concerné (⚠️ règle ajoutée). */
    LETTRE_RENVOI_RECUE,

    /** Copie d'une lettre de renvoi signée — vers l'Assistant contrôleur de la localité (⚠️ règle ajoutée). */
    LETTRE_RENVOI_COPIE,

    /** Copie d'un PV définitif (avis ≠ FAVR) — vers l'Assistant contrôleur de la localité (⚠️ règle ajoutée). */
    PV_DEFINITIF_COPIE,

    /** Copie d'un PV FAVR après clôture du dossier — vers l'Assistant contrôleur de la localité (⚠️ règle ajoutée). */
    CLOTURE_COPIE_ASSISTANT,

    /** Dossier complété par la PRMP après lettre de renvoi, à ré-examiner — vers le Membre attributaire (⚠️ règle ajoutée). */
    PIECE_AJOUTEE_APRES_RENVOI,

    /** Dispatch annulé par le Président/CC : le dossier n'est plus attribué — vers le Membre anciennement assigné (⚠️ règle ajoutée). */
    DISPATCH_ANNULE,

    /** PV signé (avis ≠ FAVR) : le sens de la décision est à transmettre à SIGMP — vers le Vérificateur (⚠️ spec navette 2026-08-01). */
    DECISION_A_TRANSMETTRE,

    /** Décision transmise à SIGMP : le PV est à archiver — vers l'Assistant contrôleur de la localité (⚠️ spec navette 2026-08-01). */
    PV_A_ARCHIVER,

    /** Compléments transmis par la PRMP après lettre de renvoi : l'examen reprend — vers le Membre attributaire (⚠️ spec navette 2026-08-01, cas 3). */
    COMPLEMENTS_TRANSMIS,

    /** Pièces manquantes / non conformes au DÉPÔT (contrôle de complétude du Secrétaire) — vers la PRMP (⚠️ spec recevabilité 2026-08-02, sans archivage). */
    PIECES_MANQUANTES_DEPOT,

    /** Compléments de dépôt transmis par la PRMP : contrôle de complétude à reprendre — vers le(s) Secrétaire(s) de la localité (⚠️ spec recevabilité 2026-08-02). */
    COMPLEMENTS_DEPOT_TRANSMIS
}
