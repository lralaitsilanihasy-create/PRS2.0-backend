package cnm.prs.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.AlerteExamen;

/** ⚠️ M5b (manuel de contrôle, §B6 ; V99) — alertes de dépassement d'examen. */
@Repository
public interface AlerteExamenRepository extends JpaRepository<AlerteExamen, Integer> {

    boolean existsByIdDossierAndEntree(Integer idDossier, LocalDateTime entree);

    List<AlerteExamen> findByIdDossierOrderByIdAlerteAsc(Integer idDossier);
}
