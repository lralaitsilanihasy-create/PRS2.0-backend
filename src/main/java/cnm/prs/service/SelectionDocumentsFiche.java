package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.BlocFicheMarche;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.RubriqueFicheMarche;
import cnm.prs.enums.TypeChampFiche;

/**
 * ⚠️ Fiche marché, lot 2a (demande front du 2026-09-23, §B1/§B5) — <strong>quelles informations vont dans quel
 * document</strong>, dérivé du référentiel et rien d'autre : un champ entre dans un document s'il en est le document
 * maître ou s'il y est repris ({@code reprises}), s'il est <strong>ouvert</strong> (type de marché, condition de cadrage)
 * et s'il a une <strong>valeur</strong> — un champ sans valeur est omis, jamais rendu vide ni « null ». Un document sans
 * aucune ligne n'est pas produit. Classe pure (aucun accès base) : la mise en page est l'affaire de
 * {@link GenerateurDocumentsFiche}.
 */
public final class SelectionDocumentsFiche {

    /** Les documents produisibles, dans l'ordre de production (le référentiel autorise aussi {@code AUCUN}). */
    public static final List<String> TYPES = List.of("DPAO", "DPAC", "DPIC", "CCAP", "AE");

    private static final Map<String, String> TITRES = Map.of(
            "DPAO", "Données particulières de l'appel d'offres",
            "DPAC", "Données particulières du cahier des clauses administratives",
            "DPIC", "Données particulières des instructions aux consultants",
            "CCAP", "Cahier des clauses administratives particulières",
            "AE", "Acte d'engagement",
            "LF", "Liste des fournitures et calendrier de livraison",
            "BP", "Bordereau des prix",
            "TC", "Spécifications techniques — tableau de conformité");

    /**
     * ⚠️ Lot 4 (2026-09-23) — le jeu documentaire d'un type de marché. En contrat-cadre, le deuxième document est le
     * {@code DPAC} et il n'y a pas de CCAP : l'acte d'engagement est le contrat. Les champs partagés par les trois types
     * (repris du plan, reflets du cadrage), dont le maître est le DPAO et qui sont repris au CCAP, y suivent la règle de
     * répartition du fichier de correspondance : clause de consultation → DPAC, clause contractuelle → AE.
     */
    private static final Map<String, Map<String, String>> SUBSTITUTIONS = Map.of(
            "CONTRAT_CADRE", Map.of("DPAO", "DPAC", "CCAP", "AE"));

    /**
     * ⚠️ 2026-09-24 (prestations intellectuelles) — le jeu documentaire d'une catégorie : le document de consultation des
     * prestations intellectuelles est le DPIC. Les champs partagés dont le maître est le DPAO (repris du plan) y vont.
     * Appliquée avant la substitution du type de marché.
     */
    private static final Map<String, Map<String, String>> SUBSTITUTIONS_CATEGORIE = Map.of(
            "PRESTATIONS_INTELLECTUELLES", Map.of("DPAO", "DPIC"));

    /**
     * ⚠️ 2026-09-25 (§B2, dossier réel à commande) — les documents établis <strong>par lot</strong> sur une ligne allotie :
     * l'acte d'engagement (un par lot, avec son montant minimum et maximum) — le reste du DAO est commun aux lots.
     */
    private static final java.util.Set<String> PAR_LOT = java.util.Set.of("AE");

    /**
     * ⚠️ 2026-09-26 (demande front « forme de la garantie de soumission : plusieurs formes admises », §B2) — la forme de
     * la garantie de soumission est une liste à <strong>choix multiples</strong> : l'acheteur admet plusieurs formes, le
     * candidat choisit. Plusieurs formes retenues s'impriment avec la tournure du dossier réel (DPAO, clause 6.6) :
     * « Une garantie de soumission doit être fournie dans l'une des formes suivantes : – soit … – soit … », une forme
     * par ligne ; une seule forme retenue : la ligne ordinaire « libellé : valeur ».
     */
    static final String FORME_GARANTIE_SOUMISSION = "B05-GS-02";

    /** Les quatre formes du CMP avec leur article ; une option qui n'y est pas s'imprime telle quelle. */
    private static final Map<String, String> FORMES_AVEC_ARTICLE = Map.of(
            "Dépôt en numéraire au Trésor", "un dépôt en numéraire au Trésor",
            "Caution personnelle et solidaire d'un organisme agréé par le MEF",
            "une caution personnelle et solidaire d'un organisme agréé par le MEF",
            "Garantie bancaire", "une garantie bancaire",
            "Chèque de banque", "un chèque de banque");

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private SelectionDocumentsFiche() {
    }

    /** Intitulé d'un type de document ({@code DPAO} → « Données particulières de l'appel d'offres »). */
    public static String titre(String type) {
        String t = TITRES.get(type);
        if (t == null) {
            t = FormulairesCandidat.titre(type);   // ⚠️ V46 — A1 à A4, C1, C2
        }
        return t == null ? type : t;
    }

    /** Intitulé d'un document, suivi de son lot s'il est établi par lot (« Acte d'engagement — lot 2 »). */
    public static String titre(String type, Integer lot) {
        return titre(type) + (lot == null ? "" : " — lot " + lot);
    }

    /**
     * @param fiche      l'état figé de la version (valeurs saisies, reprises du PPM, reflets du cadrage, en lettres)
     * @param champs     les champs actifs du référentiel, dans l'ordre (rubrique, rang)
     * @param blocs      les blocs, dans l'ordre des rangs
     * @param rubriques  les rubriques du référentiel
     * @param validation date de validation de la version (pied de page)
     */
    public static List<DocumentFicheModele> selectionner(FicheMarcheDto fiche, List<ChampFicheMarche> champs,
            List<BlocFicheMarche> blocs, List<RubriqueFicheMarche> rubriques, LocalDateTime validation) {
        String typeOuverture = fiche.getTypeMarche();
        // ⚠️ Lot 5 (2026-09-24) — et la catégorie de la fiche (à défaut : fournitures et services).
        String categorieOuverture = fiche.getCategorie() != null ? fiche.getCategorie() : "FOURNITURES_SERVICES";
        Map<String, Object> cadrage = fiche.getCadrage() == null ? Map.of() : fiche.getCadrage();
        Map<String, RubriqueFicheMarche> rubriqueParCode = new LinkedHashMap<>();
        rubriques.forEach(r -> rubriqueParCode.put(r.getCode(), r));
        String pied = "Plan " + (fiche.getRefeDossier() == null ? "—" : fiche.getRefeDossier())
                + " · ligne " + fiche.getIdDetail() + " · fiche marché version " + fiche.getVersion()
                + (validation == null ? "" : " validée le " + validation.toLocalDate().format(JOUR));
        String sousTitre = fiche.getDesignationMarche();
        // ⚠️ 2026-09-25 (§B2) — ligne allotie : les champs par lot se lisent sous CODE#n, et les documents établis par lot
        // (l'acte d'engagement) sont produits une fois par lot.
        int nbLots = Boolean.TRUE.equals(fiche.getSaisieParLot()) && fiche.getNbLots() != null ? fiche.getNbLots() : 0;

        List<DocumentFicheModele> documents = new ArrayList<>();
        for (String type : TYPES) {
            if (LotsFiche.alloti(nbLots) && PAR_LOT.contains(type)) {
                for (int lot = 1; lot <= nbLots; lot++) {
                    DocumentFicheModele m = modele(type, lot, fiche, champs, blocs, rubriqueParCode, typeOuverture,
                            categorieOuverture, cadrage, nbLots, sousTitre, pied);
                    if (m != null) {
                        documents.add(m);
                    }
                }
                continue;
            }
            DocumentFicheModele m = modele(type, null, fiche, champs, blocs, rubriqueParCode, typeOuverture,
                    categorieOuverture, cadrage, nbLots, sousTitre, pied);
            if (m != null) {
                documents.add(m);
            }
        }
        return documents;
    }

    /**
     * Un document ({@code lot} : son rang s'il est établi par lot, {@code null} s'il est commun) ; {@code null} s'il n'a
     * aucune ligne. Un champ par lot y figure pour son lot seulement dans un document de lot, une ligne par lot dans un
     * document commun.
     */
    private static DocumentFicheModele modele(String type, Integer lot, FicheMarcheDto fiche, List<ChampFicheMarche> champs,
            List<BlocFicheMarche> blocs, Map<String, RubriqueFicheMarche> rubriqueParCode, String typeOuverture,
            String categorieOuverture, Map<String, Object> cadrage, int nbLots, String sousTitre, String pied) {
        List<DocumentFicheModele.Bloc> blocsDoc = new ArrayList<>();
        for (BlocFicheMarche bloc : blocs) {
            List<DocumentFicheModele.Rubrique> rubriquesDoc = new ArrayList<>();
            Map<String, List<DocumentFicheModele.Ligne>> lignesParRubrique = new LinkedHashMap<>();
            for (ChampFicheMarche c : champs) {
                if (!bloc.getCode().equals(c.codeBloc()) || !pourDocument(c, type, typeOuverture, categorieOuverture)
                        || !c.pourTypeMarche(typeOuverture)
                        || !c.pourCategorie(categorieOuverture) || !ConditionCadrage.vraie(c.getCondition(), cadrage)) {
                    continue;
                }
                List<DocumentFicheModele.Ligne> lignes = lignesParRubrique.computeIfAbsent(c.getCodeRubrique(),
                        k -> new ArrayList<>());
                if (!LotsFiche.parLot(c, nbLots)) {
                    ajouter(lignes, c.getLibelle(), valeurDocument(c, c.getCode(), fiche));
                } else if (lot != null) {
                    ajouter(lignes, c.getLibelle(), valeurDocument(c, LotsFiche.cle(c.getCode(), lot), fiche));
                } else {
                    for (int n = 1; n <= nbLots; n++) {
                        ajouter(lignes, c.getLibelle() + " — lot " + n, valeurDocument(c, LotsFiche.cle(c.getCode(), n), fiche));
                    }
                }
            }
            lignesParRubrique.values().removeIf(List::isEmpty);
            lignesParRubrique.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(rang(rubriqueParCode.get(a.getKey())), rang(rubriqueParCode.get(b.getKey()))))
                    .forEach(e -> {
                        RubriqueFicheMarche r = rubriqueParCode.get(e.getKey());
                        rubriquesDoc.add(new DocumentFicheModele.Rubrique(r == null ? e.getKey() : r.getLibelle(), e.getValue()));
                    });
            if (!rubriquesDoc.isEmpty()) {
                blocsDoc.add(new DocumentFicheModele.Bloc(bloc.getLibelle(), rubriquesDoc));
            }
        }
        if (blocsDoc.isEmpty()) {
            return null;
        }
        return new DocumentFicheModele(type, titre(type, lot), sousTitre, blocsDoc, pied, lot);
    }

    /** Une ligne « libellé : valeur », omise sans valeur. */
    private static void ajouter(List<DocumentFicheModele.Ligne> lignes, String libelle, String valeur) {
        if (valeur != null) {
            lignes.add(new DocumentFicheModele.Ligne(libelle, valeur));
        }
    }

    /**
     * ⚠️ 2026-09-26 — la valeur telle qu'un document l'imprime : la valeur affichée, sauf la forme de la garantie de
     * soumission à plusieurs formes, rendue par la tournure des formes admises (les lignes sont séparées par {@code \n},
     * que le générateur traduit en sauts de ligne).
     */
    static String valeurDocument(ChampFicheMarche c, String cle, FicheMarcheDto fiche) {
        if (FORME_GARANTIE_SOUMISSION.equals(c.getCode()) && TypeChampFiche.LISTE_MULTIPLE.name().equals(c.getType())) {
            List<String> formes = ChampFicheMarche.liste(
                    premiere(fiche.getValeurs(), fiche.getValeursPpm(), fiche.getValeursCadrage(), cle));
            if (formes.size() > 1) {
                return formesAdmises(formes);
            }
        }
        return valeurAffichee(c, cle, fiche);
    }

    /**
     * Plusieurs formes admises : « Une garantie de soumission doit être fournie dans l'une des formes suivantes : », puis
     * « – soit … » par forme, une par ligne, dans l'ordre reçu (celui du référentiel, tel que la valeur est enregistrée).
     * Une seule forme : elle-même ; aucune : {@code null}.
     */
    static String formesAdmises(List<String> formes) {
        if (formes.size() < 2) {
            return formes.isEmpty() ? null : formes.get(0);
        }
        StringBuilder sb = new StringBuilder("Une garantie de soumission doit être fournie dans l'une des formes suivantes :");
        for (String f : formes) {
            String avecArticle = FORMES_AVEC_ARTICLE.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(f))
                    .map(Map.Entry::getValue).findFirst().orElse(f);
            sb.append("\n– soit ").append(avecArticle);
        }
        return sb.toString();
    }

    /** ⚠️ V45 — champs lus par la liste des fournitures : lieu de livraison (par lot) et délai (à commande / quantité fixe). */
    static final String CHAMP_LIEU_LIVRAISON = "B09-LL-01";
    static final String CHAMP_DELAI_MAXIMUM = "B06-EO-12";
    static final String CHAMP_DELAI = "B06-EO-11";

    /**
     * ⚠️ V45 (2026-09-25, formulaires du candidat, §B3) — la <strong>liste des fournitures et calendrier de
     * livraison</strong> ({@code LF}), entièrement générée depuis le besoin : un tableau par lot (n°, désignation, unité,
     * quantités), suivi du lieu et du délai de livraison du lot. {@code null} sans article.
     */
    public static DocumentFicheModele listeFournitures(FicheMarcheDto fiche, List<BesoinFiche.Article> articles,
            LocalDateTime validation) {
        if (articles == null || articles.isEmpty()) {
            return null;
        }
        boolean aCommande = "A_COMMANDE".equals(fiche.getTypeMarche());
        int nbLots = Boolean.TRUE.equals(fiche.getSaisieParLot()) && fiche.getNbLots() != null ? fiche.getNbLots() : 0;
        List<String> entetes = aCommande ? List.of("N°", "Désignation", "Unité", "Quantité minimum", "Quantité maximum")
                : List.of("N°", "Désignation", "Unité", "Quantité");
        List<DocumentFicheModele.Tableau> tableaux = new ArrayList<>();
        List<Integer> lots = new ArrayList<>();
        if (LotsFiche.alloti(nbLots)) {
            for (int n = 1; n <= nbLots; n++) {
                lots.add(n);
            }
        } else {
            lots.add(null);
        }
        for (Integer lot : lots) {
            List<List<String>> lignes = new ArrayList<>();
            for (BesoinFiche.Article a : articles) {
                if (!java.util.Objects.equals(a.lot(), lot)) {
                    continue;
                }
                lignes.add(aCommande
                        ? List.of(String.valueOf(a.ordre()), a.designation(), a.unite(), entier(a.quantiteMin()), entier(a.quantiteMax()))
                        : List.of(String.valueOf(a.ordre()), a.designation(), a.unite(), entier(a.quantite())));
            }
            if (lignes.isEmpty()) {
                continue;
            }
            List<String> mentions = new ArrayList<>();
            String lieu = valeurDeLot(fiche, CHAMP_LIEU_LIVRAISON, lot);
            if (lieu != null) {
                mentions.add("Lieu de livraison : " + lieu);
            }
            String delai = aCommande ? valeurDeLot(fiche, CHAMP_DELAI_MAXIMUM, lot) : valeurDeLot(fiche, CHAMP_DELAI, lot);
            if (delai != null) {
                mentions.add((aCommande ? "Délai maximum de livraison de chaque commande : " : "Délai de livraison : ")
                        + delai + " jours");
            }
            tableaux.add(new DocumentFicheModele.Tableau(lot == null ? "Fournitures" : "Lot " + lot, entetes, lignes, mentions));
        }
        String pied = "Plan " + (fiche.getRefeDossier() == null ? "—" : fiche.getRefeDossier())
                + " · ligne " + fiche.getIdDetail() + " · fiche marché version " + fiche.getVersion()
                + (validation == null ? "" : " validée le " + validation.toLocalDate().format(JOUR));
        return new DocumentFicheModele("LF", titre("LF"), fiche.getDesignationMarche(), List.of(), pied, null, tableaux);
    }

    /** La valeur saisie d'un champ pour un lot : {@code CODE#n}, à défaut le code nu. */
    private static String valeurDeLot(FicheMarcheDto fiche, String code, Integer lot) {
        Map<String, String> v = fiche.getValeurs() == null ? Map.of() : fiche.getValeurs();
        String x = lot == null ? null : v.get(LotsFiche.cle(code, lot));
        x = x != null ? x : v.get(code);
        return x == null || x.isBlank() ? null : x.trim();
    }

    private static String entier(Integer n) {
        return n == null ? "" : ValeursPpmService.montant(BigDecimal.valueOf(n));
    }

    private static int rang(RubriqueFicheMarche r) {
        return r == null || r.getRang() == null ? Integer.MAX_VALUE : r.getRang();
    }

    /** Le document est le maître du champ, ou le champ y est repris — après substitution propre au type de marché. */
    static boolean pourDocument(ChampFicheMarche c, String type, String typeMarche, String categorie) {
        if (type.equals(document(c.getDocumentMaitre(), typeMarche, categorie))) {
            return true;
        }
        return ChampFicheMarche.liste(c.getReprises()).stream().anyMatch(d -> type.equals(document(d, typeMarche, categorie)));
    }

    /** Le document effectif : substitution de la catégorie, puis du type de marché. */
    private static String document(String doc, String typeMarche, String categorie) {
        String d = SUBSTITUTIONS_CATEGORIE.getOrDefault(categorie, Map.of()).getOrDefault(doc, doc);
        return SUBSTITUTIONS.getOrDefault(typeMarche, Map.of()).getOrDefault(d, d);
    }

    /** La valeur prête à imprimer, ou {@code null} (le champ est alors omis). */
    static String valeurAffichee(ChampFicheMarche c, FicheMarcheDto fiche) {
        return valeurAffichee(c, c.getCode(), fiche);
    }

    /** La valeur du champ lue sous {@code cle} ({@code CODE#n} pour un lot), prête à imprimer, ou {@code null}. */
    static String valeurAffichee(ChampFicheMarche c, String cle, FicheMarcheDto fiche) {
        String brute = premiere(fiche.getValeurs(), fiche.getValeursPpm(), fiche.getValeursCadrage(), cle);
        if (brute == null || brute.isBlank() || "null".equalsIgnoreCase(brute.trim())) {
            return null;
        }
        String v = brute.trim();
        TypeChampFiche type;
        try {
            type = TypeChampFiche.valueOf(c.getType());
        } catch (IllegalArgumentException | NullPointerException e) {
            return v;
        }
        switch (type) {
            case OUI_NON -> {
                return "OUI".equalsIgnoreCase(v) ? "Oui" : "NON".equalsIgnoreCase(v) ? "Non" : v;
            }
            case DATE -> {
                try {
                    return LocalDate.parse(v).format(JOUR);
                } catch (DateTimeParseException e) {
                    return v;
                }
            }
            case MONTANT -> {
                BigDecimal m = ControlesFicheMarche.nombre(v);
                if (m == null) {
                    return v;
                }
                String lettres = fiche.getEnLettres() == null ? null : fiche.getEnLettres().get(cle);
                if (lettres == null) {
                    lettres = MontantEnLettres.ariary(m);
                }
                return ValeursPpmService.montant(m) + " Ariary (" + lettres + ")";
            }
            case POURCENTAGE -> {
                return v.endsWith("%") ? v : v + " %";
            }
            case LISTE_MULTIPLE -> {
                return String.join(", ", ChampFicheMarche.liste(v));   // ⚠️ V45
            }
            default -> {
                return v;
            }
        }
    }

    /** La valeur du champ : saisie, sinon reprise du PPM, sinon reflet du cadrage. */
    private static String premiere(Map<String, String> saisies, Map<String, String> ppm, Map<String, String> cadrage,
            String code) {
        for (Map<String, String> m : java.util.Arrays.asList(saisies, ppm, cadrage)) {
            if (m != null && m.get(code) != null) {
                return m.get(code);
            }
        }
        return null;
    }
}
