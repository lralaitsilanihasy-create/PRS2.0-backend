package cnm.prs.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cnm.prs.dto.ModelePvDto;

/**
 * ⚠️ 2026-10-22 (demande front « modeles-dao », D-ARMP) — les <strong>écarts</strong> d'une version d'un document du DAO avec son
 * document type : la référence est le fichier intégré (Q2), comparé ligne à ligne au fichier de commande que la version redonne
 * ({@link ConversionDao#enCommande}, la même écriture des deux côtés). Plus longue sous-suite commune ; un retrait suivi d'un ajout au
 * même endroit est une modification (avant / après). Les règles déclarées comptent comme des lignes. Pur.
 *
 * <p>⚠️ 2026-10-23 (demande front « ecarts-de-forme », B1 ; arbitrage du pilote) — chaque écart a une <strong>nature</strong> :
 * {@code FORME} (une correction d'orthographe, d'accord, de ponctuation ou de mise en page, sans validation juridique) ou
 * {@code FOND}. Voir {@link #nature}.</p>
 */
public final class EcartsDao {

    public static final String FORME = "FORME";
    public static final String FOND = "FOND";
    /** Un jeton du moteur ou un marqueur de passage : un mot à part, comparé à l'identique. */
    private static final Pattern JETON = Pattern.compile("\\{\\{[^}]*}}");
    private static final Pattern SEPARATEURS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern MARQUES = Pattern.compile("\\p{M}+");

    private EcartsDao() {
    }

    public static List<ModelePvDto.Ecart> ecarts(String reference, String version) {
        String[] a = lignes(reference);
        String[] b = lignes(version);
        int n = a.length;
        int m = b.length;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<ModelePvDto.Ecart> out = new ArrayList<>();
        List<String> retires = new ArrayList<>();
        List<String> ajoutes = new ArrayList<>();
        int i = 0;
        int j = 0;
        int rang = 0;
        while (i < n || j < m) {
            if (i < n && j < m && a[i].equals(b[j])) {
                rang = vider(out, retires, ajoutes, rang);
                i++;
                j++;
                rang++;
            } else if (j < m && (i == n || lcs[i][j + 1] >= lcs[i + 1][j])) {
                ajoutes.add(b[j++]);
            } else {
                retires.add(a[i++]);
            }
        }
        vider(out, retires, ajoutes, rang);
        return out;
    }

    /** Un groupe de retraits et d'ajouts consécutifs : modifications deux à deux, puis le reste. */
    private static int vider(List<ModelePvDto.Ecart> out, List<String> retires, List<String> ajoutes, int rang) {
        int k = 0;
        for (; k < Math.min(retires.size(), ajoutes.size()); k++) {
            out.add(new ModelePvDto.Ecart("MODIFIE", rang + k + 1, lisible(retires.get(k)), lisible(ajoutes.get(k)),
                    nature(retires.get(k), ajoutes.get(k))));
        }
        for (int r = k; r < retires.size(); r++) {
            out.add(new ModelePvDto.Ecart("RETIRE", rang + r + 1, lisible(retires.get(r)), null, FOND));
        }
        for (int r = k; r < ajoutes.size(); r++) {
            out.add(new ModelePvDto.Ecart("AJOUTE", rang + r + 1, null, lisible(ajoutes.get(r)), FOND));
        }
        int avance = Math.max(retires.size(), ajoutes.size());
        retires.clear();
        ajoutes.clear();
        return rang + avance;
    }

    /**
     * ⚠️ 2026-10-23 (« ecarts-de-forme », B1) — la nature d'une ligne modifiée ({@code avant} → {@code apres}, lignes du fichier) :
     * {@code FORME} quand les deux textes ont les mêmes mots recollés (casse, accents, ponctuation, espaces, tirets et apostrophes
     * ignorés : « ci - après » / « ci-après », « para fiscales » / « parafiscales »), ou le même nombre de mots dont chacun qui change
     * ne diffère que de deux lettres au plus (« Pariculières », « tombe » / « tombent »). Le style seul (PARA → CENTRE) est de la forme.
     * Toujours {@code FOND} : une règle modifiée ; un jeton ou un marqueur de passage changé (comparés à l'identique) ; un nombre
     * changé (« 30 » / « 60 », mais « 30ème » / « 30e » est de la forme) ; un mot de trois lettres ou moins qui change de plus d'une lettre (« ou » / « et ») ; un mot ajouté ou
     * retiré (Q1).
     */
    static String nature(String avant, String apres) {
        String[] x = typeEtTexte(avant);
        String[] y = typeEtTexte(apres);
        if ("CONDITION".equals(x[0]) || "CONDITION".equals(y[0])) {
            return FOND;
        }
        List<String> a = mots(x[1]);
        List<String> b = mots(y[1]);
        if (String.join("", a).equals(String.join("", b)) && jetons(a).equals(jetons(b))) {
            return FORME;
        }
        if (a.size() != b.size()) {
            return FOND;
        }
        for (int i = 0; i < a.size(); i++) {
            String u = a.get(i);
            String v = b.get(i);
            if (u.equals(v)) {
                continue;
            }
            // Un nombre garde ses chiffres (« 30 » / « 60 » : fond) ; les lettres autour suivent la règle (« 30ème » / « 30e » : forme).
            if (u.startsWith("{{") || v.startsWith("{{") || !u.replaceAll("\\D", "").equals(v.replaceAll("\\D", ""))) {
                return FOND;
            }
            // Un mot court (trois lettres ou moins, sans chiffre) ne tolère qu'une lettre : « ou » / « et » change le sens.
            boolean court = Math.min(u.length(), v.length()) <= 3 && !u.matches(".*\\d.*");
            int max = court ? 1 : 2;
            if (distance(u, v) > max) {
                return FOND;
            }
        }
        return FORME;
    }

    /** Les mots d'un texte : jetons tels quels ; le reste en minuscules, sans accents, coupé à la ponctuation, aux tirets, aux espaces. */
    static List<String> mots(String texte) {
        List<String> out = new ArrayList<>();
        Matcher m = JETON.matcher(texte);
        int pos = 0;
        while (m.find()) {
            normaliser(texte.substring(pos, m.start()), out);
            out.add(m.group());
            pos = m.end();
        }
        normaliser(texte.substring(pos), out);
        return out;
    }

    private static void normaliser(String texte, List<String> out) {
        String s = MARQUES.matcher(Normalizer.normalize(texte, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
        for (String mot : SEPARATEURS.split(s)) {
            if (!mot.isEmpty()) {
                out.add(mot);
            }
        }
    }

    private static List<String> jetons(List<String> mots) {
        return mots.stream().filter(w -> w.startsWith("{{")).toList();
    }

    static int distance(String a, String b) {
        int[] prec = new int[b.length() + 1];
        int[] cour = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prec[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cour[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cour[j] = Math.min(Math.min(cour[j - 1] + 1, prec[j] + 1), prec[j - 1] + c);
            }
            int[] t = prec;
            prec = cour;
            cour = t;
        }
        return prec[b.length()];
    }

    private static String[] typeEtTexte(String ligne) {
        int tab = ligne.indexOf('\t');
        return tab < 0 ? new String[] { ligne, "" } : new String[] { ligne.substring(0, tab), ligne.substring(tab + 1) };
    }

    /** Une ligne du fichier, lisible : le style puis le texte ; les séparateurs de cellule et de paragraphe en « | » et « ¶ ». */
    static String lisible(String ligne) {
        int tab = ligne.indexOf('\t');
        String type = tab < 0 ? ligne : ligne.substring(0, tab);
        String texte = tab < 0 ? "" : ligne.substring(tab + 1).replace('\u001F', '|').replace('\u001E', '¶');
        if ("CONDITION".equals(type)) {
            int sep = texte.indexOf('|');
            return "Règle " + (sep < 0 ? texte : texte.substring(0, sep) + " : " + texte.substring(sep + 1));
        }
        return texte.isEmpty() ? "[" + type + "]" : "[" + type + "] " + texte;
    }

    private static String[] lignes(String commande) {
        return Arrays.stream(commande.split("\r?\n")).filter(l -> !l.isEmpty()).toArray(String[]::new);
    }
}
