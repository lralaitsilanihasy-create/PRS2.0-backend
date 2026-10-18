package cnm.prs.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CodesCitesPointsCtrlDto;
import cnm.prs.entity.BlocFicheMarche;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.PieceSousType;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.RubriqueFicheMarche;
import cnm.prs.enums.CategorieDao;
import cnm.prs.exception.BadRequestException;
import cnm.prs.repository.BlocFicheMarcheRepository;
import cnm.prs.repository.ChampFicheMarcheRepository;
import cnm.prs.repository.PointsCtrlRepository;
import cnm.prs.repository.RubriqueFicheMarcheRepository;

/**
 * ⚠️ 2026-10-18 (demande front « grille-contrat-cadre », §B2) — les codes de la fiche cités dans le texte d'un point de contrôle
 * (« B05-GS-03 », une rubrique « B08-AV », un bloc « B12 ») : l'écran d'examen en fait des liens vers la fiche, ils doivent donc y
 * figurer. Un code cité doit exister au référentiel, actif, pour <strong>chaque</strong> forme à laquelle le point s'applique
 * ({@code CONTRAT_CADRE}, {@code AUTRE} ; les deux pour un point sans forme) et dans <strong>au moins une</strong> de ses
 * catégories (toutes pour un point sans catégorie) — un point peut citer le code des fournitures et celui des travaux. Contrôlé à
 * l'enregistrement par l'Administrateur (400 nominatif) et servi en bilan sur l'existant.
 *
 * <p>⚠️ 2026-10-18 (demande front « grille-travaux ») — un code de bloc ou de rubrique existe là où le référentiel sert ce bloc ou
 * cette rubrique (la rubrique du DQE, {@code B12-DQ}, n'a pas de champ) ; et le bilan <strong>alerte</strong>, sans bloquer, sur
 * une notion qui ne mène à rien pour une catégorie et une forme : chaque parenthèse du texte est une notion (« (B05-GS-03 ;
 * travaux : B05-GQ-03) »), en alerte si aucun de ses codes n'existe pour ce couple, sauf mention « travaux : non saisi ».</p>
 */
@Service
@Transactional(readOnly = true)
public class CodesCitesPointsCtrl {

    /** Un code de la fiche : bloc ({@code B12}), rubrique ({@code B08-AV}) ou champ ({@code B05-GS-03}). */
    static final Pattern CODE = Pattern.compile("\\bB\\d{2}(?:-[A-Z]{2}(?:-\\d{2})?)?\\b");
    /** Une notion : le contenu d'une parenthèse. */
    private static final Pattern NOTION = Pattern.compile("\\(([^()]*)\\)");

    static final String INCONNU = "INCONNU";
    static final String INACTIF = "INACTIF";
    static final String HORS_CATEGORIE = "HORS_CATEGORIE";
    static final String HORS_FORME = "HORS_FORME";
    /** ⚠️ 2026-10-18 (recette du front, §B3) — le champ ne s'affiche que si sa rubrique et son bloc l'admettent aussi. */
    static final String HORS_RUBRIQUE = "HORS_RUBRIQUE";
    /** ⚠️ 2026-10-18 (« grille-travaux », §B2) — l'alerte : une notion sans aucun code pour une catégorie et une forme. */
    static final String SANS_EQUIVALENT_CATEGORIE = "SANS_EQUIVALENT_CATEGORIE";

    /** Le mot qui désigne une catégorie dans le texte d'un point (« travaux : non saisi »). */
    private static final Map<String, String> MOT_CATEGORIE = Map.of(
            CategorieDao.FOURNITURES_SERVICES.name(), "fournitures",
            CategorieDao.TRAVAUX.name(), "travaux",
            CategorieDao.PRESTATIONS_INTELLECTUELLES.name(), "prestations intellectuelles");

    private final ChampFicheMarcheRepository champs;
    private final PointsCtrlRepository points;
    private final RubriqueFicheMarcheRepository rubriques;
    private final BlocFicheMarcheRepository blocs;

    public CodesCitesPointsCtrl(ChampFicheMarcheRepository champs, PointsCtrlRepository points,
            RubriqueFicheMarcheRepository rubriques, BlocFicheMarcheRepository blocs) {
        this.champs = champs;
        this.points = points;
        this.rubriques = rubriques;
        this.blocs = blocs;
    }

    /** Le point enregistré ne cite que des codes présents pour ses formes et catégories ; sinon 400 {@code CODES_ABSENTS}. */
    public void exiger(PointsCtrl p) {
        List<CodesCitesPointsCtrlDto.Ecart> ecarts = referentiel().ecarts(p);
        if (ecarts.isEmpty()) {
            return;
        }
        List<String> lus = ecarts.stream().map(CodesCitesPointsCtrl::lire).toList();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("ecarts", ecarts);
        throw new BadRequestException("Le point « " + p.getLibelPointCtrl() + " » cite des codes absents de la fiche : "
                + String.join(" ; ", lus) + ".", "CODES_ABSENTS", details);
    }

    /**
     * Le bilan sur l'existant : tous les points du référentiel, les seuls en écart détaillés, et les alertes par catégorie.
     *
     * @param categorie nul : toutes ; sinon les alertes de cette catégorie seulement (les écarts ne dépendent pas de la catégorie)
     */
    public CodesCitesPointsCtrlDto bilan(String categorie) {
        Referentiel ref = referentiel();
        int cites = 0;
        int codes = 0;
        List<CodesCitesPointsCtrlDto.Point> enEcart = new ArrayList<>();
        List<CodesCitesPointsCtrlDto.Alerte> alertes = new ArrayList<>();
        for (PointsCtrl p : points.findAll().stream().sorted(Comparator
                .comparing(PointsCtrl::getIdTypeDossier, Comparator.nullsFirst(String::compareTo))
                .thenComparing(PointsCtrl::getIdSousType, Comparator.nullsFirst(String::compareTo))
                .thenComparing(PointsCtrl::getOrdrePointCtrl, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(PointsCtrl::getIdPointCtrl)).toList()) {
            Set<String> c = codes(p);
            if (c.isEmpty()) {
                continue;
            }
            cites++;
            codes += c.size();
            List<CodesCitesPointsCtrlDto.Ecart> ecarts = ref.ecarts(p);
            if (!ecarts.isEmpty()) {
                enEcart.add(new CodesCitesPointsCtrlDto.Point(p.getIdPointCtrl(), p.getLibelPointCtrl(), p.getIdTypeDossier(),
                        p.getIdSousType(), p.getForme(), p.getCategorie(), ecarts));
            }
            ref.alertes(p).stream().filter(a -> categorie == null || categorie.equals(a.categorie())).forEach(alertes::add);
        }
        return new CodesCitesPointsCtrlDto(cites, codes, enEcart, alertes);
    }

    /** Les codes cités par le libellé et la description, dans l'ordre, sans doublon. */
    static Set<String> codes(PointsCtrl p) {
        Set<String> codes = new LinkedHashSet<>();
        for (String texte : new String[] { p.getLibelPointCtrl(), p.getDecriptPointCtrl() }) {
            codes.addAll(codes(texte));
        }
        return codes;
    }

    private static Set<String> codes(String texte) {
        Set<String> codes = new LinkedHashSet<>();
        if (texte != null) {
            Matcher m = CODE.matcher(texte);
            while (m.find()) {
                codes.add(m.group());
            }
        }
        return codes;
    }

    /**
     * Les notions du texte : chaque parenthèse qui cite un code, puis chaque code cité hors parenthèse (seul). Le texte de la
     * notion sert à lire la mention « travaux : non saisi ».
     */
    static List<Notion> notions(PointsCtrl p) {
        List<Notion> notions = new ArrayList<>();
        Set<String> enParenthese = new LinkedHashSet<>();
        for (String texte : new String[] { p.getLibelPointCtrl(), p.getDecriptPointCtrl() }) {
            if (texte == null) {
                continue;
            }
            Matcher m = NOTION.matcher(texte);
            while (m.find()) {
                Set<String> c = codes(m.group(1));
                if (!c.isEmpty()) {
                    notions.add(new Notion(List.copyOf(c), m.group(1)));
                    enParenthese.addAll(c);
                }
            }
        }
        for (String code : codes(p)) {
            if (!enParenthese.contains(code)) {
                notions.add(new Notion(List.of(code), code));
            }
        }
        return notions;
    }

    /** Une notion : ses codes, et son texte. */
    record Notion(List<String> codes, String texte) {

        /** La notion porte la mention « &lt;catégorie&gt; : non saisi(e) » : son absence de la fiche est assumée. */
        boolean nonSaisie(String categorie) {
            String mot = MOT_CATEGORIE.get(categorie);
            return mot != null && Pattern.compile(Pattern.quote(mot) + "\\s*:\\s*non saisie?", Pattern.CASE_INSENSITIVE)
                    .matcher(texte).find();
        }
    }

    private Referentiel referentiel() {
        return new Referentiel(champs.findAll(), rubriques.findAll(), blocs.findAll());
    }

    /** Le référentiel de la fiche, lu une fois : champs, rubriques et blocs, et ce qu'il sert pour une forme et une catégorie. */
    static final class Referentiel {

        private final List<ChampFicheMarche> champs;
        private final Map<String, RubriqueFicheMarche> rubriques;
        private final Map<String, BlocFicheMarche> blocs;
        private final Map<String, List<ChampFicheMarche>> parRubrique;

        Referentiel(List<ChampFicheMarche> champs, List<RubriqueFicheMarche> rubriques, List<BlocFicheMarche> blocs) {
            this.champs = champs;
            this.rubriques = rubriques.stream().collect(Collectors.toMap(RubriqueFicheMarche::getCode, r -> r, (a, b) -> a));
            this.blocs = blocs.stream().collect(Collectors.toMap(BlocFicheMarche::getCode, b -> b, (a, b) -> a));
            this.parRubrique = champs.stream().filter(c -> c.getCodeRubrique() != null)
                    .collect(Collectors.groupingBy(ChampFicheMarche::getCodeRubrique));
        }

        /** Les écarts du point (vide : conforme). */
        List<CodesCitesPointsCtrlDto.Ecart> ecarts(PointsCtrl p) {
            List<CodesCitesPointsCtrlDto.Ecart> ecarts = new ArrayList<>();
            for (String code : codes(p)) {
                if (champ(code)) {
                    ecartsDuChamp(p, code, ecarts);
                } else {
                    ecartsDuGroupe(p, code, ecarts);
                }
            }
            return ecarts;
        }

        /**
         * Les alertes du point : pour chaque forme du point et chaque catégorie qu'il touche (l'un de ses codes au moins y existe),
         * les notions dont aucun code n'existe, hors mention « non saisi ».
         */
        List<CodesCitesPointsCtrlDto.Alerte> alertes(PointsCtrl p) {
            List<CodesCitesPointsCtrlDto.Alerte> alertes = new ArrayList<>();
            Set<String> codes = codes(p);
            List<Notion> notions = notions(p);
            for (String forme : formes(p)) {
                for (String categorie : categories(p)) {
                    if (codes.stream().noneMatch(c -> existe(c, forme, categorie))) {
                        continue;   // la catégorie n'est pas celle du point pour cette forme (prestations intellectuelles…)
                    }
                    for (Notion n : notions) {
                        if (n.codes().stream().noneMatch(c -> existe(c, forme, categorie)) && !n.nonSaisie(categorie)) {
                            alertes.add(new CodesCitesPointsCtrlDto.Alerte(p.getIdPointCtrl(), p.getLibelPointCtrl(),
                                    p.getIdSousType(), p.getForme(), n.codes(), forme, categorie, SANS_EQUIVALENT_CATEGORIE));
                        }
                    }
                }
            }
            return alertes;
        }

        private void ecartsDuChamp(PointsCtrl p, String code, List<CodesCitesPointsCtrlDto.Ecart> ecarts) {
            List<ChampFicheMarche> designes = champs.stream().filter(c -> code.equals(c.getCode())).toList();
            List<ChampFicheMarche> actifs = designes.stream().filter(c -> Boolean.TRUE.equals(c.getActif())).toList();
            List<ChampFicheMarche> categorie = actifs.stream().filter(c -> c.pourCategorie(p.getCategorie())).toList();
            if (designes.isEmpty()) {
                ecarts.add(new CodesCitesPointsCtrlDto.Ecart(code, INCONNU, null));
            } else if (actifs.isEmpty()) {
                ecarts.add(new CodesCitesPointsCtrlDto.Ecart(code, INACTIF, null));
            } else if (categorie.isEmpty()) {
                ecarts.add(new CodesCitesPointsCtrlDto.Ecart(code, HORS_CATEGORIE, null));
            } else {
                for (String forme : formes(p)) {
                    if (categorie.stream().noneMatch(c -> pourForme(ChampFicheMarche.liste(c.getTypesMarche()), forme))) {
                        ecarts.add(new CodesCitesPointsCtrlDto.Ecart(code, HORS_FORME, forme));
                    } else if (!existe(code, forme, p.getCategorie())) {
                        ecarts.add(new CodesCitesPointsCtrlDto.Ecart(code, HORS_RUBRIQUE, forme));
                    }
                }
            }
        }

        /** Un bloc ou une rubrique : connu, servi pour chaque forme du point (dans l'une de ses catégories). */
        private void ecartsDuGroupe(PointsCtrl p, String code, List<CodesCitesPointsCtrlDto.Ecart> ecarts) {
            boolean bloc = code.indexOf('-') < 0;
            if (bloc ? !blocs.containsKey(code) : !rubriques.containsKey(code)) {
                ecarts.add(new CodesCitesPointsCtrlDto.Ecart(code, INCONNU, null));
                return;
            }
            if (!bloc && !parRubrique.getOrDefault(code, List.of()).isEmpty()
                    && parRubrique.get(code).stream().noneMatch(c -> Boolean.TRUE.equals(c.getActif()))) {
                ecarts.add(new CodesCitesPointsCtrlDto.Ecart(code, INACTIF, null));
                return;
            }
            for (String forme : formes(p)) {
                if (!existe(code, forme, p.getCategorie())) {
                    ecarts.add(new CodesCitesPointsCtrlDto.Ecart(code, HORS_FORME, forme));
                }
            }
        }

        /**
         * Le code mène-t-il à la fiche pour la forme ({@code CONTRAT_CADRE}, {@code AUTRE}) et la catégorie (nulle : l'une
         * quelconque) ? Un champ : actif, ouvert avec sa rubrique et son bloc ; une rubrique : servie (ouverte avec son bloc, et
         * l'un de ses champs actifs l'est aussi, ou elle n'en a aucun) ; un bloc : l'une de ses rubriques est servie.
         */
        boolean existe(String code, String forme, String categorie) {
            if (categorie == null) {   // l'une quelconque : la même catégorie doit valoir pour le champ, sa rubrique et son bloc
                return java.util.Arrays.stream(CategorieDao.values()).anyMatch(c -> existe(code, forme, c.name()));
            }
            if (champ(code)) {
                return champs.stream().filter(c -> code.equals(c.getCode()) && Boolean.TRUE.equals(c.getActif()))
                        .anyMatch(c -> {
                            RubriqueFicheMarche r = rubriques.get(c.getCodeRubrique());
                            return admet(ChampFicheMarche.liste(c.getTypesMarche()), ChampFicheMarche.liste(c.getCategories()), forme, categorie)
                                    && (r == null || rubriqueOuverte(r, forme, categorie));
                        });
            }
            if (code.indexOf('-') > 0) {
                RubriqueFicheMarche r = rubriques.get(code);
                return r != null && rubriqueServie(r, forme, categorie);
            }
            return rubriques.values().stream().filter(r -> code.equals(r.getCodeBloc())).anyMatch(r -> rubriqueServie(r, forme, categorie));
        }

        private boolean rubriqueServie(RubriqueFicheMarche r, String forme, String categorie) {
            List<ChampFicheMarche> siens = parRubrique.getOrDefault(r.getCode(), List.of());
            return rubriqueOuverte(r, forme, categorie) && (siens.isEmpty() || siens.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getActif()))
                    .anyMatch(c -> admet(ChampFicheMarche.liste(c.getTypesMarche()), ChampFicheMarche.liste(c.getCategories()), forme, categorie)));
        }

        /** La rubrique et son bloc admettent la forme et la catégorie. */
        private boolean rubriqueOuverte(RubriqueFicheMarche r, String forme, String categorie) {
            List<String> types = ChampFicheMarche.liste(r.getTypesMarche());
            List<String> categories = ChampFicheMarche.liste(r.getCategories());
            BlocFicheMarche b = blocs.get(r.getCodeBloc());
            if (b != null) {
                types = commun(types, b.getTypesMarche());
                categories = commun(categories, b.getCategories());
            }
            return admet(types, categories, forme, categorie);
        }

        private static boolean admet(List<String> types, List<String> categories, String forme, String categorie) {
            return pourForme(types, forme) && categories.contains(categorie);
        }

        private static List<String> commun(List<String> valeurs, String csv) {
            List<String> admises = ChampFicheMarche.liste(csv);
            return valeurs.stream().filter(admises::contains).toList();
        }
    }

    /** Un code de champ ({@code B05-GS-03}), par opposition à une rubrique ou à un bloc. */
    private static boolean champ(String code) {
        return code.chars().filter(ch -> ch == '-').count() == 2;
    }

    private static List<String> formes(PointsCtrl p) {
        return p.getForme() == null ? List.of(PieceSousType.CONTRAT_CADRE, PieceSousType.AUTRE) : List.of(p.getForme());
    }

    /**
     * Les catégories de fiche que le point peut examiner : la sienne ; à défaut, celles de son sous-type — un dossier d'appel
     * d'offres naît d'une fiche de fournitures ou de travaux, un {@code DC} d'une fiche de prestations intellectuelles
     * ({@link SousTypesDossier#dossierMiseEnConcurrence(String, boolean)}) ; sinon toutes.
     */
    private static List<String> categories(PointsCtrl p) {
        if (p.getCategorie() != null) {
            return List.of(p.getCategorie());
        }
        if (SousTypesDossier.DC.equals(p.getIdSousType())) {
            return List.of(CategorieDao.PRESTATIONS_INTELLECTUELLES.name());
        }
        if (APPELS_D_OFFRES.contains(p.getIdSousType())) {
            return List.of(CategorieDao.FOURNITURES_SERVICES.name(), CategorieDao.TRAVAUX.name());
        }
        return java.util.Arrays.stream(CategorieDao.values()).map(Enum::name).toList();
    }

    /** Les sous-types qu'une fiche de fournitures ou de travaux produit (et ceux qui en héritent la grille). */
    private static final Set<String> APPELS_D_OFFRES = Set.of(SousTypesDossier.DAOO, SousTypesDossier.DAOO_CC, SousTypesDossier.DAOR,
            SousTypesDossier.DAOOI, SousTypesDossier.DAORI, SousTypesDossier.DAOOPREQUAL);

    /** Les formes admises valent pour celle du point : le contrat-cadre, ou l'une des autres formes. */
    private static boolean pourForme(List<String> types, String forme) {
        return PieceSousType.CONTRAT_CADRE.equals(forme) ? types.contains(PieceSousType.CONTRAT_CADRE)
                : types.stream().anyMatch(t -> !PieceSousType.CONTRAT_CADRE.equals(t));
    }

    private static String lire(CodesCitesPointsCtrlDto.Ecart e) {
        return e.code() + " " + switch (e.motif()) {
            case INCONNU -> "inconnu du référentiel";
            case INACTIF -> "retiré du référentiel";
            case HORS_CATEGORIE -> "absent pour la catégorie du point";
            case HORS_RUBRIQUE -> "non affiché : sa rubrique n'est pas ouverte "
                    + (PieceSousType.CONTRAT_CADRE.equals(e.forme()) ? "au contrat-cadre" : "aux formes autres que le contrat-cadre");
            default -> PieceSousType.CONTRAT_CADRE.equals(e.forme()) ? "absent pour le contrat-cadre"
                    : "absent pour les formes autres que le contrat-cadre";
        };
    }
}
