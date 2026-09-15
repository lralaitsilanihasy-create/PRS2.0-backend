package cnm.prs.enums;

/**
 * ⚠️ <strong>Sections de l'accueil « À faire »</strong> (demande front du 2026-09-14, §3) — un geste attendu du
 * connecté, regroupé par nature. <strong>L'ordre de déclaration est l'ordre servi</strong> dans
 * {@code AFaireDto.sections} et {@code delegations.parSection} : celui du tableau §3 du contrat, qui suit le
 * circuit (réception → archivage), puis les sections de la partie contrôlée (PRMP, UGPM).
 *
 * <p>Chaque section porte deux informations fixes : l'<strong>étape</strong> dont le délai standard est servi
 * dans {@code sections[].standardHeures} ({@code null} pour un geste non chronométré), et la
 * <strong>nature de son délai</strong>, qui décide de l'urgence ({@link NatureDelai}).</p>
 */
public enum SectionAFaire {

    /** SOUMIS sans réception — Secrétaire de la localité (délégation : paire → Secrétaire active). */
    A_RECEPTIONNER(EtapeCircuit.RECEPTION, NatureDelai.CHRONOMETRE),
    /** PRET_DISPATCH — central : Président ; régional : CC de la localité (le Président en suppléance). */
    A_DISPATCHER(EtapeCircuit.DISPATCH, NatureDelai.CHRONOMETRE),
    /** DISPATCHE — attributaire courant, quel que soit son profil. */
    A_EXAMINER(EtapeCircuit.EXAMEN, NatureDelai.CHRONOMETRE),
    /** A_REEXAMINER — attributaire (réexamen après lettre de renvoi). */
    A_REEXAMINER(EtapeCircuit.EXAMEN, NatureDelai.CHRONOMETRE),
    /** EXAMINE, PV BROUILLON — examinateur ({@code imCtrlMembre} du PV). */
    PV_A_SOUMETTRE(EtapeCircuit.EXAMEN, NatureDelai.CHRONOMETRE),
    /** EXAMINE, PV EN_RECTIFICATION — examinateur. */
    PV_A_REPRENDRE(EtapeCircuit.EXAMEN, NatureDelai.CHRONOMETRE),
    /** PV PROJET_SOUMIS, deux niveaux, étage du CC (niveau nul compris) — CC du circuit. */
    PV_A_ACCEPTER(EtapeCircuit.VISA, NatureDelai.CHRONOMETRE),
    /** PV à viser — Président à l'étage PRESIDENT d'un circuit à deux niveaux ; dispatcheur en navette simple. */
    PV_A_VISER(EtapeCircuit.VISA, NatureDelai.CHRONOMETRE),
    /** PV PROJET_ACCEPTE — désigné dont la part n'est pas datée. */
    PV_A_SIGNER(EtapeCircuit.COSIGNATURE, NatureDelai.CHRONOMETRE),
    /** Lettre de renvoi SOUMIS — régional : CC de la localité ; central : CC de la localité et Président. */
    LETTRES_A_SIGNER(null, NatureDelai.SANS_DELAI),
    /** Demande de retrait EN_ATTENTE — CC de la localité et Président. */
    RETRAITS_A_DECIDER(null, NatureDelai.SANS_DELAI),
    /** EN_VERIFICATION — Vérificateur cible, sans cible tous les Vérificateurs de la localité. */
    A_VERIFIER(EtapeCircuit.VERIFICATION, NatureDelai.CHRONOMETRE),
    /** OBSERVATIONS_LEVEES — comme {@link #A_VERIFIER}. */
    A_TRANSMETTRE_SIGMP(EtapeCircuit.TRANSMISSION_SIGMP, NatureDelai.CHRONOMETRE),
    /** DECISION_TRANSMISE_SIGMP, PV non archivé — Assistant cible, sans cible les Assistants de la localité. */
    A_ARCHIVER(EtapeCircuit.ARCHIVAGE, NatureDelai.CHRONOMETRE),
    /** Lettre SIGNE non archivée — Assistants de la localité. */
    LETTRES_A_ARCHIVER(null, NatureDelai.SANS_DELAI),
    /** Dossier en attente de la PRMP, suivi par le porteur de l'étape de reprise — hors {@code aFaire}. */
    EN_ATTENTE_PRMP(null, NatureDelai.PAUSE),
    /** BROUILLON — PRMP (soumettre) ; UGPM (compléter). */
    BROUILLONS(null, NatureDelai.HORS_DELAI),
    /** EN_ATTENTE_COMPLEMENTS_DEPOT — PRMP propriétaire. */
    PIECES_DEPOT_A_COMPLETER(null, NatureDelai.PAUSE),
    /** EN_ATTENTE_PIECES — PRMP propriétaire. */
    COMPLEMENTS_A_TRANSMETTRE(null, NatureDelai.PAUSE),
    /** EN_ATTENTE_DECISION_PRMP — PRMP propriétaire. */
    A_RECTIFIER(null, NatureDelai.PAUSE),
    /** De SOUMIS à DECISION_TRANSMISE_SIGMP, hors attente — PRMP ou UGPM propriétaire ; hors {@code aFaire}. */
    EN_COURS_CNM(null, NatureDelai.SUIVI);

    /** Comment l'urgence d'une ligne se décide. */
    public enum NatureDelai {
        /** Étape chronométrée : EN_RETARD, BIENTOT ou DANS_LES_DELAIS selon le reste (SANS_DELAI si entrée inconnue). */
        CHRONOMETRE,
        /** Geste sans étape chronométrée (lettres, retraits) : toujours SANS_DELAI. */
        SANS_DELAI,
        /** Hors circuit (brouillon) : toujours HORS_DELAI. */
        HORS_DELAI,
        /** La balle est chez la PRMP : toujours EN_PAUSE. */
        PAUSE,
        /** Suivi d'un dossier qui avance à la CNM : toujours SUIVI. */
        SUIVI
    }

    private final EtapeCircuit etapeStandard;
    private final NatureDelai natureDelai;

    SectionAFaire(EtapeCircuit etapeStandard, NatureDelai natureDelai) {
        this.etapeStandard = etapeStandard;
        this.natureDelai = natureDelai;
    }

    /** Étape dont le délai standard qualifie la section ; {@code null} pour un geste non chronométré. */
    public EtapeCircuit etapeStandard() {
        return etapeStandard;
    }

    public NatureDelai natureDelai() {
        return natureDelai;
    }

    /**
     * Vrai si la section est un <strong>suivi</strong> et non un geste : {@link #EN_ATTENTE_PRMP} et
     * {@link #EN_COURS_CNM} restent servies, mais n'entrent ni dans {@code compteurs.aFaire} ni dans le badge.
     */
    public boolean horsAFaire() {
        return this == EN_ATTENTE_PRMP || this == EN_COURS_CNM;
    }
}
