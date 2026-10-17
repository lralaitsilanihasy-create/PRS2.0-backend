package cnm.prs.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ⚠️ 2026-10-17 (demande front « cadrage-depuis-dao », §B1) — les réponses de cadrage qu'aucune section reconnue ne tranche, cherchées
 * dans le texte par mots-clés : {@code variantes}, {@code formeGroupement}, {@code provenance}, {@code prixRevisable},
 * {@code garantieSoumission}, {@code avance}. Cela vaut aussi pour un DAO externe, qui n'emploie pas nos modèles.
 *
 * <ul>
 *   <li><strong>Où</strong> : seulement dans les données particulières (DPAO, DPAC, DPIC, règlement de la consultation), l'acte
 *       d'engagement et le CCAP (cahier des prescriptions spéciales). La partie en cours est celle du dernier titre de partie vu
 *       (paragraphe court qui commence par le titre, numéro de partie ou de section admis devant). Les instructions aux candidats, le
 *       CCAG, les formulaires, les modèles, les spécifications techniques, le sommaire et tout ce qui précède le premier titre ne
 *       comptent jamais.</li>
 *   <li><strong>Quoi</strong> : une phrase affirmative. Une phrase au conditionnel ou qui renvoie aux données particulières (« si… »,
 *       « lorsqu'… », « le cas échéant », « indiqué dans les DPAO »), un trou de modèle (« &lt;…&gt; », « […] », « …… ») ou une
 *       alternative restée à choisir (« OUI / NON ») ne compte pas. Une négation l'emporte sur le mot-clé qu'elle porte.</li>
 *   <li><strong>Deux réponses contraires</strong> dans le même DAO : un conflit, jamais une réponse.</li>
 * </ul>
 * Une réponse ainsi lue n'est jamais sûre : le front ne la coche pas d'office.
 */
final class CadrageParClause {

    /** Les clés que la lecture par clause peut proposer, dans l'ordre des questions. */
    static final List<String> CLES = List.of("variantes", "formeGroupement", "provenance", "prixRevisable", "garantieSoumission", "avance");
    /** Le contrat-cadre ne pose que ces deux-là (§B2 : les quatre autres ne servent à rien pour lui). */
    static final Set<String> CLES_CONTRAT_CADRE = Set.of("garantieSoumission", "avance");

    record Reponse(String cle, String valeur, String extrait, int paragraphe, String partie) {
    }

    record Resultat(List<Reponse> reponses, Map<String, List<String>> conflits) {
    }

    private record Regle(String cle, String valeur, Pattern motif) {
    }

    private enum Zone { HORS, ADMISE, EXCLUE }

    private static final String NUMERO = "^(?:[-–•▪*\\s]*)(?:(?:\\d+(?:\\.\\d+)*\\.?|[IVX]+\\.?|[A-H]\\.)\\s*[-–.:)]?\\s*)?"
            + "(?:(?:PREMIERE|DEUXIEME|TROISIEME|QUATRIEME|CINQUIEME)\\s+PARTIE\\s*[:\\-–]?\\s*)?(?:(?:SECTION|CHAPITRE|PARTIE)\\s+[IVX\\d]+\\s*[:\\-–.]?\\s*)?";
    private static final Pattern TITRE_EXCLU = Pattern.compile(NUMERO + "(?:INSTRUCTIONS?\\s+AUX\\s+(?:CANDIDATS|SOUMISSIONNAIRES|CONSULTANTS)"
            + "|CAHIER\\s+DES\\s+CLAUSES\\s+ADMINISTRATIVES\\s+GENERALES|CCAG\\b|FORMULAIRES?\\b|MODELES?\\s+D|SPECIFICATIONS\\s+TECHNIQUES"
            + "|SOMMAIRE|TABLE\\s+DES\\s+MATIERES|TERMES\\s+DE\\s+REFERENCE|BORDEREAU|DETAIL\\s+QUANTITATIF|CADRE\\s+DU\\s+BORDEREAU)");
    private static final Pattern TITRE_ADMIS = Pattern.compile(NUMERO + "(?:DONNEES\\s+PARTICULIERES|REGLEMENT\\s+DE\\s+LA\\s+CONSULTATION"
            + "|ACTE\\s+D'ENGAGEMENT|CONTRAT[- ]CADRE\\s+VALANT|CAHIER\\s+DES\\s+CLAUSES\\s+ADMINISTRATIVES\\s+PARTICULIERES"
            + "|CAHIER\\s+DES\\s+PRESCRIPTIONS\\s+SPECIALES)");
    private static final int LONGUEUR_TITRE = 150;

    private static final Pattern GENERAL = Pattern.compile("\\bSI\\b|\\bS'IL\\b|LORSQU|LE CAS ECHEANT|EVENTUELLEMENT|IL Y A LIEU"
            + "|\\b(?:DANS|PAR|AUX|SELON|EN|DES)\\s+(?:LES\\s+|LA\\s+|DES\\s+)?(?:DPAO|DPAC|DPIC|DONNEES\\s+PARTICULIERES)"
            + "|INDIQUE\\w*\\s+(?:DANS|PAR|AUX)|PRECISE\\w*\\s+(?:DANS|PAR|AUX)|[<>\\[\\]]|XXX|\\.{4,}|…{2,}"
            + "|\\bOUI\\s*/\\s*NON\\b|\\bNON\\s*/\\s*OUI\\b|/\\s*NON\\s+\\w|\\w\\s*/\\s*(?:NE\\s|N')");
    private static final Pattern NEGATION = Pattern.compile("\\bNE\\b|\\bN'|\\bPAS\\b|\\bNON\\b|\\bAUCUNE?\\b|\\bNI\\b");
    private static final Pattern PHRASES = Pattern.compile("(?<=[.;!?])\\s+|\\t|\\u001E|\\n");

    private static final String NEG = "(?:NE\\s+(?:SONT|SERONT|EST|SERA|PEUVENT|PEUT)\\s+PAS|N'(?:EST|SERA)\\s+PAS|NON|NI)";
    private static final String MONTANT = "\\d{1,3}(?:[  .]\\d{3})+(?:,\\d+)?|\\d{5,}|\\d+(?:[.,]\\d+)?\\s*%";

    private static final List<Regle> REGLES = List.of(
            new Regle("variantes", "NON", Pattern.compile("\\bVARIANTES?\\b[^.;]{0,60}?\\b" + NEG + "\\s+(?:\\w+\\s+)?(?:AUTORISEE|ADMISE|ACCEPTEE"
                    + "|PERMISE|PRISES?\\s+EN\\s+(?:COMPTE|CONSIDERATION))|\\bAUCUNE\\s+VARIANTE|\\bPAS\\s+DE\\s+VARIANTE|\\bVARIANTES?\\s*:\\s*(?:NON|NEANT|SANS\\s+OBJET)\\b")),
            new Regle("variantes", "OUI", Pattern.compile("\\bVARIANTES?\\b(?:(?!\\bNE\\b|\\bN'|\\bNON\\b|\\bPAS\\b)[^.;]){0,40}?\\b(?:SONT|SERONT|EST|SERA)"
                    + "\\s+(?:\\w+\\s+)?(?:AUTORISEE|ADMISE|ACCEPTEE|PERMISE)|\\bVARIANTES?\\s*:\\s*(?:OUI|AUTORISEES?|ADMISES?)\\b")),
            new Regle("formeGroupement", "CONJOINT_OU_SOLIDAIRE", Pattern.compile("\\bGROUPEMENT\\s+(?:CONJOINT\\s+OU\\s+SOLIDAIRE|SOLIDAIRE\\s+OU\\s+CONJOINT)\\b"
                    + "|\\bFORME\\s+DU\\s+GROUPEMENT\\s*:\\s*(?:CONJOINT\\s+OU\\s+SOLIDAIRE|SOLIDAIRE\\s+OU\\s+CONJOINT)")),
            new Regle("formeGroupement", "SOLIDAIRE_OBLIGATOIRE", Pattern.compile("\\bGROUPEMENT\\b(?:(?!CONJOINT|\\bNE\\b|\\bN'|\\bPAS\\b)[^.;]){0,60}?\\b(?:DOIT|DEVRA|SERA|EST)"
                    + "\\s+(?:ETRE\\s+)?(?:OBLIGATOIREMENT\\s+)?(?:DE\\s+TYPE\\s+|SOUS\\s+(?:LA\\s+)?FORME\\s+(?:D'UN\\s+GROUPEMENT\\s+)?)?SOLIDAIRE\\b(?!\\s+OU)"
                    + "|\\bGROUPEMENT\\s+(?:OBLIGATOIREMENT\\s+)?SOLIDAIRE\\s+(?:EXIGE|OBLIGATOIRE|IMPOSE)|\\bFORME\\s+DU\\s+GROUPEMENT\\s*:\\s*SOLIDAIRE\\b(?!\\s+OU)")),
            // Une affirmation sur LES fournitures du marché, pas une règle de prix (« Pour les fournitures acquises sur le
            // territoire… », 2463) ni une taxe (« dus en douanes sur les fournitures importées », 303337).
            new Regle("provenance", "IMPORTEES", Pattern.compile("\\bINCOTERMS?\\b[^.;]{0,40}?\\b(?:CIP|CIF)\\b|\\bFOURNITURES?\\s+(?:SONT|SERONT)\\s+IMPORTEES\\b")),
            new Regle("provenance", "NATIONAL", Pattern.compile("\\bINCOTERMS?\\b[^.;]{0,40}?\\bEXW\\b|\\bFOURNITURES?\\s+(?:SONT|SERONT)\\s+"
                    + "(?:DISPONIBLES?|ACQUISES?|ACHETEES?)\\s+(?:SUR\\s+LE\\s+TERRITOIRE\\s+NATIONAL|A\\s+MADAGASCAR|LOCALEMENT)")),
            new Regle("prixRevisable", "NON", Pattern.compile("\\bPRIX\\b[^.;]{0,60}?\\b(?:FERMES?\\b(?!\\s+OU)|NON\\s+REVISABLES?|NON\\s+ACTUALISABLES?)")),
            new Regle("prixRevisable", "OUI", Pattern.compile("\\bPRIX\\b(?:(?!\\bNE\\b|\\bN'|\\bNON\\b|\\bPAS\\b|FERME)[^.;]){0,40}?\\b(?:SONT|SERONT)\\s+(?:\\w+\\s+)?"
                    + "(?:REVISABLES?|ACTUALISABLES?)\\b")),
            new Regle("garantieSoumission", "NON", Pattern.compile("\\bGARANTIE\\s+DE\\s+SOUMISSION\\b[^.;]{0,40}?(?::\\s*|\\b(?:EST|SERA)\\s+)(?:NEANT"
                    + "|NON\\b|SANS\\s+OBJET|PAS\\s+(?:EXIGEE|REQUISE|DEMANDEE))|\\b(?:AUCUNE|PAS\\s+DE)\\s+GARANTIE\\s+DE\\s+SOUMISSION"
                    + "|\\bGARANTIE\\s+DE\\s+SOUMISSION\\s+N'EST\\s+PAS\\s+(?:EXIGEE|REQUISE|DEMANDEE)")),
            new Regle("garantieSoumission", "OUI", Pattern.compile("\\b(?:GARANTIE|CAUTION)\\s+DE\\s+SOUMISSION\\b(?:(?!\\bNE\\b|\\bN'|\\bNON\\b|\\bPAS\\b|NEANT)[^.;]){0,400}?"
                    + "(?:" + MONTANT + ")|\\b(?:GARANTIE|CAUTION)\\s+DE\\s+SOUMISSION\\s+(?:DOIT|DEVRA)\\s+ETRE\\s+(?:FOURNIE|PRODUITE|JOINTE|PRESENTEE"
                    + "|CONSTITUEE)")),
            new Regle("avance", "NON", Pattern.compile("\\bAVANCE\\b[^.;]{0,30}?(?::\\s*|\\b(?:EST|SERA)\\s+)(?:NEANT|NON\\b|SANS\\s+OBJET"
                    + "|PAS\\s+(?:PREVUE|ACCORDEE|AUTORISEE))|\\b(?:AUCUNE|PAS\\s+D')\\s*AVANCE\\b|\\bIL\\s+N'EST\\s+PAS\\s+(?:PREVU|ACCORDE|CONSENTI)\\s+D'AVANCE"
                    + "|\\bAVANCE\\s+NON\\s+(?:PREVUE|ACCORDEE|AUTORISEE)")));

    private CadrageParClause() {
    }

    /** Les réponses lues pour les {@code cles} demandées, dans les parties admises de {@code paragraphes} (tels qu'écrits). */
    static Resultat lire(List<String> paragraphes, Set<String> cles) {
        Map<String, Map<String, Reponse>> trouvees = new LinkedHashMap<>();
        Zone zone = Zone.HORS;
        String partie = null;
        for (int i = 0; i < paragraphes.size(); i++) {
            String texte = paragraphes.get(i) == null ? "" : paragraphes.get(i);
            String c = IdentiteDao.cle(texte);
            if (c.length() <= LONGUEUR_TITRE) {
                if (TITRE_EXCLU.matcher(c).find()) {
                    zone = Zone.EXCLUE;
                    continue;
                }
                if (TITRE_ADMIS.matcher(c).find()) {
                    zone = Zone.ADMISE;
                    partie = texte.trim();
                    continue;
                }
            }
            if (zone != Zone.ADMISE) {
                continue;
            }
            // Un libellé seul (« Variantes : ») se lit avec le paragraphe qui le suit.
            String unite = texte;
            if (c.endsWith(":") && c.length() < 80 && i + 1 < paragraphes.size()) {
                unite = texte + " " + paragraphes.get(i + 1);
            }
            for (String phrase : PHRASES.split(unite)) {
                String p = IdentiteDao.cle(phrase);
                if (p.isEmpty() || GENERAL.matcher(p).find()) {
                    continue;
                }
                for (Regle r : REGLES) {
                    if (!cles.contains(r.cle())) {
                        continue;
                    }
                    Matcher m = r.motif().matcher(p);
                    if (!m.find()) {
                        continue;
                    }
                    // Une négation l'emporte sur le mot-clé d'une réponse positive.
                    if (("OUI".equals(r.valeur()) || "SOLIDAIRE_OBLIGATOIRE".equals(r.valeur())) && NEGATION.matcher(m.group()).find()) {
                        continue;
                    }
                    trouvees.computeIfAbsent(r.cle(), k -> new LinkedHashMap<>()).putIfAbsent(r.valeur(),
                            new Reponse(r.cle(), r.valeur(), extrait(phrase), i, partie));
                }
            }
        }
        List<Reponse> reponses = new ArrayList<>();
        Map<String, List<String>> conflits = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Reponse>> e : trouvees.entrySet()) {
            if (e.getValue().size() == 1) {
                reponses.add(e.getValue().values().iterator().next());
            } else {
                conflits.put(e.getKey(), List.copyOf(e.getValue().keySet()));
            }
        }
        return new Resultat(reponses, conflits);
    }

    /** Les clés que l'import cherche par clause pour une fiche : le contrat-cadre n'en pose que deux (§B2). */
    static Set<String> clesPour(String typeMarche, String categorie) {
        if ("CONTRAT_CADRE".equals(typeMarche)) {
            return CLES_CONTRAT_CADRE;
        }
        if ("PRESTATIONS_INTELLECTUELLES".equals(categorie)) {
            return Set.of("variantes", "formeGroupement", "prixRevisable", "garantieSoumission", "avance");
        }
        return Set.copyOf(CLES);
    }

    private static String extrait(String phrase) {
        String t = phrase.replaceAll("\\s+", " ").trim();
        return t.length() <= 200 ? t : t.substring(0, 200) + "…";
    }
}
