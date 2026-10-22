package cnm.prs.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », §B3, lot G1) — le <strong>catalogue des variables</strong> d'un modèle
 * de PV : ce que l'Administrateur peut écrire dans son Word, par groupe, avec un exemple. Pur : il sert l'écran du catalogue, le
 * contrôle d'un fichier déposé (code inconnu, suggestion) et la production d'essai sur un dossier fictif.
 *
 * <p>Les <strong>familles</strong> bornent les groupes propres à une procédure : {@code PROCEDURE} aux dossiers de mise en
 * concurrence et de marché, {@code MARCHE} et la liste {@code TITULAIRES} au dossier de marché. Les <strong>alias</strong> sont les
 * marqueurs entre chevrons des 14 modèles du plan, lus comme les variables nouvelles (reprise, lot G4). Les <strong>clés de
 * condition</strong> sont celles que les expressions déclarées peuvent lire (grammaire de {@link ConditionsModele}), plus
 * {@code saisi.<code>} pour un champ saisi.</p>
 */
public final class CatalogueModelesPv {

    /** Une variable : code écrit dans le Word, groupe, libellé, exemple, familles servies ({@code null} = toutes), liste parente. */
    public record Variable(String code, String groupe, String libelle, String exemple, boolean date, Set<String> familles,
            String liste) {
    }

    /** Une liste répétée par {@code {{POUR:NOM}}} : le préfixe de ses variables et les familles servies. */
    public record Liste(String nom, String prefixe, String libelle, Set<String> familles) {
    }

    /** Une clé de condition : ce qu'elle lit, et ses valeurs possibles quand elles sont fermées. */
    public record CleCondition(String cle, String libelle, List<String> valeurs) {
    }

    static final String PLAN = "DDP";
    static final String MISE_EN_CONCURRENCE = "DMC";
    static final String MARCHE = "DDM";
    private static final Set<String> PROCEDURES = Set.of(MISE_EN_CONCURRENCE, MARCHE);
    private static final Set<String> MARCHES = Set.of(MARCHE);

    /** Les formats d'une variable : {@code {{CODE|format}}}. */
    public static final List<String> FORMATS = List.of("long", "court", "lettres", "majuscules", "minuscules");

    public static final List<Liste> LISTES = List.of(
            new Liste("OBSERVATIONS", "OBS", "Observations de l'examen (une ligne de tableau par observation)", null),
            new Liste("TITULAIRES", "TIT", "Titulaires proposés (une ligne de tableau par titulaire)", MARCHES));

    public static final List<Variable> VARIABLES = List.of(
            v("COMMISSION.NOM", "Commission", "Nom de la Commission", "Commission Nationale des Marchés"),
            v("COMMISSION.LOCALITE", "Commission", "Localité de la Commission", "Antananarivo"),
            v("COMMISSION.CHEF_LIEU", "Commission", "Chef-lieu, lieu d'établissement du PV", "Antananarivo"),
            v("DOSSIER.REFERENCE", "Dossier", "Référence du dossier", "00001/MAOO-CC/CNM/2026"),
            v("DOSSIER.SOUS_TYPE", "Dossier", "Sous-type du dossier", "Marché sur appel d'offres ouvert — contrat-cadre"),
            d("DOSSIER.DATE_RECEPTION", "Dossier", "Date de réception du dossier", "2026-10-19"),
            v("DOSSIER.ENTITE", "Dossier", "Entité contractante", "MINISTERE DE L'ECONOMIE ET DES FINANCES"),
            v("DOSSIER.PRMP", "Dossier", "Personne responsable des marchés publics", "RANDRIANARIVO La Personne"),
            v("DOSSIER.EXERCICE", "Dossier", "Exercice", "2026"),
            // ⚠️ 2026-10-22 (« modele-pv-plan-depart ») — le mot INITIAL des fichiers du plan, MODIFICATIF N°n sur une mise à jour.
            new Variable("DOSSIER.NATURE_PLAN", "Dossier", "Nature du plan : INITIAL, ou MODIFICATIF N°n sur une mise à jour", "INITIAL",
                    false, Set.of(PLAN), null),
            v("PV.REFERENCE", "PV", "Référence du PV", "00001/MAOO-CC/CNM/PV/2026"),
            d("PV.DATE_EXAMEN", "PV", "Date de l'examen (|lettres : « deux mille vingt-six et le vingt-deux octobre »)", "2026-10-22"),
            v("PV.AVIS", "PV", "Avis : favorable, favorable avec réserves, défavorable", "favorable"),
            d("PV.DATE_DU_JOUR", "PV", "Date du jour de la production", "2026-10-22"),
            v("PV.PRESIDENT", "Personnes", "Président de la Commission", "Andry RAKOTOMALALA"),
            v("PV.CHEF_COMMISSION", "Personnes", "Chef de la Commission", "Sitraka Tsitohaina RANDRIANARISON"),
            v("PV.MEMBRE", "Personnes", "Membre en charge du dossier", "Rina RAFIDIMANANA"),
            v("PV.VISEUR", "Personnes", "Nom de qui vise le PV", "Sitraka Tsitohaina RANDRIANARISON"),
            v("PV.VISEUR_ROLE", "Personnes", "Qualité de qui vise", "Chef de la Commission"),
            v("PV.INTERIM", "Personnes", "« par intérim » sur un PV régional visé par intérim, vide sinon", ""),
            new Variable("OBS.POINT", "Observations", "Point de contrôle", "[Dossier] Pièces administratives", false, null, "OBSERVATIONS"),
            new Variable("OBS.AU_LIEU_DE", "Observations", "Au lieu de", "Délai de validité : 60 jours", false, null, "OBSERVATIONS"),
            new Variable("OBS.LIRE", "Observations", "Lire", "Délai de validité : 90 jours", false, null, "OBSERVATIONS"),
            p("PROCEDURE.NUMERO", "Numéro de la procédure", "13-CC/MEF/SG/PRMP.2026"),
            p("PROCEDURE.OBJET", "Objet du marché", "Fourniture de produits d'entretien et des petits matériels"),
            p("PROCEDURE.MODE", "Mode de passation", "Appel d'offres ouvert"),
            p("PROCEDURE.FORME", "Forme du marché", "Contrat-cadre"),
            p("PROCEDURE.LOT", "Lot du dossier", "1"),
            m("MARCHE.DOSSIER_MEC", "Dossier de mise en concurrence et avis de la Commission", "00003/DAOO-CC/CNM/2026, avis favorable"),
            m("MARCHE.ESTIMATION", "Montant estimatif du plan (Ariary)", "231 563 789"),
            m("MARCHE.ECART", "Écart à l'estimation (offre classée 1re)", "−22,5 % (179 570 562 Ar HT contre 231 563 789 Ar estimés)"),
            t("TIT.RANG", "Rang", "1"),
            t("TIT.CANDIDAT", "Candidat", "Hygiène & Services Madagascar SARL"),
            t("TIT.MONTANT_HT", "Montant hors taxes (Ariary)", "179 570 562"),
            t("TIT.MONTANT_TTC", "Montant toutes taxes (Ariary)", "215 484 674"),
            t("TIT.DELAI", "Délai d'exécution", "12 mois"),
            // ⚠️ 2026-10-21 (« modele-lettre-renvoi », B1) — la lettre de renvoi : les marqueurs de LR_CENTRALE / LR_REGIONALE.
            d("LETTRE.DATE", "Lettre de renvoi", "Date de la lettre", "2026-10-23"),
            d("LETTRE.DATE_EXAMEN", "Lettre de renvoi", "Date de l'examen (séance d'instruction)", "2026-10-22"),
            v("LETTRE.CORPS", "Lettre de renvoi", "Corps de la lettre, avec les informations de la fiche DAO visées (un saut de ligne "
                    + "par ligne saisie)", "- produire l'attestation fiscale à jour ;\n- corriger le délai de validité des offres."),
            v("LETTRE.SIGNATAIRE", "Lettre de renvoi", "Signataire : Président (centrale) ou Chef de la Commission (régionale)",
                    "Andry RAKOTOMALALA"),
            v("LETTRE.LOCALITE", "Lettre de renvoi", "Localité du dossier (|majuscules pour l'en-tête régional)", "Toamasina"));

    /**
     * Les marqueurs entre chevrons des modèles du plan, lus comme les variables nouvelles. {@code <DATE EXAMEN>} se lit en date
     * longue : la formule « L'an … » s'écrit désormais {@code {{PV.DATE_EXAMEN|lettres}}} (l'ancien générateur la repérait au mot
     * « instituée »). Les trois marqueurs de l'annexe ne valent que sur une ligne {@code {{POUR:OBSERVATIONS}}}.
     */
    public static final Map<String, String> ALIAS = alias();

    public static final List<CleCondition> CLES = List.of(
            new CleCondition("avis", "Avis du PV", List.of("FAV", "FAVR", "DEF", "NSP")),
            new CleCondition("agpm", "Le plan porte un AGPM", List.of("oui", "non")),
            new CleCondition("portee", "Portée de la Commission", List.of("centrale", "regionale")),
            new CleCondition("sousType", "Sous-type du dossier", List.of()),
            new CleCondition("forme", "Forme du marché", List.of("QUANTITE_FIXE", "A_COMMANDE", "CONTRAT_CADRE")),
            new CleCondition("titulaires", "Nombre de titulaires proposés", List.of()));

    /** Les variables sans lesquelles un PV n'a pas ses sections attendues (l'avis se vérifie à part : variable ou condition). */
    static final Map<String, List<String>> REQUISES = Map.of(
            "PV", List.of("PV.REFERENCE", "PV.MEMBRE"),
            "LETTRE_RENVOI", List.of("DOSSIER.REFERENCE", "LETTRE.CORPS", "LETTRE.SIGNATAIRE"));

    /**
     * ⚠️ 2026-10-21 (« modele-lettre-renvoi », B1) — la nature qu'une variable sert, lue sur son préfixe : {@code LETTRE.} la lettre
     * de renvoi ; {@code PV.}, {@code OBS.}, {@code TIT.}, {@code MARCHE.} le PV ; les autres (commission, dossier, procédure) les
     * deux ({@code null}).
     */
    public static String nature(String code) {
        if (code.startsWith("LETTRE.")) {
            return "LETTRE_RENVOI";
        }
        return code.startsWith("PV.") || code.startsWith("OBS.") || code.startsWith("TIT.") || code.startsWith("MARCHE.") ? "PV" : null;
    }

    /** Une variable sert-elle cette nature ({@code null} : toutes) ? */
    public static boolean sert(Variable v, String nature) {
        String n = nature(v.code());
        return nature == null || n == null || n.equals(nature);
    }

    private CatalogueModelesPv() {
    }

    public static Optional<Variable> variable(String code) {
        return VARIABLES.stream().filter(v -> v.code().equals(code)).findFirst();
    }

    public static Optional<Liste> liste(String nom) {
        return LISTES.stream().filter(l -> l.nom().equals(nom)).findFirst();
    }

    /** Les variables servies à une famille de dossier ({@code null} : toutes). */
    public static List<Variable> pourFamille(String famille) {
        return VARIABLES.stream().filter(v -> famille == null || v.familles() == null || v.familles().contains(famille)).toList();
    }

    public static boolean cleConnue(String cle) {
        // ⚠️ 2026-10-22 — une variable hors liste se lit aussi (« PV.PRESIDENT renseigne »).
        return cle.startsWith("saisi.") || CLES.stream().anyMatch(c -> c.cle().equals(cle))
                || variable(cle).filter(v -> v.liste() == null).isPresent();
    }

    /** Le code connu le plus proche d'un code inconnu (au plus trois caractères d'écart), pour « vouliez-vous … ? ». */
    public static Optional<String> suggestion(String code) {
        String meilleur = null;
        int distance = 4;
        for (Variable v : VARIABLES) {
            int dd = levenshtein(code, v.code());
            if (dd < distance) {
                distance = dd;
                meilleur = v.code();
            }
        }
        return Optional.ofNullable(meilleur);
    }

    /** Le contexte fictif de la production d'essai : les exemples du catalogue, deux lignes par liste, un avis favorable. */
    public static ModelePvWord.Contexte exemple() {
        Map<String, ModelePvWord.Valeur> variables = new LinkedHashMap<>();
        Map<String, Map<String, ModelePvWord.Valeur>> premiere = new LinkedHashMap<>();
        for (Variable v : VARIABLES) {
            ModelePvWord.Valeur val = v.date() ? ModelePvWord.Valeur.date(LocalDate.parse(v.exemple()))
                    : ModelePvWord.Valeur.de(v.exemple());
            if (v.liste() == null) {
                variables.put(v.code(), val);
            } else {
                premiere.computeIfAbsent(v.liste(), k -> new LinkedHashMap<>()).put(v.code(), val);
            }
        }
        Map<String, List<Map<String, ModelePvWord.Valeur>>> listes = new LinkedHashMap<>();
        premiere.forEach((nom, item) -> {
            Map<String, ModelePvWord.Valeur> seconde = new LinkedHashMap<>(item);
            if ("TITULAIRES".equals(nom)) {
                seconde.put("TIT.RANG", ModelePvWord.Valeur.de("2"));
                seconde.put("TIT.CANDIDAT", ModelePvWord.Valeur.de("Entreprise de démonstration SARL"));
            }
            listes.put(nom, List.of(item, seconde));
        });
        return new ModelePvWord.Contexte(variables, listes,
                Map.of("avis", "FAV", "agpm", "non", "portee", "centrale", "sousType", "MAOO-CC", "forme", "CONTRAT_CADRE",
                        "titulaires", "2"),
                Map.of());
    }

    /** ⚠️ 2026-10-22 (demande front « apercu-exemple », Q1) — la mention portée par chaque page d'un aperçu d'exemple. */
    public static final String MENTION_EXEMPLE = "EXEMPLE — valeurs fictives";

    private static final Map<String, String> AVIS_EN_CLAIR = Map.of("FAV", "favorable", "FAVR", "favorable avec réserves", "DEF",
            "défavorable", "NSP", "ne se prononce pas");

    /**
     * ⚠️ 2026-10-22 (demande front « apercu-exemple », E1) — le <strong>dossier d'exemple</strong> de l'aperçu, pour tout modèle (PV de
     * toute famille, lettre de renvoi) : les exemples du catalogue ({@link #exemple()}), une seule observation, l'avis demandé (FAV par
     * défaut, {@code PV.AVIS} en clair), commission centrale, avec AGPM, le sous-type donné (son libellé dans
     * {@code DOSSIER.SOUS_TYPE}) et les champs saisis. L'essai du contrôle garde {@link #exemple()}.
     */
    public static ModelePvWord.Contexte exempleApercu(String avis, String sousType, String libelleSousType, Map<String, String> saisis) {
        ModelePvWord.Contexte ex = exemple();
        String a = avis == null || avis.isBlank() ? "FAV" : avis;
        Map<String, ModelePvWord.Valeur> variables = new LinkedHashMap<>(ex.variables());
        variables.put("PV.AVIS", ModelePvWord.Valeur.de(AVIS_EN_CLAIR.getOrDefault(a, a)));
        if (libelleSousType != null) {
            variables.put("DOSSIER.SOUS_TYPE", ModelePvWord.Valeur.de(libelleSousType));
        }
        Map<String, List<Map<String, ModelePvWord.Valeur>>> listes = new LinkedHashMap<>(ex.listes());
        listes.computeIfPresent("OBSERVATIONS", (k, l) -> l.subList(0, 1));
        Map<String, String> cles = new LinkedHashMap<>(ex.cles());
        cles.put("avis", a);
        cles.put("agpm", "oui");
        cles.put("portee", "centrale");
        if (sousType != null) {
            cles.put("sousType", sousType);
        }
        return new ModelePvWord.Contexte(variables, listes, cles, saisis == null ? Map.of() : saisis);
    }

    private static Variable v(String code, String groupe, String libelle, String exemple) {
        return new Variable(code, groupe, libelle, exemple, false, null, null);
    }

    private static Variable d(String code, String groupe, String libelle, String exemple) {
        return new Variable(code, groupe, libelle, exemple, true, null, null);
    }

    private static Variable p(String code, String libelle, String exemple) {
        return new Variable(code, "Procédure", libelle, exemple, false, PROCEDURES, null);
    }

    private static Variable m(String code, String libelle, String exemple) {
        return new Variable(code, "Marché", libelle, exemple, false, MARCHES, null);
    }

    private static Variable t(String code, String libelle, String exemple) {
        return new Variable(code, "Marché", libelle, exemple, false, MARCHES, "TITULAIRES");
    }

    private static Map<String, String> alias() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("<DATE EXAMEN>", "PV.DATE_EXAMEN");
        a.put("<REFERENCE PV >", "PV.REFERENCE");
        a.put("<REFERENCE PV>", "PV.REFERENCE");
        a.put("<DATE RECEPTION DU DOSSIER>", "DOSSIER.DATE_RECEPTION");
        a.put("<ENTITE CONTRACTANTE>", "DOSSIER.ENTITE");
        a.put("<ANNEE EXERCICE>", "DOSSIER.EXERCICE");
        a.put("<NOM ET PRENOMS DU PRESIDENT>", "PV.PRESIDENT");
        a.put("<NOM ET PRENOMS DU CHEF DE COMMISSION>", "PV.CHEF_COMMISSION");
        a.put("<NOM ET PRENOMS DU MEMBRE>", "PV.MEMBRE");
        a.put("<VISEUR>", "PV.VISEUR");
        a.put("<LOCALITE>", "COMMISSION.LOCALITE");
        a.put("<CHEF LIEU>", "COMMISSION.CHEF_LIEU");
        a.put("<CHEF LIEU >", "COMMISSION.CHEF_LIEU");
        a.put("<CHEF-LIEU>", "COMMISSION.CHEF_LIEU");
        a.put("<CHEF-LIEU >", "COMMISSION.CHEF_LIEU");
        a.put("<DATE AUJOURD’HUI>", "PV.DATE_DU_JOUR");
        a.put("<DATE AUJOURD'HUI>", "PV.DATE_DU_JOUR");
        a.put("<POINT DE CONTROLE>", "OBS.POINT");
        a.put("<AU LIEU DE>", "OBS.AU_LIEU_DE");
        a.put("<LIRE>", "OBS.LIRE");
        return java.util.Collections.unmodifiableMap(a);
    }

    static int levenshtein(String a, String b) {
        int[] prec = new int[b.length() + 1];
        int[] cour = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prec[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cour[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cout = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cour[j] = Math.min(Math.min(cour[j - 1] + 1, prec[j] + 1), prec[j - 1] + cout);
            }
            int[] x = prec;
            prec = cour;
            cour = x;
        }
        return prec[b.length()];
    }
}
