package cnm.prs.dto;

import java.math.BigDecimal;

/**
 * Une ligne de PPM éligible à un appel d'offres ({@code GET /api/dmcs/eligibles}, demande du 2026-09-22, §B2, H4) :
 * plan au PV signé (avis favorable, ou réserves levées), mode mappé au type DMC {@code DAO}, ligne non retirée.
 * {@code dejaDao} dit qu'un DMC existe déjà ({@code idDmc} pour rouvrir la fiche).
 */
public record LigneEligibleDto(Integer idDetail, Integer idDossier, String refeDossier, String designationMarche,
        Integer idMode, String libelleMode, BigDecimal montEstim, boolean dejaDao, Long idDmc) {
}
