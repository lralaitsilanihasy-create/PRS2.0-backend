package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.RubriqueFicheMarche;

@Repository
public interface RubriqueFicheMarcheRepository extends JpaRepository<RubriqueFicheMarche, String> {

    List<RubriqueFicheMarche> findAllByOrderByCodeBlocAscRangAsc();
}
