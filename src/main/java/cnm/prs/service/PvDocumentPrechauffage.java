package cnm.prs.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Préchauffage du convertisseur Word au démarrage (spec du 2026-08-19) : sans processus Word
 * résident, la première conversion .docx → PDF paie en plus le lancement de Word. On démarre
 * donc le pont documents4j dès que l'application est prête, en tâche de fond — l'échec
 * (machine sans Word) est journalisé et sans conséquence.
 *
 * <p>Désactivable par {@code app.pv.document.prechauffage=false} (posé dans les properties de
 * test : pas de Word à lancer pendant la suite).</p>
 */
@Component
@ConditionalOnProperty(name = "app.pv.document.prechauffage", havingValue = "true", matchIfMissing = true)
public class PvDocumentPrechauffage {

    private static final Logger log = LoggerFactory.getLogger(PvDocumentPrechauffage.class);

    private final PvDocumentGenerator generator;

    public PvDocumentPrechauffage(PvDocumentGenerator generator) {
        this.generator = generator;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void prechauffer() {
        try {
            generator.prechauffer();
            log.info("Convertisseur Word préchauffé : la première génération de PV ne paiera pas le lancement de Word.");
        } catch (RuntimeException e) {
            log.warn("Préchauffage du convertisseur Word impossible ({}) — la première génération le démarrera.",
                    e.getMessage());
        }
    }
}
