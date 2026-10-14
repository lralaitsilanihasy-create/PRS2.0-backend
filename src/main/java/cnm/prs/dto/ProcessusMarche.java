package cnm.prs.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Processus de marché saisi dans une ligne de marché ({@link SaisieMarcheLigne}) : un
 * {@code idCapm} (FK {@code t_capm}) avec ses dates prévisionnelles début/fin. Persisté en une
 * ligne {@code t_marche_prevision}. Les messages portent le nom de champ attendu par le contrat.
 *
 * <p>⚠️ 2026-10-14 (demande front « q-piece », §B1, H2 ; V114) — {@code sansObjet} : le processus du modèle n'existe pas pour
 * ce marché. Il ne porte alors aucune date et exige {@code motifSansObjet} ; sinon {@code dateDebut} reste obligatoire
 * (vérifié par le service, plus par l'annotation).</p>
 */
public record ProcessusMarche(

        @NotNull(message = "Le processus est obligatoire.")
        Integer idCapm,

        LocalDate dateDebut,

        // dateFin OPTIONNELLE : une prévision peut n'avoir qu'un début (fin non connue / ouverte).
        // La cohérence chronologique (dateDebut<dateFin, séquence) n'est vérifiée que si dateFin est présente.
        LocalDate dateFin,

        Boolean sansObjet,

        @Size(max = 500, message = "Le motif ne peut dépasser 500 caractères.")
        String motifSansObjet) {

    public ProcessusMarche(Integer idCapm, LocalDate dateDebut, LocalDate dateFin) {
        this(idCapm, dateDebut, dateFin, null, null);
    }

    public boolean estSansObjet() {
        return Boolean.TRUE.equals(sansObjet);
    }
}
