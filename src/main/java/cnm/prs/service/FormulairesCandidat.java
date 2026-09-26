package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.enums.CategorieDao;
import cnm.prs.enums.TypeChampFiche;

/**
 * ⚠️ <strong>Les formulaires du candidat sur les modèles officiels</strong> (demande front du 2026-09-25, §B8 ; arbitrages du
 * pilote du 26/09, R1-R12) — fiches de renseignements A1 à A4 (une fois pour le dossier) et garanties de soumission C1 /
 * C2 (une par lot), toutes catégories, rendus depuis les <strong>fichiers de commande du décalque</strong>
 * ({@code modeles/candidat/<sigle>.txt}, copiés tels quels du dépôt front — le texte du dossier 2463 au caractère près).
 *
 * <p><strong>Le contrat des jetons</strong>, tous de la forme {@code {{…}}}, traités <em>avant</em> le rendu :</p>
 * <ul>
 *   <li>{@code {{CODE}}} : la valeur de la fiche (saisie, reprise du plan ou reflet), pour le lot du document si le champ
 *       est par lot — montant « 1 600 000 Ariary », date « JJ/MM/AAAA », liste à choix multiples jointe par des virgules ;</li>
 *   <li>{@code {{CODE.lettres}}} : un montant en toutes lettres ({@code MontantEnLettres.ariary}), un nombre en lettres
 *       ({@code NombreEnLettres.cardinal}) ;</li>
 *   <li>{@code {{CODE.doublet}}} : l'ordinal et son abrégé (« cent cinquième (105ème) ») ;</li>
 *   <li>{@code {{DERIVE.delai-garantie.doublet}}} : idem sur {@code B05-GS-04 − B04-VO-01} (« trentième (30ème) »),
 *       protégé par le contrôle {@code VALIDITE_GARANTIE_SUP_OFFRE} ; {@code {{DERIVE.fin-validite-offre}}} :
 *       {@code B04-LR-03 + B04-VO-01} jours, en date — calculés, jamais stockés ;</li>
 *   <li>{@code {{A1B.mention}}} : « (non applicable) » quand le cadrage n'autorise pas le groupement, rien sinon — et le
 *       paragraphe est retiré (R9) ;</li>
 *   <li>marqueurs {@code {{SI:A1B}}}…{@code {{FINSI:A1B}}} : les paragraphes entre les deux sont omis sans groupement ;
 *       {@code {{SI:A3B-NATURES}}}…{@code {{FINSI:A3B-NATURES}}} : les lignes de tableau de la plage (de la ligne qui
 *       contient SI à celle qui contient FINSI, R7) sont régénérées, une par nature du marché, déduites de la catégorie
 *       (R8) ; les marqueurs sont toujours retirés ;</li>
 *   <li>un jeton dont la valeur manque s'imprime en <strong>pointillés</strong> « ……… » (R2), pour que le papier reste
 *       remplissable ; un jeton inconnu du contrat est laissé tel quel.</li>
 * </ul>
 *
 * <p>Classe pure : la fiche figée, le référentiel des champs et les modèles lus en entrée, des {@link DocumentLibre} en
 * sortie. Le rendu sans substitution ({@link #brut}) sert au comparateur de fidélité du front.</p>
 */
public final class FormulairesCandidat {

    public static final List<String> FICHES = List.of("A1", "A2", "A3", "A4");
    public static final List<String> GARANTIES = List.of("C1", "C2");
    public static final String POINTILLES = "………";

    /** Les champs qui commandent les formulaires ou alimentent les dérivés (§B8). */
    static final String FICHES_EXIGEES = "B04-CD-01";
    static final String FORME_GARANTIE = "B04-CD-02";
    static final String VALIDITE_GARANTIE = "B05-GS-04";
    static final String REMISE_OFFRES = "B04-LR-03";
    static final String VALIDITE_OFFRES = "B04-VO-01";

    private static final Pattern JETON = Pattern.compile("\\{\\{([^{}]+)}}");
    private static final Pattern MARQUEUR = Pattern.compile("\\{\\{(SI|FINSI):([A-Z0-9-]+)}}");
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Map<String, String> TITRES = Map.of(
            "A1", "Fiche de renseignements A1",
            "A2", "Fiche de renseignements A2",
            "A3", "Fiche de renseignements A3",
            "A4", "Fiche de renseignements A4",
            "C1", "Garantie bancaire de soumission (C1)",
            "C2", "Caution personnelle et solidaire de soumission (C2)");

    private FormulairesCandidat() {
    }

    public static String titre(String type) {
        return TITRES.get(type);
    }

    /**
     * @param fiche      l'état figé de la version
     * @param champs     le référentiel des champs, par code (types, par lot)
     * @param modeles    les éléments de chaque modèle, par sigle ({@link ModelesCandidat})
     * @param validation date de validation (pied de page)
     */
    public static List<DocumentLibre> generer(FicheMarcheDto fiche, Map<String, ChampFicheMarche> champs,
            Map<String, List<DocumentLibre.Element>> modeles, LocalDateTime validation) {
        List<DocumentLibre> documents = new ArrayList<>();
        String pied = pied(fiche, validation);
        Contexte ctx = new Contexte(fiche, champs);
        for (String a : ChampFicheMarche.liste(valeur(fiche, FICHES_EXIGEES, null))) {
            if (FICHES.contains(a) && modeles.containsKey(a)) {
                documents.add(new DocumentLibre(a, null, ctx.rendre(modeles.get(a), null), pied));   // R11 : une fois pour le dossier
            }
        }
        String forme = valeur(fiche, FORME_GARANTIE, null);
        List<String> garanties = forme == null ? List.of()
                : forme.contains("C1") && forme.contains("C2") ? GARANTIES : List.of(forme.trim().toUpperCase());
        int nbLots = Boolean.TRUE.equals(fiche.getSaisieParLot()) && fiche.getNbLots() != null ? fiche.getNbLots() : 0;
        for (String c : garanties) {
            if (!GARANTIES.contains(c) || !modeles.containsKey(c)) {
                continue;
            }
            if (LotsFiche.alloti(nbLots)) {
                for (int lot = 1; lot <= nbLots; lot++) {
                    documents.add(new DocumentLibre(c, lot, ctx.rendre(modeles.get(c), lot), pied));
                }
            } else {
                documents.add(new DocumentLibre(c, null, ctx.rendre(modeles.get(c), null), pied));
            }
        }
        return documents;
    }

    /** Le modèle tel quel, jetons non substitués : ce que le comparateur de fidélité du front relit. */
    public static DocumentLibre brut(String sigle, List<DocumentLibre.Element> modele) {
        return new DocumentLibre(sigle, null, modele, "Modèle " + sigle + " — rendu brut, jetons non substitués");
    }

    private static String pied(FicheMarcheDto fiche, LocalDateTime validation) {
        return "Plan " + (fiche.getRefeDossier() == null ? "—" : fiche.getRefeDossier()) + " · ligne "
                + fiche.getIdDetail() + " · fiche marché version " + fiche.getVersion()
                + (validation == null ? "" : " validée le " + validation.toLocalDate().format(JOUR));
    }

    // ------------------------------------------------------------------ substitution et marqueurs

    /** Le contexte d'une fiche : ce que valent les conditions, les répétitions et les jetons. */
    private record Contexte(FicheMarcheDto fiche, Map<String, ChampFicheMarche> champs) {

        boolean groupement() {
            return fiche.getCadrage() != null && "OUI".equalsIgnoreCase(String.valueOf(fiche.getCadrage().get("groupement")));
        }

        /** Une condition de section par son nom ; inconnue : vraie (le texte est gardé, les marqueurs retirés). */
        boolean condition(String nom) {
            return !"A1B".equals(nom) || groupement();
        }

        /** Les valeurs d'une plage régénérée par son nom ; {@code null} si ce n'est pas une répétition. */
        List<String> repetition(String nom) {
            if (!"A3B-NATURES".equals(nom)) {
                return null;
            }
            String categorie = fiche.getCategorie();
            if (CategorieDao.TRAVAUX.name().equals(categorie)) {
                return List.of("Travaux");
            }
            if (CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie)) {
                return List.of("Prestations intellectuelles");
            }
            return List.of("Fournitures", "Services");
        }

        List<DocumentLibre.Element> rendre(List<DocumentLibre.Element> modele, Integer lot) {
            List<DocumentLibre.Element> out = new ArrayList<>();
            String sectionOmise = null;   // nom de la section SI:… en cours d'omission
            for (DocumentLibre.Element e : modele) {
                if (e instanceof DocumentLibre.Paragraphe p) {
                    Matcher m = MARQUEUR.matcher(p.texte().trim());
                    if (m.matches() && m.group(0).equals(p.texte().trim())) {
                        String nom = m.group(2);
                        if ("SI".equals(m.group(1))) {
                            if (sectionOmise == null && !condition(nom)) {
                                sectionOmise = nom;
                            }
                        } else if (nom.equals(sectionOmise)) {
                            sectionOmise = null;
                        }
                        continue;   // un marqueur n'est jamais imprimé
                    }
                    if (sectionOmise != null) {
                        continue;
                    }
                    String texte = substituer(p.texte(), lot);
                    if (texte == null) {
                        continue;   // R9 : un paragraphe fait d'une seule mention vide est retiré
                    }
                    out.add(new DocumentLibre.Paragraphe(p.style(), texte));
                } else if (e instanceof DocumentLibre.Tableau t) {
                    if (sectionOmise != null) {
                        continue;
                    }
                    out.add(new DocumentLibre.Tableau(t.colonnes(), lignes(t, lot)));
                }
            }
            return out;
        }

        /** Les lignes d'un tableau : plages régénérées ou conditionnelles, puis substitution cellule par cellule. */
        private List<List<List<String>>> lignes(DocumentLibre.Tableau t, Integer lot) {
            List<List<List<String>>> out = new ArrayList<>();
            int i = 0;
            while (i < t.lignes().size()) {
                List<List<String>> ligne = t.lignes().get(i);
                String nom = marqueur(ligne, "SI");
                if (nom == null) {
                    out.add(substituer(ligne, lot));
                    i++;
                    continue;
                }
                int fin = i;
                while (fin < t.lignes().size() && !nom.equals(marqueur(t.lignes().get(fin), "FINSI"))) {
                    fin++;
                }
                fin = Math.min(fin, t.lignes().size() - 1);
                List<String> valeurs = repetition(nom);
                if (valeurs != null) {
                    List<List<String>> gabarit = sansMarqueurs(t.lignes().get(i));
                    for (String v : valeurs) {
                        List<List<String>> copie = new ArrayList<>();
                        for (int c = 0; c < gabarit.size(); c++) {
                            copie.add(new ArrayList<>(gabarit.get(c)));
                        }
                        if (!copie.isEmpty() && !copie.get(0).isEmpty()) {
                            copie.get(0).set(0, v);
                        }
                        out.add(substituer(copie, lot));
                    }
                } else if (condition(nom)) {
                    for (int k = i; k <= fin; k++) {
                        out.add(substituer(sansMarqueurs(t.lignes().get(k)), lot));
                    }
                }
                i = fin + 1;
            }
            return out;
        }

        private static String marqueur(List<List<String>> ligne, String sorte) {
            for (List<String> cellule : ligne) {
                for (String paragraphe : cellule) {
                    Matcher m = MARQUEUR.matcher(paragraphe);
                    while (m.find()) {
                        if (sorte.equals(m.group(1))) {
                            return m.group(2);
                        }
                    }
                }
            }
            return null;
        }

        private static List<List<String>> sansMarqueurs(List<List<String>> ligne) {
            List<List<String>> out = new ArrayList<>();
            for (List<String> cellule : ligne) {
                List<String> c = new ArrayList<>();
                for (String paragraphe : cellule) {
                    c.add(MARQUEUR.matcher(paragraphe).replaceAll(""));
                }
                out.add(c);
            }
            return out;
        }

        private List<List<String>> substituer(List<List<String>> ligne, Integer lot) {
            List<List<String>> out = new ArrayList<>();
            for (List<String> cellule : ligne) {
                List<String> c = new ArrayList<>();
                for (String paragraphe : cellule) {
                    String s = substituer(paragraphe, lot);
                    c.add(s == null ? "" : s);
                }
                out.add(c);
            }
            return out;
        }

        /** Le texte substitué ; {@code null} si le texte n'était qu'un jeton qui vaut la chaîne vide (mention). */
        private String substituer(String texte, Integer lot) {
            Matcher m = JETON.matcher(texte);
            StringBuilder sb = new StringBuilder();
            boolean seul = m.matches();
            m.reset();
            while (m.find()) {
                String v = jeton(m.group(1).trim(), lot);
                if (seul && v != null && v.isEmpty()) {
                    return null;
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? m.group(0) : v));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        /** La valeur d'un jeton ; {@code null} : jeton inconnu du contrat (laissé tel quel). */
        private String jeton(String nom, Integer lot) {
            if ("A1B.mention".equals(nom)) {
                return groupement() ? "" : "(non applicable)";
            }
            if ("DERIVE.delai-garantie.doublet".equals(nom)) {
                BigDecimal g = ControlesFicheMarche.nombre(valeur(fiche, VALIDITE_GARANTIE, null));
                BigDecimal o = ControlesFicheMarche.nombre(valeur(fiche, VALIDITE_OFFRES, null));
                return g == null || o == null || g.subtract(o).signum() <= 0 ? POINTILLES
                        : NombreEnLettres.doublet(g.subtract(o).longValue(), false);
            }
            if ("DERIVE.fin-validite-offre".equals(nom)) {
                LocalDate remise = ControlesFicheMarche.date(valeur(fiche, REMISE_OFFRES, null));
                BigDecimal jours = ControlesFicheMarche.nombre(valeur(fiche, VALIDITE_OFFRES, null));
                return remise == null || jours == null ? POINTILLES : remise.plusDays(jours.longValue()).format(JOUR);
            }
            if (nom.startsWith("DERIVE.") || nom.startsWith("SI:") || nom.startsWith("FINSI:")) {
                return null;
            }
            int point = nom.indexOf('.');
            String code = point < 0 ? nom : nom.substring(0, point);
            String suffixe = point < 0 ? "" : nom.substring(point + 1);
            ChampFicheMarche c = champs.get(code);
            if (c == null && !code.matches("B\\d{2}-[A-Z0-9]{1,6}-\\d{2}")) {
                return null;
            }
            String brut = valeur(fiche, code, c != null && Boolean.TRUE.equals(c.getParLot()) ? lot : null);
            if (brut == null) {
                return POINTILLES;
            }
            String type = c == null ? TypeChampFiche.TEXTE.name() : c.getType();
            BigDecimal n = ControlesFicheMarche.nombre(brut);
            return switch (suffixe) {
                case "lettres" -> TypeChampFiche.MONTANT.name().equals(type) && n != null ? MontantEnLettres.ariary(n)
                        : n != null && n.stripTrailingZeros().scale() <= 0 ? NombreEnLettres.cardinal(n.longValue()) : brut;
                case "doublet" -> n != null && n.signum() > 0 ? NombreEnLettres.doublet(n.longValue(), false) : POINTILLES;
                case "" -> affichage(type, brut, n);
                default -> null;
            };
        }

        /** La valeur d'un champ, selon son type : montant en chiffres avec l'unité, date JJ/MM/AAAA, Oui/Non, listes. */
        private static String affichage(String type, String brut, BigDecimal n) {
            if (TypeChampFiche.MONTANT.name().equals(type) && n != null) {
                return ValeursPpmService.montant(n) + " Ariary";
            }
            if (TypeChampFiche.DATE.name().equals(type)) {
                LocalDate d = ControlesFicheMarche.date(brut);
                return d == null ? brut : d.format(JOUR);
            }
            if (TypeChampFiche.OUI_NON.name().equals(type)) {
                return "OUI".equalsIgnoreCase(brut) ? "Oui" : "NON".equalsIgnoreCase(brut) ? "Non" : brut;
            }
            if (TypeChampFiche.LISTE_MULTIPLE.name().equals(type)) {
                return String.join(", ", ChampFicheMarche.liste(brut));
            }
            if (TypeChampFiche.POURCENTAGE.name().equals(type)) {
                return brut.endsWith("%") ? brut : brut + " %";
            }
            return brut;
        }
    }

    /** La valeur d'un champ (saisie, reprise du plan, reflet) ; pour un lot, {@code CODE#n} puis le code nu. */
    static String valeur(FicheMarcheDto fiche, String code, Integer lot) {
        for (Map<String, String> m : java.util.Arrays.asList(fiche.getValeurs(), fiche.getValeursPpm(), fiche.getValeursCadrage())) {
            if (m == null) {
                continue;
            }
            String v = lot == null ? null : m.get(LotsFiche.cle(code, lot));
            v = v != null ? v : m.get(code);
            if (v != null && !v.isBlank() && !"null".equalsIgnoreCase(v.trim())) {
                return v.trim();
            }
        }
        return null;
    }

    /** Fabrique de résolveurs pour les tests : inutilisée en production. */
    static Function<String, String> resolveur(FicheMarcheDto fiche, Map<String, ChampFicheMarche> champs, Integer lot) {
        Contexte ctx = new Contexte(fiche, champs);
        return nom -> ctx.jeton(nom, lot);
    }
}
