package cnm.prs.exception;

/**
 * Endpoint <strong>retiré</strong> du contrat : rendu en <strong>410 Gone</strong>, avec le geste qui
 * le remplace.
 *
 * <p>⚠️ Introduite le 2026-08-31 pour {@code POST /api/pv-examens/{id}/accepter}, fusionné dans
 * {@code /viser}. La livraison se fait « backend d'abord » (arbitrage 1) : pendant l'intervalle, un
 * front pas encore livré appellera l'ancien endpoint. Un 404 lui dirait « ça n'existe pas » et
 * enverrait chercher une faute de chemin ; un 410 dit « ça a existé, c'est retiré, voici par quoi ».
 * La différence se paie en minutes de diagnostic.</p>
 */
public class EndpointRetireException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EndpointRetireException(String message) {
        super(message);
    }
}
