package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ExamenPiece;

@Repository
public interface ExamenPieceRepository extends JpaRepository<ExamenPiece, Integer> {

    /** Résultats d'examen des pièces d'un examen. */
    List<ExamenPiece> findByIdExamen(Integer idExamen);

    /**
     * ⚠️ Audit 2026-08-27 (C2) — §1/§3.1 : résultats de pièces des examens d'une <strong>localité</strong>
     * (même chaîne examen → dispatch → réception → contrôleur récepteur que le parent {@code Examen}).
     */
    @Query("select ep from ExamenPiece ep where ep.idExamen in "
            + "(select e.idExamen from Examen e where e.dispatch.reception.ctrlRecept.idLocalite = :loc)")
    List<ExamenPiece> findVisiblesParLocalite(@Param("loc") String loc);

    /** ⚠️ C2 — résultats d'UN examen, restreints à la localité de l'appelant ({@code ?examen=}). */
    @Query("select ep from ExamenPiece ep where ep.idExamen = :idExamen and ep.idExamen in "
            + "(select e.idExamen from Examen e where e.dispatch.reception.ctrlRecept.idLocalite = :loc)")
    List<ExamenPiece> findByIdExamenEtLocalite(@Param("idExamen") Integer idExamen, @Param("loc") String loc);

    /** ⚠️ C2 — le résultat {@code id} appartient-il à un examen de la localité ? (garde de l'accès unitaire) */
    @Query("select (count(ep) > 0) from ExamenPiece ep where ep.idExamenPiece = :id and ep.idExamen in "
            + "(select e.idExamen from Examen e where e.dispatch.reception.ctrlRecept.idLocalite = :loc)")
    boolean existsDansLocalite(@Param("id") Integer id, @Param("loc") String loc);

    /** Unicité applicative du couple (idExamen, idPiece) ; {@code selfId} exclut la ligne à la mise à jour. */
    @Query("""
            select count(ep) from ExamenPiece ep
            where ep.idExamen = :idExamen and ep.idPiece = :idPiece
              and (:selfId is null or ep.idExamenPiece <> :selfId)
            """)
    long compterDoublon(@Param("idExamen") Integer idExamen, @Param("idPiece") Integer idPiece,
            @Param("selfId") Integer selfId);

    /**
     * Purge (⚠️ règle ajoutée §3.3) — supprime les examens de pièces du circuit d'un dossier retiré
     * (via examen → dispatch → réception → dossier). À appeler <strong>avant</strong> les examens.
     */
    @Modifying
    @Query("delete from ExamenPiece ep where ep.idExamen in "
            + "(select e.idExamen from Examen e where e.idDispatch in "
            + "(select di.idDispatch from Dispatch di where di.idReception in "
            + "(select r.idReception from Reception r where r.idDossier = :idDossier)))")
    int deleteParDossier(@Param("idDossier") Integer idDossier);


    /** Prochaine PK allouee par la sequence serveur {@code seq_examen_piece} (allocation atomique). */
    @Query(value = "select nextval('seq_examen_piece')", nativeQuery = true)
    Long nextIdExamenPiece();
}
