package cnm.prs.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1a, §B2) — l'envoi d'un SMS. <strong>Aucune passerelle n'est
 * raccordée</strong> (question 3 de la demande : le fournisseur est à choisir par le pilote) : cette implémentation n'envoie
 * rien et le dit au journal applicatif. Tant qu'elle reste la seule, le paramètre {@code CANDIDAT_CONFIRMATION_TELEPHONE}
 * doit rester à {@code NON} (son défaut) : sinon le code téléphonique n'arriverait jamais.
 */
@Component
public class PasserelleSms {

    private static final Logger LOG = LoggerFactory.getLogger(PasserelleSms.class);

    /** Envoie {@code message} au numéro ; rend {@code false} si rien n'est parti. */
    public boolean envoyer(String numero, String message) {
        LOG.warn("Aucune passerelle SMS raccordée : le message destiné à {} n'est pas envoyé.", numero);
        return false;
    }
}
