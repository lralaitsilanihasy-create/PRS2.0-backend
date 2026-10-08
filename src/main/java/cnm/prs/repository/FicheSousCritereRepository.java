package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.FicheSousCritere;

public interface FicheSousCritereRepository extends JpaRepository<FicheSousCritere, Integer> {

    List<FicheSousCritere> findByIdFicheOrderByOrdreAsc(Integer idFiche);

    void deleteByIdFiche(Integer idFiche);
}
