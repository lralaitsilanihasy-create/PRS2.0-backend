package cnm.prs.dto;

import java.time.LocalDateTime;

import cnm.prs.entity.TacheDossier;
import cnm.prs.service.HeuresOuvrees;

/**
 * Une occurrence de tache chronometree — chronometrage des delais, 2026-09-01.
 *
 * <p>⚠️ Unite passee du JOUR a l'HEURE ouvree le 2026-09-02 (8 h = 1 jour ouvre, fenetre de service
 * 08:00-16:00). Les horodatages {@code priseEnCharge} / {@code fin} restent a la seconde.</p>
 *
 * @param etape             valeur de {@code EtapeCircuit}
 * @param occurrence        rang du passage (1 = premier ; 2+ = reexamen, navette, boucle FAVR)
 * @param imActeur          matricule du porteur
 * @param nomActeur         « prenoms nom » resolu serveur ; null si le matricule est inconnu
 * @param profil            profil sous lequel l'acteur a agi (delegation / interim compris)
 * @param priseEnCharge     horodatage a la seconde
 * @param fin               null tant que la tache est en cours
 * @param previsionHeures   prevision en heures ouvrees
 * @param previsionStandard vrai si la prevision vient du referentiel et non d'une saisie
 * @param dureeHeuresOuvrees duree effective en heures ouvrees ; pour une tache en cours, le temps deja
 *                          ecoule — compte au plus 8 h par jour ouvre, pour rester dans l'echelle de la
 *                          prevision (sans quoi une tache prise la veille serait en faux depassement)
 * @param enCours           tache non close
 */
public record TacheDossierDto(
        String etape,
        Integer occurrence,
        String imActeur,
        String nomActeur,
        String profil,
        LocalDateTime priseEnCharge,
        LocalDateTime fin,
        Integer previsionHeures,
        boolean previsionStandard,
        long dureeHeuresOuvrees,
        boolean enCours) {

    /** Projection d'une occurrence, la duree etant convertie en heures ouvrees a la restitution. */
    public static TacheDossierDto de(TacheDossier t, String nomActeur) {
        LocalDateTime borne = t.getDateFin() != null ? t.getDateFin() : LocalDateTime.now();
        return new TacheDossierDto(t.getEtape(), t.getOccurrence(), t.getImActeur(), nomActeur,
                t.getProfil(), t.getDatePriseEnCharge(), t.getDateFin(), t.getPrevisionHeures(),
                Boolean.TRUE.equals(t.getPrevisionStandard()),
                HeuresOuvrees.ecoulees(t.getDatePriseEnCharge(), borne), t.enCours());
    }
}
