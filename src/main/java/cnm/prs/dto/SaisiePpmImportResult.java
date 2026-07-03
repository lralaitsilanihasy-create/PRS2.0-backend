package cnm.prs.dto;

import java.math.BigDecimal;
import java.util.List;

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
        /** Messages non bloquants (référentiel inconnu, champ non résolu…). */
        List<String> avertissements) {

    /** Une ligne du tableau des marchés. {@code id*} résolu si trouvé au référentiel, sinon {@code null} + libellé. */
    public record MarcheImport(
            String designationMarche,
            BigDecimal montEstim,
            BigDecimal nouvMontEstim,
            Integer idNature,
            String natureLibelle,
            Integer idMode,
            String modeLibelle,
            String financement,
            List<BeneficiaireImport> beneficiaires,
            List<PrevisionImport> previsions) {
    }

    /** Bénéficiaire d'une ligne : compte + montant sont par bénéficiaire. */
    public record BeneficiaireImport(
            String soaCode,
            String numCompte,
            BigDecimal ancMontBenef,
            BigDecimal nouvMontBenef) {
    }

    /** Prévision (jalon CAPM) : libellé du processus + date de début du jalon ({@code yyyy-MM-dd}). */
    public record PrevisionImport(
            String processus,
            String dateDebut) {
    }
}
