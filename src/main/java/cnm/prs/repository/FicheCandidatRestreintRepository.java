package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.FicheCandidatRestreint;

public interface FicheCandidatRestreintRepository extends JpaRepository<FicheCandidatRestreint, Integer> {

    List<FicheCandidatRestreint> findByIdFicheOrderByRangAsc(Integer idFiche);

    void deleteByIdFiche(Integer idFiche);
}
