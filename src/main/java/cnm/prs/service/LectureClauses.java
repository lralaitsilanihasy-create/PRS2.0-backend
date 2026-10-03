package cnm.prs.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ⚠️ 2026-10-03 (demande front « lecture par clause », option A de la note de décision, retenue par le pilote) — la passe
 * <strong>par clause</strong>, portage de {@code scripts/import-dao/clauses.mjs} (front 444d509), premier temps.
 *
 * <p>La lecture par le modèle ({@link LectureDao}, règles 1 à 9) reste la source sûre, et la seule à donner la confiance
 * haute. Cette passe la COMPLÈTE quand un DAO s'écarte du document type :</p>
 * <ul>
 *   <li>elle ne cherche que dans la SECTION des données particulières (sinon les Instructions aux candidats, qui parlent
 *       des mêmes choses en général, prennent la place) ;</li>
 *   <li>chaque information du {@link #CATALOGUE} se repère par les mots de sa clause, et sa valeur par sa FORME (une durée,
 *       un montant) dans les quelques paragraphes qui suivent ;</li>
 *   <li>pour les listes (matériel, personnel, pièces), elle ne découpe rien : elle repère le PASSAGE, que l'écran propose
 *       dans « Coller une liste ».</li>
 * </ul>
 *
 * <p>Les expressions régulières reproduisent celles du front : {@code \s} y est la classe des blancs de JavaScript
 * ({@link LectureDao#BLANC}), {@code \b} une frontière ASCII, et la casse est ignorée en Unicode.</p>
 */
final class LectureClauses {

    /** Les blancs de JavaScript ({@code \s}), contenu de classe. */
    private static final String S = LectureDao.BLANC.substring(1, LectureDao.BLANC.length() - 1);
    private static final String WS = "[" + S + "]";
    /** La frontière de mot de JavaScript ({@code \b}) : ASCII. */
    private static final String B = "(?:(?<=[A-Za-z0-9_])(?![A-Za-z0-9_])|(?<![A-Za-z0-9_])(?=[A-Za-z0-9_]))";
    private static final int I = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** Un renseignement repéré : le code (éventuellement {@code CODE#n}), la valeur brute, le paragraphe du document. */
    record Trouvee(String code, String brut, int paragraphe, String info) {
    }

    /** Un passage de liste (MATERIEL, PERSONNEL, PIECES) à proposer dans « Coller une liste ». */
    public record Passage(String liste, String texte, int paragraphe) {
    }

    /** Le résultat de la passe ; {@code section} : indice où commence la section des données particulières, ou -1. */
    record Lecture(List<Trouvee> propositions, List<Passage> passages, int section) {
    }

    private record Section(int debut, List<String> paragraphes) {
    }

    private static final Pattern INTRO = Pattern.compile("donn[ée]es particuli[èe]res ci-apr[èe]s compl[èe]tent", I);
    private static final Pattern TITRE = Pattern.compile("^\\d+\\.\\d+\\.?" + WS + "*-" + WS + "*donn[ée]es particuli[èe]res", I);

    /** La section des données particulières : ses paragraphes (700 au plus), et l'indice où elle commence. */
    private static Section section(List<String> doc) {
        int debut = -1;
        for (int i = 0; i < doc.size() && debut < 0; i++) {
            if (INTRO.matcher(doc.get(i)).find()) {
                debut = i;
            }
        }
        for (int i = 0; i < doc.size() && debut < 0; i++) {
            if (TITRE.matcher(doc.get(i)).find()) {
                debut = i;
            }
        }
        return debut < 0 ? null : new Section(debut, doc.subList(debut, Math.min(doc.size(), debut + 700)));
    }

    // Devant le nombre entre parenthèses, seulement des MOTS DE NOMBRE (« CENT VINGT (120) », « Six (06) »).
    private static final String MOTS_NOMBRE = "(?:(?:un|une|deux|trois|quatre|cinq|six|sept|huit|neuf|dix|onze|douze|treize|"
            + "quatorze|quinze|seize|vingt|trente|quarante|cinquante|soixante|cent|cents|mille|et)[" + S + "-]+)*";
    private static final Pattern DUREE = Pattern.compile(B + MOTS_NOMBRE + "(?:\\(" + WS + "*\\d+" + WS + "*\\)|\\d+)" + WS
            + "*(?:jours?|mois)" + B, I);
    private static final String MONTANT_SOURCE = "\\d{1,3}(?:[" + S + ".]\\d{3})+(?:,\\d+)?|\\d{6,}";
    private static final Pattern MONTANT = Pattern.compile(MONTANT_SOURCE);
    private static final String POURCENT_SOURCE = "\\d+(?:[.,]\\d+)?" + WS + "*%";

    /** Une information du catalogue : codes par catégorie, ancre, forme de la valeur, fenêtre, groupe de la valeur. */
    private record Info(String info, Map<String, String> codes, Map<String, String> codesPourcent, Pattern ancre,
            Pattern forme, int n, int groupe) {
    }

    /** Le catalogue du premier temps : 5 informations simples (note de décision, §4). */
    static final List<Info> CATALOGUE = List.of(
            new Info("validite", Map.of("TRAVAUX", "B04-VO-01", "FOURNITURES_SERVICES", "B04-VO-01"), Map.of(),
                    Pattern.compile("validit[ée] des offres", I), DUREE, 3, 0),
            new Info("garantie", Map.of("TRAVAUX", "B05-GQ-03", "FOURNITURES_SERVICES", "B05-GS-03"), Map.of(),
                    Pattern.compile("garantie de soumission", I), MONTANT, 2, 0),
            new Info("delai", Map.of("TRAVAUX", "B09-DL-01"), Map.of(),
                    Pattern.compile("d[ée]lai d['’]ex[ée]cution", I), DUREE, 3, 0),
            new Info("liquidite", Map.of("TRAVAUX", "B03-QT-14"), Map.of("TRAVAUX", "B03-QT-15"),
                    Pattern.compile("liquidit[ée]|ligne de cr[ée]dit", I),
                    Pattern.compile(POURCENT_SOURCE + "|" + MONTANT_SOURCE), 2, 0),
            new Info("lieu", Map.of("TRAVAUX", "B04-OV-01", "FOURNITURES_SERVICES", "B04-OP-01"), Map.of(),
                    Pattern.compile("ouverture des plis", I),
                    Pattern.compile("(?:Lieu|Bureau)" + WS + "*:" + WS + "*([^.;]{6,80})", I), 3, 1));

    /** Un passage de liste : ses catégories, son ancre, sa fin. */
    private record DefPassage(String liste, Set<String> categories, Pattern ancre, Pattern fin) {
    }

    static final List<DefPassage> PASSAGES = List.of(
            new DefPassage("MATERIEL", Set.of("TRAVAUX"), Pattern.compile("(?:gros )?mat[ée]riels?[^:]{0,80}:", I),
                    Pattern.compile("^\\(?[d-f]\\)|personnel|directeur de travaux", I)),
            new DefPassage("PERSONNEL", Set.of("TRAVAUX"),
                    Pattern.compile("personnel (?:cl[ée]|par lot|minimum|suivant)[^:]{0,60}:", I),
                    Pattern.compile("^\\(?[e-g]\\)|liquidit|r[ée]f[ée]rence", I)),
            new DefPassage("PIECES", Set.of("TRAVAUX", "FOURNITURES_SERVICES"),
                    Pattern.compile("documents ou pi[èe]ces [àa] remettre en sus[^:]*:", I),
                    Pattern.compile("^6\\.3|capacit[ée]s|^4" + WS + "*°|^\\d+\\.\\d+\\.?" + WS, I)));

    private static final Pattern LOT_AVANT = Pattern.compile("lot" + WS + "*(?:n" + WS + "*°" + WS + "*)?(\\d+)" + WS + "*:" + WS
            + "*[^\\d]{0,120}?(\\d{1,3}(?:[" + S + ".]\\d{3})+|\\d{6,})", I);
    private static final Pattern LOT_APRES = Pattern.compile("(\\d{1,3}(?:[" + S + ".]\\d{3})+|\\d{6,})[^/;\\n\\d]{0,25}?(?:\\("
            + WS + "*|pour" + WS + "+le" + WS + "+)lot" + WS + "*(?:n" + WS + "*°" + WS + "*)?0*(\\d+)", I);

    private record Lot(int lot, String brut) {
    }

    /** Les montants « par lot » d'une même clause : « Lot n° 2 : … 2 170 000 » d'abord, à défaut « 9 900 000 Ar (lot 1) ». */
    private static List<Lot> parLot(String texte) {
        List<Lot> lots = new ArrayList<>();
        Matcher m = LOT_AVANT.matcher(texte);
        while (m.find()) {
            Integer n = entier(m.group(1));
            if (n != null) {
                lots.add(new Lot(n, m.group(2)));
            }
        }
        if (lots.size() < 2) {
            lots.clear();
            m = LOT_APRES.matcher(texte);
            while (m.find()) {
                Integer n = entier(m.group(2));
                if (n != null) {
                    lots.add(new Lot(n, m.group(1)));
                }
            }
        }
        Map<Integer, Lot> vus = new LinkedHashMap<>();
        for (Lot l : lots) {
            vus.putIfAbsent(l.lot(), l);
        }
        return vus.size() >= 2 ? vus.values().stream().sorted(Comparator.comparingInt(Lot::lot)).toList() : List.of();
    }

    private static Integer entier(String s) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int recherche(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.start() : -1;
    }

    /**
     * La passe par clause sur un document (ses paragraphes normalisés). {@code dejaTrouves} : les codes (nus) que la lecture
     * par le modèle a déjà proposés — la passe ne les remplace jamais. Rend des valeurs brutes, à convertir par l'appelant,
     * et des passages de listes.
     */
    static Lecture lire(List<String> doc, String categorie, Set<String> dejaTrouves) {
        Section section = section(doc);
        if (section == null) {
            return new Lecture(List.of(), List.of(), -1);
        }
        List<String> ps = section.paragraphes();
        List<Trouvee> propositions = new ArrayList<>();
        for (Info c : CATALOGUE) {
            String code = c.codes().get(categorie);
            String codePourcent = c.codesPourcent().get(categorie);
            if (code == null || dejaTrouves.contains(code) || (codePourcent != null && dejaTrouves.contains(codePourcent))) {
                continue;
            }
            trouve:
            for (int i = 0; i < ps.size(); i++) {
                if (!c.ancre().matcher(ps.get(i)).find()) {
                    continue;
                }
                for (int k = i; k <= Math.min(ps.size() - 1, i + c.n()); k++) {
                    String texte = k == i ? ps.get(k).substring(recherche(c.ancre(), ps.get(k))) : ps.get(k);
                    Matcher m = c.forme().matcher(texte);
                    if (!m.find()) {
                        continue;
                    }
                    int paragraphe = section.debut() + k;
                    List<Lot> lots = "garantie".equals(c.info()) || "liquidite".equals(c.info())
                            ? parLot(String.join(" ", ps.subList(i, Math.min(ps.size(), i + c.n() + 1)))) : List.of();
                    if (!lots.isEmpty()) {
                        for (Lot l : lots) {
                            propositions.add(new Trouvee(code + "#" + l.lot(), l.brut(), paragraphe, c.info()));
                        }
                    } else if (m.group().contains("%") && codePourcent != null) {
                        propositions.add(new Trouvee(codePourcent, m.group(), paragraphe, c.info()));
                    } else {
                        propositions.add(new Trouvee(code, LectureDao.trim(c.groupe() > 0 ? m.group(c.groupe()) : m.group()),
                                paragraphe, c.info()));
                    }
                    break trouve;
                }
            }
        }
        List<Passage> passages = new ArrayList<>();
        for (DefPassage p : PASSAGES) {
            if (!p.categories().contains(categorie)) {
                continue;
            }
            int i = -1;
            for (int x = 0; x < ps.size() && i < 0; x++) {
                if (p.ancre().matcher(ps.get(x)).find()) {
                    i = x;
                }
            }
            if (i < 0) {
                continue;
            }
            String depuis = ps.get(i).substring(recherche(p.ancre(), ps.get(i)));
            StringBuilder texte = new StringBuilder(LectureDao.trim(p.ancre().matcher(depuis).replaceFirst("")));
            for (int k = i + 1; k < Math.min(ps.size(), i + 25) && !p.fin().matcher(ps.get(k)).find(); k++) {
                texte.append('\n').append(ps.get(k));
            }
            String t = texte.toString();
            Matcher coupe = p.fin().matcher(t);
            if (coupe.find() && coupe.start() > 0) {
                t = t.substring(0, coupe.start());
            }
            t = LectureDao.trim(t);
            if (t.length() >= 10) {
                passages.add(new Passage(p.liste(), t, section.debut() + i));
            }
        }
        return new Lecture(propositions, passages, section.debut());
    }

    private LectureClauses() {
    }
}
