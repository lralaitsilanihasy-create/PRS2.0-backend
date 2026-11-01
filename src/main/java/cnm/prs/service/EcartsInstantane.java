package cnm.prs.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import cnm.prs.dto.EcartsInstantanesDto.Ecart;
import tools.jackson.databind.JsonNode;

/**
 * ⚠️ 2026-11-01 (demande front « ecarts-ecrans-figes », §B1) — la comparaison d'un écran figé (JSON enregistré à la soumission) avec
 * son état actuel recalculé, champ par champ :
 * <ul>
 *   <li><strong>seuls les champs présents dans l'écran figé sont comparés</strong> : un champ apparu depuis (nouvelle version du
 *       serveur, champ que seul le lecteur voit) n'est pas un écart ; un champ disparu en est un ;</li>
 *   <li>les <strong>champs volatils</strong> sont écartés ({@link #VOLATILS} : états recalculés à l'horloge, alertes, gestes et droits
 *       de l'appelant, et tout champ {@code peut…}) ;</li>
 *   <li>les éléments d'une liste sont appariés par leur <strong>identité</strong> ({@link #IDENTITES} : {@code idOffre}, {@code numero},
 *       {@code lot}…), à défaut par leur rang, et nommés en clair (« Lot 1 », « Offre n° 2 ») ;</li>
 *   <li>les valeurs sont rendues telles qu'on les lit : montant « 2 939 941 765 Ar », date jj/mm/aaaa, oui/non.</li>
 * </ul>
 * Au plus {@link #PLAFOND} écarts par écran.
 */
public final class EcartsInstantane {

    public static final int PLAFOND = 50;

    /** Champs dépendant de l'heure ou de l'appelant, jamais comparés (à tout niveau). */
    static final Set<String> VOLATILS = Set.of("alertes", "gestes", "droits", "actions", "maintenant", "aujourdhui", "horloge",
            "joursRestants", "delaiRestant", "tempsRestant", "enRetard", "retard", "depasse", "echu", "proche", "aTraiter",
            "disponible", "raison", "dejaImprime", "editable", "modifiable", "lecteur", "moi");

    /** Clés d'identité d'un élément de liste, par ordre de préférence. */
    static final List<String> IDENTITES = List.of("numero", "idOffre", "idMembre", "idDocument", "idPiece", "idArticle", "idCritere",
            "idDecision", "lot", "code", "im", "idCompte", "id");

    /** Le nom d'un élément d'une liste, selon le nom de la liste. */
    private static final Map<String, String> ELEMENTS = Map.ofEntries(Map.entry("lots", "Lot"), Map.entry("offres", "Offre n°"),
            Map.entry("membres", "Membre"), Map.entry("titulaires", "Titulaire"), Map.entry("documents", "Document"),
            Map.entry("pieces", "Pièce"), Map.entry("articles", "Article"), Map.entry("decisions", "Décision"),
            Map.entry("criteres", "Critère"), Map.entry("candidats", "Candidat"), Map.entry("projets", "Projet"),
            Map.entry("lignes", "Ligne"), Map.entry("etapes", "Étape"), Map.entry("demandes", "Demande"));

    /** Libellés de champs courants ; à défaut, le nom découpé en mots. */
    private static final Map<String, String> LIBELLES = Map.ofEntries(Map.entry("montant", "montant"),
            Map.entry("montantEvalue", "montant évalué"), Map.entry("montantHt", "montant HT"), Map.entry("montantTtc", "montant TTC"),
            Map.entry("montantLu", "montant lu"), Map.entry("etat", "état"), Map.entry("statut", "statut"), Map.entry("avis", "avis"),
            Map.entry("objet", "objet"), Map.entry("designation", "désignation"), Map.entry("rang", "rang"), Map.entry("delai", "délai"),
            Map.entry("note", "note"), Map.entry("score", "score"), Map.entry("raisonSociale", "raison sociale"),
            Map.entry("nom", "nom"), Map.entry("prenom", "prénom"), Map.entry("qualite", "qualité"), Map.entry("motif", "motif"),
            Map.entry("evaluation", "évaluation"), Map.entry("proposition", "proposition"), Map.entry("conforme", "conforme"));

    private EcartsInstantane() {
    }

    /** Le résultat d'une comparaison : les écarts (au plus {@link #PLAFOND}) et s'il y en avait davantage. */
    public record Resultat(List<Ecart> ecarts, boolean tronque) {
    }

    /** Compare {@code soumis} (l'écran figé) à {@code actuel} (recalculé ; {@code null} : l'écran n'existe plus). */
    public static Resultat comparer(JsonNode soumis, JsonNode actuel) {
        List<Ecart> ecarts = new ArrayList<>();
        int[] total = { 0 };
        if (actuel == null || actuel.isNull()) {
            return new Resultat(List.of(new Ecart("", "Écran supprimé depuis la soumission", "présent", null)), false);
        }
        comparer("", new ArrayList<>(), null, soumis, actuel, ecarts, total);
        return new Resultat(ecarts, total[0] > PLAFOND);
    }

    private static void comparer(String chemin, List<String> contexte, String cle, JsonNode a, JsonNode b, List<Ecart> ecarts, int[] total) {
        if (a == null || a.isNull()) {
            if (b != null && !b.isNull() && !b.isMissingNode()) {
                ajouter(chemin, contexte, cle, null, b.isContainer() ? resume(b) : valeur(cle, b), ecarts, total);
            }
            return;
        }
        if (b == null || b.isMissingNode()) {
            ajouter(chemin, contexte, cle, a.isContainer() ? "présent" : valeur(cle, a), null, ecarts, total);
            return;
        }
        if (a.isObject() && b.isObject()) {
            Iterator<String> noms = a.propertyNames().iterator();
            while (noms.hasNext()) {
                String nom = noms.next();
                if (volatil(nom)) {
                    continue;
                }
                JsonNode vb = b.has(nom) ? b.get(nom) : null;
                List<String> ctx = new ArrayList<>(contexte);
                ctx.add(libelleChamp(nom));
                comparer(chemin.isEmpty() ? nom : chemin + "." + nom, ctx, nom, a.get(nom), vb == null ? missing() : vb, ecarts, total);
            }
            return;
        }
        if (a.isArray() && b.isArray()) {
            comparerListes(chemin, contexte, cle, a, b, ecarts, total);
            return;
        }
        String va = valeur(cle, a);
        String vb = valeur(cle, b);
        if (!java.util.Objects.equals(va, vb)) {
            ajouter(chemin, contexte, cle, va, vb, ecarts, total);
        }
    }

    private static void comparerListes(String chemin, List<String> contexte, String cle, JsonNode a, JsonNode b, List<Ecart> ecarts,
            int[] total) {
        String id = identite(a, b);
        String element = ELEMENTS.getOrDefault(cle, cle == null ? "Élément" : libelleChamp(cle));
        // Le dernier libellé du contexte est le nom de la liste : l'élément le remplace (« Lot 1 » plutôt que « lots — Lot 1 »).
        List<String> base = contexte.isEmpty() ? new ArrayList<>() : new ArrayList<>(contexte.subList(0, contexte.size() - 1));
        Map<String, JsonNode> parB = new LinkedHashMap<>();
        for (int i = 0; i < b.size(); i++) {
            parB.put(cle(b.get(i), id, i), b.get(i));
        }
        java.util.Set<String> vus = new java.util.HashSet<>();
        for (int i = 0; i < a.size(); i++) {
            String k = cle(a.get(i), id, i);
            vus.add(k);
            List<String> ctx = new ArrayList<>(base);
            ctx.add(element + " " + (id == null ? String.valueOf(i + 1) : k));
            JsonNode eb = parB.get(k);
            String c = chemin + "[" + i + "]";
            if (eb == null) {
                ajouter(c, ctx, null, resume(a.get(i)), null, ecarts, total);
            } else {
                comparer(c, ctx, null, a.get(i), eb, ecarts, total);
            }
        }
        int j = 0;
        for (Map.Entry<String, JsonNode> e : parB.entrySet()) {
            if (!vus.contains(e.getKey())) {
                List<String> ctx = new ArrayList<>(base);
                ctx.add(element + " " + (id == null ? String.valueOf(j + 1) : e.getKey()) + " (ajouté)");
                ajouter(chemin + "[+" + j + "]", ctx, null, null, resume(e.getValue()), ecarts, total);
            }
            j++;
        }
    }

    /** La clé d'identité commune aux éléments des deux listes (objets), {@code null} : appariement par rang. */
    private static String identite(JsonNode a, JsonNode b) {
        for (String k : IDENTITES) {
            boolean partout = a.size() + b.size() > 0;
            for (JsonNode n : List.of(a, b)) {
                for (int i = 0; i < n.size() && partout; i++) {
                    partout = n.get(i).isObject() && n.get(i).has(k) && !n.get(i).get(k).isNull();
                }
            }
            if (partout) {
                return k;
            }
        }
        return null;
    }

    private static String cle(JsonNode n, String id, int rang) {
        return id == null ? String.valueOf(rang) : n.get(id).asString();
    }

    private static void ajouter(String chemin, List<String> contexte, String cle, String soumis, String actuel, List<Ecart> ecarts,
            int[] total) {
        total[0]++;
        if (ecarts.size() < PLAFOND) {
            ecarts.add(new Ecart(chemin, contexte.isEmpty() ? chemin : String.join(" — ", contexte), soumis, actuel));
        }
    }

    private static boolean volatil(String nom) {
        return VOLATILS.contains(nom) || nom.startsWith("peut") && nom.length() > 4 && Character.isUpperCase(nom.charAt(4));
    }

    private static JsonNode missing() {
        return tools.jackson.databind.node.MissingNode.getInstance();
    }

    /** Le libellé d'un champ : le dictionnaire, à défaut le nom découpé en mots. */
    static String libelleChamp(String nom) {
        String l = LIBELLES.get(nom);
        if (l != null) {
            return l;
        }
        return nom.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
    }

    /** Une valeur telle qu'on la lit : montant en Ar, date jj/mm/aaaa (et heure), oui/non ; {@code null} pour une valeur nulle. */
    static String valeur(String cle, JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        if (n.isBoolean()) {
            return n.asBoolean() ? "oui" : "non";
        }
        if (n.isNumber()) {
            BigDecimal v = n.decimalValue();
            if (cle != null && cle.toLowerCase(Locale.ROOT).contains("montant")) {
                return montant(v);
            }
            return v.stripTrailingZeros().toPlainString();
        }
        if (n.isContainer()) {
            return resume(n);
        }
        String s = n.asString();
        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return s.substring(8, 10) + "/" + s.substring(5, 7) + "/" + s.substring(0, 4);
        }
        if (s.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*")) {
            return s.substring(8, 10) + "/" + s.substring(5, 7) + "/" + s.substring(0, 4) + " " + s.substring(11, 16);
        }
        if (cle != null && cle.toLowerCase(Locale.ROOT).contains("montant") && s.matches("-?\\d+(\\.\\d+)?")) {
            return montant(new BigDecimal(s));
        }
        return s;
    }

    private static String montant(BigDecimal v) {
        DecimalFormatSymbols sy = new DecimalFormatSymbols(Locale.FRANCE);
        sy.setGroupingSeparator(' ');
        sy.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", sy).format(v) + " Ar";
    }

    /** Un élément entier, résumé (ajout ou retrait) : son texte compact, au plus 120 caractères. */
    private static String resume(JsonNode n) {
        String s = n.toString();
        return s.length() > 120 ? s.substring(0, 117) + "…" : s;
    }
}
