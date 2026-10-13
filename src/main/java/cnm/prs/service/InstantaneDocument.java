package cnm.prs.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * ⚠️ R4 (manuel révisé pour l'application, lot 1, §B5 ; 2026-10-13) — l'export PDF d'un instantané : le JSON figé rendu en
 * document libre ({@link DocumentLibre}, moteur docx/pdf des pièces), pour l'archivage avec le PV. Rendu générique : un objet
 * donne ses champs « libellé : valeur » (sous-titre par objet imbriqué), une liste de valeurs une énumération, une liste d'objets
 * un tableau de leurs champs simples (les champs composés des éléments suivent, un par un). Les clés sont rendues en mots
 * (« designationMarche » → « Designation marche »).
 */
final class InstantaneDocument {

    static final String TYPE = "INSTANTANE";
    private static final int MAX_COLONNES = 8;

    private InstantaneDocument() {
    }

    static DocumentLibre rendre(String titre, String sousTitre, JsonNode racine, String pied) {
        List<DocumentLibre.Element> els = new ArrayList<>();
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, titre));
        if (sousTitre != null && !sousTitre.isBlank()) {
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, sousTitre));
        }
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        rendre(els, null, racine, 0);
        return new DocumentLibre(TYPE, null, els, pied);
    }

    private static void rendre(List<DocumentLibre.Element> els, String libelle, JsonNode n, int niveau) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            if (libelle != null) {
                els.add(para(libelle + " : —"));
            }
            return;
        }
        if (n.isValueNode()) {
            els.add(para(libelle == null ? texte(n) : libelle + " : " + texte(n)));
            return;
        }
        if (n.isArray()) {
            if (n.isEmpty()) {
                if (libelle != null) {
                    els.add(para(libelle + " : aucun"));
                }
                return;
            }
            boolean scalaires = true;
            boolean objets = true;
            for (JsonNode e : n) {
                scalaires &= e.isValueNode() || e.isNull();
                objets &= e.isObject();
            }
            if (scalaires) {
                List<String> valeurs = new ArrayList<>();
                n.forEach(e -> valeurs.add(texte(e)));
                els.add(para((libelle == null ? "" : libelle + " : ") + String.join(", ", valeurs)));
            } else if (objets) {
                tableau(els, libelle, n, niveau);
            } else {
                int i = 1;
                for (JsonNode e : n) {
                    rendre(els, (libelle == null ? "Élément" : libelle) + " " + i++, e, niveau + 1);
                }
            }
            return;
        }
        if (libelle != null) {
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, libelle));
        }
        for (Map.Entry<String, JsonNode> e : n.properties()) {
            rendre(els, nom(e.getKey()), e.getValue(), niveau + 1);
        }
    }

    /** Une liste d'objets : un tableau de leurs champs simples ; les champs composés de chaque élément suivent. */
    private static void tableau(List<DocumentLibre.Element> els, String libelle, JsonNode liste, int niveau) {
        LinkedHashSet<String> colonnes = new LinkedHashSet<>();
        for (JsonNode e : liste) {
            for (Map.Entry<String, JsonNode> f : e.properties()) {
                if ((f.getValue().isValueNode() || f.getValue().isNull()) && colonnes.size() < MAX_COLONNES) {
                    colonnes.add(f.getKey());
                }
            }
        }
        if (libelle != null) {
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, libelle));
        }
        if (!colonnes.isEmpty()) {
            List<List<List<String>>> lignes = new ArrayList<>();
            lignes.add(colonnes.stream().map(c -> List.of(nom(c))).toList());
            for (JsonNode e : liste) {
                List<List<String>> ligne = new ArrayList<>();
                for (String c : colonnes) {
                    JsonNode v = e.get(c);
                    ligne.add(List.of(v == null || v.isNull() ? "" : v.isValueNode() ? texte(v) : ""));
                }
                lignes.add(ligne);
            }
            els.add(new DocumentLibre.Tableau(colonnes.size(), lignes));
        }
        int i = 1;
        for (JsonNode e : liste) {
            for (Map.Entry<String, JsonNode> f : e.properties()) {
                if (!(f.getValue().isValueNode() || f.getValue().isNull()) && !(f.getValue().isArray() && f.getValue().isEmpty())) {
                    rendre(els, nom(f.getKey()) + " (" + (libelle == null ? "élément" : libelle.toLowerCase(Locale.ROOT)) + " " + i + ")",
                            f.getValue(), niveau + 1);
                }
            }
            i++;
        }
    }

    static String texte(JsonNode v) {
        if (v == null || v.isNull()) {
            return "—";
        }
        if (v.isBoolean()) {
            return v.booleanValue() ? "oui" : "non";
        }
        return v.asString();
    }

    /** « designationMarche » → « Designation marche » ; « PV_SIGNE » → « PV_SIGNE » (les codes restent). */
    static String nom(String cle) {
        if (cle == null || cle.isBlank()) {
            return "";
        }
        String mots = cle.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return cle.equals(cle.toUpperCase(Locale.ROOT)) ? cle : Character.toUpperCase(mots.charAt(0)) + mots.substring(1);
    }

    private static DocumentLibre.Paragraphe para(String texte) {
        return new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, texte);
    }
}
