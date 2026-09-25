package cnm.prs.service;

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
 */
@Component
@Transactional
public class BesoinFiche {

    /** Un article et ses caractéristiques, tels que le bilan et les documents les lisent. */
    public record Article(Integer lot, int ordre, String designation, String unite, Integer quantiteMin,
            Integer quantiteMax, Integer quantite, List<ArticleBesoinDto.Caracteristique> caracteristiques) {
    }

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
                a.getDesignation(), a.getUnite(), a.getQuantiteMin(), a.getQuantiteMax(), a.getQuantite(),
                a.getRedigePar(), a.getProfilRedacteur(), caracs.getOrDefault(a.getIdArticle(), List.of()))).toList();
    }

    /** La vue réduite du besoin (bilan, documents). */
    @Transactional(readOnly = true)
    public List<Article> articles(Integer idFiche) {
        return lister(idFiche).stream().map(a -> new Article(a.getLot(), a.getOrdre(), a.getDesignation(), a.getUnite(),
                a.getQuantiteMin(), a.getQuantiteMax(), a.getQuantite(), a.getCaracteristiques())).toList();
    }

    /**
     * Valide les articles reçus pour le type de marché ({@code lotImpose} : le rang que chaque article prend) ; 400
     * nominatif sur {@code articles[i].…}. Les rangs de lot eux-mêmes sont jugés par l'appelant.
     */
    public static void valider(List<ArticleBesoinDto> articles, String typeMarche) {
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        boolean aCommande = TypeMarcheDao.A_COMMANDE.name().equals(typeMarche);
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

    /**
     * Remplace les articles de la version : ceux du lot {@code lot} si {@code toutLeBesoin} est faux, tous sinon. Les
     * articles reçus sont écrits dans l'ordre de la liste (ordre 1, 2, 3… par lot), leur {@code lot} déjà fixé.
     */
    public void remplacer(Integer idFiche, boolean toutLeBesoin, Integer lot, List<ArticleBesoinDto> articles,
            String typeMarche, String redacteur, String profil) {
        List<FicheArticle> anciens = articleRepository.findParFiche(idFiche).stream()
                .filter(a -> toutLeBesoin || Objects.equals(a.getLot(), lot)).toList();
        if (!anciens.isEmpty()) {
            caracteristiqueRepository.supprimerParArticles(anciens.stream().map(FicheArticle::getIdArticle).toList());
            articleRepository.deleteAll(anciens);
            articleRepository.flush();
        }
        boolean aCommande = TypeMarcheDao.A_COMMANDE.name().equals(typeMarche);
        Map<Integer, Integer> ordreParLot = new LinkedHashMap<>();
        for (ArticleBesoinDto a : articles) {
            int ordre = ordreParLot.merge(a.getLot() == null ? 0 : a.getLot(), 1, Integer::sum);
            FicheArticle e = articleRepository.save(new FicheArticle(null, idFiche, a.getLot(), ordre,
                    a.getDesignation().trim(), a.getUnite().trim(), aCommande ? a.getQuantiteMin() : null,
                    aCommande ? a.getQuantiteMax() : null, aCommande ? null : a.getQuantite(), redacteur, profil));
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
                    a.getRedigePar(), a.getProfilRedacteur()));
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

    private static void quantite(List<ErrorResponse.FieldError> erreurs, String champ, Integer v, String nom) {
        if (v == null) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " est obligatoire."));
        } else if (v < 0) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " n'est pas négative."));
        }
    }
}
