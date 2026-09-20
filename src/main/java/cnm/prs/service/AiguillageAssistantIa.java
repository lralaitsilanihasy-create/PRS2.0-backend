package cnm.prs.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import cnm.prs.enums.IntentionAssistant;

/**
 * ⚠️ Assistant IA, lot 4 (2026-09-20, étape 1) — <strong>l'aiguillage</strong> : ce qui décide, pour une
 * question libre, quelle lecture faire ({@code docs/plan-assistant-ia.md} §4, lot 4, 4.a).
 *
 * <h2>Il est déterministe, et c'est une décision mesurée</h2>
 * <p>Le plan prévoyait d'abord un <em>aiguillage fermé</em> : le modèle nommait une intention d'une
 * liste close, en un mot. Mesuré contre {@code qwen3.5:9b-q4_K_M}, cela ne fonctionne pas —
 * <strong>4/10</strong>, et pour une raison qui ne se corrige pas en reformulant la consigne : le modèle
 * <strong>réfléchit avant de répondre</strong>, sa réflexion consomme tout le budget de sortie, et le
 * champ de contenu revient <strong>vide</strong>. À 12 jetons comme à 200.</p>
 *
 * <p>Le mode réflexion se coupe bien — mais seulement par l'<strong>API native</strong> d'Ollama
 * ({@code think: false} sur {@code /api/chat} : réponse juste en 0,5 s). Or le client de l'assistant
 * parle l'<strong>API compatible OpenAI</strong>, et c'est délibéré (ADR-0007) : c'est ce qui permet de
 * changer de serveur d'inférence sans recompiler. On ne va pas troquer cette portabilité contre une
 * classification que des mots-clés font aussi bien.</p>
 *
 * <p>D'où la règle, qui est celle du lot 3 appliquée à la compréhension : <strong>ce qui peut être
 * décidé sans le modèle ne lui est pas demandé</strong>. L'aiguillage ne coûte plus rien, ne bloque plus
 * le fil de la requête, et se teste sans serveur d'inférence.</p>
 *
 * <h2>Ce qu'un aiguillage raté coûte</h2>
 * <p>Une question non reconnue retombe sur {@link IntentionAssistant#REGLE} — la réponse documentaire du
 * lot 1, qui ne lit <strong>aucune donnée</strong>. Le repli n'est donc jamais un échec : c'est le
 * comportement d'un lot déjà mesuré et recetté. Une intention manquée coûte une réponse moins précise,
 * jamais une donnée de trop.</p>
 */
@Component
public class AiguillageAssistantIa {

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

    /**
     * Les tournures qui désignent une intention, sur le texte <strong>normalisé</strong> (minuscules,
     * sans accents). L'ordre compte : la première règle qui répond gagne, et les plus spécifiques sont
     * donc en tête.
     *
     * <p>⚠️ Ces motifs sont <strong>volontairement modestes</strong>. Ils ne cherchent pas à couvrir la
     * langue : ils couvrent ce qu'on dit vraiment à un assistant de travail, et laissent le reste au
     * repli documentaire. Les élargir se fait ici, avec un cas de test à chaque fois — c'est ce qui
     * remplace une consigne qu'on ne peut pas mesurer.</p>
     */
    private static final List<Regle> REGLES = List.of(
            new Regle(IntentionAssistant.TROUVER_DOSSIER,
                    "\\b(?:trouve|retrouve|cherche|recherche|liste|montre|affiche)\\w*\\b.{0,40}"
                            + "\\b(?:dossier|plan|ppm|marche)\\w*"),
            new Regle(IntentionAssistant.MES_TACHES,
                    "\\ba\\s+faire\\b|\\bmes\\s+taches?\\b|\\bqu[' ]?est[- ]ce\\s+que\\s+j[' ]?ai\\b"
                            + "|\\bm[' ]?attend|\\bqui\\s+m[' ]?attend|\\bma\\s+file\\b|\\bmes\\s+dossiers?\\b"
                            + "|\\bje\\s+dois\\s+(?:traiter|faire|examiner)"),
            new Regle(IntentionAssistant.MES_CHIFFRES,
                    "\\bcombien\\b|\\bcompteurs?\\b|\\bmes\\s+chiffres?\\b|\\bnombre\\s+de\\s+dossiers?\\b"
                            + "|\\ben\\s+retard\\b"),
            new Regle(IntentionAssistant.TABLEAU_DE_BORD,
                    "\\btableau\\s+de\\s+bord\\b|\\bvue\\s+d[' ]?ensemble\\b|\\bstatistiques?\\b"
                            + "|\\btaux\\s+de\\s+conformite\\b|\\bou\\s+en\\s+est\\s+la\\s+commission\\b"),
            new Regle(IntentionAssistant.ANNUAIRE,
                    "\\bannuaire\\b|\\bqui\\s+est\\b|\\bcoordonnees\\b|\\bcontact\\b"),
            new Regle(IntentionAssistant.ETAT_DOSSIER,
                    "\\bou\\s+en\\s+est\\b|\\bstatut\\s+du\\s+dossier\\b|\\betat\\s+du\\s+dossier\\b"));

    /** Une tournure et l'intention qu'elle désigne. */
    private record Regle(IntentionAssistant intention, String motif) {

        boolean reconnait(String questionNormalisee) {
            return Pattern.compile(motif).matcher(questionNormalisee).find();
        }
    }

    /** Ce que l'aiguillage a décidé, et ce qu'il a retenu de la question pour la lecture à faire. */
    public record Aiguillage(IntentionAssistant intention, String parametre, boolean surReference) {

        static Aiguillage surReference(String reference) {
            return new Aiguillage(IntentionAssistant.ETAT_DOSSIER, reference, true);
        }

        static Aiguillage de(IntentionAssistant intention, String parametre) {
            return new Aiguillage(intention, parametre, false);
        }
    }

    /**
     * Décide de l'intention d'une question.
     *
     * @param question la question, telle que l'utilisateur l'a écrite
     * @param ouvertes les intentions que le profil de l'utilisateur peut atteindre —
     *                 {@link IntentionAssistant#REGLE} en fait toujours partie
     */
    public Aiguillage decider(String question, List<IntentionAssistant> ouvertes) {
        if (question == null || question.isBlank()) {
            return Aiguillage.de(IntentionAssistant.REGLE, null);
        }
        Aiguillage surReference = surReference(question, ouvertes);
        if (surReference != null) {
            return surReference;
        }
        String normalisee = normaliser(question);
        for (Regle regle : REGLES) {
            if (ouvertes.contains(regle.intention()) && regle.reconnait(normalisee)) {
                return Aiguillage.de(regle.intention(), parametre(regle.intention(), question));
            }
        }
        return Aiguillage.de(IntentionAssistant.REGLE, null);
    }

    /**
     * L'intention qu'une <strong>référence de dossier</strong> désigne sans ambiguïté. {@code null} si
     * la question n'en porte pas, ou si le profil n'a pas accès à l'état d'un dossier — le raccourci ne
     * contourne aucune garde.
     */
    Aiguillage surReference(String question, List<IntentionAssistant> ouvertes) {
        if (question == null || !ouvertes.contains(IntentionAssistant.ETAT_DOSSIER)) {
            return null;
        }
        Matcher reference = REFERENCE.matcher(question);
        if (reference.find()) {
            // La référence est recomposée sans ses espaces : « 00002 / PPM / CNM / 2026 » se cherche
            // comme « 00002/PPM/CNM/2026 ».
            return Aiguillage.surReference(reference.group(1).replaceAll("\\s+", ""));
        }
        Matcher numero = NUMERO_DOSSIER.matcher(question);
        if (numero.find()) {
            return Aiguillage.surReference(numero.group(1));
        }
        return null;
    }

    /**
     * Ce que la lecture doit chercher. Seules les intentions qui <strong>cherchent</strong> ont un
     * paramètre, et c'est la question elle-même, nettoyée : le périmètre de l'appelant fait le reste
     * (4.b — un paramètre ne peut qu'élargir à l'intérieur de ce qu'il voit déjà).
     */
    private static String parametre(IntentionAssistant intention, String question) {
        return switch (intention) {
            case TROUVER_DOSSIER, ANNUAIRE, ETAT_DOSSIER -> termesUtiles(question);
            default -> null;
        };
    }

    /**
     * Les mots de la question qui peuvent servir de critère : les mots « pleins », débarrassés des
     * tournures de politesse et des verbes de demande. Rendus tels quels (accents compris) : la
     * recherche du serveur, elle, sait s'en passer.
     */
    static String termesUtiles(String question) {
        String sansPonctuation = question.replaceAll("[?!.,;:]", " ");
        String[] mots = sansPonctuation.split("\\s+");
        StringBuilder utiles = new StringBuilder();
        for (String mot : mots) {
            String normalise = normaliser(mot);
            if (normalise.length() > 3 && !VIDES.contains(normalise)) {
                utiles.append(utiles.isEmpty() ? "" : " ").append(mot);
            }
        }
        return utiles.isEmpty() ? question.strip() : utiles.toString();
    }

    /** Mots qui ne disent rien d'un dossier : verbes de demande, politesse, articles longs. */
    private static final List<String> VIDES = List.of("trouve", "trouver", "retrouve", "retrouver",
            "cherche", "chercher", "recherche", "rechercher", "montre", "montrer", "affiche", "afficher",
            "liste", "lister", "donne", "donner", "peux", "peut", "pouvez", "veux", "voudrais", "merci",
            "bonjour", "salut", "dossier", "dossiers", "quel", "quelle", "quels", "quelles", "pour",
            "avec", "dans", "tous", "toutes", "cette", "celui", "celle", "leur", "leurs", "est-ce",
            "annuaire", "coordonnees", "contact", "statut", "etat");

    /** Minuscules sans accents — une question se tape vite, et sans accents la moitié du temps. */
    static String normaliser(String texte) {
        return Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.FRENCH)
                .strip();
    }
}
