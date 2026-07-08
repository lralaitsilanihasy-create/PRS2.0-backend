package cnm.prs.dto;

import java.time.LocalDate;

/**
 * UGPM (idUgpm = matricule), sa PRMP de tutelle, son identité et son {@code login} (lecture seule, pour
 * pré-remplir la réinitialisation du mot de passe côté admin). Le mot de passe n'est jamais exposé.
 */
public record UgpmDto(
        String idUgpm,
        String libelle,
        String idPrmpTutelle,
        String nomUgpm,
        String prenomsUgpm,
        String cin,
        LocalDate dateCin,
        String lieuCin,
        String emailUgpm,
        String telUgpm,
        String login) {
}
