package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Ugpm;

@Repository
public interface UgpmRepository extends JpaRepository<Ugpm, String> {

    /** UGPM rattachées à une PRMP de tutelle (une PRMP chapeaute plusieurs UGPM). */
    List<Ugpm> findByIdPrmpTutelle(String idPrmpTutelle);
}
