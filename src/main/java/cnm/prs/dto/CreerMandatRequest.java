package cnm.prs.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/mandats} — nomination ou <strong>reconduction</strong> d'une PRMP.
 *
 * <p>Une reconduction se déclare exactement comme une nomination : c'est un mandat distinct, avec son
 * propre arrêté et ses propres dates. Il n'existe volontairement aucun endpoint de « prolongation ».</p>
 *
 * @param idPrmp    titulaire du mandat (matricule PRMP)
 * @param refArrete référence de l'arrêté de nomination — obligatoire et jamais réutilisée
 * @param dateDebut prise de fonction
 * @param dateFin   fin de mandat ; à défaut {@code dateDebut + 3 ans - 1 jour}
 * @param titulaire nom du titulaire à figer ; à défaut « prénoms nom » lus sur {@code t_prmp}
 */
public record CreerMandatRequest(
        @NotBlank @Size(max = 10) String idPrmp,
        @NotBlank @Size(max = 100) String refArrete,
        @NotNull LocalDate dateDebut,
        LocalDate dateFin,
        @Size(max = 200) String titulaire) {
}
