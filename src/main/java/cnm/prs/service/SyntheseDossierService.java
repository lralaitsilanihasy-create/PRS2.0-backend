package cnm.prs.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import cnm.prs.config.AssistantIaProperties;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.service.AssistantIaService.Demandeur;
import cnm.prs.service.ClientModeleIa.ModeleIndisponibleException;
import cnm.prs.service.FluxGenerationIa.Canal;
import cnm.prs.service.FluxGenerationIa.ClientPartiException;
import cnm.prs.service.OutilsDossierIa.Faits;

/**
 * ⚠️ Assistant IA, lot 2 (2026-09-20, étape 3) — <strong>la synthèse d'un dossier</strong>, de bout en
 * bout ({@code docs/plan-assistant-ia.md} §4, lot 2).
 *
 * <h2>L'ordre des opérations EST la sécurité</h2>
 * <ol>
 *   <li><strong>Dans le fil de la requête</strong> : {@link OutilsDossierIa#lire} appelle la liste
 *       blanche sous l'identité de l'utilisateur. Un refus (403, 404) remonte <strong>avant</strong>
 *       que le moindre flux ne s'ouvre : l'utilisateur reçoit une erreur HTTP franche, pas une synthèse
 *       vide.</li>
 *   <li><strong>Dans la file de génération</strong> : le modèle rédige, à partir des seuls faits déjà
 *       lus. Il n'y a plus d'utilisateur authentifié à ce moment-là — et il n'en faut plus.</li>
 * </ol>
 *
 * <p>Le flux émet {@code faits} <strong>en premier</strong>, avant la moindre seconde de calcul : ce que
 * le serveur a lu s'affiche tout de suite, la prose arrive ensuite. C'est la doctrine du lot rendue
 * visible à l'écran — <em>les faits sont du serveur, la prose est du modèle</em> — et c'est aussi ce qui
 * rend l'attente supportable.</p>
 */
@Service
public class SyntheseDossierService {

    private static final Logger log = LoggerFactory.getLogger(SyntheseDossierService.class);

    /** Le flux reste ouvert un peu au-delà du délai du modèle, pour pouvoir dire pourquoi il s'arrête. */
    private static final long MARGE_FLUX_MS = 30_000;

    private final AssistantIaProperties props;
    private final OutilsDossierIa outils;
    private final DialogueSyntheseDossier dialogue;
    private final ClientModeleIa client;
    private final FluxGenerationIa flux;
    private final AuditLogService audit;

    public SyntheseDossierService(AssistantIaProperties props, OutilsDossierIa outils,
            DialogueSyntheseDossier dialogue, ClientModeleIa client, FluxGenerationIa flux,
            AuditLogService audit) {
        this.props = props;
        this.outils = outils;
        this.dialogue = dialogue;
        this.client = client;
        this.flux = flux;
        this.audit = audit;
    }

    /**
     * Rédige la synthèse d'un dossier. La réponse arrive en flux SSE : {@code faits}, puis {@code texte}
     * au fil de la génération, puis {@code fin} — ou {@code erreur}.
     *
     * @throws ResourceNotFoundException si l'assistant n'est pas activé, ou si le dossier n'existe pas
     */
    public SseEmitter synthetiser(int idDossier, Demandeur demandeur) {
        if (!props.actif()) {
            throw new ResourceNotFoundException("L'assistant IA n'est pas activé.");
        }
        // ⚠️ ICI, et nulle part ailleurs : c'est le seul endroit du geste où l'utilisateur existe.
        Faits faits = outils.lire(idDossier);
        return flux.lancer(props.timeoutSecondes() * 1000L + MARGE_FLUX_MS,
                canal -> rediger(faits, demandeur, canal),
                message -> journaliser(demandeur, "SYNTHESE_REFUSEE", faits, "", List.of(), 0, message));
    }

    private void rediger(Faits faits, Demandeur demandeur, Canal canal) {
        long debut = System.nanoTime();
        StringBuilder synthese = new StringBuilder();
        String action = "SYNTHESE_DOSSIER";
        String erreur = null;
        List<String> anomalies = List.of();
        try {
            canal.envoyer("faits", faits);
            client.generer(dialogue.messages(faits, demandeur.profil()), morceau -> {
                synthese.append(morceau);
                canal.envoyer("texte", Map.of("t", morceau));
            }, canal::annule);
            if (canal.annule()) {
                action = "SYNTHESE_INTERROMPUE";
            } else {
                anomalies = dialogue.anomalies(synthese.toString());
            }
        } catch (ClientPartiException e) {
            action = "SYNTHESE_INTERROMPUE";
        } catch (ModeleIndisponibleException e) {
            action = "SYNTHESE_ECHEC";
            erreur = e.getMessage();
        } catch (RuntimeException e) {
            log.error("Assistant IA : échec inattendu pendant la synthèse du dossier {}", faits.idDossier(), e);
            action = "SYNTHESE_ECHEC";
            erreur = "Erreur inattendue de l'assistant.";
        }
        long dureeMs = millisecondes(debut);
        journaliser(demandeur, action, faits, synthese.toString(), anomalies, dureeMs, erreur);

        if (erreur != null) {
            canal.erreur(erreur);
        } else if (!"SYNTHESE_INTERROMPUE".equals(action)) {
            try {
                canal.envoyer("fin", Map.of("modele", props.modele(), "dureeMs", dureeMs,
                        "mention", DialogueSyntheseDossier.MENTION));
                canal.terminer();
            } catch (ClientPartiException e) {
                log.debug("Assistant IA : fin de synthèse non transmise, le navigateur est parti");
            }
        } else {
            canal.terminer();
        }
    }

    /**
     * Trace l'échange. Elle porte ce qu'aucun autre journal ne dira : <strong>quelles lectures ont
     * abouti et lesquelles ont été refusées</strong> pour ce profil — c'est la preuve, a posteriori,
     * que l'assistant n'a lu que ce que l'utilisateur pouvait lire.
     */
    private void journaliser(Demandeur demandeur, String action, Faits faits, String synthese,
            List<String> anomalies, long dureeMs, String erreur) {
        StringBuilder trace = new StringBuilder()
                .append("Profil : ").append(demandeur.profil() == null ? "inconnu" : demandeur.profil().name())
                .append("\nDossier : ").append(faits.idDossier())
                .append(" (").append(faits.reference()).append(')')
                .append("\nLectures abouties : ").append(String.join(", ", faits.outilsLus()))
                .append("\nLectures écartées : ")
                .append(faits.outilsRefuses().isEmpty() ? "aucune" : String.join(", ", faits.outilsRefuses()))
                .append("\n\nSynthèse :\n").append(synthese.isBlank() ? "(aucune)" : synthese.strip())
                .append("\nDurée : ").append(dureeMs).append(" ms");
        if (!anomalies.isEmpty()) {
            // ⚠️ Ce que la synthèse a d'anormal reste ÉCRIT même si l'écran l'affiche telle quelle : c'est
            // la mesure qui dira s'il faut reprendre la consigne, ou changer de modèle.
            trace.append("\nAnomalies : ").append(String.join(" ; ", anomalies));
        }
        if (erreur != null) {
            trace.append("\nErreur : ").append(erreur);
        }
        try {
            audit.enregistrerDetail(demandeur.ref(), AssistantIaService.TABLE_AUDIT,
                    String.valueOf(faits.idDossier()), action, props.modele(), trace.toString(),
                    demandeur.ip());
        } catch (RuntimeException e) {
            log.warn("Assistant IA : échec de la journalisation d'une synthèse ({})", e.getMessage());
        }
    }

    private static long millisecondes(long debutNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - debutNanos);
    }
}
