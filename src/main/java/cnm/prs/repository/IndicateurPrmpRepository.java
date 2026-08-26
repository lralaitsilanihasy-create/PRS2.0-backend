package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.IndicateurPrmp;

@Repository
public interface IndicateurPrmpRepository extends JpaRepository<IndicateurPrmp, Integer> {

    /** Existe-t-il au moins un indicateur pour cette PRMP ? (garde de suppression PRMP) */
    boolean existsByIdPrmp(String idPrmp);

    /**
     * ⚠️ LOT 3a (2026-08-26) — §3.1 « Mes indicateurs [Lecture] » : indicateurs d'UNE PRMP. La PRMP
     * (et l'UGPM de sa tutelle) ne voit que les siens ; Président et Administrateur voient tout.
     */
    List<IndicateurPrmp> findByIdPrmp(String idPrmp);
}
