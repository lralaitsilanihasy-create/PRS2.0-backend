package cnm.prs.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * ⚠️ Lot 6 (2026-09-17, §B1) — <strong>dépôt de la plus ancienne inscription encore en attente</strong>
     * pour un type d'acteur, ou {@code null} si la file est vide.
     *
     * <p>{@code t_compte_auth} ne porte <strong>aucune date de création</strong> (cf. {@code V1__baseline.sql}) :
     * seule {@code DATE_DECISION} y figure, renseignée au moment où l'Administrateur tranche — donc jamais
     * pour une inscription en attente. La demande §B1 exclut toute migration ; l'ancienneté se lit donc sur
     * la première pièce déposée, écrite dans la même transaction que l'inscription
     * ({@code AuthService.inscrire*} → {@code PieceJointeService.stocker}, {@code DATE_DEPOT = now}).</p>
     *
     * <p>Les pièces d'une inscription sont classées sous son {@code LOGIN} et ne sont reclassées sous
     * l'identifiant de l'acteur qu'à la validation ({@code PieceJointeService.reAffecter}) : tant que
     * l'inscription attend, la jointure par login est exacte.</p>
     */
    @Query("""
            select min(p.dateDepot) from PieceJointe p
            where p.login in (
                select c.login from CompteAuth c
                where c.statut = :statut and c.typeActeur = :typeActeur)
            """)
    LocalDateTime premierDepotDesComptes(@Param("statut") String statut, @Param("typeActeur") String typeActeur);

    /** Prochaine PK allouee par la sequence serveur {@code seq_piece_jointe} (allocation atomique). */
    @Query(value = "select nextval('seq_piece_jointe')", nativeQuery = true)
    Long nextIdPieceJointe();
}
