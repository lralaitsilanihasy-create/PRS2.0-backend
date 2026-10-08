package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.PieceSousType;

public interface PieceSousTypeRepository extends JpaRepository<PieceSousType, Long> {

    List<PieceSousType> findByIdSousTypeOrderByOrdreAscIdAsc(String idSousType);
}
