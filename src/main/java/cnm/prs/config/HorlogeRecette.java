package cnm.prs.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ⚠️ 2026-10-24 (demande front « horloge-recette », R1 ; ADR-0016) — l'horloge du serveur, décalable <strong>en recette
 * seulement</strong>. Toute la règle du temps la lit : le bean {@link Clock} de {@link ClockConfig} quand la recette est active,
 * et {@link #HORLOGE} pour le code qui n'a pas d'horloge injectée ({@code LocalDateTime.now(HorlogeRecette.HORLOGE)}).
 *
 * <p>Hors recette ({@code app.recette.horloge.active=false}, le défaut), le décalage reste nul et ne peut pas changer :
 * {@link #HORLOGE} donne l'heure système. Seuls quelques appels gardent l'heure réelle par construction (jetons JWT, sessions
 * de connexion, horodatage des réponses d'erreur), listés par {@code HorlogeUniqueTest}.</p>
 *
 * <p>Le journal garde la vérité : sous horloge décalée, chaque entrée de journal porte {@link #mention()}, l'heure réelle
 * ({@link MentionHorlogeRecette}).</p>
 */
public final class HorlogeRecette {

    private static final AtomicReference<Duration> DECALAGE = new AtomicReference<>(Duration.ZERO);
    private static volatile boolean active;
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** L'horloge du serveur : l'heure système plus le décalage de recette (nul hors recette). */
    public static final Clock HORLOGE = new Decalee(ZoneId.systemDefault());

    private HorlogeRecette() {
    }

    /** Appelé par {@link ClockConfig} quand {@code app.recette.horloge.active=true}. */
    static void activer(Duration decalage) {
        active = true;
        DECALAGE.set(decalage == null ? Duration.ZERO : decalage);
    }

    public static boolean active() {
        return active;
    }

    public static Duration decalage() {
        return DECALAGE.get();
    }

    /** Le nouveau décalage ; refusé hors recette. */
    public static void regler(Duration decalage) {
        if (!active) {
            throw new IllegalStateException("Horloge de recette inactive (app.recette.horloge.active=false)");
        }
        DECALAGE.set(decalage);
    }

    public static boolean decalee() {
        return !DECALAGE.get().isZero();
    }

    /** L'heure réelle, sans décalage. */
    public static LocalDateTime heureReelle() {
        return LocalDateTime.now(Clock.systemDefaultZone());
    }

    /** « [recette — heure réelle : 24/10/2026 13:46] » sous horloge décalée, sinon {@code null}. */
    public static String mention() {
        return decalee() ? "[recette — heure réelle : " + heureReelle().format(FORMAT) + "]" : null;
    }

    /**
     * Le texte suivi de la mention (sous horloge décalée), tenu dans {@code max} caractères en raccourcissant le texte, jamais la
     * mention ({@code max <= 0} : sans limite).
     */
    public static String mentionner(String texte, int max) {
        String m = mention();
        if (m == null) {
            return texte;
        }
        if (texte == null || texte.isBlank()) {
            return m;
        }
        String t = texte;
        if (max > 0 && t.length() + 1 + m.length() > max) {
            t = t.substring(0, Math.max(0, max - 2 - m.length())) + "…";
        }
        return t + " " + m;
    }

    private static final class Decalee extends Clock {

        private final ZoneId zone;

        Decalee(ZoneId zone) {
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId z) {
            return z.equals(zone) ? this : new Decalee(z);
        }

        @Override
        public Instant instant() {
            return Instant.now().plus(DECALAGE.get());
        }

        @Override
        public String toString() {
            return "HorlogeRecette[" + zone + ", " + DECALAGE.get() + "]";
        }
    }
}
