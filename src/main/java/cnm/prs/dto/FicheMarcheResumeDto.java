package cnm.prs.dto;

/**
 * ⚠️ Fiche marché (demande front du 2026-09-23, lot 1b, §B1) — l'état réduit de la fiche liée à un dossier, porté
 * par {@link DossierDto#getFicheMarche()} : de quoi que la page du dossier affiche la fiche sans second appel. Les
 * valeurs sont celles de la <strong>dernière version</strong> ({@code GET /api/fiches-marche/{idDmc}}), en-tête lu sur
 * la ligne courante du plan.
 *
 * @param refeDossierPpm    référence du dossier de planification de la ligne
 * @param designationMarche objet du marché (ligne courante)
 * @param statut            {@code BROUILLON} ou {@code VALIDEE}
 * @param version           numéro de la dernière version
 * @param nbSaisis          champs de saisie renseignés (bilan des contrôles)
 * @param nbAttendus        champs de saisie attendus (bilan des contrôles)
 */
public record FicheMarcheResumeDto(Long idDmc, Integer idDetail, String refeDossierPpm, String designationMarche,
        String typeMarche, String statut, Integer version, int nbSaisis, int nbAttendus) {
}
