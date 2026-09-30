package cnm.prs.dto;

/**
 * ⚠️ Avis spécifique d'appel d'offres (demande front du 2026-09-30, §B4) — réponse de
 * {@code GET /api/fiches-marche/{idDmc}/avis-specifique/disponibilite} : l'avis peut-il être imprimé, et sinon pourquoi
 * (mêmes raisons que le 409 {@code AVIS_INDISPONIBLE}), avec l'état lu pour le décider.
 *
 * @param disponible      l'avis peut être imprimé
 * @param raison          {@code null} si disponible ; sinon {@code CATEGORIE_SANS_AVIS}, {@code SANS_DOSSIER},
 *                        {@code PV_NON_SIGNE}, {@code AVIS_NON_FAVORABLE}, {@code RESERVES_NON_LEVEES} ou
 *                        {@code FICHE_NON_VALIDEE}
 * @param idAvis          avis du PV signé ({@code FAV}, {@code FAVR}, {@code DEF}, {@code NSP}), ou {@code null}
 * @param statutPv        {@code SIGNE} si un PV signé existe, sinon {@code null}
 * @param statutDossier   statut du dossier soumis courant, ou {@code null}
 * @param idDossierSoumis dossier soumis courant de la fiche, ou {@code null}
 */
public record AvisDisponibiliteDto(boolean disponible, String raison, String idAvis, String statutPv,
        String statutDossier, Integer idDossierSoumis) {
}
