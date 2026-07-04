package cnm.prs.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Ligne de marché saisie via la façade PPM. {@code idDossier}, {@code idPpm} et {@code idMode}
 * sont renseignés par le service (le mode est déterminé automatiquement, §3.1 M02).
 */
public record SaisieMarcheLigne(

        // idDetail : null à la création (PK allouée par le serveur) ; renseigné pour identifier une
        // ligne existante lors de l'édition d'un brouillon (réconciliation par idDetail).
        Integer idDetail,

        @Size(max = 500)
        String designationMarche,

        @Size(max = 20)
        String numCompte,

        BigDecimal montEstim,

        @Size(max = 20)
        String financement,

        @Size(max = 20)
        String statut,

        Integer idNature,

        // Processus de marché + dates prévisionnelles (idCapm, dateDebut, dateFin). @Valid cascade la
        // validation par processus (chemins marches[i].processus[j].champ). « Au moins un processus »
        // est exigé à la CRÉATION (SaisieService) — pas via @NotEmpty ici, pour ne pas casser l'édition
        // qui partage ce DTO (EditionPpmRequest).
        @Valid
        List<ProcessusMarche> processus,

        // idMode : mode de passation CHOISI par la PRMP (facultatif) ; null → mode recommandé.
        // Le serveur valide qu'il appartient à l'ensemble autorisé (sinon 409).
        Integer idMode) {
}
