package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.SousTypeDossier;

@Repository
public interface SousTypeDossierRepository extends JpaRepository<SousTypeDossier, String> {

    /** Sous-types d'une famille (ex. DDP → PPM, PPM-AGPM) — lecture « par famille » du front. */
    List<SousTypeDossier> findByIdTypeDossierOrderByIdSousType(String idTypeDossier);
}
