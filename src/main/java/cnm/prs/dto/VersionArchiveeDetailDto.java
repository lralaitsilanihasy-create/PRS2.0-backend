package cnm.prs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ⚠️ Versions archivées (demande pilote du 2026-09-06) — contenu <strong>complet, en lecture seule</strong>
 * d'une version archivée ({@code GET /api/dossiers/{id}/versions-archivees/{numero}}) : l'en-tête et
 * les lignes de marché telles qu'elles étaient, avec leurs bénéficiaires, lots et dates prévisionnelles.
 *
 * <p>Les lignes reprennent les <strong>mêmes champs</strong> que {@link MarcheDto} (montants,
 * classification, forme, justifications, suppression logique) pour que le front les pose dans son
 * tableau partagé sans adaptation. Elles sont ordonnées par {@code idDetail}, comme le diff.</p>
 *
 * <p><strong>Version reprise d'avant la V18</strong> (l'instantané du dernier cycle, devenu version
 * n° 1) : ses collections n'avaient été figées que par empreinte ; elles sont <em>reconstituées</em>
 * depuis ces empreintes, en mode dégradé — désignations de lots normalisées en minuscules, unité de
 * lot absente, un seul montant par bénéficiaire (porté par {@code nouvMontBenef}), compte budgétaire
 * du bénéficiaire absent.</p>
 *
 * @param version en-tête de la version
 * @param lignes  lignes de marché figées
 */
public record VersionArchiveeDetailDto(
        VersionArchiveeDto version,
        List<LigneVersion> lignes) {

    /** Une ligne de marché telle qu'elle était dans la version — mêmes champs que {@link MarcheDto}. */
    public record LigneVersion(
            Integer idDetail,
            Integer idLigneOrigine,
            String designationMarche,
            String numCompte,
            BigDecimal montEstim,
            BigDecimal ancienMontEstim,
            BigDecimal nouvMontEstim,
            String financement,
            String statut,
            Integer idNature,
            Integer idMode,
            String formeMarche,
            Boolean supprimee,
            String justifModeDerogatoire,
            String justifDelaiAmenage,
            List<BeneficiaireVersion> beneficiaires,
            List<LotVersion> lots,
            List<PrevisionVersion> processus) {
    }

    /** Un service bénéficiaire figé — mêmes champs que {@link ServiceBeneficiaireDto}, sans identifiants. */
    public record BeneficiaireVersion(
            String soaCode,
            String numCompte,
            BigDecimal ancMontBenef,
            BigDecimal nouvMontBenef) {
    }

    /** Un lot figé — mêmes champs que {@link LotDto}, sans identifiants. */
    public record LotVersion(
            String designationLot,
            BigDecimal montLot,
            Integer qteLot,
            String uniteLot) {
    }

    /**
     * Une date prévisionnelle figée — mêmes champs que {@link MarchePrevisionDto}, sans identifiants.
     * {@code ordre} est l'ordre d'affichage <em>actuel</em> du processus ({@code t_capm.ORDRE}), lu à la
     * restitution : c'est un attribut du référentiel, pas de la version.
     */
    public record PrevisionVersion(
            Integer idCapm,
            Integer ordre,
            LocalDate dateDebut,
            LocalDate dateFin) {
    }
}
