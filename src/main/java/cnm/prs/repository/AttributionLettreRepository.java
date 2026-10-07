package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AttributionLettre;

public interface AttributionLettreRepository extends JpaRepository<AttributionLettre, Long> {

    List<AttributionLettre> findByIdDmcAndLotOrderByIdAsc(Long idDmc, Integer lot);

    List<AttributionLettre> findByIdOffreOrderByIdDesc(String idOffre);
}
