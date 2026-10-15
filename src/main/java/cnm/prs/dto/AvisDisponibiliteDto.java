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
 * @param daoComplet      ⚠️ R9a (demande front du 14/10, remarque non bloquante) — l'état du DAO complet de la dernière version
 *                        validée, exigé à l'impression (art. 31) : {@code PRODUIT} ; {@code A_ASSEMBLER} (il sera assemblé à
 *                        l'impression, qui peut alors rendre 409 {@code DAO_COMPLET_ABSENT}) ; {@code SANS_ASSEMBLAGE} (pas
 *                        d'assemblage sur ce serveur : la règle ne s'applique pas) ; {@code null} sans version validée. N'entre pas
 *                        dans {@code disponible}.
 * @param supportsMinimaux ⚠️ 2026-10-15 (demande front « marchés subséquents », §B0.2) — rappel, à l'impression, des supports où
 *                        la mise en concurrence d'un <strong>contrat-cadre</strong> doit au minimum être publiée, en sus des
 *                        art. 32-33 de la loi (décret n° 2019-1064, art. 9) ; liste vide hors contrat-cadre. N'entre pas dans
 *                        {@code disponible} : les supports saisis à l'impression restent libres.
 */
public record AvisDisponibiliteDto(boolean disponible, String raison, String idAvis, String statutPv,
        String statutDossier, Integer idDossierSoumis, String daoComplet, java.util.List<String> supportsMinimaux) {

    public AvisDisponibiliteDto(boolean disponible, String raison, String idAvis, String statutPv, String statutDossier, Integer idDossierSoumis) {
        this(disponible, raison, idAvis, statutPv, statutDossier, idDossierSoumis, null, java.util.List.of());
    }

    public AvisDisponibiliteDto avecDaoComplet(String etat) {
        return new AvisDisponibiliteDto(disponible, raison, idAvis, statutPv, statutDossier, idDossierSoumis, etat, supportsMinimaux);
    }

    public AvisDisponibiliteDto avecSupportsMinimaux(java.util.List<String> supports) {
        return new AvisDisponibiliteDto(disponible, raison, idAvis, statutPv, statutDossier, idDossierSoumis, daoComplet,
                supports == null ? java.util.List.of() : supports);
    }
}
