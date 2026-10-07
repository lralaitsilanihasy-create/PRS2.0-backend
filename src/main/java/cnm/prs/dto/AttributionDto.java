package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ 2026-10-07 (évaluation des offres, lot 2, tranche 2a, §B1) — l'attribution d'une procédure, lot par lot, après le rapport
 * d'évaluation : l'état du lot, la proposition du rapport, le dossier de marché au contrôle de la Commission et son avis. ⚠️ Tranche 2b
 * (§B3, §B4.1, §B4.2) : l'attributaire, l'information des candidats, le délai d'attente, les demandes d'explication. Les sections des
 * étapes suivantes (mise au point, notification, avis d'attribution, pièces de l'attributaire, infructuosité, sans suite) arrivent avec
 * les tranches 2c et 2d.
 */
public record AttributionDto(Long idDmc, List<LotAttribution> lots) {

    /**
     * Un lot : {@code etat} ∈ {@code EN_EVALUATION} (rapport pas encore signé) · {@code PROPOSE} (rapport signé ; la proposition dit si
     * le lot est attribuable ou proposé infructueux) · {@code AU_CONTROLE} (dossier de marché créé) · {@code AVIS_RENDU} (PV de la
     * Commission signé) · ⚠️ 2b : {@code ATTRIBUE} (choix de la PRMP) · {@code INFORME} (candidats informés, délai d'attente en cours) ·
     * {@code SIGNABLE} (délai écoulé). {@code projetDisponible} : le projet de marché produit par le serveur se télécharge.
     */
    public record LotAttribution(Integer lot, String etat, EvaluationDto.Proposition proposition, DossierMarche dossierMarche,
            boolean projetDisponible, Attributaire attributaire, Information information, DelaiAttente delaiAttente,
            List<Explication> explications) {
    }

    /** Le dossier de marché du lot : {@code avis} ∈ {@code FAV} · {@code FAVR} · {@code DEF}, nul tant que le PV n'est pas signé. */
    public record DossierMarche(Integer idDossier, String sousType, String statut, String avis, LocalDateTime creeLe, String creePar) {
    }

    /** ⚠️ 2b (§B3) — l'offre attribuée par la PRMP (toujours celle proposée par la CAO, Q4), le montant hors taxes du marché. */
    public record Attributaire(String idOffre, Integer numero, String candidat, String nif, BigDecimal montant, BigDecimal montantTtc,
            String delai, String motif, LocalDateTime le, String par) {
    }

    /**
     * ⚠️ 2b (§B4.1) — l'information des candidats : la date et la PRMP signataire des lettres (signature électronique simple, Q9), la
     * date d'affichage du résultat au siège déclarée, et une lettre par candidat.
     */
    public record Information(LocalDateTime le, String par, String signataire, LocalDate dateAffichage, List<Lettre> lettres) {
    }

    /**
     * Une lettre : {@code type} ∈ {@code ATTRIBUTION} · {@code NON_RETENU} ; {@code envoyeeLe} : la date d'envoi du courriel (nulle sans
     * adresse) ; {@code lueLe} : l'accusé de lecture de la plateforme (première consultation du résultat par le candidat).
     */
    public record Lettre(Long id, String idOffre, Integer numero, String candidat, String type, String motif, LocalDateTime envoyeeLe,
            LocalDateTime lueLe) {
    }

    /**
     * ⚠️ 2b (§B4.1, art. 78 de la loi n° 2016-055) — le délai d'attente de dix jours francs : {@code debut} = la plus tardive de
     * l'information et de l'affichage (ce jour ne compte pas), {@code fin} = le dernier jour du délai, {@code signableLe} = le premier jour
     * où la signature est possible, {@code ecoule} : ce jour est atteint.
     */
    public record DelaiAttente(LocalDate debut, LocalDate fin, LocalDate signableLe, int jours, boolean ecoule) {
    }

    /** ⚠️ 2b (§B4.2, art. 52-II) — une demande d'explication : {@code etat} ∈ {@code EN_ATTENTE} · {@code REPONDUE}. */
    public record Explication(Long id, String idOffre, Integer numero, String candidat, String question, LocalDateTime demandeeLe, String etat,
            String reponse, String reponseNom, Long reponseTaille, LocalDateTime reponduLe) {
    }

    // ------------------------------------------------------------------ corps des requêtes

    /** {@code POST …/lots/{lot}/attribuer} : {@code idOffre} facultatif (l'offre proposée), {@code motif} facultatif. */
    public record AttribuerRequest(String idOffre, String motif) {
    }

    /** {@code POST …/lots/{lot}/informer} : la date d'affichage du résultat au siège de l'autorité contractante. */
    public record InformerRequest(LocalDate dateAffichage) {
    }

    /** {@code POST /api/candidat/offres/{idOffre}/explication}. */
    public record ExplicationRequest(String question) {
    }

    // ------------------------------------------------------------------ côté candidat et public

    /**
     * ⚠️ 2b (§B4.1) — le résultat d'une offre, pour son candidat, après l'information : {@code retenu}, {@code motifRejet} (non retenu),
     * l'attributaire et les caractéristiques de l'offre retenue (montant hors taxes et TTC, délai), la lettre, les dates.
     */
    public record Resultat(String idOffre, Integer numero, Integer lot, boolean retenu, String motifRejet, String attributaire,
            BigDecimal montant, BigDecimal montantTtc, String delai, boolean lettreDisponible, LocalDateTime dateInformation,
            LocalDate dateAffichage, LocalDate finDelai, LocalDate signableLe) {
    }

    /** ⚠️ 2b (§B4.1) — le résultat publié sur la page publique de la procédure, lot par lot, après l'information. */
    public record ResultatPublic(Integer lot, String attributaire, BigDecimal montant, LocalDateTime dateInformation, LocalDate dateAffichage) {
    }
}
