package cnm.prs.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * ⚠️ <strong>Chronométrage en HEURES ouvrées</strong> (règle du pilote, 2026-09-02) — source unique de
 * l'arithmétique horaire, en remplacement de l'échelle en jours livrée la veille.
 *
 * <p><strong>8 heures ouvrées = 1 jour ouvré</strong> (arbitrage ②), sur une <strong>fenêtre de
 * service 08:00–16:00, du lundi au vendredi</strong>. Samedi et dimanche sont exclus ; les jours fériés
 * restent hors périmètre v1.</p>
 *
 * <h2>Pourquoi une fenêtre de service et non un plafond journalier</h2>
 *
 * <p>La règle exige que l'<strong>écoulé soit dans la même échelle que la prévision</strong> : compter
 * des heures d'horloge (24 h/jour) contre une prévision en heures de service (8 h/jour) mettrait en
 * dépassement une tâche prise en charge la veille. Deux algorithmes corrigent cela ; ils ne se valent
 * pas.</p>
 *
 * <p>Le <em>plafond journalier</em> — au plus 8 h comptées par jour ouvré touché — compte une journée
 * entière dès qu'un jour est effleuré, fût-ce d'une minute. Une tâche prise lundi 09:00 et mesurée mardi
 * 09:00 rendrait <strong>16 h</strong> : deux journées pour vingt-quatre heures d'horloge dont une seule
 * de travail. La prévision de 8 h serait déjà dépassée de 8 h, soit exactement le défaut que la règle
 * met en garde, à une taille près.</p>
 *
 * <p>La <strong>fenêtre de service</strong> rend, pour le même cas, <strong>8 h</strong> — 7 h le lundi
 * (09:00 → 16:00) et 1 h le mardi (08:00 → 09:00) : la prévision est exactement consommée, ni plus ni
 * moins. C'est elle qui est retenue.</p>
 *
 * <p><strong>Propriété qui en découle</strong> : {@code heures ÷ 8 = jours ouvrés}, exactement. La
 * nouvelle échelle est un <em>raffinement</em> de l'ancienne, jamais un changement de sens — un dossier
 * entièrement au délai standard annonce la même date qu'avant la bascule.</p>
 *
 * <p>Une tâche prise en charge <strong>hors fenêtre</strong> (22:00, un dimanche) n'accumule rien
 * jusqu'à l'ouverture suivante : on ne compte pas comme temps de traitement une heure où personne ne
 * travaille. L'horodatage brut, lui, reste enregistré <strong>à la seconde</strong>.</p>
 */
public final class HeuresOuvrees {

    /** Heures ouvrées dans une journée de service — le taux de conversion de l'arbitrage ②. */
    public static final int HEURES_PAR_JOUR = 8;

    /** Ouverture du service. ⚠️ Hypothèse validée par le pilote le 2026-09-02 ; visible dans les durées restituées. */
    private static final LocalTime OUVERTURE = LocalTime.of(8, 0);

    /** Fermeture du service — {@code OUVERTURE + HEURES_PAR_JOUR}, invariant que le test verrouille. */
    private static final LocalTime FERMETURE = LocalTime.of(16, 0);

    private HeuresOuvrees() {
    }

    /**
     * Heures ouvrées écoulées entre deux instants : le <strong>recouvrement</strong> de l'intervalle
     * avec les fenêtres de service des jours ouvrés qu'il traverse.
     *
     * <p>Rend 0 si {@code fin} précède {@code debut} — un intervalle négatif est une donnée incohérente,
     * pas un crédit de temps. Arrondi à l'heure <strong>inférieure</strong> : on ne compte que les heures
     * pleines effectivement écoulées.</p>
     */
    public static long ecoulees(LocalDateTime debut, LocalDateTime fin) {
        if (debut == null || fin == null || !fin.isAfter(debut)) {
            return 0L;
        }
        long minutes = 0L;
        LocalDate jour = debut.toLocalDate();
        LocalDate dernier = fin.toLocalDate();
        while (!jour.isAfter(dernier)) {
            if (JoursOuvres.estOuvre(jour)) {
                LocalDateTime ouverture = LocalDateTime.of(jour, OUVERTURE);
                LocalDateTime fermeture = LocalDateTime.of(jour, FERMETURE);
                LocalDateTime debutUtile = debut.isAfter(ouverture) ? debut : ouverture;
                LocalDateTime finUtile = fin.isBefore(fermeture) ? fin : fermeture;
                if (finUtile.isAfter(debutUtile)) {
                    minutes += Duration.between(debutUtile, finUtile).toMinutes();
                }
            }
            jour = jour.plusDays(1);
        }
        return minutes / 60L;
    }

    /**
     * Conversion d'un total d'heures ouvrées en jours ouvrés, <strong>arrondi au supérieur</strong> :
     * une journée entamée compte pleine. C'est ce qui fait glisser la date prévisionnelle au lieu de la
     * faire mentir — 9 h de travail restant tiennent sur 2 jours, pas sur 1.
     */
    public static long enJoursArrondiSuperieur(long heures) {
        if (heures <= 0L) {
            return 0L;
        }
        return (heures + HEURES_PAR_JOUR - 1) / HEURES_PAR_JOUR;
    }
}
