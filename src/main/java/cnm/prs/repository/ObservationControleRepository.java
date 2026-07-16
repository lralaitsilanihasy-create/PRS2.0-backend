package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ObservationControle;

@Repository
public interface ObservationControleRepository extends JpaRepository<ObservationControle, Integer> {

    /** Lignes d'observation d'un point de contrôle, triées par ordre de saisie ASC. */
    List<ObservationControle> findByIdDetailOrderByOrdreAsc(Integer idDetail);

    /** Supprime les lignes d'observation d'un point de contrôle (replace-on-save / cascade). */
    void deleteByIdDetail(Integer idDetail);

    /**
     * Purge (⚠️ règle ajoutée §3.3) — supprime les observations des lignes de grille d'un dossier retiré
     * (observation → détail d'examen → examen → dispatch → réception → dossier). Feuille : à appeler en
     * <strong>tout premier</strong>, avant les détails d'examen.
     */
    @Modifying
    @Query("delete from ObservationControle o where o.idDetail in "
            + "(select ed.idDetailExamen from ExamenDetail ed where ed.idExamen in "
            + "(select e.idExamen from Examen e where e.idDispatch in "
            + "(select di.idDispatch from Dispatch di where di.idReception in "
            + "(select r.idReception from Reception r where r.idDossier = :idDossier))))")
    int deleteParDossier(@Param("idDossier") Integer idDossier);
}
