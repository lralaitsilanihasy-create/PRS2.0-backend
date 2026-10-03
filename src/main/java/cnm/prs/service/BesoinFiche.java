package cnm.prs.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ArticleBesoinDto;
import cnm.prs.entity.FicheArticle;
import cnm.prs.entity.FicheCaracteristique;
import cnm.prs.enums.TypeMarcheDao;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.repository.FicheArticleRepository;
import cnm.prs.repository.FicheCaracteristiqueRepository;

/**
 * ⚠️ <strong>Le besoin d'une version de fiche DAO</strong> (V45, demande front du 2026-09-25, §B1) — lecture,
 * remplacement en bloc, copie à la révision, et la vue réduite qu'en tirent le bilan et la génération. Les gardes
 * (profil, fiche validée, catégorie, rang de lot) sont celles de {@link FicheMarcheService}, qui appelle ce composant.
 *
 * <p>⚠️ V59 (2026-10-02, DQE des travaux, §B1) — le besoin d'une fiche de travaux est son <strong>détail quantitatif et
 * estimatif</strong> : chaque article porte en plus un numéro de prix, une série (code et intitulé), un libellé du
 * bordereau, la mention « soumis à sous-détail » et un plafond. Quantités à deux décimales, toutes catégories.</p>
 */
@Component
@Transactional
public class BesoinFiche {

    /**
     * Un article et ses caractéristiques, tels que le bilan et les documents les lisent. ⚠️ V59 (2026-10-02) — quantités
     * décimales et, pour les travaux, numéro de prix, série, libellé du bordereau, sous-détail et plafond.
     */
    public record Article(Integer lot, int ordre, String designation, String unite, BigDecimal quantiteMin,
            BigDecimal quantiteMax, BigDecimal quantite, List<ArticleBesoinDto.Caracteristique> caracteristiques,
            String numeroPrix, String serie, String serieLibelle, String libelleBordereau, boolean sousDetail,
            BigDecimal plafond) {

        /** Fournitures : un article sans les propriétés des travaux. */
        public Article(Integer lot, int ordre, String designation, String unite, BigDecimal quantiteMin,
                BigDecimal quantiteMax, BigDecimal quantite, List<ArticleBesoinDto.Caracteristique> caracteristiques) {
            this(lot, ordre, designation, unite, quantiteMin, quantiteMax, quantite, caracteristiques, null, null, null,
                    null, false, null);
        }
    }

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final FicheArticleRepository articleRepository;
    private final FicheCaracteristiqueRepository caracteristiqueRepository;

    public BesoinFiche(FicheArticleRepository articleRepository, FicheCaracteristiqueRepository caracteristiqueRepository) {
        this.articleRepository = articleRepository;
        this.caracteristiqueRepository = caracteristiqueRepository;
    }

    /** Les articles d'une version, triés par lot puis ordre, avec leurs caractéristiques. */
    @Transactional(readOnly = true)
    public List<ArticleBesoinDto> lister(Integer idFiche) {
        if (idFiche == null) {
            return List.of();
        }
        List<FicheArticle> articles = articleRepository.findParFiche(idFiche);
        Map<Integer, List<ArticleBesoinDto.Caracteristique>> caracs = new LinkedHashMap<>();
        if (!articles.isEmpty()) {
            caracteristiqueRepository.findByIdArticleInOrderByIdArticleAscOrdreAsc(
                    articles.stream().map(FicheArticle::getIdArticle).toList())
                    .forEach(c -> caracs.computeIfAbsent(c.getIdArticle(), k -> new ArrayList<>()).add(
                            new ArticleBesoinDto.Caracteristique(c.getIdCaracteristique(), c.getOrdre(), c.getLibelle(),
                                    c.getExigence())));
        }
        return articles.stream().map(a -> new ArticleBesoinDto(a.getIdArticle(), a.getLot(), a.getOrdre(),
                a.getDesignation(), a.getUnite(), plat(a.getQuantiteMin()), plat(a.getQuantiteMax()), plat(a.getQuantite()),
                a.getRedigePar(), a.getProfilRedacteur(), caracs.getOrDefault(a.getIdArticle(), List.of()),
                a.getNumeroPrix(), a.getSerie(), a.getSerieLibelle(), a.getLibelleBordereau(), a.isSousDetail(),
                plat(a.getPlafond()))).toList();
    }

    /** La vue réduite du besoin (bilan, documents). */
    @Transactional(readOnly = true)
    public List<Article> articles(Integer idFiche) {
        return lister(idFiche).stream().map(a -> new Article(a.getLot(), a.getOrdre(), a.getDesignation(), a.getUnite(),
                a.getQuantiteMin(), a.getQuantiteMax(), a.getQuantite(), a.getCaracteristiques(), a.getNumeroPrix(),
                a.getSerie(), a.getSerieLibelle(), a.getLibelleBordereau(), Boolean.TRUE.equals(a.getSousDetail()),
                a.getPlafond())).toList();
    }

    /** Une quantité telle que servie : sans zéros inutiles (« 5 », « 2054.5 »), jamais en notation scientifique. */
    static BigDecimal plat(BigDecimal v) {
        if (v == null) {
            return null;
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    /** Fournitures et services : voir {@link #valider(List, String, boolean)}. */
    public static void valider(List<ArticleBesoinDto> articles, String typeMarche) {
        valider(articles, typeMarche, false);
    }

    /**
     * Valide les articles reçus pour le type de marché ; 400 nominatif sur {@code articles[i].…}. Les rangs de lot
     * eux-mêmes sont jugés par l'appelant. ⚠️ V59 (2026-10-02, §B1.2) — pour une fiche de travaux ({@code travaux}) :
     * numéro de prix et série obligatoires, numéro de prix unique dans le lot, un seul intitulé par série du lot, plafond
     * de 0 à 100. Toutes catégories : deux décimales au plus pour une quantité.
     */
    public static void valider(List<ArticleBesoinDto> articles, String typeMarche, boolean travaux) {
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        boolean aCommande = TypeMarcheDao.A_COMMANDE.name().equals(typeMarche);
        Map<String, Integer> numeros = new LinkedHashMap<>();
        Map<String, String> libellesSerie = new LinkedHashMap<>();
        for (int i = 0; i < articles.size(); i++) {
            ArticleBesoinDto a = articles.get(i);
            String p = "articles[" + i + "].";
            if (a == null) {
                erreurs.add(new ErrorResponse.FieldError("articles[" + i + "]", "Article vide."));
                continue;
            }
            texte(erreurs, p + "designation", a.getDesignation(), 500, "La désignation");
            texte(erreurs, p + "unite", a.getUnite(), 20, "L'unité");
            if (aCommande) {
                quantite(erreurs, p + "quantiteMin", a.getQuantiteMin(), "La quantité minimum (marché à commande)");
                quantite(erreurs, p + "quantiteMax", a.getQuantiteMax(), "La quantité maximum (marché à commande)");
            } else {
                quantite(erreurs, p + "quantite", a.getQuantite(), "La quantité");
            }
            if (travaux) {
                travaux(erreurs, p, i, a, numeros, libellesSerie);
            }
            List<ArticleBesoinDto.Caracteristique> caracs = a.getCaracteristiques() == null ? List.of() : a.getCaracteristiques();
            for (int j = 0; j < caracs.size(); j++) {
                ArticleBesoinDto.Caracteristique c = caracs.get(j);
                String q = p + "caracteristiques[" + j + "].";
                texte(erreurs, q + "libelle", c == null ? null : c.getLibelle(), 300, "Le libellé de la caractéristique");
                texte(erreurs, q + "exigence", c == null ? null : c.getExigence(), 500, "L'exigence");
            }
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
    }

    /** ⚠️ V59 — les propriétés d'un article de travaux. */
    private static void travaux(List<ErrorResponse.FieldError> erreurs, String p, int i, ArticleBesoinDto a,
            Map<String, Integer> numeros, Map<String, String> libellesSerie) {
        String lot = String.valueOf(a.getLot());
        String dansLeLot = a.getLot() == null ? "" : " du lot " + a.getLot();
        texte(erreurs, p + "numeroPrix", a.getNumeroPrix(), 10, "Le numéro de prix");
        texte(erreurs, p + "serie", a.getSerie(), 10, "La série");
        facultatif(erreurs, p + "serieLibelle", a.getSerieLibelle(), 200, "L'intitulé de la série");
        facultatif(erreurs, p + "libelleBordereau", a.getLibelleBordereau(), 200, "Le libellé du bordereau");
        if (a.getNumeroPrix() != null && !a.getNumeroPrix().isBlank()) {
            Integer deja = numeros.putIfAbsent(lot + "|" + a.getNumeroPrix().trim(), i);
            if (deja != null) {
                erreurs.add(new ErrorResponse.FieldError(p + "numeroPrix", "Le numéro de prix « " + a.getNumeroPrix().trim()
                        + " » est déjà celui de l'article " + (deja + 1) + dansLeLot + " : il est unique dans le lot."));
            }
        }
        if (a.getSerie() != null && !a.getSerie().isBlank() && a.getSerieLibelle() != null && !a.getSerieLibelle().isBlank()) {
            String deja = libellesSerie.putIfAbsent(lot + "|" + a.getSerie().trim(), a.getSerieLibelle().trim());
            if (deja != null && !deja.equals(a.getSerieLibelle().trim())) {
                erreurs.add(new ErrorResponse.FieldError(p + "serieLibelle", "La série « " + a.getSerie().trim() + " »"
                        + dansLeLot + " s'intitule déjà « " + deja + " » : une série n'a qu'un intitulé."));
            }
        }
        if (a.getPlafond() != null && (a.getPlafond().signum() < 0 || a.getPlafond().compareTo(CENT) > 0)) {
            erreurs.add(new ErrorResponse.FieldError(p + "plafond", "Le plafond est un pourcentage, de 0 à 100."));
        }
    }

    /** Fournitures et services : voir {@link #remplacer(Integer, boolean, Integer, List, String, String, String, boolean)}. */
    public void remplacer(Integer idFiche, boolean toutLeBesoin, Integer lot, List<ArticleBesoinDto> articles,
            String typeMarche, String redacteur, String profil) {
        remplacer(idFiche, toutLeBesoin, lot, articles, typeMarche, redacteur, profil, false);
    }

    /**
     * Remplace les articles de la version : ceux du lot {@code lot} si {@code toutLeBesoin} est faux, tous sinon. Les
     * articles reçus sont écrits dans l'ordre de la liste (ordre 1, 2, 3… par lot), leur {@code lot} déjà fixé.
     * ⚠️ V59 — pour une fiche de travaux, les propriétés des travaux sont écrites, et l'intitulé d'une série, porté par
     * un seul de ses articles, est recopié sur toute la série du lot ; hors travaux, elles sont ignorées.
     */
    public void remplacer(Integer idFiche, boolean toutLeBesoin, Integer lot, List<ArticleBesoinDto> articles,
            String typeMarche, String redacteur, String profil, boolean travaux) {
        List<FicheArticle> anciens = articleRepository.findParFiche(idFiche).stream()
                .filter(a -> toutLeBesoin || Objects.equals(a.getLot(), lot)).toList();
        if (!anciens.isEmpty()) {
            caracteristiqueRepository.supprimerParArticles(anciens.stream().map(FicheArticle::getIdArticle).toList());
            articleRepository.deleteAll(anciens);
            articleRepository.flush();
        }
        Map<String, String> libellesSerie = new LinkedHashMap<>();
        if (travaux) {
            for (ArticleBesoinDto a : articles) {
                if (a.getSerieLibelle() != null && !a.getSerieLibelle().isBlank()) {
                    libellesSerie.putIfAbsent(a.getLot() + "|" + a.getSerie().trim(), a.getSerieLibelle().trim());
                }
            }
        }
        boolean aCommande = TypeMarcheDao.A_COMMANDE.name().equals(typeMarche);
        Map<Integer, Integer> ordreParLot = new LinkedHashMap<>();
        for (ArticleBesoinDto a : articles) {
            int ordre = ordreParLot.merge(a.getLot() == null ? 0 : a.getLot(), 1, Integer::sum);
            FicheArticle e = new FicheArticle();
            e.setIdFiche(idFiche);
            e.setLot(a.getLot());
            e.setOrdre(ordre);
            e.setDesignation(a.getDesignation().trim());
            e.setUnite(a.getUnite().trim());
            e.setQuantiteMin(aCommande ? a.getQuantiteMin() : null);
            e.setQuantiteMax(aCommande ? a.getQuantiteMax() : null);
            e.setQuantite(aCommande ? null : a.getQuantite());
            e.setRedigePar(redacteur);
            e.setProfilRedacteur(profil);
            if (travaux) {
                e.setNumeroPrix(a.getNumeroPrix().trim());
                e.setSerie(a.getSerie().trim());
                e.setSerieLibelle(libellesSerie.get(a.getLot() + "|" + a.getSerie().trim()));
                e.setLibelleBordereau(vide(a.getLibelleBordereau()));
                e.setSousDetail(Boolean.TRUE.equals(a.getSousDetail()));
                e.setPlafond(a.getPlafond());
            }
            e = articleRepository.save(e);
            ecrireCaracteristiques(e.getIdArticle(), a.getCaracteristiques());
        }
    }

    /** Retire un article de la version ; faux s'il n'en fait pas partie. */
    public boolean supprimer(Integer idFiche, Integer idArticle) {
        FicheArticle a = articleRepository.findById(idArticle).orElse(null);
        if (a == null || !a.getIdFiche().equals(idFiche)) {
            return false;
        }
        caracteristiqueRepository.supprimerParArticles(List.of(idArticle));
        articleRepository.delete(a);
        return true;
    }

    /** La révision copie le besoin de la version précédente, comme ses valeurs. */
    public void copier(Integer idFicheSource, Integer idFicheCible) {
        for (ArticleBesoinDto a : lister(idFicheSource)) {
            FicheArticle e = articleRepository.save(new FicheArticle(null, idFicheCible, a.getLot(), a.getOrdre(),
                    a.getDesignation(), a.getUnite(), a.getQuantiteMin(), a.getQuantiteMax(), a.getQuantite(),
                    a.getRedigePar(), a.getProfilRedacteur(), a.getNumeroPrix(), a.getSerie(), a.getSerieLibelle(),
                    a.getLibelleBordereau(), Boolean.TRUE.equals(a.getSousDetail()), a.getPlafond()));
            ecrireCaracteristiques(e.getIdArticle(), a.getCaracteristiques());
        }
    }

    private void ecrireCaracteristiques(Integer idArticle, List<ArticleBesoinDto.Caracteristique> caracs) {
        int ordre = 0;
        for (ArticleBesoinDto.Caracteristique c : caracs == null ? List.<ArticleBesoinDto.Caracteristique>of() : caracs) {
            caracteristiqueRepository.save(new FicheCaracteristique(null, idArticle, ++ordre, c.getLibelle().trim(),
                    c.getExigence().trim()));
        }
    }

    private static void texte(List<ErrorResponse.FieldError> erreurs, String champ, String v, int max, String nom) {
        if (v == null || v.isBlank()) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " est obligatoire."));
        } else if (v.trim().length() > max) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " : " + max + " caractères au plus."));
        }
    }

    private static void facultatif(List<ErrorResponse.FieldError> erreurs, String champ, String v, int max, String nom) {
        if (v != null && v.trim().length() > max) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " : " + max + " caractères au plus."));
        }
    }

    private static String vide(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static void quantite(List<ErrorResponse.FieldError> erreurs, String champ, BigDecimal v, String nom) {
        if (v == null) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " est obligatoire."));
        } else if (v.signum() < 0) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " n'est pas négative."));
        } else if (plat(v).scale() > 2) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " : deux décimales au plus."));   // ⚠️ V59
        } else if (v.setScale(0, RoundingMode.DOWN).precision() > 13) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " : treize chiffres au plus avant la virgule."));
        }
    }
}
