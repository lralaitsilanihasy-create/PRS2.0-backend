package cnm.prs.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cnm.prs.dto.ModelePvDto;

/**
 * ⚠️ 2026-10-22 (demande front « modeles-dao », D-ARMP) — les <strong>écarts</strong> d'une version d'un document du DAO avec son
 * document type : la référence est le fichier intégré (Q2), comparé ligne à ligne au fichier de commande que la version redonne
 * ({@link ConversionDao#enCommande}, la même écriture des deux côtés). Plus longue sous-suite commune ; un retrait suivi d'un ajout au
 * même endroit est une modification (avant / après). Les règles déclarées comptent comme des lignes. Pur.
 */
public final class EcartsDao {

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
            out.add(new ModelePvDto.Ecart("MODIFIE", rang + k + 1, lisible(retires.get(k)), lisible(ajoutes.get(k))));
        }
        for (int r = k; r < retires.size(); r++) {
            out.add(new ModelePvDto.Ecart("RETIRE", rang + r + 1, lisible(retires.get(r)), null));
        }
        for (int r = k; r < ajoutes.size(); r++) {
            out.add(new ModelePvDto.Ecart("AJOUTE", rang + r + 1, null, lisible(ajoutes.get(r))));
        }
        int avance = Math.max(retires.size(), ajoutes.size());
        retires.clear();
        ajoutes.clear();
        return rang + avance;
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
