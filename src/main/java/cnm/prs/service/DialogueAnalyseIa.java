package cnm.prs.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import cnm.prs.entity.Marche;
import cnm.prs.entity.Nature;
import cnm.prs.enums.GraviteSignalement;
import cnm.prs.enums.TypeSignalement;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 6) — <strong>ce qu'on demande au modèle,
 * et ce qu'on accepte de lui</strong>.
 *
 * <p>Isolé de {@link AnalysePreControleIaService}, qui s'occupe d'activer, d'enregistrer et de
 * journaliser, pour une raison précise : le dialogue avec le modèle est la partie <strong>qu'il faut
 * mesurer</strong>. La batterie de référence ({@code PreControleIaBatterieTest}) rejoue ces fonctions
 * contre le vrai modèle, sans base ni transaction — c'est ce qui permet de dire, avant de changer de
 * modèle en production, s'il fait mieux ou moins bien.</p>
 *
 * <h2>Une passe courte par question — leçon de la mesure</h2>
 *
 * <p>La première version posait <strong>une seule</strong> question portant les trois recherches, avec
 * leurs exceptions et un ordre de priorité. Mesure : <strong>3 cas sur 7</strong>. En allongeant la
 * consigne pour corriger les écarts, on est passé à 4, puis retombé à 3 — le modèle de 9 milliards de
 * paramètres se contredisait (une piste « objet imprécis » dont le constat disait que l'objet était
 * suffisant), oubliait l'interdiction du même compte, et confondait les types.</p>
 *
 * <p>D'où la forme actuelle : <strong>une passe par type</strong>, chacune avec une consigne courte qui ne
 * demande qu'une chose. Trois appels au lieu d'un — c'est plus de calcul, mais l'analyse est un geste
 * explicite et rare, pas une frappe au clavier. Et la <strong>précision compte plus que le coût</strong> :
 * une piste fausse apprend à la PRMP à tout écarter sans lire.</p>
 *
 * <h2>Les garde-fous sont en code, pas en consigne</h2>
 *
 * <p>Tout ce qui peut être vérifié l'est ici, et non demandé au modèle : les lignes doivent exister dans
 * CE plan, les textes sont bornés, les doublons écartés, et une piste de fractionnement portant sur des
 * lignes du <strong>même compte</strong> est refusée — c'est le domaine exclusif d'une règle opposable, et
 * le modèle l'oubliait une fois sur deux malgré la consigne. Ce qu'on peut vérifier ne se demande pas.</p>
 */
@Component
public class DialogueAnalyseIa {

    private static final Logger log = LoggerFactory.getLogger(DialogueAnalyseIa.class);

    /**
     * Plafond de pistes retenues par analyse, toutes passes confondues. Au-delà, l'écran cesse d'être
     * lisible et la PRMP écarte en série sans lire — ce qui vaut moins que rien (plan, 3.e).
     */
    public static final int PISTES_MAX = 8;

    /**
     * ⚠️ <strong>Lignes par question posée au modèle</strong> — réglé en recette le 2026-09-20, et c'est un
     * défaut qui n'était visible que là.
     *
     * <p>La première version envoyait le plan entier, plafonné à 120 lignes. Sur un plan réel de 130
     * lignes, l'inventaire faisait <strong>37 000 caractères</strong> — environ 10 000 jetons, très
     * au-delà de la fenêtre de contexte d'un modèle local (4 096 par défaut sous Ollama). Le modèle
     * recevait un prompt tronqué et rendait une réponse illisible : les trois passes étaient
     * <strong>ignorées en silence</strong>, et l'analyse annonçait « rien à signaler » sur un plan qu'elle
     * n'avait pas lu. C'est le pire des défauts possibles pour cette fonctionnalité, et aucun test contre
     * un faux serveur ne pouvait le voir.</p>
     *
     * <p>D'où le découpage : des lots d'une trentaine de lignes (~7 000 caractères), et
     * {@link #LOTS_MAX} lots au plus par question — soit six appels pour les trois recherches. Au-delà, un
     * geste explicite se transformerait en attente de plusieurs minutes.</p>
     */
    public static final int LIGNES_PAR_LOT = 30;

    /**
     * Nombre de lots examinés par recherche. Borne le temps d'une analyse (six appels au modèle) — et
     * <strong>ce qu'elle a vu se dit à l'écran</strong> : « l'assistant a examiné les 60 premières lignes
     * sur 130 ». Une couverture partielle annoncée vaut mieux qu'une couverture totale supposée.
     */
    public static final int LOTS_MAX = 2;

    /** Nombre maximal de lignes qu'une analyse examine, toutes passes confondues. */
    public static final int LIGNES_MAX = LIGNES_PAR_LOT * LOTS_MAX;

    /**
     * Longueur maximale d'un objet transmis. Ramenée de 220 à 160 caractères en recette : la partie
     * distinctive d'un objet est à son début (« Acquisition de matériels informatiques… »), la fin porte
     * les mentions de lot et d'exercice, que le modèle n'a pas à lire — et chaque caractère compte dans la
     * fenêtre de contexte.
     */
    private static final int OBJET_MAX = 160;

    /** Longueur maximale d'un texte rendu par le modèle (constat ou suggestion). */
    private static final int TEXTE_MAX = 900;

    /**
     * ⚠️ Longueur minimale d'un constat — réglée en recette le 2026-09-20. Trois pistes s'étaient
     * enregistrées avec « une phrase » pour constat : le modèle avait <strong>recopié le gabarit</strong>
     * du format au lieu de le remplir. La PRMP lisait alors un point signalé qui ne disait rien.
     *
     * <p>Le gabarit ne se demande donc plus en clair ({@link #FORMAT}), et ce qui en resterait est refusé
     * ici. Le plancher reste <strong>bas à dessein</strong> : il est là pour écarter ce qui n'est pas un
     * constat (« une phrase », « à préciser »), pas pour légiférer sur le style — une piste brève mais
     * informative reste une piste. Sur un plan réel, le plus court qu'ait rendu la recette faisait 99
     * caractères.</p>
     */
    private static final int CONSTAT_MIN = 20;

    /** Ce qu'un gabarit non rempli laisse derrière lui : {@code <…>}. */
    private static final Pattern GABARIT = Pattern.compile("<[^>]{0,80}>");

    /**
     * Longueur d'un objet cité dans la phrase de hiérarchisation. Trois objets entiers du plan de recette
     * feraient 600 caractères : la phrase censée faire gagner du temps en ferait perdre.
     */
    private static final int OBJET_SYNTHESE = 55;

    /** Tête commune des consignes : le cadre, et le format de réponse. Volontairement brève. */
    private static final String CADRE = """
            Tu aides le contrôle a priori des marchés publics de Madagascar (Commission Nationale des \
            Marchés). On te donne les lignes d'un PLAN ANNUEL de passation — pas un dossier d'appel \
            d'offres.
            """;

    /**
     * ⚠️ Le format, et le <strong>plafond de trois pistes par réponse</strong> — réglé en recette le
     * 2026-09-20. Le modèle répondait juste, mais long : plus de 3 000 caractères de constats et de
     * suggestions, que le serveur d'inférence coupait à son budget de sortie. Un JSON tronqué est un JSON
     * illisible, et l'analyse concluait « rien à signaler ». Demander peu, et court, est la première des
     * deux parades ; la seconde est de savoir <strong>récupérer</strong> une réponse coupée
     * ({@link #json}).
     */
    private static final String FORMAT = """

            Réponds UNIQUEMENT par cet objet JSON, sans texte autour :
            {"pistes":[{"lignes":[1,2],"constat":"<ce qui vous semble anormal>","suggestion":"Au lieu de : \
            <ce qui est écrit>\\nLire : <ce qu'il faudrait lire>"}]}
            Remplace ce qui est entre chevrons ; ne recopie pas les chevrons.
            Au plus TROIS pistes, les plus nettes. Un constat d'UNE phrase, une suggestion d'une ligne : une \
            réponse trop longue est coupée et perdue.
            S'il n'y a rien à signaler, réponds exactement {"pistes":[]} — c'est une réponse normale et \
            fréquente. Ne cherche pas à trouver quelque chose pour avoir trouvé quelque chose.
            N'utilise que des numéros de ligne présents dans la liste. Écris en français, vouvoie, et \
            emploie « il semble » ou « à vérifier » : ce sont des pistes, pas des constats. Ne donne aucun \
            avis sur le dossier — cette décision appartient à la Commission.
            """;

    /**
     * Budget de sortie d'une passe, en jetons. Large au regard de trois pistes courtes — le défaut trouvé
     * en recette venait de l'inverse : le budget d'une réponse de chat (900) coupait un JSON de trois
     * pistes en plein milieu.
     */
    public static final int JETONS_PAR_PASSE = 1500;

    private final ObjectMapper mapper;

    public DialogueAnalyseIa(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Les pistes retenues d'une passe. */
    public record Lecture(List<SignalementDetecte> pistes) {
    }

    /**
     * La consigne d'<strong>une</strong> recherche. Chacune ne demande qu'une chose, et dit ce qu'elle ne
     * veut pas — c'est ce que la mesure a montré nécessaire.
     */
    public String consigne(TypeSignalement type) {
        // La recherche est nommée en tête : c'est la trace qui permet, dans un journal ou un test, de
        // savoir de QUELLE passe une réponse vient — le type rendu, lui, ne vient jamais du modèle.
        return "Recherche demandée : " + type.name() + "\n" + CADRE + switch (type) {
            case FRACTIONNEMENT_DEGUISE -> """

                    Cherche UNE SEULE chose : des lignes qui couvrent LE MÊME BESOIN sous des libellés \
                    différents — même route nationale, même bâtiment, même périmètre irrigué, même \
                    opération découpée en sections, en phases ou en tranches.

                    Ne signale pas deux besoins réellement différents pour un même bénéficiaire : des \
                    tables et des bancs pour une même école sont deux fournitures distinctes.
                    """;
            case NATURE_INCOHERENTE -> """

                    Cherche UNE SEULE chose : les lignes dont la NATURE déclarée ne correspond pas à \
                    l'objet — par exemple des ordinateurs déclarés en « Travaux », ou la construction d'un \
                    bâtiment déclarée en « Fournitures ».
                    """;
            case OBJET_IMPRECIS -> """

                    Cherche UNE SEULE chose : les objets GÉNÉRIQUES, au point qu'on ne sait pas ce qui est \
                    acheté — « Achat de matériel », « Travaux divers », « Prestations de services », \
                    « Équipements », « Fournitures de bureau » sans plus.

                    APPLIQUE CE TEST, ET RIEN D'AUTRE : si l'objet contient un CHIFFRE (une quantité, une \
                    surface, une longueur, un nombre) OU un NOM DE LIEU, d'établissement ou de route, \
                    alors il est SUFFISANT et tu ne le signales pas. Tu signales en revanche TOUJOURS un \
                    objet qui n'a NI l'un NI l'autre : « Achat de matériel », « Travaux divers », \
                    « Prestations de services », « Équipements », « Fournitures de bureau » doivent être \
                    signalés.

                    Tu n'as pas à juger si l'objet est assez détaillé pour lancer la procédure : la marque, \
                    le modèle, les matériaux, la classe énergétique, les spécifications techniques, l'état \
                    des lieux et le détail des interventions relèvent du dossier d'appel d'offres, PAS du \
                    plan annuel. Ne reproche jamais à un objet d'être trop précis.

                    Le plus souvent, un plan n'a aucun objet générique : réponds alors {"pistes":[]}.
                    """;
            default -> throw new IllegalArgumentException(
                    "Type hors du champ de l'assistant : " + type.name());
        } + FORMAT;
    }

    /** Les lignes soumises au modèle, toutes passes confondues : celles du plan, plafonnées. */
    public List<Marche> lignesAnalysees(ContextePreControle contexte) {
        return contexte.lignes().stream().limit(LIGNES_MAX).toList();
    }

    /**
     * Les <strong>lots</strong> de lignes, un par question posée au modèle. Voir {@link #LIGNES_PAR_LOT} :
     * un plan entier ne tient pas dans la fenêtre de contexte d'un modèle local, et le lui envoyer quand
     * même produit une réponse illisible — donc une analyse qui dit « rien à signaler » sans avoir lu.
     */
    public List<List<Marche>> lots(ContextePreControle contexte) {
        List<Marche> lignes = lignesAnalysees(contexte);
        List<List<Marche>> lots = new ArrayList<>();
        for (int debut = 0; debut < lignes.size(); debut += LIGNES_PAR_LOT) {
            lots.add(lignes.subList(debut, Math.min(debut + LIGNES_PAR_LOT, lignes.size())));
        }
        return lots;
    }

    /**
     * L'inventaire du plan, en texte compact et numéroté par {@code ID_DETAIL} — le modèle doit pouvoir
     * désigner une ligne sans ambiguïté, et nous devons pouvoir refuser un numéro qu'il aurait inventé.
     *
     * <p>Aucun acteur, aucune référence de dossier, aucune PRMP : le modèle n'a pas besoin de savoir de
     * qui est ce plan pour juger d'un libellé.</p>
     */
    public String inventaire(ContextePreControle contexte, List<Marche> lignes) {
        StringBuilder texte = new StringBuilder("Exercice : ")
                .append(contexte.exercice() == null ? "inconnu" : contexte.exercice())
                .append("\nLignes du plan (numéro | nature | compte(s) | financement | montant HT | objet) :\n");
        for (Marche ligne : lignes) {
            texte.append("- ").append(ligne.getIdDetail()).append(" | ")
                    .append(contexte.nature(ligne).map(Nature::getLibelle).orElse("nature inconnue"))
                    .append(" | ")
                    .append(contexte.comptes(ligne).isEmpty() ? "compte absent"
                            : String.join(", ", contexte.comptes(ligne)))
                    .append(" | ")
                    .append(ligne.getFinancement() == null || ligne.getFinancement().isBlank()
                            ? "financement non précisé" : ligne.getFinancement().trim())
                    .append(" | ").append(montant(contexte.montant(ligne)))
                    .append(" | ").append(objet(ligne)).append('\n');
        }
        if (contexte.lignes().size() > lignes.size()) {
            texte.append("(plan tronqué : ").append(contexte.lignes().size() - lignes.size())
                    .append(" ligne(s) au-delà de la limite d'analyse ne sont pas listées)\n");
        }
        return texte.toString();
    }

    /**
     * Lit la réponse d'<strong>une passe</strong> et n'en garde que ce qui est <strong>vérifiable</strong>.
     * Le type n'est pas demandé au modèle : il est celui de la passe — une question, une réponse.
     *
     * @param dejaVues clés déjà retenues par les passes précédentes, pour qu'une même piste ne sorte pas
     *                 deux fois
     */
    public Lecture lire(String reponse, TypeSignalement type, ContextePreControle contexte,
            List<Marche> lignes, Set<String> dejaVues) {
        JsonNode racine = json(reponse);
        if (racine == null) {
            // ⚠️ L'extrait est dans le journal, et il y reste : sans lui, une réponse illisible est
            // indiagnosticable — on ne sait pas si le modèle a refusé, divagué, ou été coupé. C'est ce qui
            // a coûté le plus de temps à la recette du 2026-09-20.
            log.warn("[PRE-CONTROLE IA] réponse illisible du modèle ({}) sur le PPM {} : passe ignorée. "
                    + "Réponse reçue ({} caractères) : {}",
                    type.name(), contexte.ppm().getIdPpm(), reponse == null ? 0 : reponse.length(),
                    extrait(reponse));
            return new Lecture(List.of());
        }
        Map<Integer, Marche> parNumero = lignes.stream()
                .collect(Collectors.toMap(Marche::getIdDetail, l -> l, (a, b) -> a, LinkedHashMap::new));

        List<SignalementDetecte> pistes = new ArrayList<>();
        for (JsonNode brute : racine.path("pistes")) {
            retenir(brute, type, parNumero, contexte, dejaVues).ifPresent(pistes::add);
        }
        return new Lecture(pistes);
    }

    /**
     * La <strong>phrase de hiérarchisation</strong> — « sur 120 lignes, regardez ces trois-là d'abord ».
     *
     * <p>Elle est <strong>composée ici</strong>, à partir des pistes retenues, et non demandée au modèle :
     * une phrase de synthèse générée est une surface d'hallucination de plus, pour un gain nul — les
     * lignes à regarder, nous les connaissons exactement.</p>
     *
     * <p>⚠️ Elle nomme les lignes par leur <strong>objet</strong>, pas par leur identifiant technique
     * (recette du 2026-09-20 : « lignes 300009 et 300029 » ne dit rien à une PRMP, qui ne voit nulle part
     * ce numéro dans son plan — la phrase censée dire OÙ REGARDER ne le disait donc pas).</p>
     */
    public String synthese(List<SignalementDetecte> pistes, ContextePreControle contexte) {
        if (pistes.isEmpty()) {
            return null;
        }
        Map<Integer, Marche> parNumero = contexte.lignes().stream().collect(
                Collectors.toMap(Marche::getIdDetail, l -> l, (a, b) -> a, LinkedHashMap::new));
        String liste = pistes.stream().limit(3)
                .map(p -> designationCourte(numeros(p), parNumero, contexte) + " (" + resumeType(p.type()) + ')')
                .collect(Collectors.joining(" ; "));
        return "À regarder d'abord : " + liste
                + (pistes.size() > 3 ? " — et " + (pistes.size() - 3) + " autre(s) piste(s)." : ".");
    }

    /** L'objet de la première ligne visée, écourté, et le compte des autres. */
    private static String designationCourte(List<Integer> numeros, Map<Integer, Marche> parNumero,
            ContextePreControle contexte) {
        Marche premiere = numeros.isEmpty() ? null : parNumero.get(numeros.get(0));
        String objet = premiere == null ? null : contexte.designation(premiere);
        if (objet == null || objet.isBlank()) {
            // Repli : mieux vaut un identifiant qu'une phrase amputée.
            return "lignes " + numeros.stream().map(String::valueOf).collect(Collectors.joining(" et "));
        }
        String court = objet.length() <= OBJET_SYNTHESE ? objet
                : objet.substring(0, OBJET_SYNTHESE - 1).trim() + "…";
        return "« " + court + " »"
                + (numeros.size() > 1 ? " et " + (numeros.size() - 1)
                        + (numeros.size() == 2 ? " autre ligne" : " autres lignes") : "");
    }

    private static List<Integer> numeros(SignalementDetecte piste) {
        if (piste.idDetail() != null) {
            return List.of(piste.idDetail());
        }
        return piste.lignes().stream().map(SignalementDetecte.LigneVisee::idDetail).toList();
    }

    private static String resumeType(TypeSignalement type) {
        return switch (type) {
            case FRACTIONNEMENT_DEGUISE -> "même besoin possible";
            case NATURE_INCOHERENTE -> "nature à confirmer";
            case OBJET_IMPRECIS -> "objet à préciser";
            default -> type.name();
        };
    }

    // ------------------------------------------------------------------ filtrage

    /** Une piste, si elle passe toutes les vérifications. */
    private Optional<SignalementDetecte> retenir(JsonNode brute, TypeSignalement type,
            Map<Integer, Marche> parNumero, ContextePreControle contexte, Set<String> dejaVues) {
        String constat = texteBorne(brute.path("constat").asString(null), TEXTE_MAX);
        // ⚠️ Un constat qui a gardé le gabarit, ou qui tient en trois mots, n'apprend rien à la PRMP et
        // occupe la place d'un vrai point (recette du 2026-09-20). Cela se vérifie : cela ne se demande pas.
        if (constat == null || constat.length() < CONSTAT_MIN || GABARIT.matcher(constat).find()) {
            return Optional.empty();
        }
        List<Integer> numeros = new ArrayList<>();
        for (JsonNode numero : brute.path("lignes")) {
            if (numero.isNumber() && parNumero.containsKey(numero.asInt())) {
                numeros.add(numero.asInt());
            }
        }
        numeros = numeros.stream().distinct().sorted().toList();
        if (numeros.isEmpty()) {
            return Optional.empty();   // une piste qui ne vise aucune ligne connue n'est pas exploitable
        }
        if (type == TypeSignalement.FRACTIONNEMENT_DEGUISE && !recevablePourFractionnement(numeros, parNumero,
                contexte)) {
            return Optional.empty();
        }
        // La clé rend la piste retrouvable d'une analyse à l'autre : son type et les lignes qu'elle vise,
        // pas son texte — le modèle ne reformule pas deux fois pareil, et un écartement motivé doit tenir.
        String cle = type.name() + "|"
                + numeros.stream().map(String::valueOf).collect(Collectors.joining("-"));
        if (cle.length() > 200 || !dejaVues.add(cle)) {
            return Optional.empty();
        }
        String suggestion = texteBorne(brute.path("suggestion").asString(null), TEXTE_MAX);
        if (suggestion != null && GABARIT.matcher(suggestion).find()) {
            suggestion = null;   // le constat vaut seul ; un gabarit affiché ne vaut rien
        }
        if (numeros.size() == 1) {
            return Optional.of(SignalementDetecte.surLigne(type, GraviteSignalement.A_VERIFIER, cle,
                    numeros.get(0), constat, suggestion));
        }
        List<SignalementDetecte.LigneVisee> visees = numeros.stream()
                .map(n -> new SignalementDetecte.LigneVisee(n, contexte.montant(parNumero.get(n)))).toList();
        return Optional.of(SignalementDetecte.surPlusieursLignes(type, GraviteSignalement.A_VERIFIER, cle,
                visees, constat, suggestion));
    }

    /**
     * Une piste de <strong>fractionnement déguisé</strong> n'est recevable que si elle vise
     * <strong>plusieurs</strong> lignes et qu'elles ne <strong>partagent aucun compte</strong>.
     *
     * <p>Ce garde-fou est en code parce que la consigne ne suffisait pas : le modèle signalait des lignes
     * d'un même compte une fois sur deux. Or ce cas est celui de la <strong>règle</strong>
     * {@code FRACTIONNEMENT_COMPTE}, qui l'établit comme un fait opposable, avec sa base légale. Le dire
     * deux fois — une fois en fait, une fois en piste — ne serait que du bruit, et le bruit tue l'outil.</p>
     */
    private static boolean recevablePourFractionnement(List<Integer> numeros,
            Map<Integer, Marche> parNumero, ContextePreControle contexte) {
        if (numeros.size() < 2) {
            return false;
        }
        Set<String> communs = null;
        for (Integer numero : numeros) {
            Set<String> comptes = new LinkedHashSet<>(contexte.comptes(parNumero.get(numero)));
            if (communs == null) {
                communs = comptes;
            } else {
                communs.retainAll(comptes);
            }
        }
        return communs == null || communs.isEmpty();
    }

    /** Début d'une réponse, sur une seule ligne, pour le journal technique. */
    private static String extrait(String reponse) {
        if (reponse == null || reponse.isBlank()) {
            return "(vide)";
        }
        String propre = reponse.strip().replaceAll("\\s+", " ");
        return propre.length() <= 300 ? propre : propre.substring(0, 300) + "…";
    }

    private static String texteBorne(String texte, int max) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        String propre = texte.strip();
        return propre.length() <= max ? propre : propre.substring(0, max - 1) + "…";
    }

    /**
     * Extrait l'objet JSON de la réponse, même si le modèle l'a entouré de texte ou d'une clôture de bloc
     * Markdown — ce qu'un modèle local fait régulièrement malgré la consigne.
     *
     * <p>⚠️ <strong>Et même s'il a été coupé en plein milieu</strong> (recette du 2026-09-20) : quand la
     * réponse dépasse le budget de sortie, le serveur d'inférence la tronque, et tout est perdu pour un
     * point-virgule manquant. {@link #reparer} récupère alors les pistes <strong>complètes</strong> qui
     * précèdent la coupure. Jeter trois bonnes pistes parce que la quatrième est incomplète serait
     * absurde.</p>
     */
    private JsonNode json(String reponse) {
        if (reponse == null || reponse.isBlank()) {
            return null;
        }
        int debut = reponse.indexOf('{');
        if (debut < 0) {
            return null;
        }
        int fin = reponse.lastIndexOf('}');
        if (fin > debut) {
            try {
                return mapper.readTree(reponse.substring(debut, fin + 1));
            } catch (RuntimeException e) {
                // La réponse est probablement tronquée : on tente de sauver ce qui est complet.
                log.info("[PRE-CONTROLE IA] réponse mal formée, tentative de récupération des pistes "
                        + "complètes ({} caractères).", reponse.length());
            }
        }
        return reparer(reponse.substring(debut));
    }

    /**
     * Reconstruit un JSON exploitable à partir d'une réponse <strong>coupée</strong> : on garde les
     * éléments complets de {@code "pistes"} et on referme le tableau.
     *
     * <p>La profondeur d'accolades suffit à repérer la fin de chaque piste : à chaque retour au niveau du
     * tableau, l'élément qui précède est complet. On coupe après le dernier, et on ajoute {@code ]}}.
     * {@code null} si aucune piste complète n'a été reçue.</p>
     */
    private JsonNode reparer(String brut) {
        int crochet = brut.indexOf('[');
        if (crochet < 0) {
            return null;
        }
        int profondeur = 0;
        int finDernierElement = -1;
        boolean dansChaine = false;
        boolean echappe = false;
        for (int i = crochet + 1; i < brut.length(); i++) {
            char c = brut.charAt(i);
            if (echappe) {
                echappe = false;
                continue;
            }
            if (c == '\\') {
                echappe = true;
            } else if (c == '"') {
                dansChaine = !dansChaine;
            } else if (!dansChaine && c == '{') {
                profondeur++;
            } else if (!dansChaine && c == '}') {
                profondeur--;
                if (profondeur == 0) {
                    finDernierElement = i;
                }
            }
        }
        if (finDernierElement < 0) {
            return null;
        }
        try {
            return mapper.readTree(brut.substring(0, finDernierElement + 1) + "]}");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String montant(BigDecimal montant) {
        return montant == null ? "montant absent" : ContextePreControle.formaterMontant(montant) + " Ar";
    }

    private static String objet(Marche ligne) {
        String objet = ligne.getDesignationMarche();
        if (objet == null || objet.isBlank()) {
            return "objet absent";
        }
        String propre = objet.trim().replaceAll("\\s+", " ");
        return propre.length() <= OBJET_MAX ? propre : propre.substring(0, OBJET_MAX - 1) + "…";
    }
}
