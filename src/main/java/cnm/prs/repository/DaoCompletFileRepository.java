package cnm.prs.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.DaoCompletFile;

/** ⚠️ V144 (2026-10-28, « dao-complet-en-file ») — la file d'assemblage du DAO complet. */
@Repository
public interface DaoCompletFileRepository extends JpaRepository<DaoCompletFile, Integer> {

    /** La file dans l'ordre de passage : la tête de file (avis spécifique), puis l'ordre d'arrivée. */
    @Query("""
            select f from DaoCompletFile f where f.etat = 'EN_FILE'
             order by f.prioritaire desc, f.entreeLe asc, f.idFiche asc
            """)
    List<DaoCompletFile> enFile();

    /** Les versions qu'on peut prendre maintenant (pas d'échec récent), dans l'ordre de passage. */
    @Query("""
            select f from DaoCompletFile f where f.etat = 'EN_FILE' and (f.repriseApres is null or f.repriseApres <= :maintenant)
             order by f.prioritaire desc, f.entreeLe asc, f.idFiche asc
            """)
    List<DaoCompletFile> prenables(@Param("maintenant") LocalDateTime maintenant);

    Optional<DaoCompletFile> findFirstByIdDmcOrderByIdFicheDesc(Long idDmc);

    /** Au démarrage : un assemblage interrompu par l'arrêt du serveur retourne en file, à sa place. */
    @Modifying
    @Query("update DaoCompletFile f set f.etat = 'EN_FILE', f.depuis = :maintenant where f.etat = 'EN_COURS'")
    int remettreEnFile(@Param("maintenant") LocalDateTime maintenant);
}
