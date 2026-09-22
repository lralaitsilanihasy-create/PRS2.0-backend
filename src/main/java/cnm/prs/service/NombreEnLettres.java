package cnm.prs.service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Conversion de nombres entiers en toutes lettres (français) et d'une date en toutes lettres.
 * Couvre 0 aux milliards (⚠️ 2026-09-22 : étendu au-delà de 999 999 pour les montants). Règles d'accord
 * appliquées : « vingt et un », « quatre-vingts » (s seulement si exact), « cent(s) », « mille »
 * invariable.
 */
public final class NombreEnLettres {

    private NombreEnLettres() {
    }

    private static final String[] UNITES = {
            "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
            "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf"
    };
    private static final String[] DIZAINES = {
            "", "", "vingt", "trente", "quarante", "cinquante", "soixante", "", "quatre-vingt", ""
    };

    /**
     * Date pour la formule juridique « L'an … » du PV : <strong>année en toutes lettres</strong> + « et le »
     * + <strong>jour</strong> (en toutes lettres, 1er → « premier ») + <strong>mois</strong>.
     * Ex. {@code 23/06/2026 → "deux mille vingt-six et le vingt-trois juin"}.
     */
    public static String dateExamenPourLAn(LocalDate date) {
        if (date == null) {
            return "";
        }
        return anneeEnLettres(date.getYear()) + " et le " + jourMoisEnLettres(date);
    }

    /** Année en toutes lettres (orthographe standard « mille » pour 2000+), ex. {@code 2026 → "deux mille vingt-six"}. */
    public static String anneeEnLettres(int annee) {
        return cardinal(annee);
    }

    /** Jour (en toutes lettres, 1er → « premier ») + nom du mois, ex. {@code 23/06 → "vingt-trois juin"}. */
    public static String jourMoisEnLettres(LocalDate date) {
        String jour = date.getDayOfMonth() == 1 ? "premier" : cardinal(date.getDayOfMonth());
        String mois = date.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH);
        return jour + " " + mois;
    }

    /**
     * Entier en toutes lettres.
     *
     * <p>⚠️ Fiche marché DAO (demande front du 2026-09-22, §B3) — étendu de 999 999 aux <strong>millions et
     * milliards</strong> (les montants de marché en Ariary dépassent couramment le milliard) : « huit millions quatre
     * cent mille », « un milliard deux cents millions ». Million et milliard sont des noms : ils s'accordent
     * (« deux millions ») et le multiplicateur garde son « s » (« deux cents millions ») ; « mille » reste invariable.
     * L'extension vaut pour tous les consommateurs (PV, lettres).</p>
     */
    public static String cardinal(long n) {
        if (n < 0) {
            return "moins " + cardinal(-n);
        }
        if (n >= 1_000_000L) {
            long milliards = n / 1_000_000_000L;
            long millions = (n / 1_000_000L) % 1000;
            long reste = n % 1_000_000L;
            StringBuilder sb = new StringBuilder();
            if (milliards > 0) {
                sb.append(cardinal(milliards)).append(milliards > 1 ? " milliards" : " milliard");
            }
            if (millions > 0) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(centaines((int) millions)).append(millions > 1 ? " millions" : " million");
            }
            if (reste > 0) {
                sb.append(' ').append(cardinal(reste));
            }
            return sb.toString();
        }
        if (n < 1000) {
            return centaines((int) n);
        }
        int milliers = (int) (n / 1000);
        int reste = (int) (n % 1000);
        String tete = milliers == 1 ? "mille" : centaines(milliers, false) + " mille";   // « quatre cent mille » : pas de « s » devant mille
        return reste == 0 ? tete : tete + " " + centaines(reste);
    }

    /** 0 à 999. */
    private static String centaines(int n) {
        return centaines(n, true);
    }

    /** 0 à 999 ; {@code terminal} faux quand un multiplicateur suit (« quatre cent mille », sans « s »). */
    private static String centaines(int n, boolean terminal) {
        int c = n / 100;
        int r = n % 100;
        if (c == 0) {
            return deux(r);
        }
        String tete = c == 1 ? "cent" : UNITES[c] + " cent";
        if (r == 0) {
            return c > 1 && terminal ? tete + "s" : tete;   // « deux cents », mais « cent » et « deux cent mille »
        }
        String texte = tete + " " + deux(r);
        return !terminal && texte.endsWith("vingts") ? texte.substring(0, texte.length() - 1) : texte;   // « quatre-vingt mille »
    }

    /** 0 à 99. */
    private static String deux(int n) {
        if (n < 20) {
            return UNITES[n];
        }
        int d = n / 10;
        int u = n % 10;
        if (d == 7 || d == 9) {                 // 70-79 / 90-99 : base 60 / 80 + (10..19)
            String base = d == 7 ? "soixante" : "quatre-vingt";
            if (d == 7 && u == 1) {
                return "soixante et onze";
            }
            return base + "-" + UNITES[10 + u];
        }
        if (u == 0) {
            return d == 8 ? "quatre-vingts" : DIZAINES[d];   // « quatre-vingts » (s si exact)
        }
        if (u == 1 && d != 8) {
            return DIZAINES[d] + " et un";      // « vingt et un »… (mais « quatre-vingt-un »)
        }
        return DIZAINES[d] + "-" + UNITES[u];
    }
}
