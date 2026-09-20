package cnm.prs.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.SeuilMarche;
import cnm.prs.enums.BaremeSeuil;
import cnm.prs.enums.TypeSeuil;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3) — accès au référentiel de seuils
 * ({@code tr_seuil_marche}).
 */
@Repository
public interface SeuilMarcheRepository extends JpaRepository<SeuilMarche, Integer> {

    /**
     * Toutes les valeurs en vigueur à une date, quel que soit leur type ou leur catégorie : le
     * pré-contrôle charge le barème <strong>une fois</strong> pour tout un plan, puis travaille en
     * mémoire — un PPM porte des dizaines à des centaines de lignes, et chacune interroge plusieurs
     * seuils.
     */
    @Query("""
            select s from SeuilMarche s
            where s.dateEffet <= :date and (s.dateFin is null or s.dateFin > :date)
            """)
    List<SeuilMarche> findEnVigueurLe(@Param("date") LocalDate date);

    /**
     * Les valeurs d'un type et d'un barème en vigueur à une date — pour l'écran d'administration du
     * référentiel, qui présente un barème à la fois.
     */
    @Query("""
            select s from SeuilMarche s
            where s.typeSeuil = :type and s.bareme = :bareme
              and s.dateEffet <= :date and (s.dateFin is null or s.dateFin > :date)
            order by s.categorieSeuil
            """)
    List<SeuilMarche> findEnVigueurPourBareme(@Param("type") TypeSeuil type, @Param("bareme") BaremeSeuil bareme,
            @Param("date") LocalDate date);

    /** Toute l'histoire d'une case du barème, la plus récente d'abord (administration, traçabilité). */
    List<SeuilMarche> findByTypeSeuilAndBaremeOrderByDateEffetDesc(TypeSeuil typeSeuil, BaremeSeuil bareme);
}
