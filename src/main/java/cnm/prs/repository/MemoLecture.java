package cnm.prs.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * ⚠️ 2026-10-30 (demande front « performance », §B1) — la <strong>mémoire de lecture d'une liste</strong>. Une liste de procédures
 * appelle, pour chaque procédure, des services qui relisent chacun les mêmes données (responsable, membres de la CAO, plan,
 * fiche…) : jusqu'à une centaine de requêtes par procédure. Le service de la liste ouvre une mémoire le temps de son appel
 * ({@code try (MemoLecture m = MemoLecture.ouvrir()) { … }}), y <strong>précharge</strong> en une requête par nature les données de
 * toutes ses procédures ({@link #poser}) ; les lectures qui passent par {@link #lire} y puisent au lieu d'interroger la base.
 * <p>
 * Hors d'une mémoire ouverte (tout autre appel, écriture comprise), {@link #lire} lit la base comme avant. La mémoire est propre au
 * fil d'exécution et refermée à la fin de la liste : elle ne s'ouvre que sur des lectures (aucune écriture ne la traverse), si bien
 * qu'elle ne peut rien servir de périmé. Une mémoire ouverte dans une autre reste celle de l'appelant le plus extérieur.
 */
public final class MemoLecture implements AutoCloseable {

    private static final ThreadLocal<Map<String, Object>> COURANTE = new ThreadLocal<>();

    private final boolean proprietaire;

    private MemoLecture(boolean proprietaire) {
        this.proprietaire = proprietaire;
    }

    /** Ouvre une mémoire pour le fil courant (ou rejoint celle déjà ouverte). */
    public static MemoLecture ouvrir() {
        if (COURANTE.get() != null) {
            return new MemoLecture(false);
        }
        COURANTE.set(new HashMap<>());
        return new MemoLecture(true);
    }

    /** Une mémoire est-elle ouverte sur ce fil ? */
    public static boolean ouverte() {
        return COURANTE.get() != null;
    }

    /** La valeur mémorisée sous {@code cle}, lue (puis mémorisée) au premier appel ; sans mémoire ouverte, la lecture seule. */
    @SuppressWarnings("unchecked")
    public static <T> T lire(String cle, Supplier<T> lecture) {
        Map<String, Object> m = COURANTE.get();
        if (m == null) {
            return lecture.get();
        }
        if (m.containsKey(cle)) {
            return (T) m.get(cle);
        }
        T v = lecture.get();
        m.put(cle, v);
        return v;
    }

    /** Précharge une valeur (sans effet sans mémoire ouverte). */
    public static void poser(String cle, Object valeur) {
        Map<String, Object> m = COURANTE.get();
        if (m != null) {
            m.put(cle, valeur);
        }
    }

    @Override
    public void close() {
        if (proprietaire) {
            COURANTE.remove();
        }
    }
}
