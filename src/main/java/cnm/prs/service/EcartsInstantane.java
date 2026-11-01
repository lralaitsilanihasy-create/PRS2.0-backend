package cnm.prs.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
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
 *   <li>les éléments d'une liste sont appariés par leur <strong>identité</strong> ({@link #IDENTITES}), à défaut par leur rang, et
 *       nommés en clair (« Lot 1 », « Offre n° 2 ») ;</li>
 *   <li>les valeurs sont rendues telles qu'on les lit : montant « 2 939 941 765 Ar », date jj/mm/aaaa, oui/non.</li>
 * </ul>
 * ⚠️ 2026-11-01 (demande front « ecarts-lisibles ») — B1 : un code connu est servi par son libellé ({@link Dictionnaire} : états des
 * écrans, et référentiels avis, statut de marché, nature, mode) ; B2 : les noms de champ des écrans figés en français, majuscule
 * initiale ({@link #LIBELLES}) ; B3 : un objet ajouté ou retiré résumé en une phrase ({@link #resume}), sans identifiant technique.
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
            Map.entry("lignes", "Ligne"), Map.entry("etapes", "Étape"), Map.entry("demandes", "Demande"),
            Map.entry("piecesAttributaire", "Pièce"), Map.entry("suiviTitulaires", "Titulaire"), Map.entry("recours", "Recours"),
            Map.entry("lettres", "Lettre"), Map.entry("signataires", "Signataire"));

    /** Libellés des noms de champ des écrans figés (B2) ; à défaut, le nom découpé en mots. */
    private static final Map<String, String> LIBELLES = Map.ofEntries(Map.entry("montant", "montant"),
            Map.entry("montantEvalue", "montant évalué"), Map.entry("montantHt", "montant HT"), Map.entry("montantTtc", "montant TTC"),
            Map.entry("montantLu", "montant lu"), Map.entry("montantCorrige", "montant corrigé"), Map.entry("etat", "état"),
            Map.entry("statut", "statut"), Map.entry("avis", "avis"), Map.entry("objet", "objet"), Map.entry("designation", "désignation"),
            Map.entry("designationMarche", "désignation du marché"), Map.entry("rang", "rang"), Map.entry("delai", "délai"),
            Map.entry("note", "note"), Map.entry("score", "score"), Map.entry("raisonSociale", "raison sociale"), Map.entry("nom", "nom"),
            Map.entry("prenom", "prénom"), Map.entry("qualite", "qualité"), Map.entry("motif", "motif"), Map.entry("evaluation", "évaluation"),
            Map.entry("proposition", "proposition"), Map.entry("conforme", "conforme"), Map.entry("dossierMarche", "dossier de marché"),
            Map.entry("delaiAttente", "délai d'attente"), Map.entry("information", "information des soumissionnaires"),
            Map.entry("piecesAttributaire", "pièces de l'attributaire"), Map.entry("avisAttribution", "avis d'attribution"),
            Map.entry("suiviTitulaires", "suivi des titulaires"), Map.entry("attributaire", "attributaire"),
            Map.entry("titulaires", "titulaires"), Map.entry("signature", "signature"), Map.entry("enregistrement", "enregistrement"),
            Map.entry("approbation", "approbation"), Map.entry("notification", "notification"), Map.entry("visa", "visa du contrôleur financier"),
            Map.entry("decision", "décision"), Map.entry("recours", "recours"), Map.entry("candidat", "candidat"), Map.entry("nif", "NIF"),
            Map.entry("ligne", "ligne du plan"), Map.entry("valeurs", "valeurs"), Map.entry("dates", "dates"),
            Map.entry("versionPpm", "version du plan"), Map.entry("lettres", "lettres d'invitation"), Map.entry("documents", "documents"),
            Map.entry("projets", "projets"), Map.entry("membres", "membres"), Map.entry("president", "président"),
            Map.entry("dateCreation", "date de création"), Map.entry("dateSoumission", "date de soumission"),
            Map.entry("idDossierSoumis", "dossier soumis"), Map.entry("referenceEnregistrement", "référence d'enregistrement"),
            Map.entry("modeRemise", "mode de remise"), Map.entry("seance", "séance"), Map.entry("ouverteLe", "ouverte le"),
            Map.entry("closeLe", "close le"), Map.entry("signeLe", "signé le"), Map.entry("notifieLe", "notifié le"),
            Map.entry("attribueLe", "attribué le"), Map.entry("idMode", "mode de passation"), Map.entry("idNature", "nature"),
            Map.entry("statutMarche", "statut du marché"), Map.entry("formeMarche", "forme du marché"), Map.entry("categorie", "catégorie"),
            Map.entry("typeMarche", "forme du marché"), Map.entry("montEstim", "montant estimatif"), Map.entry("financement", "financement"));

    /**
     * B1 — les libellés des codes : {@code codes} (états des écrans, avis, statut de marché, formes, catégories), {@code natures} et
     * {@code modes} (référentiels par identifiant, lus pour les champs {@code idNature} et {@code idMode}).
     */
    public record Dictionnaire(Map<String, String> codes, Map<Integer, String> natures, Map<Integer, String> modes) {

        public static final Dictionnaire VIDE = new Dictionnaire(Map.of(), Map.of(), Map.of());

        /** Les états des écrans (dossier, attribution, PV, séance, évaluation, cérémonie, retrait, fiche, procédure) et les formes. */
        public static Map<String, String> etats() {
            Map<String, String> m = new HashMap<>();
            String[][] t = { { "BROUILLON", "Brouillon" }, { "SOUMIS", "Soumis" }, { "PRET_DISPATCH", "Prêt au dispatch" },
                    { "DISPATCHE", "Dispatché" }, { "EXAMINE", "Examiné" }, { "PV_SIGNE", "PV signé" }, { "EN_VERIFICATION", "En vérification" },
                    { "EN_ATTENTE_DECISION_PRMP", "En attente de la décision de la PRMP" }, { "OBSERVATIONS_LEVEES", "Observations levées" },
                    { "DECISION_TRANSMISE_SIGMP", "Décision transmise au SIGMP" }, { "EN_ATTENTE_PIECES", "En attente de pièces" },
                    { "A_REEXAMINER", "À réexaminer" }, { "EN_ATTENTE_COMPLEMENTS_DEPOT", "En attente de compléments" }, { "RETIRE", "Retiré" },
                    { "CLOTURE", "Clôturé" }, { "REMPLACE", "Remplacé" }, { "AU_CONTROLE", "Au contrôle" }, { "ATTRIBUE", "Attribué" },
                    { "INFORME", "Soumissionnaires informés" }, { "SIGNE", "Signé" }, { "NOTIFIE", "Notifié" }, { "PUBLIE", "Publié" },
                    { "INFRUCTUEUX", "Infructueux" }, { "PROJET_SOUMIS", "Projet soumis" }, { "EN_RECTIFICATION", "En rectification" },
                    { "PROJET_ACCEPTE", "Projet accepté" }, { "A_VENIR", "À venir" }, { "OUVERTE", "Ouverte" }, { "DECHIFFREE", "Déchiffrée" },
                    { "PV_A_SIGNER", "PV à signer" }, { "ILLISIBLE", "Illisible" }, { "CLOSE", "Close" }, { "EN_COURS", "En cours" },
                    { "RAPPORT_A_SIGNER", "Rapport à signer" }, { "A_REFAIRE", "À refaire" }, { "EN_ATTENTE", "En attente" },
                    { "ACCEPTEE", "Acceptée" }, { "REFUSEE", "Refusée" }, { "VALIDEE", "Validée" }, { "A_PREPARER", "À préparer" },
                    { "PAPIER", "Papier" }, { "ELECTRONIQUE", "Électronique" }, { "COMPLETE", "Complète" }, { "INCOMPLETE", "Incomplète" },
                    { "ABSENTE", "Absente" } };
            for (String[] x : t) {
                m.put(x[0], x[1]);
            }
            for (cnm.prs.enums.FormeMarche f : cnm.prs.enums.FormeMarche.values()) {
                m.putIfAbsent(f.name(), f.libelle());
            }
            for (cnm.prs.enums.CategorieDao c : cnm.prs.enums.CategorieDao.values()) {
                m.putIfAbsent(c.name(), c.libelle());
            }
            m.putIfAbsent("QUANTITE_FIXE", "Quantité fixe");
            m.putIfAbsent("A_COMMANDE", "À commandes");
            m.putIfAbsent("CONTRAT_CADRE", "Contrat-cadre");
            return m;
        }
    }

    private EcartsInstantane() {
    }

    /** Le résultat d'une comparaison : les écarts (au plus {@link #PLAFOND}) et s'il y en avait davantage. */
    public record Resultat(List<Ecart> ecarts, boolean tronque) {
    }

    /** Compare sans dictionnaire de codes. */
    public static Resultat comparer(JsonNode soumis, JsonNode actuel) {
        return comparer(soumis, actuel, Dictionnaire.VIDE);
    }

    /** Compare {@code soumis} (l'écran figé) à {@code actuel} (recalculé ; {@code null} : l'écran n'existe plus). */
    public static Resultat comparer(JsonNode soumis, JsonNode actuel, Dictionnaire dico) {
        List<Ecart> ecarts = new ArrayList<>();
        int[] total = { 0 };
        if (actuel == null || actuel.isNull()) {
            return new Resultat(List.of(new Ecart("", "Écran supprimé depuis la soumission", "présent", null)), false);
        }
        new Comparaison(dico, ecarts, total).comparer("", new ArrayList<>(), null, soumis, actuel);
        return new Resultat(ecarts, total[0] > PLAFOND);
    }

    private record Comparaison(Dictionnaire dico, List<Ecart> ecarts, int[] total) {

        void comparer(String chemin, List<String> contexte, String cle, JsonNode a, JsonNode b) {
            if (a == null || a.isNull()) {
                if (b != null && !b.isNull() && !b.isMissingNode()) {
                    ajouter(chemin, contexte, null, b.isContainer() ? resume(b) : valeur(cle, b));
                }
                return;
            }
            if (b == null || b.isMissingNode()) {
                ajouter(chemin, contexte, a.isContainer() ? resume(a) : valeur(cle, a), null);
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
                    comparer(chemin.isEmpty() ? nom : chemin + "." + nom, ctx, nom, a.get(nom),
                            vb == null ? tools.jackson.databind.node.MissingNode.getInstance() : vb);
                }
                return;
            }
            if (a.isArray() && b.isArray()) {
                comparerListes(chemin, contexte, cle, a, b);
                return;
            }
            String va = valeur(cle, a);
            String vb = valeur(cle, b);
            if (!java.util.Objects.equals(va, vb)) {
                ajouter(chemin, contexte, va, vb);
            }
        }

        void comparerListes(String chemin, List<String> contexte, String cle, JsonNode a, JsonNode b) {
            String id = identite(a, b);
            String element = ELEMENTS.getOrDefault(cle, cle == null ? "Élément" : libelleChamp(cle));
            // Le dernier libellé du contexte est le nom de la liste : l'élément le remplace (« Lot 1 » plutôt que « Lots — Lot 1 »).
            List<String> base = contexte.isEmpty() ? new ArrayList<>() : new ArrayList<>(contexte.subList(0, contexte.size() - 1));
            Map<String, JsonNode> parB = new LinkedHashMap<>();
            for (int i = 0; i < b.size(); i++) {
                parB.put(cleElement(b.get(i), id, i), b.get(i));
            }
            java.util.Set<String> vus = new java.util.HashSet<>();
            for (int i = 0; i < a.size(); i++) {
                String k = cleElement(a.get(i), id, i);
                vus.add(k);
                List<String> ctx = new ArrayList<>(base);
                ctx.add(element + " " + nomElement(a.get(i), id, k, i));
                JsonNode eb = parB.get(k);
                String c = chemin + "[" + i + "]";
                if (eb == null) {
                    ajouter(c, ctx, resume(a.get(i)), null);
                } else {
                    comparer(c, ctx, null, a.get(i), eb);
                }
            }
            int j = 0;
            for (Map.Entry<String, JsonNode> e : parB.entrySet()) {
                if (!vus.contains(e.getKey())) {
                    List<String> ctx = new ArrayList<>(base);
                    ctx.add(element + " " + nomElement(e.getValue(), id, e.getKey(), j) + " (ajouté)");
                    ajouter(chemin + "[+" + j + "]", ctx, null, resume(e.getValue()));
                }
                j++;
            }
        }

        void ajouter(String chemin, List<String> contexte, String soumis, String actuel) {
            total[0]++;
            if (ecarts.size() < PLAFOND) {
                ecarts.add(new Ecart(chemin, contexte.isEmpty() ? chemin : String.join(" — ", contexte), soumis, actuel));
            }
        }

        /** Une valeur telle qu'on la lit : libellé d'un code connu (B1), montant en Ar, date jj/mm/aaaa (et heure), oui/non. */
        String valeur(String cle, JsonNode n) {
            if (n == null || n.isNull() || n.isMissingNode()) {
                return null;
            }
            if (n.isBoolean()) {
                return n.asBoolean() ? "oui" : "non";
            }
            if (n.isNumber()) {
                if ("idNature".equals(cle) && dico.natures().containsKey(n.asInt())) {
                    return dico.natures().get(n.asInt());
                }
                if ("idMode".equals(cle) && dico.modes().containsKey(n.asInt())) {
                    return dico.modes().get(n.asInt());
                }
                BigDecimal v = n.decimalValue();
                return cle != null && montant(cle) ? montant(v) : v.stripTrailingZeros().toPlainString();
            }
            if (n.isContainer()) {
                return resume(n);
            }
            String s = n.asString();
            String code = dico.codes().get(s);
            if (code != null) {
                return code;
            }
            if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return date(s);
            }
            if (s.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*")) {
                return date(s) + " " + s.substring(11, 16);
            }
            if (cle != null && montant(cle) && s.matches("-?\\d+(\\.\\d+)?")) {
                return montant(new BigDecimal(s));
            }
            return s;
        }

        /**
         * B3 — un objet ajouté ou retiré, résumé en une phrase par ce qui l'identifie à l'écran : offre (candidat — offre n° — montant),
         * acte daté (le … par … — référence), membre (nom prénom — qualité), document (fichier — date), délai (du … au … (n jours)) ;
         * à défaut, le texte compact sans les champs {@code id…} ; une liste, ses éléments résumés.
         */
        String resume(JsonNode n) {
            if (n.isArray()) {
                List<String> parts = new ArrayList<>();
                n.forEach(x -> parts.add(x.isContainer() ? resume(x) : valeur(null, x)));
                return couper(String.join(" ; ", parts));
            }
            if (!n.isObject()) {
                return valeur(null, n);
            }
            String candidat = texte(n, "candidat", "raisonSociale", "soumissionnaire");
            if (candidat != null) {
                List<String> p = new ArrayList<>(List.of(candidat));
                if (n.hasNonNull("numero")) {
                    p.add("offre n° " + n.get("numero").asString());
                }
                String m = premierMontant(n);
                if (m != null) {
                    p.add(m);
                }
                return couper(String.join(" — ", p));
            }
            if (n.hasNonNull("debut") && n.hasNonNull("fin") || n.hasNonNull("du") && n.hasNonNull("au")) {
                String d1 = n.hasNonNull("debut") ? n.get("debut").asString() : n.get("du").asString();
                String d2 = n.hasNonNull("fin") ? n.get("fin").asString() : n.get("au").asString();
                String jours = "";
                try {
                    jours = " (" + ChronoUnit.DAYS.between(LocalDate.parse(d1.substring(0, 10)), LocalDate.parse(d2.substring(0, 10))) + " jours)";
                } catch (RuntimeException e) {
                    // dates illisibles : sans le nombre de jours
                }
                return "du " + date(d1) + " au " + date(d2) + jours;
            }
            String le = texte(n, "le", "date", "dateSignature", "signeLe");
            if (le != null && le.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                StringBuilder sb = new StringBuilder("le ").append(date(le));
                String par = texte(n, "par", "auteur", "signePar");
                if (par != null) {
                    sb.append(" par ").append(par);
                }
                String ref = texte(n, "reference", "autorite", "referenceEnregistrement", "approbationAutorite");
                if (ref != null) {
                    sb.append(" — ").append(ref);
                }
                return couper(sb.toString());
            }
            String nom = texte(n, "nom");
            if (nom != null && (n.hasNonNull("prenom") || n.hasNonNull("qualite") || n.hasNonNull("prenoms"))) {
                String prenom = texte(n, "prenom", "prenoms");
                String qualite = texte(n, "qualite");
                return couper(nom + (prenom == null ? "" : " " + prenom) + (qualite == null ? "" : " — " + valeur("qualite", n.get("qualite"))));
            }
            String fichier = texte(n, "nomFichier", "fichier");
            if (fichier != null) {
                String d = texte(n, "dateGeneration", "dateUpload", "deposeLe", "date");
                return couper(fichier + (d == null || !d.matches("\\d{4}-\\d{2}-\\d{2}.*") ? "" : " — " + date(d)));
            }
            // À défaut : le texte compact, sans les identifiants techniques.
            List<String> parts = new ArrayList<>();
            Iterator<String> noms = n.propertyNames().iterator();
            while (noms.hasNext()) {
                String k = noms.next();
                JsonNode v = n.get(k);
                if (k.startsWith("id") || v == null || v.isNull() || volatil(k)) {
                    continue;
                }
                parts.add(libelleChamp(k).toLowerCase(Locale.ROOT) + " : " + (v.isContainer() ? resume(v) : valeur(k, v)));
            }
            return couper(String.join(", ", parts));
        }

        private String premierMontant(JsonNode n) {
            for (String k : List.of("montant", "montantTtc", "montantHt", "montantEvalue", "montantLu")) {
                if (n.hasNonNull(k)) {
                    return valeur(k, n.get(k));
                }
            }
            return null;
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

    /**
     * Le nom d'un élément : sa clé d'identité quand elle est parlante ({@code numero}, {@code lot}, {@code code}…) ; derrière un
     * identifiant technique ({@code id…}), son numéro, à défaut son rang, son lot, son nom, son candidat, son fichier, sa position (« les identifiants techniques ne
     * s'affichent pas »).
     */
    private static String nomElement(JsonNode n, String id, String cle, int position) {
        if (id != null && !id.startsWith("id")) {
            return cle;
        }
        for (String k : List.of("numero", "rang", "lot", "nom", "candidat", "nomFichier", "code")) {
            if (n != null && n.isObject() && n.hasNonNull(k) && !n.get(k).isContainer()) {
                return n.get(k).asString();
            }
        }
        return String.valueOf(position + 1);
    }

    private static String cleElement(JsonNode n, String id, int rang) {
        return id == null ? String.valueOf(rang) : n.get(id).asString();
    }

    private static boolean volatil(String nom) {
        return VOLATILS.contains(nom) || nom.startsWith("peut") && nom.length() > 4 && Character.isUpperCase(nom.charAt(4));
    }

    private static boolean montant(String cle) {
        return cle.toLowerCase(Locale.ROOT).contains("montant") || "montEstim".equals(cle);
    }

    /** Le libellé d'un champ (B2) : le dictionnaire, à défaut le nom découpé en mots ; majuscule initiale. */
    static String libelleChamp(String nom) {
        String l = LIBELLES.get(nom);
        if (l == null) {
            l = nom.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        }
        return l.isEmpty() ? l : Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    /** La première valeur textuelle non vide parmi ces champs. */
    private static String texte(JsonNode n, String... cles) {
        for (String k : cles) {
            if (n.hasNonNull(k) && !n.get(k).isContainer() && !n.get(k).asString().isBlank()) {
                return n.get(k).asString();
            }
        }
        return null;
    }

    private static String date(String s) {
        return s.length() >= 10 ? s.substring(8, 10) + "/" + s.substring(5, 7) + "/" + s.substring(0, 4) : s;
    }

    private static String montant(BigDecimal v) {
        DecimalFormatSymbols sy = new DecimalFormatSymbols(Locale.FRANCE);
        sy.setGroupingSeparator(' ');
        sy.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", sy).format(v) + " Ar";
    }

    private static String couper(String s) {
        return s.length() > 200 ? s.substring(0, 197) + "…" : s;
    }
}
