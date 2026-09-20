package cnm.prs.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import cnm.prs.enums.IntentionAssistant;
import cnm.prs.service.ClientModeleIa.Message;
import cnm.prs.service.ClientModeleIa.ModeleIndisponibleException;

/**
 * ⚠️ Assistant IA, lot 4 (2026-09-20, étape 1) — <strong>l'aiguillage fermé</strong> : ce qui décide,
 * pour une question libre, quelle lecture faire ({@code docs/plan-assistant-ia.md} §4, lot 4, 4.a).
 *
 * <h2>Trois barrières, dans cet ordre</h2>
 * <ol>
 *   <li><strong>Ce qui se voit ne se demande pas.</strong> Une question qui porte une référence de
 *       dossier part directement sur ce dossier — aucune passe de modèle, aucun doute. C'est la leçon
 *       du lot 3, appliquée à la compréhension : <em>ce qui peut être vérifié ne se demande pas</em>.</li>
 *   <li><strong>Le modèle NOMME, il n'ouvre pas.</strong> On lui demande un mot d'une liste close, et
 *       rien d'autre : ni paramètre, ni justification, ni phrase.</li>
 *   <li><strong>Le repli est la réponse du lot 1.</strong> Une intention inconnue, une réponse bavarde,
 *       un modèle en panne : tout cela donne {@link IntentionAssistant#REGLE}, qui ne lit aucune donnée
 *       et dont la qualité est déjà mesurée. Un aiguillage raté coûte une réponse moins précise, jamais
 *       une donnée de trop.</li>
 * </ol>
 */
@Component
public class AiguillageAssistantIa {

    private static final Logger log = LoggerFactory.getLogger(AiguillageAssistantIa.class);

    /**
     * ⚠️ Budget de la passe de classification : <strong>très court</strong>, parce que la réponse
     * attendue est UN MOT. Un budget large inviterait le modèle à expliquer son choix, et à noyer le mot
     * dans une phrase que {@link IntentionAssistant#lire} rejetterait.
     */
    private static final int JETONS_INTENTION = 12;

    /**
     * Une référence de dossier PRS : {@code 00002/PPM/CNM/2026}, ou les formes voisines des autres
     * types. Quatre segments séparés par des barres obliques, le premier numérique, le dernier une
     * année — assez strict pour ne pas confondre avec une date ou un montant.
     */
    private static final Pattern REFERENCE = Pattern.compile(
            "\\b(\\d{1,6}\\s*/\\s*[A-Za-zÀ-ÿ]{2,10}\\s*/\\s*[A-Za-zÀ-ÿ]{2,10}\\s*/\\s*(?:19|20)\\d{2})\\b");

    /** « dossier n° 100003 », « le dossier 100003 » — l'autre façon de désigner un dossier. */
    private static final Pattern NUMERO_DOSSIER = Pattern.compile(
            "\\bdossiers?\\s+(?:n\\s*[°ºo]\\s*)?(\\d{3,9})\\b", Pattern.CASE_INSENSITIVE);

    /** Ce que l'aiguillage a décidé, et ce qu'il a retenu de la question pour la lecture à faire. */
    public record Aiguillage(IntentionAssistant intention, String parametre, boolean parLeModele) {

        static Aiguillage certain(IntentionAssistant intention, String parametre) {
            return new Aiguillage(intention, parametre, false);
        }

        static Aiguillage duModele(IntentionAssistant intention) {
            return new Aiguillage(intention, null, true);
        }
    }

    private final ClientModeleIa client;

    public AiguillageAssistantIa(ClientModeleIa client) {
        this.client = client;
    }

    /**
     * Décide de l'intention d'une question.
     *
     * @param question la question, telle que l'utilisateur l'a écrite
     * @param ouvertes les intentions que le profil de l'utilisateur peut atteindre —
     *                 {@link IntentionAssistant#REGLE} en fait toujours partie
     */
    public Aiguillage decider(String question, List<IntentionAssistant> ouvertes) {
        Aiguillage evident = sansLeModele(question, ouvertes);
        if (evident != null) {
            return evident;
        }
        if (ouvertes.size() <= 1) {
            // Rien d'autre que le repli n'est ouvert à ce profil : demander au modèle serait du gâchis.
            return Aiguillage.duModele(IntentionAssistant.REGLE);
        }
        try {
            String rendu = client.generer(messages(question, ouvertes), fragment -> { }, () -> false,
                    JETONS_INTENTION);
            IntentionAssistant lue = IntentionAssistant.lire(rendu);
            // ⚠️ Une intention FERMÉE à ce profil retombe sur le repli : le modèle ne peut pas ouvrir une
            // porte que la garde refuserait de toute façon — autant ne pas la pousser.
            return Aiguillage.duModele(ouvertes.contains(lue) ? lue : IntentionAssistant.REGLE);
        } catch (ModeleIndisponibleException e) {
            log.debug("Assistant IA : aiguillage impossible ({}), repli documentaire", e.getMessage());
            return Aiguillage.duModele(IntentionAssistant.REGLE);
        }
    }

    /**
     * L'aiguillage <strong>sans modèle</strong> : ce que la question dit d'elle-même. {@code null} si
     * elle ne dit rien d'assez net.
     */
    Aiguillage sansLeModele(String question, List<IntentionAssistant> ouvertes) {
        if (question == null || question.isBlank()) {
            return Aiguillage.certain(IntentionAssistant.REGLE, null);
        }
        if (!ouvertes.contains(IntentionAssistant.ETAT_DOSSIER)) {
            return null;
        }
        Matcher reference = REFERENCE.matcher(question);
        if (reference.find()) {
            // La référence est recomposée sans ses espaces : « 00002 / PPM / CNM / 2026 » se cherche
            // comme « 00002/PPM/CNM/2026 ».
            return Aiguillage.certain(IntentionAssistant.ETAT_DOSSIER,
                    reference.group(1).replaceAll("\\s+", ""));
        }
        Matcher numero = NUMERO_DOSSIER.matcher(question);
        if (numero.find()) {
            return Aiguillage.certain(IntentionAssistant.ETAT_DOSSIER, numero.group(1));
        }
        return null;
    }

    /**
     * La consigne de classification. Volontairement <strong>sèche</strong> : la liste, l'ordre de rendre
     * un mot, et rien de plus. Tout ce qu'on ajouterait ici invite à répondre par une phrase.
     */
    static List<Message> messages(String question, List<IntentionAssistant> ouvertes) {
        String liste = ouvertes.stream()
                .map(i -> i.name() + " : " + i.description())
                .collect(Collectors.joining("\n"));
        String consigne = """
                Tu classes une question posée à l'assistant de PRS, l'application de la Commission \
                nationale des marchés de Madagascar.

                Réponds par UN SEUL MOT, choisi dans cette liste, sans rien ajouter :
                %s

                Si la question ne correspond à aucune, ou si tu hésites, réponds REGLE.
                N'explique jamais ton choix. Ne réponds pas à la question.""".formatted(liste);
        return List.of(new Message("system", consigne),
                new Message("user", "Question : " + question.strip().toLowerCase(Locale.FRENCH)));
    }
}
