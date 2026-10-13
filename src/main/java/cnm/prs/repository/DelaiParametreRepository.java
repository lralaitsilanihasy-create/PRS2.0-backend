package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.DelaiParametre;

/** ⚠️ R6a (manuel révisé, lot 2, §B1 ; V106) — les délais de publicité administrables. */
public interface DelaiParametreRepository extends JpaRepository<DelaiParametre, String> {

    List<DelaiParametre> findAllByOrderByCodeAsc();
}
