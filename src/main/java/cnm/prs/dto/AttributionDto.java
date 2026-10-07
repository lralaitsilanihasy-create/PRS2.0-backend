package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-07 (évaluation des offres, lot 2, tranche 2a, §B1) — l'attribution d'une procédure, lot par lot, après le rapport
 * d'évaluation : l'état du lot, la proposition du rapport, le dossier de marché au contrôle de la Commission et son avis. Les sections
 * des étapes suivantes (attributaire, information, délai d'attente, explications, mise au point, notification, avis d'attribution,
 * pièces de l'attributaire, infructuosité, sans suite) arrivent avec les tranches 2b à 2d.
 */
public record AttributionDto(Long idDmc, List<LotAttribution> lots) {

    /**
     * Un lot : {@code etat} ∈ {@code EN_EVALUATION} (rapport pas encore signé) · {@code PROPOSE} (rapport signé ; la proposition dit si
     * le lot est attribuable ou proposé infructueux) · {@code AU_CONTROLE} (dossier de marché créé) · {@code AVIS_RENDU} (PV de la
     * Commission signé). {@code projetDisponible} : le projet de marché produit par le serveur se télécharge.
     */
    public record LotAttribution(Integer lot, String etat, EvaluationDto.Proposition proposition, DossierMarche dossierMarche,
            boolean projetDisponible) {
    }

    /** Le dossier de marché du lot : {@code avis} ∈ {@code FAV} · {@code FAVR} · {@code DEF}, nul tant que le PV n'est pas signé. */
    public record DossierMarche(Integer idDossier, String sousType, String statut, String avis, LocalDateTime creeLe, String creePar) {
    }
}
