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
 * @param attributaire           ⚠️ 2026-09-04 — matricule de l ATTRIBUTAIRE COURANT du dossier
 *                               (imCtrlMembre du dispatch, reattributions comprises), ou null tant
 *                               que le dossier n est pas dispatche. Meme derivation que la garde de
 *                               la prise en charge d EXAMEN : le front y masque le geste a quiconque
 *                               n est pas l attributaire, et les ecrans qui ne chargent PAS les
 *                               dispatchs (la consultation) n ont ainsi aucun appel de liste a
 *                               ajouter — le serveur qui repond ici a deja le dispatch sous la main.
 * @param acteursAttendus        ⚠️ 2026-09-04 — matricules que la PRISE EN CHARGE accepterait pour
 *                               l etape courante, ou null quand la liste ne peut pas etre CLOSE.
 *                               EXAMEN : l attributaire. VISA a deux niveaux : le CC dispatcheur au
 *                               niveau CC, les Presidents au niveau PRESIDENT. COSIGNATURE : les
 *                               designes du visa. Partout ailleurs — et sur une navette SIMPLE, ou
 *                               l interim ouvre le visa a tout P/CC du perimetre — null : le front
 *                               replie alors sur le porteur nominal et le serveur tranche.
 *                               ⚠️ null n est PAS « personne » : une liste vide aurait bloque tout le
 *                               monde. C est la MEME valeur que celle sur laquelle porte la garde,
 *                               jamais une derivation voisine.
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
        LocalDate datePrevisionnelleFin,
        String attributaire,
        List<String> acteursAttendus) {
}
