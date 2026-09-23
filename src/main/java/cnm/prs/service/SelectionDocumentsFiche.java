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
    public static final List<String> TYPES = List.of("DPAO", "DPAC", "CCAP", "AE");

    private static final Map<String, String> TITRES = Map.of(
            "DPAO", "Données particulières de l'appel d'offres",
            "DPAC", "Données particulières du cahier des clauses administratives",
            "CCAP", "Cahier des clauses administratives particulières",
            "AE", "Acte d'engagement");

    /**
     * ⚠️ Lot 4 (2026-09-23) — le jeu documentaire d'un type de marché. En contrat-cadre, le deuxième document est le
     * {@code DPAC} et il n'y a pas de CCAP : l'acte d'engagement est le contrat. Les champs partagés par les trois types
     * (repris du plan, reflets du cadrage), dont le maître est le DPAO et qui sont repris au CCAP, y suivent la règle de
     * répartition du fichier de correspondance : clause de consultation → DPAC, clause contractuelle → AE.
     */
    private static final Map<String, Map<String, String>> SUBSTITUTIONS = Map.of(
            "CONTRAT_CADRE", Map.of("DPAO", "DPAC", "CCAP", "AE"));

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private SelectionDocumentsFiche() {
    }

    /** Intitulé d'un type de document ({@code DPAO} → « Données particulières de l'appel d'offres »). */
    public static String titre(String type) {
        return TITRES.getOrDefault(type, type);
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
        Map<String, Object> cadrage = fiche.getCadrage() == null ? Map.of() : fiche.getCadrage();
        Map<String, RubriqueFicheMarche> rubriqueParCode = new LinkedHashMap<>();
        rubriques.forEach(r -> rubriqueParCode.put(r.getCode(), r));
        String pied = "Plan " + (fiche.getRefeDossier() == null ? "—" : fiche.getRefeDossier())
                + " · ligne " + fiche.getIdDetail() + " · fiche marché version " + fiche.getVersion()
                + (validation == null ? "" : " validée le " + validation.toLocalDate().format(JOUR));
        String sousTitre = fiche.getDesignationMarche();

        List<DocumentFicheModele> documents = new ArrayList<>();
        for (String type : TYPES) {
            List<DocumentFicheModele.Bloc> blocsDoc = new ArrayList<>();
            for (BlocFicheMarche bloc : blocs) {
                List<DocumentFicheModele.Rubrique> rubriquesDoc = new ArrayList<>();
                Map<String, List<DocumentFicheModele.Ligne>> lignesParRubrique = new LinkedHashMap<>();
                for (ChampFicheMarche c : champs) {
                    if (!bloc.getCode().equals(c.codeBloc()) || !pourDocument(c, type, typeOuverture)
                            || !c.pourTypeMarche(typeOuverture) || !ConditionCadrage.vraie(c.getCondition(), cadrage)) {
                        continue;
                    }
                    String valeur = valeurAffichee(c, fiche);
                    if (valeur == null) {
                        continue;
                    }
                    lignesParRubrique.computeIfAbsent(c.getCodeRubrique(), k -> new ArrayList<>())
                            .add(new DocumentFicheModele.Ligne(c.getLibelle(), valeur));
                }
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
            if (!blocsDoc.isEmpty()) {
                documents.add(new DocumentFicheModele(type, titre(type), sousTitre, blocsDoc, pied));
            }
        }
        return documents;
    }

    private static int rang(RubriqueFicheMarche r) {
        return r == null || r.getRang() == null ? Integer.MAX_VALUE : r.getRang();
    }

    /** Le document est le maître du champ, ou le champ y est repris — après substitution propre au type de marché. */
    static boolean pourDocument(ChampFicheMarche c, String type, String typeMarche) {
        Map<String, String> sub = SUBSTITUTIONS.getOrDefault(typeMarche, Map.of());
        if (type.equals(sub.getOrDefault(c.getDocumentMaitre(), c.getDocumentMaitre()))) {
            return true;
        }
        return ChampFicheMarche.liste(c.getReprises()).stream().anyMatch(d -> type.equals(sub.getOrDefault(d, d)));
    }

    /** La valeur prête à imprimer, ou {@code null} (le champ est alors omis). */
    static String valeurAffichee(ChampFicheMarche c, FicheMarcheDto fiche) {
        String brute = premiere(fiche.getValeurs(), fiche.getValeursPpm(), fiche.getValeursCadrage(), c.getCode());
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
                String lettres = fiche.getEnLettres() == null ? null : fiche.getEnLettres().get(c.getCode());
                if (lettres == null) {
                    lettres = MontantEnLettres.ariary(m);
                }
                return ValeursPpmService.montant(m) + " Ariary (" + lettres + ")";
            }
            case POURCENTAGE -> {
                return v.endsWith("%") ? v : v + " %";
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
