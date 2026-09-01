package cnm.prs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Chronometrage complet d'un dossier — chronometrage des delais, 2026-09-01. Matiere de la frise front.
 *
 * @param idDossier              dossier concerne
 * @param taches                 occurrences, de la plus ancienne a la plus recente
 * @param debutCompteur          cloture de l'etape RECEPTION (enregistrement) ; null si pas encore atteinte
 * @param finCompteur            cloture de l'etape TRANSMISSION_SIGMP ; null tant que le dossier court
 * @param dureeBruteJoursOuvres  compteur BRUT : enregistrement -> SIGMP, a la lettre de la regle
 * @param dureeNetteJoursOuvres  compteur NET CNM : le brut moins les attentes PRMP — c'est lui qui juge la CNM
 * @param attentePrmpJoursOuvres cumul des fenetres ou la balle etait chez la PRMP
 * @param etapeCourante          etape ouverte, ou null si aucune tache CNM n'est en cours
 * @param attentePrmp            vrai si la balle est CHEZ LA PRMP en ce moment (derive du statut courant)
 * @param datePrevisionnelleFin  date annoncee a la PRMP, en jours ouvres
 */
public record ChronometrageDto(
        Integer idDossier,
        List<TacheDossierDto> taches,
        LocalDateTime debutCompteur,
        LocalDateTime finCompteur,
        long dureeBruteJoursOuvres,
        long dureeNetteJoursOuvres,
        long attentePrmpJoursOuvres,
        String etapeCourante,
        boolean attentePrmp,
        LocalDate datePrevisionnelleFin) {
}
