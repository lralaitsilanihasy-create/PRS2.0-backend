package cnm.prs.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * ⚠️ 2026-10-04 (soumission en ligne, lot 4, §B2 ; ADR-0013 §2) — les <strong>parts claires apportées en séance</strong>, gardées
 * <strong>en mémoire du serveur seulement</strong> : jamais en base, jamais au journal, effacées (octets mis à zéro) au déchiffrement,
 * au constat d'illisibilité, ou si le serveur redémarre (les membres les rapportent alors). Par procédure, puis par détenteur
 * ({@code K…} ou {@code SECOURS}), puis par offre.
 */
@Component
public class PartsEnSeance {

    private final Map<Long, Map<String, Map<String, byte[]>>> parts = new ConcurrentHashMap<>();

    /** Pose (ou remplace) toutes les parts d'un détenteur. */
    public void poser(Long idDmc, String detenteur, Map<String, byte[]> partsParOffre) {
        Map<String, Map<String, byte[]>> seance = parts.computeIfAbsent(idDmc, k -> new ConcurrentHashMap<>());
        Map<String, byte[]> avant = seance.put(detenteur, new LinkedHashMap<>(partsParOffre));
        effacer(avant);
    }

    /** Les détenteurs qui ont apporté leurs parts, et leurs parts (copie de lecture). */
    public Map<String, Map<String, byte[]>> de(Long idDmc) {
        Map<String, Map<String, byte[]>> s = parts.get(idDmc);
        return s == null ? Map.of() : new LinkedHashMap<>(s);
    }

    /** Oublie toutes les parts d'une séance. */
    public void oublier(Long idDmc) {
        Map<String, Map<String, byte[]>> s = parts.remove(idDmc);
        if (s != null) {
            s.values().forEach(PartsEnSeance::effacer);
        }
    }

    private static void effacer(Map<String, byte[]> m) {
        if (m != null) {
            m.values().forEach(b -> Arrays.fill(b, (byte) 0));
        }
    }
}
