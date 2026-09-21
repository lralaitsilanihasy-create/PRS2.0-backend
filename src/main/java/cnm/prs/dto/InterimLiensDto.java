package cnm.prs.dto;

import java.time.LocalDate;

/**
 * Les deux liens d'intérim portés par {@link ControleurDto} (annuaire, demande du 2026-09-21, §B5) :
 * un titulaire absent est « suppléé par Y jusqu'au D2 », un intérimaire « supplée X jusqu'au D2 ».
 * Résolus en lot sur les intérims ACTIFS à la date du jour.
 */
public final class InterimLiensDto {

    private InterimLiensDto() {
    }

    /** Sur la fiche du <strong>titulaire</strong> : qui le supplée aujourd'hui. */
    public record EnCours(Integer idInterim, String imInterimaire, String nomInterimaire, LocalDate dateFin) {
    }

    /** Sur la fiche de l'<strong>intérimaire</strong> : qui il supplée aujourd'hui (une entrée par intérim). */
    public record Pour(Integer idInterim, String imTitulaire, String nomTitulaire, LocalDate dateFin) {
    }
}
