package cnm.prs.service;

import org.springframework.stereotype.Component;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1b, §B4) — le <strong>raccordement au service de la DGI</strong>
 * qui dit si un NIF existe. Son interface n'est pas connue (question 4 de la demande, à obtenir de la DGI par le pilote) :
 * ce raccordement est <strong>vide</strong> et répond toujours {@link Reponse#INDISPONIBLE}. On ne l'invente pas.
 *
 * <p>Quand la DGI aura publié son interface, une implémentation réelle remplacera ce composant ; la voie
 * {@code AUTOMATIQUE} du paramètre {@code CANDIDAT_VERIFICATION_NIF} s'en servira sans autre changement. Une réponse
 * {@code INDISPONIBLE} (service injoignable, réponse illisible) retombe sur la voie {@code SUR_PIECES}.</p>
 */
@Component
public class RaccordementDgi {

    /** Ce que la DGI répond pour un NIF. */
    public enum Reponse { CONNU, INCONNU, INDISPONIBLE }

    public Reponse verifier(String nif) {
        return Reponse.INDISPONIBLE;
    }
}
