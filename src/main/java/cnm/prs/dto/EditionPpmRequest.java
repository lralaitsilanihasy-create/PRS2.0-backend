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

        @Valid
        List<SaisieMarcheLigne> marches) {
}
