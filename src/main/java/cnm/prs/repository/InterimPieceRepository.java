package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.InterimPiece;

/** Pièce PDF d'un intérim désigné ({@code t_interim_piece}, clé partagée avec {@code t_interim}). */
@Repository
public interface InterimPieceRepository extends JpaRepository<InterimPiece, Integer> {
}
