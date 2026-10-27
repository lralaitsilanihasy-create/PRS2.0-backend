package cnm.prs.dto;

import java.math.BigDecimal;

/**
 * Une ligne de PPM éligible à un appel d'offres ({@code GET /api/dmcs/eligibles}, demande du 2026-09-22, §B2, H4) :
 * plan au PV signé (avis favorable, ou réserves levées), mode mappé au type DMC {@code DAO}, ligne non retirée.
 * {@code dejaDao} dit qu'un DMC existe déjà ({@code idDmc} pour rouvrir la fiche).
 *
 * <p>⚠️ Lot 1c (2026-09-23, §B2) — {@code formeMarche} : la forme saisie au plan ({@code A_COMMANDE},
 * {@code CONTRAT_CADRE}, {@code QUANTITE_FIXE}, {@code null} si non renseignée) ; {@code formeOutillee} : la fiche sait la
 * préparer. Une ligne non outillée <strong>reste listée</strong> (le front la montre désactivée, avec sa forme) ; sa
 * création répond 409 {@code FORME_NON_OUTILLEE}.</p>
 */
public record LigneEligibleDto(Integer idDetail, Integer idDossier, String refeDossier, String designationMarche,
        Integer idMode, String libelleMode, BigDecimal montEstim, boolean dejaDao, Long idDmc, String formeMarche,
        boolean formeOutillee,
        /** ⚠️ Lot 5 (2026-09-24) — catégorie de fiche DAO lue sur la nature de la ligne ; {@code null} si inconnue. */
        String categorie,
        /** ⚠️ Lot 5 — la fiche sait préparer cette catégorie (jumeau de {@code formeOutillee}). */
        boolean categorieOutillee,
        /** ⚠️ 2026-09-29 — la nature de la ligne au plan ({@code t_marche.ID_NATURE}) ; {@code null} si la ligne n'en a pas. */
        Integer idNature,
        /**
         * ⚠️ 2026-09-29 — son libellé ({@code t_nature.LIBELLE}), ce que la PRMP lit (« Services » et « Fournitures » sont
         * deux natures d'une même catégorie) ; {@code null} si la ligne n'a pas de nature.
         */
        String libelleNature,
        /** ⚠️ 2026-10-06 (compteurs, §B2) — les reçus de frais de dossier {@code EN_ATTENTE} de la fiche ; 0 sans fiche ou en papier. */
        long recusEnAttente,
        /** ⚠️ §B2 — le nombre seul des offres déposées (ou écartées) ; 0 sans fiche ou en papier. */
        long nbOffres,
        /**
         * ⚠️ 2026-10-14 (demande front « sous-type-ligne », §B1) — le sous-type du dossier que la fiche de cette ligne produira
         * ({@code DAOO}, {@code DAOR}, {@code DAOOI}, {@code DAORI}, {@code DAOOPREQUAL} ou {@code DC}), calculé par la règle de la
         * validation ({@link cnm.prs.service.SousTypesDossier#dossierMiseEnConcurrence}) sur le mode et la catégorie de la ligne
         * <strong>à la date de la lecture</strong> (H1) ; jamais {@code DAO} (H2).
         */
        String sousType,
        /**
         * ⚠️ 2026-10-19 (demande front « etat-fiche-liste », §B1) — la fiche de la ligne : statut et numéro de sa dernière version
         * ({@code BROUILLON} v1 tant qu'aucune version n'est enregistrée, {@code BROUILLON} sur une révision ouverte) ;
         * {@code null} sans fiche.
         */
        EtatFiche fiche,
        /** ⚠️ §B1 — le dossier soumis né de la fiche, son statut courant ; {@code null} sans fiche ou tant qu'il n'est pas créé. */
        EtatDossier dossier,
        /**
         * ⚠️ 2026-10-19 (demande front « dossier-marche-lecture », §B2) — les dossiers de marché nés de l'attribution de la fiche, un
         * par lot qui a le sien, lus sur {@code t_attribution} ; vide tant qu'aucun lot n'a son dossier, {@code null} sans fiche.
         */
        java.util.List<DossierMarche> dossiersMarche,
        /**
         * ⚠️ 2026-10-20 (demande front « liste-dossier-marche », §B1) — le numéro de la procédure, règle de l'en-tête
         * ({@code DaoCompletService.numero}) sur la dernière version de la fiche : {@code B02-OE-01} pour un contrat-cadre, puis
         * {@code B02-OB-03}, à défaut la référence du plan ; {@code null} sans fiche.
         */
        String numeroProcedure,
        /**
         * ⚠️ §B2 — l'état de l'attribution, par lot, calculé par la règle de {@code GET /api/fiches-marche/{idDmc}/attribution} ; vide
         * tant que l'évaluation n'est pas ouverte, {@code null} sans fiche.
         */
        java.util.List<AttributionLot> attributions,
        /**
         * ⚠️ 2026-10-27 (demande front « dossier-marche-remise-papier », P1) — le mode de remise figé par la dernière version validée de
         * la fiche ({@code PAPIER} ou {@code ELECTRONIQUE}) ; {@code null} sans fiche ou sans version validée.
         */
        String modeRemise,
        /** ⚠️ P1 — les lots de la procédure (ceux de la ligne du plan ; un lot 1 pour une ligne sans lot) ; {@code null} sans fiche. */
        java.util.List<LotProcedure> lots,
        /**
         * ⚠️ P1 — une procédure papier seulement : son dossier de marché peut-il naître ({@code ouvert}) ; sinon la première raison
         * ({@code motif} : {@code FICHE_NON_VALIDEE}, {@code DOSSIER_NON_CLOTURE}, {@code AVIS_NON_FAVORABLE}, {@code AVIS_NON_PUBLIE}).
         * {@code null} hors remise papier.
         */
        MarchePapier marchePapier) {

    /** Un lot de la procédure : son numéro et son intitulé (« Lot n°02 : … », nul pour une ligne sans lot). */
    public record LotProcedure(Integer lot, String intitule) {
    }

    /** P1 — l'ouverture du dossier de marché d'une procédure papier, et sa raison quand elle est fermée. */
    public record MarchePapier(boolean ouvert, String motif) {
    }

    /**
     * L'attribution d'un lot : son état ({@code EN_EVALUATION}, {@code PROPOSE}, {@code AU_CONTROLE}…), la signature du rapport
     * d'évaluation (nulle avant), la proposition du rapport (nulle avant la clôture, ou pour un lot proposé infructueux).
     */
    public record AttributionLot(Integer lot, String etat, java.time.LocalDateTime rapportSigneLe, Propose propose) {
    }

    /** L'offre proposée au rang 1 ; {@code nbTitulaires} > 1 pour un contrat-cadre à plusieurs titulaires. */
    public record Propose(String candidat, BigDecimal montant, int nbTitulaires) {
    }

    /** Le dossier de marché d'un lot : identifiant, référence (nulle avant la réception), statut courant. */
    public record DossierMarche(Integer lot, Integer idDossier, String refeDossier, String statut) {
    }

    /** L'état de la fiche d'une ligne : {@code BROUILLON} ou {@code VALIDEE}, et le numéro de la dernière version. */
    public record EtatFiche(String statut, Integer version) {
    }

    /** Le dossier soumis d'une fiche : identifiant, référence, statut courant. */
    public record EtatDossier(Integer idDossier, String refeDossier, String statut) {
    }
}
