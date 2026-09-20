package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.AnomalieLigne;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3) — lignes visées par un signalement
 * inter-lignes ({@code t_anomalie_ligne}).
 */
@Repository
public interface AnomalieLigneRepository extends JpaRepository<AnomalieLigne, AnomalieLigne.Cle> {

    List<AnomalieLigne> findByIdAnomalie(Integer idAnomalie);

    /** Les lignes de plusieurs signalements d'un coup — l'écran en liste des dizaines. */
    List<AnomalieLigne> findByIdAnomalieIn(Collection<Integer> idsAnomalie);

    /**
     * Remise à plat des lignes d'un signalement avant réécriture : le groupe visé change quand le plan
     * change (une ligne ajoutée au même compte entre dans le groupe).
     */
    long deleteByIdAnomalie(Integer idAnomalie);
}
