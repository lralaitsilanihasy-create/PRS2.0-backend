package cnm.prs.enums;

/**
 * ⚠️ <strong>Chronométrage et prévision des délais</strong> (règle du pilote, 2026-09-01) — les étapes
 * chronométrées du circuit, dans leur ordre de parcours.
 *
 * <p>Chaque étape a un <strong>porteur</strong> (profil), un <strong>statut d'éligibilité</strong> et un
 * <strong>geste métier de clôture qui existe déjà</strong> : la fin d'une étape n'est jamais saisie, elle
 * est déduite de l'acte que le porteur pose de toute façon.</p>
 *
 * <p>⚠️ <strong>2026-09-12 — son ENTRÉE non plus ne se saisit pas</strong> : le geste de « prise en
 * charge » a été supprimé, et l'entrée dans une étape est la fin de celle qui précède. Un porteur n'a
 * plus rien à déclarer avant d'agir ; son délai se mesure entre deux transitions déjà horodatées.</p>
 *
 * <p><strong>Pourquoi la vérification et la transmission SIGMP sont DEUX étapes</strong> (la spec n'en
 * proposait qu'une). Quand les observations ne sont pas levées, le dossier passe à
 * {@code EN_ATTENTE_DECISION_PRMP} — une suspension PRMP — <em>entre</em> les deux actes. Une tâche unique
 * enjamberait cette fenêtre et ferait porter au Vérificateur le temps d'attente de la PRMP, alors que la
 * règle veut précisément qu'aucune tâche CNM ne coure pendant ces périodes. Scindées, la suspension tombe
 * proprement entre deux tâches.</p>
 */
public enum EtapeCircuit {

    /**
     * Réception et enregistrement par le Secrétaire. <strong>Close par la réception marquée
     * {@code COMPLET}</strong>, qui déclenche {@code PRET_DISPATCH} — et non par un geste « attribuer un
     * numéro », qui n'existe pas dans ce circuit.
     */
    RECEPTION(ProfilUtilisateur.SECRETAIRE, true),

    /** Dispatch vers un Membre, par le Président ou le Chef de commission. */
    DISPATCH(ProfilUtilisateur.CHEF_COMMISSION, true),

    /** Examen par le Membre dispatché, clos par la soumission du projet de PV. Rejouable (réexamen, navette). */
    EXAMEN(ProfilUtilisateur.MEMBRE, true),

    /** Visa du projet de PV par le dispatcheur (ou son intérimaire). Rejouable (navettes successives). */
    VISA(ProfilUtilisateur.CHEF_COMMISSION, true),

    /**
     * Co-signature du PV. ⚠️ Depuis la co-signature élargie (2026-09-04), le visa peut désigner un Membre
     * ET un Chef de commission : chacun signe SA part, dans l'ordre qu'il veut, et chaque signature
     * enregistre son propre passage — c'est la seule étape du circuit à en produire plusieurs de front.
     */
    COSIGNATURE(ProfilUtilisateur.MEMBRE, true),

    /**
     * ⚠️ Le <strong>délai propre à la PRMP</strong> : l'étape ouverte tant que le dossier attend sa
     * rectification ({@code EN_ATTENTE_DECISION_PRMP}), de la vérification qui a maintenu les
     * observations jusqu'à la resoumission.
     *
     * <p><strong>Hors compteur global</strong> ({@code dansCompteurGlobal = false}) : ce temps est
     * <em>suspensif</em>, déjà mesuré à part en attente PRMP ({@code attentePrmpHeuresOuvrees}) — il ne
     * s'impute pas à la Commission, et la date prévisionnelle ne le somme pas. L'étape existe pour que ce
     * délai soit <em>visible</em>, à côté de ceux de la CNM et dans la même unité.</p>
     */
    RECTIFICATION_PRMP(ProfilUtilisateur.PRMP, false),

    /** Vérification des documents témoins. Rejouable (boucle FAVR, resoumissions après rectification). */
    VERIFICATION(ProfilUtilisateur.VERIFICATEUR, true),

    /** Transmission du sens de la décision à SIGMP — dernière étape du compteur global. */
    TRANSMISSION_SIGMP(ProfilUtilisateur.VERIFICATEUR, true),

    /**
     * Archivage par l'Assistant contrôleur. Chronométré par profil mais <strong>hors compteur
     * global</strong> : la règle du pilote arrête le chronomètre à la validation sur SIGMP.
     */
    ARCHIVAGE(ProfilUtilisateur.ASSISTANT_CONTROLEUR, false);

    private final ProfilUtilisateur porteur;
    private final boolean dansCompteurGlobal;

    EtapeCircuit(ProfilUtilisateur porteur, boolean dansCompteurGlobal) {
        this.porteur = porteur;
        this.dansCompteurGlobal = dansCompteurGlobal;
    }

    /**
     * Profil <strong>nominal</strong> du porteur. Les délégations et l'intérim sont résolus à l'exécution
     * par {@code PermissionService.peutExercer} : ce champ dit quelle tâche c'est, pas qui a le droit.
     */
    public ProfilUtilisateur porteur() {
        return porteur;
    }

    /** Vrai si l'étape entre dans le compteur global (enregistrement → validation SIGMP). */
    public boolean dansCompteurGlobal() {
        return dansCompteurGlobal;
    }

    /** Étapes retenues pour la date prévisionnelle de fin — celles du compteur global. */
    public static java.util.List<EtapeCircuit> etapesDuCompteur() {
        return java.util.Arrays.stream(values()).filter(EtapeCircuit::dansCompteurGlobal).toList();
    }
}
