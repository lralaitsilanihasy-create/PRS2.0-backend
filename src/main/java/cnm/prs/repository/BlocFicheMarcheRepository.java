package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.BlocFicheMarche;

@Repository
public interface BlocFicheMarcheRepository extends JpaRepository<BlocFicheMarche, String> {

    List<BlocFicheMarche> findAllByOrderByRangAsc();
}
