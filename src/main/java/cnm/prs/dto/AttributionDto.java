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
            List<Explication> explications,
            /** ⚠️ 2c (§B4.3, §B4.4, §B5) — nuls (ou liste vide) avant leur geste. */
            MiseAuPoint miseAuPoint, List<Recours> recours, Signature signature, Enregistrement enregistrement, NotificationMarche notification,
            AvisAttribution avisAttribution, PiecesAttributaire piecesAttributaire, Retrait retrait,
            /** ⚠️ 2d-1 (§B6, Q3) — la déclaration d'infructuosité (nulle sans elle) ; les reprises de l'évaluation après un avis défavorable. */
            Infructuosite infructuosite, List<Reprise> reprises) {
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

    // ------------------------------------------------------------------ ⚠️ tranche 2c (§B4.3, §B4.4, §B5)

    /** Un fichier de l'attribution, téléchargeable par {@code GET …/attribution/pieces/{id}/fichier}. */
    public record Fichier(Long id, String nature, String nom, String format, Long taille, LocalDateTime deposeLe) {
    }

    /** La mise au point (art. 35-VIII) : le rapport, et son fichier s'il y en a un. */
    public record MiseAuPoint(String rapport, LocalDateTime le, String par, Fichier fichier) {
    }

    /**
     * Un recours (Q5, titre VIII) : {@code type} ∈ {@code REEXAMEN} (non suspensif ; {@code echeanceReponse} = réception + 10 jours) ·
     * {@code REVISION_ARMP} · {@code REFERE} (suspensifs ; {@code finSuspension} = réception + 20 jours) ; {@code bloquant} : il ferme
     * la signature aujourd'hui (suspensif, sans décision, suspension en cours).
     */
    public record Recours(Long id, String type, LocalDate dateReception, String requerant, String objet, LocalDateTime declareLe,
            String declarePar, List<Fichier> fichiers, boolean suspensif, LocalDate finSuspension, LocalDate echeanceReponse, boolean bloquant,
            DecisionRecours decision) {
    }

    /** La décision d'un recours : {@code issue} ∈ {@code REJETE} · {@code ACCUEILLI} · {@code AUTRE}. */
    public record DecisionRecours(LocalDate date, String issue, String motif, LocalDateTime le, String par, List<Fichier> fichiers) {
    }

    /** La signature du marché : la date déclarée, le marché signé déposé. */
    public record Signature(LocalDate dateSignature, LocalDateTime le, String par, Fichier fichier) {
    }

    /** L'enregistrement du marché (art. 54, Q6) : la date, la référence, le justificatif. */
    public record Enregistrement(LocalDate date, String reference, LocalDateTime le, Fichier fichier) {
    }

    /**
     * La notification (art. 54) : {@code recueLe} = la réception par l'attributaire, date d'effet du marché — accusé de lecture de la
     * plateforme ({@code receptionDeclaree} faux) ou date déclarée par la PRMP (vrai).
     */
    public record NotificationMarche(LocalDate date, LocalDateTime le, String par, LocalDateTime recueLe, Boolean receptionDeclaree) {
    }

    /** L'avis d'attribution (art. 53) : {@code echeance} = notification + 30 jours ; {@code disponible} une fois publié. */
    public record AvisAttribution(LocalDate echeance, LocalDate datePublication, LocalDateTime publieLe, String par, boolean disponible) {
    }

    /**
     * Les pièces fiscales et sociales de l'attributaire (art. 20-I) : {@code echeance} = information + 15 jours ; les dernières pièces
     * de chaque type, et si elles sont reconnues conformes.
     */
    public record PiecesAttributaire(LocalDate echeance, boolean delaiDepasse, boolean fiscaleConforme, boolean socialeConforme,
            List<PieceAttributaire> pieces) {
    }

    /** Une pièce de l'attributaire : {@code type} ∈ {@code FISCALE} · {@code SOCIALE} ; {@code conforme} nul tant qu'elle n'est pas vérifiée. */
    public record PieceAttributaire(Long id, String type, LocalDate dateDelivrance, String nom, Long taille, LocalDateTime deposeLe,
            Boolean conforme, String motif, LocalDateTime verifieeLe) {
    }

    /** Le retrait du marché faute de pièces fiscales et sociales (art. 20-I). */
    public record Retrait(LocalDateTime le, String par, String motif) {
    }

    /** {@code POST …/lots/{lot}/notification}. */
    public record NotificationRequest(LocalDate dateNotification, LocalDate dateReception) {
    }

    /** {@code POST …/lots/{lot}/avis}. */
    public record AvisRequest(LocalDate datePublication) {
    }

    /** {@code POST …/lots/{lot}/pieces/{id}/verifier}. */
    public record VerificationRequest(Boolean conforme, String motif) {
    }

    /** {@code POST …/lots/{lot}/retirer}. */
    public record RetraitRequest(String motif) {
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
            LocalDate dateAffichage, LocalDate finDelai, LocalDate signableLe,
            /** ⚠️ 2c — la signature, la notification et sa réception, le marché signé ; l'attributaire seul : ses pièces, le retrait. */
            LocalDate dateSignature, LocalDate dateNotification, LocalDateTime notificationRecueLe, boolean marcheDisponible,
            PiecesAttributaire piecesAttributaire, boolean retire) {
    }

    /** ⚠️ 2b (§B4.1) — le résultat publié sur la page publique de la procédure, lot par lot, après l'information. */
    public record ResultatPublic(Integer lot, String attributaire, BigDecimal montant, LocalDateTime dateInformation, LocalDate dateAffichage,
            /** ⚠️ 2c (§B4.4) — l'avis d'attribution publié : {@code GET /api/procedures-en-ligne/{idDmc}/avis-attribution/{lot}}. */
            LocalDate datePublicationAvis, boolean avisDisponible,
            /** ⚠️ 2d-1 (§B6) — le lot déclaré infructueux (attributaire et montant nuls) : le motif et la date de la décision. */
            boolean infructueux, String motifInfructuosite, LocalDate dateDecision,
            /** ⚠️ 2d-3 (§B6) — la procédure déclarée sans suite (une seule entrée, {@code lot} nul) : ses motifs ; la date dans {@code dateDecision}. */
            boolean sansSuite, String motifsSansSuite) {
    }
/** ⚠️ 2d-1 (§B6) — la déclaration d'infructuosité du lot par la PRMP ; {@code suite} ∈ {@code RELANCE} · {@code RESTREINTE} · {@code NEGOCIEE}. */    public record Infructuosite(LocalDateTime le, String par, String motif, String decisionReference, LocalDate decisionDate, String suite) {    }    /** ⚠️ 2d-1 (Q3) — une reprise de l'évaluation : le dossier de marché refusé, son avis, le motif ; le rapport archivé. */    public record Reprise(Long id, LocalDateTime le, String par, String motif, Integer idDossier, String avis, boolean rapportDisponible,
            /** ⚠️ 2d-2 — {@code REPRISE} (avis défavorable) ou {@code REATTRIBUTION} (retrait) ; l'offre retirée ; la note de validité. */
            String type, String idOffreRetiree, String note) {    }    /** {@code POST …/lots/{lot}/infructueux} : le motif, la décision de la PRMP (référence, date), la suite déclarée (facultative). */    public record InfructuositeRequest(String motif, Decision decision, String suite) {    }    public record Decision(String reference, LocalDate date) {    }    /** {@code POST …/lots/{lot}/reprendre} : le motif de la reprise de l'évaluation. */    public record RepriseRequest(String motif) {    }
}
