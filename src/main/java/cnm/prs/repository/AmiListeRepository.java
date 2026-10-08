package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AmiListe;

public interface AmiListeRepository extends JpaRepository<AmiListe, AmiListe.Cle> {
    List<AmiListe> findByIdDmcOrderByRangAsc(Long idDmc);

    void deleteByIdDmc(Long idDmc);
}
