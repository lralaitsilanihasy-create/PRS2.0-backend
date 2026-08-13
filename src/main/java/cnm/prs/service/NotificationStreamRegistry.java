package cnm.prs.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * ⚠️ Spec notifications temps réel (2026-08-02) — registre des connexions SSE par destinataire
 * ({@code DESTINATAIRE_REF} : matricule contrôleur ou id PRMP). Chaque onglet ouvre son propre flux
 * ({@code GET /api/notifications/stream}) ; à l'émission d'une notification (ou à un marquage lu /
 * non-lu, pour la synchronisation entre onglets), un événement {@code maj} est poussé à TOUS les flux
 * du destinataire — le front recharge alors le compteur (calculé côté serveur). Repli : polling front.
 */
@Component
public class NotificationStreamRegistry {

    /** Durée de vie d'un flux (le client se reconnecte automatiquement à l'expiration). */
    private static final long TIMEOUT_MS = 30L * 60 * 1000;

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Ouvre un flux pour ce destinataire ; retiré du registre à la fermeture/expiration/erreur. */
    public SseEmitter subscribe(String ref) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(ref, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> retirer(ref, emitter));
        emitter.onTimeout(() -> retirer(ref, emitter));
        emitter.onError((e) -> retirer(ref, emitter));
        try {
            emitter.send(SseEmitter.event().name("connecte").data("ok"));
        } catch (IOException e) {
            retirer(ref, emitter);
        }
        return emitter;
    }

    /** Pousse l'événement {@code maj} à tous les flux du destinataire (les flux morts sont retirés). */
    public void push(String ref) {
        List<SseEmitter> list = ref == null ? null : emitters.get(ref);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("maj").data("1"));
            } catch (Exception e) {
                retirer(ref, emitter);
            }
        }
    }

    private void retirer(String ref, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(ref);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(ref, list);
            }
        }
    }
}
