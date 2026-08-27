package cnm.prs.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Édition d'un brouillon PPM en un appel (façade, §3.1 M02) : met à jour l'en-tête du PPM et
 * <strong>remplace</strong> l'ensemble de ses lignes de marché par la liste fournie (ajout des
 * nouvelles, mise à jour des existantes par {@code idDetail}, retrait des absentes). La localité,
 * le type, le propriétaire et l'entité du dossier ne changent pas (fixés à la saisie).
 *
 * <p>⚠️ Règle corrigée (2026-07-18) — les <strong>sous-objets</strong> des lignes
 * ({@code beneficiaires[]}, {@code lots[]}, {@code processus[]}) sont traités comme au POST, avec les
 * mêmes validations (Σ bénéficiaires, chronologie, ≥1 processus par ligne nouvelle). Pour une ligne
 * <em>mise à jour</em> ({@code idDetail} fourni) : liste <strong>fournie</strong> = remplacement
 * complet des enfants de ce type ; liste <strong>absente</strong> ({@code null}) = enfants conservés.</p>
 */
public record EditionPpmRequest(

        @NotNull
        Integer exercice,

        @NotBlank
        @Size(max = 210)
        String signataire,

        @NotNull
        LocalDate dateSignature,

        @NotBlank
        @Size(max = 100)
        String reference,

        /**
         * ⚠️ 2026-08-05 (versionnement des PPM) — motif de la mise à jour, corrigeable tant que la
         * version n'est pas soumise. Sans effet sur un dossier qui n'est pas une version ; {@code null}
         * laisse le motif inchangé (il reste obligatoire à la création de la version).
         */
        @Size(max = 500)
        String motifMaj,

        // ⚠️ @Valid sur le paramètre de type (cf. SaisiePpmRequest) — sur la List, il est déprécié.
        List<@Valid SaisieMarcheLigne> marches) {
}
