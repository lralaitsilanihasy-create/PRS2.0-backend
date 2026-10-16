package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.RemiseConsulte;

/** ⚠️ V125 (lot MS-2) — les titulaires consultés par une remise en concurrence. */
public interface RemiseConsulteRepository extends JpaRepository<RemiseConsulte, RemiseConsulte.Cle> {

    List<RemiseConsulte> findByIdRemiseOrderByIdOffreAsc(Long idRemise);

    List<RemiseConsulte> findByIdOffreOrderByIdRemiseDesc(String idOffre);
}
