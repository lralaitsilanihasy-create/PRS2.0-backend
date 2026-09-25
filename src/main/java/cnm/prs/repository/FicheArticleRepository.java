package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cnm.prs.entity.FicheArticle;

/** ⚠️ V45 (2026-09-25) — les articles du besoin d'une version de fiche. */
public interface FicheArticleRepository extends JpaRepository<FicheArticle, Integer> {

    @Query("select a from FicheArticle a where a.idFiche = :idFiche order by coalesce(a.lot, 0), a.ordre, a.idArticle")
    List<FicheArticle> findParFiche(@Param("idFiche") Integer idFiche);
}
