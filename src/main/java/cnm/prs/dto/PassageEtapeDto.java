package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * Un PASSAGE par une etape du circuit — chronometrage des delais, refonte du 2026-09-12.
 *
 * <p>⚠️ <strong>Remplace l'ancien {@code TacheDossierDto}</strong>, qui decrivait une prise en charge
 * (son horodatage, sa prevision saisie, son drapeau « standard »). La prise en charge n'existe plus :
 * un passage se lit desormais en trois valeurs — <strong>entree</strong>, <strong>fin</strong>,
 * <strong>duree</strong> — toutes les trois DERIVEES des transitions deja horodatees. Rien ne s'y
 * saisit.</p>
 *
 * <p>L'<strong>entree</strong> est l'instant ou le dossier est entre dans l'etape : la fin du passage
 * precedent, ou — si elle est posterieure — la date de depot du dossier ou la sortie d'une attente PRMP
 * (le temps ou la balle etait chez la PRMP n'est pas impute a l'etape qui reprend).</p>
 *
 * <p>La <strong>duree</strong> est en HEURES OUVREES (8 h = 1 jour ouvre, fenetre de service
 * 08:00–16:00), comme tous les compteurs depuis le 2026-09-02 ; les horodatages restent a la seconde.</p>
 *
 * @param etape              valeur de {@code EtapeCircuit}
 * @param occurrence         rang du passage (1 = premier ; 2+ = reexamen, navette, boucle FAVR)
 * @param imActeur           matricule de l'acteur a qui l'etape revenait
 * @param nomActeur          « prenoms nom » resolu serveur ; null si le matricule est inconnu
 * @param profil             profil sous lequel l'etape a ete tenue (delegation / interim compris)
 * @param entree             entree dans l'etape, derivee ; null si aucune borne anterieure n'est connue
 * @param fin                fin de l'etape ; null pour l'etape EN COURS
 * @param dureeHeuresOuvrees fin − entree en heures ouvrees ; pour l'etape en cours, le temps deja ecoule
 * @param enCours            vrai pour l'etape ouverte du dossier (la seule sans fin)
 */
public record PassageEtapeDto(
        String etape,
        Integer occurrence,
        String imActeur,
        String nomActeur,
        String profil,
        LocalDateTime entree,
        LocalDateTime fin,
        long dureeHeuresOuvrees,
        boolean enCours) {
}
