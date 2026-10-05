package cnm.prs.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * ⚠️ 2026-10-05 (lot 5, §B2.1) — <strong>relire un prix écrit en lettres</strong> (« deux millions quatre cent cinquante mille »)
 * pour le confronter aux chiffres à l'ouverture des plis ({@code LETTRES_DIVERGENTES}). Tolérant aux deux orthographes (règles
 * traditionnelles ou de 1990 : traits d'union, « cents » / « cent », « vingts » / « vingt »), aux majuscules et aux accents ; la
 * partie décimale suit « virgule » (« … virgule cinquante »). Rend {@code null} pour un texte qui ne se lit pas : jamais d'alerte
 * sur un texte illisible pour le serveur — la commission le lit.
 */
public final class LettresEnNombre {

    private static final Map<String, Integer> UNITES = Map.ofEntries(Map.entry("zero", 0), Map.entry("un", 1), Map.entry("une", 1),
            Map.entry("deux", 2), Map.entry("trois", 3), Map.entry("quatre", 4), Map.entry("cinq", 5), Map.entry("six", 6),
            Map.entry("sept", 7), Map.entry("huit", 8), Map.entry("neuf", 9), Map.entry("dix", 10), Map.entry("onze", 11),
            Map.entry("douze", 12), Map.entry("treize", 13), Map.entry("quatorze", 14), Map.entry("quinze", 15), Map.entry("seize", 16),
            Map.entry("vingt", 20), Map.entry("vingts", 20), Map.entry("trente", 30), Map.entry("quarante", 40),
            Map.entry("cinquante", 50), Map.entry("soixante", 60), Map.entry("septante", 70), Map.entry("huitante", 80),
            Map.entry("octante", 80), Map.entry("nonante", 90));

    private LettresEnNombre() {
    }

    /** Le nombre écrit en lettres, {@code null} s'il ne se lit pas. */
    public static BigDecimal lire(String texte) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        String t = Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("ariary|francs?|\\bar\\b", " ").replaceAll("[-,.;:'’]", " ").trim();
        String[] parties = t.split("\\bvirgule\\b");
        if (parties.length > 2) {
            return null;
        }
        Long entier = entier(parties[0]);
        if (entier == null) {
            return null;
        }
        BigDecimal n = BigDecimal.valueOf(entier);
        if (parties.length == 2) {
            String dec = parties[1].trim();
            Long d = entier(dec);
            if (d == null) {
                return null;
            }
            // « virgule cinq » = 0,5 ; « virgule cinquante » = 0,50 : la partie décimale est lue telle qu'écrite (au centième).
            n = n.add(new BigDecimal(d).movePointLeft(d < 10 && !dec.startsWith("zero") ? 1 : 2));
        }
        return n;
    }

    /** La partie entière ; {@code null} pour un mot inconnu ou un texte vide. */
    private static Long entier(String texte) {
        String[] mots = texte.trim().split("\\s+");
        if (mots.length == 0 || mots[0].isEmpty()) {
            return null;
        }
        long total = 0;      // milliards et millions déjà fermés
        long milliers = 0;   // le groupe en cours, avant « mille »
        long groupe = 0;     // les centaines, dizaines et unités en cours
        boolean lu = false;
        for (String m : mots) {
            if (m.equals("et")) {
                continue;
            }
            Integer u = UNITES.get(m);
            if (u != null) {
                // « quatre vingt » = 80 : quatre puis vingt multiplie.
                if (u == 20 && groupe % 100 == 4) {
                    groupe = groupe - 4 + 80;
                } else {
                    groupe += u;
                }
                lu = true;
            } else if (m.equals("cent") || m.equals("cents")) {
                groupe = (groupe == 0 ? 1 : groupe) * 100;
                lu = true;
            } else if (m.equals("mille")) {
                milliers += (groupe == 0 ? 1 : groupe) * 1000;
                groupe = 0;
                lu = true;
            } else if (m.equals("million") || m.equals("millions")) {
                total += (milliers + groupe == 0 ? 1 : milliers + groupe) * 1_000_000L;
                milliers = 0;
                groupe = 0;
                lu = true;
            } else if (m.equals("milliard") || m.equals("milliards")) {
                total = (total + milliers + groupe == 0 ? 1 : total + milliers + groupe) * 1_000_000_000L;
                milliers = 0;
                groupe = 0;
                lu = true;
            } else {
                return null;
            }
        }
        return lu ? total + milliers + groupe : null;
    }
}
