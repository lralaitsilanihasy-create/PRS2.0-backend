package cnm.prs.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;

/**
 * ⚠️ Assistant IA — la <strong>file de génération</strong> et le flux SSE, partagés par tous les gestes
 * qui font travailler le modèle (2026-09-20, extraite d'{@link AssistantIaService} au lot 2, étape 3).
 *
 * <p><strong>Un seul pool pour toute l'application, et c'est le point.</strong> Le serveur d'inférence
 * ne calcule qu'une réponse à la fois : deux files indépendantes — une par fonctionnalité — ne
 * doubleraient pas le débit, elles se disputeraient le même GPU et allongeraient les deux attentes. Deux
 * générations simultanées au plus, huit en attente, et au-delà on le <strong>dit</strong> plutôt que de
 * faire patienter sans fin.</p>
 *
 * <p>⚠️ La tâche s'exécute <strong>hors du fil de la requête</strong> : il n'y a donc plus d'utilisateur
 * authentifié dedans. Toute lecture de données doit avoir eu lieu <strong>avant</strong> — c'est la règle
 * d'étanchéité du plan (§2), et la raison d'être de cette séparation.</p>
 */
@Component
public class FluxGenerationIa {

    private static final Logger log = LoggerFactory.getLogger(FluxGenerationIa.class);

    private static final int GENERATIONS_SIMULTANEES = 2;
    private static final int FILE_D_ATTENTE = 8;

    /** Le navigateur ne lit plus le flux (panneau fermé, page quittée) : on cesse de calculer. */
    public static final class ClientPartiException extends RuntimeException {
        ClientPartiException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Ce qu'une génération reçoit pour parler au navigateur. Elle n'a jamais l'émetteur lui-même : elle
     * ne peut donc ni le laisser ouvert par oubli, ni écrire après l'avoir clos.
     */
    public interface Canal {

        /** Émet un événement SSE. Lève {@link ClientPartiException} si le navigateur est parti. */
        void envoyer(String evenement, Object donnees);

        /** Le navigateur est-il parti, ou le flux a-t-il expiré ? */
        boolean annule();

        /** Clôt le flux sur une erreur lisible par l'utilisateur. */
        void erreur(String message);

        /** Clôt le flux normalement — après le dernier événement utile. */
        void terminer();
    }

    private final ThreadPoolExecutor pool;

    public FluxGenerationIa() {
        AtomicInteger numero = new AtomicInteger();
        this.pool = new ThreadPoolExecutor(GENERATIONS_SIMULTANEES, GENERATIONS_SIMULTANEES, 60,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(FILE_D_ATTENTE), tache -> {
                    Thread t = new Thread(tache, "assistant-ia-" + numero.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
    }

    @PreDestroy
    void arreter() {
        pool.shutdownNow();
    }

    /**
     * Ouvre un flux et confie la génération au pool.
     *
     * @param timeoutMs durée de vie du flux — un peu au-delà du délai du modèle, pour pouvoir dire
     *                  pourquoi il s'arrête
     * @param tache     la génération ; elle reçoit le canal et le clôt elle-même
     * @param siSature  appelé, dans le fil de la requête, quand la file est pleine : le geste n'a pas
     *                  lieu, et l'appelant le journalise avec son propre vocabulaire
     */
    public SseEmitter lancer(long timeoutMs, Consumer<Canal> tache, Consumer<String> siSature) {
        SseEmitter emetteur = new SseEmitter(timeoutMs);
        AtomicBoolean annule = new AtomicBoolean();
        emetteur.onTimeout(() -> annule.set(true));
        emetteur.onError(e -> annule.set(true));
        Canal canal = canal(emetteur, annule);
        try {
            pool.execute(() -> tache.accept(canal));
        } catch (RejectedExecutionException e) {
            String message = "L'assistant est très sollicité en ce moment. Réessayez dans un instant.";
            canal.erreur(message);
            siSature.accept(message);
        }
        return emetteur;
    }

    private Canal canal(SseEmitter emetteur, AtomicBoolean annule) {
        return new Canal() {

            @Override
            public void envoyer(String evenement, Object donnees) {
                try {
                    emetteur.send(SseEmitter.event().name(evenement).data(donnees,
                            MediaType.APPLICATION_JSON));
                } catch (IOException | IllegalStateException e) {
                    annule.set(true);
                    throw new ClientPartiException(e);
                }
            }

            @Override
            public boolean annule() {
                return annule.get();
            }

            @Override
            public void erreur(String message) {
                try {
                    emetteur.send(SseEmitter.event().name("erreur").data(Map.of("message", message),
                            MediaType.APPLICATION_JSON));
                    emetteur.complete();
                } catch (IOException | IllegalStateException e) {
                    log.debug("Assistant IA : message d'erreur non transmis, le navigateur est parti ({})",
                            e.getMessage());
                }
            }

            @Override
            public void terminer() {
                try {
                    emetteur.complete();
                } catch (RuntimeException e) {
                    log.debug("Assistant IA : flux déjà clos ({})", e.getMessage());
                }
            }
        };
    }
}
