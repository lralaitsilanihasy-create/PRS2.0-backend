package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.RecuDao;

/** ⚠️ V72 — les reçus de frais de dossier. */
@Repository
public interface RecuDaoRepository extends JpaRepository<RecuDao, Integer> {

    List<RecuDao> findByIdDmcOrderByDateDepotDescIdRecuDesc(Long idDmc);

    List<RecuDao> findByIdDmcAndNifOrderByDateDepotDescIdRecuDesc(Long idDmc, String nif);

    /** ⚠️ 2026-10-06 (compteurs, §B2) — les reçus EN_ATTENTE par DMC, en une requête : {@code [idDmc, nombre]}. */
    @org.springframework.data.jpa.repository.Query("select r.idDmc, count(r) from RecuDao r where r.etat = 'EN_ATTENTE' group by r.idDmc")
    List<Object[]> compterEnAttenteParDmc();

    /** ⚠️ 2026-10-06 (compteurs, §B1) — les reçus EN_ATTENTE des fiches d'une PRMP (le plan de la ligne du DMC). */
    @org.springframework.data.jpa.repository.Query("""
            select count(r) from RecuDao r, DossierMec d, Marche m, Dossier ds
             where r.etat = 'EN_ATTENTE' and r.idDmc = d.idDmc and d.idDetail = m.idDetail and m.idDossier = ds.idDossier
               and ds.idPrmp = :idPrmp
            """)
    long compterEnAttentePourPrmp(@org.springframework.data.repository.query.Param("idPrmp") String idPrmp);
}
