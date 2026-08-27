package cnm.prs.dto;

import java.util.List;

import jakarta.validation.Valid;

/**
 * Décisions de l'Administrateur sur les entités <strong>proposées</strong> (non listées) d'une
 * inscription, lors de la validation. Les entités <strong>existantes</strong> déclarées sont
 * traitées automatiquement (activées si disponibles, sinon signalées en conflit). Peut être
 * {@code null}/vide s'il n'y a aucune entité proposée à arbitrer.
 */
public record ValidationInscriptionRequest(

        // ⚠️ @Valid sur le paramètre de type (cf. SaisiePpmRequest) — sur la List, il est déprécié.
        List<@Valid DecisionEntiteProposee> entitesProposees) {

    /**
     * Décision sur une entité proposée. Pour l'accepter, {@code accepter=true} et
     * {@code idOrganigramme} renseigné (requis pour créer l'entité dans le référentiel).
     */
    public record DecisionEntiteProposee(
            Integer idDemande,
            boolean accepter,
            Integer idOrganigramme) {
    }
}
