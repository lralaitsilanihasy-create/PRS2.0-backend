package cnm.prs.dto;

/**
 * ⚠️ Avis spécifique d'appel d'offres (demande front du 2026-09-30, §B3) — corps de
 * {@code POST /api/fiches-marche/{idDmc}/avis-specifique} : les informations de publication saisies à l'impression
 * (décision Q4 du pilote). Elles ne sont pas des données de la fiche : elles entrent dans l'avis et sont conservées avec
 * le document produit. Les quatre sont obligatoires ; les dates au format ISO {@code AAAA-MM-JJ}.
 *
 * @param datePublication date de publication de l'avis
 * @param jmpNumero       numéro du Journal des Marchés Publics de l'avis général
 * @param jmpDate         date de ce numéro du JMP
 * @param supports        autres supports de publication et leurs dates, tel quel
 */
public record AvisSpecifiqueRequest(String datePublication, String jmpNumero, String jmpDate, String supports) {
}
