package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.Ami;

public interface AmiRepository extends JpaRepository<Ami, Long> {

    List<Ami> findByEtatOrderByDateLimiteAsc(String etat);
}
