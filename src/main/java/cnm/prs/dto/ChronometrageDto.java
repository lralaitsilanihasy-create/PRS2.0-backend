package cnm.prs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Chronometrage complet d un dossier — chronometrage des delais, 2026-09-01. Matiere de la frise front.
 *
 * <p>⚠️ Tous les compteurs sont en HEURES ouvrees depuis le 2026-09-02 (8 h = 1 jour ouvre) : une seule
 * unite partout, aucune somme ne melange les deux.</p>
 *
 * @param idDossier              dossier concerne
 * @param taches                 occurrences, de la plus ancienne a la plus recente
 * @param debutCompteur          cloture de l'etape RECEPTION (enregistrement) ; null si pas encore atteinte
 * @param finCompteur            cloture de l'etape TRANSMISSION_SIGMP ; null tant que le dossier court
 * @param dureeBruteHeuresOuvrees  compteur BRUT, en HEURES ouvrees : enregistrement -> SIGMP, a la lettre
 * @param dureeNetteHeuresOuvrees  compteur NET CNM : le brut moins les attentes PRMP — c'est lui qui juge la CNM
 * @param attentePrmpHeuresOuvrees cumul, en heures ouvrees, des fenetres ou la balle etait chez la PRMP
 * @param etapeCourante          etape ouverte, ou null si aucune tache CNM n'est en cours
 * @param attentePrmp            vrai si la balle est CHEZ LA PRMP en ce moment (derive du statut courant)
 * @param datePrevisionnelleFin  date annoncee a la PRMP — reste une DATE, seule rescapee de la bascule
 *                               d unite du 2026-09-02 (somme en heures, convertie par tranche de 8 h)
 */
public record ChronometrageDto(
        Integer idDossier,
        List<TacheDossierDto> taches,
        LocalDateTime debutCompteur,
        LocalDateTime finCompteur,
        long dureeBruteHeuresOuvrees,
        long dureeNetteHeuresOuvrees,
        long attentePrmpHeuresOuvrees,
        String etapeCourante,
        boolean attentePrmp,
        LocalDate datePrevisionnelleFin) {
}
