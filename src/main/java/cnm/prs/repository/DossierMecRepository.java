package cnm.prs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.DossierMec;

@Repository
public interface DossierMecRepository extends JpaRepository<DossierMec, Long> {

    /** Le DMC d'une ligne de marché (relation 1-1). */
    Optional<DossierMec> findByIdDetail(Integer idDetail);

    /** Une ligne de marché a-t-elle déjà un DMC ? */
    boolean existsByIdDetail(Integer idDetail);

    /** Supprime le DMC d'une ligne de marché (cascade applicative à la suppression du marché). */
    long deleteByIdDetail(Integer idDetail);

    /**
     * ⚠️ Fiche marché DAO (2026-09-22, §B2) — les DMC portés par une <strong>filiation</strong> de lignes (même
     * {@code ID_LIGNE_ORIGINE} à travers les versions du plan, la première ligne étant sa propre origine) : un DMC lié
     * à un ancêtre compte pour la ligne courante. Le plus ancien d'abord.
     */
    @org.springframework.data.jpa.repository.Query("""
            select d from DossierMec d, Marche m where d.idDetail = m.idDetail
              and (m.idLigneOrigine = :origine or m.idDetail = :origine) order by d.idDmc asc
            """)
    java.util.List<DossierMec> findParFiliation(@org.springframework.data.repository.query.Param("origine") Integer origine);

    /**
     * Tous les DMC avec l'origine de leur ligne ({@code [Integer origine, DossierMec dmc]}) — une seule requête pour
     * calculer {@code dejaDao} sur une liste d'éligibles (pas une requête par ligne).
     */
    @org.springframework.data.jpa.repository.Query("""
            select coalesce(m.idLigneOrigine, m.idDetail), d from DossierMec d, Marche m
             where d.idDetail = m.idDetail order by d.idDmc asc
            """)
    java.util.List<Object[]> findAvecOrigine();
}
