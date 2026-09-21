package cnm.prs.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import cnm.prs.enums.MotifInterim;

/**
 * Partie {@code data} de {@code POST /api/interims} (multipart, avec la partie {@code piece}) — désignation
 * d'un intérimaire par le titulaire absent (demande front du 2026-09-21, §B2).
 *
 * <p>{@code dateFin} est obligatoire sauf pour {@link MotifInterim#VACANCE_POSTE} — vérifié en service
 * (400), la contrainte dépendant du motif. Il n'existe volontairement ni PUT ni DELETE : une prolongation
 * est un nouvel intérim.</p>
 *
 * @param imTitulaire   le contrôleur absent — doit être l'utilisateur de session (ou l'Administrateur agit en repli)
 * @param imInterimaire le désigné
 * @param dateDebut     premier jour inclus
 * @param dateFin       dernier jour inclus ; nulle seulement pour {@code VACANCE_POSTE}
 * @param motif         voir {@link MotifInterim}
 * @param reference     note de service / décision de désignation
 */
public record CreerInterimRequest(
        @NotBlank @Size(max = 10) String imTitulaire,
        @NotBlank @Size(max = 10) String imInterimaire,
        @NotNull LocalDate dateDebut,
        LocalDate dateFin,
        @NotNull MotifInterim motif,
        @NotBlank @Size(max = 100) String reference) {
}
