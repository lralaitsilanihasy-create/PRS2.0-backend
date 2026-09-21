package cnm.prs.dto;

import java.util.List;

/**
 * Réponse de {@code GET /api/interims/mes} — <strong>le signal du front</strong> (bannière, menus, droits ;
 * demande du 2026-09-21, §B2), lu au même moment que {@code delegation-profils}.
 *
 * @param exerces intérims ACTIFS où le connecté est l'intérimaire (« Vous suppléez X jusqu'au D2 »)
 * @param subi    intérim ACTIF où le connecté est le titulaire (« Vous êtes suppléé par Y »), ou {@code null}
 * @param aVenir  intérims A_VENIR où le connecté est titulaire ou intérimaire
 */
public record MesInterimsDto(List<InterimDto> exerces, InterimDto subi, List<InterimDto> aVenir) {
}
