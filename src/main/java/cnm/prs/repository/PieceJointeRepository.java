package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.PieceJointe;

@Repository
public interface PieceJointeRepository extends JpaRepository<PieceJointe, Integer> {

    /** La pièce active d'un type pour un compte (une seule par couple LOGIN/TYPE_PIECE). */
    Optional<PieceJointe> findByLoginAndTypePiece(String login, String typePiece);

    /** Toutes les pièces d'un compte. */
    List<PieceJointe> findByLogin(String login);

    /** Purge toutes les pièces d'une clé acteur (suppression de l'acteur). */
    void deleteByLogin(String login);

    /** Plus grand ID_PIECE existant (0 si table vide) — pour générer la PK assignée. */
    @Query("select coalesce(max(p.idPiece), 0) from PieceJointe p")
    Integer findMaxId();
}
