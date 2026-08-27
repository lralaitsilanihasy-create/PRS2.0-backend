package cnm.prs.dto;

/**
 * Résultat <strong>léger</strong> de la recherche de dossier par référence
 * ({@code GET /api/dossiers/recherche?q=}) — ⚠️ audit 2026-08-27 (lot D §6).
 *
 * <p>La barre de recherche de la topbar téléchargeait, à CHAQUE frappe validée, la liste complète
 * des dossiers <em>et</em> celle des PPM, puis cherchait la référence en JavaScript. Ce DTO ne porte
 * que ce dont l'écran a besoin pour afficher un résultat et y naviguer : l'identifiant, les deux
 * références possibles, la famille (qui détermine la liste de destination) et le statut (qui
 * détermine le groupe, brouillon ou soumis).</p>
 *
 * <p>Volontairement <strong>plus pauvre</strong> que {@link DossierDto} : une résolution de référence
 * n'a pas à divulguer la localité, la PRMP propriétaire, les auteurs ni la version.</p>
 *
 * @param idDossier     identifiant du dossier, pour la navigation
 * @param refeDossier   référence officielle du dossier, posée à la réception ({@code null} avant)
 * @param reference     référence <strong>affichée</strong> et effectivement cherchée :
 *                      {@code refeDossier} s'il existe, sinon celle du PPM rattaché
 * @param idTypeDossier famille du dossier ({@code tr_type_dossier}) — segment de l'URL de destination
 * @param statut        statut du dossier — {@code BROUILLON} ou non, second segment de l'URL
 */
public record RechercheDossierDto(Integer idDossier, String refeDossier, String reference,
        String idTypeDossier, String statut) {
}
