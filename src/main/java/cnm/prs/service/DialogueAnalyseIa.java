package cnm.prs.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
     * Plafond de lignes envoyées au modèle. Un PPM de plusieurs centaines de lignes dépasserait la fenêtre
     * de contexte d'un modèle local, et la réponse s'en dégraderait sans avertissement.
     */
    public static final int LIGNES_MAX = 120;

    /** Longueur maximale d'un objet transmis : au-delà, la phrase est tronquée, pas la liste. */
    private static final int OBJET_MAX = 220;

    /** Longueur maximale d'un texte rendu par le modèle (constat ou suggestion). */
    private static final int TEXTE_MAX = 900;

    /** Tête commune des consignes : le cadre, et le format de réponse. Volontairement brève. */
    private static final String CADRE = """
            Tu aides le contrôle a priori des marchés publics de Madagascar (Commission Nationale des \
            Marchés). On te donne les lignes d'un PLAN ANNUEL de passation — pas un dossier d'appel \
            d'offres.
            """;

    private static final String FORMAT = """

            Réponds UNIQUEMENT par cet objet JSON, sans texte autour :
            {"pistes":[{"lignes":[1,2],"constat":"une ou deux phrases","suggestion":"Au lieu de : ...\\nLire : ..."}]}
            S'il n'y a rien à signaler, réponds exactement {"pistes":[]} — c'est une réponse normale et \
            fréquente. Ne cherche pas à trouver quelque chose pour avoir trouvé quelque chose.
            N'utilise que des numéros de ligne présents dans la liste. Écris en français, vouvoie, et \
            emploie « il semble » ou « à vérifier » : ce sont des pistes, pas des constats. Ne donne aucun \
            avis sur le dossier — cette décision appartient à la Commission.
            """;

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

    /** Les lignes soumises au modèle : celles du plan, plafonnées. */
    public List<Marche> lignesAnalysees(ContextePreControle contexte) {
        return contexte.lignes().stream().limit(LIGNES_MAX).toList();
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
            log.warn("[PRE-CONTROLE IA] réponse illisible du modèle ({}) sur le PPM {} : passe ignorée.",
                    type.name(), contexte.ppm().getIdPpm());
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
     */
    public String synthese(List<SignalementDetecte> pistes) {
        if (pistes.isEmpty()) {
            return null;
        }
        String liste = pistes.stream().limit(3)
                .map(p -> "lignes " + numeros(p).stream().map(String::valueOf)
                        .collect(Collectors.joining(" et ")) + " (" + resumeType(p.type()) + ')')
                .collect(Collectors.joining(" ; "));
        return "À regarder d'abord : " + liste
                + (pistes.size() > 3 ? " — et " + (pistes.size() - 3) + " autre(s) piste(s)." : ".");
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
        if (constat == null) {
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
     */
    private JsonNode json(String reponse) {
        if (reponse == null || reponse.isBlank()) {
            return null;
        }
        int debut = reponse.indexOf('{');
        int fin = reponse.lastIndexOf('}');
        if (debut < 0 || fin <= debut) {
            return null;
        }
        try {
            return mapper.readTree(reponse.substring(debut, fin + 1));
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
