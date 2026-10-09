package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ActeGestion;

/** ⚠️ M5a (manuel de contrôle, §B1 et §B5 ; V98) — les actes de gestion contractuelle et leur marché. */
@Repository
public interface ActeGestionRepository extends JpaRepository<ActeGestion, Integer> {

    Optional<ActeGestion> findByIdDossier(Integer idDossier);

    List<ActeGestion> findByIdDossierMarcheOrderByIdActeAsc(Integer idDossierMarche);
}
