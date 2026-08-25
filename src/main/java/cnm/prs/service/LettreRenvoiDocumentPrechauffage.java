package cnm.prs.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Préchauffage du convertisseur Word des lettres de renvoi au démarrage — pendant de
 * {@link PvDocumentPrechauffage} (le générateur de lettres a son propre {@code IConverter}, celui
 * du PV ne le couvre donc pas). Sans processus Word résident, la première conversion .docx → PDF
 * paie en plus le lancement de Word ; on démarre le pont documents4j dès que l'application est
 * prête, en tâche de fond — l'échec (machine sans Word) est journalisé et sans conséquence.
 *
 * <p>Désactivable par {@code app.lettre-renvoi.document.prechauffage=false} (posé dans les
 * properties de test : pas de Word à lancer pendant la suite).</p>
 */
@Component
@ConditionalOnProperty(name = "app.lettre-renvoi.document.prechauffage", havingValue = "true",
        matchIfMissing = true)
public class LettreRenvoiDocumentPrechauffage {

    private static final Logger log = LoggerFactory.getLogger(LettreRenvoiDocumentPrechauffage.class);

    private final LettreRenvoiDocumentGenerator generator;

    public LettreRenvoiDocumentPrechauffage(LettreRenvoiDocumentGenerator generator) {
        this.generator = generator;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void prechauffer() {
        try {
            generator.prechauffer();
            log.info("Convertisseur Word des lettres de renvoi préchauffé : la première génération ne "
                    + "paiera pas le lancement de Word.");
        } catch (RuntimeException e) {
            log.warn("Préchauffage du convertisseur Word des lettres impossible ({}) — la première "
                    + "génération le démarrera.", e.getMessage());
        }
    }
}
