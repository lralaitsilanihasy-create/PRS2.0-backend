package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.FicheMarche;

@Repository
public interface FicheMarcheRepository extends JpaRepository<FicheMarche, Integer> {

    /** La version courante d'un DMC (la plus récente). */
    Optional<FicheMarche> findFirstByIdDmcOrderByNumeroVersionDesc(Long idDmc);

    /** Toutes les versions d'un DMC, de la plus ancienne à la plus récente. */
    List<FicheMarche> findByIdDmcOrderByNumeroVersionAsc(Long idDmc);

    Optional<FicheMarche> findByIdDmcAndNumeroVersion(Long idDmc, Integer numeroVersion);
}
