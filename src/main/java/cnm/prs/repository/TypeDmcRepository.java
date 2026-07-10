package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.TypeDmc;

@Repository
public interface TypeDmcRepository extends JpaRepository<TypeDmc, Long> {

    /** Un type de DMC porte-t-il déjà ce code (unicité) ? */
    boolean existsByCode(String code);
}
