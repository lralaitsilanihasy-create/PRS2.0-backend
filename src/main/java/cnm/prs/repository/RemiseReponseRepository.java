package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.RemiseReponse;

/** ⚠️ V125 (lot MS-2) — les réponses des titulaires à une remise en concurrence. */
public interface RemiseReponseRepository extends JpaRepository<RemiseReponse, Long> {

    List<RemiseReponse> findByIdRemiseOrderByIdAsc(Long idRemise);

    Optional<RemiseReponse> findByIdRemiseAndIdOffre(Long idRemise, String idOffre);
}
