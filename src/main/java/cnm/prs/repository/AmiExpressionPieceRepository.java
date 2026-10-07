package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AmiExpressionPiece;

public interface AmiExpressionPieceRepository extends JpaRepository<AmiExpressionPiece, Long> {

    List<AmiExpressionPiece> findByIdExpressionOrderByIdAsc(String idExpression);
}
