package cnm.prs.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * ⚠️ <strong>Import du DAO — lecture du PDF « texte »</strong> (demande front du 2026-09-29, §B5 règle 4 ; ADR-0012) —
 * portage tel quel de {@code frontendprs2/scripts/import-dao/PdfLignes.java} (les lignes et leur position) et de
 * {@code paragraphesPdf} de {@code lire.mjs} (les paragraphes refaits depuis ces positions). Pas d'OCR : un PDF sans
 * texte ne donne rien.
 *
 * <ol>
 *   <li><strong>Lignes</strong> : ce que PDFBox rend comme ligne ({@code setSortByPosition}), en ne gardant que le texte
 *       horizontal, non incliné et dans le cadre de la page — le filigrane nominatif du 2463 est posé par une matrice
 *       inclinée ou hors de la page ; une ligne est coupée là où l'abscisse saute de plus de trois espaces (deux colonnes).
 *       Positions arrondies au dixième de point, comme la sortie TSV du front.</li>
 *   <li>En-têtes et pieds (même texte au même endroit sur trois pages au moins) et numéros de page écartés.</li>
 *   <li>Morceaux d'une même ligne de base (à 1,5 pt), contigus, recollés.</li>
 *   <li>Paragraphes par colonne (abscisse 240 pt) : une ligne rejoint le paragraphe ouvert de sa colonne si elle le suit à
 *       interligne normal : l'interligne mesuré sur la page, plus un point (⚠️ 2026-09-29).</li>
 *   <li>Ordre de lecture d'une page : hauteur de début de paragraphe, puis colonne ; césure « mot- suite » recollée.</li>
 * </ol>
 */
public final class LecturePdf {

    private static final Pattern NUMERO = Pattern.compile("^\\d{1,3}$");
    private static final Pattern PAGE_N = Pattern.compile("^page\\s+\\d+(\\s+(sur|/)\\s+\\d+)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CESURE = Pattern.compile("([A-Za-z0-9_])- ([A-Za-z0-9_])");
    static final double ABSCISSE_COLONNE = 240;

    private LecturePdf() {
    }

    /** Un morceau de ligne : page, abscisse de début, ligne de base, abscisse de fin, hauteur, texte. */
    record Morceau(int page, double x, double y, double xFin, double h, String texte) {
    }

    /** Les paragraphes d'un PDF, normalisés, non vides ; vide si le PDF n'a pas de texte. */
    public static List<String> paragraphes(byte[] pdf) throws IOException {
        return paragraphes(morceaux(pdf));
    }

    /** ⚠️ Lot D4 (2026-09-30, §B6.3) — les mêmes paragraphes TELS QU'ÉCRITS (césures recollées, non normalisés). */
    public static List<String> paragraphesDOrigine(byte[] pdf) throws IOException {
        return paragraphesDOrigine(morceaux(pdf));
    }

    static List<String> paragraphes(List<Morceau> morceaux) {
        return paragraphesDOrigine(morceaux).stream().map(LectureDao::norm).toList();
    }

    // ------------------------------------------------------------------ PdfLignes

    static List<Morceau> morceaux(byte[] pdf) throws IOException {
        List<Morceau> out = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper s = new PDFTextStripper() {
                @Override
                protected void writeString(String texte, List<TextPosition> positions) {
                    float l = getCurrentPage().getMediaBox().getWidth();
                    float h = getCurrentPage().getMediaBox().getHeight();
                    List<TextPosition> droites = new ArrayList<>();
                    for (TextPosition p : positions) {
                        float x = p.getXDirAdj();
                        float y = p.getYDirAdj();
                        boolean incline = Math.abs(p.getTextMatrix().getShearY()) > 0.01f
                                || Math.abs(p.getTextMatrix().getShearX()) > 0.01f;
                        if (p.getDir() == 0f && !incline && x >= 0 && x <= l && y >= 0 && y <= h) {
                            droites.add(p);
                        }
                    }
                    ecrire(droites, getCurrentPageNo(), out);
                }
            };
            s.setSortByPosition(true);
            s.getText(doc);
        }
        return out;
    }

    private static void ecrire(List<TextPosition> ps, int page, List<Morceau> out) {
        if (ps.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        TextPosition debut = ps.get(0);
        TextPosition prec = null;
        for (TextPosition p : ps) {
            if (prec != null) {
                float espace = p.getXDirAdj() - (prec.getXDirAdj() + prec.getWidthDirAdj());
                float largeurEspace = Math.max(prec.getWidthOfSpace(), 1f);
                // ⚠️ 2026-09-29 (front a3217b3) — OpenPDF pose une espace au même endroit que la première lettre de la
                // ligne : triée par position, elle tombe juste après cette lettre (« M ARCHE DE »). Une espace qui commence
                // à l'intérieur de la lettre précédente ne sépare rien : ignorée.
                if (p.getUnicode().isBlank() && espace < -1f) {
                    continue;
                }
                if (espace > 3 * largeurEspace && espace > 12f) {
                    sortir(sb, debut, prec, page, out);
                    sb.setLength(0);
                    debut = p;
                } else if (espace > largeurEspace * 0.3f && !sb.isEmpty() && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
            }
            sb.append(p.getUnicode());
            prec = p;
        }
        sortir(sb, debut, prec, page, out);
    }

    private static void sortir(StringBuilder sb, TextPosition debut, TextPosition fin, int page, List<Morceau> out) {
        String t = sb.toString().replace('\t', ' ').strip();
        if (t.isEmpty()) {
            return;
        }
        out.add(new Morceau(page, dixieme(debut.getXDirAdj()), dixieme(debut.getYDirAdj()),
                dixieme(fin.getXDirAdj() + fin.getWidthDirAdj()), dixieme(debut.getHeightDir()), t));
    }

    /** Le nombre tel que la sortie TSV du front l'écrit ({@code %.1f}) puis le relit. */
    private static double dixieme(float v) {
        return Double.parseDouble(String.format(Locale.ROOT, "%.1f", v));
    }

    // ------------------------------------------------------------------ paragraphesPdf

    private static final class Ligne {
        final int page;
        final double x;
        final double y;
        double xFin;
        final double h;
        final StringBuilder texte;

        Ligne(Morceau m) {
            page = m.page();
            x = m.x();
            y = m.y();
            xFin = m.xFin();
            h = m.h();
            texte = new StringBuilder(m.texte());
        }
    }

    private static final class Paragraphe {
        final int colonne;
        final double y;
        double yDernier;
        final StringBuilder texte;

        Paragraphe(int colonne, double y, String texte) {
            this.colonne = colonne;
            this.y = y;
            this.yDernier = y;
            this.texte = new StringBuilder(texte);
        }
    }

    static List<String> paragraphesDOrigine(List<Morceau> morceaux) {
        // Les en-têtes et pieds de page (même texte au même endroit sur trois pages au moins) et les numéros de page seuls.
        Map<String, Set<Integer>> hautBas = new HashMap<>();
        for (Morceau m : morceaux) {
            hautBas.computeIfAbsent(cleHautBas(m), k -> new HashSet<>()).add(m.page());
        }
        Set<Integer> pages = new LinkedHashSet<>();
        morceaux.forEach(m -> pages.add(m.page()));
        List<String> sortie = new ArrayList<>();
        for (int page : pages) {
            List<Morceau> ms = new ArrayList<>();
            for (Morceau m : morceaux) {
                if (m.page() == page && hautBas.get(cleHautBas(m)).size() < 3 && !numeroDePage(m)) {
                    ms.add(m);
                }
            }
            ms.sort(Comparator.comparingDouble(Morceau::y).thenComparingDouble(Morceau::x));   // tri stable
            // Lignes de la page : morceaux d'une même ligne de base, contigus, recollés.
            List<Ligne> lignes = new ArrayList<>();
            for (Morceau m : ms) {
                Ligne l = null;
                for (Ligne q : lignes) {
                    if (Math.abs(q.y - m.y()) <= 1.5 && m.x() >= q.xFin - 1 && m.x() - q.xFin <= 3) {
                        l = q;
                        break;
                    }
                }
                if (l != null) {
                    l.texte.append(m.x() - l.xFin > 0.8 ? " " : "").append(m.texte());
                    l.xFin = m.xFin();
                } else {
                    lignes.add(new Ligne(m));
                }
            }
            lignes.sort(Comparator.<Ligne>comparingDouble(q -> q.y).thenComparingDouble(q -> q.x));
            // Paragraphes par colonne. ⚠️ 2026-09-29 (front a3217b3) — l'interligne se MESURE sur la page : le plus petit
            // écart vertical fréquent (au moins deux fois) entre deux lignes successives d'une même colonne, arrondi au
            // demi-point, 10 à défaut (9,7 pt dans le 2463 ; 15 pt dans nos PDF, où 18 pt sépare deux paragraphes).
            Map<Double, Integer> ecarts = new LinkedHashMap<>();
            for (int col = 0; col <= 1; col++) {
                List<Double> ys = new ArrayList<>();
                for (Ligne l : lignes) {
                    if ((l.x >= ABSCISSE_COLONNE ? 1 : 0) == col) {
                        ys.add(l.y);
                    }
                }
                for (int i = 1; i < ys.size(); i++) {
                    double e = Math.round((ys.get(i) - ys.get(i - 1)) * 2) / 2.0;
                    if (e >= 3) {
                        ecarts.merge(e, 1, Integer::sum);
                    }
                }
            }
            double interligne = ecarts.entrySet().stream().filter(x -> x.getValue() >= 2).map(Map.Entry::getKey)
                    .min(Double::compare).orElse(10.0);
            Map<Integer, Paragraphe> ouverts = new LinkedHashMap<>();
            List<Paragraphe> pars = new ArrayList<>();
            for (Ligne l : lignes) {
                int c = l.x >= ABSCISSE_COLONNE ? 1 : 0;
                Paragraphe o = ouverts.get(c);
                double pas = interligne + 1;
                if (o != null && l.y - o.yDernier > 0 && l.y - o.yDernier <= pas) {
                    o.texte.append(' ').append(l.texte);
                    o.yDernier = l.y;
                    continue;
                }
                Paragraphe p = new Paragraphe(c, l.y, l.texte.toString());
                pars.add(p);
                ouverts.put(c, p);
            }
            pars.sort(Comparator.<Paragraphe>comparingDouble(p -> p.y).thenComparingInt(p -> p.colonne));
            for (Paragraphe p : pars) {
                // ⚠️ §B6.3 — non normalisé : la reconnaissance normalise, une valeur de texte se reprend ici.
                String t = CESURE.matcher(p.texte.toString()).replaceAll("$1$2");
                if (!LectureDao.norm(t).isEmpty()) {
                    sortie.add(t);
                }
            }
        }
        return sortie;
    }

    private static String cleHautBas(Morceau m) {
        return Math.round(m.y() / 4) + "|" + LectureDao.norm(m.texte());
    }

    private static boolean numeroDePage(Morceau m) {
        String t = LectureDao.norm(m.texte());
        return NUMERO.matcher(t).matches() || PAGE_N.matcher(t).matches();
    }
}
