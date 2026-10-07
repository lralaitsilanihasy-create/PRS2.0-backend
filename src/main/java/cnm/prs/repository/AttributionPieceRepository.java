package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AttributionPiece;

public interface AttributionPieceRepository extends JpaRepository<AttributionPiece, Long> {

    List<AttributionPiece> findByIdDmcAndLotOrderByIdAsc(Long idDmc, Integer lot);
}
