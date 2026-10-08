package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.Negociation;

public interface NegociationRepository extends JpaRepository<Negociation, Long> {

    List<Negociation> findByIdDmcOrderByIdAsc(Long idDmc);

    List<Negociation> findByIdDmcAndLotOrderByIdAsc(Long idDmc, Integer lot);
}
