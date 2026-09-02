package cnm.prs.service;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * ⚠️ <strong>Chronométrage des délais</strong> — arithmétique du <strong>calendrier</strong> ouvré :
 * quels jours comptent, et comment décaler une date.
 *
 * <p><strong>Samedi et dimanche exclus ; jours fériés hors périmètre v1</strong> (arbitrage ③, 2026-09-01).
 * Les horodatages restent enregistrés <strong>à la seconde</strong> ; seule la restitution convertit.</p>
 *
 * <p>⚠️ Depuis le 2026-09-02, l'unité du chronométrage est l'<strong>heure ouvrée</strong>
 * ({@link HeuresOuvrees}, 8 h = 1 jour ouvré). Cette classe ne porte plus d'<em>écoulé</em> : mesurer une
 * durée en jours à partir de deux horodatages offrait au premier appelant venu le piège d'échelle que la
 * règle du 02/09 met en garde — une prévision en heures de service confrontée à des jours de calendrier.
 * L'écoulé vit désormais dans {@link HeuresOuvrees#ecoulees}, et nulle part ailleurs.</p>
 */
public final class JoursOuvres {

    private JoursOuvres() {
    }

    /** Vrai si la date tombe un jour ouvré (lundi → vendredi). */
    public static boolean estOuvre(LocalDate date) {
        DayOfWeek jour = date.getDayOfWeek();
        return jour != DayOfWeek.SATURDAY && jour != DayOfWeek.SUNDAY;
    }

    /**
     * Jours ouvrés entre deux dates, borne de départ <strong>exclue</strong> et borne d'arrivée
     * <strong>incluse</strong> : du vendredi au lundi = 1 (le week-end ne compte pas).
     */
    public static long entre(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null || fin.isBefore(debut)) {
            return 0L;
        }
        long compte = 0L;
        LocalDate courant = debut;
        while (courant.isBefore(fin)) {
            courant = courant.plusDays(1);
            if (estOuvre(courant)) {
                compte++;
            }
        }
        return compte;
    }

    /**
     * Date obtenue en ajoutant {@code nbJours} jours <strong>ouvrés</strong>. Un ajout de 0 rend la date
     * de départ telle quelle, <strong>même si elle tombe un week-end</strong> : on ne déplace pas une
     * échéance que personne n'a demandé de déplacer.
     */
    public static LocalDate ajouter(LocalDate depart, long nbJours) {
        if (depart == null) {
            return null;
        }
        LocalDate courant = depart;
        long restants = Math.max(0L, nbJours);
        while (restants > 0) {
            courant = courant.plusDays(1);
            if (estOuvre(courant)) {
                restants--;
            }
        }
        return courant;
    }
}
