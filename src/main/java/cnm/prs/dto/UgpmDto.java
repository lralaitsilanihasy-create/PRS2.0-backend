package cnm.prs.dto;

import java.time.LocalDate;

/** UGPM, sa PRMP de tutelle et son identité (lecture). */
public record UgpmDto(
        String idUgpm,
        String libelle,
        String idPrmpTutelle,
        String nomUgpm,
        String prenomsUgpm,
        String imUgpm,
        String cin,
        LocalDate dateCin,
        String lieuCin,
        String emailUgpm,
        String telUgpm) {
}
