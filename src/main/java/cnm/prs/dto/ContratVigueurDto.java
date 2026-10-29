package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-1 ; V124) — un contrat-cadre ou un marché à commandes en vigueur :
 * {@code GET /api/contrats-en-vigueur} et {@code GET /api/contrats-en-vigueur/{id}}.
 *
 * @param etat           {@code EN_VIGUEUR} (aujourd'hui au plus tard à {@code dateFin}) ou {@code ECHU} ; un contrat échu n'admet plus ni
 *                       remise en concurrence ni commande
 * @param finMaximale    la date au-delà de laquelle aucune reconduction ne le porte (effet + durée maximale), nulle sans durée maximale
 * @param dateLimiteDecision la date avant laquelle la décision de reconduire se prend ({@code dateFin} − préavis), nulle sans reconduction
 * @param titulaires     la liste fermée des titulaires (décret n° 2019-1064, art. 18), dans l'ordre du classement ; les retirés n'y sont pas
 * @param alertes        codes, dans l'ordre : {@code RECONDUCTION_A_DECIDER}, {@code ECHEANCE_PROCHE}, {@code ECHU} ; ⚠️ 2026-10-29
 *                       {@code REMISE_A_LANCER} après {@code ECHEANCE_PROCHE} ; puis celles de la consommation (MS-3)
 * @param periodiciteMois ⚠️ 2026-10-29 (« contrat-cadre-4a », §B3 ; V147) — le calendrier structuré des remises, figé à la notification
 *                       ({@code B02-PC-04}) ; nul hors rythme « Selon le calendrier fixé ci-après » et pour un contrat créé avant V147
 * @param premiereRemise ⚠️ V147 — la date de la première remise ({@code B02-PC-05}), mêmes cas nuls
 * @param prochaineRemise ⚠️ V147 — l'échéance du calendrier qui attend sa remise (passée si la remise est en retard), sinon la suivante ;
 *                       nulle sans calendrier structuré, pour un contrat échu, ou sans échéance avant la fin de la période en cours
 */
public record ContratVigueurDto(Long id, Long idDmc, Integer lot, String typeMarche, String objet, String acheteur, String etat,
        LocalDate dateEffet, LocalDate dateFin, Integer periodeMois, Integer dureeMaxMois, LocalDate finMaximale, boolean reconductible,
        Integer reconductionsMax, int reconductions, Integer preavisMois, LocalDate dateLimiteDecision, BigDecimal montantEstime,
        BigDecimal montantMinimum, BigDecimal montantMaximum, String formeSubsequents, String rythme, String calendrier,
        List<Titulaire> titulaires, List<Decision> decisions, List<String> alertes, Integer periodiciteMois, LocalDate premiereRemise,
        LocalDate prochaineRemise) {

    /** Un titulaire : son offre, son candidat et son offre indicative. */
    public record Titulaire(String idOffre, Integer numero, String candidat, String nif, BigDecimal montant, String delai, Integer rang) {
    }

    /** Une décision de reconduction : {@code RECONDUIRE} ou {@code NE_PAS_RECONDUIRE}. */
    public record Decision(Long id, String decision, String reference, LocalDate dateDecision, LocalDate finAvant, LocalDate nouvelleFin,
            boolean horsPreavis, LocalDateTime le, String par) {
    }

    /** {@code POST /api/contrats-en-vigueur/{id}/reconduction}. */
    public record ReconductionRequest(String decision, String reference, LocalDate dateDecision) {
    }
}
