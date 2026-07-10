package cnm.prs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.TypeDmc;

@Repository
public interface TypeDmcRepository extends JpaRepository<TypeDmc, Long> {

    /** Un type de DMC porte-t-il déjà ce code (unicité) ? */
    boolean existsByCode(String code);

    /** Type de DMC par code (pour la dérivation automatique depuis le libellé du mode). */
    Optional<TypeDmc> findByCode(String code);
}
