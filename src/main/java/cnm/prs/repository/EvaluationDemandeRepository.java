package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.EvaluationDemande;

public interface EvaluationDemandeRepository extends JpaRepository<EvaluationDemande, Long> {

    List<EvaluationDemande> findByIdOffreAndTypeOrderByDemandeeLeAscIdAsc(String idOffre, String type);

    List<EvaluationDemande> findByIdDmcOrderByDemandeeLeAscIdAsc(Long idDmc);

    /** ⚠️ Tranche 1d (§B7) — les demandes (précisions, justifications) sans réponse dont le délai court, sur les fiches d'une PRMP. */
    @org.springframework.data.jpa.repository.Query("""
            select count(x) from EvaluationDemande x, DossierMec d, Marche m, Dossier ds
             where x.reponduLe is null and x.echeance > :maintenant and x.idDmc = d.idDmc and d.idDetail = m.idDetail
               and m.idDossier = ds.idDossier and ds.idPrmp = :idPrmp
            """)
    long compterEnAttentePourPrmp(@org.springframework.data.repository.query.Param("idPrmp") String idPrmp,
            @org.springframework.data.repository.query.Param("maintenant") java.time.LocalDateTime maintenant);
}
