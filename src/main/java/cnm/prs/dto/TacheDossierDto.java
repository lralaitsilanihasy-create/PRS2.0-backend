package cnm.prs.dto;

import java.time.LocalDateTime;

import cnm.prs.entity.TacheDossier;
import cnm.prs.service.JoursOuvres;

/**
 * Une occurrence de tache chronometree — chronometrage des delais, 2026-09-01.
 *
 * @param etape             valeur de {@code EtapeCircuit}
 * @param occurrence        rang du passage (1 = premier ; 2+ = reexamen, navette, boucle FAVR)
 * @param imActeur          matricule du porteur
 * @param nomActeur         « prenoms nom » resolu serveur ; null si le matricule est inconnu
 * @param profil            profil sous lequel l'acteur a agi (delegation / interim compris)
 * @param priseEnCharge     horodatage a la seconde
 * @param fin               null tant que la tache est en cours
 * @param previsionJours    prevision en jours ouvres
 * @param previsionStandard vrai si la prevision vient du referentiel et non d'une saisie
 * @param dureeJoursOuvres  duree effective ; pour une tache en cours, le temps deja ecoule
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
        Integer previsionJours,
        boolean previsionStandard,
        long dureeJoursOuvres,
        boolean enCours) {

    /** Projection d'une occurrence, la duree etant convertie en jours ouvres a la restitution. */
    public static TacheDossierDto de(TacheDossier t, String nomActeur) {
        LocalDateTime borne = t.getDateFin() != null ? t.getDateFin() : LocalDateTime.now();
        return new TacheDossierDto(t.getEtape(), t.getOccurrence(), t.getImActeur(), nomActeur,
                t.getProfil(), t.getDatePriseEnCharge(), t.getDateFin(), t.getPrevisionJours(),
                Boolean.TRUE.equals(t.getPrevisionStandard()),
                JoursOuvres.ecoules(t.getDatePriseEnCharge(), borne), t.enCours());
    }
}
