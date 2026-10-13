package cnm.prs.dto;

/**
 * ⚠️ R6a (manuel révisé pour l'application, lot 2, §B1 ; arbitrage Q6 du pilote ; 2026-10-13) — un des neuf délais de la loi
 * n° 2016-055, servi par {@code GET /api/delais-legaux} pour l'écran et pour le PV.
 *
 * @param numero     le rang dans la table du pilote (1 à 9)
 * @param valeur     la valeur légale
 * @param unite      {@code JOURS}, {@code JOURS_FRANCS}, {@code JOURS_OUVRABLES}, {@code MOIS}
 * @param sens       {@code MINIMUM} (au moins) ou {@code MAXIMUM} (au plus)
 * @param pointDepart ce qui fait courir le délai ; convention : le jour de départ n'est pas compté
 * @param effet      ce que l'application en fait
 * @param bloquant   un manquement refuse la saisie ou la soumission
 */
public record DelaiLegalDto(int numero, String code, int valeur, String unite, String sens, String texte, String pointDepart,
        String article, String effet, boolean bloquant) {
}
