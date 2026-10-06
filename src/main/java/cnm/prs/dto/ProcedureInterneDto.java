package cnm.prs.dto;

/**
 * ⚠️ 2026-10-06 (demande front « la liste des procédures en ligne », §B1) — une fiche en remise électronique, pour son responsable ou
 * l'Administrateur ({@code GET /api/fiches-marche/en-ligne}).
 *
 * @param statutFiche  {@code BROUILLON} · {@code VALIDEE} · {@code REVISION} (un brouillon après une version validée)
 * @param parInterim   l'appelant l'exerce par intérim pour le responsable titulaire (ADR-0008)
 * @param etat         {@code NON_LANCEE} (avis non imprimé, ou fiche hors des critères en ligne) · {@code A_VENIR} · {@code OUVERTE} ·
 *                     {@code CLOSE}
 * @param etatSeance   l'état de la séance ; {@code A_VENIR} l'heure d'ouverture passée sans séance ; {@code null} avant l'heure
 * @param nbOffres     le nombre seul des offres déposées (déposées ou écartées)
 * @param aTraiter     sans responsable, cérémonie non close, ou séance du jour / en cours : en tête de liste
 */
public record ProcedureInterneDto(Long idDmc, String reference, String objet, String autoriteContractante, String categorie,
        String statutFiche, ResponsableProcedureDto responsable, boolean parInterim, String etatCao, String etatCeremonie,
        String datePublication, String dateOuvertureDepots, String dateLimite, String dateOuverturePlis, String etat, String etatSeance,
        long nbOffres, boolean aTraiter) {
}
