package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cnm.prs.entity.FicheCaracteristique;

/** ⚠️ V45 (2026-09-25) — les caractéristiques exigées des articles du besoin. */
public interface FicheCaracteristiqueRepository extends JpaRepository<FicheCaracteristique, Integer> {

    List<FicheCaracteristique> findByIdArticleInOrderByIdArticleAscOrdreAsc(Collection<Integer> idsArticle);

    @Modifying
    @Query("delete from FicheCaracteristique c where c.idArticle in :ids")
    void supprimerParArticles(@Param("ids") Collection<Integer> ids);
}
