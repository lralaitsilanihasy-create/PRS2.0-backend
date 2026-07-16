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

    /** §3.3 — retrait approuvé par le Chef de commission. */
    RETIRE,

    /** §2.8 / §3.6 — clôture automatique après levée des observations. */
    CLOTURE;

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
