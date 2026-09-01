package cnm.prs.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ⚠️ <strong>Chronométrage des délais</strong> (règle du pilote, 2026-09-01) — arithmétique des jours
 * <strong>ouvrés</strong>, source unique pour tout le chronométrage.
 *
 * <p><strong>Samedi et dimanche exclus ; jours fériés hors périmètre v1</strong> (arbitrage ③). Les
 * horodatages restent enregistrés <strong>à la seconde</strong> : seule la restitution convertit en
 * jours ouvrés. Mélanger les deux — stocker des jours ouvrés — rendrait impossible tout recalcul le jour
 * où les fériés entreront dans le périmètre.</p>
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
     * Nombre de jours ouvrés <strong>écoulés</strong> entre deux instants, en comptant les jours entiers
     * révolus : de lundi 9h à mardi 9h = 1. Rend 0 si {@code fin} précède {@code debut} — un intervalle
     * négatif est une donnée incohérente, pas un crédit de temps.
     */
    public static long ecoules(LocalDateTime debut, LocalDateTime fin) {
        if (debut == null || fin == null || fin.isBefore(debut)) {
            return 0L;
        }
        return entre(debut.toLocalDate(), fin.toLocalDate());
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
