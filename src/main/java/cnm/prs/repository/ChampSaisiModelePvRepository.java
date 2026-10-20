package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ChampSaisiModelePv;

/** ⚠️ 2026-10-20 (« generateur-modeles-pv », lot G1 ; V138). */
public interface ChampSaisiModelePvRepository extends JpaRepository<ChampSaisiModelePv, Long> {

    List<ChampSaisiModelePv> findByIdVersionOrderByOrdreAsc(Long idVersion);

    void deleteByIdVersion(Long idVersion);
}
