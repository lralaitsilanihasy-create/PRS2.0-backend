package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.ModePassation;

@Repository
public interface ModePassationRepository extends JpaRepository<ModePassation, Integer> {
    // La reprise de CATEGORIE vit désormais dans la migration Flyway V2 (LOT 2, 2026-08-26) —
    // l'ex-requête findByDeclencheAgpmTrueAndCategorieIsNull n'a plus d'appelant.


    /** Prochaine PK allouee par la sequence serveur {@code seq_mode_passation} (allocation atomique). */
    @Query(value = "select nextval('seq_mode_passation')", nativeQuery = true)
    Long nextIdMode();
}
