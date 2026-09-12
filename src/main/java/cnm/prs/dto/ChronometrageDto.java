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
 * <p>⚠️ <strong>Refonte du 2026-09-12 — le delai par etape est AUTOMATIQUE.</strong> Les deux champs qui
 * servaient la prise en charge ont disparu : {@code taches} (les occurrences de prise en charge, avec
 * leur prevision saisie) devient {@code etapes} — entree, fin, duree, derivees des transitions deja
 * horodatees — et {@code acteursAttendus} n'existe plus, faute de geste a autoriser. {@code attributaire}
 * demeure : c'est une donnee du dossier, pas du chronometre.</p>
 *
 * @param idDossier              dossier concerne
 * @param etapes                 passages par les etapes, du plus ancien au plus recent, l'etape EN COURS
 *                               fermant la liste ({@code fin = null}, duree = temps deja ecoule)
 * @param debutCompteur          cloture de l'etape RECEPTION (enregistrement) ; null si pas encore atteinte
 * @param finCompteur            cloture de l'etape TRANSMISSION_SIGMP ; null tant que le dossier court
 * @param dureeBruteHeuresOuvrees  compteur BRUT, en HEURES ouvrees : enregistrement -> SIGMP, a la lettre
 * @param dureeNetteHeuresOuvrees  compteur NET CNM : le brut moins les attentes PRMP — c'est lui qui juge la CNM
 * @param attentePrmpHeuresOuvrees cumul, en heures ouvrees, des fenetres ou la balle etait chez la PRMP
 * @param etapeCourante          etape ouverte, ou null si aucune tache CNM n'est en cours
 * @param attentePrmp            vrai si la balle est CHEZ LA PRMP en ce moment (derive du statut courant)
 * @param datePrevisionnelleFin  date annoncee a la PRMP — reste une DATE, seule rescapee de la bascule
 *                               d unite du 2026-09-02 (somme en heures, convertie par tranche de 8 h)
 * @param attributaire           matricule de l ATTRIBUTAIRE COURANT du dossier ({@code imCtrlMembre} du
 *                               dispatch, reattributions comprises), ou null tant que le dossier n est
 *                               pas dispatche. Servi ici pour que les ecrans qui ne chargent PAS les
 *                               dispatchs (la consultation) n aient aucun appel de liste a ajouter — le
 *                               serveur qui repond ici a deja le dispatch sous la main.
 */
public record ChronometrageDto(
        Integer idDossier,
        List<PassageEtapeDto> etapes,
        LocalDateTime debutCompteur,
        LocalDateTime finCompteur,
        long dureeBruteHeuresOuvrees,
        long dureeNetteHeuresOuvrees,
        long attentePrmpHeuresOuvrees,
        String etapeCourante,
        boolean attentePrmp,
        LocalDate datePrevisionnelleFin,
        String attributaire) {
}
