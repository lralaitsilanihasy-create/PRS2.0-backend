package cnm.prs.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CodesCitesPointsCtrlDto;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.PieceSousType;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.exception.BadRequestException;
import cnm.prs.repository.ChampFicheMarcheRepository;
import cnm.prs.repository.PointsCtrlRepository;

/**
 * ⚠️ 2026-10-18 (demande front « grille-contrat-cadre », §B2) — les codes de la fiche cités dans le texte d'un point de contrôle
 * (« B05-GS-03 », une rubrique « B08-AV », un bloc « B12 ») : l'écran d'examen en fait des liens vers la fiche, ils doivent donc y
 * figurer. Un code cité doit exister au référentiel, actif, pour <strong>chaque</strong> forme à laquelle le point s'applique
 * ({@code CONTRAT_CADRE}, {@code AUTRE} ; les deux pour un point sans forme) et dans <strong>au moins une</strong> de ses
 * catégories (toutes pour un point sans catégorie) — un point peut citer le code des fournitures et celui des travaux. Contrôlé à
 * l'enregistrement par l'Administrateur (400 nominatif) et servi en bilan sur l'existant.
 */
@Service
@Transactional(readOnly = true)
public class CodesCitesPointsCtrl {

    /** Un code de la fiche : bloc ({@code B12}), rubrique ({@code B08-AV}) ou champ ({@code B05-GS-03}). */
    static final Pattern CODE = Pattern.compile("\\bB\\d{2}(?:-[A-Z]{2}(?:-\\d{2})?)?\\b");

    static final String INCONNU = "INCONNU";
    static final String INACTIF = "INACTIF";
    static final String HORS_CATEGORIE = "HORS_CATEGORIE";
    static final String HORS_FORME = "HORS_FORME";

    private final ChampFicheMarcheRepository champs;
    private final PointsCtrlRepository points;

    public CodesCitesPointsCtrl(ChampFicheMarcheRepository champs, PointsCtrlRepository points) {
        this.champs = champs;
        this.points = points;
    }

    /** Le point enregistré ne cite que des codes présents pour ses formes et catégories ; sinon 400 {@code CODES_ABSENTS}. */
    public void exiger(PointsCtrl p) {
        List<CodesCitesPointsCtrlDto.Ecart> ecarts = ecarts(p, champs.findAll());
        if (ecarts.isEmpty()) {
            return;
        }
        List<String> lus = ecarts.stream().map(CodesCitesPointsCtrl::lire).toList();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("ecarts", ecarts);
        throw new BadRequestException("Le point « " + p.getLibelPointCtrl() + " » cite des codes absents de la fiche : "
                + String.join(" ; ", lus) + ".", "CODES_ABSENTS", details);
    }

    /** Le bilan sur l'existant : tous les points du référentiel, les seuls en écart détaillés. */
    public CodesCitesPointsCtrlDto bilan() {
        List<ChampFicheMarche> referentiel = champs.findAll();
        int cites = 0;
        int codes = 0;
        List<CodesCitesPointsCtrlDto.Point> enEcart = new ArrayList<>();
        for (PointsCtrl p : points.findAll().stream().sorted(java.util.Comparator
                .comparing(PointsCtrl::getIdTypeDossier, java.util.Comparator.nullsFirst(String::compareTo))
                .thenComparing(PointsCtrl::getIdSousType, java.util.Comparator.nullsFirst(String::compareTo))
                .thenComparing(PointsCtrl::getOrdrePointCtrl, java.util.Comparator.nullsLast(Integer::compareTo))
                .thenComparing(PointsCtrl::getIdPointCtrl)).toList()) {
            Set<String> c = codes(p);
            if (c.isEmpty()) {
                continue;
            }
            cites++;
            codes += c.size();
            List<CodesCitesPointsCtrlDto.Ecart> ecarts = ecarts(p, referentiel);
            if (!ecarts.isEmpty()) {
                enEcart.add(new CodesCitesPointsCtrlDto.Point(p.getIdPointCtrl(), p.getLibelPointCtrl(), p.getIdTypeDossier(),
                        p.getIdSousType(), p.getForme(), p.getCategorie(), ecarts));
            }
        }
        return new CodesCitesPointsCtrlDto(cites, codes, enEcart);
    }

    /** Les codes cités par le libellé et la description, dans l'ordre, sans doublon. */
    static Set<String> codes(PointsCtrl p) {
        Set<String> codes = new LinkedHashSet<>();
        for (String texte : new String[] { p.getLibelPointCtrl(), p.getDecriptPointCtrl() }) {
            if (texte != null) {
                Matcher m = CODE.matcher(texte);
                while (m.find()) {
                    codes.add(m.group());
                }
            }
        }
        return codes;
    }

    /** Les écarts du point au référentiel donné (vide : conforme). */
    static List<CodesCitesPointsCtrlDto.Ecart> ecarts(PointsCtrl p, List<ChampFicheMarche> referentiel) {
        List<String> formes = p.getForme() == null ? List.of(PieceSousType.CONTRAT_CADRE, PieceSousType.AUTRE) : List.of(p.getForme());
        List<CodesCitesPointsCtrlDto.Ecart> ecarts = new ArrayList<>();
        for (String code : codes(p)) {
            List<ChampFicheMarche> designes = referentiel.stream().filter(c -> designe(code, c)).toList();
            List<ChampFicheMarche> actifs = designes.stream().filter(c -> Boolean.TRUE.equals(c.getActif())).toList();
            List<ChampFicheMarche> categorie = actifs.stream().filter(c -> c.pourCategorie(p.getCategorie())).toList();
            if (designes.isEmpty()) {
                ecarts.add(new CodesCitesPointsCtrlDto.Ecart(code, INCONNU, null));
            } else if (actifs.isEmpty()) {
                ecarts.add(new CodesCitesPointsCtrlDto.Ecart(code, INACTIF, null));
            } else if (categorie.isEmpty()) {
                ecarts.add(new CodesCitesPointsCtrlDto.Ecart(code, HORS_CATEGORIE, null));
            } else {
                for (String forme : formes) {
                    if (categorie.stream().noneMatch(c -> pourForme(c, forme))) {
                        ecarts.add(new CodesCitesPointsCtrlDto.Ecart(code, HORS_FORME, forme));
                    }
                }
            }
        }
        return ecarts;
    }

    /** Le code désigne le champ : son code même, ou sa rubrique, ou son bloc. */
    private static boolean designe(String code, ChampFicheMarche c) {
        return c.getCode() != null && (c.getCode().equals(code) || c.getCode().startsWith(code + "-"));
    }

    /** Le champ vaut pour la forme du point : le contrat-cadre, ou l'une des autres formes. */
    private static boolean pourForme(ChampFicheMarche c, String forme) {
        List<String> types = ChampFicheMarche.liste(c.getTypesMarche());
        return PieceSousType.CONTRAT_CADRE.equals(forme) ? types.contains(PieceSousType.CONTRAT_CADRE)
                : types.stream().anyMatch(t -> !PieceSousType.CONTRAT_CADRE.equals(t));
    }

    private static String lire(CodesCitesPointsCtrlDto.Ecart e) {
        return e.code() + " " + switch (e.motif()) {
            case INCONNU -> "inconnu du référentiel";
            case INACTIF -> "retiré du référentiel";
            case HORS_CATEGORIE -> "absent pour la catégorie du point";
            default -> PieceSousType.CONTRAT_CADRE.equals(e.forme()) ? "absent pour le contrat-cadre"
                    : "absent pour les formes autres que le contrat-cadre";
        };
    }
}
