package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cnm.prs.entity.AttributionPiece;

public interface AttributionPieceRepository extends JpaRepository<AttributionPiece, Long> {

    /** Les pièces du cycle en cours du lot (⚠️ V92 : celles d'un cycle retiré sont archivées, hors de cette lecture). */
    @Query("select p from AttributionPiece p where p.idDmc = :idDmc and p.lot = :lot and p.archiveLe is null order by p.id asc")
    List<AttributionPiece> findByIdDmcAndLotOrderByIdAsc(@Param("idDmc") Long idDmc, @Param("lot") Integer lot);
}
