package cnm.prs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.RegleAnomalie;

@Repository
public interface RegleAnomalieRepository extends JpaRepository<RegleAnomalie, Integer> {

    /**
     * ⚠️ Pré-contrôle du PPM (2026-09-20, lot 3, étape 2) — la règle portant ce code. C'est par là que le
     * pré-contrôle sait si une règle est <strong>active</strong> : la colonne {@code ACTIF} d'une table se
     * change sans redéploiement, ce qui est toute la réponse à la fatigue d'alerte.
     */
    Optional<RegleAnomalie> findByCodeRegle(String codeRegle);

    /** Prochaine PK allouee par la sequence serveur {@code seq_regle_anomalie} (allocation atomique). */
    @Query(value = "select nextval('seq_regle_anomalie')", nativeQuery = true)
    Long nextIdRegleAnomalie();
}
