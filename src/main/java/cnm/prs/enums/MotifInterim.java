package cnm.prs.enums;

/**
 * Motif d'un intérim désigné (colonne {@code t_interim.MOTIF}, contrainte {@code ck_interim_motif}) —
 * demande front du 2026-09-21, §B1.
 *
 * <p>Seul {@link #VACANCE_POSTE} admet un intérim <strong>sans date de fin</strong> : un poste vacant n'a
 * pas de terme connu. Les quatre autres motifs décrivent une absence, qui en a un.</p>
 */
public enum MotifInterim {
    CONGE,
    MISSION,
    MALADIE,
    /** Poste vacant : {@code dateFin} peut être nulle (jusqu'à la nomination d'un titulaire). */
    VACANCE_POSTE,
    AUTRE
}
