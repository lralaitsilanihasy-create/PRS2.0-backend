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

    /**
     * ⚠️ Co-signature (2026-08-28) — le Président / le CC a signé et DÉSIGNÉ un Membre pour
     * co-signer : vers ce Membre, qui est seul à pouvoir poser la part Membre et n'a aucun autre
     * moyen de l'apprendre. Distinct de {@link #PV_A_VALIDER}, qui appelle le P/CC à clore la
     * navette : les deux ne s'adressent ni au même profil ni au même moment du circuit.
     */
    PV_A_COSIGNER,

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
    COMPLEMENTS_DEPOT_TRANSMIS,

    /** ⚠️ 2026-10-04 (soumission en ligne, lot 2, §B6) — cérémonie des clés : vers chaque membre désigné, à la désignation et à la réouverture. */
    CLE_A_PUBLIER,

    /** Cérémonie close, clés publiées — vers la PRMP et les membres. */
    CLES_PUBLIEES,

    /** Rappel : part non vérifiée depuis la clôture, à J − FICHE_SE_VERIFICATION_PART_JOURS de la date limite — vers le membre. */
    PART_A_VERIFIER,

    /** Marge du quorum épuisée (parts disponibles ≤ quorum) — vers le responsable de la procédure. */
    MARGE_QUORUM,

    /** Une part déclarée perdue — vers le responsable de la procédure. */
    PART_PERDUE,

    /** ⚠️ 2026-10-04 (soumission en ligne, lot 3, §B6) — accusé de réception d'une offre scellée — vers le candidat (courriel). */
    ACCUSE_DEPOT,

    /** Offre retirée par le candidat — vers le candidat (courriel). */
    OFFRE_RETIREE,

    /** Date limite passée : les dépôts sont clos, avec le nombre — vers la PRMP et le responsable de la procédure. */
    DEPOTS_CLOS,

    /** ⚠️ 2026-10-04 (soumission en ligne, lot 4, §B7) — la séance d'ouverture approche (la veille, une heure avant) — membres et responsable. */
    SEANCE_A_VENIR,

    /** La séance est ouverte : apportez vos parts — vers les membres de la CAO. */
    PARTS_ATTENDUES,

    /** Le PV d'ouverture est produit — vers la PRMP, les membres et, s'il est publié, les soumissionnaires. */
    PV_OUVERTURE,

    /** S5 : les offres sont illisibles, la procédure est à relancer — vers les soumissionnaires. */
    OFFRES_ILLISIBLES,

    /** ⚠️ 2026-10-04 (arbitrages du pilote, §B2) — le PV d'ouverture est à signer — vers chaque membre présent de la CAO. */
    PV_A_SIGNER,
    /** ⚠️ 2026-10-05 (dépositaire, §B3) — le responsable demande la part de secours au dépositaire. */
    SECOURS_DEMANDE,
    /** ⚠️ 2026-10-06 (retrait après paiement, §B3) — un reçu de frais de dossier à valider (PRMP) ; décidé (candidat, par courriel). */
    RECU_A_VALIDER,
    RECU_VALIDE,
    RECU_REFUSE,

    /** ⚠️ 2026-10-07 (évaluation des offres, §B7) — l'évaluation est ouverte : vers chaque membre de la CAO et la PRMP. */
    EVALUATION_OUVERTE,
    /** ⚠️ 2026-10-07 (§B2, art. 35-VI) — une demande de précisions : vers le candidat (et par courriel). */
    PRECISION_DEMANDEE,
    /** ⚠️ 2026-10-07 (§B2) — la réponse du candidat : vers la PRMP et les membres de la CAO. */
    PRECISION_RECUE,
    /** ⚠️ 2026-10-07 (§B4, art. 48) — une demande de justification d'un prix anormal : vers le candidat (et par courriel). */
    JUSTIFICATION_DEMANDEE,
    /** ⚠️ 2026-10-07 (§B4) — la justification du candidat : vers la PRMP et les membres de la CAO. */
    JUSTIFICATION_RECUE,
    /** ⚠️ 2026-10-07 (§B6) — le rapport d'évaluation est produit : vers chaque membre appelé à le signer. */
    RAPPORT_A_SIGNER,
    /** ⚠️ 2026-10-07 (§B6) — le rapport d'évaluation est signé : vers la PRMP et les membres de la CAO. */
    RAPPORT_EVALUATION,
    /** ⚠️ 2026-10-07 (lot 2, tranche 2b, §B4.1) — le résultat de la procédure : vers chaque candidat non retenu du lot (et par courriel). */
    RESULTAT_DISPONIBLE,
    /** ⚠️ 2026-10-07 (§B4.1) — la lettre d'attribution : vers l'attributaire (et par courriel). */
    ATTRIBUTION,
    /** ⚠️ 2026-10-07 (§B4.2, art. 52-II) — une demande d'explication d'un candidat non retenu : vers la PRMP. */
    EXPLICATION_DEMANDEE,
    /** ⚠️ 2026-10-07 (§B4.2) — la réponse écrite de la PRMP : vers le candidat (et par courriel). */
    EXPLICATION_REPONDUE,
    /** ⚠️ 2026-10-07 (lot 2, tranche 2c, §B4.3, art. 54) — le marché signé, enregistré, est notifié : vers l'attributaire (et par courriel). */
    MARCHE_NOTIFIE,
    /** ⚠️ 2026-10-07 (§B5, art. 20-I) — une pièce fiscale ou sociale déposée par l'attributaire : vers la PRMP. */
    PIECES_ATTRIBUTAIRE_DEPOSEES,
    /** ⚠️ 2026-10-07 (§B5) — la vérification d'une pièce par la PRMP : vers l'attributaire (et par courriel). */
    PIECE_ATTRIBUTAIRE_VERIFIEE,
    /** ⚠️ 2026-10-07 (§B5) — le retrait du marché faute de pièces : vers l'attributaire (et par courriel). */
    MARCHE_RETIRE,
    /** ⚠️ 2026-10-07 (AMI en ligne, §B2) — l'accusé de dépôt d'une expression d'intérêt : vers le candidat (et par courriel). */
    AMI_EXPRESSION_DEPOSEE,
    /** ⚠️ 2026-10-07 (AMI en ligne, tranche AMI-b, Q5) — le résultat de la présélection : vers chaque candidat (retenu, ou non retenu et son motif). */
    AMI_RESULTAT,
    /** ⚠️ 2026-10-07 (AMI-b, §B4) — la lettre d'invitation à remettre une proposition : vers chaque candidat de la liste restreinte. */
    LETTRE_INVITATION,
    /** ⚠️ 2026-10-08 (lot 3 PI, PI-d1, §B3) — la seconde séance est ouverte : vers chaque candidat dont l'enveloppe financière s'ouvre (invité à y assister). */
    SEANCE_FINANCIERE
}
