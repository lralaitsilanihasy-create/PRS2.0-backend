package cnm.prs.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Envoi réel des notifications par e-mail (§3.x — diffusion des alertes).
 *
 * <p>Asynchrone (ne bloque pas l'action métier) et tolérant : si l'envoi est désactivé
 * ({@code app.mail.enabled=false}) ou si aucun serveur SMTP n'est configuré, l'appel est
 * sans effet ; une panne SMTP est journalisée et n'interrompt jamais le flux.</p>
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean enabled;
    private final boolean journaliser;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.mail.journaliser:false}") boolean journaliser,
            @Value("${app.mail.from:no-reply@cnm.mg}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.enabled = enabled;
        this.journaliser = journaliser;
        this.from = from;
    }

    @Async
    public void envoyer(String destinataire, String sujet, String corps) {
        if (destinataire == null || destinataire.isBlank()) {
            return;
        }
        // ⚠️ 2026-10-04 (recette, soumission en ligne) — app.mail.journaliser=true : le courriel entier au journal (codes de
        // confirmation et d'activation compris). RECETTE SEULEMENT : jamais en production, où l'envoi réel est la règle.
        if (journaliser) {
            log.info("COURRIEL{} à {} — {}\n{}", enabled ? "" : " (envoi désactivé)", destinataire, sujet, corps);
        }
        if (!enabled) {
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("Envoi e-mail activé (app.mail.enabled=true) mais aucun serveur SMTP configuré "
                    + "(spring.mail.host). E-mail à {} non expédié.", destinataire);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(destinataire);
            message.setSubject(sujet != null ? sujet : "");
            message.setText(corps != null ? corps : "");
            sender.send(message);
            log.debug("E-mail envoyé à {}", destinataire);
        } catch (Exception e) {
            log.warn("Échec d'envoi e-mail à {} : {}", destinataire, e.getMessage());
        }
    }
}
