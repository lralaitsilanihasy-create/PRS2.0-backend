package cnm.prs.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Part JSON ({@code data}) de l'inscription PRMP v2 (multipart). Accompagne les fichiers
 * {@code arrete}, {@code cin} (obligatoires) et {@code photo} (optionnel).
 *
 * <p>La PRMP déclare ses entités contractantes : soit des entités <strong>existantes</strong>
 * (par id, dans {@code idEntites}), soit des entités <strong>non listées</strong> proposées
 * (dans {@code entitesNonListees}). Au moins une entité (existante ou proposée) est requise —
 * contrôlé côté service.</p>
 */
public record RegisterPrmpV2Request(

        @NotBlank @Size(max = 100) String login,
        @NotBlank @MotDePasseValide String motDePasse,

        @NotBlank @Size(max = 10) String idPrmp,
        @NotBlank @Size(max = 100) String nomPrmp,
        @NotBlank @Size(max = 100) String prenomsPrmp,
        @NotBlank @Size(max = 100) String arreteNomin,
        @NotNull LocalDate dateNomin,
        @NotBlank @Size(max = 12) String cin,
        @NotNull LocalDate dateCin,
        @NotBlank @Size(max = 50) String lieuCin,
        @NotBlank @Size(max = 100) String emailPrmp,
        @NotBlank @Size(max = 20) String telPrmp,

        List<Integer> idEntites,

        // ⚠️ @Valid sur le paramètre de type (cf. SaisiePpmRequest) — sur la List, il est déprécié.
        List<@Valid EntiteNonListeeRequest> entitesNonListees) {
}
