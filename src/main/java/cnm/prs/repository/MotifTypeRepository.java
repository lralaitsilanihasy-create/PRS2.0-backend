package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.MotifType;

/** ⚠️ M4 (manuel de contrôle, §B4 ; V97) — les motifs-types de la conclusion. */
@Repository
public interface MotifTypeRepository extends JpaRepository<MotifType, Integer> {

    List<MotifType> findByIdTypeDossier(String idTypeDossier);
}
