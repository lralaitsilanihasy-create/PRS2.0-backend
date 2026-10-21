package cnm.prs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ImageModelePv;

/** ⚠️ 2026-10-21 (« modele-pv-page », §B3 ; V139). */
public interface ImageModelePvRepository extends JpaRepository<ImageModelePv, Long> {

    Optional<ImageModelePv> findByCode(String code);
}
