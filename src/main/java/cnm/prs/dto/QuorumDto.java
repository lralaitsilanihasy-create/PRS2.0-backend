package cnm.prs.dto;

/**
 * ⚠️ 2026-10-10 (manuel révisé, lot 1, R1, §B2) — le quorum de la CAO tel qu'il est constaté sur un PV ou un rapport.
 *
 * @param regle           la règle appliquée : {@code MAJORITE}, {@code TOUS}, ou le nombre minimal (texte)
 * @param requis          nombre de signataires requis
 * @param obtenus         signataires non empêchés comptés (présents à la clôture, ou signatures posées)
 * @param atteint         {@code obtenus >= requis} et le président (ou son représentant) compté
 * @param presidentRequis vrai dès qu'un président est désigné dans la CAO
 * @param presidentSigne  le président, ou son représentant désigné pour ce contexte, est compté
 * @param representant    le compte du représentant désigné pour ce contexte, ou nul
 * @param representantNom son nom
 */
public record QuorumDto(String regle, int requis, int obtenus, boolean atteint, boolean presidentRequis, boolean presidentSigne,
        String representant, String representantNom) {
}
