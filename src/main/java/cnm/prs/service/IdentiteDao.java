package cnm.prs.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cnm.prs.dto.ImportDaoResult;

/**
 * ⚠️ 2026-10-17 (demande front « identite-dao-importe », §B1 ; arbitrages du pilote du 17/10) — le verdict d'identité d'un DAO
 * importé : est-il celui de la ligne du plan de la fiche ? Le contrôle ne lit pas de champ : il part des valeurs du plan, qui sont
 * sûres, et les <strong>cherche</strong> dans tout le texte, car un DAO venu d'ailleurs n'emploie pas nos libellés.
 *
 * <ul>
 *   <li><strong>Les critères</strong> (B1.1) : {@code OBJET}, {@code AUTORITE}, {@code MODE}, {@code FORME} (contrat-cadre), {@code LOTS}
 *       (ligne allotie). Le montant, l'imputation, les dates n'y entrent pas.</li>
 *   <li><strong>Retrouvé</strong> (B1.2) : les mots significatifs de la valeur du plan (ceux de {@code LectureTableaux.motsDe}, les mentions
 *       entre parenthèses ou crochets retirées) apparaissent dans l'ordre dans un même passage (un paragraphe et le suivant, car
 *       l'extraction coupe), à au plus un mot près quand la valeur en compte quatre ou plus. Deux mots se valent s'ils sont égaux, si
 *       l'un commence l'autre (cinq lettres au moins : « FOURNITUREET », collé par l'extraction) ou s'ils partagent sept lettres en
 *       tête (« ADMINISTRATIF », « ADMINISTRATIVE »).</li>
 *   <li><strong>Contredit</strong> (B1.3) exige un indice positif : l'objet de la garde (« Objet : … », lu dans les trois premières
 *       pages) ne ressemble pas à celui du plan, ou ressemble nettement plus à une autre ligne de la même autorité (B1.4) ; une autre
 *       autorité est nommée en tête et celle du plan n'apparaît nulle part ; le mode ou la forme divergent (règle actuelle) ; le DAO
 *       compte plus de lots que le plan, ou nomme un lot sans rapport.</li>
 *   <li><strong>Lignes voisines</strong> (B1.4) : la ressemblance d'une ligne est le coefficient de Dice de ses mots significatifs et de
 *       ceux de l'objet de la garde ; sans objet lu, 1 si son objet est retrouvé en tête du document, 0,85 à un mot près. Une voisine
 *       l'emporte <em>nettement</em> à {@value #NETTEMENT} de plus que la fiche et {@value #SEUIL} au moins ; elle ressemble
 *       <em>autant</em> sous cet écart. Les lots départagent une ligne allotie.</li>
 * </ul>
 */
final class IdentiteDao {

    static final String OBJET = "OBJET";
    static final String AUTORITE = "AUTORITE";
    static final String MODE = "MODE";
    static final String FORME = "FORME";
    static final String LOTS = "LOTS";
    static final String RETROUVE = "RETROUVE";
    static final String CONTREDIT = "CONTREDIT";
    static final String INTROUVABLE = "INTROUVABLE";
    static final String CONFORME = "CONFORME";
    static final String NON_PROUVE = "NON_PROUVE";
    static final String ETRANGER = "ETRANGER";

    /** Écart de ressemblance au-delà duquel une voisine l'emporte nettement. */
    static final double NETTEMENT = 0.2;
    /** Ressemblance minimale d'une voisine pour compter. */
    static final double SEUIL = 0.5;
    private static final double A_UN_MOT_PRES = 0.85;
    /** La tête du document : ses trois premières pages (PDF), à défaut ses 80 premiers paragraphes (Word). */
    private static final int PAGES_TETE = 3;
    private static final int PARAGRAPHES_TETE = 80;
    /** Mots du document sautés au plus entre deux mots retrouvés d'une valeur. */
    private static final int ECART_MOTS = 3;
    private static final int LONGUEUR_EXTRAIT = 200;

    private static final Pattern MOT_VIDE = Pattern.compile("^(DES|LES|POUR|AVEC|PAR|SUR|UNE)$");
    private static final Pattern NON_MOT = Pattern.compile("[^A-Z0-9 ]");
    private static final Pattern ENTRE_PARENTHESES = Pattern.compile("\\([^)]*\\)|\\[[^\\]]*\\]");
    private static final Pattern LIBELLE_OBJET = Pattern.compile("^OBJET\\s*(?:(?:DU|DE LA|DE L'|DES|DE)\\s*)?"
            + "(?:PRESENT\\s+)?(?:MARCHE|CONTRAT[- ]CADRE|CONTRAT|APPEL D'OFFRES|CONSULTATION|DOSSIER)?\\s*:\\s*(.*)$");
    private static final Pattern LIBELLE = Pattern.compile("^(?:-\\s*)?(?:LOT\\b|FINANCEMENT|IMPUTATION|COMPTE|COORDONNEES|LANCE|N\\s*°|DATE)");
    private static final Pattern LOT_NUMERO = Pattern.compile("\\bLOT\\s*(?:N\\s*°?\\s*)?0?(\\d{1,2})\\s*[:\\-–]");
    private static final Pattern LOT_DESIGNE = Pattern.compile("^-?\\s*LOT\\s*(?:N\\s*°?\\s*)?0?(\\d{1,2})\\s*[:\\-–]\\s*(.+)$");

    /** Ce que le plan dit de la ligne de la fiche ; {@code forme} nulle hors contrat-cadre, {@code lots} vides hors ligne allotie. */
    record Plan(String objet, List<String> autorites, List<String> sigles, String mode, String forme, int nbLots, List<String> lots) {
    }

    /** Une autre ligne des plans de la même autorité (exercice et exercice précédent). */
    record Voisine(Integer idDetail, String objet, List<String> lots) {
    }

    /** Le texte du document : ses paragraphes tels qu'écrits et, pour un PDF, la page de chacun (nul pour un Word). */
    record Texte(List<String> paragraphes, List<Integer> pages) {
    }

    /** Un passage du document : ses mots significatifs, son texte, sa page. */
    private record Passage(int indice, List<String> mots, String texte, Integer page) {
    }

    private record Trouve(Passage passage, boolean exact) {
    }

    private IdentiteDao() {
    }

    /**
     * Le verdict. {@code divergences} : les codes que la lecture a déjà vus diverger du plan ({@code B01-AC-13}, {@code B01-AC-19},
     * {@code typeMarche}) ; {@code autresAutorites} : les libellés et sigles des autres autorités du référentiel.
     */
    static ImportDaoResult.Identite juger(Texte texte, Plan plan, List<Voisine> voisines, List<String> autresAutorites,
            Set<String> divergences) {
        List<Passage> tous = passages(texte);
        List<Passage> tete = tete(tous, texte.pages() != null);
        List<ImportDaoResult.Critere> criteres = new ArrayList<>();
        List<String> raisons = new ArrayList<>();
        ImportDaoResult.Voisine[] voisine = new ImportDaoResult.Voisine[1];
        String[] autant = new String[1];

        criteres.add(objet(plan, voisines, tous, tete, raisons, voisine, autant));
        criteres.add(autorite(plan, autresAutorites, tous, tete, raisons));
        criteres.add(simple(MODE, plan.mode(), tous, tete, divergences.contains("B01-AC-13"), raisons,
                "il dit un autre mode de passation que le plan (« " + plan.mode() + " »)"));
        if (plan.forme() != null) {
            criteres.add(simple(FORME, plan.forme(), tous, tete,
                    divergences.contains("B01-AC-19") || divergences.contains(FormulairesCandidat.CLE_TYPE_MARCHE), raisons,
                    "il dit une autre forme de marché que le plan (« " + plan.forme() + " »)"));
        }
        if (LotsFiche.alloti(plan.nbLots())) {
            criteres.add(lots(plan, tous, tete, raisons));
        }

        boolean contredit = criteres.stream().anyMatch(c -> CONTREDIT.equals(c.etat()));
        boolean introuvable = criteres.stream().anyMatch(c -> INTROUVABLE.equals(c.etat()));
        String verdict = contredit ? ETRANGER : introuvable ? NON_PROUVE : CONFORME;
        String motif;
        if (contredit) {
            motif = "Ce DAO n'est pas celui de la ligne « " + plan.objet() + " » : " + String.join(" ; ", raisons) + ".";
        } else if (introuvable) {
            List<String> manquants = criteres.stream().filter(c -> INTROUVABLE.equals(c.etat())).map(c -> libelle(c.critere())).toList();
            motif = "Rien ne contredit le plan, mais " + String.join(", ", manquants) + (manquants.size() > 1 ? " n'ont" : " n'a")
                    + " pas été retrouvé" + (manquants.size() > 1 ? "s" : "") + " dans le document" + (autant[0] == null ? "" : " ; " + autant[0])
                    + " : la PRMP atteste que c'est le DAO de cette ligne.";
        } else {
            motif = "Le DAO porte " + String.join(", ", criteres.stream().map(c -> libelle(c.critere())).toList()) + " de la ligne du plan.";
        }
        return new ImportDaoResult.Identite(verdict, criteres, voisine[0], motif);
    }

    // ------------------------------------------------------------------ les critères

    private static ImportDaoResult.Critere objet(Plan plan, List<Voisine> voisines, List<Passage> tous, List<Passage> tete,
            List<String> raisons, ImportDaoResult.Voisine[] voisine, String[] autant) {
        Trouve trouve = chercher(mots(sansParentheses(plan.objet())), tous, tete);
        String[] lu = objetLu(tete);
        List<String> motsLus = lu == null ? List.of() : mots(sansParentheses(lu[0]));
        // La ressemblance se mesure sur l'objet de la garde quand il est lu ; à défaut, sur la tête du document (fiche et voisines
        // à égalité : une recherche dans tout le texte avantagerait la fiche d'un passage de hasard).
        double sFiche = ressemblance(plan.objet(), motsLus, tete);
        Voisine meilleure = null;
        double sVoisine = 0;
        for (Voisine v : voisines) {
            double s = ressemblance(v.objet(), motsLus, tete);
            if (s > sVoisine) {
                sVoisine = s;
                meilleure = v;
            }
        }
        // B1.4 : les lots départagent une ligne allotie avant de conclure.
        if (meilleure != null && sVoisine >= SEUIL && Math.abs(sVoisine - sFiche) < NETTEMENT + 1e-9 && LotsFiche.alloti(plan.nbLots())) {
            int f = lotsRetrouves(plan.lots(), tous);
            int v = lotsRetrouves(meilleure.lots(), tous);
            if (f > v) {
                sVoisine = 0;
            } else if (v > f) {
                sVoisine = sFiche + NETTEMENT + 0.01;
            }
        }
        String extraitLu = lu == null ? null : lu[1];
        Integer pageLue = lu == null || Integer.parseInt(lu[2]) < 0 ? null : Integer.valueOf(lu[2]);
        if (meilleure != null && sVoisine >= SEUIL && sVoisine >= sFiche + NETTEMENT - 1e-9) {
            voisine[0] = new ImportDaoResult.Voisine(meilleure.idDetail(), meilleure.objet());
            raisons.add("son objet est celui de la ligne " + meilleure.idDetail() + " « " + meilleure.objet() + " »");
            return new ImportDaoResult.Critere(OBJET, plan.objet(), CONTREDIT, extraitLu, pageLue);
        }
        if (lu != null && sFiche < SEUIL && trouve == null && LectureTableaux.ecart(sansParentheses(plan.objet()), sansParentheses(lu[0]))) {
            raisons.add("son objet (« " + lu[0].trim() + " ») ne ressemble pas à celui du plan");
            return new ImportDaoResult.Critere(OBJET, plan.objet(), CONTREDIT, extraitLu, pageLue);
        }
        if (meilleure != null && sVoisine >= SEUIL && sVoisine > sFiche - NETTEMENT) {
            voisine[0] = new ImportDaoResult.Voisine(meilleure.idDetail(), meilleure.objet());
            autant[0] = "la ligne " + meilleure.idDetail() + " « " + meilleure.objet() + " » lui ressemble autant";
            return new ImportDaoResult.Critere(OBJET, plan.objet(), INTROUVABLE, extraitLu, pageLue);
        }
        if (trouve != null) {
            return critere(OBJET, plan.objet(), RETROUVE, trouve.passage());
        }
        return new ImportDaoResult.Critere(OBJET, plan.objet(), INTROUVABLE, extraitLu, pageLue);
    }

    private static ImportDaoResult.Critere autorite(Plan plan, List<String> autres, List<Passage> tous, List<Passage> tete,
            List<String> raisons) {
        String nom = plan.autorites().isEmpty() ? null : plan.autorites().get(0);
        for (String a : plan.autorites()) {
            Trouve t = chercher(mots(a), tous, tete);
            if (t != null) {
                return critere(AUTORITE, nom, RETROUVE, t.passage());
            }
        }
        for (String s : plan.sigles()) {
            Passage p = sigle(s, tous, tete);
            if (p != null) {
                return critere(AUTORITE, nom, RETROUVE, p);
            }
        }
        // B1.3 : une autre autorité nommée en tête, et celle du plan nulle part.
        Set<String> propres = new LinkedHashSet<>();
        plan.autorites().forEach(a -> propres.addAll(mots(a)));
        for (String autre : autres) {
            List<String> m = mots(autre);
            Passage p = null;
            if (m.size() >= 3 && !propres.containsAll(m)) {
                Trouve t = chercher(m, tete, tete);
                p = t != null && t.exact() ? t.passage() : null;
            } else if (m.size() == 1 && autre.trim().length() >= 3 && !autre.trim().contains(" ")) {
                p = sigle(autre.trim(), tete, tete);
            }
            if (p != null) {
                raisons.add("il nomme une autre autorité contractante (« " + autre.trim() + " »)");
                return critere(AUTORITE, nom, CONTREDIT, p);
            }
        }
        return new ImportDaoResult.Critere(AUTORITE, nom, INTROUVABLE, null, null);
    }

    private static ImportDaoResult.Critere simple(String critere, String valeur, List<Passage> tous, List<Passage> tete, boolean diverge,
            List<String> raisons, String raison) {
        if (diverge) {
            raisons.add(raison);
            return new ImportDaoResult.Critere(critere, valeur, CONTREDIT, null, null);
        }
        Trouve t = valeur == null ? null : chercher(mots(valeur), tous, tete);
        return t == null ? new ImportDaoResult.Critere(critere, valeur, INTROUVABLE, null, null) : critere(critere, valeur, RETROUVE, t.passage());
    }

    private static ImportDaoResult.Critere lots(Plan plan, List<Passage> tous, List<Passage> tete, List<String> raisons) {
        String valeur = plan.nbLots() + " lots : " + String.join(" ; ", plan.lots());
        // B1.3 : plus de lots que le plan, ou une désignation de lot sans rapport.
        int max = 0;
        Passage auMax = null;
        for (Passage p : tete) {
            Matcher m = LOT_NUMERO.matcher(cle(p.texte()));
            while (m.find()) {
                int k = Integer.parseInt(m.group(1));
                if (k > max) {
                    max = k;
                    auMax = p;
                }
            }
        }
        if (max > plan.nbLots()) {
            raisons.add("il compte " + max + " lots, le plan " + plan.nbLots());
            return critere(LOTS, valeur, CONTREDIT, auMax);
        }
        for (Passage p : tete) {
            Matcher m = LOT_DESIGNE.matcher(cle(p.texte()));
            if (!m.matches()) {
                continue;
            }
            int k = Integer.parseInt(m.group(1));
            String designation = m.group(2);
            if (k >= 1 && k <= plan.lots().size() && !designation.isBlank() && !LOT_NUMERO.matcher(designation).find()) {
                String duPlan = plan.lots().get(k - 1);
                if (chercher(mots(sansParentheses(duPlan)), tous, tete) == null
                        && LectureTableaux.ecart(sansParentheses(duPlan), sansParentheses(designation))) {
                    raisons.add("son lot " + k + " (« " + designation.trim() + " ») est sans rapport avec celui du plan (« " + duPlan + " »)");
                    return critere(LOTS, valeur, CONTREDIT, p);
                }
            }
        }
        int retrouves = lotsRetrouves(plan.lots(), tous);
        if (retrouves == plan.lots().size() && !plan.lots().isEmpty()) {
            Trouve premier = chercher(mots(sansParentheses(plan.lots().get(0))), tous, tete);
            return critere(LOTS, valeur, RETROUVE, premier == null ? null : premier.passage());
        }
        return new ImportDaoResult.Critere(LOTS, valeur, INTROUVABLE, null, null);
    }

    private static int lotsRetrouves(List<String> lots, List<Passage> tous) {
        int n = 0;
        for (String l : lots) {
            if (chercher(mots(sansParentheses(l)), tous, tous) != null) {
                n++;
            }
        }
        return n;
    }

    private static String libelle(String critere) {
        return switch (critere) {
            case OBJET -> "l'objet";
            case AUTORITE -> "l'autorité contractante";
            case MODE -> "le mode de passation";
            case FORME -> "la forme du marché";
            default -> "les lots";
        };
    }

    // ------------------------------------------------------------------ la recherche

    private static List<Passage> passages(Texte t) {
        List<Passage> out = new ArrayList<>();
        for (int i = 0; i < t.paragraphes().size(); i++) {
            String p = t.paragraphes().get(i);
            Integer page = t.pages() == null || i >= t.pages().size() ? null : t.pages().get(i);
            out.add(new Passage(i, mots(p), p, page));
        }
        return out;
    }

    private static List<Passage> tete(List<Passage> tous, boolean pdf) {
        return pdf ? tous.stream().filter(p -> p.page() != null && p.page() <= PAGES_TETE).toList()
                : tous.subList(0, Math.min(PARAGRAPHES_TETE, tous.size()));
    }

    /**
     * La valeur retrouvée : en tête d'abord, puis dans {@code ou} ; {@code exact} si tous ses mots y sont. Un passage est un paragraphe
     * suivi du suivant (l'extraction coupe un objet sur deux lignes) ; l'extrait est le paragraphe où la valeur commence.
     */
    private static Trouve chercher(List<String> valeur, List<Passage> ou, List<Passage> tete) {
        if (valeur.isEmpty()) {
            return null;
        }
        int tolerance = valeur.size() >= 4 ? 1 : 0;
        Trouve approche = null;
        for (List<Passage> liste : List.of(tete, ou)) {
            for (int i = 0; i < liste.size(); i++) {
                Passage p = liste.get(i);
                List<String> fenetre = new ArrayList<>(p.mots());
                Passage suivant = i + 1 < liste.size() && liste.get(i + 1).indice() == p.indice() + 1 ? liste.get(i + 1) : null;
                if (suivant != null) {
                    fenetre.addAll(suivant.mots());
                }
                int[] r = manquants(valeur, fenetre, tolerance);
                Passage debut = suivant != null && r[1] >= p.mots().size() ? suivant : p;
                if (r[0] == 0) {
                    return new Trouve(debut, true);
                }
                if (r[0] > 0 && approche == null) {
                    approche = new Trouve(debut, false);
                }
            }
            if (approche != null) {
                return approche;
            }
        }
        return null;
    }

    /**
     * Le nombre de mots de {@code valeur} manquants dans {@code fenetre}, dans l'ordre (-1 au-delà de la tolérance), et la position du
     * premier mot retrouvé.
     */
    private static int[] manquants(List<String> valeur, List<String> fenetre, int tolerance) {
        int meilleur = -1;
        int position = -1;
        for (int debut = 0; debut <= tolerance && debut < valeur.size(); debut++) {
            for (int s = 0; s < fenetre.size(); s++) {
                if (!egal(valeur.get(debut), fenetre.get(s))) {
                    continue;
                }
                int manque = debut;
                int j = s + 1;
                for (int k = debut + 1; k < valeur.size() && manque <= tolerance; k++) {
                    int trouve = -1;
                    for (int x = j; x < Math.min(fenetre.size(), j + ECART_MOTS + 1); x++) {
                        if (egal(valeur.get(k), fenetre.get(x))) {
                            trouve = x;
                            break;
                        }
                    }
                    if (trouve < 0) {
                        manque++;
                    } else {
                        j = trouve + 1;
                    }
                }
                if (manque <= tolerance && (meilleur < 0 || manque < meilleur)) {
                    meilleur = manque;
                    position = s;
                    if (meilleur == 0) {
                        return new int[] { 0, position };
                    }
                }
            }
        }
        return new int[] { meilleur, position };
    }

    private static Passage sigle(String sigle, List<Passage> ou, List<Passage> tete) {
        String s = cle(sigle);
        if (s.length() < 2) {
            return null;
        }
        Pattern p = Pattern.compile("(?<![A-Z0-9])" + Pattern.quote(s) + "(?![A-Z0-9])");
        for (List<Passage> liste : List.of(tete, ou)) {
            for (Passage x : liste) {
                if (p.matcher(cle(x.texte())).find()) {
                    return x;
                }
            }
        }
        return null;
    }

    /** L'objet de la garde (« Objet : … », « OBJET DU CONTRAT-CADRE : … »), sur la ligne ou la suivante : texte, extrait, page (-1). */
    private static String[] objetLu(List<Passage> tete) {
        for (int i = 0; i < tete.size(); i++) {
            Matcher m = LIBELLE_OBJET.matcher(cle(tete.get(i).texte()));
            if (!m.matches()) {
                continue;
            }
            Passage debut = tete.get(i);
            String texte = debut.texte().substring(Math.max(0, debut.texte().indexOf(':') + 1)).trim();
            int j = i + 1;
            if (texte.isEmpty() && j < tete.size()) {
                texte = tete.get(j).texte().trim();
                j++;
            }
            if (j < tete.size() && suite(texte, tete.get(j).texte())) {
                texte = texte + " " + tete.get(j).texte().trim();
            }
            if (mots(sansParentheses(texte)).isEmpty()) {
                continue;
            }
            return new String[] { texte, extrait(texte), String.valueOf(debut.page() == null ? -1 : debut.page()) };
        }
        return null;
    }

    /** Le paragraphe suivant continue l'objet : il commence par une minuscule, ou il est court et l'objet ne finit pas sa phrase. */
    private static boolean suite(String objet, String suivant) {
        String s = suivant.trim();
        if (s.isEmpty() || objet.isEmpty()) {
            return false;
        }
        if (Character.isLowerCase(s.charAt(0))) {
            return true;
        }
        char fin = objet.charAt(objet.length() - 1);
        return s.length() < 40 && ".:;)]".indexOf(fin) < 0 && s.indexOf(':') < 0 && !LIBELLE.matcher(cle(s)).find();
    }

    private static double ressemblance(String objetLigne, List<String> motsLus, List<Passage> tete) {
        List<String> m = mots(sansParentheses(objetLigne));
        if (!motsLus.isEmpty()) {
            return dice(m, motsLus);
        }
        Trouve t = chercher(m, tete, tete);
        return t == null ? 0 : t.exact() ? 1 : A_UN_MOT_PRES;
    }

    private static double dice(List<String> a, List<String> b) {
        List<String> ua = uniques(a);
        List<String> ub = uniques(b);
        if (ua.isEmpty() || ub.isEmpty()) {
            return 0;
        }
        int communs = 0;
        for (String x : ua) {
            if (ub.stream().anyMatch(y -> egal(x, y))) {
                communs++;
            }
        }
        return 2.0 * communs / (ua.size() + ub.size());
    }

    private static List<String> uniques(List<String> mots) {
        List<String> out = new ArrayList<>();
        for (String m : mots) {
            if (out.stream().noneMatch(x -> egal(x, m))) {
                out.add(m);
            }
        }
        return out;
    }

    static boolean egal(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        String court = a.length() <= b.length() ? a : b;
        String long_ = court == a ? b : a;
        if (court.length() >= 5 && long_.startsWith(court)) {
            return true;
        }
        int k = 0;
        while (k < court.length() && a.charAt(k) == b.charAt(k)) {
            k++;
        }
        return k >= 7;
    }

    /** Les mots significatifs, comme {@code motsDe} du prototype : plus de deux lettres, hors mots vides, sans « S » final. */
    static List<String> mots(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        for (String w : NON_MOT.matcher(cle(s)).replaceAll(" ").split(" ")) {
            if (w.length() > 2 && !MOT_VIDE.matcher(w).matches()) {
                out.add(w.endsWith("S") ? w.substring(0, w.length() - 1) : w);
            }
        }
        return out;
    }

    /** Sans accents, en capitales, apostrophes et tirets typographiques ramenés, blancs réduits. */
    static String cle(String s) {
        String t = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKD).replaceAll("[\\u0300-\\u036f]", "")
                .replaceAll("[’ʼ‘`]", "'").replaceAll("[‐‑‒–—]", "-").toUpperCase(Locale.ROOT);
        return t.replaceAll("\\s+", " ").trim();
    }

    static String sansParentheses(String s) {
        return s == null ? "" : ENTRE_PARENTHESES.matcher(s).replaceAll(" ");
    }

    private static ImportDaoResult.Critere critere(String critere, String plan, String etat, Passage p) {
        return new ImportDaoResult.Critere(critere, plan, etat, p == null ? null : extrait(p.texte()), p == null ? null : p.page());
    }

    private static String extrait(String texte) {
        String t = texte.replaceAll("\\s+", " ").trim();
        return t.length() <= LONGUEUR_EXTRAIT ? t : t.substring(0, LONGUEUR_EXTRAIT) + "…";
    }
}
