package cnm.prs.enums;

import java.util.List;

/**
 * Statuts d'un dossier (colonne {@code t_dossier.STATUT}).
 *
 * <p><strong>Attention :</strong> seules les valeurs ci-dessous sont nommées
 * explicitement (littéralement) dans {@code docs/regles-gestion.md}. Les statuts
 * intermédiaires du circuit (reçu, dispatché, en examen, PV en cours,
 * vérification) n'y figurent pas sous forme de littéraux — ils sont décrits
 * uniquement comme des <em>étapes</em> du pipeline (§2). Ils restent à valider
 * avant d'être ajoutés ici (cf. ambiguïté A3 de l'analyse d'écart).</p>
 */
public enum StatutDossier {

    /** Saisie en cours par la PRMP (façade), pas encore soumise — invisible des contrôleurs. */
    BROUILLON,

    /** Soumis par la PRMP (action {@code …/soumettre}), entré dans le circuit. */
    SOUMIS,

    /** §2.2 — déclenché automatiquement dès {@code COMPLET = true}. */
    PRET_DISPATCH,

    /**
     * ⚠️ Règle ajoutée (non issue de la brochure) — dossier <strong>dispatché</strong> vers un Membre,
     * en attente d'examen (§2.3 → §2.4). Posé automatiquement à la création d'un dispatch.
     */
    DISPATCHE,

    /**
     * ⚠️ Règle ajoutée (non issue de la brochure) — dossier <strong>examiné</strong> : un examen a été
     * créé (§2.4 → §2.5). Posé automatiquement à la création de l'examen. Le dossier quitte « à examiner » ;
     * l'examen et le projet de PV restent <strong>modifiables</strong> via la navette tant que le PV n'est
     * pas signé.
     */
    EXAMINE,

    /**
     * ⚠️ Règle ajoutée (non issue de la brochure) — <strong>PV signé</strong> (§2.6) : posé automatiquement
     * à la signature du PV. L'examen devient <strong>définitif (verrouillé)</strong> ; le dossier est en
     * attente de vérification.
     */
    PV_SIGNE,

    /**
     * ⚠️ Règle ajoutée (non issue de la brochure) — PV signé avec avis « favorable avec réserves » :
     * le dossier est en cours de <strong>vérification itérative</strong> par le Contrôleur vérificateur
     * (distinct de {@code PV_SIGNE} et {@code CLOTURE}). Posé automatiquement à la signature.
     */
    EN_VERIFICATION,

    /**
     * ⚠️ Règle ajoutée — observations de vérification NON levées : le dossier sort du circuit
     * vérificateur et attend que la PRMP prenne connaissance des observations, rectifie, puis
     * décide de la suite (renvoyer en vérification ou clôturer). Non re-vérifiable par le vérificateur.
     */
    EN_ATTENTE_DECISION_PRMP,

    /**
     * ⚠️ Règle ajoutée (2026-08-01, spec navette) — observations de vérification LEVÉES (cas 2, fin de
     * boucle) : le vérificateur doit encore transmettre l'approbation (+ levée) à SIGMP.
     */
    OBSERVATIONS_LEVEES,

    /**
     * ⚠️ Règle ajoutée (2026-08-01, spec navette) — le sens de la décision de la Commission a été
     * transmis à SIGMP par le vérificateur ({@code t_transmission_sigmp}) ; le PV est chez
     * l'Assistant contrôleur pour archivage (l'archivage clôt le dossier).
     */
    DECISION_TRANSMISE_SIGMP,

    /**
     * ⚠️ Règle ajoutée (2026-08-01, spec navette, cas 3) — lettre de renvoi SIGNÉE : l'examen est
     * suspendu, le dossier attend les pièces / informations complémentaires de la PRMP
     * (non modifiable par les Membres ; reprise via {@code …/transmettre-complements}).
     */
    EN_ATTENTE_PIECES,

    /**
     * ⚠️ Règle ajoutée (2026-08-02, réexamen après lettre de renvoi) — pièces complémentaires TRANSMISES
     * par la PRMP ({@code …/transmettre-complements}) : le dossier revient dans la file « à examiner »
     * du Membre attributaire pour <strong>réexamen</strong> (examen rouvert, mêmes dispatch/examen/PV).
     * La navette repart quand le Membre re-soumet le projet de PV (→ {@link #EXAMINE}).
     */
    A_REEXAMINER,

    /**
     * ⚠️ Règle ajoutée (2026-08-02, spec recevabilité au dépôt) — contrôle de complétude du SECRÉTAIRE :
     * pièces manquantes / non conformes signalées à la PRMP AVANT enregistrement de la réception.
     * Distinct de {@link #EN_ATTENTE_PIECES} (lettre de renvoi, après examen). Reprise via
     * {@code …/transmettre-complements-depot} (action explicite PRMP → retour {@link #SOUMIS}).
     */
    EN_ATTENTE_COMPLEMENTS_DEPOT,

    /** §3.3 — retrait approuvé par le Chef de commission. */
    RETIRE,

    /** §2.8 / §3.6 — clôture automatique après levée des observations. */
    CLOTURE,

    /**
     * ⚠️ Règle ajoutée (2026-08-05, mise à jour des PPM) — dossier <strong>remplacé par une version
     * postérieure</strong> : il reste intégralement consultable (historique, PV, pièces) mais n'est plus
     * le PPM en vigueur. <strong>Jamais supprimé.</strong>
     *
     * <p>Posé sur le PRÉDÉCESSEUR au moment où la nouvelle version est <strong>soumise</strong> — pas à la
     * création de son brouillon : une mise à jour abandonnée ne doit pas neutraliser le dossier en vigueur.
     * Le successeur le référence par {@code t_dossier.ID_DOSSIER_PARENT} ; la chaîne des versions se
     * remonte de proche en proche.</p>
     */
    REMPLACE;

    /**
     * ⚠️ Règle ajoutée (§3.3) — statuts « <strong>avant PV signé</strong> » : un dossier de la PRMP y est
     * <strong>retirable</strong> (une demande de retrait est possible tant que le PV n'est pas signé, à
     * toute étape antérieure du circuit Réception → Dispatch → Examen → projet de PV). À partir de
     * {@link #PV_SIGNE} (puis {@link #EN_VERIFICATION}, {@link #EN_ATTENTE_DECISION_PRMP}, {@link #RETIRE},
     * {@link #CLOTURE}) le retrait est refusé. {@link #BROUILLON} en est exclu : pré-circuit, il est
     * supprimable (« Mes brouillons »), pas « retirable ».
     *
     * <p><strong>Source unique</strong> : la liste déroulante des dossiers retirables
     * ({@code GET /api/dossiers/retirables}) et la garde de {@code POST /api/demande-retraits} s'appuient
     * toutes deux sur cet ensemble — elles ne peuvent donc pas diverger (sinon le front listerait des
     * dossiers que le POST rejetterait en 409).</p>
     */
    public static final List<String> NOMS_AVANT_PV_SIGNE =
            List.of(SOUMIS.name(), PRET_DISPATCH.name(), DISPATCHE.name(), EXAMINE.name());
}
