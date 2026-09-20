package cnm.prs.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import cnm.prs.config.AssistantIaProperties;
import cnm.prs.dto.EtatAssistantIaDto;
import cnm.prs.dto.EtatAssistantIaDto.DocumentIaDto;
import cnm.prs.dto.SourceIaDto;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.service.ClientModeleIa.Message;
import cnm.prs.service.ClientModeleIa.ModeleIndisponibleException;
import cnm.prs.service.FluxGenerationIa.Canal;
import cnm.prs.service.FluxGenerationIa.ClientPartiException;

/**
 * Assistant IA local, lot 1 : il répond aux questions sur les règles du contrôle des marchés et de
 * PRS, à partir du corpus documentaire seul ({@code docs/plan-assistant-ia.md} ; ADR-0007).
 *
 * <h2>Ce que l'assistant ne fait pas</h2>
 * <ul>
 *   <li>Il ne lit <strong>aucune donnée métier</strong> : ni dossier, ni PPM, ni personne. Sa seule
 *       source est le corpus ({@link CorpusIaService}), identique pour tous les profils.</li>
 *   <li>Il n'écrit rien et ne décide rien : la consigne lui interdit tout avis sur un dossier réel.</li>
 *   <li>Il ne répond pas de mémoire : sans passage pertinent, la réponse est fixe et le modèle n'est
 *       même pas appelé. Interrogé sans le manuel, le modèle de développement cite le droit français
 *       (« code de la commande publique ») — d'où cette règle.</li>
 * </ul>
 *
 * <h2>Déroulé d'une question</h2>
 * <p>Recherche des passages dans le fil de la requête, puis génération dans la file partagée de
 * l'assistant ({@link FluxGenerationIa}) — un seul pool pour tous les gestes qui font travailler le
 * modèle, puisque le serveur d'inférence n'en calcule qu'un à la fois. Le flux SSE émet, dans l'ordre :
 * {@code sources} (les extraits numérotés), {@code texte} (chaque morceau de réponse), puis
 * {@code fin} — ou {@code erreur} avec un message pour l'utilisateur.</p>
 *
 * <p>Chaque échange est tracé au journal d'audit (table {@value #TABLE_AUDIT}) : qui, quand, quel
 * modèle, la question, la réponse et ses sources.</p>
 */
@Service
public class AssistantIaService {

    private static final Logger log = LoggerFactory.getLogger(AssistantIaService.class);

    static final String TABLE_AUDIT = "assistant_ia";
    /**
     * Au-delà, un extrait est coupé : cinq extraits et la réponse doivent tenir dans le contexte de
     * 8 192 jetons. Une page du manuel fait au plus 3 000 caractères (médiane 2 000), plus 600 de la
     * page suivante : 3 600 couvrent tout le manuel, soit ~5 000 jetons pour cinq extraits au pire.
     */
    private static final int TAILLE_MAX_EXTRAIT = 3600;
    /** Le flux reste ouvert un peu au-delà du délai du modèle, pour pouvoir dire pourquoi il s'arrête. */
    private static final long MARGE_FLUX_MS = 30_000;

    /** Qui pose la question — relevé dans le fil de la requête, avant de passer au pool de calcul. */
    public record Demandeur(String ref, ProfilUtilisateur profil, String ip) {
    }

    private final AssistantIaProperties props;
    private final CorpusIaService corpus;
    private final ClientModeleIa client;
    private final AuditLogService audit;
    private final FluxGenerationIa flux;

    public AssistantIaService(AssistantIaProperties props, CorpusIaService corpus, ClientModeleIa client,
            AuditLogService audit, FluxGenerationIa flux) {
        this.props = props;
        this.corpus = corpus;
        this.client = client;
        this.audit = audit;
        this.flux = flux;
    }

    /** État affiché par le front ; ne sonde le serveur d'inférence que si l'assistant est actif. */
    public EtatAssistantIaDto etat() {
        if (!props.actif()) {
            return EtatAssistantIaDto.inactif();
        }
        List<DocumentIaDto> documents = corpus.documents().stream()
                .map(d -> new DocumentIaDto(d.libelle(), d.passages()))
                .toList();
        return new EtatAssistantIaDto(true, client.disponible(), props.modele(), documents);
    }

    /**
     * Pose une question. La réponse arrive en flux SSE sur l'émetteur rendu.
     *
     * @throws ResourceNotFoundException si l'assistant n'est pas activé
     */
    public SseEmitter poser(String question, Demandeur demandeur) {
        if (!props.actif()) {
            throw new ResourceNotFoundException("L'assistant IA n'est pas activé.");
        }
        String q = question.strip();
        // ⚠️ La recherche a lieu ICI, dans le fil de la requête : au-delà, il n'y a plus d'utilisateur.
        List<SourceIaDto> sources = numeroter(corpus.rechercher(q, props.extraits()));
        return flux.lancer(props.timeoutSecondes() * 1000L + MARGE_FLUX_MS,
                canal -> repondre(q, sources, demandeur, canal),
                message -> journaliser(demandeur, "QUESTION_REFUSEE", q, "", sources, 0, message));
    }

    /**
     * Calcule la réponse, la <strong>journalise</strong>, puis clôt le flux ({@code fin} ou
     * {@code erreur}) : l'échange est tracé avant que l'utilisateur ne le voie terminé.
     */
    private void repondre(String question, List<SourceIaDto> sources, Demandeur demandeur, Canal canal) {
        long debut = System.nanoTime();
        StringBuilder reponse = new StringBuilder();
        String action = "QUESTION";
        String erreur = null;
        try {
            canal.envoyer("sources", sources);
            if (sources.isEmpty()) {
                String texte = aucunPassage();
                reponse.append(texte);
                canal.envoyer("texte", Map.of("t", texte));
            } else {
                client.generer(messages(question, sources, demandeur.profil()), morceau -> {
                    reponse.append(morceau);
                    canal.envoyer("texte", Map.of("t", morceau));
                }, canal::annule);
            }
            if (canal.annule()) {
                action = "QUESTION_INTERROMPUE";
            }
        } catch (ClientPartiException e) {
            action = "QUESTION_INTERROMPUE";
        } catch (ModeleIndisponibleException e) {
            action = "QUESTION_ECHEC";
            erreur = e.getMessage();
        } catch (RuntimeException e) {
            log.error("Assistant IA : échec inattendu pendant la génération", e);
            action = "QUESTION_ECHEC";
            erreur = "Erreur inattendue de l'assistant.";
        }
        long dureeMs = millisecondes(debut);
        journaliser(demandeur, action, question, reponse.toString(), sources, dureeMs, erreur);

        if (erreur != null) {
            canal.erreur(erreur);
        } else if (!"QUESTION_INTERROMPUE".equals(action)) {
            try {
                canal.envoyer("fin", Map.of("modele", props.modele(), "dureeMs", dureeMs));
                canal.terminer();
            } catch (ClientPartiException e) {
                log.debug("Assistant IA : fin de réponse non transmise, le navigateur est parti");
            }
        } else {
            // Navigateur parti : on libère la requête asynchrone sans attendre son expiration.
            canal.terminer();
        }
    }

    // ---------------------------------------------------------------- consigne et extraits

    /** Consigne système, puis la question accompagnée de ses extraits numérotés. */
    static List<Message> messages(String question, List<SourceIaDto> sources, ProfilUtilisateur profil) {
        String consigne = """
                Tu es l'Assistant IA de PRS (Procurement Review System), l'application de la Commission \
                nationale des marchés (CNM) de Madagascar pour le contrôle a priori des marchés publics.
                Ton rôle : aider l'utilisateur à comprendre les règles du contrôle des marchés publics et le \
                fonctionnement de PRS. Tu es une aide, jamais un décideur.

                Règles impératives :
                1. Réponds UNIQUEMENT à partir des extraits numérotés fournis avec la question. N'utilise pas \
                tes connaissances générales. Le droit applicable est le droit malgache (Code des marchés \
                publics, loi n° 2016-055 du 25 janvier 2017) : ne cite jamais le droit d'un autre pays.
                2. Après chaque information, indique l'extrait qui l'appuie par son numéro entre crochets, par \
                exemple [1] ou [2][3]. N'invente jamais de numéro.
                3. Si les extraits ne permettent pas de répondre, dis-le simplement et suggère de reformuler \
                la question. Ne devine pas.
                4. Ne donne jamais d'avis sur un dossier réel (favorable, défavorable, conforme, non conforme) : \
                cette décision appartient à la Commission. Tu expliques la règle, tu ne l'appliques pas à la \
                place du contrôleur.
                5. Réponds en français et vouvoie toujours l'utilisateur, même s'il te tutoie ou écrit à la \
                première personne. Sois clair et bref : quelques phrases, ou une courte liste à puces si c'est \
                plus lisible. Pas de titre.
                6. N'emploie pas de vocabulaire informatique (noms de tables, de champs, d'API, identifiants \
                techniques), même s'il figure dans les extraits : dis la même chose en langage courant.

                L'utilisateur est : %s.""".formatted(libelleProfil(profil));
        StringBuilder demande = new StringBuilder("Extraits :\n");
        for (SourceIaDto s : sources) {
            demande.append("\n[").append(s.numero()).append("] ").append(s.document()).append(" — ")
                    .append(s.reference()).append('\n').append(s.extrait()).append('\n');
        }
        demande.append("\nQuestion : ").append(question);
        return List.of(new Message("system", consigne), new Message("user", demande.toString()));
    }

    static List<SourceIaDto> numeroter(List<CorpusIaService.Passage> passages) {
        List<SourceIaDto> sources = new ArrayList<>(passages.size());
        int numero = 1;
        for (CorpusIaService.Passage p : passages) {
            String extrait = p.texte().length() > TAILLE_MAX_EXTRAIT
                    ? p.texte().substring(0, TAILLE_MAX_EXTRAIT) + " […]" : p.texte();
            sources.add(new SourceIaDto(numero++, p.document(), p.reference(), extrait));
        }
        return List.copyOf(sources);
    }

    /** Réponse fixe quand aucun passage ne correspond : pas d'appel au modèle, donc rien d'inventé. */
    private String aucunPassage() {
        String documents = corpus.documents().stream()
                .map(CorpusIaService.DocumentCharge::libelle)
                .collect(Collectors.joining(" ni dans "));
        if (documents.isEmpty()) {
            return "Aucun document de référence n'est chargé : l'assistant ne peut pas répondre pour "
                    + "l'instant. Signalez-le à l'administrateur de PRS.";
        }
        return "Je n'ai trouvé, dans " + documents + ", aucun passage qui traite de cette question. "
                + "Essayez de la reformuler avec d'autres mots — par exemple le nom de la procédure, du "
                + "document ou de l'étape concernée.";
    }

    static String libelleProfil(ProfilUtilisateur profil) {
        if (profil == null) {
            return "un utilisateur de PRS";
        }
        return switch (profil) {
            case PRMP -> "une Personne responsable des marchés publics (PRMP), qui prépare les dossiers";
            case UGPM -> "un membre d'une Unité de gestion de la passation des marchés (UGPM), qui prépare les dossiers";
            case PRESIDENT -> "le Président de la Commission nationale des marchés";
            case CHEF_COMMISSION -> "un Chef de commission";
            case SECRETAIRE -> "un Secrétaire de commission, chargé de la réception des dossiers";
            case MEMBRE -> "un Membre de commission, chargé de l'examen des dossiers";
            case VERIFICATEUR -> "un Contrôleur vérificateur";
            case ASSISTANT_CONTROLEUR -> "un Assistant contrôleur";
            case CHARGE_PUBLICATION -> "un Chargé de publication";
            case ADMINISTRATEUR -> "un Administrateur de PRS";
        };
    }

    // ---------------------------------------------------------------- journal

    private void journaliser(Demandeur demandeur, String action, String question, String reponse,
            List<SourceIaDto> sources, long dureeMs, String erreur) {
        StringBuilder trace = new StringBuilder()
                .append("Profil : ").append(demandeur.profil() == null ? "inconnu" : demandeur.profil().name())
                .append("\nQuestion : ").append(question)
                .append("\n\nRéponse :\n").append(reponse.isBlank() ? "(aucune)" : reponse.strip())
                .append("\n\nSources : ")
                .append(sources.isEmpty() ? "aucune" : sources.stream()
                        .map(s -> "[" + s.numero() + "] " + s.document() + " — " + s.reference())
                        .collect(Collectors.joining(" ; ")))
                .append("\nDurée : ").append(dureeMs).append(" ms");
        if (erreur != null) {
            trace.append("\nErreur : ").append(erreur);
        }
        try {
            audit.enregistrerDetail(demandeur.ref(), TABLE_AUDIT, null, action, props.modele(), trace.toString(),
                    demandeur.ip());
        } catch (RuntimeException e) {
            // Le journal ne doit jamais casser la réponse — même règle que l'intercepteur d'audit.
            log.warn("Assistant IA : échec de la journalisation d'un échange ({})", e.getMessage());
        }
    }

    private static long millisecondes(long debutNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - debutNanos);
    }
}
