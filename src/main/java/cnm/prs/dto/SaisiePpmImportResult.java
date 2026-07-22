package cnm.prs.dto;

import java.math.BigDecimal;
import java.util.List;

import cnm.prs.enums.ChampAnomalie;
import cnm.prs.enums.GraviteAnomalie;
import cnm.prs.enums.TypeAnomalie;

/**
 * Résultat <strong>read-only</strong> du parsing d'un PPM PDF (endpoint {@code POST /api/saisies/ppm/import}).
 * Sert à <strong>pré-remplir</strong> le formulaire de saisie : rien n'est créé en base. Les référentiels
 * manquants ({@code idNature}/{@code idMode}/{@code numCompte}/{@code soaCode}/entité) ne sont pas créés ici —
 * renvoyés en <em>libellé seul</em> et listés dans {@link #avertissements}. La création reste le
 * {@code POST /api/saisies/ppm} existant (la création-à-la-volée des référentiels s'y fait).
 */
public record SaisiePpmImportResult(
        Integer exercice,
        /** Best-effort (« Fait à… le… », sinon date d'établissement) — {@code null} si introuvable, format {@code yyyy-MM-dd}. */
        String dateSignature,
        /** « Autorité Contractante » telle que lue (pour information). */
        String autoriteContractante,
        /** Entité résolue depuis l'autorité contractante si trouvée, sinon {@code null} → la PRMP choisit dans sa liste. */
        Integer idEntiteContract,
        List<MarcheImport> marches,
        /**
         * Messages non bloquants à plat (référentiel inconnu, champ non résolu…). ⚠️ Conservé pour
         * rétro-compatibilité ; les {@link MarcheImport#anomalies() anomalies structurées} par marché
         * (ligne + champ + gravité) le remplacent avantageusement côté revue front.
         */
        List<String> avertissements,
        /**
         * ⚠️ Règle ajoutée (2026-07-22) — nombre de marchés portant <strong>au moins une anomalie</strong>
         * de transcription (compteur de revue : le front met en avant ces lignes). {@code 0} si tout est net.
         */
        Integer nbAVerifier) {

    /**
     * ⚠️ Règle ajoutée (2026-07-22) — anomalie de transcription <strong>structurée</strong> (contrat de revue
     * front) : le champ exact, le type, la gravité, l'indicateur d'auto-correction et un message prêt à afficher.
     */
    public record AnomalieTranscription(
            ChampAnomalie champ,
            TypeAnomalie type,
            GraviteAnomalie gravite,
            /** {@code true} si le backend a auto-corrigé (à confirmer par l'humain) ; {@code null} sinon. */
            Boolean corrige,
            String message) {
    }

    /** Une ligne du tableau des marchés. {@code id*} résolu si trouvé au référentiel, sinon {@code null} + libellé. */
    public record MarcheImport(
            String designationMarche,
            /**
             * ⚠️ Règle ajoutée (2026-07-18) — forme du marché RELEVÉE dans l'objet (désignation conservée
             * intégrale, même décision que pour les lots) : « contrat cadre » → {@code CONTRAT_CADRE},
             * « à commande » → {@code A_COMMANDE}, sinon défaut {@code QUANTITE_FIXE}. Jamais null.
             */
            String formeMarche,
            BigDecimal montEstim,
            BigDecimal nouvMontEstim,
            Integer idNature,
            String natureLibelle,
            Integer idMode,
            String modeLibelle,
            String financement,
            List<BeneficiaireImport> beneficiaires,
            List<PrevisionImport> previsions,
            /**
             * Lots du marché (⚠️ règle ajoutée) : extraits <strong>best-effort</strong> du motif d'allotissement
             * décrit dans la désignation (« répartis en NN Lots : Lot 01 : … ; Lot 02 : … »). Renseignés
             * ({@code designationLot} seul) uniquement si le compte annoncé égale le nombre de segments trouvés —
             * sinon vides + avertissement. ⚠️ Décision revisitée (2026-07-18) : {@code designationMarche} reste
             * <strong>intégrale</strong> dans tous les cas — l'énumération des lots y demeure, en plus d'ici.
             */
            List<LotImport> lots,
            /**
             * ⚠️ Règle ajoutée (2026-07-22) — anomalies de transcription de <strong>cette ligne</strong>
             * (vide si RAS) : le front pointe le champ exact + la gravité, au lieu de balayer
             * {@code avertissements[]} à plat.
             */
            List<AnomalieTranscription> anomalies) {
    }

    /** Bénéficiaire d'une ligne : compte + montant sont par bénéficiaire. */
    public record BeneficiaireImport(
            String soaCode,
            String numCompte,
            BigDecimal ancMontBenef,
            BigDecimal nouvMontBenef) {
    }

    /** Lot d'une ligne de marché (allotissement) : désignation + montant/quantité/unité optionnels. */
    public record LotImport(
            String designationLot,
            BigDecimal montLot,
            Integer qteLot,
            String uniteLot) {
    }

    /** Prévision (jalon CAPM) : libellé du processus + date de début du jalon ({@code yyyy-MM-dd}). */
    public record PrevisionImport(
            String processus,
            String dateDebut) {
    }
}
