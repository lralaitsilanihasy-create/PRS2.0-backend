package cnm.prs.dto;

import java.math.BigDecimal;

/**
 * Une ligne de PPM éligible à un appel d'offres ({@code GET /api/dmcs/eligibles}, demande du 2026-09-22, §B2, H4) :
 * plan au PV signé (avis favorable, ou réserves levées), mode mappé au type DMC {@code DAO}, ligne non retirée.
 * {@code dejaDao} dit qu'un DMC existe déjà ({@code idDmc} pour rouvrir la fiche).
 *
 * <p>⚠️ Lot 1c (2026-09-23, §B2) — {@code formeMarche} : la forme saisie au plan ({@code A_COMMANDE},
 * {@code CONTRAT_CADRE}, {@code QUANTITE_FIXE}, {@code null} si non renseignée) ; {@code formeOutillee} : la fiche sait la
 * préparer. Une ligne non outillée <strong>reste listée</strong> (le front la montre désactivée, avec sa forme) ; sa
 * création répond 409 {@code FORME_NON_OUTILLEE}.</p>
 */
public record LigneEligibleDto(Integer idDetail, Integer idDossier, String refeDossier, String designationMarche,
        Integer idMode, String libelleMode, BigDecimal montEstim, boolean dejaDao, Long idDmc, String formeMarche,
        boolean formeOutillee) {
}
